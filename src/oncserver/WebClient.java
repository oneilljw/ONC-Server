package oncserver;

import java.util.Calendar;
import java.util.UUID;

public class WebClient
{
	private ClientState state;
	private UUID sessionID;
	private Calendar timestamp;
	
	WebClient(UUID sessionID)
	{
		this.state = ClientState.Connected;
		this.sessionID = sessionID;
		timestamp = Calendar.getInstance();
	}
	
	//getters
	ClientState getClientState() { return state; }
	UUID getSessionID() { return sessionID; }
	Calendar getTimeStamp() { return timestamp; }
	
	//setters
	void setClientState(ClientState state) {this.state = state; }
}
