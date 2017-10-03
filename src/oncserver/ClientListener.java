package oncserver;

import java.util.EventListener;

public interface ClientListener extends EventListener 
{
	public void clientChanged(ClientEvent ce);
}
