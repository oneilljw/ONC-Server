package oncserver;

import java.util.EventObject;

public class ClientEvent extends EventObject 
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private ClientType type;
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
	Object getObject1() { return eventObject; }
}
