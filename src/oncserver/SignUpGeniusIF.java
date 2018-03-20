package oncserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.swing.SwingWorker;

import com.google.gson.Gson;

import ourneighborschild.Frequency;
import ourneighborschild.GeniusSignUps;
import ourneighborschild.SignUp;

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
		SignUpGeniusImporter importer = new SignUpGeniusImporter(SignUpEventType.SIGNUP, url);
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
		this.fireSignUpDataChanged(this, SignUpEventType.SIGNUP, geniusSignUps);
	}
	
	void signUpContentReceived(List<SignUpActivity> activityList)
	{
		this.fireSignUpDataChanged(this, SignUpEventType.REPORT, activityList);
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
//    	SignUps signUps;
    		GeniusSignUps importedSignUps;
    		SignUpReport signUpReport;
    		
    		SignUpGeniusImporter(SignUpEventType type, String url)
    		{
    			this.type = type;
    			this.url = url;
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
				
//				System.out.println(String.format("GenIF response = %s", response.toString()));
				
				//process the data in the background thread
				Gson gson = new Gson();
				if(type == SignUpEventType.SIGNUP)
				{
					SignUps signUps =  gson.fromJson(response.toString(), SignUps.class);
										
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
				else
					signUpReport = gson.fromJson(response.toString(), SignUpReport.class);
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
		}
		
		 /*
	     * Executed in event dispatching thread
	     */
	    @Override
	    public void done()
	    {
	    		//notify the thread is complete
	    		if(type == SignUpEventType.SIGNUP)
	    			signUpSummaryReceived(importedSignUps);
	    		else if(type == SignUpEventType.REPORT)
	    			signUpContentReceived(signUpReport.getContent().getSignUpActivities());
	    }
    }
}
