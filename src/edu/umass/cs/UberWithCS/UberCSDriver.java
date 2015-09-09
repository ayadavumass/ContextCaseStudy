package edu.umass.cs.UberWithCS;

import java.io.IOException;
import java.util.HashMap;
import java.util.Random;

import edu.umass.cs.msocket.contextsocket.ContextMember;
import edu.umass.cs.msocket.contextsocket.ContextSocket;

public class UberCSDriver implements Runnable
{
	// 300 seconds, 5 mins
	public static final int DRIVER_UPDATE_LOC_TIME = 300000;
	
	public static String driverID;
	private ContextMember cmember;
	
	public static void main(String[] args)
	{
		driverID = args[0];
	}
	
	public UberCSDriver()
	{
		try
		{
			cmember = new ContextMember(driverID, new HashMap<String, Object>());
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	@Override
	public void run()
	{
		Random rand = new Random();
		while(true)
		{
			double currLat = rand.nextInt((int)(UberConstants.MAX_LAT - UberConstants.MIN_LAT));
			double currLong = rand.nextInt((int)(UberConstants.MAX_LONG - UberConstants.MIN_LONG));
			
			cmember.setAttributes("contextLat", currLat);
			cmember.setAttributes("contextLong", currLong);
			
			try
			{
				Thread.sleep(DRIVER_UPDATE_LOC_TIME);
			} catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}
	}
	
	private class AcceptDriverClass implements Runnable
	{
		@Override
		public void run()
		{
			while(true)
			{
				ContextSocket accptContextSock = cmember.accept();
				byte[] readByteArray = new byte[1000];
				
				try 
				{
					accptContextSock.getInputStream().read(readByteArray);
					System.out.println(new String(readByteArray));
					Random rand = new Random();
					int randInt = rand.nextInt(2);
					if(randInt == 1)
					{
						String response = "Yes";
						accptContextSock.getOutputStream().write(response.getBytes());
						// reading again for ack
						accptContextSock.getInputStream().read(readByteArray);
						System.out.println(new String(readByteArray));
					}
				} catch (IOException e)
				{
					e.printStackTrace();
				}
			}
		}
	}
	
}