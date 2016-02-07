package oncserver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import ourneighborschild.Agent;
import ourneighborschild.ChangePasswordRequest;
import ourneighborschild.ONCEncryptor;
import ourneighborschild.ONCServerUser;
import ourneighborschild.ONCUser;
import ourneighborschild.UserStatus;
import au.com.bytecode.opencsv.CSVWriter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerUserDB extends ONCServerDB
{
	private static final int USER_RECORD_LENGTH = 16;
	private static final String USER_PASSWORD_PREFIX = "onc";
	private static ServerUserDB instance  = null;
	
	private static ClientManager clientMgr;
	
	private List<ONCServerUser> userAL;
	private int id;
	
	private ServerUserDB() throws NumberFormatException, IOException
	{
		clientMgr = ClientManager.getInstance();
		
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
	
		userAL.add(su); //Add new user to data base
		
		save(year);
		
		return "ADDED_USER" + gson.toJson(su.getUserFromServerUser(), ONCUser.class) ;
	}
	
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
			su.setPermission(updatedUser.getPermission());
			if(updatedUser.changePasswordRqrd())
			{
				su.setUserPW(USER_PASSWORD_PREFIX + updatedUser.getPermission().toString());
				su.setStatus(UserStatus.Change_PW);
			}
			
			save(year);
			
			ONCUser updateduser = su.getUserFromServerUser();
			return "UPDATED_USER" + gson.toJson(updateduser, ONCUser.class);
		}
		else 
			return "UPDATE_FAILED";
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
							"SL Changed By", "Sessions", "Last Login", "Agent ID"};
		
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
