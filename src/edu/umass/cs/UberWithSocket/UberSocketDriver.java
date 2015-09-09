package edu.umass.cs.UberWithSocket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Random;

import org.json.JSONException;
import org.json.JSONObject;

import edu.umass.cs.UberWithCS.UberConstants;

public class UberSocketDriver implements Runnable
{
	public static final int DRIVER_UPDATE_LOC_TIME 					= 300000;
	
	private Socket driverToServerSock;
	private Random rand;
	private final int driverID;
	
	private final Object writeLock;
	
	public UberSocketDriver(int driverID)
	{
		this.driverID = driverID;
		
		try
		{
			driverToServerSock = 
					new Socket(InetAddress.getByName(UberSocketDefaults.SERVER_ADDRESS), UberSocketDefaults.SERVER_PORT);
		} catch (UnknownHostException e)
		{
			e.printStackTrace();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
		writeLock = new Object();
		
		new Thread(new ReadFromSocket()).start();
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
				updateDriversLocation();
				Thread.sleep(DRIVER_UPDATE_LOC_TIME);
			} catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}
	}
	
	private void updateDriversLocation()
	{
		double currLat  = rand.nextInt((int)(UberConstants.MAX_LAT - UberConstants.MIN_LAT));
		double currLong = rand.nextInt((int)(UberConstants.MAX_LONG - UberConstants.MIN_LONG));
		
		JSONObject messageJSON = new JSONObject();
		String locString = currLat+":"+currLong;
		
		try
		{
			messageJSON.put(UberMessage.Keys.MESG_TYPE.toString(), UberMessage.DRIVER_LOC_UPDATE);
			messageJSON.put(UberMessage.Keys.DRIVER_LOC.toString(), locString);
			messageJSON.put(UberMessage.Keys.DRIVER_ID.toString(), driverID);
			
			String jsonString = messageJSON.toString();
			
			UberMessage sendReq = new UberMessage(jsonString.length(), jsonString.getBytes());
			
			synchronized(writeLock)
			{
				driverToServerSock.getOutputStream().write(sendReq.getBytes());
			}
		} catch(JSONException e1)
		{
			e1.printStackTrace();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	private class ReadFromSocket implements Runnable
	{
		public void run()
		{
			while(true)
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
							int	numBytesRead = driverToServerSock.getInputStream().read
									(dataPayload, numRead, messageHeader.getDataPayloadLength()-numRead);
							numRead = numRead + numBytesRead;
							
							System.out.println("Message from server "+new String(dataPayload));
							JSONObject dataJSON = new JSONObject(new String(dataPayload));
							
							switch( dataJSON.getInt(UberMessage.Keys.MESG_TYPE.toString()) )
							{
								case UberMessage.TAXI_REQ_SERVER_TO_DRIVER:
								{
									//send reqs to them for the taxi req	
									JSONObject messageJSON = new JSONObject();
									
									try
									{
										messageJSON.put(UberMessage.Keys.MESG_TYPE.toString(), 
												UberMessage.TAXI_REQ_DRIVER_TO_SERVER);
										
										// 0 for no, 1 for yes
										int yesOrNo = rand.nextInt(2);
										
										messageJSON.put(UberMessage.Keys.DRIVER_ANSWER.toString(), yesOrNo);
										messageJSON.put(UberMessage.Keys.REQ_ID.toString(), 
												dataJSON.getLong(UberMessage.Keys.REQ_ID.toString()));
										
										String jsonString = messageJSON.toString();
										
										UberMessage sendReq = new UberMessage(jsonString.length(), jsonString.getBytes());
										synchronized(writeLock)
										{
											driverToServerSock.getOutputStream().write(sendReq.getBytes());
										}
										
									} catch(JSONException e1)
									{
										e1.printStackTrace();
									} catch (IOException e)
									{
										e.printStackTrace();
									}
									break;
								}
								
								case UberMessage.TAXI_REQ_SERVER_TO_DRIVER_ACK:
								{
									int serverAnswer = dataJSON.getInt(UberMessage.Keys.SERVER_ANSWER.toString());
									if(serverAnswer == 0)
									{
										System.out.println("Server denied the request");
									}
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
		}
		
		private UberMessage readUberMessageBytes()
		{
			byte[] messageHeaderBytes = new byte[UberMessage.HEADER_LENGTH];
			int numRead = 0;
			
			try
			{
				while(numRead < UberMessage.HEADER_LENGTH)
				{
					int	numBytesRead = driverToServerSock.getInputStream().
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
	
}