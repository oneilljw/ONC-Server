package oncserver;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

import javax.swing.SwingWorker;

import com.google.gson.Gson;

import ourneighborschild.Frequency;
import ourneighborschild.GeniusSignUps;
import ourneighborschild.ONCVolunteer;
import ourneighborschild.SignUp;
import ourneighborschild.SignUpActivity;
import ourneighborschild.VolAct;
import ourneighborschild.Activity;

public class SignUpGeniusIF
{
	private static final String API_KEY = "NGJMZlhzZm5SK3d4L002ODFyek9iQT09";
	private static final String SIGNUPS_URL = "https://api.signupgenius.com/v2/k/signups/created/active/?user_key=%s";
	private static final String SIGNUP_REPORT_URL = "https://api.signupgenius.com/v2/k/signups/report/%s/%d/?user_key=%s";
	
	private static SignUpGeniusIF instance;

	public static SignUpGeniusIF getInstance()
	{
		if(instance == null)
			instance = new SignUpGeniusIF();
		
		return instance;
	}

	void requestSignUpList()
	{
		SignUpGeniusVolunteerImporter importer = new SignUpGeniusVolunteerImporter();
		importer.execute();
	}
	
	void requestSignUpContent(SignUp signup, SignUpReportType reportType)
	{
		SignUpGeniusVolunteerImporter importer = new SignUpGeniusVolunteerImporter(signup, reportType);
		importer.execute();
	}
	
	void signUpSummaryReceived(GeniusSignUps geniusSignUps)
	{	
		this.fireSignUpDataChanged(this, SignUpEventType.SIGNUP_LIST_IMPORT, geniusSignUps);
	}
	
	void signUpContentReceived(SignUpEventType type, Object object)
	{
		this.fireSignUpDataChanged(this, type, object);
	}
	
	//List of registered listeners for Client change events
	protected ArrayList<SignUpListener> listeners;
   
	/** Register a listener for database DataChange events */
	synchronized public void addSignUpListener(SignUpListener l)
	{
		if (listeners == null)
   		listeners = new ArrayList<SignUpListener>();
   		listeners.add(l);
	}  

	/** Remove a listener for server DataChange */
	synchronized public void removeSignUpListener(SignUpListener l)
	{
   		if (listeners == null)
   			listeners = new ArrayList<SignUpListener>();
   		listeners.remove(l);
	}
   
	/** Fire a Data ChangedEvent to all registered listeners */
	@SuppressWarnings("unchecked")
	void fireSignUpDataChanged(Object source, SignUpEventType eventType, Object eventObject)
	{
   		// if we have no listeners, do nothing...
   		if(listeners != null && !listeners.isEmpty())
   		{
   			// create the event object to send
   			SignUpEvent event = new SignUpEvent(source, eventType, eventObject);

   			// make a copy of the listener list in case anyone adds/removes listeners
   			ArrayList<SignUpListener> targets;
   			synchronized (this) { targets = (ArrayList<SignUpListener>) listeners.clone(); }

   			// walk through the cloned listener list and call the dataRecevied method in each
   			for(SignUpListener l:targets)
   				l.signUpDataReceived(event);
   		}
   }
/*	
	private String getSignUpData(String url)
	{
		StringBuffer response = new StringBuffer();
		
		try 
		{
			URL obj = new URL(url);
			HttpURLConnection con = (HttpURLConnection) obj.openConnection();
			con.setRequestMethod("GET");	// optional default is GET

			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String inputLine;
			while ((inputLine = in.readLine()) != null) 
				response.append(inputLine);

			in.close();

//			System.out.println(String.format("GeniusIF.getSignUps: code= %d, reponse= %s", con.getResponseCode(), response));
		} 
		catch (MalformedURLException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		catch (ProtocolException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		catch (IOException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return response.toString();
	}
*/	
	private class SignUpCommon
	{
		private String[] message;
		private boolean success;
		
		//getters
		@SuppressWarnings("unused")
		List<String> getMessage()
		{
			List<String> messageList = new ArrayList<String>();
			for(String mssg : message)
				messageList.add(mssg);
			
			return messageList;
		}
		
