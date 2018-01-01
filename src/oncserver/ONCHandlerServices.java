package oncserver;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import ourneighborschild.UserPermission;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

public abstract class ONCHandlerServices 
{
	protected static final int HTTP_OK = 200;
	
	protected static final String REFERRAL_STATUS_HTML = "ScrollFamTable.htm";
	protected static final String DASHBOARD_HTML = "Dashboard.htm";
	protected static final String PARTNER_TABLE_HTML = "PartnerTable.htm";
	protected static final String LOGOUT_HTML = "logout.htm";
	protected static final String MAINTENANCE_HTML = "maintenance.htm";
	
	void sendHTMLResponse(HttpExchange t, HtmlResponse html) throws IOException
	{
		t.sendResponseHeaders(html.getCode(), html.getResponse().length());
		OutputStream os = t.getResponseBody();
		os.write(html.getResponse().getBytes());
		os.close();
		t.close();
	}
	
	String readFile( String file ) throws IOException
	{
	    BufferedReader reader = new BufferedReader(new FileReader(file));
	    String         line = null;
	    StringBuilder  stringBuilder = new StringBuilder();

	    while((line = reader.readLine()) != null)
	    {
	        stringBuilder.append(line);
	        stringBuilder.append(System.getProperty("line.separator"));
	    }
	    
	    reader.close();
	    
	    return stringBuilder.toString();
	}
	
	Map<String, String> createMap(Map<String, Object> params, String[] keys)
	{
		Map<String, String> map = new HashMap<String, String>();
		for(String key:keys)
		{
			//code modified 10-18-16 to prevent null value exception if input map does not contain a key
			//if key is missing in input map, it is added with an empty string;
			if(params.containsKey(key) && params.get(key) != null)
				map.put(key, (String) params.get(key));
			else
				map.put(key, "");
		}
		
		return map;
	}
	
	String getHomePageHTML(WebClient wc, String message, String famRef)
	{
		String homePageHTML;
		
		String userFN;
		if(wc.getWebUser().getFirstName().equals(""))
			userFN = wc.getWebUser().getLastName();
		else
			userFN = wc.getWebUser().getFirstName();
		
		//determine which home page, elf or agent
		if(wc.getWebUser().getPermission() == UserPermission.Admin ||
				wc.getWebUser().getPermission() == UserPermission.Sys_Admin)
		{
			//read the dashboard page html
			try
			{
				homePageHTML = readFile(String.format("%s/%s",System.getProperty("user.dir"), DASHBOARD_HTML));
				homePageHTML = homePageHTML.replace("USER_NAME", userFN);
				homePageHTML = homePageHTML.replace("USER_MESSAGE", message);
				homePageHTML = homePageHTML.replaceAll("REPLACE_TOKEN", wc.getSessionID().toString());
				return homePageHTML;
			}
			catch (IOException e) 
			{
				return "<p>ONC Family Page Unavailable</p>";
			}
		}
		else if(wc.getWebUser().getPermission() == UserPermission.General)
		{
			//read the partner table html
			try
			{
				homePageHTML = readFile(String.format("%s/%s",System.getProperty("user.dir"), PARTNER_TABLE_HTML));
				homePageHTML = homePageHTML.replace("USER_NAME", userFN);
				homePageHTML = homePageHTML.replace("USER_MESSAGE", message);
				homePageHTML = homePageHTML.replace("REPLACE_TOKEN", wc.getSessionID().toString());
				homePageHTML = homePageHTML.replace("HOME_LINK_VISIBILITY", "hidden");
				return homePageHTML;
			}
			catch (IOException e) 
			{
				return "<p>Partner Table Unavailable</p>";
			}
		}
		else	//user permission = AGENT
		{
			try
			{
				homePageHTML = readFile(String.format("%s/%s",System.getProperty("user.dir"), REFERRAL_STATUS_HTML));
				homePageHTML = homePageHTML.replace("USER_NAME", userFN);
				homePageHTML = homePageHTML.replace("USER_MESSAGE", message);
				homePageHTML = homePageHTML.replace("REPLACE_TOKEN", wc.getSessionID().toString());
				homePageHTML = homePageHTML.replace("THANKSGIVING_CUTOFF", enableReferralButton("Thanksgiving"));
				homePageHTML = homePageHTML.replace("DECEMBER_CUTOFF", enableReferralButton("December"));
				homePageHTML = homePageHTML.replace("EDIT_CUTOFF", enableReferralButton("Edit"));
				homePageHTML = homePageHTML.replace("HOME_LINK_VISIBILITY", "hidden");
				return homePageHTML;
			}
			catch (IOException e) 
			{
				return "<p>Family Table Unavailable</p>";
			}
		}
	}
	
	String enableReferralButton(String day)
	{
		ServerGlobalVariableDB gDB = null;
		try 
		{
			gDB = ServerGlobalVariableDB.getInstance();
			
			//get current season
			int currSeason = DBManager.getCurrentYear();
			Calendar today = Calendar.getInstance();
			Date seasonStartDate = gDB.getSeasonStartDate(currSeason);
			Date compareDate = gDB.getDeadline(currSeason, day);
			
			if(seasonStartDate != null && compareDate != null && 
				today.getTime().compareTo(seasonStartDate) >=0 && today.getTime().compareTo(compareDate) < 0 )
				return "enabled";
			else
				return "disabled";
		}
		catch (FileNotFoundException e) 
		{
			return "disabled";
		}
		catch (IOException e) 
		{
			return "disabled";
		}
	}
	
	void sendFile(HttpExchange t, String mimetype, String sheetname) throws IOException
	{
		// add the required response header
	    Headers h = t.getResponseHeaders();
	    h.add("Content-Type", mimetype);

	    //get file
	    String path = String.format("%s/%s", System.getProperty("user.dir"), sheetname);
	    File file = new File(path);
	    byte [] bytearray  = new byte [(int)file.length()];
	      
	    FileInputStream fis = new FileInputStream(file);
	      
	    BufferedInputStream bis = new BufferedInputStream(fis);
	    bis.read(bytearray, 0, bytearray.length);
	    bis.close();
	      	
	    //send the response.
	    t.sendResponseHeaders(HTTP_OK, file.length());
	    OutputStream os = t.getResponseBody();
	    os.write(bytearray,0,bytearray.length);
	    os.close();
	    t.close();
	}
	
	String invalidTokenReceived()
	{
		String response = null;
		try {
			if(ONCWebServer.isWebsiteOnline())
			{
				response = readFile(String.format("%s/%s",System.getProperty("user.dir"), LOGOUT_HTML));
				response = response.replace("WELCOME_MESSAGE", "Your session expired, please login again:");
			}
			else
			{
				response = readFile(String.format("%s/%s",System.getProperty("user.dir"), MAINTENANCE_HTML));
				response = response.replace("TIME_BACK_UP", "after 4pm EDT");
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return response;
	}

	String invalidTokenReceivedToJsonRequest(String mssg, String callback)
	{
		//send an error message json that will trigger a dialog box in the client
		String json = "{\"error\":\"Your seesion expired due to inactivity\"}";
		
		return callback +"(" + json +")";
	}
	
	String getHomeLinkVisibility(WebClient wc)
	{
		if(wc.getWebUser().getPermission() == UserPermission.Admin ||
				wc.getWebUser().getPermission() == UserPermission.Sys_Admin)
		{
			return "visible";
		}
		else
			return "hidden";
	}
}
