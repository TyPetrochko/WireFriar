/**
 * A wrapper class to represent a socket's
 * representation in the OS. It also encodes
 * connection state, buffers, etc.
 * 
 * Buffers are kept in "write" mode, meaning
 * they must be flipped before being read, 
 * and compacted after reading.
 */
import java.nio.*;
import java.util.*;
import java.lang.reflect.Method;

public class TCPSockWrapper{
    private final long retryInterval = 100; // how frequently we retry to connect while pending
    private final TCPManager tcpMan;
    private final Node node;
    private final TCPSock sock;
    private final ByteBuffer readBuff;
    private final ByteBuffer writeBuff;
    // TCP socket states
    enum State {
        // protocol states
        CLOSED,
        LISTEN,
        SYN_SENT,
        ESTABLISHED,
        SHUTDOWN // close requested, FIN not sent (due to unsent data in queue)
    }
    private State state;
    private int startSeq;
    private Queue<RequestTuple> pendingConnections;
    private int requestsBacklog;

    private AsyncSendHelper sendHelper;
    private AsyncReceiveHelper receiveHelper;

    /**
     * Create a new TCPSockWrapper as the client,
     * without knowing the foreign address and port.
     *
     * You can connect later.
     */
    public TCPSockWrapper(TCPManager tcpMan, Node node, int readBuffSize, int writeBuffSize){
        this.tcpMan = tcpMan;
        this.node = node;
        this.sock = new TCPSock(tcpMan, this);
        this.readBuff = ByteBuffer.allocate(readBuffSize);
        this.writeBuff = ByteBuffer.allocate(writeBuffSize);
        this.state = State.CLOSED;
        this.startSeq = 1;
        this.requestsBacklog = -1;
    }

    /**
     * Create a new TCPSockWrapper as the server,
     * specifying a foreign address and port.
     */
    public TCPSockWrapper(TCPManager tcpMan, Node node, int readBuffSize, int writeBuffSize, 
        int foreignAddress, int foreignPort,
        int localAddress, int localPort){
        this.tcpMan = tcpMan;
        this.node = node;
        this.sock = new TCPSock(tcpMan, this, 
            foreignAddress, foreignPort,
            localAddress, localPort);
        this.readBuff = ByteBuffer.allocate(readBuffSize);
        this.writeBuff = ByteBuffer.allocate(writeBuffSize);
        this.state = State.ESTABLISHED;
        this.startSeq = 1;
        this.requestsBacklog = -1;
    }

    /**
     * @param bytes Bytes to be written into read buffer
     */
    public void writeToReadBuff(byte [] bytes) throws BufferOverflowException{
        try{
            readBuff.put(bytes);
        }catch(ReadOnlyBufferException robe){
            System.err.println("TCPSockWrapper: can't write to read buffer");
            robe.printStackTrace();
        }
    }

    /**
     * @param bytes Bytes to be written into write buffer
     */
    public void writeToWriteBuff(byte [] bytes) throws BufferOverflowException{
        try{
            Debug.log(node, "TCPSockWrapper: Writing " + bytes.length 
                + " bytes to write buff");
            Debug.log(node, "\t\tTCPSockWrapper: Write Buff State PRE-write = ");
            Debug.log(node, "\t\t\tTCPSockWrapper: Position: " + writeBuff.position());
            Debug.log(node, "\t\t\tTCPSockWrapper: Limit: " + writeBuff.limit());
            Debug.log(node, "\t\t\tTCPSockWrapper: Capacity: " + writeBuff.capacity());
            Debug.log(node, "\t\t\tTCPSockWrapper: First byte: " + writeBuff.array()[0]);
            writeBuff.put(bytes);
            Debug.log(node, "\t\tTCPSockWrapper: Write Buff State POST-write = ");
            Debug.log(node, "\t\t\tTCPSockWrapper: Position: " + writeBuff.position());
            Debug.log(node, "\t\t\tTCPSockWrapper: Limit: " + writeBuff.limit());
            Debug.log(node, "\t\t\tTCPSockWrapper: Capacity: " + writeBuff.capacity());
            Debug.log(node, "\t\t\tTCPSockWrapper: First byte: " + writeBuff.array()[0]);
        }catch(ReadOnlyBufferException robe){
            System.err.println("TCPSockWrapper: can't write to write buffer");
            robe.printStackTrace();
        }
    }

    /**
     * @param bytes Maximum number of bytes to read from read buffer
     * @return Bytes read
     */
    public byte [] readFromReadBuff(int numBytes){
        return readFromBuffer(numBytes, readBuff);
    }

