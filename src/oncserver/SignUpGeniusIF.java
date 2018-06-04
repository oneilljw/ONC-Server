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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

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
	private static final String SIGNUPS_URL = "https://api.signupgenius.com/v2/k/signups/created/all/?user_key=%s";
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
		String url = String.format(SIGNUPS_URL, API_KEY);
		SignUpGeniusImporter importer = new SignUpGeniusImporter(SignUpEventType.SIGNUP_IMPORT, url);
		importer.execute();
	}
	
	void requestSignUpContent(int signupid, SignUpReportType reportType)
	{
		String url = String.format(SIGNUP_REPORT_URL, reportType.toString(), signupid, API_KEY);
		SignUpGeniusImporter importer = new SignUpGeniusImporter(SignUpEventType.REPORT, url);
		importer.execute();
	}
	
	void signUpSummaryReceived(GeniusSignUps geniusSignUps)
	{	
		this.fireSignUpDataChanged(this, SignUpEventType.SIGNUP_IMPORT, geniusSignUps);
	}
	
	void signUpContentReceived(SignUpEventType type, List<Activity> volActList)
	{
		this.fireSignUpDataChanged(this, type, volActList);
	}
	
	void signUpVolunteersReceived(SignUpEventType type, List<ONCVolunteer> volList)
	{
		this.fireSignUpDataChanged(this, type, volList);
	}
	
	void signUpVolunteerActivitiesReceived(SignUpEventType type, List<VolAct> volActList)
	{
		this.fireSignUpDataChanged(this, type, volActList);
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
    * This class communicates with SignUp Genius thru it's api to fetch season data. This executes as a 
    * background task since the size of SignUps can grow large.
    * ***************************************************************************************************/
    public class SignUpGeniusImporter extends SwingWorker<Void, Void>
    {
    		SignUpEventType type;
    		String url;
    		GeniusSignUps importedSignUps;
    		SignUpReport signUpReport;
    		List<Activity> newActivitiesFoundList;
		List<Activity> updatedActivitiesFoundList;
		List<ONCVolunteer> newVolunteerFoundList;
		List<ONCVolunteer> updatedVolunteerFoundList;
		List<VolAct> newVolActFoundList;
		List<VolAct> updatedVolActFoundList;
    		
    		SignUpGeniusImporter(SignUpEventType type, String url)
    		{
    			this.type = type;
    			this.url = url;
    		}
    	
		@Override
		protected Void doInBackground() throws Exception
		{
/*			
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
				
//				if(type == SignUpEventType.REPORT)
//				{
//					String path = String.format("%s/testfile.txt", System.getProperty("user.dir"));
//					PrintWriter out = new PrintWriter(path);
//					out.println(response.toString());
//					out.close();
//				}
				
//				System.out.println(String.format("GenIF response = %s", response.toString()));
				
				//process the data in the background thread
				Gson gson = new Gson();
				if(type == SignUpEventType.SIGNUP_IMPORT)
					processSignUpsJson(response.toString());
				else
				{
					//get the report that was imported
					signUpReport = gson.fromJson(response.toString(), SignUpReport.class);
					
					//create the unique activity list
					List<SignUpActivity> uniqueActList = createUniqueActivityList(signUpReport.getContent().getSignUpActivities());
					
					//create the new and modified activity lists
					createNewAndModifiedActivityLists(uniqueActList);
					
					//create the unique volunteer list
					List<ONCVolunteer> uniqueVolList = createUniqueVolunteerList(signUpReport.getContent().getSignUpActivities());
					
					//create the new and modified volunteer lists
					createNewAndModifiedVolunteerLists(uniqueVolList);	
				}
			}
			catch (UnknownHostException uhex) 
			{
				System.out.println(String.format("GeniusIF.doInBack: UnknownHostException"));
				simulateGeniusReportFetch();
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
			
			return null;
*/			
			simulateGeniusReportFetch();
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
			String path = String.format("%s/testfile.txt", System.getProperty("user.dir"));
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
//			System.out.println("Contents : " + fileAsString);

			//get the report that was imported
			Gson gson = new Gson();
			SignUpReport signUpReport = gson.fromJson(response, SignUpReport.class);
			
			//create the unique activity list
			List<SignUpActivity> uniqueActList = createUniqueActivityList(signUpReport.getContent().getSignUpActivities());
			
			//create the new and modified activity lists
			createNewAndModifiedActivityLists(uniqueActList);	
			
			//create the unique volunteer list
			List<ONCVolunteer> uniqueVolList = createUniqueVolunteerList(signUpReport.getContent().getSignUpActivities());
		
			//create the new and modified volunteer lists
			createNewAndModifiedVolunteerLists(uniqueVolList, signUpReport.getContent().getSignUpActivities());
			
			//for the modified volunteers, create their VolAct lists
			for(ONCVolunteer v : updatedVolunteerFoundList)
			{
				updatedVolActFoundList = createVolunteerActivityList(signUpReport.getContent().getSignUpActivities(), v);
//				for(VolAct va: updatedVolActFoundList)
//					System.out.println(String.format("SUGIF.sim: modified vol = %s, volID= %d actID = %d, qty= %d, comment= %s",
//							v.getLNFI(), va.getVolID(), va.getActID(), va.getQty(), va.getComment()));
			}
			
			//for the new volunteers, create their VolAct lists
			for(ONCVolunteer v : newVolunteerFoundList)
			{
				newVolActFoundList = createVolunteerActivityList(signUpReport.getContent().getSignUpActivities(), v);
				//print the list for debug purposes
//				for(VolAct va: newVolActFoundList)
//					System.out.println(String.format("SUGIF.sim: new vol = %s, volID= %d actID = %d, qty= %d, comment= %s",
//							v.getLNFI(), va.getVolID(), va.getActID(), va.getQty(), va.getComment()));
			}
		}
		
		List<SignUpActivity> createUniqueActivityList(List<SignUpActivity>  signUpActivityList)
		{	
			//create the unique activity list
			List<SignUpActivity>  uniqueSignUpActivityList = new ArrayList<SignUpActivity>();
			for(SignUpActivity sua : signUpActivityList)
				if(!isActivityInList(uniqueSignUpActivityList, sua.getSlotitemid()))
					uniqueSignUpActivityList.add(sua);
			
			return uniqueSignUpActivityList;
		}
		
		List<ONCVolunteer> createUniqueVolunteerList(List<SignUpActivity>  signUpActivityList)
		{	
			//create the unique volunteer list
			List<ONCVolunteer>  uniqueSignUpVolunteerList = new ArrayList<ONCVolunteer>();
			for(SignUpActivity sua : signUpActivityList)
			{	
				ONCVolunteer importedVol = new ONCVolunteer(sua);
				if(!isVolunteerInList(uniqueSignUpVolunteerList, importedVol))
					uniqueSignUpVolunteerList.add(importedVol);
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
			while(index < list.size() && !list.get(index).getEmail().equals(v.getEmail()))
				index++;
			
			return index < list.size();
		}
		
		void createNewAndModifiedActivityLists(List<SignUpActivity> uniqueActList)
		{
			newActivitiesFoundList = new ArrayList<Activity>();
			updatedActivitiesFoundList = new ArrayList<Activity>();
			
			//clone a list of the current activities from the activity database
			ServerActivityDB activityDB;
			try
			{
				activityDB = ServerActivityDB.getInstance();
				List<Activity> cloneActList = activityDB.clone(DBManager.getCurrentYear());
				
				//compare the unique activity list to the cloned list. If a unique activity is not in 
				//the current list or if the activity name, start, end or location has been modified, add
				//the activity to the newAndModified activities list
				for(SignUpActivity sua : uniqueActList)
				{
					Activity importedVA = new Activity(sua);
					int index = 0, result = -1;
					while(index < cloneActList.size() && 
						(result = importedVA.compareActivities(cloneActList.get(index))) == Activity.VOLUNTEER_ACTIVITY_DOES_NOT_MATCH)
						index++;
						
					if(index < cloneActList.size())
					{
						//there is a match. If it's not an exact match, add it as a modified activity
						if(result != Activity.VOLUNTEER_ACTIVITY_EXACT_MATCH)
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
		
		void createNewAndModifiedVolunteerLists(List<ONCVolunteer> uniqueVolList, List<SignUpActivity> suActList)
		{
			newVolunteerFoundList = new ArrayList<ONCVolunteer>();
			updatedVolunteerFoundList = new ArrayList<ONCVolunteer>();
			
			//clone a list of the current activities from the activity database
			ServerVolunteerDB volDB;
			try
			{
				volDB = ServerVolunteerDB.getInstance();
				List<ONCVolunteer> cloneVolList = volDB.clone(DBManager.getCurrentYear());
				
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
		
		 /*
	     * Executed in event dispatching thread
	     */
	    @Override
	    public void done()
	    {
	    		//notify the thread is complete
	    		if(type == SignUpEventType.SIGNUP_IMPORT)
	    			signUpSummaryReceived(importedSignUps);
	    		else if(type == SignUpEventType.REPORT)
	    		{
	    			//if lists are not empty, notify clients
	    			if(!updatedActivitiesFoundList.isEmpty())
	    				signUpContentReceived(SignUpEventType.UPDATED_ACTIVITIES, updatedActivitiesFoundList);
	    			
	    			if(!newActivitiesFoundList.isEmpty())
	    				signUpContentReceived(SignUpEventType.NEW_ACTIVITIES, newActivitiesFoundList);
	    			
	    			if(!updatedVolunteerFoundList.isEmpty())
	    				signUpVolunteersReceived(SignUpEventType.UPDATED_VOLUNTEERS, updatedVolunteerFoundList);
	    			
	    			if(!newVolunteerFoundList.isEmpty())
	    				signUpVolunteersReceived(SignUpEventType.NEW_VOLUNTEERS, newVolunteerFoundList);
	    			
	    			//must update these lists for the id's of new activities and new volunteers
	    			//before adding new volActs
	    			
	    			if(!updatedVolActFoundList.isEmpty())
	    				signUpVolunteerActivitiesReceived(SignUpEventType.UPDATED_VOLUNTEER_ACTIVITIES, updatedVolActFoundList);
	    			
	    			if(!newVolActFoundList.isEmpty())
	    				signUpVolunteerActivitiesReceived(SignUpEventType.NEW_VOLUNTEERS, newVolActFoundList);
	    		}
	    }
    }
}
