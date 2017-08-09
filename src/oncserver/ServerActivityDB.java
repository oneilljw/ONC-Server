package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import ourneighborschild.VolunteerActivity;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerActivityDB extends ServerSeasonalDB
{
	private static final int ACTIVITY_DB_HEADER_LENGTH = 16;
	
	private static List<ActivityDBYear> activityDB;
	private static ServerActivityDB instance = null;

	private ServerActivityDB() throws FileNotFoundException, IOException
	{
		//create the activity data bases for TOTAL_YEARS number of years
		activityDB = new ArrayList<ActivityDBYear>();

		//populate the data base for the last TOTAL_YEARS from persistent store
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the activity list for each year
			ActivityDBYear activityDBYear = new ActivityDBYear(year);
									
			//add the list of activities for the year to the db
			activityDB.add(activityDBYear);
									
			//import the activities from persistent store
			importDB(year, String.format("%s/%dDB/ActivityDB.csv",
					System.getProperty("user.dir"),
						year), "Activity DB",ACTIVITY_DB_HEADER_LENGTH);
		
			//set the next id
			activityDBYear.setNextID(getNextID(activityDBYear.getList()));
		}
	}
	
	public static ServerActivityDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerActivityDB();
		
		return instance;
	}
	
	//Search the database for the activites for a specified year Return a json list
	String getActivities(int year)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<VolunteerActivity>>(){}.getType();
			
		String response = gson.toJson(activityDB.get(year - BASE_YEAR).getList(), listtype);
		return response;	
	}
	
	//creates a list of volunteer activities based on stored string of activity ID's 
	//separated by the '_' character.
	List<VolunteerActivity> createActivityList(int year, String zActivities)
	{
		ActivityDBYear activityDBYear = activityDB.get(year - BASE_YEAR);
		List<VolunteerActivity> activityList = activityDBYear.getList();
		
		List<VolunteerActivity> volActList = new LinkedList<VolunteerActivity>();
		
		String[] activityParts = zActivities.split("_");
		for(String zActivity : activityParts)
		{
			int index = 0;
			while(index < activityList.size() && activityList.get(index).getID() != Integer.parseInt(zActivity))
				index++;
			
			if(index < activityList.size())
				volActList.add(activityList.get(index));
		}
		
		return volActList;
	}
	
	static HtmlResponse getActivityJSONP(int year, String name, String callbackFunction)
	{		
		Gson gson = new Gson();
		List<VolunteerActivity> searchList = activityDB.get(year - BASE_YEAR).getList();

		String response;
		int index=0;
		while(index < searchList.size() && !searchList.get(index).getName().equalsIgnoreCase(name))
			index++;
		
		if(index< searchList.size())
			response = gson.toJson(searchList.get(index), VolunteerActivity.class);
		else
			response = "{\"id\":-1}";	//send back id = -1, meaning not found
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HTTPCode.Ok);		
	}

	@Override
	String add(int year, String json) 
	{
		//Need to change this to add a check to see if the activity already exists, 
		//similar to what we do for agents.
		
		//Create a volunteer activity object for the new activity
		Gson gson = new Gson();
		VolunteerActivity addedActivity = gson.fromJson(json, VolunteerActivity.class);
				
		//set the new ID and timestamp for the new activity
		ActivityDBYear activityDBYear = activityDB.get(year - BASE_YEAR);
		
		addedActivity.setID(activityDBYear.getNextID());
		addedActivity.setDateChanged(new Date());
		
		activityDBYear.add(addedActivity);
		activityDBYear.setChanged(true);
				
		return "ADDED_ACTIVITY" + gson.toJson(addedActivity, VolunteerActivity.class);
	}
	
	String update(int year, String json)
	{
		//Create a volunteer activity object for the updated driver
		Gson gson = new Gson();
		VolunteerActivity updatedActivity = gson.fromJson(json, VolunteerActivity.class);
		updatedActivity.setDateChanged(new Date());
		
		//Find the position for the current activity being updated
		ActivityDBYear activityDBYear = activityDB.get(year - BASE_YEAR);
		List<VolunteerActivity> activityList = activityDBYear.getList();
		int index = 0;
		while(index < activityList.size() && activityList.get(index).getID() != updatedActivity.getID())
			index++;
		
		//Replace the current object with the update
		if(index < activityList.size())
		{
			activityList.set(index, updatedActivity);
			activityDBYear.setChanged(true);
			return "UPDATED_ACTIVITY" + gson.toJson(updatedActivity, VolunteerActivity.class);
		}
		else
			return "UPDATE_FAILED";
	}
	
	String delete(int year, String json)
	{
		//Create an object for the delete request
		Gson gson = new Gson();
		VolunteerActivity deletedActivity = gson.fromJson(json, VolunteerActivity.class);
		
		//find and remove the deleted activity from the data base
		ActivityDBYear activityDBYear = activityDB.get(year - BASE_YEAR);
		List<VolunteerActivity> activityList = activityDBYear.getList();
		
		int index = 0;
		while(index < activityList.size() && activityList.get(index).getID() != deletedActivity.getID())
			index++;
		
		if(index < activityList.size())
		{
			activityList.remove(index);
			activityDBYear.setChanged(true);
			return "DELETED_ACTIVITY" + json;
		}
		else
			return "DELETE_FAILED";	
	}


	@Override
	void addObject(int year, String[] nextLine)
	{
		ActivityDBYear activityDBYear = activityDB.get(year - BASE_YEAR);
		activityDBYear.add(new VolunteerActivity(nextLine));	
	}

	@Override
	void createNewYear(int newYear)
	{
		//create a new Activity data base year for the year provided in the newYear parameter
		//The activity db year list is copied from the prior year, assuming it exists. All start
		//and end times are adjusted for the change in year
		ActivityDBYear newActivityDBYear = new ActivityDBYear(newYear);
		ActivityDBYear priorActivityDBYear = activityDB.get(newYear-1-BASE_YEAR);
		
		for(VolunteerActivity activity : priorActivityDBYear.getList())
		{
			VolunteerActivity newYearActivity = new VolunteerActivity(activity);
			
			newYearActivity.setStartDate(updateDateForNewYear(newYear, activity.getStartDate()));
			newYearActivity.setEndDate(updateDateForNewYear(newYear, activity.getEndDate()));
			
			System.out.println(String.format("ServerActDB.createNewYear: act %s, prior start %s, curr start %s",
					newYearActivity.getName(), activity.getStartDate(), newYearActivity.getStartDate()));
			
			newActivityDBYear.add(newYearActivity);
		}
		
		activityDB.add(newActivityDBYear);
		newActivityDBYear.setChanged(true);	//mark this db for persistent saving on the next save event
	}
	
	String updateDateForNewYear(int newYear, String priorDate)
	{
		Calendar lastyearDate = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("M/d/yy");
		
		try 
		{
			lastyearDate.setTime(sdf.parse(priorDate));
		}
		catch (ParseException e) 
		{
			e.printStackTrace();
		}
		
		//add 52 weeks or 364 days such that its the same week 
		lastyearDate.add(Calendar.DAY_OF_YEAR, 364);	
		
		return sdf.format(lastyearDate.getTime());
	}
	
	public static boolean isLeapYear(int year)
	{
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.YEAR, year);
		return cal.getActualMaximum(Calendar.DAY_OF_YEAR) > 365;
	}
	
	private class ActivityDBYear extends ServerDBYear
	{
		private List<VolunteerActivity> activityList;
	    	
	    ActivityDBYear(int year)
	    {
	    	super();
	    	activityList = new ArrayList<VolunteerActivity>();
	    }
	    
	    //getters
	    List<VolunteerActivity> getList() { return activityList; }
	    
	    void add(VolunteerActivity addedActivity) { activityList.add(addedActivity); }
	}

	@Override
	void save(int year)
	{
		 ActivityDBYear activityDBYear = activityDB.get(year - BASE_YEAR);
		 
		 if(activityDBYear.isUnsaved())
		 {
			 String[] header = {"ID", "Category" ,"Name","Start Date","Start Time",
					 			"End Date","End Time", "Location", "Description", "Open", "Notify",
					 			"Timestamp", "Changed By", "SL Pos","SL Message", "SL Changed By"};
			 
			String path = String.format("%s/%dDB/ActivityDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(activityDBYear.getList(), header, path);
			activityDBYear.setChanged(false);
		}
	}	
}
