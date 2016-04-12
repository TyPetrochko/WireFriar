import java.lang.reflect.Method;
import java.util.*;

public class AsyncSendHelper{
	private final int initialRetryInterval = 200; // how frequently we retry to connect while pending
    private final int defaultWindow = 1000; // how large should default window be
    private final double alpha = .125; // meta-var for RTT prediction
    private final double beta = .25; // meta-var for RTT std. dev prediction
    private final TCPManager tcpMan;
    private final Node node;
    private final TCPSockWrapper wrapper;

    private final int foreignAddress;
    private final int foreignPort;
    private final int localAddress;
    private final int localPort;

    private TransportBuffer transportBuffer;

    private int highestSeqSent;
    private int highestSeqConfirmed;
    private int cwnd;
    private long timeout;

    private boolean isFlushing;

    private int rttEst; // round trip time estimate
    private int rttDev; // round trip std. dev estimate

    public AsyncSendHelper(TCPSockWrapper wrapper, Node node, TCPManager tcpMan, int seq){
    	this.foreignAddress = wrapper.getTCPSock().getForeignAddress();
    	this.foreignPort = wrapper.getTCPSock().getForeignPort();
    	this.localAddress = wrapper.getTCPSock().getLocalAddress();
    	this.localPort = wrapper.getTCPSock().getLocalPort();

    	this.wrapper = wrapper;
    	this.node = node;
    	this.tcpMan = tcpMan;
    	this.highestSeqConfirmed = seq;
    	this.highestSeqSent = seq;
        this.cwnd = defaultWindow;
    	this.timeout = initialRetryInterval;

        try{
            this.transportBuffer = new TransportBuffer(Callback.getMethod("goBackN", this, null), 
                this, null, tcpMan.getManager(), node);
        }catch (Exception e){
            System.err.println("AsyncSendHelper: ERROR; couldn't get goBackN method");
            e.printStackTrace();
        }

    	this.isFlushing = false;

        this.rttEst = initialRetryInterval;
        this.rttDev = 0;
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
        Debug.log(node, "AsyncSendHelper: Received ack from server: " + transport.getSeqNum());
        Debug.log(node, "\tAsyncSendHelper: Highest seq sent = " + highestSeqSent);
        Debug.log(node, "\tAsyncSendHelper: Highest seq ackd = " + highestSeqConfirmed);
    	
     //    if(transport.getType() != Transport.ACK){
    	// 	return;
    	// }else if(transport.getSeqNum() != highestSeqConfirmed + 1){
     //        Debug.log(node, "AsyncSendHelper: Received out-of-order ACK, flushing now");
     //        flush();
    	// 	return;
    	// }else if(wrapper.getState() == TCPSockWrapper.State.CLOSED){
     //        return;
     //    }

        if(transport.getType() != Transport.ACK){
            System.err.println("AsyncSendHelper: ERROR; somehow received a packet that wasn't an ack");
            return;
        }

        if(transport.getSeqNum() > highestSeqConfirmed){
            // advance window 
            highestSeqConfirmed = transport.getSeqNum();

            // remove outdated transports
            Queue<Transport> bufferedTransports = transportBuffer.getAllTransports();
            while(!bufferedTransports.isEmpty()){
                Transport t = bufferedTransports.peek();
                if(t.getSeqNum() + t.getPayload().length < highestSeqConfirmed){
                    bufferedTransports.poll();
                }else{
                    break;
                }
            }

            // adjust our RTT estimate/timeout
            adjustRTT(200);

            // pause timer while we flush
            transportBuffer.stopTimer();

            // send new packets via flush
            flush();
            return;
        }

        // Cancel this packet's callback and update RTT estimate/std.dev
     //    RetryCallback toCancel = RetryCallback.getCallback(transport.getSeqNum());
     //    if(toCancel != null){
     //        long rttMeasured = tcpMan.getManager().now() - toCancel.getTimeSent();
     //        adjustRTT(rttMeasured);
     //        toCancel.cancelAndRemove();
     //    }

    	// // Received acknowledgement; continue
    	// Debug.log(node, "AsyncSendHelper: Received acknowledgement for sequence number " + highestSeqSent);
    	// highestSeqConfirmed += transport.getPayload();
    	// flush();
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
        Debug.log(node, "AsyncSendHelper: Flushing was called");
        Debug.log(node, "\tAsyncSendHelper: Highest seq sent = " + highestSeqSent);
        Debug.log(node, "\tAsyncSendHelper: Highest seq ackd = " + highestSeqConfirmed);

        // check if we're done flushing
    	if(wrapper.getWriteBuffSize() == 0){
            handleDoneFlushing();
    		return;
    	}else{
            isFlushing = true;
        }

        /* Send full window */
        while(highestSeqSent < highestSeqConfirmed + cwnd){

            // are we done sending?
            if(wrapper.getWriteBuffSize() == 0){
                handleDoneFlushing();
                return;
            }

            // Determine num bytes to send
            int numBytesToSend = 0;
            if(Transport.MAX_PAYLOAD_SIZE <= wrapper.getWriteBuffSize() 
                && Transport.MAX_PAYLOAD_SIZE <= highestSeqConfirmed + cwnd - highestSeqSent){
                numBytesToSend = Transport.MAX_PAYLOAD_SIZE;
            }else if(wrapper.getWriteBuffSize() <= Transport.MAX_PAYLOAD_SIZE 
                && wrapper.getWriteBuffSize() <= highestSeqConfirmed + cwnd - highestSeqSent){
                numBytesToSend = wrapper.getWriteBuffSize();
            }else {
                numBytesToSend = highestSeqConfirmed + cwnd - highestSeqSent;
            }

            // if(wrapper.getWriteBuffSize() >= Transport.MAX_PAYLOAD_SIZE &&){
            //     numBytesToSend = Transport.MAX_PAYLOAD_SIZE;
            // }else{
            //     numBytesToSend = wrapper.getWriteBuffSize();
            // }

            byte[] payload = wrapper.readFromWriteBuff(numBytesToSend);
            tryToSendBytes(payload, highestSeqSent + 1);

            // advance window
            highestSeqSent += payload.length;

            // setupSendPacketRetry(payload, i);
        }

        transportBuffer.startTimer(timeout);
    }

