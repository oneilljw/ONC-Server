package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import ourneighborschild.VolunteerActivity;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerActivityDB extends ServerSeasonalDB
{
	private static final int ACTIVITY_DB_HEADER_LENGTH = 16;
	private static final int COMMENT_ACTIIVTY_IDENTIFIER_LENGTH = 4;
	
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
						year), "Activity DB", ACTIVITY_DB_HEADER_LENGTH);
			
			//sort the search list by Start date
			Collections.sort(activityDBYear.getList(), new VolunteerActivityDateComparator());
		
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
	
	static int size(int year)
	{
		return activityDB.get(year - BASE_YEAR).getList().size();
	}
	
	static HtmlResponse getActivityDayJSONP(int year, String callbackFunction)
	{		
		Gson gson = new Gson();
		Type listOfActivities = new TypeToken<ArrayList<ActivityDay>>(){}.getType();
		
		//put the activity database for the year in chronological order by start date
		List<VolunteerActivity> searchList = new ArrayList<VolunteerActivity>();
		for(VolunteerActivity va : activityDB.get(year-BASE_YEAR).getList())
			if(va.isOpen())
				searchList.add(va);
		
		Collections.sort(searchList, new VolunteerActivityDateComparator());
		
		//iterate thru the list and create a Activity Day object for each activity
		String startDate = searchList.get(0).getStartDate();
		int outerIndex = 0;
		while(outerIndex < searchList.size())
		{
			List<ActivityDay> dayList = new ArrayList<ActivityDay>();
		}
		
		
		
		
		String response = gson.toJson(searchList, listOfActivities);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HTTPCode.Ok);		
	}
	
	static HtmlResponse getActivitesJSONP(int year, String callbackFunction)
	{		
		Gson gson = new Gson();
		Type listOfActivities = new TypeToken<ArrayList<VolunteerActivity>>(){}.getType();
		
		List<VolunteerActivity> searchList = new ArrayList<VolunteerActivity>();
		for(VolunteerActivity va : activityDB.get(year-BASE_YEAR).getList())
			if(va.isOpen())
				searchList.add(va);
		
		String response = gson.toJson(searchList, listOfActivities);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HTTPCode.Ok);		
	}
	
	//creates a list of volunteer activities based on stored string of activity ID's 
	//separated by the '_' character.
	List<VolunteerActivity> createActivityList(int year, String zActivities, String zComments)
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
			{
				//create a deep copy of the activity
				VolunteerActivity volActivity = new VolunteerActivity(activityList.get(index));
				
				//see if there are volunteer comments that need to be added to the activity
				if(!zComments.isEmpty())
				{
					addVolunteerCommentsToActivity(volActivity, zComments);
//					System.out.println(String.format("ServActDB.createActList: vaID: %d, volunteer activity comment: %s",
//							volActivity.getID(), volActivity.getComment()));
				}
				
				volActList.add(volActivity);
			}
		}
		
		return volActList;
	}
	
	//creates a list of volunteer activities based on a parameter map from the web site.
	//The parameter map contains the activity check boxes and activity comments
	List<VolunteerActivity> createActivityList(int year, Map<String, String> actMap)
	{
//		Set<String> actkeyset = actMap.keySet();
//		for(String key:actkeyset)
//			System.out.println(String.format("ActivityDB actMapKey= %s, actMapvalue= %s", key, (String)actMap.get(key)));
		
		ActivityDBYear activityDBYear = activityDB.get(year - BASE_YEAR);
		List<VolunteerActivity> activityList = activityDBYear.getList();
		List<VolunteerActivity> volActList = new LinkedList<VolunteerActivity>();
		
		//iterate thru the activity map and add the activities the volunteer sign up for plus 
		//their comments
		for(String key : actMap.keySet())
		{
			if(key.startsWith("actckbox"))
			{	
				for(VolunteerActivity va : activityList)
				{
					//check if the activity check box value == the vol activity id
					if(Integer.parseInt(actMap.get(key)) == va.getID())
					{
						//create a deep copy of the activity and add the associated comment with 
						//the actckbox key
						VolunteerActivity volActivity = new VolunteerActivity(va);
						volActivity.setComment(actMap.get("actcomment" + key.substring(8)));
						volActList.add(volActivity);
						break;
					}
				}
			}
		}
		
//		for(VolunteerActivity va: volActList)
//			System.out.println(String.format("ServerActDB.createActList act= %s, comment= %s",
//					va.getName(), va.getComment()));
			
		return volActList;
	}
	
	void addVolunteerCommentsToActivity(VolunteerActivity va, String zComments)
	{
		String[] commentsArray = zComments.split("_");
		
		int index = 0;
		while(index < commentsArray.length && 
			   commentsArray[index].length() >= COMMENT_ACTIIVTY_IDENTIFIER_LENGTH)
		{
			///each valid comment starts with a 4 character numeric activity identifier,
			//break it out and test it's validity
			String comment = commentsArray[index++];
			
			String actID = comment.substring(0, COMMENT_ACTIIVTY_IDENTIFIER_LENGTH);
			String actComment = comment.substring(COMMENT_ACTIIVTY_IDENTIFIER_LENGTH);
			
//			System.out.println(String.format("ServActDB.addVolComments array: vaID: %d, commentArray size= %d, comment 0: %s, actID= %s, actCommnet= %s",
//					va.getID(), commentsArray.length, commentsArray[0], actID, actComment));
			
			if(isNumeric(actID) && Integer.parseInt(actID) == va.getID())
			{
//				System.out.println(String.format("ServActDB.addVolComments: vaID: %d, set comment: %s",
//						va.getID(), actComment));
				
				va.setComment(actComment);
				break;
			}
		}
	}
/*	
	static HtmlResponse getActivitiesJSONP(int year, String name, String callbackFunction)
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
*/
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
					 			"End Date","End Time", "Location", "Description", 
					 			"Open", "Notify",
					 			"Timestamp", "Changed By", "SL Pos","SL Message", "SL Changed By"};
			 
			String path = String.format("%s/%dDB/ActivityDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(activityDBYear.getList(), header, path);
			activityDBYear.setChanged(false);
		}
	}
	
	private static class VolunteerActivityDateComparator implements Comparator<VolunteerActivity>
	{
		@Override
		public int compare(VolunteerActivity o1, VolunteerActivity o2)
		{
			SimpleDateFormat sdf = new SimpleDateFormat("M/d/yy");
			Date o1StartDate, o2StartDate;
			try 
			{
				o1StartDate = sdf.parse(o1.getStartDate());
				o2StartDate = sdf.parse(o2.getStartDate());
			} 
			catch (ParseException e) 
			{
				return 0;
			}
				
			return o1StartDate.compareTo(o2StartDate);
		}
	}
}
