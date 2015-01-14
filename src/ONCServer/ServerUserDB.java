package ONCServer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import au.com.bytecode.opencsv.CSVWriter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import OurNeighborsChild.ChangePasswordRequest;
import OurNeighborsChild.ONCServerUser;
import OurNeighborsChild.ONCUser;

public class ServerUserDB extends ONCServerDB
{
	private static final int USER_RECORD_LENGTH = 11;
	private static ServerUserDB instance  = null;
	
	private List<ONCServerUser> userAL;
	private int id;
	
	private ServerUserDB() throws NumberFormatException, IOException
	{
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
	
		userAL.add(su); //Add new user to data base
		
		//Create response to send to requesting client
		ONCUser newuser = new ONCUser(su.getID(), su.getDateChanged(), su.getChangedBy(),
									su.getStoplightPos(), su.getStoplightMssg(), su.getStoplightChangedBy(),
									su.getFirstname(), su.getLastname(), su.getPermission());
		
		save(year);
		
		return "ADDED_USER" + gson.toJson(newuser, ONCUser.class) ;
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
	 
	 String changePassword(int year, String json)
	 {
		Gson gson = new Gson();
		ChangePasswordRequest cpwReq = gson.fromJson(json, ChangePasswordRequest.class);
		String response;
		
		//find user
		int index = 0;
		while(index < userAL.size() && userAL.get(index).getID() != cpwReq.getUserID())
		 index++;
		 
		if(index < userAL.size()) 
		{
			//found user, check other parameters to ensure the right user
			ONCServerUser su = userAL.get(index);
			
			if(cpwReq.getCurrPW().equals(su.getUserPW()))
			{
				if(cpwReq.getFirstName().equals(su.getFirstname()) && cpwReq.getLastName().equals(su.getLastname()))
				{
					//user id found, current password matches and user first and last names match
					su.setUserPW(cpwReq.getNewPW());
					response = "PASSWORD_CHANGED<html>Your password has been changed!</html>";
				}
				else
					response = "PASSWORD_CHANGE_FAILED<html>Change password request failed<br>" +
							"The user name not found.<br>Please contact ONC management.</html>";
			}
			else
				response = "PASSWORD_CHANGE_FAILED<html>Change password request failed.<br>" +
						"Current password was incorrect.<br>Please try again.</html>";
		}
		else
			response = String.format("PASSWORD_CHANGE_FAILED<html>Change password request failed.<br>" +
					"User #%d couldn't be located.<br>Please contact ONC management.</html>", cpwReq.getUserID());
		
		save(year);
		return response;	
	 }
	 
	@Override
	void addObject(int year, String[] nextLine) 
	{
		Calendar date_changed = Calendar.getInstance();
		if(!nextLine[6].isEmpty())
			date_changed.setTimeInMillis(Long.parseLong(nextLine[6]));
			
		userAL.add(new ONCServerUser(nextLine, date_changed.getTime()));
		
	}

	@Override
	void createNewYear(int year) {
		// TODO Auto-generated method stub
		
	}

	@Override
	void save(int year)
	{
		String[] header = {"ID", "Username", "Password", "Permission", "First Name", "Last Name",
							"Date Changed", "Changed By", "SL Position", "SL Message", "SL Changed By"};
		
		String path = System.getProperty("user.dir") + "/users.csv";
		File oncwritefile = new File(path);
			
		try 
	    {
	    	CSVWriter writer = new CSVWriter(new FileWriter(oncwritefile.getAbsoluteFile()));
	    	writer.writeNext(header);
	    	
	    	for(ONCUser u: userAL)
	    		writer.writeNext(u.getExportRow());	//Write user row
	    	
	    	writer.close();
	    } 
	    catch (IOException x)
	    {
	    	System.err.format("IO Exception: %s%n", x);
	    }
	}
}