    public void goBackN(){
        Debug.log(node, "AsyncSendHelper: Firing goBackN");
        for(Transport t : transportBuffer.getAllTransports()){
            node.sendSegment(localAddress, foreignAddress, 
                Protocol.TRANSPORT_PKT, t.pack());
        }

        transportBuffer.startTimer(timeout);
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
                (Object) new Integer(seqToAcknowledge)}, seqToAcknowledge, m.now(), payload);

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
        Debug.log(node, "AsyncSendHelper: Callback firing");
        Debug.log(node, "\tAsyncSendHelper: Highest seq sent = " + highestSeqSent);
        Debug.log(node, "\tAsyncSendHelper: Highest seq ackd = " + highestSeqConfirmed);
        if(seqToAcknowledge <= highestSeqConfirmed){
            Debug.log(node, "AsyncSendHelper: Shouldn't be here - callback not canceled");
            return;
        }

        // prevent other callbacks from firing
        RetryCallback.cancelAll();

        // re-send all unacknowledged packets
        for(int i = highestSeqConfirmed + 1; i < highestSeqSent + 1; i++){
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

    /**
     * Send a fin signal now, as the write buff
     * is empty and the TCPSock would like to close.
     */
    public void sendFinSignalNow(){
        sendFinSignal(highestSeqSent);
        highestSeqSent++;
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

            transportBuffer.addTransport(t);

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

        node.logOutput("time = " + tcpMan.getManager().now() + " msec");
            node.logOutput("\tDone flushing");

        if(wrapper.getState() == TCPSockWrapper.State.SHUTDOWN && highestSeqSent == highestSeqConfirmed){
            sendFinSignalNow();
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
        node.logOutput("time = " + tcpMan.getManager().now() + " msec");
        node.logOutput("\tsent FIN to " + wrapper.getTCPSock().getForeignAddress());
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

    /**
     * Adjust the RTT estimate and std. dev, along
     * with the timeout.
     *
     * @param rttMeasured long The measured RTT
     *      of a given ack.
     */
    private void adjustRTT(long rttMeasured){
        // System.err.println("AsyncSendHelper: ADJUSTING for RTT = " + rttMeasured 
        //     + ", est = " + rttEst 
        //     + ", RTTdev = " + rttDev 
        //     + ", timeout = " + timeout);
        rttEst = (int)((1.0 - alpha)*rttEst + alpha * rttMeasured);
        rttDev = (int)((1.0 - beta)*rttDev + beta*Math.abs(rttEst - rttMeasured));
        timeout = rttEst + 4*rttDev;
        // System.err.println("AsyncSendHelper: Set timeout = " + timeout);
        // System.err.println("AsyncSendHelper: Measured rtt: " + rttMeasured);
        // System.err.println("AsyncSendHelper: DONE ADJUSTING for RTT = " + rttMeasured 
        //     + ", est = " + rttEst 
        //     + ", RTTdev = " + rttDev 
        //     + ", timeout = " + timeout);
    }
}