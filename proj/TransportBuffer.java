/**
 * A utility class for buffering un-acknowledged packets.
 * Note that it is the sender's responsibility for restarting
 * the timer when the callback fires. It will not automatically
 * do so.
 */

import java.lang.reflect.Method;
import java.util.*;

public class TransportBuffer {
	private final Queue<Transport> buffer; 
	private final Manager manager;
	private final Node node;

	private final Method method;
	private final Object obj;
	private final Object[] params;

	private CancelableCallback currentCallback;

	public TransportBuffer(Method method, Object obj, Object[] params, Manager manager, Node node){
		this.buffer = new LinkedList<Transport>();
		this.manager = manager;
		this.node = node;
		this.method = method;
		this.obj = obj;
		this.params = params;

		this.currentCallback = null;
	}

	public void startTimer(long timeout){
		if(currentCallback != null){
			currentCallback.cancel();
		}

		currentCallback = new CancelableCallback(method, obj, params);

		manager.addTimer(this.node.getAddr(), timeout, currentCallback);
	}

	public void stopTimer(){
		if(currentCallback != null) {
			currentCallback.cancel();
		}
	}

	public Transport peekTransport(){
		return (Transport) buffer.peek();
	}

	public Transport pollTransport(){
		return (Transport) buffer.poll();
	}

	public void addTransport(Transport transport){
		if(transport == null){
			System.err.println("TransportBuffer: Gave a null transport");
			return;
		}

		buffer.add(transport);
	}

	public Queue<Transport> getAllTransports(){
		return buffer;
	}
}