    /**
     * @param numBytes Maximum number of bytes to read from write buffer
     * @return Bytes read
     */
    public byte[] readFromWriteBuff(int numBytes){
        return readFromBuffer(numBytes, writeBuff);
    }

    /**
     * Send contents of write buffer
     * down wire.
     * 
     * @return int -1 on failure, 0
     *          otherwise.
     */
    public int flushWriteBuff(){
        if(sendHelper == null){
            return -1;
        }else if(sendHelper.isFlushing()){
            return 0;
        }else{
            sendHelper.flush();
            return 0;
        }
    }

    /**
     * Handle an incoming Transport on this connection
     * 
     * @param transport The incoming Transport
     * @param from The incoming address
     */
    public void handleTransport(Transport transport, int from){
        String stateString = "";
        switch (this.state) {
            case CLOSED:
                stateString = "CLOSED";
                break;
            case LISTEN:
                stateString = "LISTEN";
                processIncomingRequest(transport, from);
                break;
            case SYN_SENT:
                stateString = "SYN_SENT";
                checkConnectionAcknowledgement(transport);
                break;
            case ESTABLISHED:
                stateString = "ESTABLISHED";
                processDataExchangeOrRetransmission(transport, from);
                break;
            case SHUTDOWN:
                stateString = "SHUTDOWN";
                break;
        }

        Debug.log(node, "TCPSockWrapper: Received transport while in state " + stateString);
        // if(sock.isClosed()){
        //     Debug.log(node, "TCPSockWrapper: Received transport but connection closed");
        //     // Ignore it
        // }else if(sock.isListening()){
        //     // TODO Add a new connection to queue
        //     processIncomingRequest(transport, from);
        //     Debug.log(node, "TCPSockWrapper: Received transport while listening");
        // }else if(sock.isConnectionPending()){
        //     Debug.log(node, "TCPSockWrapper: Received transport while waiting for connection ack.");
        //     checkConnectionAcknowledgement(transport);
        // }else if(sock.isConnected()){
        //     if(transport.getType() == Transport.DATA){
        //         Debug.log(node, "TCPSockWrapper: Received data packet");
        //         // Process data
        //     }else if(transport.getType() == Transport.SYN){
        //         // Didn't receive original
        //         Debug.log(node, "TCPSockWrapper: Client didn't get original acknowledgement, re-sending now...");
        //         sendConnectionAcknowledgement(from, transport.getSrcPort(), 
        //             node.getAddr(), transport.getDestPort());
        //     }
        // }else if(sock.isClosurePending()){
        //     Debug.log(node, "TCPSockWrapper: Received transport while trying to close");
        //     // TODO Figure out what this does/should do
        // }
    }

    /**
     * Set up a connection to destination port/IP.
     * The associated socket must be closed for this
     * to work
     *
     * Connection status is handled in the Socket itself,
     * so state is set upon returning.
     *
     * @param destAddr destination address to connect to
     * @param destPort destination port to connect to
     * @return int 0 on success, -1 otherwise
     */
    public int setupConnection(Integer destAddr, Integer destPort){
        if(!(sock.isClosed() || sock.isConnectionPending())){
            return -1; // may have already connected! (called from scheduled retry)
        }

        Debug.log(node, "TCPSockWrapper: Trying to connect to " + destAddr + ":" + destPort + "...");
        
        try{
            // Create a packet to initiate the connection
            Transport t = new Transport(sock.getLocalPort(), 
                destPort, Transport.SYN, -1, startSeq, new byte[0]); // use window size -1

            // Send the packet
            node.sendSegment(sock.getLocalAddress(), destAddr, 
                Protocol.TRANSPORT_PKT, t.pack());

            // Reflect state change
            this.state = State.SYN_SENT;

            // Make sure it keeps trying until success
            setupConnectionRetry(retryInterval, destAddr, destPort);
            return 0;
        }catch (IllegalArgumentException iae){
            System.err.println("TCPSockWrapper: Passed bad args to Transport constructor");
            iae.printStackTrace();
            return -1;
        }
    }

