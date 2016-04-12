/**
 * A callback that allows canceling, for
 * timer-related purposes.
 */
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

public class CancelableCallback extends Callback{
	private boolean canceled;
	
	public CancelableCallback(Method method, Object obj, Object[] params){
		super(method, obj, params);
		
		canceled = false;
	}

	/**
	 * Cancel this callback
	 */
	public void cancel(){
		canceled = true;
	}

	/**
	 * Invoke this callback
	 */
	@Override
    public void invoke() throws IllegalAccessException, InvocationTargetException {
    	if(!canceled){
			super.invoke();
		}
    }

}