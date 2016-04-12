import java.nio.*;

public class AsyncReceiveHelper {
	private final long retryInterval = 100; // how frequently we retry to connect while pending
    private final TCPManager tcpMan;
    private final Node node;
    private final TCPSockWrapper wrapper;

    private final int foreignAddress;
    private final int foreignPort;
    private final int localAddress;
    private final int localPort;

    private int highestSeqReceived;
    private int cwnd;
    private long timeout;

    private boolean isFlushing;

    public AsyncReceiveHelper(TCPSockWrapper wrapper, Node node, TCPManager tcpMan, int seq){
    	this.foreignAddress = wrapper.getTCPSock().getForeignAddress();
    	this.foreignPort = wrapper.getTCPSock().getForeignPort();
    	this.localAddress = wrapper.getTCPSock().getLocalAddress();
    	this.localPort = wrapper.getTCPSock().getLocalPort();

    	this.wrapper = wrapper;
    	this.node = node;
    	this.tcpMan = tcpMan;
    	this.highestSeqReceived = seq;
    	this.timeout = retryInterval;

    	this.isFlushing = false;

    	Debug.log(node, "AsyncReceiveHelper: Initializing a new receive helper");
    	Debug.log(node, "\tForeign address " + foreignAddress + ":" + foreignPort);
    	Debug.log(node, "\tLocal address " + localAddress + ":" + localPort);
    }

    public void processData(Transport t){
    	if(t.getType() == Transport.FIN){
            node.logOutput("time = " + tcpMan.getManager().now() + " msec");
            node.logOutput("\treceived FIN from " + wrapper.getTCPSock().getForeignAddress());
            processTermination();
            return;
        }else if(t.getSeqNum() != highestSeqReceived + 1){
            sendAck(highestSeqReceived);
            return;
        }

        // abort if not enough space remaining
        if(wrapper.getReadBuffSpaceRemaining() < t.getPayload().length){
            System.err.println("AsyncReceiveHelper: Buffer overwhelmed");
            Debug.log(node, "AsyncReceiveHelper: Buffer overwhelmed, " 
                + "dropping packet with sequence number " + t.getSeqNum());
            return;
        }

        highestSeqReceived += t.getPayload().length;

        // send ACK
    	try{
    		wrapper.writeToReadBuff(t.getPayload());
    		Debug.log(node, "AsyncReceiveHelper: Stored " + t.getPayload().length 
    			+ " bytes in read buffer");
    		sendAck(highestSeqReceived);
    	}catch (BufferOverflowException boe){
    		Debug.log(node, "AsyncReceiveHelper: Read buffer overflowed");
    		boe.printStackTrace();
    	}

    }

    public void sendAck(int seqToAcknowledge){
    	try{
    		// Make a transport to send the data
    		Transport t = new Transport(localPort, foreignPort, 
    			Transport.ACK, wrapper.getReadBuffSpaceRemaining(), seqToAcknowledge, 
                new byte[0]);

    		// Send the packet over the wire
    		node.sendSegment(localAddress, foreignAddress, 
    			Protocol.TRANSPORT_PKT, t.pack());

    	}catch(IllegalArgumentException iae){
            System.err.println("AsyncReceiveHelper: Shouldn't be here" 
            	+ " passed bad args to Transport constructor");
            System.err.println("ARGS:");
            System.err.println("\tFROM " + localAddress + ":" + localPort);
            System.err.println("\tTO " + foreignAddress + ":" + foreignPort);
            iae.printStackTrace();
            return;
        }
    }


    /* ###############################
     * ####### Private Methods #######
     * ###############################
     */

    private void processTermination(){
        wrapper.close();
    }
}