package edu.umass.cs.UberWithSocket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONException;
import org.json.JSONObject;

public class UberSocketServer implements Runnable
{
	public static final int MAX_TAXI_DISTANCE = 10;
	
	private ServerSocketChannel serverSocketChannel;
	private Selector selector;
	
	private ExecutorService eservice;
	
	private final ConcurrentHashMap<Integer, DriverInfo> driverInfoMap;
	private long customerReqID;
	
	private Object customerReqIDLock;
	
	private ConcurrentHashMap<Long, CustomerReqInfo> outstandingCustomerReqMap;
	
	public UberSocketServer() throws IOException
	{
		serverSocketChannel = ServerSocketChannel.open();
		
		serverSocketChannel.socket().bind(new InetSocketAddress
				(InetAddress.getByName(UberSocketDefaults.SERVER_ADDRESS), UberSocketDefaults.SERVER_PORT));
		serverSocketChannel.configureBlocking(false);
		selector = Selector.open();
		eservice = Executors.newFixedThreadPool(100);
		driverInfoMap = new ConcurrentHashMap<Integer, DriverInfo>();
		customerReqID = 0;
		customerReqIDLock = new Object();
	}
	
	public static void main(String[] args)
	{
	}
	
	@Override
	public void run()
	{
		while(true)
		{
			try
			{
				SocketChannel socketChannel =
				            serverSocketChannel.accept();
				
				socketChannel.register(selector, SelectionKey.OP_READ);
			} catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}
	
	private void customerTaxiProcessing(JSONObject dataJSON, SocketChannel taxiRequestChannel)
	{
		try
		{
			String pickupLocString = dataJSON.getString(UberMessage.Keys.PICKUP_LOC.toString());
			String[] parsed = pickupLocString.split(":");
			
			double pickLat  = Double.parseDouble(parsed[0]);
			double pickLong = Double.parseDouble(parsed[1]);
			
			long currReqID;
			
			synchronized(customerReqIDLock)
			{
				currReqID = customerReqID++;
			}
			
			CustomerReqInfo custReqInfo = new CustomerReqInfo();
			
			custReqInfo.customerSockChannel = taxiRequestChannel;
			custReqInfo.pickupLoc = pickupLocString;
			custReqInfo.reqID = currReqID;
			outstandingCustomerReqMap.put(currReqID, custReqInfo);
			
			
			Iterator<Integer> keyIter = driverInfoMap.keySet().iterator();
			
			while( keyIter.hasNext() )
			{
				int currKey = keyIter.next();
				DriverInfo drivInfo = driverInfoMap.get(currKey);
				SocketChannel driverSock = drivInfo.driverSockChannel;
				
				parsed = drivInfo.driverLocation.split(":");
				double drivLat = Double.parseDouble(parsed[0]);
				double drivLong = Double.parseDouble(parsed[1]);
				
				double minLat  = pickLat   - MAX_TAXI_DISTANCE;
				double maxLat  = pickLat   + MAX_TAXI_DISTANCE;
				double minLong = pickLong  - MAX_TAXI_DISTANCE;
				double maxLong = pickLong  + MAX_TAXI_DISTANCE;
				
				if( ((drivLat >= minLat) && (drivLat <= maxLat)) && ((drivLong >= minLong) && (drivLong <= maxLong)) )
				{
					//send reqs to them for the taxi req
					
					JSONObject messageJSON = new JSONObject();
					
					try
					{
						messageJSON.put(UberMessage.Keys.MESG_TYPE.toString(), UberMessage.TAXI_REQ_SERVER_TO_DRIVER);
						messageJSON.put(UberMessage.Keys.PICKUP_LOC.toString(), pickupLocString);
						messageJSON.put(UberMessage.Keys.REQ_ID.toString(), currReqID);
						
						String jsonString = messageJSON.toString();
						
						UberMessage sendReq = new UberMessage(jsonString.length(), jsonString.getBytes());
						driverSock.socket().getOutputStream().write(sendReq.getBytes());
						
					} catch(JSONException e1)
					{
						e1.printStackTrace();
					} catch (IOException e)
					{
						e.printStackTrace();
					}
				}
			}
		} catch (JSONException e)
		{
			e.printStackTrace();
		}
	}
	
	private void processDriverLocationUpdate(JSONObject dataJSON, SocketChannel driverChannel)
	{
		try
		{
			int driverID     = dataJSON.getInt(UberMessage.Keys.DRIVER_ID.toString());
			String locString = dataJSON.getString(UberMessage.Keys.DRIVER_LOC.toString());
			
			DriverInfo drivInfo = new DriverInfo();
			drivInfo.driverLocation = locString;
			drivInfo.driverSockChannel = driverChannel;
			
			driverInfoMap.put(driverID, drivInfo);
		} catch (JSONException e)
		{
			e.printStackTrace();
		}
	}
	
	private void  processTaxiDriverReply(JSONObject dataJSON, SocketChannel driversChannel)
	{
		try
		{
			int yesOrNo = dataJSON.getInt(UberMessage.Keys.DRIVER_ANSWER.toString());
			long reqID  = dataJSON.getLong(UberMessage.Keys.REQ_ID.toString());
			int driverID = dataJSON.getInt(UberMessage.Keys.DRIVER_ID.toString());
			
			// driver said yes
			if(yesOrNo == 1)
			{
				// this operation is atomic, only one will be returned non null
				CustomerReqInfo custReqInfo = outstandingCustomerReqMap.remove(reqID);
				
				JSONObject messageJSON = new JSONObject();
				messageJSON.put(UberMessage.Keys.MESG_TYPE.toString(), UberMessage.TAXI_REQ_SERVER_TO_DRIVER_ACK);
				messageJSON.put(UberMessage.Keys.REQ_ID.toString(), reqID);
				try
				{
					if( custReqInfo != null )
					{
						// 1 for yes, 0 for no
						messageJSON.put(UberMessage.Keys.SERVER_ANSWER.toString(), 1);
						
						// send message to the user.
						JSONObject userMesgJSON = new JSONObject();
						userMesgJSON.put(UberMessage.Keys.MESG_TYPE.toString(), UberMessage.TAXI_REQ_SERVER_TO_CUST);
						userMesgJSON.put(UberMessage.Keys.REQ_ID.toString(), reqID);
						userMesgJSON.put(UberMessage.Keys.DRIVER_ID.toString(), driverID);
						
						
						String jsonString = userMesgJSON.toString();
						
						UberMessage sendReq = new UberMessage(jsonString.length(), jsonString.getBytes());
						custReqInfo.customerSockChannel.socket().getOutputStream().write(sendReq.getBytes());
					}
					else
					{
						messageJSON.put(UberMessage.Keys.SERVER_ANSWER.toString(), 0);
					}
					String jsonString = messageJSON.toString();
					
					UberMessage sendReq = new UberMessage(jsonString.length(), jsonString.getBytes());
					driversChannel.socket().getOutputStream().write(sendReq.getBytes());
				} catch(JSONException e1)
				{
					e1.printStackTrace();
				} catch (IOException e)
				{
					e.printStackTrace();
				}
			}
		} catch (JSONException e)
		{
			e.printStackTrace();
		}
	}
	
	private class SelectorRead implements Runnable
	{
		@Override
		public void run()
		{
			while(true)
			{
				int readyChannels;
				try
				{
					readyChannels = selector.select();
					
					if(readyChannels == 0) continue;
					
					
					Set<SelectionKey> selectedKeys = selector.selectedKeys();
					
					Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
					
					while( keyIterator.hasNext() )
					{
						SelectionKey key = keyIterator.next();
						
						if (key.isReadable())
						{
							SocketChannel readyChannel = (SocketChannel) key.channel();
							eservice.submit(new ReadProcessingClass(readyChannel));
						}
						
					    keyIterator.remove();
					}
					
				} catch (IOException e) 
				{
					e.printStackTrace();
				}
			}
		}
	}
	
	private class ReadProcessingClass implements Runnable
	{
		private SocketChannel channelToRead;
		
		public ReadProcessingClass(SocketChannel channelToRead)
		{
			this.channelToRead = channelToRead;
		}
		
		@Override
		public void run()
		{
			UberMessage messageHeader = readUberMessageBytes();
			
			if( messageHeader != null )
			{
				try
				{
					byte[] dataPayload = new byte[messageHeader.getDataPayloadLength()];
					int numRead = 0;
					while( numRead < messageHeader.getDataPayloadLength() )
					{
						int	numBytesRead = channelToRead.socket().getInputStream().read
								(dataPayload, numRead, messageHeader.getDataPayloadLength()-numRead);
						numRead = numRead + numBytesRead;
						
						System.out.println("Message from server "+new String(dataPayload));
						JSONObject dataJSON = new JSONObject(new String(dataPayload));
						
						switch( dataJSON.getInt(UberMessage.Keys.MESG_TYPE.toString()) )
						{
							case UberMessage.TAXI_REQ_CUST_TO_SERVER:
							{
								customerTaxiProcessing(dataJSON, channelToRead);
								break;
							}
							case UberMessage.TAXI_REQ_DRIVER_TO_SERVER:
							{
								processTaxiDriverReply(dataJSON, channelToRead);
								break;
							}
							case UberMessage.DRIVER_LOC_UPDATE:
							{
								processDriverLocationUpdate(dataJSON, channelToRead);
								break;
							}
						}
					}
				} catch(IOException ex)
				{
					ex.printStackTrace();
				} catch (JSONException e)
				{
					e.printStackTrace();
				}
			}
		}
		
		private UberMessage readUberMessageBytes()
		{
			byte[] messageHeaderBytes = new byte[UberMessage.HEADER_LENGTH];
			int numRead = 0;
			
			try
			{
				while(numRead < UberMessage.HEADER_LENGTH)
				{
					int	numBytesRead = channelToRead.socket().getInputStream().
							read(messageHeaderBytes, numRead, UberMessage.HEADER_LENGTH-numRead);
					numRead = numRead + numBytesRead;
				}
				return UberMessage.getDataMessageHeader(messageHeaderBytes);
			} catch (IOException e)
			{
				e.printStackTrace();
				return null;
			}
		}
	}
	
	private class DriverInfo
	{
		public String driverLocation;
		public SocketChannel driverSockChannel;
	}
	
	private class CustomerReqInfo
	{
		public String pickupLoc;
		public SocketChannel customerSockChannel;
		public long reqID;
	}
}