package oncserver;

import java.util.EventObject;

public class SignUpEvent extends EventObject
{
	/**
	 * Used to notify listeners that an event has occurred with the SignUp API interface 
	 */
	private static final long serialVersionUID = 1L;
	private SignUpEventType eventType;
	private Object eventObject;
	
	public SignUpEvent(Object source, SignUpEventType eventType, Object eventObject) 
	{
		super(source);
		this.eventType = eventType;
		this.eventObject = eventObject;
	}

	SignUpEventType type() { return eventType; }
	Object getSignUpObject() { return eventObject; }
}
