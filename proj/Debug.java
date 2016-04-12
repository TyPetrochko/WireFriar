public final class Debug {
	public static boolean DEBUG = true;

	private Debug (){}

	public static void log(String msg){
		if(DEBUG){
			System.out.println("\t" + msg);
		}
	}

	public static void log(Node node, String msg){
		if(DEBUG){
			System.out.println("\t" + node.getAddr() + ": " + msg);
		}
	}

	public static void verifyPacket(Node node, Transport t){
		byte expected = (byte) ((t.getSeqNum() - 2) * 107);
		byte actual = t.getPayload()[0];
		boolean correct = (actual == expected);

		if(!correct){
			log(node, "PACKET NUMBER " + t.getSeqNum() + " IS CORRUPT");
			log(node, "\tExpected: " + expected);
			log(node, "\tReceived: " + actual);
		}
	}
}