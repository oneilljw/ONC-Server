package oncserver;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import ourneighborschild.Agent;
import ourneighborschild.ChangePasswordRequest;
import ourneighborschild.ONCObject;
import ourneighborschild.ONCServerUser;
import ourneighborschild.ONCUser;
import ourneighborschild.UserAccess;
import ourneighborschild.UserPreferences;
import ourneighborschild.UserStatus;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerUserDB extends ServerPermanentDB
{
	private static final int USER_RECORD_LENGTH = 23;
	private static final String USER_PASSWORD_PREFIX = "onc";
	private static final String USERDB_FILENAME = "/users.csv";
	private static ServerUserDB instance  = null;
	
	private static ClientManager clientMgr;
	private static ServerAgentDB serverAgentDB;
	
	private static List<ONCServerUser> userAL;
//	private int nextID;
	
	private ServerUserDB() throws NumberFormatException, IOException
	{
		clientMgr = ClientManager.getInstance();
		serverAgentDB = ServerAgentDB.getInstance();
		
		userAL = new ArrayList<ONCServerUser>();
//		System.out.println(String.format("ServerUserDB filename: %s", System.getProperty("user.dir") + USERDB_FILENAME));
		importDB(String.format("%s/PermanentDB%s", System.getProperty("user.dir"), USERDB_FILENAME), "User DB", USER_RECORD_LENGTH);
		nextID = getNextID(userAL);
		bSaveRequired = false;
	}
	
	public static ServerUserDB getInstance() throws NumberFormatException, IOException
	{
		if(instance == null)
			instance = new ServerUserDB();
		
		return instance;
	}
	
	@Override
	String add(String json)
	{
		Gson gson = new Gson();
		ONCServerUser su = gson.fromJson(json, ONCServerUser.class);
		
		su.setID(nextID++);	//Set id for new user
		su.setUserPW("onc" + su.getPermission().toString());
		su.setStatus(UserStatus.Change_PW);
		
		//If the user has web site access, ask the Agent DB to add the agent if there not already there.
		//Set the agentID field to the agentID. If not web site access, set the agentID to -1;
		if(su.getAccess() == UserAccess.Website || su.getAccess() == UserAccess.AppAndWebsite)
			su.setAgentID(serverAgentDB.checkForAgent(su));
		else
			su.setAgentID(-1);
		
		userAL.add(su); //Add new user to data base
		bSaveRequired = true;
//		save();
		
		return "ADDED_USER" + gson.toJson(su.getUserFromServerUser(), ONCUser.class) ;
	}
	
	//update from desktop client
	String update(int year, String json)	//User DB currently not implemented by year
	{
		Gson gson = new Gson();
		ONCUser updatedUser = gson.fromJson(json, ONCUser.class);
		
		//Find the user by id and replace the old user object
		int index = 0;
		while(index < userAL.size() && userAL.get(index).getID() != updatedUser.getID())
			index++;
				
		if(index < userAL.size())
		{
			ONCServerUser su = userAL.get(index);
			
			//check to see if user is an agent and the agent profile has changed before updating
			//the server user
			boolean bAgentProfileChanged = su.getAgentID() > -1 && 
				(!su.getLastname().equals(updatedUser.getLastname()) ||
				  !su.getFirstname().equals(updatedUser.getFirstname()) ||
				   !su.getOrg().equals(updatedUser.getOrg()) ||
				    !su.getTitle().equals(updatedUser.getTitle()) ||
				     !su.getPhone().equals(updatedUser.getPhone()) ||
				      !su.getEmail().equals(updatedUser.getEmail()));
			
			//update the server user
			su.setLastname(updatedUser.getLastname());
			su.setFirstname(updatedUser.getFirstname());
			su.setStatus(updatedUser.getStatus());
			su.setPermission(updatedUser.getPermission());
			su.setOrg(updatedUser.getOrg());
			su.setTitle(updatedUser.getTitle());
			su.setPhone(updatedUser.getPhone());
			
			if(!su.getEmail().equals(updatedUser.getEmail()))
				updateUserEmail(su, updatedUser.getEmail());	//special processing on email change

			if(updatedUser.changePasswordRqrd())	//check for password reset
			{
				su.setUserPW(USER_PASSWORD_PREFIX + updatedUser.getPermission().toString());
				su.setStatus(UserStatus.Change_PW);
			}
			
			//check if access is adding or removing web access. If so, must keep user and agent
			//data bases in sync
			if((su.getAccess()==UserAccess.Website || su.getAccess()==UserAccess.AppAndWebsite) &&
				updatedUser.getAccess()==UserAccess.App) 
			{
				//UserAccess to web site is being removed, set the Agent ID to -1
				su.setAgentID(-1);
				su.setAccess(updatedUser.getAccess());	
			}
			else if(su.getAccess()==UserAccess.App &&
					(updatedUser.getAccess()==UserAccess.Website || 
					  updatedUser.getAccess()==UserAccess.AppAndWebsite))
			{
				//UserAccess to web site is being added, need to check for adding agent
				su.setAgentID(serverAgentDB.checkForAgent(su));
				su.setAccess(updatedUser.getAccess());
			}
			
			//set preference updates
			UserPreferences currPrefs = su.getPreferences();
			UserPreferences newPrefs = updatedUser.getPreferences();
			currPrefs.setFontSize(newPrefs.getFontSize());
			currPrefs.setWishAssigneeFilter(newPrefs.getWishAssigneeFilter());
			currPrefs.setFamilyDNSFilter(newPrefs.getFamilyDNSFilter());
			
			bSaveRequired = true;
//			save();
			
			//userDB and agentDB must stay in sync.
			if(bAgentProfileChanged)
				serverAgentDB.processUserUpdate(su);
				
			ONCUser updateduser = su.getUserFromServerUser();
			return "UPDATED_USER" + gson.toJson(updateduser, ONCUser.class);
		}
		else 
			return "UPDATE_FAILED";
	}
	
	/*********************
	 * Email addresses are stored for both agents and users. Also, some users use there email address as
	 * their user id. Special processing is necessary when the email is updated. If the email and the user
	 * id are the same, update both.
	 * @param su
	 * @param email
	 */
	static void updateUserEmail(ONCServerUser su, String newEmailAddress)
	{
		if(su.getUserID().equals(su.getEmail()))  //email address is user id, so need to update that too
			su.setUserID(newEmailAddress);
		
		su.setEmail(newEmailAddress);
	}
	
	//update from web site
	ONCServerUser updateProfile(ONCServerUser su, Map<String, Object> params)
	{
		//determine if there is a change to the ONCServerUser object
		if(!su.getFirstname().equals((String) params.get("firstname")) || 
			!su.getLastname().equals((String)params.get("lastname")) ||
			 !su.getOrg().equals((String)params.get("org")) ||
			  !su.getTitle().equals((String)params.get("title")) ||
			   !su.getEmail().equals((String)params.get("email")) ||
			    !su.getPhone().equals((String)params.get("phone")))
		{
			//there was a change, so update the profile fields and save ONCServerUser object
			su.setFirstname((String) params.get("firstname"));
			su.setLastname((String) params.get("lastname"));
			su.setOrg((String) params.get("org"));
			su.setTitle((String) params.get("title"));
			
			if(!su.getEmail().equals((String)params.get("email")))
				updateUserEmail(su, (String)params.get("email"));
			
			su.setEmail((String) params.get("email"));
			su.setPhone((String) params.get("phone"));
			
			//determine if the user status was Update_Profile. If it was set it to Active.
			if(su.getStatus() == UserStatus.Update_Profile)
				su.setStatus(UserStatus.Active);
			
			bSaveRequired = true;
//			save();
			
			//userDB and agentDB must stay in sync. If user is an agent, notify agent db of update
			if(su.getAgentID() > -1)	//notify AgentDB of email change
				serverAgentDB.processUserUpdate(su);
			
			return su;
		}
		else
			return null; //no change detected and UserStatus != Update_Profile
	}
	
	//update from web site
	ONCServerUser reviewedProfile(ONCServerUser su)
	{
		//determine if the user was responding to a update profile request. If so, 
		//change their status to UserStatus.Active
		if(su.getStatus() == UserStatus.Update_Profile)
		{
			//user reviewed, so update the status field and mark for saving
			su.setStatus(UserStatus.Active);
			bSaveRequired = true;
			return su;
		}
		else
			return null; //UserStatus != Update_Profile
	}
	
	void processAgentUpdate(Agent updatedAgent)
	{
		//see if the agent is a user, using agent id match
		int index=0;
		while(index < userAL.size() && userAL.get(index).getAgentID() != updatedAgent.getID())
			index++;
		
		if(index < userAL.size())
		{
			//User found, update the profile, paying special attention to the email address
			ONCServerUser updatedUser = userAL.get(index);
			
			//deal with the agent name
			String[] name_parts = updatedAgent.getAgentName().trim().split(" ");
			if(name_parts.length == 0)
			{
				updatedUser.setFirstname("");
				updatedUser.setLastname("");
			}
			else if(name_parts.length == 1)
			{
				updatedUser.setFirstname("");
				updatedUser.setLastname(name_parts[0]);
			}
			else if(name_parts.length == 2)
			{
				updatedUser.setFirstname(name_parts[0]);
				updatedUser.setLastname(name_parts[1]);
			}
			else
			{
				updatedUser.setFirstname(name_parts[0] + " " + name_parts[1]);
				updatedUser.setLastname(name_parts[2]);
			}
			
			updatedUser.setOrg(updatedAgent.getAgentOrg());
			updatedUser.setTitle(updatedAgent.getAgentTitle());
			updatedUser.setPhone(updatedAgent.getAgentPhone());
			if(!updatedUser.getEmail().equals(updatedAgent.getAgentEmail()))
				updateUserEmail(updatedUser, updatedAgent.getAgentEmail());
			
			bSaveRequired = true;
//			save();
			
			//notify all clients of the change
			ONCUser user = new ONCUser(updatedUser); //create a ONCUSer for ONCServerUser
			Gson gson = new Gson();
			ClientManager clientMgr = ClientManager.getInstance();
			clientMgr.notifyAllClients("UPDATED_USER" + gson.toJson(user, ONCUser.class));
		}
	}
	
	ONCServerUser find(String uid)
	{
		int index = 0;
		while(index < userAL.size() && !userAL.get(index).getUserID().equalsIgnoreCase(uid))
			index++;
		
		if(index < userAL.size())
			return userAL.get(index);
		else 
			return null;		
	}
	
	ONCServerUser find(int id)
	{
		int index = 0;
		while(index < userAL.size() && userAL.get(index).getID() != id)
			index++;
		
		if(index < userAL.size())
			return userAL.get(index);
		else 
			return null;		
	}
	
	 String getUsers()
	 {
		 //create a list of ONCUsers from the list of ONCServerUsers because we don't share/send
		 //userid's and passwords over the network to clients, we redact that information from
		 //the ONCServerUser objects
		 ArrayList<ONCUser> redactedServerUserAL = new ArrayList<ONCUser>();
		 for(ONCServerUser su: userAL)
			 redactedServerUserAL.add(su.getUserFromServerUser());
		 
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCUser>>(){}.getType();
				
		String response = gson.toJson(redactedServerUserAL, listtype);
		return response;	
	 }
	 
	 String changePassword(int year, String json, DesktopClient requestingClient)
	 {
		Gson gson = new Gson();
		ChangePasswordRequest cpwReq = gson.fromJson(json, ChangePasswordRequest.class);
		
		String currPW = ServerEncryptionManager.decrypt(cpwReq.getCurrPW());
		String newPW = ServerEncryptionManager.decrypt(cpwReq.getNewPW());
		String response;
		
		//find user
		int index = 0;
		while(index < userAL.size() && userAL.get(index).getID() != cpwReq.getUserID())
		 index++;
		 
		if(index < userAL.size()) 
		{
			//found user, check other parameters to ensure the right user
			ONCServerUser su = userAL.get(index);
			
			if(currPW.equals(su.getUserPW()))
			{
				if(cpwReq.getFirstName().equals(su.getFirstname()) && cpwReq.getLastName().equals(su.getLastname()))
				{
					//user id found, current password matches and user first and last names match
					su.setUserPW(newPW);
					if(su.changePasswordRqrd())
					{
						//need to notify other clients of update to user
						su.setStatus(UserStatus.Update_Profile);

				    	String change = "UPDATED_USER" + gson.toJson(su.getUserFromServerUser(), ONCUser.class);
				    	clientMgr.notifyAllOtherClients(requestingClient, change);	//null to notify all clients
					}
					
					bSaveRequired = true;
//					save();
					response = "PASSWORD_CHANGED<html>Your password has been changed!</html>";
					
				}
				else
					response = "PASSWORD_CHANGE_FAILED<html>Change password request failed<br>" +
							"The user name not found.<br>Please contact ONC Exec Dir.</html>";
			}
			else
				response = "PASSWORD_CHANGE_FAILED<html>Change password request failed.<br>" +
						"Current password was incorrect.<br>Please try again.</html>";
		}
		else
			response = String.format("PASSWORD_CHANGE_FAILED<html>Change password request failed.<br>" +
					"User #%d couldn't be located.<br>Please contact ONC Exec Dir.</html>", cpwReq.getUserID());
		
		
		return response;	
	 }
	 
	 int changePassword(String currpw, String newpw, WebClient webClient)
	 {
		int result = 0;
		
		//find user in server user data base
		int index = 0;
		while(index < userAL.size() && userAL.get(index).getID() != webClient.getWebUser().getID())
		 index++;
		 
		if(index < userAL.size()) 
		{
			//found user, check other parameters to ensure the right user
			ONCServerUser su = userAL.get(index);
			
			if(su.pwMatch(currpw))
			{
				su.setUserPW(newpw);
				
				if(su.changePasswordRqrd())
				{
					//need to notify other clients of update to user
					su.setStatus(UserStatus.Update_Profile);

					Gson gson = new Gson();
				    String change = "UPDATED_USER" + gson.toJson(su.getUserFromServerUser(), ONCUser.class);
				    clientMgr.notifyAllClients(change);	//notify all desktop clients
				}
				
				bSaveRequired = true;
//				save();
			}
			else
				result = -1;
		}
		else
			result = -2;
		
		return result;	
	 } 
	
	@Override
	void addObject(String[] nextLine) 
	{
		Calendar date_changed = Calendar.getInstance();
		if(!nextLine[6].isEmpty())
			date_changed.setTimeInMillis(Long.parseLong(nextLine[8]));
		
		nextLine[1] = ServerEncryptionManager.decrypt(nextLine[1]);
		nextLine[2] = ServerEncryptionManager.decrypt(nextLine[2]);
			
		userAL.add(new ONCServerUser(nextLine, date_changed.getTime()));
		
	}
	
	void requestSave()
	{
		bSaveRequired = true;
	}
/*
	@Override
	void save()
	{
		String path = System.getProperty("user.dir") + getFileName();
		File oncwritefile = new File(path);
		
		try 
	    {
	    	CSVWriter writer = new CSVWriter(new FileWriter(oncwritefile.getAbsoluteFile()));
	    	writer.writeNext(getExportHeader());
	    	
	    	for(ONCServerUser su: userAL)
	    		writer.writeNext(su.getExportRow());	//Write server user row
	    	
	    	for(ONCObject oncObj : getONCObjectList())
	    		writer.writeNext(oncObj.getExportRow());
	    	
	    	writer.close();
	    } 
	    catch (IOException x)
	    {
	    	System.err.format("IO Exception: %s%n", x);
	    }
	}
*/	
	@Override
	String[] getExportHeader()
	{
		return new String[] {"ID", "Username", "Password", "Status", "Access", "Permission", "First Name",
				"Last Name", "Date Changed", "Changed By", "SL Position", "SL Message", 
				"SL Changed By", "Sessions", "Last Login", "Orginization", "Title",
				"Email", "Phone", "Agent ID", "Font Size", "Wish Assignee Filter",
				"Family DNS Filter"};
	}
	
	@Override
	String getFileName() { return USERDB_FILENAME; }
	
	@Override
	List<? extends ONCObject> getONCObjectList() { return userAL; }
}
