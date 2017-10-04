package oncserver;

import java.util.EventObject;

public class ClientEvent extends EventObject 
{
	/**
	 * Used to notify listeners that an event has occurred with a server desktop or web client. 
	 * Typical events are login, logout, state change, etc. See ClientEventTypes for a
	 * complete list.
	 */
	private static final long serialVersionUID = 1L;
	private ClientType type;	//Desktop or Web Client
	private ClientEventType eventType;
	private Object eventObject;
	
	public ClientEvent(Object source, ClientType type, ClientEventType eventType, Object eventObject) 
	{
		super(source);
		this.type = type;
		this.eventType = eventType;
		this.eventObject = eventObject;
	}

	ClientType getClientType() { return type; }
	ClientEventType getEventType() { return eventType; }
	Object getObject() { return eventObject; }
}
