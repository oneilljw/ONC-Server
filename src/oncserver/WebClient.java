package oncserver;

import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import ourneighborschild.ONCUser;

public class WebClient
{
	private ClientState state;
	private UUID sessionID;
	private long loginTimestamp;
	private long lastTimestamp;
	private ONCUser webUser;
	
	WebClient(UUID sessionID, ONCUser webUser)
	{
		this.state = ClientState.Connected;
		this.sessionID = sessionID;
		loginTimestamp = new Date().getTime();
		lastTimestamp = loginTimestamp;
		this.webUser = webUser;
	}
	
	//getters
	ClientState getClientState() { return state; }
	String getSessionID() { return sessionID.toString(); }
	long getloginTimeStamp() { return loginTimestamp; }
	long getLastTimeStamp() { return lastTimestamp; }
	ONCUser getWebUser() { return webUser; }
	
	//setters
	void setClientState(ClientState state) {this.state = state; }
	void updateTimestamp() {this.lastTimestamp = new Date().getTime(); }
}