		@SuppressWarnings("unused")
		boolean wasSuccessful() { return success; }
	}
	
	private class SignUps extends SignUpCommon
	{
		private SignUp[] data;
		
		//getters
		public List<SignUp> getSignUps()
		{
			List<SignUp> list = new LinkedList<SignUp>();
			for(SignUp su : data)
				list.add(su);
			
			return list;	
		}
	}
	
	private class SignUpReport extends SignUpCommon
	{
		private SignUpContent data;
		
		//getters
		SignUpContent getContent() { return data; }
		
	}
	
	private class SignUpContent
	{
		private SignUpActivity[] signup;
		
		//getters
		List<SignUpActivity> getSignUpActivities()
		{
			List<SignUpActivity> list = new ArrayList<SignUpActivity>();
			for(SignUpActivity su:signup)
				list.add(su);
			
			return list;	
		}
	}
	
	/***************************************************************************************************
    * This class communicates with SignUp Genius thru it's api to fetch season volunteer and activity data.
    * This executes as a background task since the size of SignUps can grow large.
    * ***************************************************************************************************/
    public class SignUpGeniusVolunteerImporter extends SwingWorker<Void, Void>
    {
    		SignUpEventType type;
    		String url;
    		SignUp signup;
    		GeniusSignUps importedSignUps;
    		SignUpReport signUpReport;
    		List<SignUpActivity> signUpActList;
    		List<Activity> newActivitiesFoundList;
    		List<Activity> updatedActivitiesFoundList;
    		List<Activity> deletedActivitiesFoundList;
		List<ONCVolunteer> newVolunteerFoundList;
		List<ONCVolunteer> updatedVolunteerFoundList;
    		
		//constructor for importing list of sign-ups in ONC account
    		SignUpGeniusVolunteerImporter()
    		{
    			this.type = SignUpEventType.SIGNUP_LIST_IMPORT;
    			this.signup = null;
    			this.url = String.format(SIGNUPS_URL, API_KEY);
    		}
    		
    		//constructor for importing volunteers and activities from a specific sign-up in ONC account
    		SignUpGeniusVolunteerImporter(SignUp signup, SignUpReportType reportType)
    		{
    			this.type = SignUpEventType.REPORT;
    			this.signup = signup;
    			this.url = String.format(SIGNUP_REPORT_URL, reportType.toString(), signup.getSignupid(), API_KEY);
    			
    			//initialize the lists
    			newActivitiesFoundList = new ArrayList<Activity>();
    			updatedActivitiesFoundList = new ArrayList<Activity>();
    			deletedActivitiesFoundList = new ArrayList<Activity>();
    			newVolunteerFoundList = new ArrayList<ONCVolunteer>();
    			updatedVolunteerFoundList = new ArrayList<ONCVolunteer>();
    		}
    	
		@Override
		protected Void doInBackground() throws Exception
		{			
			StringBuffer response = new StringBuffer();
			try 
			{
				URL obj = new URL(url);
				HttpURLConnection con = (HttpURLConnection) obj.openConnection();
				con.setRequestMethod("GET");	// optional default is GET

				BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
				String inputLine;
				while ((inputLine = in.readLine()) != null) 
					response.append(inputLine);

				in.close();
							
				//process the data in the background thread
				Gson gson = new Gson();
				if(type == SignUpEventType.SIGNUP_LIST_IMPORT)
					processSignUpsJson(response.toString());
				else
				{
					//get the report that was imported
//					System.out.println(String.format("SUGIF.GenRptFetch: signUpJson= %s", response.toString()));
					signUpReport = gson.fromJson(response.toString(), SignUpReport.class); //create the report from the json
					signUpActList = signUpReport.getContent().getSignUpActivities(); //get the list of signUpActivities
					
//					System.out.println(String.format("SUGIF.GenRptFetch: signUpActList size=%d", signUpActList.size()));
					
					//not all sign up activities have an end date. If they don't, set the end date to Christmas Day
					//for the current season. Note: SignUpActivity times are in seconds not milliseconds
					Calendar xmasDay = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
					xmasDay.set(DBManager.getCurrentSeason(),11,25,5,0,0);
					for(SignUpActivity sua : signUpActList)
						if(sua.getEnddate() == 0)
							sua.setEnddate(xmasDay.getTimeInMillis()/1000);	//milliseconds to seconds
					
					//create the unique activity list
					List<SignUpActivity> uniqueActList = createUniqueActivityList(signUpActList);
					
					//create the new, modified and deleted activity lists
					createNewModifiedAndDeletedActivityLists(uniqueActList);	
					
					//create a list of unique volunteers present in the imported sign-up
					List<ONCVolunteer> uniqueVolList = createUniqueVolunteerList(signUpActList);
				
					//create the new and modified volunteer lists
					if(!uniqueVolList.isEmpty())
						createNewAndModifiedVolunteerLists(uniqueVolList, signUpActList);
				}
			}
			catch (UnknownHostException uhex) 
			{
				System.out.println(String.format("GeniusIF.doInBack: UnknownHostException"));
//				simulateGeniusReportFetch();
			}
			catch (MalformedURLException e) 
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
			catch (ProtocolException e) 
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
			catch (IOException e) 
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
//			simulateGeniusReportFetch();
			return null;
		}
		
