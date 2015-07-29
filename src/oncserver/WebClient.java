package oncserver;

import java.util.Calendar;
import java.util.UUID;

import ourneighborschild.ONCUser;

public class WebClient
{
	private ClientState state;
	private UUID sessionID;
	private Calendar timestamp;
	private ONCUser webUser;
	
	WebClient(UUID sessionID, ONCUser webUser)
	{
		this.state = ClientState.Connected;
		this.sessionID = sessionID;
		timestamp = Calendar.getInstance();
		this.webUser = webUser;
	}
	
	//getters
	ClientState getClientState() { return state; }
	String getSessionID() { return sessionID.toString(); }
	Calendar getTimeStamp() { return timestamp; }
	ONCUser getWebUser() { return webUser; }
	
	//setters
	void setClientState(ClientState state) {this.state = state; }
}
