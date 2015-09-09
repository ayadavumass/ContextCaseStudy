package edu.umass.cs.UberWithCS;
import java.util.Vector;
import java.util.concurrent.ConcurrentMap;

import edu.umass.cs.msocket.contextsocket.ContextSocket;
import edu.umass.cs.msocket.contextsocket.ContextWriter;


public class UberCSCustomer
{
	public static final int MAX_TAXI_DISTANCE = 10;
	//owner name 
	public static final String writerName = "writer0";
	
	public static final double destCoordLat  = 50;
	public static final double destCoordLong = 50;
	
	private double pickupLocationLat;
	private double pickupLocationLong;
	
	public static void main(String[] args)
	{
	}
	
	private void getATaxi(double pickLat, double pickLong)
	{
		setPickUpLocation(pickLat, pickLong);
		
		double minLat = pickLat   - MAX_TAXI_DISTANCE;
		double maxLat = pickLat   + MAX_TAXI_DISTANCE;
		double minLong = pickLong - MAX_TAXI_DISTANCE;
		double maxLong = pickLong + MAX_TAXI_DISTANCE;
		
		String taxiQuery = minLat +" <= contextLat <= "+maxLat+" && "+minLong+" <= contextLong <= "+maxLong;
		
		try
		{
			ContextWriter cwriter = new ContextWriter(writerName, taxiQuery);
			String message = "Need a ride to "+destCoordLat+", "+destCoordLong+" from "+pickLat+", "+pickLong;
			cwriter.writeAll(message.getBytes(), 0, message.getBytes().length);
			
			ConcurrentMap<String, ContextSocket> memberMap = cwriter.accept();
			
			Vector<ContextSocket> memberVect = new Vector<ContextSocket>();
			memberVect.addAll(memberMap.values());
			
			
			
			byte[] driverResp = new byte[1000];
			boolean ackSent = false;
			for(int i=0;i<memberVect.size();i++)
			{
				ContextSocket currsock = memberVect.get(i);
				
				int numBytes = currsock.getInputStream().read(driverResp);
				if(numBytes > 0)
				{
					String response = new String(driverResp);
					if(response.equals("Yes"))
					{
						if(!ackSent)
						{
							String resp = "Yes";
							currsock.getOutputStream().write(resp.getBytes());
							ackSent = true;
						}
						else
						{
							String resp = "No";
							currsock.getOutputStream().write(resp.getBytes());
							ackSent = true;
						}
					}
					//System.out.println();
				}
			}
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	private void setPickUpLocation(double pickLat, double pickLong)
	{
		this.pickupLocationLat  = pickLat;
		this.pickupLocationLong = pickLong;
	}
}