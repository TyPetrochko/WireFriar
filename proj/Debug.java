public final class Debug {
	public static boolean DEBUG = true;

	private Debug (){}

	public static void log(String msg){
		if(DEBUG){
			System.out.println(msg);
		}
	}
}