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
import ourneighborschild.GeniusSignUps;
import ourneighborschild.VolunteerActivity;

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
		Type listtype = new TypeToken<ArrayList<VolunteerActivity>>(){}.getType();
			
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
	
	List<VolunteerActivity> clone(int year)
	{
		List<VolunteerActivity> actList = activityDB.get(year - BASE_YEAR).getList();
		List<VolunteerActivity> cloneList = new ArrayList<VolunteerActivity>();
		
		for(VolunteerActivity va : actList)
			cloneList.add(new VolunteerActivity(va));
		
		return cloneList;		
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
	//creates a list of volunteer activities based on stored string of activity ID's 
	//separated by the '_' character.
	List<VolunteerActivity> createActivityList(int year, String zActivities, String zComments)
	{
		ActivityDBYear activityDBYear = activityDB.get(year - BASE_YEAR);
		List<VolunteerActivity> activityList = activityDBYear.getList();
		
		List<VolunteerActivity> volActList = new LinkedList<VolunteerActivity>();

		if(!zActivities.isEmpty())
		{
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
						addVolunteerCommentsToActivity(volActivity, zComments);

					volActList.add(volActivity);
				}
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
	
	String add(int year, VolunteerActivity addedActivity) 
	{	
		//set the new ID and time stamp for the new activity
		ActivityDBYear activityDBYear = activityDB.get(year - BASE_YEAR);
		
		addedActivity.setID(activityDBYear.getNextID());
		addedActivity.setDateChanged(new Date());
		
		activityDBYear.add(addedActivity);
		activityDBYear.setChanged(true);
		
		Gson gson = new Gson();
		return  "ADDED_ACTIVITY" + gson.toJson(addedActivity, VolunteerActivity.class);
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
	
	String update(int year, VolunteerActivity updatedActivity)
	{
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
			
			Gson gson = new Gson();
			return "UPDATED_ACTIVITY" + gson.toJson(updatedActivity, VolunteerActivity.class);
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
			List<VolunteerActivity> updatedVAList = (List<VolunteerActivity>) event.getSignUpObject();
			List<String> clientJsonMssgList = new ArrayList<String>();
			
			//add the updated activities to the database
			for(VolunteerActivity va : updatedVAList)
			{
				String response = update(DBManager.getCurrentYear(), va);
				if(response != null)
					clientJsonMssgList.add(response);
//				System.out.println(String.format("ServVolDB.newAndMod: updateAct: %s, id= %d, geniusid= %d, start= %d, end= %d",
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
			List<VolunteerActivity> newVAList = (List<VolunteerActivity>) event.getSignUpObject();
			List<String> clientJsonMssgList = new ArrayList<String>();
			
			for(VolunteerActivity va : newVAList)
			{
				String response = add(DBManager.getCurrentYear(), va);
				if(response != null)
					clientJsonMssgList.add(response);
				
//				System.out.println(String.format("ServVolDB.newAndMod: newAct: %s, id= %d, start= %d, end= %d",
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
			 String[] header = {"ID", "Genius ID", "Category" ,"Name","StartTimeMillis",
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
	
	private static class VolunteerActivityDateComparator implements Comparator<VolunteerActivity>
	{
		@Override
		public int compare(VolunteerActivity o1, VolunteerActivity o2)
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
