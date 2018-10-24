package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ourneighborschild.Activity;
import ourneighborschild.ONCVolunteer;
import ourneighborschild.VolAct;

public class ServerVolunteerActivityDB extends ServerSeasonalDB implements SignUpListener
{
	private static final int VOLUNTEER_ACTIVITY_DB_HEADER_LENGTH = 6;
	
	private static ServerVolunteerActivityDB instance = null;
	private static List<VolunteerActivityDBYear> volunteerActivityDB;
	
	private static SignUpGeniusIF geniusIF;
	
	private ServerVolunteerActivityDB() throws FileNotFoundException, IOException
	{
		//create the activity data bases for TOTAL_YEARS number of years
		volunteerActivityDB = new ArrayList<VolunteerActivityDBYear>();
		
		geniusIF = SignUpGeniusIF.getInstance();
		geniusIF.addSignUpListener(this);
		
		//populate the data base for the last TOTAL_YEARS from persistent store
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the volunteer activity list for each year
			VolunteerActivityDBYear volunteerActivityDBYear = new VolunteerActivityDBYear();
									
			//add the list of activities for the year to the db
			volunteerActivityDB.add(volunteerActivityDBYear);
									
			//import the activities from persistent store
			importDB(year, String.format("%s/%dDB/VolunteerActivityDB.csv", System.getProperty("user.dir"),
						year), "Volunteer Activity DB", VOLUNTEER_ACTIVITY_DB_HEADER_LENGTH );
		
			//set the next id
			volunteerActivityDBYear.setNextID(getNextID(volunteerActivityDBYear.getList()));
		}
	}
	
	public static ServerVolunteerActivityDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerVolunteerActivityDB();
		
		return instance;
	}
	
	List<VolAct> clone(int year)
	{
		List<VolAct> volActList = volunteerActivityDB.get(year - BASE_YEAR).getList();
		List<VolAct> cloneList = new ArrayList<VolAct>();
		
		for(VolAct va: volActList)
			cloneList.add(new VolAct(va));
		
		return cloneList;		
	}
	
	String getVolunteerActivities(int year)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<VolAct>>(){}.getType();
			
		String response = gson.toJson(volunteerActivityDB.get(year - BASE_YEAR).getList(), listtype);
		return response;	
	}
	
	List<VolAct> getVolunteerActivities(int year, ONCVolunteer v)
	{
		List<VolAct> volList = new ArrayList<VolAct>();
		
		for(VolAct va : volunteerActivityDB.get(year - BASE_YEAR).getList())
			if(va.getVolID() == v.getID())
				volList.add(va);
		
		return volList;
	}
	
	//method returns number of volunteers who have signed up for an activity in a given year
	int getVolunteerCount(int year, Activity a)
	{
		int volCount = 0;
		for(VolAct va : volunteerActivityDB.get(year - BASE_YEAR).getList())
			if(va.getActID() == a.getID())
				volCount += va.getQty();
				
		return volCount;
	}
	
	//method examines each activity in the parameter list. If the activity already exists and is unmodified, 
	//no action is taken. If the activity exists but the comment or quantity is modified, an update occurs.
	//If the activity does not exist, a new activity is added.
	List<String> processActivityList(int year, List<VolAct> inputVAList)
	{
		List<String> clientNotificationList = new LinkedList<String>();
			
		List<VolAct> vaList = volunteerActivityDB.get(year - BASE_YEAR).getList();
		for(VolAct va : inputVAList)
		{
			int index = 0;
			while(index < vaList.size())
			{
				VolAct volAct = vaList.get(index);
				if(volAct.getVolID() == va.getVolID() && volAct.getActID() == va.getActID())
				{
					//action matches, see if comment and qty remained equal, if not update
					if(volAct.getQty() != va.getQty() || !volAct.getComment().equals(va.getComment()))
					{
						//change in quantity or comment detected, update
						String response = update(year, va);
						if(response != null)
							clientNotificationList.add(response);
					}
					
					break;
				}
				else
					index++;
			}
			
			if(index < vaList.size())	//volAct was not found, add it
				clientNotificationList.add(add(year, va));
		}
		
		return clientNotificationList;
	}
	
	void processAddedActivities(int year, List<VolAct> newVAList)
	{
		List<String> addedVAJsonMssgList = new ArrayList<String>();
		
		for(VolAct va : newVAList)
			addedVAJsonMssgList.add(add(year, va));
			
		if(!addedVAJsonMssgList.isEmpty())
		{
			//there were new volunteer activities, send list of new json's to clients
			ClientManager clientMgr = ClientManager.getInstance();
			clientMgr.notifyAllInYearClients(DBManager.getCurrentYear(), addedVAJsonMssgList);
		}
	}

	@Override
	String add(int year, String json) 
	{
		//Create a volunteer activity object for the new activity
		Gson gson = new Gson();
		VolAct addedActivity = gson.fromJson(json, VolAct.class);
				
		//set the new ID and time stamp for the new activity
		VolunteerActivityDBYear activityDBYear = volunteerActivityDB.get(year - BASE_YEAR);
		
		addedActivity.setID(activityDBYear.getNextID());
		
		activityDBYear.add(addedActivity);
		activityDBYear.setChanged(true);
				
		return "ADDED_VOLUNTEER_ACTIVITY" + gson.toJson(addedActivity, VolAct.class);
	}
	
	String add(int year, VolAct addedActivity) 
	{	
		//set the new ID for the VolAct
		VolunteerActivityDBYear activityDBYear = volunteerActivityDB.get(year - BASE_YEAR);
		addedActivity.setID(activityDBYear.getNextID());
		
		activityDBYear.add(addedActivity);
		activityDBYear.setChanged(true);
		
		Gson gson = new Gson();
		return  "ADDED_VOLUNTEER_ACTIVITY" + gson.toJson(addedActivity, VolAct.class);
	}
	
	List<String> addVolunteerActivityList(int year, String vaGroupJson)
	{
		List<String> returnMssgList = new ArrayList<String>();
		
		//un-bundle to list of VolAct objects
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<VolAct>>(){}.getType();
		List<VolAct> vaList = gson.fromJson(vaGroupJson, listtype);
		
		if(vaList.isEmpty())
			returnMssgList.add("NO_VOLUNTEER_ACTIVITIES");
		else
		{	
			//for each vol act in the list, add it to the db 
			for(VolAct va : vaList)
				returnMssgList.add(add(year, va));
			
			volunteerActivityDB.get(year - BASE_YEAR).setChanged(true);
					
			//if update was successful, need to q the change to all in-year clients
			//notify in year clients of change
		}
		
		return returnMssgList;
	}
	
	String update(int year, String json)
	{
		//Create a volunteer activity object
		Gson gson = new Gson();
		VolAct updatedVA = gson.fromJson(json, VolAct.class);
		
		//Find the position for the current driver being updated
		VolunteerActivityDBYear volActDBYear = volunteerActivityDB.get(year - BASE_YEAR);
		List<VolAct> vaList = volActDBYear.getList();
		int index = 0;
		while(index < vaList.size() && vaList.get(index).getID() != updatedVA.getID())
			index++;
		
		//Replace the current object with the update
		if(index < vaList.size())
		{
			vaList.set(index, updatedVA);
			volActDBYear.setChanged(true);
			return "UPDATED_VOLUNTEER_ACTIVITY" + gson.toJson(updatedVA, VolAct.class);
		}
		else
			return "UPDATE_FAILED";
	}
	
	String update(int year, VolAct updatedActivity)
	{
		//Find the position for the current activity being updated
		VolunteerActivityDBYear volActDBYear = volunteerActivityDB.get(year - BASE_YEAR);
		List<VolAct> volActList = volActDBYear.getList();
		int index = 0;
		while(index < volActList.size() && volActList.get(index).getID() != updatedActivity.getID())
			index++;
		
		//Replace the current object with the update
		if(index < volActList.size())
		{
			volActList.set(index, updatedActivity);
			volActDBYear.setChanged(true);
			
			Gson gson = new Gson();
			return "UPDATED_VOLUNTEER_ACTIVITY" + gson.toJson(updatedActivity, VolAct.class);
		}
		
		return null;
	}
	
	String delete(int year, String json)
	{
		//Create an object for the delete request
		Gson gson = new Gson();
		VolAct deletedVA = gson.fromJson(json, VolAct.class);
		
		//find and remove the deleted volunteer activity from the data base
		VolunteerActivityDBYear volActDBYear = volunteerActivityDB.get(year - BASE_YEAR);
		List<VolAct> vaList = volActDBYear.getList();
		
		int index = 0;
		while(index < vaList.size() && vaList.get(index).getID() != deletedVA.getID())
			index++;
		
		if(index < vaList.size())
		{
			vaList.remove(index);
			volActDBYear.setChanged(true);
			return "DELETED_VOLUNTEER_ACTIVITY" + json;
		}
		else
			return "DELETE_FAILED";	
	}
	
	String delete(int year, VolAct deletedVA)
	{
		Gson gson = new Gson();
		
		//find and remove the deleted volunteer activity from the data base
		VolunteerActivityDBYear volActDBYear = volunteerActivityDB.get(year - BASE_YEAR);
		List<VolAct> vaList = volActDBYear.getList();
		
		int index = 0;
		while(index < vaList.size() && vaList.get(index).getID() != deletedVA.getID())
			index++;
		
		if(index < vaList.size())
		{
			vaList.remove(index);
			volActDBYear.setChanged(true);
			return "DELETED_VOLUNTEER_ACTIVITY" + gson.toJson(deletedVA, VolAct.class);
		}
		else
			return "DELETE_FAILED";	
	}
	
	void processNewSignUpGeniusVolunteerActivities(int year, List<VolAct> sugNewVAList)
	{
		List<String> clientJsonMssgList = new ArrayList<String>();
		
		//add the new volunteer activities to the database
		for(VolAct va : sugNewVAList)
		{
			String response = add(year, va);
			if(response != null)
				clientJsonMssgList.add(response);
		}
		
		if(!clientJsonMssgList.isEmpty())
		{
			//there were new activities, send list of new json's to clients
			ClientManager clientMgr = ClientManager.getInstance();
			clientMgr.notifyAllInYearClients(DBManager.getCurrentYear(), clientJsonMssgList);
		}
	}
	
	void processUpdatedSignUpGeniusVolunteerActivities(int year, List<VolAct> sugModVAList)
	{
		List<String> clientJsonMssgList = new ArrayList<String>();
		
		//update the volunteer activities in the database
		for(VolAct va : sugModVAList)
		{
//			System.out.println(String.format("ServVADB.processUpdatedVA's: updating VA id=%d, volID=%d, actID=%d, qty=%d, comment=%s",
//					va.getID(), va.getVolID(), va.getActID(), va.getQty(), va.getComment()));
			
			String response = update(year, va);
			if(response != null)
			{
				clientJsonMssgList.add(response);
//				System.out.println("ServVADB.processUpdatedVA's: updating VA: " + response);
			}
		}
		
		if(!clientJsonMssgList.isEmpty())
		{
			//there were updated activities, send list of new json's to clients
			ClientManager clientMgr = ClientManager.getInstance();
			clientMgr.notifyAllInYearClients(DBManager.getCurrentYear(), clientJsonMssgList);
		}
		
//		System.out.println(String.format("ServVADB.processUpdatedVA's: clientJsonMssgList size=%d", clientJsonMssgList.size()));
	}
	
	void processDeletedSignUpGeniusVolunteerActivities(int year, List<VolAct> sugDelVAList)
	{
		List<String> clientJsonMssgList = new ArrayList<String>();
		
		//remove volunteer activities in the database
		for(VolAct va : sugDelVAList)
		{
			String response = delete(year, va);
			if(response != null)
				clientJsonMssgList.add(response);
		}
		
		if(!clientJsonMssgList.isEmpty())
		{
			//there were deleted activities, send list of new json's to clients
			ClientManager clientMgr = ClientManager.getInstance();
			clientMgr.notifyAllInYearClients(DBManager.getCurrentYear(), clientJsonMssgList);
		}
	}
	
	@Override
	void createNewYear(int newYear)
	{
		//create a new VolunteerActivity data base year for the year provided in the newYear parameter
		//The activity db year list is copied from the prior year, assuming it exists. All start
		//and end times are adjusted for the change in year
		VolunteerActivityDBYear newActivityDBYear = new VolunteerActivityDBYear();

		volunteerActivityDB.add(newActivityDBYear);
		newActivityDBYear.setChanged(true);	//mark this db for persistent saving on the next save event
	}

	@Override
	void addObject(int year, String[] nextLine)
	{
		VolunteerActivityDBYear volunteerActivityDBYear = volunteerActivityDB.get(year - BASE_YEAR);
		volunteerActivityDBYear.add(new VolAct(nextLine));	
	}
