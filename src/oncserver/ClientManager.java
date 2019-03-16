package oncserver;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import javax.swing.ImageIcon;
import javax.swing.Timer;

import ourneighborschild.ONCServerUser;
import ourneighborschild.ONCUser;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

public class ClientManager extends ClientEventGenerator
{
	private static final boolean CLIENT_TIMER_ENABLED = true;
	private static final int CLIENT_HEARTBEAT_SAMPLE_RATE = 1000 * 60 * 1; //one minute
	private static final int DESKTOP_CLIENT_INACTIVE_LIMIT = 1000 * 60 * 3; //three minutes
	private static final int DESKTOP_CLIENT_TERMINAL_LIMIT = 1000 * 60 * 10; //ten minutes
	private static final int WEB_CLIENT_TERMINAL_LIMIT = 1000 * 60 * 20; //twenty minutes
	private static final String SESSION_ID_NAME = "SID=";
	
	private static ClientManager instance = null;
	
	private ArrayList<DesktopClient> dtClientAL;	//list of desktop clients connected to server
	private static ArrayList<WebClient> webClientAL;	//list of web clients connected to server
	private int clientID;	
	
	private Timer clientTimer;
	
	private ClientManager()
	{
		dtClientAL = new ArrayList<DesktopClient>();
		webClientAL = new ArrayList<WebClient>();
		clientID = 0;
		
		//Create the client timer and enable it if CLIENT_TIMER_ENABLED
		clientTimer = new Timer(CLIENT_HEARTBEAT_SAMPLE_RATE, new ClientTimerListener());
				if(CLIENT_TIMER_ENABLED)
					clientTimer.start();
	}
	
	public static ClientManager getInstance()
	{
		if(instance == null)
			instance = new ClientManager();
		
		return instance;
	}
	
	void clientDied(DesktopClient c)
	{
		c.closeClientSocket();
		dtClientAL.remove(c);
//		serverUI.displayDesktopClientTable(dtClientAL);
		fireClientChanged(this, ClientType.DESKTOP, ClientEventType.DIED, c);
	}
	
	void clientQuit(DesktopClient c)
	{
//		serverUI.addLogMessage(String.format("Client %d quit", c.getClientID()));
		fireClientChanged(this, ClientType.DESKTOP, ClientEventType.MESSAGE, String.format("Client %d quit", c.getClientID()));
		c.closeClientSocket();
		dtClientAL.remove(c);
//		serverUI.displayDesktopClientTable(dtClientAL);
		fireClientChanged(this, ClientType.DESKTOP, ClientEventType.LOGOUT, c);
	}
	
	void killClient(DesktopClient c)
	{
//		serverUI.addLogMessage(String.format("Client %d killed", c.getClientID()));
		fireClientChanged(this, ClientType.DESKTOP, ClientEventType.MESSAGE,String.format("Client %d killed", c.getClientID()));
		c.closeClientSocket();
		dtClientAL.remove(c);
//		serverUI.displayDesktopClientTable(dtClientAL);
		fireClientChanged(this, ClientType.DESKTOP, ClientEventType.KILLED, c);
	}
	
	void clientLoggedOut(DesktopClient c)
	{
//		serverUI.addLogMessage(String.format("Client %d, %s logged out", c.getClientID(),
//												c.getClientName()));
		fireClientChanged(this, ClientType.DESKTOP, ClientEventType.MESSAGE,String.format("Client %d, %s logged out", 
															c.getClientID(),c.getClientName()));
		c.closeClientSocket();
		dtClientAL.remove(c);
//		serverUI.displayDesktopClientTable(dtClientAL);
		fireClientChanged(this, ClientType.DESKTOP, ClientEventType.LOGOUT, c);
	}
	
	synchronized DesktopClient addDesktopClient(Socket socket)
	{
		DesktopClient c = new DesktopClient(socket, clientID);
		dtClientAL.add(c);
		fireClientChanged(this, ClientType.DESKTOP, ClientEventType.CONNECTED, c);
		c.start();
		clientID++;
		
		return c; 
	}
	
	synchronized WebClient addWebClient(HttpExchange t, ONCServerUser webUser)
	{
		WebClient wc = new WebClient(UUID.randomUUID(), webUser);
		webClientAL.add(wc);

//		serverUI.displayWebsiteClientTable(webClientAL);
		fireClientChanged(this, ClientType.WEB, ClientEventType.CONNECTED, wc);
//		serverUI.addLogMessage(String.format("Web Client connected, ip=%s, user= %s, sessionID=%s", 
//				 				t.getRemoteAddress().toString(), webUser.getLastName(), wc.getSessionID()));
		fireClientChanged(this, ClientType.WEB, ClientEventType.MESSAGE,String.format("Web Client connected, ip=%s, user= %s, sessionID=%s", 
 				t.getRemoteAddress().toString(), webUser.getLastName(), wc.getSessionID()));
		
		return wc; 
	}
	
