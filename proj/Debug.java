/**
 * A utility debug class
 */
public final class Debug {
	public static boolean DEBUG = false;
	public static boolean TRACE = true;

	private Debug (){}

	/** 
	 * Log if debug enabled
	 *
	 * @param msg  The message to print out
	 */
	public static void log(String msg){
		if(DEBUG){
			System.out.println("\t" + msg);
		}
	}

	/** 
	 * Log to node if debug enabled
	 *
	 * @param node The node to send debug signal to
	 * @param msg  The message to print out
	 */
	public static void log(Node node, String msg){
		if(DEBUG){
			System.out.println("\t" + node.getAddr() + ": " + msg);
		}
	}

	/**
	 * Trace a single character if tracing
	 * is enabled.
	 *
	 * @param s The character to print, represented
	 * 			as a string.
	 */
	public static void trace(String s){
		if(TRACE){
			System.out.print(s);
		}
	}
}
