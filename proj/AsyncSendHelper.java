import java.lang.reflect.Method;

public class AsyncSendHelper{
	private final long retryInterval = 100; // how frequently we retry to connect while pending
    private final int defaultWindow = 12; // how large should default window be
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
        this.cwnd = defaultWindow;
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
    	}else if(transport.getSeqNum() != highestSeqAcknowledged + 1){
    		return;
    	}else if(wrapper.getState() == TCPSockWrapper.State.CLOSED){
            return;
        }

        // Cancel this packet's callback
        RetryCallback toCancel = RetryCallback.getCallback(transport.getSeqNum());
        if(toCancel != null){
            toCancel.cancelAndRemove();
        }

    	// Received acknowledgement; continue
    	Debug.log(node, "AsyncSendHelper: Received acknowledgement for sequence number " + highestSeqSent);
    	highestSeqAcknowledged++;
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
    		Debug.log(node, "AsyncSendHelper: Done flushing - write buffer empty");
            handleDoneFlushing();
    		return;
    	}else{
            isFlushing = true;
        }

        /* Send full window */
        for (int i = highestSeqSent + 1; i < highestSeqAcknowledged + cwnd + 1; i++){
            if(wrapper.getWriteBuffSize() == 0){
                Debug.log(node, "AsyncSendHelper: Done flushing - write buffer empty");
                handleDoneFlushing();
                return;
            }

            // Determine num bytes to send
            int numBytesToSend = 0;
            if(wrapper.getWriteBuffSize() > Transport.MAX_PAYLOAD_SIZE){
                numBytesToSend = Transport.MAX_PAYLOAD_SIZE;
            }else{
                numBytesToSend = wrapper.getWriteBuffSize();
            }

            byte[] payload = wrapper.readFromWriteBuff(numBytesToSend);

            tryToSendBytes(payload, i);
            highestSeqSent++;

            setupSendPacketRetry(payload, i);
        }
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

        try{
            // Get method to fire on timeout
            Method method = Callback.getMethod("resendIfNotAcknowledged", 
                this, new String [] {"[B", "java.lang.Integer"});

            Callback cb = new RetryCallback(method, this, new Object []{
                (Object) payload,
                (Object) new Integer(seqToAcknowledge)}, seqToAcknowledge, payload);

            m.addTimer(this.node.getAddr(), timeout, cb);
        }catch(Exception e){
            System.err.println("AsyncSendHelper: Couldn't send packet");
            e.printStackTrace();
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
            Debug.log(node, "AsyncSendHelper: Shouldn't be here - callback not canceled");
            return;
        }

        // prevent other callbacks from firing
        RetryCallback.cancelAll();

        // re-send all unacknowledged packets
        for(int i = highestSeqAcknowledged + 1; i < highestSeqSent + 1; i++){
            RetryCallback toResend = RetryCallback.getCallback(i);
            if(toResend != null){
                byte[] payloadToSend = toResend.getPayload();

                tryToSendBytes(payloadToSend, i);
                setupSendPacketRetry(payloadToSend, i);
            }
        }
    }

    /**
     * Determine if flushing is currently
     * in progress.
     *
     * @return boolean Whether or not this
     *      helper is currently flushing.
     */
    public boolean isFlushing(){
    	return isFlushing;
    }

    /* ###############################
     * ####### Private Methods #######
     * ###############################
     */

    /**
     * Try to send some bytes down the wire.
     * it assumes that the payload is data, so
     * this cannot be used to terminate/set up
     * connections.
     *
     * @param payload byte[] the data to send
     * @param seqnum int The sequence number of the packet
     */
    private void tryToSendBytes(byte [] payload, int seqNum){
        Debug.log(node, "AsyncSendHelper: Sending " + payload.length 
            + " bytes over network: sequence number " + seqNum);
        try{
            // Make a transport to send the data
            Transport t = new Transport(localPort, foreignPort, 
                Transport.DATA, -1, seqNum, payload);

            Debug.verifyPacket(node, t);

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

    /**
     * Handle the write buffer being done. If
     * the socket is currently being shut down,
     * then this involves signaling our wrapper
     * to set the state to closed. Otherwise do
     * nothing.
     */
    private void handleDoneFlushing(){
        isFlushing = false;

        if(wrapper.getState() == TCPSockWrapper.State.SHUTDOWN && highestSeqSent == highestSeqAcknowledged){
            System.err.println("AsyncSendHelper: Done flushing, highest seq sent is " 
                + highestSeqSent + ", highest acknowledged is " + highestSeqAcknowledged);
            sendFinSignal(highestSeqSent);
            highestSeqSent++;
        }
    }

    /**
     * Send a FIN signal down the wire and
     * don't worry if it arrives. This is the
     * last step in terminating a connection.
     * 
     * This method does NOT handle 
     * book-keeping or resource release. It
     * merely sends the termination signal.
     *
     * @param seqNum int The sequence number
     *      of the termination sequence. This
     *      is effectively ignored, so its
     *      correctness is not necessary.
     */
    private void sendFinSignal(int seqNum){
        Debug.log(node, "AsyncSendHelper: Sending termination signal");
        try{
            // Make a transport to send the data
            Transport t = new Transport(localPort, foreignPort, 
                Transport.FIN, -1, seqNum, new byte[0]);

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