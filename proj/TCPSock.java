/**
 * <p>Title: CPSC 433/533 Programming Assignment</p>
 *
 * <p>Description: Fishnet socket implementation</p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: Yale University</p>
 *
 * @author Hao Wang
 * @version 1.0
 */

public class TCPSock {
    private TCPManager tcpMan;
    private TCPSockWrapper wrapper;
    private int foreignAddress;
    private int foreignPort;
    private int localAddress;
    private int localPort;

    public TCPSock(TCPManager tcpMan, TCPSockWrapper wrapper) {
        this.tcpMan = tcpMan;
        this.wrapper = wrapper;
        this.foreignAddress = -1;
        this.foreignPort = -1;
        this.localAddress = -1;
        this.localPort = -1;
    }

    /*
     * The following are the socket APIs of TCP transport service.
     * All APIs are NON-BLOCKING.
     */

    /**
     * Bind a socket to a local port
     *
     * @param localPort int local port number to bind the socket to
     * @return int 0 on success, -1 otherwise
     */
    public int bind(int localPort) {
        if(tcpMan.bind(this.wrapper, localPort) == 0){
            localAddress = tcpMan.getAddress();
            this.localPort = localPort;
            return 0;
        }else{
            return -1;
        }
    }

    /**
     * Listen for connections on a socket
     * @param backlog int Maximum number of pending connections
     * @return int 0 on success, -1 otherwise
     */
    public int listen(int backlog) {
        if(!isBound()){
            Debug.log("TCPSock: Could not listen (not bound)");
            return -1;
        }else{
            this.wrapper.setListening();
            Debug.log("TCPSock: Socket now listening on port " + localPort);
            return 0;
        }
    }

    /**
     * Accept a connection on a socket
     *
     * @return TCPSock The first established connection on the request queue
     */
    public TCPSock accept() {
        return null;
    }

    public boolean isBound(){
        return tcpMan.hasSocketWrapper(this.wrapper);
    }

    public boolean isListening(){
        return (wrapper.getState() == TCPSockWrapper.State.LISTEN);
    }

    public boolean isConnectionPending() {
        return (wrapper.getState() == TCPSockWrapper.State.SYN_SENT);
    }

    public boolean isClosed() {
        return (wrapper.getState() == TCPSockWrapper.State.CLOSED);
    }

    public boolean isConnected() {
        return (wrapper.getState() == TCPSockWrapper.State.ESTABLISHED);
    }

    public boolean isClosurePending() {
        return (wrapper.getState() == TCPSockWrapper.State.SHUTDOWN);
    }

    public int getForeignAddress(){
        return foreignAddress;
    }

    public int getForeignPort(){
        return foreignPort;
    }

    public int getLocalAddress(){
        return localAddress;
    }

    public int getLocalPort(){
        return localPort;
    }
    /**
     * Initiate connection to a remote socket
     *
     * @param destAddr int Destination node address
     * @param destPort int Destination port
     * @return int 0 on success, -1 otherwise
     */
    public int connect(int destAddr, int destPort) {
        foreignAddress = destAddr;
        foreignPort = destPort;

        RequestTuple oldKey = new RequestTuple(-1, -1, localAddress, localPort);
        RequestTuple newKey = new RequestTuple(destAddr, destPort, localAddress, localPort);

        if(tcpMan.updateSocketEntry(oldKey, newKey) == -1){
            return -1;
        }else{
            return wrapper.setupConnection(destAddr, destPort);
        }
    }

    /**
     * Initiate closure of a connection (graceful shutdown)
     */
    public void close() {
    }

    /**
     * Release a connection immediately (abortive shutdown)
     */
    public void release() {
    }

    /**
     * Write to the socket up to len bytes from the buffer buf starting at
     * position pos.
     *
     * @param buf byte[] the buffer to write from
     * @param pos int starting position in buffer
     * @param len int number of bytes to write
     * @return int on success, the number of bytes written, which may be smaller
     *             than len; on failure, -1
     */
    public int write(byte[] buf, int pos, int len) {
        return -1;
    }

    /**
     * Read from the socket up to len bytes into the buffer buf starting at
     * position pos.
     *
     * @param buf byte[] the buffer
     * @param pos int starting position in buffer
     * @param len int number of bytes to read
     * @return int on success, the number of bytes read, which may be smaller
     *             than len; on failure, -1
     */
    public int read(byte[] buf, int pos, int len) {
        int readBuffSize = wrapper.getReadBuffSize();
        int destBuffSize = buf.length - pos;
        int numBytesToRead = 0;

        // Determine which of the three limits the num. of bytes to read
        if (readBuffSize <= destBuffSize && readBuffSize <= len) {
            numBytesToRead = readBuffSize;    
        }else if(destBuffSize <= readBuffSize && destBuffSize <= len){
            numBytesToRead = destBuffSize;
        }else if(len <= readBuffSize && len <= destBuffSize){
            numBytesToRead = len;
        }

        // copy the read bytes to the provided buffer (not efficient)
        byte [] bytesRead = wrapper.readFromReadBuff(numBytesToRead);
        System.arraycopy(bytesRead, 0, buf, pos, numBytesToRead);
        return bytesRead.length;
    }

    /*
     * End of socket API
     */
}