	List<DesktopClient> getDesktopClientList() { return dtClientAL; }
	List<WebClient> getWebClientList() { return webClientAL; }
	
	/*************************************************************************************
	 * Find a desktop client by client id. If client id is not logged into the server, 
	 * return null,otherwise return a reference to the client.
	 * **********************************************************************************/
	DesktopClient findClient(long clientID)
	{
		//Search for client
		int index = 0;
		while(index < dtClientAL.size() && dtClientAL.get(index).getClientID() != clientID)
			index++;
			
		if(index < dtClientAL.size())
			return dtClientAL.get(index);
		else
			return null;	//client not found
	}
	
	/*************************************************************************************
	 * Find a web client by client token. If client token is not logged into the server, 
	 * return null,otherwise return a reference to the web client.
	 * **********************************************************************************/
	WebClient findClient(String sessionID)
	{
		//Search for client
		int index = 0;
		while(index < webClientAL.size() && !webClientAL.get(index).getSessionID().equals(sessionID))
			index++;
			
		if(index < webClientAL.size())
			return webClientAL.get(index);
		else
			return null;	//client not found
	}
	
	/********************************************************************************
	 * Find a web client by Cookie SID. If the Cookie isn't presented in the Headers
	 * parameter of the client is not logged into the server return null, otherwise 
	 * return a reference to the web client.
	 * *******************************************************************************/
	WebClient findAndValidateClient(Headers reqHeaders)
	{
		WebClient wc = null;
		
		if(reqHeaders.containsKey("Cookie"))
		{
			String sessionID = null;
			List<String> reqHeaderList = reqHeaders.get("Cookie");
			for(String s: reqHeaderList)
				if(s.startsWith(SESSION_ID_NAME))
					sessionID = s.substring(SESSION_ID_NAME.length()+1, s.length()-1);
			
			if(sessionID != null)
			{
				//Search for client
				int index = 0;
				while(index < webClientAL.size() && !webClientAL.get(index).getSessionID().equals(sessionID))
					index++;
				
				if(index < webClientAL.size())
				{
					wc = webClientAL.get(index);
					wc.updateTimestamp();
				}
			}
		}
		
		return wc;
	}
/*	
	static HtmlResponse getClientJSONP(WebClient wc, String callbackFunction)
	{		
		String response = "{"
						+ "\"firstname\":\"" + wc.getWebUser().getFirstName() + "\"," 
						+ "\"lastname\":\"" + wc.getWebUser().getLastName() + "\","
						+ "\"title\":\"" + wc.getWebUser().getTitle() + "\","
						+ "\"org\":\"" + wc.getWebUser().getOrganization() + "\","
						+ "\"email\":\"" + wc.getWebUser().getEmail() + "\","
						+ "\"phone\":\"" + wc.getWebUser().getCellPhone() + "\","
						+ "}";
			
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
*/	
	static HtmlResponse getClientJSONP(WebClient wc, String callbackFunction)
	{	
		String response;
		
		WebUser webUser = new WebUser(wc.getWebUser(), ServerGroupDB.getGroupList(wc.getWebUser()));
			
		Gson gson = new Gson();
		response = gson.toJson(webUser, WebUser.class);
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);
	}
	
	/***
	 * called to get user status from web server. If user status === UserStatus.Update_Profile
	 * the web user will see the profile dialog pop-up. Regardless of whether they make a change, 
	 * we should set their status to UserStatus.Active
	 * @param sessionID
	 * @param callbackFunction
	 * @return
	 */
	static HtmlResponse getUserStatusJSONP(String sessionID, String callbackFunction)
	{		
		String response;
			
		int index = 0;
		while(index < webClientAL.size() && !webClientAL.get(index).getSessionID().equals(sessionID))
			index++;
			
		if(index < webClientAL.size())
		{	
			webClientAL.get(index).updateTimestamp();
			response = "{"
						+ "\"userstatus\":\"" + webClientAL.get(index).getWebUser().getStatus().toString() + "\"," 
						+ "}";
		}
		else
			response = "";
			
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	
	/***
	 * called to get user status from web server. If user status === UserStatus.Update_Profile
	 * the web user will see the profile dialog pop-up. Regardless of whether they make a change, 
	 * we should set their status to UserStatus.Active
	 * @param sessionID
	 * @param callbackFunction
	 * @return
	 */
	static HtmlResponse getUserStatusJSONP(WebClient wc, String callbackFunction)
	{		
		String response;
			
		if(wc != null)
		{	
			response = "{"
						+ "\"userstatus\":\"" + wc.getWebUser().getStatus().toString() + "\"," 
						+ "}";
		}
		else
			response = "";
			
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	
	/*************************************************************************************
	 * Find a web client by sessionID. If client sessionID is not logged into the server, 
	 * return null,otherwise return a reference to the web client.
	 * **********************************************************************************/
	void logoutWebClient(WebClient wc)
	{
		int index = 0;
		while(index<webClientAL.size() && !webClientAL.get(index).getSessionID().equals(wc.getSessionID()))
			index++;
		
		if(index < webClientAL.size())
		{
			webClientAL.remove(index);
			fireClientChanged(this, ClientType.WEB, ClientEventType.LOGOUT, null);
		}
	}
	
	void clientLoginAttempt(boolean bValid, String mssg)
	{
//		serverUI.addLogMessage(mssg);
		fireClientChanged(this, ClientType.DESKTOP, ClientEventType.MESSAGE, mssg);
		
		if(bValid)	//redraw table, we now know who the client is
//			serverUI.displayDesktopClientTable(dtClientAL);
			fireClientChanged(this, ClientType.DESKTOP, ClientEventType.LOGIN, null);
	}
	
	void clientStateChanged(ClientType type, ClientEventType eventType, Object client)
	{
//		serverUI.displayDesktopClientTable(dtClientAL);
		fireClientChanged(this, type, eventType, client);
	}
	
	void addLogMessage(String mssg)
	{
//		serverUI.addLogMessage(mssg);
		fireClientChanged(this, ClientType.DESKTOP, ClientEventType.MESSAGE, mssg);
	}
	
	String getOnlineUsers()
	{
		List<ONCUser> userList= new ArrayList<ONCUser>();
		for(DesktopClient c:dtClientAL)
			userList.add(c.getClientUser());
		
		for(WebClient wc: webClientAL)
			userList.add(new ONCUser(wc.getWebUser()));
			
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCUser>>(){}.getType();
		
		String response = gson.toJson(userList, listtype);
		return "ONLINE_USERS" + response;		
	}
	
	void notifyAllInYearClients(int year, String mssg)
	{
		//send message to all clients connected in a particular year. 
		for(DesktopClient c:dtClientAL )
		{
			//Add change to the change queue's of every in year client
			if(c.getYear() == year)	
				c.addChange(mssg);
		}
	}
	
	void notifyAllInYearClients(int year, List<String> mssgList)
	{
		//send message to all clients connected in a particular year. 
		for(DesktopClient c:dtClientAL )
		{
			//Add change list to the change queue's of every in year client
			if(c.getYear() == year)
				for(String mssg : mssgList)
					c.addChange(mssg);
		}
	}
	
	void notifyAllClients(String mssg)
	{
		//send message to all clients connected in a particular year. 
		for(DesktopClient c:dtClientAL )
		{
			//Add change to the change queue's of every other client
			c.addChange(mssg);
		}
	}
	
	void notifyAllClients(List<String> mssgList)
	{
		//send message to all clients connected in a particular year. 
		for(DesktopClient c:dtClientAL )
		{
			//Add change list to the change queue's of every in year client
			for(String mssg : mssgList)
				c.addChange(mssg);
		}
	}
	
	void notifyAllOtherClients(DesktopClient requestingClient, String mssg)
	{
		//Add change to the change queue's of every other client
		for(DesktopClient c:dtClientAL ) 
			if(c != requestingClient )
				c.addChange(mssg);
	}
	
	int notifyClient(DesktopClient targetClient, String mssg)
	{
		//Search for client
		int index = 0;
		while(index < dtClientAL.size() &&! dtClientAL.get(index).equals(targetClient))
			index++;
		
		if(index < dtClientAL.size())
		{
			dtClientAL.get(index).addChange(mssg);
			return 0;
		}
		else
			return -1;	//client not found
	}

	void notifyAllOtherInYearClients(DesktopClient requestingClient, String change)
	{
		//Need to add change to all client changes lists so they can poll for the change
		for(DesktopClient c:dtClientAL )
		{
			//Add change to the change queue's of every other client that is using the same years data
			if(c != requestingClient  && c.getYear() == requestingClient.getYear())
				c.addChange(change);	
		}
	}
	
	void notifyAllOtherInYearClients(DesktopClient requestingClient, List<String> changeList)
	{
		//Need to add change to all client changes lists so they can poll for the change
		for(DesktopClient c:dtClientAL )
		{
			//Add change to the change queue's of every other client that is using the same years data
			if(c != requestingClient  && c.getYear() == requestingClient.getYear())
				for(String change : changeList)
					c.addChange(change);
		}
	}
	
	ImageIcon getAppIcon() 
	{ 
		return ServerUI.getInstance().getIcon(0); 
	}
	
	/***************************************************************************************
	 * This method checks all active clients to assess their heart beat according to the 
	 * heart beat state diagram. The method creates a list of all client heart beats that
	 * have gone terminal and are still lost, in order to kill the client and remove them 
	 * from the client manager queue 
	 */
	void checkClientHeartbeat()
	{
		//For each client that is supposed to have a heart beat (State is Logged_In or DB_Selected)
		//check that they still have a heart beat. If they don't have a heart beat, create a list
		//for notification and kill. For clients that are running but haven't logged in, if they
		//exceed the log in time, kill them
		ArrayList<DesktopClient> killClientList = new ArrayList<DesktopClient>();

		for(DesktopClient c: dtClientAL)	
		{
			ClientState clientState = c.getClientState();
			long timeSinceLastHeartbeat = System.currentTimeMillis() - c.getTimeLastActiveInMillis();
			
			if(clientState == ClientState.Running  && timeSinceLastHeartbeat > DESKTOP_CLIENT_INACTIVE_LIMIT)
			{
				killClientList.add(c);
			}
			else if(clientState == ClientState.Logged_In || clientState == ClientState.DB_Selected)
			{
				if(c.getClientHeartbeat() == Heartbeat.Terminal && timeSinceLastHeartbeat > DESKTOP_CLIENT_TERMINAL_LIMIT)
				{
					//Heart beat is terminal  and remained lost past the terminal time limit
					//kill the client by closing the socket which causes an IO exception which
					//will terminate the client thread
					killClientList.add(c);
				}
				else if(c.getClientHeartbeat() == Heartbeat.Lost && timeSinceLastHeartbeat > DESKTOP_CLIENT_TERMINAL_LIMIT)
				{
					//Heart beat was lost and remained lost past the terminal time limit
					c.setClientHeartbeat(Heartbeat.Terminal);
					fireClientChanged(this, ClientType.DESKTOP, ClientEventType.TERMINAL, c);
				}
				else if(c.getClientHeartbeat() == Heartbeat.Active && timeSinceLastHeartbeat > DESKTOP_CLIENT_INACTIVE_LIMIT)
				{
					//Heart beat was not detected
					c.setClientHeartbeat(Heartbeat.Lost);
					fireClientChanged(this, ClientType.DESKTOP, ClientEventType.LOST, c);		
				}
				else if((c.getClientHeartbeat() == Heartbeat.Lost || c.getClientHeartbeat() == Heartbeat.Terminal) && 
							timeSinceLastHeartbeat < DESKTOP_CLIENT_INACTIVE_LIMIT)
				{
					//Heart beat was lost and is still lost or went terminal and re-recovered prior to
					//killing the client
					c.setClientHeartbeat(Heartbeat.Active);
					fireClientChanged(this, ClientType.DESKTOP, ClientEventType.ACTIVE, c);		
				}
			}
		}
			
		//if there are any clients to kill, kill them here
		for(DesktopClient kc : killClientList)
		{
			kc.setClientState(ClientState.Ended);
			kc.closeClientSocket();
			clientDied(kc);
		}
		
		for(int index = webClientAL.size()-1; index >= 0; index--)
		{
			WebClient wc = webClientAL.get(index);
			long currentTime = Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis();
			if(currentTime - wc.getLastTimeStamp() > WEB_CLIENT_TERMINAL_LIMIT)
			{
				webClientAL.remove(index);
			}
		}
		
//		serverUI.displayWebsiteClientTable(webClientAL);
		fireClientChanged(this, ClientType.WEB, ClientEventType.KILLED, null);
	}
	
	private class ClientTimerListener implements ActionListener
	{
		@Override
		public void actionPerformed(ActionEvent arg0) 
		{
			checkClientHeartbeat();
		}
	}
}