		void processSignUpsJson(String json)
		{
			Gson gson = new Gson();
			SignUps signUps =  gson.fromJson(json, SignUps.class);
			
			//Create a GeniusSignUps object. For each of the imported sign ups, initialize and 
			//adjust the time
			importedSignUps = new GeniusSignUps(signUps.getSignUps());

			for(SignUp su : importedSignUps.getSignUpList())
			{
				//Initialize the member variables that are don't come from
				//the genius import for each sign up. They will be null otherwise.
				su.setLastImportTimeInMillis(0);
				su.setFrequency(Frequency.NEVER);
				
				//adjust the SignUp Genius end time to milliseconds from seconds
				su.setEndtime(su.getEndtimeInMillis() * 1000);
			}	
		}
		
		void simulateGeniusReportFetch()
		{
			String path = String.format("%s/testfile3.txt", System.getProperty("user.dir"));
			InputStream is = null;
			try
			{
				is = new FileInputStream(path);
			}
			catch (FileNotFoundException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
			BufferedReader buf = new BufferedReader(new InputStreamReader(is)); 
			String line = null;
			try
			{
				line = buf.readLine();
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			StringBuilder sb = new StringBuilder(); 
			while(line != null)
			{ 
				sb.append(line).append("\n"); 
				try
				{
					line = buf.readLine();
				}
				catch (IOException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
			} 
			
			String response = sb.toString(); 
//			System.out.println("Simulated SignUpReportJson: " + response);

			//get the report that was imported
			Gson gson = new Gson();
			signUpReport = gson.fromJson(response, SignUpReport.class);	//create the report from the json
			signUpActList = signUpReport.getContent().getSignUpActivities();	//get the list of signUpActivities
//			System.out.println(String.format("SUGIF.simGenRptFetch: signUpActList size=%d", signUpActList.size()));
			
			//not all sign up activities have an end date. If they don't, set the end date to Christmas Day
			//for the current season.
			Calendar xmasDay = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
			xmasDay.set(DBManager.getCurrentSeason(),11,25,5,0,0);
			for(SignUpActivity sua : signUpActList)
				if(sua.getEnddate() == 0)
					sua.setEnddate(xmasDay.getTimeInMillis()/1000);	//note: SignUpActivity times are in secs not millis
					
			//create the unique activity list
			List<SignUpActivity> uniqueActList = createUniqueActivityList(signUpActList);
			
			//create the new and modified activity lists
			createNewModifiedAndDeletedActivityLists(uniqueActList);	
			
			//create a list of unique volunteers present in the imported sign-up
			List<ONCVolunteer> uniqueVolList = createUniqueVolunteerList(signUpActList);
		
			//create the new and modified volunteer lists
			if(!uniqueVolList.isEmpty())
				createNewAndModifiedVolunteerLists(uniqueVolList, signUpActList);
		}
		
		/***
		 * Parses the list of imported sign-up activities and produces a list of unique sign-up genius
		 * activities in the list. A volunteer may sign-up for multiple activities, this method
		 * produces a list in which an activity occurs once, regardless of how many volunteers
		 * and activities are in the sign-up
		 * @param signUpActivityList
		 * @return
		 */
		List<SignUpActivity> createUniqueActivityList(List<SignUpActivity> signUpActivityList)
		{	
			//create the unique activity list
			List<SignUpActivity>  uniqueSignUpActivityList = new ArrayList<SignUpActivity>();
			for(SignUpActivity sua : signUpActivityList)
				if(!isActivityInList(uniqueSignUpActivityList, sua.getSlotitemid()))
					uniqueSignUpActivityList.add(sua);
			
			return uniqueSignUpActivityList;
		}
		
		/***
		 * Parses the list of imported sign-up activities and produces a list of unique volunteers
		 * in the list. A volunteer may sign-up for multiple activities, this method produces a list
		 * where the volunteers is listed once, regardless of how many activities they signed up for.
		 * @param signUpActivityList
		 * @return
		 */
		List<ONCVolunteer> createUniqueVolunteerList(List<SignUpActivity>  signUpActivityList)
		{	
			//create the unique volunteer list
			List<ONCVolunteer>  uniqueSignUpVolunteerList = new ArrayList<ONCVolunteer>();
			for(SignUpActivity sua : signUpActivityList)
			{	
				if(!sua.getFirstname().isEmpty() && !sua.getLastname().isEmpty() && !sua.getEmail().isEmpty())
				{
					ONCVolunteer importedVol = new ONCVolunteer(sua);
					if(!isVolunteerInList(uniqueSignUpVolunteerList, importedVol))
						uniqueSignUpVolunteerList.add(importedVol);
				}
			}
			
			return uniqueSignUpVolunteerList;
		}
		
		boolean isActivityInList(List<SignUpActivity> list, long slotitemid)
		{
			int index = 0;
			while(index < list.size() && list.get(index).getSlotitemid() != slotitemid)
				index++;
			
			return index < list.size();
		}
		
		boolean isVolunteerInList(List<ONCVolunteer> list, ONCVolunteer v)
		{
			int index = 0;	
			while(index < list.size() && !(list.get(index).getFirstName().equalsIgnoreCase(v.getFirstName()) &&
					list.get(index).getLastName().equalsIgnoreCase(v.getLastName()) &&
					list.get(index).getEmail().equalsIgnoreCase(v.getEmail())))
				index++;
			
			return index < list.size();
		}
		
		void createNewModifiedAndDeletedActivityLists(List<SignUpActivity> importedUniqueActList)
		{
			//clone a list of the current activities from the activity database
			ServerActivityDB activityDB;
			ServerVolunteerActivityDB volActDB;
			try
			{
				activityDB = ServerActivityDB.getInstance();
				volActDB = ServerVolunteerActivityDB.getInstance();
				List<Activity> cloneActList = activityDB.clone(DBManager.getCurrentSeason());
				List<VolAct> cloneVolActList = volActDB.clone(DBManager.getCurrentSeason());
				
				//compare the clone list to the imported activity list. Identify previously imported
				//sign up genius activities that are no longer in the signup. They can only be deleted
				//if no volunteers have already signed up for the activity
				for(Activity a : cloneActList)
				{
					int index = 0;
					while(index < importedUniqueActList.size())
					{
						Activity importedVA = new Activity(importedUniqueActList.get(index));
						int result = a.compareActivities(importedVA);
						if(result >= Activity.VOLUNTEER_ACTIVITY_GENIUS_MATCH)
							break;
						else
							index++;
					}
					
					if(index == importedUniqueActList.size() && getVolunteerCount(cloneVolActList, a) == 0)
						deletedActivitiesFoundList.add(a);
				}
				//compare the imported unique activity list to the cloned list. If a unique activity is not
				//in the current list or if the activity name, start, end or location has been modified, add
				//the activity to the newAndModified activities list
				for(SignUpActivity sua : importedUniqueActList)
				{
					Activity importedVA = new Activity(sua);
					int index = 0, result = -1;
					while(index < cloneActList.size() && 
						(result = importedVA.compareActivities(cloneActList.get(index))) == Activity.VOLUNTEER_ACTIVITY_DOES_NOT_MATCH)
						index++;
						
					if(index < cloneActList.size())	//at least a partial match
					{
						//there is a match. If it's not an exact match but the
						//genius ID's match, the date/time has been updated
						if(result == Activity.VOLUNTEER_ACTIVITY_GENIUS_MATCH)
						{
							//add the current ID and add activity to update list
							importedVA.setID(cloneActList.get(index).getID());
							updatedActivitiesFoundList.add(importedVA);
						}
					}
					else //there was not a match, it's a new activity
						newActivitiesFoundList.add(importedVA);
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
		
		//method returns number of volunteers who have signed up for an activity in a given year
		int getVolunteerCount(List<VolAct> cloneVolActList, Activity a)
		{
			int volCount = 0;
			for(VolAct va : cloneVolActList)
				if(va.getActID() == a.getID())
					volCount += va.getQty();
			
			return volCount;
		}
		
		void createNewAndModifiedVolunteerLists(List<ONCVolunteer> uniqueVolList, List<SignUpActivity> suActList)
		{
			//clone a list of the current activities from the activity database
			ServerVolunteerDB volDB;
			try
			{
				volDB = ServerVolunteerDB.getInstance();
				List<ONCVolunteer> cloneVolList = volDB.clone(DBManager.getCurrentSeason());
				
				//compare the unique volunteer list to the cloned list. If a volunteer is not in 
				//the current list or if the volunteer name or contact info has been modified, add
				//the volunteer to the newAndModified volunteer list
				for(ONCVolunteer importedVol : uniqueVolList)
				{
					int index = 0, result = -1;
					while(index < cloneVolList.size() && 
						(result = importedVol.compareVolunteers(cloneVolList.get(index))) == ONCVolunteer.VOLUNTEER_DOES_NOT_MATCH)
						index++;	

					if(index < cloneVolList.size())
					{
						//there is a match. If it's not an exact match, add it as a modified volunteer
						if(result != ONCVolunteer.VOLUNTEER_EXACT_MATCH)
						{
							//add the current ID and add activity to update list
							importedVol.setID(cloneVolList.get(index).getID());
							updatedVolunteerFoundList.add(importedVol);
						}
					}
					else  //there was not a match, it's a new volunteer
					{	
						newVolunteerFoundList.add(importedVol);
					}
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
/*		
		List<VolAct> createVolunteerActivityList(List<SignUpActivity> suActList, ONCVolunteer vol)
		{
			List<VolAct> vaList = new ArrayList<VolAct>();
			
			try
			{
				ServerActivityDB activityDB = ServerActivityDB.getInstance();
				for(SignUpActivity sua : suActList)
				{
					//search thru the imported activities and find ones that the volunteer signed up for
					if(sua.getEmail().equals(vol.getEmail()) && 
						sua.getFirstname().equalsIgnoreCase(vol.getFirstName()) &&
						 sua.getLastname().equalsIgnoreCase(vol.getLastName()))
					{
						Activity actualActivity = activityDB.findActivity(DBManager.getCurrentYear(), sua.getSlotitemid());
						if(actualActivity != null)
							vaList.add(new VolAct(-1, vol.getID(), actualActivity.getID(),
										sua.getSlotitemid(), sua.getMyqty(), sua.getComment()));
					}
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
	
			return vaList;
		}
*/		
		 /*
	     * Executed in event dispatching thread
	     */
	    @Override
	    protected void done()
	    {
	    		//notify the thread is complete
	    		if(type == SignUpEventType.SIGNUP_LIST_IMPORT)
	    			signUpSummaryReceived(importedSignUps);
	    		else if(type == SignUpEventType.REPORT)
	    		{
	    			//update the time sign up was imported and notify the clients that a signup was imported
	    			signup.setLastImportTimeInMillis(System.currentTimeMillis());
/*	    			
	    			System.out.println(String.format("GeniusIF.done: #SignUpImported ID= %d, time=%d", 
	    					signup.getSignupid(), signup.getLastImportTimeInMillis()));
	    			System.out.println(String.format("GeniusIF.done: #New Activities= %d", newActivitiesFoundList.size())); 
	    			System.out.println(String.format("GeniusIF.done: #Updated Activities= %d", updatedActivitiesFoundList.size()));
	    			System.out.println(String.format("GeniusIF.done: #Deleted Activities= %d", deletedActivitiesFoundList.size())); 
	    			System.out.println(String.format("GeniusIF.done: #Updated Volunteers= %d", updatedVolunteerFoundList.size())); 
	    			System.out.println(String.format("GeniusIF.done: #New Volunteers= %d", newVolunteerFoundList.size())); 
*/				
	    			signUpContentReceived(SignUpEventType.REPORT, signup);
	    				
	    			//if lists are not empty, notify clients
	    			if(!deletedActivitiesFoundList.isEmpty())
	    				signUpContentReceived(SignUpEventType.DELETED_ACTIVITIES, deletedActivitiesFoundList);
	    			
	    			if(!updatedActivitiesFoundList.isEmpty())
	    				signUpContentReceived(SignUpEventType.UPDATED_ACTIVITIES, updatedActivitiesFoundList);
	    			
	    			for(Activity ua : updatedActivitiesFoundList)
	    				System.out.println(String.format("ServActDB.printActivities %s: act ID= %d, actGenID= %d, act Name= %s",
	    						"UPDATED ACTS FOUND:", ua.getID(), ua.getGeniusID(),ua.getName()));
	    			
	    			if(!newActivitiesFoundList.isEmpty())
	    				signUpContentReceived(SignUpEventType.NEW_ACTIVITIES, newActivitiesFoundList);
	    				
	    			if(!updatedVolunteerFoundList.isEmpty())
	    			{
//	    				//for debug, print the updatedVolFoundList
//	    				for(ONCVolunteer uv : updatedVolunteerFoundList)
//	    					System.out.println(String.format("SUGIF.done: updatedVolID=%d, fn= %s, ln= %s, email= %s",
// 							uv.getID(), uv.getFirstName(), uv.getLastName(), uv.getEmail()));
	    				
	    				ServerVolunteerDB volDB;
						try
						{
							volDB = ServerVolunteerDB.getInstance();
							volDB.processUpdatedSignUpGeniusVolunteers(updatedVolunteerFoundList);
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
	    			
	    			if(!newVolunteerFoundList.isEmpty())
	    			{
	    				ServerVolunteerDB volDB;
					try
					{
						//add the new volunteers found to the volunteer data base and get their actual IDs
						volDB = ServerVolunteerDB.getInstance();
						volDB.processNewSignUpGeniusVolunteers(newVolunteerFoundList);
							
//						//for debug, print the addedVolList
//		    				for(ONCVolunteer nv :addedVolList)
//		    				System.out.println(String.format("SUGIF.done: newVolID=%d, fn= %s, ln= %s, email= %s",
//		    							nv.getID(), nv.getFirstName(), nv.getLastName(), nv.getEmail()));
		    				
		    				
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
	    			
	    			//process sign-up for volunteer activities
	    			ImportedVolunteerActivityProcessor vaProcessor= new ImportedVolunteerActivityProcessor(signUpActList);
	    			vaProcessor.execute();
	    		}
	    }
    }
    
    /***************************************************************************************************
     * This class processes sign-ups from sign up genius to determine which volunteer activities
     * are already in the data base and which are new, have been modified or deleted
     * ***************************************************************************************************/
     public class ImportedVolunteerActivityProcessor extends SwingWorker<Void, Void>
     {
    	 	private List<SignUpActivity> signUpActList;
    	 	private List<VolAct> modVAList;
		private List<VolAct> newVAList;
		private List<VolAct> delVAList;
//		private List<VolAct> sameVAList;
    	 	
    	 	ImportedVolunteerActivityProcessor(List<SignUpActivity> signUpActList)
    	 	{
    	 		this.signUpActList = signUpActList;
    	 		this.modVAList = new ArrayList<VolAct>();
			this.newVAList = new ArrayList<VolAct>();
			this.delVAList = new ArrayList<VolAct>();
//			this.sameVAList = new ArrayList<VolAct>();
    	 	}
    	 	
		@Override
		protected Void doInBackground() throws Exception
		{
			///clone a list of the current activities from the activity database and a list of current
			//volunteers from the volunteer database
			ServerActivityDB actDB;
			ServerVolunteerDB volDB;
			ServerVolunteerActivityDB volActDB;
			try
			{
				//get clone lists of current activities, volunteers and volunteer activities
				//in the database. The activity and volunteer databases will have been
				//updated post import. We'll use them to align the volunteer activities 
				//imported
				actDB = ServerActivityDB.getInstance();
				List<Activity> cloneActList = actDB.clone(DBManager.getCurrentSeason());

				volDB = ServerVolunteerDB.getInstance();
				List<ONCVolunteer> cloneVolList = volDB.clone(DBManager.getCurrentSeason());
				
				volActDB = ServerVolunteerActivityDB.getInstance();
				List<VolAct> cloneVolActList = volActDB.clone(DBManager.getCurrentSeason());
				
				//for each imported sua, find the activity and volunteer. They will be in the
				//cloned DB's. Create a new VA and add it to a new VA list
				List<VolAct> suaVAList = new ArrayList<VolAct>();
				for(SignUpActivity sua : signUpActList)
				{
					//find the activity in the clone list only if the sua has a last name and email.
					if(!sua.getLastname().isEmpty() && !sua.getEmail().isEmpty())
					{
						int actIndex = 0;
						while(actIndex < cloneActList.size() && cloneActList.get(actIndex).getGeniusID() != sua.getSlotitemid())
							actIndex++;
					
						if(actIndex < cloneActList.size())
						{
							//found the activity, now find the volunteer
							Activity act = cloneActList.get(actIndex);

							//find the volunteer in the clone list. If found, create a new VolunteerActivity
							//and add it to the volunteerActivityList. A volunteer is declared a match if
							//their first and last names plus their email addresses match
							int volIndex = 0;
							while(volIndex < cloneVolList.size() &&
								!(cloneVolList.get(volIndex).getFirstName().equalsIgnoreCase(sua.getFirstname()) &&
								cloneVolList.get(volIndex).getLastName().equalsIgnoreCase(sua.getLastname()) &&
								cloneVolList.get(volIndex).getEmail().equalsIgnoreCase(sua.getEmail())))
							volIndex++;
						
							if(volIndex < cloneVolList.size())
								suaVAList.add(new VolAct(-1, cloneVolList.get(volIndex).getID(), act.getID(),
													act.getGeniusID(), sua.getMyqty(), sua.getComment()));
						}
					}
				}

//				writeToFile(suaVAList);
				
				//now that we've got the list of imported volunteer acts with correct activity and
				//volunteer id's, compare to the current cloned volunteer activity database to see what's changed
				for(VolAct suaVA : suaVAList)
				{
					int index = 0;
					while(index < cloneVolActList.size() && (cloneVolActList.get(index).getVolID() != suaVA.getVolID() ||
															cloneVolActList.get(index).getActID() != suaVA.getActID()))
					
						index++;
					
//					System.out.println(String.format("SUGIF.dib: volID= %d, actID=%d, index= %d", suaVA.getVolID(), suaVA.getActID(), index));
					
					if(index == cloneVolActList.size()) 
					{	
						//did not find the volID and the actID, so it's a new volunteer activity
						newVAList.add(new VolAct(-1, suaVA.getVolID(), suaVA.getActID(), suaVA.getGeniusID(),
													suaVA.getQty(), suaVA.getComment()));
					}
					else if(index < cloneVolActList.size() && 
							 cloneVolActList.get(index).getQty() != suaVA.getQty() ||
							  !cloneVolActList.get(index).getComment().equals(suaVA.getComment()))
					{
						//found the volID and actID, however, qty and/or comment don't match, update both
						cloneVolActList.get(index).setQty(suaVA.getQty());
						cloneVolActList.get(index).setComment(suaVA.getComment());
						modVAList.add(cloneVolActList.get(index));
					}
//					else
//						sameVAList.add(cloneVolActList.get(index));
				}
				
				//now that we've determined new and modified VA's, compare current DB VA's to the import
				//to see if any must be removed. It should be removed if it came from sign-up genius
				//previously and is no longer in the import. These are sign-up genius volunteers who
				//change their activities during the season
				for(VolAct cloneVA : cloneVolActList)
				{
					if(cloneVA.getGeniusID() > -1)
					{
						int index = 0;
						while(index < suaVAList.size())
						{	
							if(suaVAList.get(index).getVolID() != cloneVA.getVolID() ||
								suaVAList.get(index).getActID() != cloneVA.getActID())
								index++;
							else
								break;
						}
						
						if(index == suaVAList.size()) //did not find the volID and the actID, so it's a new deleted activity
							delVAList.add(new VolAct(cloneVA.getID(), cloneVA.getVolID(), cloneVA.getActID(), cloneVA.getGeniusID(),
													cloneVA.getQty(), cloneVA.getComment()));
					}
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
			return null;
		}
/*		
		void writeToFile(List<VolAct> vaList)
		{
			 
			String[] header = {"ID", "Vol ID" ,"Act ID","Genius ID", "Qty", "Comment"};
				 
			String path = String.format("%s/DebugVAFile.csv", System.getProperty("user.dir"));
			
			try 
		    {
		    		CSVWriter writer = new CSVWriter(new FileWriter(path));
		    		writer.writeNext(header);
		    	 
		    		for(int index=0; index < vaList.size(); index++)
		    		{
		    			VolAct va = vaList.get(index);
		    			writer.writeNext(va.getExportRow());	//Get ONCObject row
		    		}
		    		
		    		writer.close();
		    } 
		    catch (IOException x)
		    {
		    		System.err.format("IO Exception: %s%n", x);
		    }	
		}
*/		
		@Override
		protected void done()
		{
			try
			{
//				System.out.println(String.format("GeniusVAIMP.done: #Same VA's= %d", sameVAList.size()));
//				System.out.println(String.format("GeniusVAImp.done: #Updated VA's= %d", modVAList.size()));
//  				System.out.println(String.format("GeniusVAIMP.done: #New VA's= %d", newVAList.size()));
//  				System.out.println(String.format("GeniusVAIMP.done: #Deleted VA's= %d", delVAList.size()));
    				
				ServerVolunteerActivityDB vaDB = ServerVolunteerActivityDB.getInstance();
				if(!modVAList.isEmpty())
					vaDB.processUpdatedSignUpGeniusVolunteerActivities(DBManager.getCurrentSeason(), modVAList);
				
				if(!newVAList.isEmpty())
					vaDB.processNewSignUpGeniusVolunteerActivities(DBManager.getCurrentSeason(), newVAList);
				
				if(!delVAList.isEmpty())
					vaDB.processDeletedSignUpGeniusVolunteerActivities(DBManager.getCurrentSeason(), delVAList);
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
/*			
			//print the new VA's found
			for(VolAct newVA : newVAList)
				System.out.println(String.format("SUGIF.VAImporter.done: new VA id= %d, volID=%d, actID=%d, genID=%d, qty=%d, comment=%s",
						newVA.getID(), newVA.getVolID(), newVA.getActID(), newVA.getGeniusID(), newVA.getQty(), newVA.getComment()));
			
			//print the modified VA's found
			for(VolAct modVA : modVAList)
				System.out.println(String.format("SUGIF.VAImporter.done: modified VA id= %d, volID=%d, actID=%d, genID=%d, qty=%d, comment=%s",
						modVA.getID(), modVA.getVolID(), modVA.getActID(), modVA.getGeniusID(), modVA.getQty(), modVA.getComment()));
			
			//print the deleted VA's found
			for(VolAct delVA : delVAList)
				System.out.println(String.format("SUGIF.VAImporter.done: deleted VA id= %d, volID=%d, actID=%d, genID=%d, qty=%d, comment=%s",
						delVA.getID(), delVA.getVolID(), delVA.getActID(), delVA.getGeniusID(), delVA.getQty(), delVA.getComment()));
*/
		}
     }
}