/*	
	void poplulateVolActDBAndSave()
	{
		try
		{
			ServerVolunteerDB volDB = ServerVolunteerDB.getInstance();
			String[] header = {"ID", "Vol ID" ,"Act ID","Genius ID", "Qty", "Comment"};
			
			for(int year=2012; year < 2018; year++)
			{
				List<ONCVolunteer> volList = volDB.clone(year);
				System.out.println(String.format("SVADB.pop: clone size= %d, year= %d", volList.size(), year));
				
				for(ONCVolunteer v : volList)
					for(VolunteerActivity va : v.getActivityList())
						add(year, new VolAct(-1, v.getID(), va.getID(), va.getGeniusID(),
								v.getQty(), va.getComment()));
				
				//save the volAct list to the proper year's csv file
				String path = String.format("%s/%dDB/VolunteerActivityDB.csv", System.getProperty("user.dir"), year);
				
				VolunteerActivityDBYear volunteerActivityDBYear = volunteerActivityDB.get(year - BASE_YEAR);
				exportDBToCSV(volunteerActivityDBYear.getList(), header, path);	
			}
		}
		catch (FileNotFoundException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	}
*/	
	@Override
	public void signUpDataReceived(SignUpEvent event)
	{
		if(event.type() == SignUpEventType.UPDATED_VOLUNTEER_ACTIVITIES)
		{
			//process list of updated volunteer activities.
			@SuppressWarnings("unchecked")
			List<VolAct> updatedVolActList = (List<VolAct>) event.getSignUpObject();
			List<String> clientJsonMssgList = new ArrayList<String>();
			
			//add the updated volunteers to the database
			for(VolAct va : updatedVolActList)
			{
				String response = update(DBManager.getCurrentYear(), va);
				if(response != null)
					clientJsonMssgList.add(response);
//				System.out.println(String.format("ServVolDB.newAndMod: updateVol: %s %s, id= %d",
//						v.getFirstName(), v.getLastName(), v.getID()));
			}
			
			if(!clientJsonMssgList.isEmpty())
			{
				//there were updates, send list of updated json's to clients
				ClientManager clientMgr = ClientManager.getInstance();
				clientMgr.notifyAllInYearClients(DBManager.getCurrentYear(), clientJsonMssgList);
			}
		}
		else if(event.type() == SignUpEventType.NEW_VOLUNTEER_ACTIVITIES)
		{
			//print the new volunteer activities list
			@SuppressWarnings("unchecked")
			List<VolAct> newVolActList = (List<VolAct>) event.getSignUpObject();
			List<String> clientJsonMssgList = new ArrayList<String>();
			
			//add the updated volunteers to the database
			for(VolAct va : newVolActList)
			{
				String response = add(DBManager.getCurrentYear(), va);
				if(response != null)
					clientJsonMssgList.add(response);
				
//				System.out.println(String.format("ServVolDB.newAndMod: newVol: %s %s, id= %d",
//						v.getFirstName(), v.getLastName(), v.getID()));
			}
			
			if(!clientJsonMssgList.isEmpty())
			{
				//there were new activities, send list of new json's to clients
				ClientManager clientMgr = ClientManager.getInstance();
				clientMgr.notifyAllInYearClients(DBManager.getCurrentYear(), clientJsonMssgList);
			}
		}
	}

	@Override
	void save(int year)
	{
		 VolunteerActivityDBYear volunteerActivityDBYear = volunteerActivityDB.get(year - BASE_YEAR);
		 
		 if(volunteerActivityDBYear.isUnsaved())
		 {
			String[] header = {"ID", "Vol ID" ,"Act ID","Genius ID", "Qty", "Comment"};
			 
			String path = String.format("%s/%dDB/VolunteerActivityDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(volunteerActivityDBYear.getList(), header, path);
			volunteerActivityDBYear.setChanged(false);
		 }
	}
	
	private class VolunteerActivityDBYear extends ServerDBYear
	{
		private List<VolAct> volunteerActivityList;
	    	
	    VolunteerActivityDBYear()
	    {
	    		super();
	    		volunteerActivityList = new ArrayList<VolAct>();
	    }
	    
	    //getters
	    List<VolAct> getList() { return volunteerActivityList; }
	    
	    void add(VolAct addedActivity) { volunteerActivityList.add(addedActivity); }
	}
}
