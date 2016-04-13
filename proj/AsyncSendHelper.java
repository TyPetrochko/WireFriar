import java.lang.reflect.Method;
import java.util.*;

public class AsyncSendHelper{
    private final int INITIAL_RETRY_INTERVAL = 200;     // how frequently we retry a packet (ms)
    private final int DEFAULT_WINDOW = 1000;            // how large should default window be (bytes)
    private final double ALPHA = .125;                  // meta-var for RTT prediction (ms)
    private final double BETA = .25;                    // meta-var for RTT std. dev prediction (ms)
    private final boolean CONGESTION_CONTROL = true;    // should account for congestion?

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
    private int timeout;
    
    private boolean isFlushing;

    private int rttEst; // round trip time estimate
    private int rttDev; // round trip std. dev estimate

    /* Congestion control */
    private int ssThresh;
    private int lastSeqAckd;
    private int numAckRepeats;

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
        this.cwnd = DEFAULT_WINDOW;
    	this.timeout = INITIAL_RETRY_INTERVAL;

        this.ssThresh = Integer.MAX_VALUE;
        this.lastSeqAckd = -1;
        this.numAckRepeats = 0;

        try{
            this.transportBuffer = new TransportBuffer(Callback.getMethod("goBackN", this, null), 
                this, null, tcpMan.getManager(), node);
        }catch (Exception e){
            System.err.println("AsyncSendHelper: ERROR; couldn't get goBackN method");
            e.printStackTrace();
        }

    	this.isFlushing = false;

