package oncserver;

import java.util.Date;
import java.util.UUID;

import ourneighborschild.ONCServerUser;

public class WebClient
{
	private ClientState state;
	private UUID sessionID;
	private long loginTimestamp;
	private long lastTimestamp;
	private ONCServerUser webUser;
	
	WebClient(UUID sessionID, ONCServerUser webUser)
	{
		this.state = ClientState.Connected;
		this.sessionID = sessionID;
		this.loginTimestamp = new Date().getTime();
		this.lastTimestamp = loginTimestamp;
		this.webUser = webUser;
	}
	
	//getters
	ClientState getClientState() { return state; }
	String getSessionID() { return sessionID.toString(); }
	long getloginTimeStamp() { return loginTimestamp; }
	long getLastTimeStamp() { return lastTimestamp; }
	ONCServerUser getWebUser() { return webUser; }
	
	//setters
	void setClientState(ClientState state) {this.state = state; }
	void updateTimestamp() {this.lastTimestamp = new Date().getTime(); }
}
