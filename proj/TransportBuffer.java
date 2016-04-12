/**
 * A utility class for buffering un-acknowledged packets.
 * Note that it is the sender's responsibility for restarting
 * the timer when the callback fires. It will not automatically
 * do so.
 */
import java.lang.reflect.Method;
import java.util.*;

public class TransportBuffer {
	private final Queue<TransportWrapper> buffer; 
	private final Manager manager;
	private final Node node;

	private final Method method;
	private final Object obj;
	private final Object[] params;

	private CancelableCallback currentCallback;

	public TransportBuffer(Method method, Object obj, Object[] params, Manager manager, Node node){
		this.buffer = new LinkedList<TransportWrapper>();
		this.manager = manager;
		this.node = node;
		this.method = method;
		this.obj = obj;
		this.params = params;

		this.currentCallback = null;
	}

	/**
	 * Start the callback timer. When the timer reaches
	 * zero, the callback method will call with its 
	 * specified params.
	 *
	 * @param timeout The timer in milliseconds.
	 */
	public void startTimer(long timeout){
		if(currentCallback != null){
			currentCallback.cancel();
		}

		currentCallback = new CancelableCallback(method, obj, params);

		manager.addTimer(this.node.getAddr(), timeout, currentCallback);
	}

	/**
	 * Stop the callback timer.
	 * @see startTimer
	 */
	public void stopTimer(){
		if(currentCallback != null) {
			currentCallback.cancel();
		}
	}

	/**
	 * Examing the first Transport (wrapper) in the
	 * buffer, without removing it.
	 *
	 * @return The TransportWrapper at the start of the
	 * 		queue.
	 */
	public TransportWrapper peekTransport(){
		return (TransportWrapper) buffer.peek();
	}

	/**
	 * Examing the first Transport (wrapper) in the
	 * buffer and remove it.
	 *
	 * @return The TransportWrapper to be removed.
	 */
	public TransportWrapper pollTransport(){
		return (TransportWrapper) buffer.poll();
	}

	/**
	 * Add a Transport to buffer.
	 *
	 * @param transport The Transport to add to the 
	 * 		head of the queue.
	 * @param timeSent The time this Transport
	 * 		was sent.
	 */
	public void addTransport(Transport transport, long timeSent){
		if(transport == null){
			System.err.println("TransportBuffer: Gave a null transport");
			return;
		}

		buffer.add(new TransportWrapper(transport, timeSent));
	}

	/**
	 * Retreive the backing buffer of Transport
	 * Wrappers.
	 *
	 * @return The TransportWrapper buffer
	 */
	public Queue<TransportWrapper> getAllTransports(){
		return buffer;
	}
}