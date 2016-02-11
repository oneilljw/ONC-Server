package oncserver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import ourneighborschild.ChangePasswordRequest;
import ourneighborschild.ONCEncryptor;
import ourneighborschild.ONCServerUser;
import ourneighborschild.ONCUser;
import ourneighborschild.UserAccess;
import ourneighborschild.UserStatus;
import au.com.bytecode.opencsv.CSVWriter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerUserDB extends ONCServerDB
{
	private static final int USER_RECORD_LENGTH = 20;
	private static final String USER_PASSWORD_PREFIX = "onc";
	private static ServerUserDB instance  = null;
	
	private static ClientManager clientMgr;
	private static AgentDB agentDB;
	
	private static List<ONCServerUser> userAL;
	private int id;
	
	private ServerUserDB() throws NumberFormatException, IOException
	{
		clientMgr = ClientManager.getInstance();
		agentDB = AgentDB.getInstance();
		
		userAL = new ArrayList<ONCServerUser>();
		importDB(0, System.getProperty("user.dir") + "/users.csv", "User DB", USER_RECORD_LENGTH);
		id = getNextID(userAL);
	}
	
	public static ServerUserDB getInstance() throws NumberFormatException, IOException
	{
		if(instance == null)
			instance = new ServerUserDB();
		
		return instance;
	}
	
	@Override
	String add(int year, String json)	//User DB currently not implemented by year
	{
		Gson gson = new Gson();
		ONCServerUser su = gson.fromJson(json, ONCServerUser.class);
		
		su.setID(id++);	//Set id for new user
		su.setUserPW("onc" + su.getPermission().toString());
		su.setStatus(UserStatus.Change_PW);
		
		//If the user has web site access, ask the Agent DB to add the agent if there not already there.
		//Set the agentID field to the agentID. If not web site access, set the agentID to -1;
		if(su.getAccess() == UserAccess.Website || su.getAccess() == UserAccess.AppAndWebsite)
			su.setAgentID(agentDB.checkForAgent(DBManager.getCurrentYear(), su));
		else
			su.setAgentID(-1);
		
		userAL.add(su); //Add new user to data base
		save(-1);	//userDB not implemented by year
		
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
			su.setLastname(updatedUser.getLastname());
			su.setFirstname(updatedUser.getFirstname());
			su.setStatus(updatedUser.getStatus());
			su.setPermission(updatedUser.getPermission());
			su.setOrg(updatedUser.getOrg());
			su.setTitle(updatedUser.getTitle());
			su.setEmail(updatedUser.getEmail());
			su.setPhone(updatedUser.getPhone());
			if(updatedUser.changePasswordRqrd())	//check for password reset
			{
				su.setUserPW(USER_PASSWORD_PREFIX + updatedUser.getPermission().toString());
				su.setStatus(UserStatus.Change_PW);
			}
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
				su.setAgentID(agentDB.checkForAgent(DBManager.getCurrentYear(), su));
				su.setAccess(updatedUser.getAccess());
			}
			
			save(-1);	//userDB not implemented by year
			
			ONCUser updateduser = su.getUserFromServerUser();
			return "UPDATED_USER" + gson.toJson(updateduser, ONCUser.class);
		}
		else 
			return "UPDATE_FAILED";
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
			su.setEmail((String) params.get("email"));
			su.setPhone((String) params.get("phone"));
			
			save(-1);	//userDB not implemented by year currently
			
			return su;
		}
		else
			return null; //no change detected
	}
	
	ONCServerUser find(String uid)
	{
		int index = 0;
		while(index < userAL.size() && !userAL.get(index).getUserID().equals(uid))
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
		
		String currPW = ONCEncryptor.decrypt(cpwReq.getCurrPW());
		String newPW = ONCEncryptor.decrypt(cpwReq.getNewPW());
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
					
					save(year);
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
					
				save(-1);	//year is irrelevant, only one user db
			}
			else
				result = -1;
		}
		else
			result = -2;
		
		return result;	
	 } 
	
	@Override
	void addObject(int year, String[] nextLine) 
	{
		Calendar date_changed = Calendar.getInstance();
		if(!nextLine[6].isEmpty())
			date_changed.setTimeInMillis(Long.parseLong(nextLine[8]));
		
		Calendar last_login = Calendar.getInstance();
		if(!nextLine[14].isEmpty())
			last_login.setTimeInMillis(Long.parseLong(nextLine[14]));
			
		userAL.add(new ONCServerUser(nextLine, date_changed.getTime(), last_login.getTime()));
		
	}

	@Override
	void createNewYear(int year) {
		// TODO Auto-generated method stub
		
	}

	@Override
	void save(int year)
	{
		String[] header = {"ID", "Username", "Password", "Status", "Access", "Permission", "First Name",
							"Last Name", "Date Changed", "Changed By", "SL Position", "SL Message", 
							"SL Changed By", "Sessions", "Last Login", "Orginization", "Title",
							"Email", "Phone", "Agent ID"};
		
		String path = System.getProperty("user.dir") + "/users.csv";
		File oncwritefile = new File(path);
			
		try 
	    {
	    	CSVWriter writer = new CSVWriter(new FileWriter(oncwritefile.getAbsoluteFile()));
	    	writer.writeNext(header);
	    	
	    	for(ONCServerUser su: userAL)
	    		writer.writeNext(su.getExportRow());	//Write server user row
	    	
	    	writer.close();
	    } 
	    catch (IOException x)
	    {
	    	System.err.format("IO Exception: %s%n", x);
	    }
	}
}
