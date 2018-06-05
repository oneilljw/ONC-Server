package oncserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

import ourneighborschild.SignUp;
import ourneighborschild.VolAct;
import ourneighborschild.GeniusSignUps;
import ourneighborschild.ONCVolunteer;
import ourneighborschild.Activity;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

public class ServerActivityDB extends ServerSeasonalDB implements SignUpListener
{
	private static final int ACTIVITY_DB_HEADER_LENGTH = 15;
	private static final int COMMENT_ACTIIVTY_IDENTIFIER_LENGTH = 4;
	private static final String GENIUS_STATUS_FILENAME = "GeniusSignUps.csv";
	private static final int SIGNUP_RECORD_LENGTH = 5;
	
	private static ServerActivityDB instance = null;
	private static SignUpGeniusIF geniusIF;
	
	private static List<ActivityDBYear> activityDB;
	
//	private GeniusStatus geniusStatus;
	
//	private List<SignUp> signUpList;
//	private long lastSignUpListImportTime;
	
	private GeniusSignUps geniusSignUps;
	private boolean bSignUpsSaveRequested;

	private ServerActivityDB() throws FileNotFoundException, IOException
	{
		//create the activity data bases for TOTAL_YEARS number of years
		activityDB = new ArrayList<ActivityDBYear>();
		
		geniusIF = SignUpGeniusIF.getInstance();
		geniusIF.addSignUpListener(this);
		
		//create the list of genius sign-ups. The list of sign ups will populate on
		//a callback from the separate thread that fetches signups from SignUp Genius
		geniusSignUps = new GeniusSignUps();
		importGeniusSignUps(String.format("%s/PermanentDB/%s", System.getProperty("user.dir"), GENIUS_STATUS_FILENAME), "Genius Sing Ups", SIGNUP_RECORD_LENGTH);
		bSignUpsSaveRequested = false;
		
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
		
		//populate the list of sign ups. The SignUp Genius interface will create a thread
		//that will fetch current signups and the callback thru the listener will populate
		//the list
//		geniusIF.requestSignUpList();
	}
	
	public static ServerActivityDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerActivityDB();
		
		return instance;
	}
	
	//Search the database for the activities for a specified year Return a json list
	String getActivities(int year)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<Activity>>(){}.getType();
			
		String response = gson.toJson(activityDB.get(year - BASE_YEAR).getList(), listtype);
		return response;	
	}
	
	
	//Return the list of sign-ups
	String getSignUps()
	{
		Gson gson = new Gson();
//		Type listtype = new TypeToken<ArrayList<SignUp>>(){}.getType();
				
		String response = gson.toJson(geniusSignUps, GeniusSignUps.class);
		return response;	
	}
	
	static int size(int year)
	{
		return activityDB.get(year - BASE_YEAR).getList().size();
	}
	
	List<Activity> clone(int year)
	{
		List<Activity> actList = activityDB.get(year - BASE_YEAR).getList();
		List<Activity> cloneList = new ArrayList<Activity>();
		
		for(Activity va : actList)
			cloneList.add(new Activity(va));
		
		return cloneList;		
	}
	
	Activity findActivity(int year, int actID)
	{
		List<Activity> actList = activityDB.get(year - BASE_YEAR).getList();
		int index = 0;
		while(index < actList.size() && actList.get(index).getID() != actID)	
			index++;
		
		return index < actList.size() ? actList.get(index) : null;
	}
	
	Activity findActivity(int year, long actGeniusID)
	{
		List<Activity> actList = activityDB.get(year - BASE_YEAR).getList();
		int index = 0;
		while(index < actList.size() && actList.get(index).getGeniusID() != actGeniusID)	
			index++;
		
		return index < actList.size() ? actList.get(index) : null;
	}
