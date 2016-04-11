
/**
 * A cancelable call-back method.
 * Unlike the ordinary callback,
 * these callbacks can be canceled
 * to prevent them from firing.
 *
 * Also contains a static collection
 * of all callbacks, to allow canceling
 * callbacks.
 */
import java.util.*;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

public class RetryCallback extends Callback {
	private static Map<Integer, RetryCallback> callbacks = new HashMap<Integer, RetryCallback>();

	private boolean canceled;

	private int seqNum;
    private long timeSent;
    private byte [] payload;

	public RetryCallback(Method method, Object obj, Object[] params, int seqNum, long timeSent, byte [] payload) {
		super(method, obj, params);
		
		this.canceled = false;
		this.seqNum = seqNum;
        this.timeSent = timeSent;
        this.payload = payload;

		callbacks.put(seqNum, this);
    }

    /**
     * Cancel all RetryCallback instances.
     * This can be used when a single 
     * callback wants to prevent all
     * other callbacks from double-firing
     * (in go-back-N).
     */
    public static void cancelAll(){
    	for(Integer key : callbacks.keySet()){
    		callbacks.get(key).cancel();
    	}
    }

    /**
     * Get the callback associated
     * with the passed sequence number.
     *
     * @param seqNum int The sequence
     * 		corresponding to the callback
     * 		to retrieve.
     *
     * @return RetryCallback The specified
     * 		callback.
     */
    public static RetryCallback getCallback(int seqNum){
    	if(!callbacks.containsKey(seqNum)){
    		return null;
    	}
    	return callbacks.get(seqNum);
    }

    /**
     * Cancel this callback, without
     * removing it from shared collection.
     */
    public void cancel(){
    	canceled = true;
    }

    /**
     * Cancel this callback, and remove it 
     * from shared callback collection.
     */
    public void cancelAndRemove(){
        canceled = true;
        callbacks.remove(seqNum);
    }

    /**
     * Get the payload associated with
     * this callback
     *
     * @return byte[] The associated payload.
     */
    public byte [] getPayload(){
        return payload;
    }

    /**
     * Get the send-time associated with
     * this callback
     *
     * @return long The associated send-time.
     */
    public long getTimeSent(){
        return timeSent;
    }

    @Override
    public void invoke() throws IllegalAccessException, InvocationTargetException {
    	if(!canceled){
			super.invoke();
		}
    }
}
