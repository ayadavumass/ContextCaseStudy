package edu.umass.cs.UberWithSocket;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class UberMessage
{
	//keys
	public static enum Keys      {MESG_TYPE, PICKUP_LOC, DRIVER_LOC, DRIVER_ID, REQ_ID, DRIVER_ANSWER, SERVER_ANSWER};
	
	//public static enum MesgTypes {CUST_TO_SERVER, SERVER_TO_DRIVER, DRIVER_TO_SERVER, SERVER_TO_CUST};
	
	// message types
	public static final int TAXI_REQ_CUST_TO_SERVER					= 1;
	public static final int TAXI_REQ_SERVER_TO_DRIVER				= 2;
	public static final int TAXI_REQ_DRIVER_TO_SERVER				= 3;
	public static final int TAXI_REQ_SERVER_TO_CUST					= 4;
	public static final int TAXI_REQ_SERVER_TO_DRIVER_ACK			= 5;
	public static final int DRIVER_LOC_UPDATE						= 6;
	
	
	// number of bytes
	public static final int HEADER_LENGTH							= Integer.SIZE/8;
	
	private final int dataLength;
	private final byte[] dataPayload;
	
	public UberMessage(int dataPayloadLen, byte[] userDataPayload)
	{
		dataPayload = userDataPayload;
		dataLength = dataPayloadLen;
	}
	
	public static int sizeofHeader()
	{
		return HEADER_LENGTH;
	}
	
	public int size()
	{
		return sizeofHeader() + dataLength;
	}
	
	public int getDataPayloadLength()
	{
		return dataLength;
	}
	
	public byte[] getBytes()
	{
	    ByteBuffer buf = ByteBuffer.allocate(HEADER_LENGTH + (dataPayload != null ? dataLength : 0));
	    buf.putInt(dataLength);
	    if (dataPayload != null)
	    {
	    	buf.put(dataPayload, 0, dataLength);
	    }
	    buf.flip();
	    return buf.array();
	}

	/*
	 * This method assumes that the byte[] argument b exactly contains a
	 * DataMessage object, i.e., there is no excess bytes beyond the header and
	 * the message body. If that is not the case, it will return null.
	 */
	public static UberMessage getDataMessage(byte[] b)
	{
		if (b == null || b.length < UberMessage.HEADER_LENGTH)
	      return null;
	    ByteBuffer buf = ByteBuffer.wrap(b);
	    UberMessage dm = new UberMessage(buf.getInt(), Arrays.copyOfRange(b, UberMessage.HEADER_LENGTH, b.length));
	    return dm;
	}

	public static UberMessage getDataMessageHeader(byte[] b)
	{
		if (b == null || b.length < UberMessage.HEADER_LENGTH)
	      return null;
	    ByteBuffer buf = ByteBuffer.wrap(b, 0, UberMessage.HEADER_LENGTH);
	    return new UberMessage(buf.getInt(), null);
	}
	
	/*public String toString()
	{
		String s = "";
	    s += Type + ", " + sendSeq + ", " + ackSeq + ", " + length 
	    		+ ", " + (msg != null ? new String(msg) : "");
	    return s;
	}*/
}