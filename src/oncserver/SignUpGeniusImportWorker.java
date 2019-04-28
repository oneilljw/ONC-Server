package oncserver;

import java.io.BufferedReader;
import java.io.IOException;
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
 *
 */
public abstract class SignUpGeniusImportWorker extends SwingWorker <Void, Void>
{
	protected static final String API_KEY = "NGJMZlhzZm5SK3d4L002ODFyek9iQT09";
//	protected SignUpEventType eventType;
	
//	SignUpGeniusImportWorker(SignUpEventType eventType)
//	{
//		this.eventType = eventType;
//	}
	
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
	
	//not all sign up activities have an end date. If they don't, set the end date to Christmas Day
	//for the current season. Note: SignUpActivity times are in seconds not milliseconds
	long getChristmasDayTimeInSeconds()
	{
		Calendar xmasDay = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		xmasDay.set(DBManager.getCurrentSeason(),11,25,5,0,0);
		return xmasDay.getTimeInMillis()/1000;	//milliseconds to seconds
	}
	
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
}