        this.rttEst = INITIAL_RETRY_INTERVAL;
        this.rttDev = 0;
    }

    /**
     * Handle an incoming acknowledgement;
     * this should be called from 
     * TCPSockWrapper.
     *
     * @param transport The incoming ACK signal.
     */
    public void checkAck(Transport transport){
        Debug.log(node, "AsyncSendHelper: Received ack from server: " + transport.getSeqNum());
        Debug.log(node, "\tAsyncSendHelper: Highest seq sent = " + highestSeqSent);
        Debug.log(node, "\tAsyncSendHelper: Highest seq ackd = " + highestSeqConfirmed);


        if(transport.getType() != Transport.ACK){
            System.err.println("AsyncSendHelper: ERROR; somehow received a packet that wasn't an ack");
            return;
        }

        // We may want to adjust window for congestion control
        if(CONGESTION_CONTROL){
            checkForTripleAck(transport.getSeqNum());
        }

        // make sure that we're not receiving a stale ack
        if(transport.getSeqNum() <= highestSeqConfirmed){
            Debug.trace("?");
            return;
        }else{
            Debug.trace(":");
        }

        // advance window 
        highestSeqConfirmed = transport.getSeqNum();

        // update window size
        if(CONGESTION_CONTROL){
            if(cwnd < ssThresh){
                cwnd += Transport.MAX_PAYLOAD_SIZE;
            }else{
                cwnd += (int)((double) Transport.MAX_PAYLOAD_SIZE / cwnd);
            }

            // either congestion or flow may limit window
            cwnd = Math.min(cwnd, transport.getWindow());
            Debug.log("AsyncSendHelper: CWND = " + cwnd);
            Debug.log("AsyncSendHelper: ssThresh = " + ssThresh);
        }else{
            // use vanilla flow control
            cwnd = transport.getWindow();
            Debug.log("AsyncSendHelper: CWND = " + cwnd);
        }

        // remove outdated transports
        Queue<TransportWrapper> bufferedTransports = transportBuffer.getAllTransports();
        while(!bufferedTransports.isEmpty()){
            TransportWrapper tw = bufferedTransports.peek();
            Transport t = tw.getTransport();

            // check if this Transport is old (stale)
            if(t.getSeqNum() < highestSeqConfirmed){
                bufferedTransports.poll();

                // adjust our RTT estimate/timeout
                adjustRTT(tcpMan.getManager().now() - tw.getTimeSent());
            }else{
                break;
            }
        }

        // pause timer while we flush
        transportBuffer.stopTimer();

        // send new packets via flush
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
                && Transport.MAX_PAYLOAD_SIZE <= highestSeqConfirmed + cwnd - highestSeqSent + 1){
                numBytesToSend = Transport.MAX_PAYLOAD_SIZE;
            }else if(wrapper.getWriteBuffSize() <= Transport.MAX_PAYLOAD_SIZE
                && wrapper.getWriteBuffSize() <= highestSeqConfirmed + cwnd - highestSeqSent + 1){
                numBytesToSend = wrapper.getWriteBuffSize();
            }else {
                numBytesToSend = highestSeqConfirmed + cwnd - highestSeqSent + 1;
            }

            byte[] payload = wrapper.readFromWriteBuff(numBytesToSend);
            tryToSendBytes(payload, highestSeqSent + 1);

            Debug.trace(".");

            // advance window
            highestSeqSent += payload.length;
        }

        transportBuffer.startTimer(timeout);
    }

    /**
     * Resend all buffered Transports, as the timer
     * has run out.
     */
    public void goBackN(){
        Debug.log(node, "AsyncSendHelper: Firing goBackN with " 
            + transportBuffer.getAllTransports().size() + " remaining transports in buffer");
        Debug.log(node, "\tAsyncSendHelper: Highest seq sent = " + highestSeqSent);
        Debug.log(node, "\tAsyncSendHelper: Highest seq ackd = " + highestSeqConfirmed);

        if(CONGESTION_CONTROL){
            ssThresh = (int)(cwnd / 2.0);
            cwnd = Transport.MAX_PAYLOAD_SIZE;
            Debug.log("AsyncSendHelper: CWND = " + cwnd);
            Debug.log("AsyncSendHelper: SSTHRESH = " + ssThresh);
        }

        if(transportBuffer.getAllTransports().size() == 1){
            Debug.log(node, "\tBytes in single transport = " 
                + transportBuffer.peekTransport().getTransport().getPayload().length);
            Debug.log(node, "\tSeqNum of single transport = " 
                + transportBuffer.peekTransport().getTransport().getSeqNum());
        }

        for(TransportWrapper tw : transportBuffer.getAllTransports()){
            tw.setTimeSent(tcpMan.getManager().now());
            Debug.log("!");
            node.sendSegment(localAddress, foreignAddress, 
                Protocol.TRANSPORT_PKT, tw.getTransport().pack());
        }

        transportBuffer.startTimer(timeout);
        flush();
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
     * Determine if there are some un-acknowledged
     * transports in buffer
     *
     * @return True if there are buffered transports
     */
    public boolean hasBufferedTransports(){
        return transportBuffer.getAllTransports().isEmpty();
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

            transportBuffer.addTransport(t, tcpMan.getManager().now());

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

        transportBuffer.startTimer(timeout);

        // node.logOutput("time = " + tcpMan.getManager().now() + " msec");
        // node.logOutput("\tDone flushing, still " + transportBuffer.getAllTransports().size() 
        //     + " buffered transports");

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
     * The one exception is that it stops the
     * callback timer. This is to prevent a
     * glitch in which the last remaining byte
     * gets resent (forever) even after the
     * fin signal is sent.
     *
     * @param seqNum int The sequence number
     *      of the termination sequence. This
     *      is effectively ignored, so its
     *      correctness is not necessary.
     */
    private void sendFinSignal(int seqNum){
        Debug.log(node, "AsyncSendHelper: Sending termination signal");
        // node.logOutput("time = " + tcpMan.getManager().now() + " msec");
        // node.logOutput("\tsent FIN to " + wrapper.getTCPSock().getForeignAddress());

        transportBuffer.stopTimer();

        try{
            // Make a transport to send the data
            Transport t = new Transport(localPort, foreignPort, 
                Transport.FIN, -1, seqNum, new byte[0]);

            // Send the packet over the wire
            node.sendSegment(localAddress, foreignAddress, 
                Protocol.TRANSPORT_PKT, t.pack());

            Debug.trace("F");

        }catch(IllegalArgumentException iae){
            System.err.println("AsyncSendHelper: Shouldn't be here " 
                + " passed bad args to Transport constructor");
            iae.printStackTrace();
            return;
        }
    }

    /**
     * Adjust the last sequence ACK'ed and its
     * count, based off of an incoming ACK. If this
     * is a triple ACK, then we adjust for congestion
     * control.
     *
     * @param seq The incoming packet sequence number
     */
    private void checkForTripleAck(int seq){
        if(lastSeqAckd == seq){
            numAckRepeats++;
        }else{
            lastSeqAckd = seq;
            numAckRepeats = 1;
        }

        if(numAckRepeats == 3){
            cwnd = (int)(cwnd / 2.0);
            ssThresh = cwnd;
        }

        Debug.log("AsyncSendHelper: CWND = " + cwnd);
        Debug.log("AsyncSendHelper: ssThresh = " + ssThresh);
    }

    /**
     * Adjust the RTT estimate and std. dev, along
     * with the timeout.
     *
     * @param rttMeasured long The measured RTT
     *      of a given ack.
     */
    private void adjustRTT(long rttMeasured){
        rttEst = (int)((1.0 - ALPHA)*rttEst + ALPHA * rttMeasured);
        rttDev = (int)((1.0 - BETA)*rttDev + BETA * Math.abs(rttEst - rttMeasured));
        timeout = rttEst + 4*rttDev;
    }
}