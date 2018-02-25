package oncserver;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ourneighborschild.UserPermission;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public abstract class ONCWebpageHandler implements HttpHandler 
{
	protected static final int HTTP_OK = 200;
	
	private static final String ONC_FAMILY_PAGE_HTML = "ONC.htm";
	private static final String UPDATE_HTML = "Edit.htm";
	private static final String REFERRAL_HTML = "FamilyReferral.htm";
	private static final String COMMON_FAMILY_JS_FILE = "CommonFamily.js";
	private static final String PARTNER_UPDATE_HTML = "Partner.htm";
	private static final String REGION_TABLE_HTML = "RegionTable.htm";
	private static final String REGION_UPDATE_HTML = "Region.htm";
	private static final String DRIVER_SIGN_IN_HTML = "DriverReg.htm";
	private static final String VOLUNTEER_SIGN_IN_HTML = "WarehouseSignIn.htm";
	private static final String VOLUNTEER_REGISTRATION_HTML = "VolRegistration.htm";
	private static final String REFERRAL_STATUS_HTML = "ScrollFamTable.htm";
	private static final String DASHBOARD_HTML = "Dashboard.htm";
	private static final String PARTNER_TABLE_HTML = "PartnerTable.htm";
	private static final String LOGOUT_HTML = "logout.htm";
	private static final String MAINTENANCE_HTML = "maintenance.htm";
	private static final String CHANGE_PASSWORD_HTML = "Change.htm";
	
	protected static final String SESSION_ID_NAME = "SID=";
	
	protected ClientManager clientMgr;
	protected static Map<String,String> webpageMap;
	
	ONCWebpageHandler()
	{
		clientMgr = ClientManager.getInstance();
		loadWebpages();
	}
	
	static String loadWebpages()
	{
		webpageMap = new HashMap<String, String>();
		try 
		{
			webpageMap.put("dashboard", readFile(String.format("%s/%s",System.getProperty("user.dir"), DASHBOARD_HTML)));
			webpageMap.put("familystatus", readFile(String.format("%s/%s",System.getProperty("user.dir"), REFERRAL_STATUS_HTML)));
			webpageMap.put("family", readFile(String.format("%s/%s",System.getProperty("user.dir"), ONC_FAMILY_PAGE_HTML)));
			webpageMap.put("referral", readFile(String.format("%s/%s",System.getProperty("user.dir"), REFERRAL_HTML)));
			webpageMap.put("update", readFile(String.format("%s/%s",System.getProperty("user.dir"), UPDATE_HTML)));
			webpageMap.put("partnertable", readFile(String.format("%s/%s",System.getProperty("user.dir"), PARTNER_TABLE_HTML)));
			webpageMap.put("updatepartner", readFile(String.format("%s/%s",System.getProperty("user.dir"), PARTNER_UPDATE_HTML)));
			webpageMap.put("regiontable", readFile(String.format("%s/%s",System.getProperty("user.dir"), REGION_TABLE_HTML)));
			webpageMap.put("updateregion", readFile(String.format("%s/%s",System.getProperty("user.dir"), REGION_UPDATE_HTML)));
			webpageMap.put("driversignin", readFile(String.format("%s/%s",System.getProperty("user.dir"), DRIVER_SIGN_IN_HTML)));
			webpageMap.put("volunteersignin", readFile(String.format("%s/%s",System.getProperty("user.dir"), VOLUNTEER_SIGN_IN_HTML)));
			webpageMap.put("volunteerregistration", readFile(String.format("%s/%s",System.getProperty("user.dir"), VOLUNTEER_REGISTRATION_HTML)));
			webpageMap.put("online", readFile(String.format("%s/%s",System.getProperty("user.dir"), LOGOUT_HTML)));
			webpageMap.put("welcome", readFile(String.format("%s/%s",System.getProperty("user.dir"), MAINTENANCE_HTML)));
			webpageMap.put("changepw", readFile(String.format("%s/%s",System.getProperty("user.dir"), CHANGE_PASSWORD_HTML)));
			
			return "UPDATED_WEBPAGES";
		} 
		catch (IOException e) 
		{
			return "UPDATE_FAILED";
		}
	};
	
	void sendHTMLResponse(HttpExchange t, HtmlResponse html) throws IOException
	{
		if(html.getCookie() != null)
		{
			Headers header = t.getResponseHeaders();
    			ArrayList<String> headerList = new ArrayList<String>();
    			headerList.add(html.getCookie().toString());
    			header.put("Set-Cookie", headerList);
		}
		
		t.sendResponseHeaders(html.getCode(), html.getResponse().length());
		OutputStream os = t.getResponseBody();
		os.write(html.getResponse().getBytes());
		os.close();
		t.close();
	}
	
	static String readFile( String file ) throws IOException
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
	
	String getHomePageHTML(WebClient wc, String message)
	{
		//determine which home page to send to client based on UserPermission
		if(wc.getWebUser().getPermission() == UserPermission.Sys_Admin)
			return getDashboardWebpage(wc, message);
		else if(wc.getWebUser().getPermission() == UserPermission.Admin)
			return getDashboardWebpage(wc, message);
		else if(wc.getWebUser().getPermission() == UserPermission.General)
			return getPartnerTableWebpage(wc, message);	//send the partner table page
		else	 
			return getFamilyStatusWebpage(wc, message);	//send the famiy status page
	}
	
	String getFamilyStatusWebpage(WebClient wc, String message)
	{
		String response = webpageMap.get("familystatus");
		response = response.replace("USER_NAME", wc.getWebUser().getFirstName());
		response = response.replace("USER_MESSAGE", message);
		response = response.replace("REPLACE_TOKEN", wc.getSessionID().toString());
		response = response.replace("THANKSGIVING_CUTOFF", enableReferralButton("Thanksgiving"));
		response = response.replace("DECEMBER_CUTOFF", enableReferralButton("December"));
		response = response.replace("EDIT_CUTOFF", enableReferralButton("Edit"));
		response = response.replace("HOME_LINK_VISIBILITY", getHomeLinkVisibility(wc));
		
		return response;
	}
	
	String getPartnerTableWebpage(WebClient wc, String message)
	{
		String response = webpageMap.get("partnertable");
		response = response.replace("USER_NAME", getUserFirstName(wc));
		response = response.replace("USER_MESSAGE", message);
		response = response.replace("REPLACE_TOKEN", wc.getSessionID().toString());
		response = response.replace("HOME_LINK_VISIBILITY", getHomeLinkVisibility(wc));
		
		return response;
	}
	
	String getDashboardWebpage(WebClient wc, String message)
	{
		String response = webpageMap.get("dashboard");
		response = response.replace("USER_NAME", getUserFirstName(wc));
		response = response.replace("USER_MESSAGE", message);
		response = response.replaceAll("REPLACE_TOKEN", wc.getSessionID().toString());
		
		return response;
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
			if(ONCSecureWebServer.isWebsiteOnline())
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

	HtmlResponse invalidTokenReceivedToJsonRequest(String mssg, String callback)
	{
		//send an error message json that will trigger a dialog box in the client
		String json = "{\"error\":\"Your seesion expired due to inactivity\"}";
		
//		return callback +"(" + json +")";
		
		return new HtmlResponse(callback +"(" + json +")", HttpCode.Ok);
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
	
	/***********
	 * Method takes the request headers from HttpExchange and examines each header for one named
	 * Cookie in accordance with the RFC's. It then takes the Cookie header and searches for a string
	 * that starts with the SESSION_ID_NAME. If it finds that string, it returns the sessionID from
	 * that string. The sessionID string will have format SESSION_ID_NAME="xxxx-xxxx-xxxx", where the x's
	 * represent hexadecimal characters. The method uses String.substring to eliminate the SESSION_ID_NAME and
	 * the quotation marks the encapsulate the sessionID in the cookie.
	 * 
	 * If the passed Headers parameter doesn't contain a "Cookie" header, the method returns null.
	 * @param reqHeaders
	 * @return sessionID
	 */
	String getSessionID(Headers reqHeaders)
	{
		String sessionID = null;
		if(reqHeaders.containsKey("Cookie"))
		{
			List<String> reqHeaderList = reqHeaders.get("Cookie");
			for(String s: reqHeaderList)
				if(s.startsWith(SESSION_ID_NAME))
					sessionID = s.substring(SESSION_ID_NAME.length()+1, s.length()-1);
		}
		
		return sessionID;
	}
	
	String getUserFirstName(WebClient wc)
	{
		if(wc != null && wc.getWebUser().getFirstName().equals(""))
			 return wc.getWebUser().getLastName();
		else if(wc != null)
			return wc.getWebUser().getFirstName();
		else
			return "";
	}
	
	void printParameters(HttpExchange t)
	{
		@SuppressWarnings("unchecked")
		Map<String, Object> params = (Map<String, Object>)t.getAttribute("parameters");
		Set<String> keyset = params.keySet();
		for(String key:keyset)
			System.out.println(String.format("uri=%s, key=%s, value=%s", t.getRequestURI().toASCIIString(), key, params.get(key)));
	}
}
