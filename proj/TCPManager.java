/**
 * <p>Title: CPSC 433/533 Programming Assignment</p>
 *
 * <p>Description: Fishnet TCP manager</p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: Yale University</p>
 *
 * @author Hao Wang
 * @version 1.0
 */
import java.util.*;
import java.nio.*;

public class TCPManager {
    private Node node;
    private int addr;
    private Manager manager;
    private Map<RequestTuple, TCPSockWrapper> sockets;

    public static int DEFAULT_READ_BUFF_SIZE = 87380;
    public static int DEFAULT_WRITE_BUFF_SIZE = 16384;

    private static final byte dummy[] = new byte[0];

    public TCPManager(Node node, int addr, Manager manager) {
        this.node = node;
        this.addr = addr;
        this.manager = manager;
        this.sockets = new HashMap<RequestTuple, TCPSockWrapper>();
    }

    /**
     * Start this TCP manager
     */
    public void start() {
        // nothing to do yet
    }

    public int getAddress(){
        return this.addr;
    }

    public Node getNode(){
        return this.node;
    }

    public Manager getManager(){
        return this.manager;
    }

    public void receivePacket(int from, Packet packet){
        /*
         * TODO:
         *      -> Phase 1: assume zero network lossage
         *      -> Phase 2: stop-and-wait per single packet
         *      -> Phase 3: go-back-n transmission
         */

        Transport transport = Transport.unpack(packet.getPayload());
        RequestTuple key = new RequestTuple(from, transport.getSrcPort(), packet.getDest(), transport.getDestPort());
        RequestTuple wildCardKey = new RequestTuple(-1, -1, packet.getDest(), transport.getDestPort());

        TCPSockWrapper match = null;
        if (sockets.containsKey(key)) {
            match = (TCPSockWrapper) sockets.get(key);
        }else if(sockets.containsKey(wildCardKey)){
            match = (TCPSockWrapper) sockets.get(wildCardKey);
        }

        if(match != null){
            Debug.log(node, "TCPManager: Received a packet from " + from 
                + " to " + key.localAddress + ":" + key.localPort);
            match.handleTransport(transport, from);
        }
    }

    /*
     * Begin socket API
     */

    /**
     * Create a socket
     *
     * @return TCPSock the newly created socket, which is not yet bound to
     *                 a local port
     */
    public TCPSock socket() {
        return new TCPSockWrapper(this, node, DEFAULT_READ_BUFF_SIZE, DEFAULT_WRITE_BUFF_SIZE).getTCPSock();
    }
    
    /**
     * Bind a socket
     *
     * @param sockWrapper The socket to bind
     * @param port local port to bind to
     * @return int 0 on success, -1 otherwise
     */
    public int bind(TCPSockWrapper sockWrapper, int port){
        return bind(sockWrapper, port, -1, -1);
    }

    /**
     * Bind a socket
     *
     * @param sockWrapper The socket to bind
     * @param localPort Local port to bind to
     * @param foreignAddress Foreign address to bind to
     * @param foreignPort Foreign port to bind to
     * @return int 0 on success, -1 otherwise
     */
    public int bind(TCPSockWrapper sockWrapper, int localPort, int foreignAddress, int foreignPort){
        RequestTuple rt = new RequestTuple(foreignAddress, foreignPort, addr, localPort);
        
        if(sockets.containsKey(rt)){
            Debug.log(node, "TCPManager: could not bind a socket to port " 
                + localPort + " (another socket already bound to port)");
            return -1;
        }else if (sockets.containsValue(sockWrapper)){
            Debug.log(node, "TCPManager: could not bind a socket to port " 
                + localPort + " (this socket already bound to port" + sockWrapper.getTCPSock().getLocalPort() + ")");
            return -1;
        }else{
            sockets.put(rt, sockWrapper);
            Debug.log(node, "TCPManager: bound a socket to port " + localPort);
            return 0;
        }
    }

    /**
     * Update a socket entry to a new key.
     * @param oldKey The key of the old entry
     * @param newKey The key of the new entry
     * @return int 0 on success, otherwise -1
     */
    public int updateSocketEntry(RequestTuple oldKey, RequestTuple newKey){
        if(!sockets.containsKey(oldKey)){
            return -1;
        }

        TCPSockWrapper entry = (TCPSockWrapper) sockets.remove(oldKey);
        sockets.put(newKey, entry);
        return 0;
    }
    
    /**
     * Check if a socket is bound (we have it in our socket Map)
     *
     * @param The socket to check
     * @return true if bound, else false
     */
    public boolean hasSocketWrapper(TCPSockWrapper wrapper){
        return sockets.containsValue(wrapper);
    }

    /*
     * End Socket API
     */
}
