package oncserver;

import java.util.ArrayList;

/****
 * Base class for the integration with SignUp Genius to import a list of sign-ups and to import
 * specific sign-ups. This class provides the infrastructure for SignUp Event notification
 * @author johnoneil
 *
 */
public abstract class SignUpGeniusImporter
{
	//List of registered listeners for Client change events
	protected ArrayList<SignUpListener> listeners;
   
	/** Register a listener for database DataChange events */
	synchronized public void addSignUpListener(SignUpListener l)
	{
		if (listeners == null)
   		listeners = new ArrayList<SignUpListener>();
   		listeners.add(l);
	}  

	/** Remove a listener for server DataChange */
	synchronized public void removeSignUpListener(SignUpListener l)
	{
   		if (listeners == null)
   			listeners = new ArrayList<SignUpListener>();
   		listeners.remove(l);
	}
   
	/** Fire a Data ChangedEvent to all registered listeners */
	@SuppressWarnings("unchecked")
	void fireSignUpDataChanged(Object source, SignUpEventType eventType, Object eventObject)
	{
   		// if we have no listeners, do nothing...
   		if(listeners != null && !listeners.isEmpty())
   		{
   			// create the event object to send
   			SignUpEvent event = new SignUpEvent(source, eventType, eventObject);

   			// make a copy of the listener list in case anyone adds/removes listeners
   			ArrayList<SignUpListener> targets;
   			synchronized (this) { targets = (ArrayList<SignUpListener>) listeners.clone(); }

   			// walk through the cloned listener list and call the dataRecevied method in each
   			for(SignUpListener l:targets)
   				l.signUpDataReceived(event);
   		}
    }
}
