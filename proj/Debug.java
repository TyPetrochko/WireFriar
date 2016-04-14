/**
 * A utility debug class
 */
public final class Debug {
	public static boolean DEBUG = false;
	public static boolean TRACE = true;
	public static boolean STATISTICS = true;
	
	private static boolean tracing = false;

	private Debug (){}

	/** 
	 * Log if debug enabled
	 *
	 * @param msg  The message to print out
	 */
	public static void log(String msg){
		
		if(DEBUG){
			if(tracing){
				System.out.print("\n");
				tracing = false;
			}
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
			if(tracing){
				System.out.print("\n");
				tracing = false;
			}
			System.out.println("\t" + node.getAddr() + ": " + msg);
		}
	}


	/** 
	 * Log to node if statistics enabled
	 *
	 * @param node The node to send debug signal to
	 * @param msg  The message to print out
	 */
	public static void stat(Node node, String msg){
		if(STATISTICS){
			if(tracing){
				System.out.print("\n");
				tracing = false;
			}
			System.out.println(node.getAddr() + ": " + msg);
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
			tracing = true;
			System.out.print(s);
		}
	}
}
