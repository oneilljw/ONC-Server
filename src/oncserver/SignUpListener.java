package oncserver;

import java.util.EventListener;

public interface SignUpListener extends EventListener
{
	public void signUpDataReceived(SignUpEvent event);
}
