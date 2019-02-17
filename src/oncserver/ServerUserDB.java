package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import ourneighborschild.BritepathFamily;
import ourneighborschild.ChangePasswordRequest;
import ourneighborschild.EmailAddress;
import ourneighborschild.ONCEmail;
import ourneighborschild.ONCEmailAttachment;
import ourneighborschild.ONCFamily;
import ourneighborschild.ONCNote;
import ourneighborschild.ONCObject;
import ourneighborschild.ONCServerUser;
import ourneighborschild.ONCUser;
import ourneighborschild.ServerCredentials;
import ourneighborschild.UserAccess;
import ourneighborschild.UserPermission;
import ourneighborschild.UserPreferences;
import ourneighborschild.UserStatus;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerUserDB extends ServerPermanentDB
{
	private static final int USER_RECORD_LENGTH = 26;
	private static final String USER_PASSWORD_PREFIX = "onc";
	private static final String USERDB_FILENAME = "newuser.csv";
	private static final int RECOVERY_ID_LENGTH = 16;
	
	private static final String AGENT_EMAIL_ADDRESS = "schoolcontact@ourneighborschild.org";
	private static final String AGENT_EMAIL_PASSWORD = "crazyelf";
	
	private static ServerUserDB instance  = null;
	
	private static ClientManager clientMgr;
	
	private static List<ONCServerUser> userAL;
	
	private ServerUserDB() throws NumberFormatException, IOException
	{
		clientMgr = ClientManager.getInstance();
		
		userAL = new ArrayList<ONCServerUser>();
//		System.out.println(String.format("ServerUserDB filename: %s", System.getProperty("user.dir") + USERDB_FILENAME));
		importDB(String.format("%s/PermanentDB/%s", System.getProperty("user.dir"), USERDB_FILENAME), "User DB", USER_RECORD_LENGTH);
		nextID = getNextID(userAL);
		bSaveRequired = false;
		
//		System.out.println(String.format("ServerUserDB: added %d IDs", addMissingEncryptedUserIDs()));
	}
	
	public static ServerUserDB getInstance() throws NumberFormatException, IOException
	{
		if(instance == null)
			instance = new ServerUserDB();
		
		return instance;
	}
	
	public static ONCServerUser getServerUser(int userid)
	{
		if(userid < 0)
			return null;
		else
		{
			int index = 0;
			while(index < userAL.size() && userAL.get(index).getID() != userid)
				index++;
		
			return index < userAL.size() ? userAL.get(index) : null;
		}
	}
	
	ONCServerUser findUserByRecoveryID(String recoveryID)
	{
		if(recoveryID.length() != RECOVERY_ID_LENGTH)
			return null;
		else
		{
			int index = 0;
			while(index < userAL.size() && !userAL.get(index).getRecoveryID().equals(recoveryID))
				index++;
			
			return index < userAL.size() ? userAL.get(index) : null;
		}
	}
	
	@Override
	String add(String json, ONCUser client)
	{
		Gson gson = new Gson();
		ONCServerUser su = gson.fromJson(json, ONCServerUser.class);
		
		su.setID(nextID++);	//Set id for new user
		su.setUserPW(USER_PASSWORD_PREFIX + su.getPermission().toString());
		su.setStatus(UserStatus.Change_PW);
		su.setDateChanged(new Date());
		su.setChangedBy(client.getLNFI());
		
		userAL.add(su); //Add new user to data base
		bSaveRequired = true;
		
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
			
			//update the server user
			su.setLastName(updatedUser.getLastName());
			su.setFirstName(updatedUser.getFirstName());
			su.setStatus(updatedUser.getStatus());
			su.setAccess(updatedUser.getAccess());
			su.setPermission(updatedUser.getPermission());
			su.setOrganization(updatedUser.getOrganization());
			su.setTitle(updatedUser.getTitle());
			su.setCellPhone(updatedUser.getCellPhone());
			
			if(!su.getEmail().equals(updatedUser.getEmail()))
				updateUserEmail(su, updatedUser.getEmail());	//special processing on email change

			if(updatedUser.getStatus() == UserStatus.Reset_PW)	//check if reset password
			{
				su.setUserPW(USER_PASSWORD_PREFIX + updatedUser.getPermission().toString());
				su.setStatus(UserStatus.Change_PW);
			}
			
			su.setDateChanged(new Date());
			
			//set updated group list
			su.setGroupList(updatedUser.getGroupList());
			
			//set preference updates
			UserPreferences currPrefs = su.getPreferences();
			UserPreferences newPrefs = updatedUser.getPreferences();
			currPrefs.setFontSize(newPrefs.getFontSize());
			currPrefs.setWishAssigneeFilter(newPrefs.getWishAssigneeFilter());
			currPrefs.setFamilyDNSFilter(newPrefs.getFamilyDNSFilter());
			
			bSaveRequired = true;
			
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
		if(!su.getFirstName().equals((String) params.get("firstname")) || 
			!su.getLastName().equals((String)params.get("lastname")) ||
			 !su.getOrganization().equals((String)params.get("org")) ||
			  !su.getTitle().equals((String)params.get("title")) ||
			   !su.getEmail().equals((String)params.get("email")) ||
			    !su.getCellPhone().equals((String)params.get("phone")))
		{
			//there was a change, so update the profile fields and save ONCServerUser object
			su.setFirstName((String) params.get("firstname"));
			su.setLastName((String) params.get("lastname"));
			su.setOrganization((String) params.get("org"));
			su.setTitle((String) params.get("title"));
			updateUserEmail(su, (String)params.get("email"));
			su.setCellPhone((String) params.get("phone"));
			
			//determine if the user status was Update_Profile. If it was set it to Active.
			if(su.getStatus() == UserStatus.Update_Profile)
				su.setStatus(UserStatus.Active);
			
			bSaveRequired = true;

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
				if(cpwReq.getFirstName().equals(su.getFirstName()) && cpwReq.getLastName().equals(su.getLastName()))
				{
					//user id found, current password matches and user first and last names match
					su.setUserPW(newPW);
					su.disableRecoveryID();
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
				su.disableRecoveryID();
				
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
	void addObject(String type, String[] nextLine) 
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
				"Email", "Phone", "Groups", "Font Size", "Wish Assignee Filter",
				"Family DNS Filter", "Failed Count", "RecoveryID", "RecoveryID Time"};
	}
	
	@Override
	String getFileName() { return USERDB_FILENAME; }
	
	@Override
	List<? extends ONCObject> getONCObjectList() { return userAL; }
	
	String getAgents()
	{
		List<ONCWebAgent> agentList = new ArrayList<ONCWebAgent>();
		for(ONCServerUser su : userAL)
			if(su.getPermission().compareTo(UserPermission.Agent) >= 0)
				agentList.add(new ONCWebAgent(su));
		
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCWebAgent>>(){}.getType();		
		return gson.toJson(agentList, listtype);
	}
	
	List<ONCServerUser> getUsersInGroup(int groupID, EnumSet<UserStatus> usEnumSet)
	{
		List<ONCServerUser> groupMemberList = new ArrayList<ONCServerUser>();
		for(ONCServerUser su : userAL)
		{
			if(usEnumSet.contains(su.getStatus()))
			{		
				List<Integer> groupList = su.getGroupList();
			
				int index = 0;
				while(index < groupList.size() && groupList.get(index) != groupID)
					index++;
			
				if(index < groupList.size())
					groupMemberList.add(su);
			}
		}
		
		return groupMemberList;
	}
	
	/***
	 * Returns an <Agent> json that contains agents that referred families in the parameter
	 * year. Uses the JSONP construct.
	 * @param year
	 * @param callbackFunction
	 * @return
	 */
/*	
	static HtmlResponse getAgentsJSONP(int year, ONCServerUser agent, int groupID, String callbackFunction)
	{	
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCWebAgent>>(){}.getType();
		
		List<ONCWebAgent> agentReferredInYearList = new ArrayList<ONCWebAgent>();
		
		//get the group permission
		int groupPermission = 0;
		if(groupID > -2)
		{
			ONCGroup agentGroup = ServerGroupDB.getGroup(groupID);
			groupPermission = agentGroup == null ? 0 : agentGroup.getPermission();
		}
		
		//if user permission is AGENT, only return a list of that agent, plus all other agents in the
		//group that referred in the year. Otherwise,  return all agents that referred
		if(agent.getPermission().compareTo(UserPermission.Agent) == 0 && groupPermission > 0)
		{
			agentReferredInYearList.add(new ONCWebAgent(agent));	//add the logged in user/agent
			//populate the list with all other agents who are in the group and who referred in the year
			for(ONCServerUser su: userAL)
				if(su.getID() != agent.getID())
					for(Integer i : su.getGroupList())
						if(i == groupID && ServerFamilyDB.didAgentReferInYear(year, su.getID()))
							agentReferredInYearList.add(new ONCWebAgent(su));			
			
//			System.out.println(String.format("ServerUserDB.getAgents: %s %s in group %d",
//					su.getLastname(), bInGroup ? "is" : "isn't", groupID));
		}
		else if(agent.getPermission().compareTo(UserPermission.Agent) == 0 && groupPermission == 0)
		{
			//just return the agent
			agentReferredInYearList.add(new ONCWebAgent( agent));
		}
		else if(agent.getPermission().compareTo(UserPermission.Agent) > 0 && groupID > -1)
		{
			//populate the list with all other agents who are in the group and who referred in the year
			for(ONCServerUser su: userAL)
				if(su.getID() != agent.getID())
					for(Integer i : su.getGroupList())
						if(i == groupID && ServerFamilyDB.didAgentReferInYear(year, su.getID()))
							agentReferredInYearList.add(new ONCWebAgent(su));
		}
		else
		{
			//add all agents that referred in year
			for(ONCServerUser su : userAL)
				if(ServerFamilyDB.didAgentReferInYear(year, su.getID()))
					agentReferredInYearList.add(new ONCWebAgent(su));
			
			//sort the list by name
			Collections.sort(agentReferredInYearList, new ONCAgentNameComparator());
		}
			
		String response = gson.toJson(agentReferredInYearList, listtype);
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HTTPCode.Ok);		
	}
*/	
	/***
	 * Returns an <Agent> json that contains agents that referred families in the parameter
	 * year. Uses the JSONP construct.
	 * @param year
	 * @param callbackFunction
	 * @return
	 */
/*	
	static HtmlResponse getAgentsInGroupJSONP(int year, ONCUser user, int groupID, String callbackFunction)
	{	
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCWebAgent>>(){}.getType();
		
		List<ONCWebAgent> agentsInGroupList = new ArrayList<ONCWebAgent>();
		
		for(ONCServerUser su : userAL)
				if(su.isInGroup(groupID) && ServerFamilyDB.didAgentReferInYear(year, su.getID()))
					agentsInGroupList.add(new ONCWebAgent(su));
			
			//sort the list by name
			Collections.sort(agentsInGroupList, new ONCAgentNameComparator());
			
		String response = gson.toJson(agentsInGroupList, listtype);
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HTTPCode.Ok);		
	}
*/	
	static HtmlResponse getAgentJSONP(int agentID, String callbackFunction)
	{		
		Gson gson = new Gson();
		String response;
	
		int index=0;
		while(index < userAL.size() && userAL.get(index).getID() != agentID)
			index++;
		
		if(index < userAL.size())
		{
			ONCWebAgent oncWebAgent = new ONCWebAgent(userAL.get(index));
			response = gson.toJson(oncWebAgent, ONCWebAgent.class);
		}
		else
			response = "";
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	
	ONCWebAgent getWebAgent(int agentID)
	{		
		int index=0;
		while(index < userAL.size() && userAL.get(index).getID() != agentID)
			index++;
		
		return index < userAL.size() ? new ONCWebAgent(userAL.get(index)) : null;	
	}	
		
	private ONCServerUser createSeverUserFromBritepathsReferral(BritepathFamily bpFam, DesktopClient currClient)
	{
		//split britepaths agent name into first name and last name
		String firstName = "", lastName= "";
		String[] name_parts = bpFam.getReferringAgentName().split(" ");
		if(name_parts.length == 0)
		{
			firstName = "No Name";
			lastName = "No Name";
		}
		else if(name_parts.length == 1)
		{
			lastName = name_parts[0];
		}
		else if(name_parts.length == 2)
		{
			firstName = name_parts[0];
			lastName = name_parts[1];
		}
		else if(name_parts.length == 3)
		{
			firstName = name_parts[0];
			lastName = name_parts[1] + " " + name_parts[2];	
		}
		else
		{
			firstName = name_parts[0] + " " + name_parts[1];
			lastName = name_parts[2];
			int index = 3;
			while(index < name_parts.length)
				lastName.concat(" " + name_parts[index++]);
		}
		
		return new ONCServerUser(-1, new Date(), currClient.getClientUser().getLNFI(), 3, 
				"New Britepaths Referral User", currClient.getClientUser().getLNFI(),
				firstName, lastName, UserStatus.Inactive, UserAccess.Website, UserPermission.Agent,
				bpFam.getReferringAgentEmail(), USER_PASSWORD_PREFIX + UserPermission.Agent.toString(),
				0, System.currentTimeMillis(), true, bpFam.getReferringAgentOrg(), 
				bpFam.getReferringAgentTitle(), bpFam.getReferringAgentEmail(),
				bpFam.getReferringAgentPhone(), new LinkedList<Integer>());	
	}
	
	ONCServerUser findUserByEmailAndPhone(String email, String phone)
	{
		int index = 0;
		while(index < userAL.size() && !(userAL.get(index).getEmail().equalsIgnoreCase(email) &&
				getDigits(userAL.get(index).getHomePhone()).equals(getDigits(phone))))
			index++;
		
		if(index < userAL.size())
			return userAL.get(index);
		else
			return null;
	}
	
	String getDigits(String pn)
	{
		return pn.replaceAll("\\D+","");
	}
	
	
	ImportONCObjectResponse processImportedReferringAgent(BritepathFamily bpFam, DesktopClient currClient)
	{		
		//check to see if the user already exists by name. If so, don't create a new
		//user. If they don't exist, add them as a user
		ONCServerUser newPotentialUser = createSeverUserFromBritepathsReferral(bpFam, currClient);
		
		//try to find the new potential user
		int index = 0;
		while(index < userAL.size() && !userAL.get(index).doesUserMatch(newPotentialUser))
			index++;
				
		if(index < userAL.size())
		{
			//found a match. Determine if other fields user have changed. If so, update
			boolean bUserUpdated = false;
			ONCServerUser existingSU = userAL.get(index);
			
			if(!newPotentialUser.getOrganization().trim().isEmpty() && !newPotentialUser.getOrganization().equals(existingSU.getOrganization())) 
			{
				existingSU.setOrganization(newPotentialUser.getOrganization().trim());
				bUserUpdated = true;
			}
			
			if(!newPotentialUser.getTitle().trim().isEmpty() && !newPotentialUser.getTitle().equals(existingSU.getTitle())) 
			{
				existingSU.setTitle(newPotentialUser.getTitle().trim()); 
				bUserUpdated = true;
			}
					
			if(!newPotentialUser.getEmail().trim().isEmpty() && !newPotentialUser.getEmail().equals(existingSU.getEmail()))
			{
				existingSU.setEmail(newPotentialUser.getEmail().trim());
				bUserUpdated = true;
			}
					
			if(!newPotentialUser.getCellPhone().trim().isEmpty() && !newPotentialUser.getCellPhone().equals(existingSU.getCellPhone())) 
			{
				existingSU.setCellPhone(newPotentialUser.getCellPhone().trim());
				bUserUpdated = true;
			}
			
			if(bUserUpdated)
			{
				bSaveRequired = true;
				
				Gson gson = new Gson();
				ONCUser updatedUser = existingSU.getUserFromServerUser();
				return new ImportONCObjectResponse(USER_UPDATED, updatedUser, 
						"UPDATED_USER" + gson.toJson(updatedUser, ONCUser.class));
			}
			else
				return new ImportONCObjectResponse(USER_UNCHANGED, null, "UNCHANGED");
		}
		else
		{
			//user match not found, add the potential user to the database, save and return
			newPotentialUser.setID(nextID++);
			userAL.add(newPotentialUser);
		
			bSaveRequired = true;

			ONCUser addedUser = newPotentialUser.getUserFromServerUser();
			Gson gson = new Gson();
			return new ImportONCObjectResponse(USER_ADDED, addedUser, "ADDED_USER" + gson.toJson(addedUser, ONCUser.class));
		}
	}
	
	int updateUserNames()
	{
		int count = 0;
		for(ONCServerUser su : userAL)
		{
			if(su.getUserID().equals("oncAgent") && su.getPermission() == UserPermission.Agent)
			{
				su.setUserID(su.getEmail());
				count++;
			}
		}
		
		if(count > 0)
			bSaveRequired = true;
		
		return count;
	}
	void createAndSendRecoveryEmail(ONCServerUser recUser)
	{
		String subject = "Account Recovery - Our Neighbor's Child";
		String emailBody = createRecoveryEmail(recUser);
		createAndSendUserEmail(subject, emailBody, recUser.getUserFromServerUser());
	}
	
	boolean createAndSendNoteNotificationEmail(int year, ONCNote note)
	{
		//find the agent and send the email
		try
		{
			ServerFamilyDB famDB = ServerFamilyDB.getInstance();
			ONCFamily fam = famDB.getFamily(year,  note.getOwnerID());
			
			if(fam != null)
			{	
				int index=0;
				while(index < userAL.size() && userAL.get(index).getID() != fam.getAgentID())
					index++;
			
				if(index < userAL.size())
				{
					ONCServerUser recUser = userAL.get(index);
					String subject = "New Referral Notes - Our Neighbor's Child";
					String emailBody = createNoteNotificationEmail(recUser);
					createAndSendUserEmail(subject, emailBody, recUser);
					return true;
				}
				else
					return false;
			}
			else
				return false;
		}
		catch (FileNotFoundException e)
		{
			return false;
		}
		catch (IOException e)
		{
			return false;
		}
	}
	
	private void createAndSendUserEmail(String subject, String emailBody, ONCUser recUser)
	{
		//build the email
		ArrayList<ONCEmail> emailAL = new ArrayList<ONCEmail>();
		ArrayList<ONCEmailAttachment> attachmentAL = new ArrayList<ONCEmailAttachment>();
		
		//Create recipient list for email.
		ArrayList<EmailAddress> recipientAddressList = new ArrayList<EmailAddress>();
		
		//verify the agent has a valid email address and name. If not, return an empty list
		if(recUser != null && recUser.getEmail() != null && recUser.getEmail().length() > 2 &&
				recUser.getLastName() != null && recUser.getLastName().trim().length() > 2)
        {
			//LIVE EMAIL ADDRESS
			EmailAddress toAddress = new EmailAddress(recUser.getEmail(), recUser.getLastName());	//live
			recipientAddressList.add(toAddress);    	
        }
	        
	    //If the email isn't valid, the message will not be sent.
	    if(subject != null && emailBody != null && !recipientAddressList.isEmpty())
	        	emailAL.add(new ONCEmail(subject, emailBody, recipientAddressList));     	
		
		//Create the from address string array
		EmailAddress fromAddress = new EmailAddress(AGENT_EMAIL_ADDRESS, "Our Neighbor's Child");
//		EmailAddress fromAddress = new EmailAddress(TEST_AGENT_EMAIL_SENDER_ADDRESS, "Our Neighbor's Child");
		
		//Create the blind carbon copy list 
		ArrayList<EmailAddress> bccList = new ArrayList<EmailAddress>();
		bccList.add(new EmailAddress(AGENT_EMAIL_ADDRESS, "School Coordinator"));
//		bccList.add(new EmailAddress("kellylavin1@gmail.com", "Kelly Lavin"));
//		bccList.add(new EmailAddress("mnrogers123@msn.com", "Nicole Rogers"));
//		bccList.add(new EmailAddress("johnwoneill@cox.net", "John O'Neill"));
		
		//Google Mail
		ServerCredentials creds = new ServerCredentials("smtp.gmail.com", AGENT_EMAIL_ADDRESS, AGENT_EMAIL_PASSWORD);
		
	    ServerEmailer oncEmailer = new ServerEmailer(fromAddress, bccList, emailAL, attachmentAL, creds);
	    oncEmailer.execute();
	    
	    //now that an email has been scheduled, update the user acoount 
	}
	
	String createRecoveryEmail(ONCServerUser su)
	{
        //Create the text part of the email using html
		String link = String.format("\"https://oncdms.org:%d/recoverylogin?caseID=%s\">Account Recovery Link", 
				8902, su.getRecoveryID());
		
		String msg = "<html><body><div>" +
			"<p>Dear " + su.getFirstName() + ",</p>"
			+ "<p>Thank you for connecting families in your school with holiday assistance through Our Neighbor's "
			+ "Child. We received your account recovery request and have provided detailed instructions in this "
			+ "email. Clicking on Account Recovery Link [below] will take you to our recovery webpage. "
			+ "<b>IMPORTANT: For security reasons, this link will be active for one hour.</b></p> "
			+ "<p>Once you reach the recovery webpage, your User Name will automatically appear in the "
			+ "User Name field. Please make note of it for future access. Please enter the following "
			+ "case sensitive temporary password: <b>" + su.getUserPW() + "</b> in the Password field prior to "
			+ "clicking the login button.</p>"
			+ "<p>If account recovery is successful, you will be prompted to set a new password for subsequent "
			+ "access to your account. Use the temporary password again to set a new password.</p>"
			+"<p><b><a href=" + link + "</a></b></p>"
		    +"<p>Our Neighbor's Child<br>"
		    +"P.O. Box 276<br>"
		    +"Centreville, VA 20120<br>"
		    +"<a href=\"http://www.ourneighborschild.org\">www.ourneighborschild.org</a></p></div>";
		    
        return msg;
	}
	
	String createNoteNotificationEmail(ONCUser user)
	{
        //Create the text part of the note notification email using html
		String link = String.format("\"https://oncdms.org:%d/welcome\">Our Neighbor's Child Login", 8902);
		String currYear = DBManager.getMostCurrentYear();
		
		String msg = "<html><body><div>" +
			"<p>Dear " + user.getFirstName() + ",</p>"
			+ "<p>As we process one or more of your " + currYear + " referrals, we have "
			+ "some questions or comments for you and created a note for you to view. You can access and "
			+ "respond to notes for your referrals by logging in to our referral website.</p>"
			+ "<p>Families with new notes will be highlighed in yellow in the " + currYear + " table found "
			+ "at the bottom of our referral status webpage. Clicking the \"Notes\" button displays referral "
			+ "notes. Please take a moment to view and respond.</p>"
			+ "<p>This link <b><a href=" + link + "</a></b> will take you to our login webpage.</p>"
			+ "<p>Thank You!</p>"
		    +"<p>Our Neighbor's Child<br>"
		    +"P.O. Box 276<br>"
		    +"Centreville, VA 20120<br>"
		    +"<a href=\"http://www.ourneighborschild.org\">www.ourneighborschild.org</a></p></div>";

        return msg;
	}	
}
