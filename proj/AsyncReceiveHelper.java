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
    }

    public void processData(Transport t){
    	if(t.getType() != Transport.DATA){
    		return;
    	}else if(t.getSeqNum() <= highestSeqReceived){
    		sendAck(t.getSeqNum()); // didn't get last ACK
    		return;
    	}else if(t.getSeqNum() == highestSeqReceived + 1){
    		highestSeqReceived++;
    	}else{
    		return;
    	}

    	try{
    		wrapper.writeToReadBuff(t.getPayload());
    		sendAck(t.getSeqNum());
    	}catch (BufferOverflowException boe){
    		Debug.log(node, "AsyncReceiveHelper: Read buffer overflowed");
    		boe.printStackTrace();
    	}

    }

    public void sendAck(int seqToAcknowledge){
    	try{
    		// Make a transport to send the data
    		Transport t = new Transport(localPort, foreignPort, 
    			Transport.ACK, -1, seqToAcknowledge, new byte[0]);

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