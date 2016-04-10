
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

	public RetryCallback(Method method, Object obj, Object[] params, int seqNum) {
		super(method, obj, params);
		
		this.canceled = false;
		this.seqNum = seqNum;
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
    		RetryCallback toCancel = callbacks.remove(key);
    		toCancel.cancel();
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
     * Cancel this callback.
     */
    public void cancel(){
    	canceled = true;
    	callbacks.remove(seqNum);
    }

    @Override
    public void invoke() throws IllegalAccessException, InvocationTargetException {
    	if(!canceled){
			super.invoke();
		}
    }
}

// class CallbackKey {
// 	public final int seqNum;
// 	public final byte [] payload;
// 	public CallbackKey(seqNum, payload){
// 		this.seqNum = seqNum;
// 		this.payload = payload;
// 	}

// 	@Override
//     public int hashCode(){
//     	int base = seqNum;
//         return base << 5 + base; // mult. by 33 quickly
//     }

//     @Override
//     public boolean equals(Object o){
//     	if (this == o)
//             return true;

//         if (!(o instanceof CallbackKey))
//             return false;

//         CallbackKey otherKey = (CallbackKey) o;

//         return (otherKey.seqNum == seqNum 
//         	&& otherKey.payload == payload);
//     }
// }