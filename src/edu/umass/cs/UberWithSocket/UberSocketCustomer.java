package edu.umass.cs.UberWithSocket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import org.json.JSONException;
import org.json.JSONObject;

public class UberSocketCustomer
{
	public static final int MAX_TAXI_DISTANCE 		= 10;
	//owner name 
	public static final String writerName 			= "writer0";
	
	public static final double destCoordLat  		= 50;
	public static final double destCoordLong 		= 50;
	
	private double pickupLocationLat;
	private double pickupLocationLong;
	private Socket customerToServerSock;
	
	public static void main(String[] args)
	{
	}
	
	public UberSocketCustomer()
	{
		try
		{
			customerToServerSock = 
					new Socket(InetAddress.getByName(UberSocketDefaults.SERVER_ADDRESS), UberSocketDefaults.SERVER_PORT);
		} catch (UnknownHostException e) 
		{
			e.printStackTrace();
		} catch (IOException e) 
		{
			e.printStackTrace();
		}
	}
	
	private void getATaxi(double pickLat, double pickLong)
	{
		setPickUpLocation(pickLat, pickLong);
		JSONObject messageJSON = new JSONObject();
		String locString = pickLat+":"+pickLong;
		
		try
		{
			messageJSON.put(UberMessage.Keys.MESG_TYPE.toString(), UberMessage.TAXI_REQ_CUST_TO_SERVER);
			messageJSON.put(UberMessage.Keys.PICKUP_LOC.toString(), locString);
			
			String jsonString = messageJSON.toString();
			
			UberMessage sendReq = new UberMessage(jsonString.length(), jsonString.getBytes());
			customerToServerSock.getOutputStream().write(sendReq.getBytes());
		} catch(JSONException e1)
		{
			e1.printStackTrace();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	private void setPickUpLocation(double pickLat, double pickLong)
	{
		this.pickupLocationLat  = pickLat;
		this.pickupLocationLong = pickLong;
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
							int	numBytesRead = customerToServerSock.getInputStream().read
									(dataPayload, numRead, messageHeader.getDataPayloadLength()-numRead);
							numRead = numRead + numBytesRead;
							
							System.out.println("Message from server "+new String(dataPayload));
							JSONObject dataJSON = new JSONObject(new String(dataPayload));
							
							switch( dataJSON.getInt(UberMessage.Keys.MESG_TYPE.toString()) )
							{
								case UberMessage.TAXI_REQ_SERVER_TO_CUST:
								{
									long reqID   = dataJSON.getLong(UberMessage.Keys.REQ_ID.toString());
									int driverID = dataJSON.getInt(UberMessage.Keys.DRIVER_ID.toString());
									System.out.println("Received reply redID "+reqID+" driverID "+driverID);
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
					int	numBytesRead = customerToServerSock.getInputStream().
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