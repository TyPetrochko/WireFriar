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

public class TCPManager {
    private Node node;
    private int addr;
    private Manager manager;
    private Map<RequestTuple, TCPSock> sockets;

    private static final byte dummy[] = new byte[0];

    public TCPManager(Node node, int addr, Manager manager) {
        this.node = node;
        this.addr = addr;
        this.manager = manager;
        this.sockets = new HashMap<RequestTuple, TCPSock>();
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

    public void receivePacket(int from, Packet packet){
        Debug.log("TCPManager: Received a packet from " + from);

        Transport transport = Transport.unpack(packet);
        RequestTuple match = new RequestTuple(from, transport.srcPort, packet.dest, transport.destPort);

        if (sockets.containsKey(match)) {
            // Todo continue here!
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
        return new TCPSock(this);
    }
    /**
     * Bind a socket
     *
     * @param The socket to bind, and local port to bind to
     * @return int 0 on success, -1 otherwise
     */
    public int bind(TCPSock sock, int port){
        RequestTuple rt = new RequestTuple(-1, -1, addr, port);
        
        if(sockets.containsKey(rt)){
            Debug.log("TCPManager: could not bind a socket to port " 
                + port + " (another socket already bound to port)");
            return -1;
        }else if (sockets.containsValue(sock)){
            Debug.log("TCPManager: could not bind a socket to port " 
                + port + " (this socket already bound to port" + sock.getLocalPort() + ")");
            return -1;
        }else{
            sockets.put(rt, sock);
            Debug.log("TCPManager: bound a socket to port " + port);
            return 0;
        }
    }
    
    /**
     * Check if a socket is bound (we have it in our socket Map)
     *
     * @param The socket to check
     * @return true if bound, else false
     */
    public boolean hasSocket(TCPSock sock){
        return sockets.containsValue(sock);
    }

    /*
     * End Socket API
     */
}

/**
 * A generic hash key for storing request tuples.
 *
 * To implement wildcards, set the foreign port and
 * address equal to -1
 */ 
class RequestTuple {
    public final int foreignAddress;
    public final int foreignPort;
    public final int localAddress;
    public final int localPort;

    public RequestTuple(int foreignAddress, int foreignPort, int localAddress, int localPort){
        this.foreignAddress = foreignAddress;
        this.foreignPort = foreignPort;
        this.localAddress = localAddress;
        this.localPort = localPort;
    }

    @Override
    public boolean equals(Object o){
        if (this == o)
            return true;

        if (!(o instanceof RequestTuple))
            return false;

        RequestTuple rt = (RequestTuple) o;
        
        boolean match = rt.foreignAddress == this.foreignAddress
            && rt.foreignPort == this.foreignPort
            && rt.localAddress == this.localAddress
            && rt.localPort == this.localPort;

        return match;
    }

    @Override
    public int hashCode(){
        int base = (localAddress + 2) * (localAddress + 2) * (foreignAddress + 2) * (foreignPort + 2);
        return base << 5 + base; // mult. by 33 quickly
    }
}