    /**
     * Accept a new connection, and return
     * an appropriate socket for it.
     *
     * @return A TCPSock from queue, or null 
     *         if queue empty or an error occurs
     */
    public TCPSock acceptConnection(){
        if(pendingConnections.isEmpty()){
            return null;
        }

        RequestTuple nextRequest = (RequestTuple) pendingConnections.remove();
        
        TCPSockWrapper newConnectionWrapper = new TCPSockWrapper(this.tcpMan, this.node, 
            TCPManager.DEFAULT_READ_BUFF_SIZE, 
            TCPManager.DEFAULT_WRITE_BUFF_SIZE,
            nextRequest.foreignAddress,
            nextRequest.foreignPort,
            sock.getLocalAddress(),
            sock.getLocalPort());

        int success = tcpMan.bind(newConnectionWrapper, nextRequest.localPort,
            nextRequest.foreignAddress, nextRequest.foreignPort);

        if(success == -1){
            Debug.log(node, "TCPSockWrapper: Tried to accept connection, but couldn't bind");
            return null;
        }

        newConnectionWrapper.setReceiveHelper();

        sendConnectionAcknowledgement(nextRequest);
        return newConnectionWrapper.getTCPSock();
    }

    /**
     * Return how many bytes are in
     * read buffer.
     */
    public int getReadBuffSize(){
        return readBuff.position();
    }

    /**
     * Return how many bytes are in
     * read buffer. NOT how many
     * can be safely written.
     */
    public int getWriteBuffSize(){
        return writeBuff.position();
    }

    /**
     * Return how many bytes
     * can be written to read buff.
     */
    public int getReadBuffSpaceRemaining(){
        return readBuff.limit() - readBuff.position();
    }

    /**
     * Return how many bytes
     * can be written to write buff.
     */
    public int getWriteBuffSpaceRemaining(){
        return writeBuff.limit() - writeBuff.position();
    }

    public TCPSock getTCPSock(){
        return this.sock;
    }

    public State getState(){
        return this.state;
    }

    public void setListening(int backlog){
        this.pendingConnections = new LinkedList<RequestTuple>();
        this.requestsBacklog = backlog;
        this.state = State.LISTEN;
    }

    public void setReceiveHelper(){
        this.receiveHelper = new AsyncReceiveHelper(this, node, tcpMan, startSeq);
    }


    /*
     * Private Methods
     */

    /**
     * Read from a buffer, and put it back in write mode
     * if necessary.
     */
    private byte [] readFromBuffer(int numBytes, ByteBuffer buff){
        Debug.log(node, "\t\tTCPSockWrapper: State PRE-read = ");
        Debug.log(node, "\t\t\tTCPSockWrapper: Position: " + buff.position());
        Debug.log(node, "\t\t\tTCPSockWrapper: Limit: " + buff.limit());
        Debug.log(node, "\t\t\tTCPSockWrapper: Capacity: " + buff.capacity());
        Debug.log(node, "\t\t\tTCPSockWrapper: First byte: " + buff.array()[0]);

        buff.flip(); // set the limit to current position, then position to 0
        try{
            if(numBytes >= buff.limit()){ // full read
                Debug.log(node, "\t\tTCPSockWrapper: Reading whole contents of buffer: " + buff.limit() 
                    + " (requested " + numBytes + ")");
                byte [] contents = new byte[buff.limit()];
                buff.get(contents);
                buff.clear();
                return contents;
            }else{ // partial read
                Debug.log(node, "\t\tTCPSockWrapper: Reading partial contents of buffer: " + numBytes);
                byte [] contents = new byte[numBytes];
                buff.get(contents);
                buff.compact();

                // reverse-flip to put in write mode
                //int lim = buff.limit();
                //buff.limit(buff.capacity());
                //buff.position(lim);
                return contents;
            }
        }catch(BufferUnderflowException bue){
            System.err.println("TCPSockWrapper: buffer underflowed (shouldn't happen)");
            bue.printStackTrace();
            return null;
        }finally{
            Debug.log(node, "\t\tTCPSockWrapper: State POST-read = ");
            Debug.log(node, "\t\t\tTCPSockWrapper: Position: " + buff.position());
            Debug.log(node, "\t\t\tTCPSockWrapper: Limit: " + buff.limit());
            Debug.log(node, "\t\t\tTCPSockWrapper: Capacity: " + buff.capacity());
            Debug.log(node, "\t\t\tTCPSockWrapper: First byte: " + buff.array()[0]);
        }
    }
    /**
     * Check in timeout seconds if this
     * connection has been made yet.
     *
     * If not, re-try connection.
     */
    private void setupConnectionRetry(long timeout, int destAddr, int destPort){
        Manager m = tcpMan.getManager();
        try {
            Method method = Callback.getMethod("setupConnection", this, new String [] {"java.lang.Integer", "java.lang.Integer"});
            Callback cb = new Callback(method, this, new Object [] {
                (Object) new Integer(destAddr), 
                (Object) new Integer(destPort)});

            m.addTimer(this.node.getAddr(), timeout, cb);
        }catch(Exception e) {
            System.err.println("TCPSockWrapper: Failed to add timer callback. Method Name: setupConnection" +
                 "\nException: " + e);
        }
    }