/*	
	static HtmlResponse getActivityDayJSONP(int year, String callbackFunction)
	{		
		Gson gson = new Gson();
		Type listOfActivities = new TypeToken<ArrayList<ActivityDay>>(){}.getType();
		
		//put the activity database for the year in chronological order by start date.
		//create the list of ActivityDay's
		List<VolunteerActivity> searchList = new ArrayList<VolunteerActivity>();
		for(VolunteerActivity va : activityDB.get(year-BASE_YEAR).getList())
			if(va.isOpen())
				searchList.add(va);
		
		Collections.sort(searchList, new VolunteerActivityDateComparator());
		List<ActivityDay> dayList = new ArrayList<ActivityDay>();
		
		//get the first start date and activity. Create a new Activity Day and add teh
		//first activity to its activity list.
		int searchIndex = 0, dayCount = 0;
		while(searchIndex < searchList.size())
		{
			//create a new ActivityDay object and populate all the activities
			//from the search list that start on the same day
			String startDate = searchList.get(searchIndex).getStartDate();
			ActivityDay ad = new ActivityDay(dayCount++, startDate);
		
			while(searchIndex < searchList.size() && 
					searchList.get(searchIndex).getStartDate().equals(startDate))
				ad.addActivity(searchList.get(searchIndex++));
				
			dayList.add(ad);
		}
		
		//create and wrap the json in the callback function per the JSONP protocol
		String response = gson.toJson(dayList, listOfActivities);
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
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
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
*/
	/******
	 * Returns an activity based on time. Finds the closest match from an activity. Used
	 * when volunteer registers in the warehouse and didn't already register thru sign-up genius.
	 * The closest match is defined as the activity with a start time that is the closest to the
	 * time passed as a parameter
	 * @param year - int of the year activity match is for
	 * @param time - long of UTC in milliseconds used for match search
	 * @param tolerance - range in minutes search will use before and after start time and end time for a match
	 * @return
	 */
	Activity matchActivity(int year, long time)
	{
		ActivityDBYear activityDBYear = activityDB.get(year - BASE_YEAR);
		List<Activity> activityList = activityDBYear.getList();
		
		Activity closestActivity = null;
		long closest_time = -1;
		for(Activity act : activityList)
		{
			//calculate the time difference
			long diff = act.getStartDate() - time;
			long abs_diff = diff < 0 ? -diff : diff;
			
			if(closest_time < 0 || closest_time > abs_diff)
			{
				closest_time = abs_diff;
				closestActivity = act;
			}
		}
			
		return closestActivity;
	}
	
	//creates a list of vol activities based on a parameter map from the web site.
	//The parameter map contains the activity check boxes and activity comments
	List<VolAct> createActivityList(int year, Map<String, String> actMap, ONCVolunteer v)
	{
//		Set<String> actkeyset = actMap.keySet();
//		for(String key:actkeyset)
//			System.out.println(String.format("ActivityDB actMapKey= %s, actMapvalue= %s", key, (String)actMap.get(key)));
		
		ActivityDBYear activityDBYear = activityDB.get(year - BASE_YEAR);
		List<Activity> activityList = activityDBYear.getList();
		
		List<VolAct> volActList = new LinkedList<VolAct>();
		
		//iterate thru the activity map and add the activities the volunteer sign up for plus 
		//their comments
		for(String key : actMap.keySet())
		{
			if(key.startsWith("actckbox"))
			{	
				for(Activity va : activityList)
				{
					//check if the activity check box value == the vol activity id
					if(Integer.parseInt(actMap.get(key)) == va.getID())
					{
						//for each activity in the map, determine if it's already
						VolAct volAct = new VolAct(-1, v.getID(), va.getID(), va.getGeniusID(), 1, 
								actMap.get("actcomment" + key.substring(8)));
					
						volActList.add(volAct);
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
		Activity addedActivity = gson.fromJson(json, Activity.class);
				
		//set the new ID and timestamp for the new activity
		ActivityDBYear activityDBYear = activityDB.get(year - BASE_YEAR);
		
		addedActivity.setID(activityDBYear.getNextID());
		addedActivity.setDateChanged(new Date());
		
		activityDBYear.add(addedActivity);
		activityDBYear.setChanged(true);
				
		return "ADDED_ACTIVITY" + gson.toJson(addedActivity, Activity.class);
	}
	
	String add(int year, Activity addedActivity) 
	{	
		//set the new ID and time stamp for the new activity
		ActivityDBYear activityDBYear = activityDB.get(year - BASE_YEAR);
		
		addedActivity.setID(activityDBYear.getNextID());
		addedActivity.setDateChanged(new Date());
		
		activityDBYear.add(addedActivity);
		activityDBYear.setChanged(true);
		
		Gson gson = new Gson();
		return  "ADDED_ACTIVITY" + gson.toJson(addedActivity, Activity.class);
	}
	
	String update(int year, String json)
	{
		//Create a volunteer activity object for the updated driver
		Gson gson = new Gson();
		Activity updatedActivity = gson.fromJson(json, Activity.class);
		updatedActivity.setDateChanged(new Date());
		
		//Find the position for the current activity being updated
		ActivityDBYear activityDBYear = activityDB.get(year - BASE_YEAR);
		List<Activity> activityList = activityDBYear.getList();
		int index = 0;
		while(index < activityList.size() && activityList.get(index).getID() != updatedActivity.getID())
			index++;
		
		//Replace the current object with the update
		if(index < activityList.size())
		{
			activityList.set(index, updatedActivity);
			activityDBYear.setChanged(true);
			return "UPDATED_ACTIVITY" + gson.toJson(updatedActivity, Activity.class);
		}
		else
			return "UPDATE_FAILED";
	}
	
	String update(int year, Activity updatedActivity)
	{
		//Find the position for the current activity being updated
		ActivityDBYear activityDBYear = activityDB.get(year - BASE_YEAR);
		List<Activity> activityList = activityDBYear.getList();
		int index = 0;
		while(index < activityList.size() && activityList.get(index).getID() != updatedActivity.getID())
			index++;
		
		//Replace the current object with the update
		if(index < activityList.size())
		{
			activityList.set(index, updatedActivity);
			activityDBYear.setChanged(true);
			
			Gson gson = new Gson();
			return "UPDATED_ACTIVITY" + gson.toJson(updatedActivity, Activity.class);
		}
		
		return null;
	}
	
	String updateSignUp(String json)
	{
		//Create a sign-up object from the json
		Gson gson = new Gson();
		SignUp updatedSignUpReq = gson.fromJson(json, SignUp.class);
		
		//find the signUp and update it
		List<SignUp> signUpList = geniusSignUps.getSignUpList();
		int index = 0;
		while(index < signUpList.size() && signUpList.get(index).getSignupid() != updatedSignUpReq.getSignupid())
			index++;
		
		if(index < signUpList.size() && signUpList.get(index).getFrequency() != updatedSignUpReq.getFrequency())
		{
			signUpList.get(index).setFrequency(updatedSignUpReq.getFrequency());
			bSignUpsSaveRequested = true;
			return "UPDATED_SIGNUP" + gson.toJson(signUpList.get(index), SignUp.class);
		}
		else
			return "UPDATE_FAILED";
	}
	
	String delete(int year, String json)
	{
		//Create an object for the delete request
		Gson gson = new Gson();
		Activity deletedActivity = gson.fromJson(json, Activity.class);
		
		//find and remove the deleted activity from the data base
		ActivityDBYear activityDBYear = activityDB.get(year - BASE_YEAR);
		List<Activity> activityList = activityDBYear.getList();
		
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
		activityDBYear.add(new Activity(nextLine));	
	}

	@Override
	void createNewYear(int newYear)
	{
		//create a new Activity data base year for the year provided in the newYear parameter
		//The activity db year list is copied from the prior year, assuming it exists. All start
		//and end times are adjusted for the change in year
		ActivityDBYear newActivityDBYear = new ActivityDBYear(newYear);
/*		
		ActivityDBYear priorActivityDBYear = activityDB.get(newYear-1-BASE_YEAR);
		
		for(VolunteerActivity activity : priorActivityDBYear.getList())
		{
			VolunteerActivity newYearActivity = new VolunteerActivity(activity);
			
			newYearActivity.setStartDate(updateDateForNewYear(newYear, activity.getStartDate()));
			newYearActivity.setEndDate(updateDateForNewYear(newYear, activity.getEndDate()));
			
			newActivityDBYear.add(newYearActivity);
		}
*/		
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
	
	SignUp findSignUp(List<SignUp> searchList, int signupid)
	{
		int index = 0;
		while(index < searchList.size() && searchList.get(index).getSignupid() != signupid)
			index++;
		
		return index < searchList.size() ? searchList.get(index) : null;
	}
	 
	@Override
	public void signUpDataReceived(SignUpEvent event)
	{
		if(event.type() == SignUpEventType.SIGNUP_IMPORT)
		{
			//Set the import time for the list of sign=ups. Go thru each of the imported sign ups.
			//If it hasn't previously been imported, add it to the current list. That way we preserve
			//the frequency and last import time setting in each existing sign up
			GeniusSignUps geniusSignUpsImported = (GeniusSignUps) event.getSignUpObject();
			geniusSignUps.setLastSignUpListImportTime(geniusSignUpsImported.getLastSignUpListImportTime());
			
			//remove any signUps in the current list that are not in the imported list
			List<SignUp> currList = geniusSignUps.getSignUpList();
			Iterator<SignUp> i = currList.iterator();
			while (i.hasNext()) 
				if(findSignUp(geniusSignUpsImported.getSignUpList(), i.next().getSignupid()) == null)
					   i.remove();

			//add signUps from the imported list if they are not already in the currList.
			for(SignUp importedSU : geniusSignUpsImported.getSignUpList())
				if(findSignUp(currList, importedSU.getSignupid()) == null)
					currList.add(importedSU);
			
			bSignUpsSaveRequested = true;
			
			//DEBUG. The save won't be necessary during a season as at least one year will be unlocked
//			for(SignUp addedSU : geniusSignUps.getSignUpList())
//				System.out.println(String.format("ActDB.signUpDataRec: added %s, endtime %d", 
//							addedSU.getTitle(), addedSU.getEndtime()));
//			saveSignUps();
			
			Gson gson = new Gson();
			String clientSignUpJson = "UPDATED_GENIUS_SIGNUPS" + gson.toJson(geniusSignUps, GeniusSignUps.class);
			
			ClientManager clientMgr = ClientManager.getInstance();
			clientMgr.notifyAllClients(clientSignUpJson);
		}
		else if(event.type() == SignUpEventType.UPDATED_ACTIVITIES)
		{
			//process list of updated activities.
			@SuppressWarnings("unchecked")
			List<Activity> updatedVAList = (List<Activity>) event.getSignUpObject();
			List<String> clientJsonMssgList = new ArrayList<String>();
			
			//add the updated activities to the database
			for(Activity va : updatedVAList)
			{
				String response = update(DBManager.getCurrentYear(), va);
				if(response != null)
					clientJsonMssgList.add(response);
//				System.out.println(String.format("ServActDB.newAndMod: updateAct: %s, id= %d, geniusid= %d, start= %d, end= %d",
//						va.getName(), va.getID(), va.getGeniusID(), va.getStartDate(), va.getEndDate()));
			}
			
			if(!clientJsonMssgList.isEmpty())
			{
				//there were updates, send list of updated json's to clients
				ClientManager clientMgr = ClientManager.getInstance();
				clientMgr.notifyAllInYearClients(DBManager.getCurrentYear(), clientJsonMssgList);
			}
		}
		else if(event.type() == SignUpEventType.NEW_ACTIVITIES)
		{
			//print the new activities list
			@SuppressWarnings("unchecked")
			List<Activity> newVAList = (List<Activity>) event.getSignUpObject();
			List<String> clientJsonMssgList = new ArrayList<String>();
			
			for(Activity va : newVAList)
			{
				String response = add(DBManager.getCurrentYear(), va);
				if(response != null)
					clientJsonMssgList.add(response);
				
//				System.out.println(String.format("ServActDB.newAndMod: newAct: %s, id= %d, start= %d, end= %d",
//						va.getName(), va.getGeniusID(), va.getStartDate(), va.getEndDate()));
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
		 ActivityDBYear activityDBYear = activityDB.get(year - BASE_YEAR);
		 
		 if(activityDBYear.isUnsaved())
		 {
			 String[] header = {"ID", "Category" ,"Name","StartTimeMillis",
					 			"EndTimeMillis", "Location", "Description", "Open", "Notify", 
					 			"Timestamp", "Changed By", "SL Pos","SL Message", "SL Changed By"};
			 
			String path = String.format("%s/%dDB/ActivityDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(activityDBYear.getList(), header, path);
			activityDBYear.setChanged(false);
		 }
		
		 //test to see if genius status should be saved
		 if(bSignUpsSaveRequested)
			 saveSignUps();
	}
	
	void importGeniusSignUps(String path, String name, int length) throws FileNotFoundException, IOException
	{
		CSVReader reader = new CSVReader(new FileReader(path));
    		String[]nextLine, header;
    	
    		if((header = reader.readNext()) != null)	//Does file have records? 
    		{
    			//Read the User File
    			if(header.length == length)	//Does the record have the right # of fields? 
    			{
    				//first record only contains one field and is the last import time for the sign-up list
    				nextLine = reader.readNext();
    				if(nextLine != null && nextLine.length > 0)	// nextLine[] is an array of fields from the record
    				{
    					geniusSignUps.setLastSignUpListImportTime(nextLine[0].isEmpty() ? 0 : Long.parseLong(nextLine[0]));
    					while ((nextLine = reader.readNext()) != null)	// nextLine[] is an array of fields from the record
        					geniusSignUps.add(new SignUp(nextLine));
    				}
    				else
    				{
    					String error = String.format("%s first record error, length = %d", name, nextLine.length);
    					JOptionPane.showMessageDialog(null, error,  name + "Corrupted", JOptionPane.ERROR_MESSAGE);
    				}
    			}
    			else
    			{
    				String error = String.format("%s file corrupted, header length = %d", name, header.length);
    				JOptionPane.showMessageDialog(null, error,  name + "Corrupted", JOptionPane.ERROR_MESSAGE);
    			}		   			
    		}
    		else
    		{
    			String error = String.format("%s file is empty", name);
    			JOptionPane.showMessageDialog(null, error,  name + " Empty", JOptionPane.ERROR_MESSAGE);
    		}
    	
    		reader.close();
	}
/*	
	void forceSave(int year)
	{
		ActivityDBYear activityDBYear = activityDB.get(year - BASE_YEAR);
		 
		String[] header = {"ID", "Genius ID", "Category" ,"Name", "StartTimeMillis",
					 			 "EndTimeMillis", "Location", "Description", "Open", "Notify",
					 			"Timestamp", "Changed By", "SL Pos","SL Message", "SL Changed By"};
			 
		String path = String.format("%s/%dDB/ActivityDB.csv", System.getProperty("user.dir"), year);
		exportDBToCSV(activityDBYear.getList(), header, path);
	}
*/	
	
	void saveSignUps()
	{
		if(bSignUpsSaveRequested)
		{
			String[] header = {"Last Import Time", "SignUp ID" ,"Title", "End Date", "Frequency"};
			
			String[] firstRow = new String[1];
			firstRow[0] = Long.toString(geniusSignUps.getLastSignUpListImportTime());
			
			String path = String.format("%s/PermanentDB/%s", System.getProperty("user.dir"), GENIUS_STATUS_FILENAME);
			File oncwritefile = new File(path);
		
			try 
			{
				CSVWriter writer = new CSVWriter(new FileWriter(oncwritefile.getAbsoluteFile()));
				writer.writeNext(header);
				writer.writeNext(firstRow);
	    	
				for(SignUp su : geniusSignUps.getSignUpList())
					writer.writeNext(su.getExportRow());
				
				writer.close();
				
				bSignUpsSaveRequested = false;
			} 
			catch (IOException x)
			{
				System.err.format("IO Exception: %s%n", x.getMessage());
			}
		}
	}

	private class ActivityDBYear extends ServerDBYear
	{
		private List<Activity> activityList;
	    	
	    ActivityDBYear(int year)
	    {
	    		super();
	    		activityList = new ArrayList<Activity>();
	    }
	    
	    //getters
	    List<Activity> getList() { return activityList; }
	    
	    void add(Activity addedActivity) { activityList.add(addedActivity); }
	}
	
	private static class VolunteerActivityDateComparator implements Comparator<Activity>
	{
		@Override
		public int compare(Activity o1, Activity o2)
		{
			if(o1.getStartDate() < o2.getStartDate())
				return -1;
			else if(o1.getStartDate() > o2.getStartDate())
				return 1;
			else
				return 0;
		}
	}
}
