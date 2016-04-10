import java.lang.reflect.Method;

public class AsyncSendHelper{
	private final long retryInterval = 100; // how frequently we retry to connect while pending
    private final TCPManager tcpMan;
    private final Node node;
    private final TCPSockWrapper wrapper;

    private final int foreignAddress;
    private final int foreignPort;
    private final int localAddress;
    private final int localPort;

    private int highestSeqSent;
    private int highestSeqAcknowledged;
    private int cwnd;
    private long timeout;

    private boolean isFlushing;

    public AsyncSendHelper(TCPSockWrapper wrapper, Node node, TCPManager tcpMan, int seq){
    	this.foreignAddress = wrapper.getTCPSock().getForeignAddress();
    	this.foreignPort = wrapper.getTCPSock().getForeignPort();
    	this.localAddress = wrapper.getTCPSock().getLocalAddress();
    	this.localPort = wrapper.getTCPSock().getLocalPort();

    	this.wrapper = wrapper;
    	this.node = node;
    	this.tcpMan = tcpMan;
    	this.highestSeqAcknowledged = seq;
    	this.highestSeqSent = seq;
    	this.timeout = retryInterval;

    	this.isFlushing = false;
    }

    /**
     * Handle an incoming acknowledgement;
     * this should be called from 
     * TCPSockWrapper.
     *
     * @param transport Transport The incoming
     *			ack.
     */
    public void checkAck(Transport transport){
    	if(transport.getType() != Transport.ACK){
    		return;
    	}else if(transport.getSeqNum() != highestSeqSent){
    		return;
    	}

    	// Received acknowledgement; continue
    	Debug.log(node, "AsyncSendHelper: Received acknowledgement for sequence " + highestSeqSent);
    	highestSeqAcknowledged = highestSeqSent;
    	flush();
    }

    /**
     * Start Async command chain that
     * results in TCP transmission.
     * 
     * Does nothing if write buff
     * is empty, and stop Async 
     * command chain.
     */
    public void flush(){
    	if(wrapper.getWriteBuffSize() == 0){
    		Debug.log(node, "AsyncSendHelper: Done flushing");
    		isFlushing = false;
    		return;
    	}

    	// Determine num bytes to send
    	int numBytesToSend = 0;
    	if(wrapper.getWriteBuffSize() > Transport.MAX_PAYLOAD_SIZE){
    		numBytesToSend = Transport.MAX_PAYLOAD_SIZE;
    	}else{
    		numBytesToSend = wrapper.getWriteBuffSize();
    	}

    	Debug.log(node, "AsyncSendHelper: Trying to send " + numBytesToSend + " bytes");
    	Debug.log(node, "\tAsyncSendHelper: There is " + wrapper.getWriteBuffSize() 
    		+ " bytes available to send");

    	// Read bytes from write buffer
    	byte[] payload = wrapper.readFromWriteBuff(numBytesToSend);

    	Debug.log(node, "\tAsyncSendHelper: Recovered payload of size " + payload.length);

    	// Try to send bytes
    	tryToSendBytes(payload, highestSeqAcknowledged + 1);
    	highestSeqSent = highestSeqAcknowledged + 1;
    	Debug.log(node, "AsyncSendHelper: Sending sequence number " + highestSeqSent);

    	// Make sure we re-try if don't get acknowledgement
    	setupSendPacketRetry(payload, highestSeqSent);
    }

    /**
     * Set up a callback to retry this payload
     * in timeout ms if not acknowledged by then.
     *
     * @param payload byte[] Bytes to re-send
     * @param seqToAcknowledge int The sequence number to acknowledge
     */
    public void setupSendPacketRetry(byte [] payload, int seqToAcknowledge){
    	Manager m = tcpMan.getManager();
        try {
            Method method = Callback.getMethod("resendIfNotAcknowledged", this, new String [] {"[B", "java.lang.Integer"});
            Callback cb = new Callback(method, this, new Object [] {
                (Object) payload, 
                (Object) new Integer(seqToAcknowledge)});

            m.addTimer(this.node.getAddr(), timeout, cb);
        }catch(Exception e) {
            System.err.println("TCPSockWrapper: Failed to add timer callback. Method Name: resendIfNotAcknowledged" +
                 "\nException: " + e);
        }
    }

    /**
     * Callback to resend a packet 
     *
     * @param payload byte[] Bytes to re-send
     * @param seqToAcknowledge int The sequence number to acknowledge
     */
    public void resendIfNotAcknowledged(byte [] payload, Integer seqToAcknowledge){
    	if(seqToAcknowledge <= highestSeqAcknowledged){
    		return; // already acknowledged
    	}
    	
    	// Try to send bytes
    	tryToSendBytes(payload, seqToAcknowledge);

    	// Make sure we re-try if don't get acknowledgement
    	setupSendPacketRetry(payload, seqToAcknowledge);
    }

    public boolean isFlushing(){
    	return isFlushing;
    }

    /*
     * Private methods
     */

    /**
     * Try to send some bytes down the wire.
     *
     * @param payload byte[] the data to send
     * @param seqnum int The sequence number of the packet
     */
    private void tryToSendBytes(byte [] payload, int seqNum){
    	Debug.log(node, "AsyncSendHelper: Sending " + payload.length + " bytes over network");
    	try{
    		// Make a transport to send the data
    		Transport t = new Transport(localPort, foreignPort, 
    			Transport.DATA, -1, seqNum, payload);

    		// Send the packet over the wire
    		node.sendSegment(localAddress, foreignAddress, 
    			Protocol.TRANSPORT_PKT, t.pack());

    	}catch(IllegalArgumentException iae){
            System.err.println("AsyncSendHelper: Shouldn't be here " 
            	+ " passed bad args to Transport constructor");
            iae.printStackTrace();
            return;
        }
    }
}