    /**
     * Check if this transport acknowledges the
     * pending connection was established.
     * 
     * If so, handle everything required.
     */
    private void checkConnectionAcknowledgement(Transport transport){
        if(!sock.isConnectionPending()){
            Debug.log(node, "TCPSockWrapper: Received acknowledgement, but connection not pending");
            return;
        }else if(transport.getType() != Transport.ACK){
            Debug.log(node, "TCPSockWrapper: Received a non-acknowledgement while connection pending");
            return;
        }else if(transport.getSeqNum() != startSeq){
            Debug.log(node, "TCPSockWrapper: Received acknowledgement, but the seq num was wrong");
            return;
        }

        Debug.log(node, "TCPSockWrapper: Received acknowledgement... connected!");

        this.sendHelper = new AsyncSendHelper(this, node, tcpMan, startSeq);
        this.state = State.ESTABLISHED;
    }

    /**
     * Add a new connection requeset to our backlog queue.
     * Returns 0 on success, else -1.
     *
     * Call accept() to send the acknowledgement.
     */
    private void processIncomingRequest(Transport transport, int from){
        RequestTuple newlyPendingConnection = new RequestTuple(from, transport.getSrcPort(), 
            sock.getLocalAddress(), sock.getLocalPort());

        if(pendingConnections.contains(newlyPendingConnection)){
            return; // duplicate request
        }else if(pendingConnections.size() < requestsBacklog){
            pendingConnections.add(newlyPendingConnection);
            Debug.log(node, "TCPSockWrapper: Added a new request from " + from + " to queue");
        }else{
            Debug.log(node, "TCPSockWrapper: Couldn't accept a new request from " + from + " to queue: queue full");
            if(state == State.ESTABLISHED)
                Debug.log(node, "\tState: ESTABLISHED");
            else if(state == State.SYN_SENT)
                Debug.log(node, "\tState: SYN_SENT");
            else if(state == State.LISTEN)
                Debug.log(node, "\tState: LISTEN");
        }
    }

    /**
     * Send an acknowledgement packet to a 
     * newly accepted connection. Because the
     * packet might not reach its destination,
     * multiple may get sent.
     *
     * Connection ACK's either come from accept
     * or from handleTransport(). The latter
     * case is when we are established, but
     * receive a SYN packet. This indicates
     * the original ACK was never received.
     *
     * @param req The node to acknowledge (as request four-tuple)
     */
    private void sendConnectionAcknowledgement(RequestTuple req){
        sendConnectionAcknowledgement(req.foreignAddress, req.foreignPort, req.localAddress, req.localPort);
    }

    private void sendConnectionAcknowledgement(int foreignAddress, int foreignPort, 
        int localAddress, int localPort){
        try{
            // Create an acknowledgement packet
            Transport t = new Transport(localPort, foreignPort, 
                Transport.ACK, -1, startSeq, new byte[0]); // use window size -1

            // Send the packet
            node.sendSegment(localAddress, foreignAddress, 
                Protocol.TRANSPORT_PKT, t.pack());

        }catch (IllegalArgumentException iae){
            System.err.println("TCPSockWrapper: Passed bad args to Transport constructor");
            iae.printStackTrace();
        }

    }

    /**
     * Handle an incoming data packet while 
     * a connection is established
     * 
     * This can either be called either
     * on client or server side.
     */
    private void processDataExchangeOrRetransmission(Transport transport, int from){

        switch (transport.getType()) {
            case Transport.DATA:
                Debug.log(node, "TCPSockWrapper: Server received data!");
                if(this.receiveHelper != null){
                    receiveHelper.processData(transport);
                }else{
                    Debug.log("\tTCPSockWrapper: However, no receive helper");
                }
                break;
            case Transport.SYN:
                Debug.log(node, "TCPSockWrapper: Client didn't get original acknowledgement, re-sending now...");
                sendConnectionAcknowledgement(from, transport.getSrcPort(), 
                    node.getAddr(), transport.getDestPort());
                break;
            case Transport.ACK:
                if(sendHelper != null){
                    sendHelper.checkAck(transport);
                }
                break;
        }

        // if(transport.getType() == Transport.DATA){
        //     // Process data
        // }else if(transport.getType() == Transport.SYN){
        //     // Didn't receive original
        //     Debug.log(node, "TCPSockWrapper: Client didn't get original acknowledgement, re-sending now...");
        //     sendConnectionAcknowledgement(from, transport.getSrcPort(), 
        //         node.getAddr(), transport.getDestPort());
        // }else if (Transport.get)
    }
}
