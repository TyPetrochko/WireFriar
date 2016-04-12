/**
 * A basic wrapper for buffering a transport.
 * Includes the time sent for RTT tracking.
 */
public class TransportWrapper {
	private Transport transport;
	private long timeSent;

	public TransportWrapper(Transport transport, long timeSent){
		this.transport = transport;
		this.timeSent = timeSent;
	}

	/**
	 * Get the time this transport was sent.
	 *
	 * @return The time in milliseconds
	 */
	public long getTimeSent(){
		return timeSent;
	}

	/**
	 * Get the transport associated with this
	 * wrapper.
	 *
	 * @return The associated transport.
	 */
	public Transport getTransport(){
		return transport;
	}

	/**
	 * Re-set the time this transport was sent.
	 *
	 * @param timeSent The time this transport 
	 * 		was sent
	 */
	public void setTimeSent(long timeSent){
		this.timeSent = timeSent;
	}
}
