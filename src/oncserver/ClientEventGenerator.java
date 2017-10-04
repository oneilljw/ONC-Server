package oncserver;

import java.util.ArrayList;

public abstract class ClientEventGenerator 
{
	/***
	 * Provides listener registration for classes that fire client events.
	 */
	
	//List of registered listeners for Client change events
    protected ArrayList<ClientListener> listeners;
    
    /** Register a listener for database DataChange events */
    synchronized public void addClientListener(ClientListener l)
    {
    	if (listeners == null)
    		listeners = new ArrayList<ClientListener>();
    	listeners.add(l);
    }  

    /** Remove a listener for server DataChange */
    synchronized public void removeClientListener(ClientListener l)
    {
    	if (listeners == null)
    		listeners = new ArrayList<ClientListener>();
    	listeners.remove(l);
    }
    
    /** Fire a Data ChangedEvent to all registered listeners */
    @SuppressWarnings("unchecked")
	protected void fireClientChanged(Object source, ClientType type, ClientEventType eventType, Object eventObject)
    {
    	// if we have no listeners, do nothing...
    	if (listeners != null && !listeners.isEmpty())
    	{
    		// create the event object to send
    		ClientEvent event = new ClientEvent(source, type, eventType, eventObject);

    		// make a copy of the listener list in case anyone adds/removes listeners
    		ArrayList<ClientListener> targets;
    		synchronized (this) { targets = (ArrayList<ClientListener>) listeners.clone(); }

    		// walk through the cloned listener list and call the dataChanged method in each
    		for(ClientListener l:targets)
    			l.clientChanged(event);
    	}
    }
}
