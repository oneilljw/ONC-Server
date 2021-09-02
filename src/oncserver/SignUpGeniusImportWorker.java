package oncserver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
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
import java.util.List;
import java.util.TimeZone;

import javax.swing.SwingWorker;

import ourneighborschild.SignUpActivity;

/***
 * Base class for importing a json from Our Neighbor's Child SignUp Genius account. Two basic imports
 * are supported:
 * 	1. Importing a list of active SignUp's
 * 	2. Importing a specific SignUp.
 * 
 * Import of either type of json is performed in a separate thread from the event dispatch thread.
 * The structure of the json returned depends of the type. A series object classes are used to 
 * process the json response from SignUp Genius. SignUpCommon is the common object class in both types.
 * @author johnoneil
 */
public abstract class SignUpGeniusImportWorker extends SwingWorker <Void, Void>
{
	protected static final String SIGNUP_REPORT_URL = "https://api.signupgenius.com/v2/k/signups/report/%s/%d/?user_key=%s";
	
	String getSignUpJson(String url)
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
						
			return response.toString();
		}
		catch (UnknownHostException uhex) 
		{
			return "";
		}
		catch (MalformedURLException e) 
		{
			return "";
		} 
		catch (ProtocolException e) 
		{
			return "";
		} 
		catch (IOException e) 
		{
			return "";
		}
	}
	
	static String apiKey() { return ServerEncryptionManager.getKey("key5"); }
	
	//not all sign up activities have an end date. If they don't, set the end date to Christmas Day
	//for the current season. Note: SignUpActivity times are in seconds not milliseconds
	long getChristmasDayTimeInSeconds()
	{
		Calendar xmasDay = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		xmasDay.set(DBManager.getCurrentSeason(),11,25,5,0,0);
		return xmasDay.getTimeInMillis()/1000;	//milliseconds to seconds
	}
	
	/****
	 * reads a previously stored json from SignUp Genius from a file and returns it as a String
	 */
/*	
	String simulateGeniusReportFetch(String filename)
    	{
    		InputStream is = null;
    		BufferedReader buf = null;
    		String line = null;
    		try
    		{
    			is = new FileInputStream(String.format("%s/%s", System.getProperty("user.dir"), filename));
    			buf = new BufferedReader(new InputStreamReader(is));
    			line = buf.readLine();
    			
    			StringBuilder sb = new StringBuilder(); 
        		while(line != null)
        		{ 
        			sb.append(line).append("\n"); 
        			line = buf.readLine();
        		}
        		
        		buf.close();
        		
        		return sb.toString();
    		}
    		catch (FileNotFoundException e)
    		{
    			return null;
    		}
    		catch (IOException e)
    		{
    			return null;
    		}
    	}
	
	void saveJsonToFile(String json, String filename) 
	{
		String path = String.format("%s/%dDB/%s", System.getProperty("user.dir"),DBManager.getCurrentSeason(), filename);

		BufferedWriter writer = null;
		try
		{
			writer = new BufferedWriter(new FileWriter(path));
			writer.write(json);
		}
		catch (IOException e)
		{
			ServerUI.addDebugMessage("SignUp Genius Import Error: Unable to write json to " + filename);
		}
		finally
		{
			try
			{
				writer.close();
			}
			catch (IOException e)
			{
				ServerUI.addDebugMessage("SignUp Genius Import Error: Unable to close json file " + filename);
			}
		}
	}
*/	
	protected class SignUpCommon
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
	
 	/***
     * Component of the json object returned when importing a specific SignUp from SignUp Genius
     * @author johnoneil
     *
     */
    	protected class SignUpReport extends SignUpCommon
    	{
    		private SignUpContent data;
    		
    		//getters
    		SignUpContent getContent() { return data; }
    		
    	}
    	
    	/***
    	 * Component of the object returned from SignUp Genius when importing a specific Volunteer SignUp
    	 * from the Our Neighbor's Child SignUp Genius Volunteer SignUps. An array of SignUpActivity objects
    	 *  as part of the json.
    	 * @author johnoneil
    	 *
    	 */
    	protected class SignUpContent
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
}
