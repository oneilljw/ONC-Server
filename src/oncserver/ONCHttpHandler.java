package oncserver;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import ourneighborschild.Agent;
import ourneighborschild.MealStatus;
import ourneighborschild.MealType;
import ourneighborschild.ONCAdult;
import ourneighborschild.ONCChild;
import ourneighborschild.ONCFamily;
import ourneighborschild.ONCMeal;
import ourneighborschild.ONCServerUser;
import ourneighborschild.ONCUser;
import ourneighborschild.Transportation;
import ourneighborschild.UserPermission;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.google.gson.Gson;

public class ONCHttpHandler implements HttpHandler
{
	private static final String REFERRAL_STATUS_HTML = "ScrollFamTable.htm";
	private static final String ONC_ELF_PAGE_HTML = "ONC.htm";
	private static final String UPDATE_HTML = "EditFamily.htm";
	private static final String LOGOUT_HTML = "logout.htm";
	private static final String MAINTENANCE_HTML = "maintenance.htm";
	private static final String REFERRAL_HTML = "FamilyReferral.htm";
	private static final String CHANGE_PASSWORD_HTML = "Change.htm";
	private static final String EDIT_PROFILE_HTML = "profile.htm";
	private static final int DEFAULT_YEAR = 2014;
	private static final int FAMILY_STOPLIGHT_RED = 2;
	private static final long DAYS_TO_MILLIS = 1000 * 60 * 60 * 24; 
	private static final int HTTP_OK = 200;
	
	public ONCHttpHandler()
	{
		
	}
	
	public void handle(HttpExchange t) throws IOException 
    {
    	@SuppressWarnings("unchecked")
		Map<String, Object> params = (Map<String, Object>)t.getAttribute("parameters");
    	
    	String requestURI = t.getRequestURI().toASCIIString();
    	
    	String mssg = String.format("HTTP request %s: %s:%s", t.getRemoteAddress().toString(), t.getRequestMethod(), requestURI);
		ServerUI serverUI = ServerUI.getInstance();
		serverUI.addLogMessage(mssg);
    	
    	if(requestURI.equals("/welcome"))
    	{
    		String response = null;
    		try {
    			if(ONCWebServer.isWebsiteOnline())
    			{
    				response = readFile(String.format("%s/%s",System.getProperty("user.dir"), LOGOUT_HTML));
    				response = response.replace("WELCOME_MESSAGE", "Welcome to Our Neighbor's Child, Please Login:");
    			}
    			else
    			{
    				response = readFile(String.format("%s/%s",System.getProperty("user.dir"), MAINTENANCE_HTML));
    				response = response.replace("TIME_BACK_UP", ONCWebServer.getWebsiteTimeBackOnline());
    			}
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    		
    		sendHTMLResponse(t, new HtmlResponse(response, HTTPCode.Ok));
    	}
    	else if(requestURI.contains("/logout"))
    	{
    		if(params.containsKey("token"))
    		{	
    			String sessionID = (String) params.get("token");
    			ClientManager clientMgr = ClientManager.getInstance();
    		
    			if(!clientMgr.logoutWebClient(sessionID))
    				clientMgr.addLogMessage(String.format("ONCHttpHandler.handle/logut: logout failure, client %s not found", sessionID));
    		} 
    		
    		Headers header = t.getResponseHeaders();
    		ArrayList<String> headerList = new ArrayList<String>();
    		headerList.add("http://www.ourneighborschild.org");
    		header.put("Location", headerList);
  	
    		sendHTMLResponse(t, new HtmlResponse("", HTTPCode.Redirect));
    		
    	}
    	else if(requestURI.contains("/startpage"))
    	{
    		//authenticate the web client by token and activity
    		WebClient wc = null;
    		if(params.containsKey("token"))
    		{	
    			String sessionID = (String) params.get("token");
    			ClientManager clientMgr = ClientManager.getInstance();
    			
    			wc = clientMgr.findClient(sessionID);
    		}
    			
    		if(wc != null)	//if the web client is authenticated, send the home page
    		{
    			//update time stamp, get the home page html and return it
    			wc.updateTimestamp();
    			
    			String response = getHomePageHTML(wc, wc.getWebUser().getFirstname(), "");
    			sendHTMLResponse(t, new HtmlResponse(response, HTTPCode.Ok));
    		}
    		else
    		{	
    			Headers header = t.getResponseHeaders();
    			ArrayList<String> headerList = new ArrayList<String>();
    			headerList.add("http://www.ourneighborschild.org");
    			header.put("Location", headerList);
    			sendHTMLResponse(t, new HtmlResponse("", HTTPCode.Redirect));
    		}	
    	}
    	else if(requestURI.contains("/login"))
    	{
    		sendHTMLResponse(t, loginRequest(t.getRequestMethod(), params, t));
    	}
    	else if(t.getRequestURI().toString().contains("/dbStatus"))
    	{
    		sendHTMLResponse(t, DBManager.getDatabaseStatusJSONP((String) params.get("callback")));
    	}
    	else if(requestURI.contains("/agents"))
    	{
    		int year = Integer.parseInt((String) params.get("year"));
    		
    		String sessionID = (String) params.get("token");
    		ClientManager clientMgr = ClientManager.getInstance();
    		WebClient client = clientMgr.findClient(sessionID);
    		
    		HtmlResponse response = AgentDB.getAgentsJSONP(year, 
    				client.getWebUser(), (String) params.get("callback"));
    		
    		sendHTMLResponse(t, response);
    	}
    	else if(requestURI.contains("/families"))
    	{
    		int year = Integer.parseInt((String) params.get("year"));
    		
    		HtmlResponse response = FamilyDB.getFamiliesJSONP(year, (String) params.get("callback"));
    		sendHTMLResponse(t, response);
    	}
    	else if(requestURI.contains("/references"))
    	{
    		int year = Integer.parseInt((String) params.get("year"));
    		
    		HtmlResponse response = FamilyDB.getFamilyReferencesJSONP(year, (String) params.get("callback"));
    		sendHTMLResponse(t, response);
    	}
    	else if(requestURI.contains("/familysearch"))
    	{
    		HtmlResponse response = FamilyDB.searchForFamilyReferencesJSONP(
    									Integer.parseInt((String) params.get("year")),
    									 (String) params.get("searchstring"),
    									  (String) params.get("callback"));
    		sendHTMLResponse(t, response);
    	}
    	else if(requestURI.contains("/getfamily"))
    	{
    		int year = Integer.parseInt((String) params.get("year"));
    		String targetID = (String) params.get("targetid");
    		
    		HtmlResponse response = FamilyDB.getFamilyJSONP(year, targetID, (String) params.get("callback"));
    		sendHTMLResponse(t, response);
    	}
    	else if(requestURI.contains("/getagent"))
    	{
    		int year = Integer.parseInt((String) params.get("year"));
    		int agtID = Integer.parseInt((String) params.get("agentid"));
    		
    		HtmlResponse response = AgentDB.getAgentJSONP(year, agtID, (String) params.get("callback"));
    		sendHTMLResponse(t, response);
    	}
    	else if(requestURI.contains("/getuser"))
    	{
    		String sessionID = (String) params.get("token");
    		
    		HtmlResponse response = ClientManager.getClientJSONP(sessionID, (String) params.get("callback"));
    		sendHTMLResponse(t, response);
    	}
    	else if(requestURI.contains("/getmeal"))
    	{
    		int year = Integer.parseInt((String) params.get("year"));
    		String targetID = (String) params.get("mealid");
    		
    		HtmlResponse response = ServerMealDB.getMealJSONP(year, targetID, (String) params.get("callback"));
    		sendHTMLResponse(t, response);
    	}
    	else if(requestURI.contains("/children"))
    	{
    		int year = Integer.parseInt((String) params.get("year"));
    		int famID = FamilyDB.getFamilyID(year, (String) params.get("targetid"));
    		
    		HtmlResponse response = ServerChildDB.getChildrenInFamilyJSONP(year, famID, (String) params.get("callback"));
    		sendHTMLResponse(t, response);
    	}
    	else if(requestURI.contains("/wishes"))
    	{
    		int year = Integer.parseInt((String) params.get("year"));
    		int childID = Integer.parseInt((String) params.get("childid"));
    		
    		HtmlResponse response = ServerChildDB.getChildWishesJSONP(year, childID, (String) params.get("callback"));
    		sendHTMLResponse(t, response);
    	}
    	else if(requestURI.contains("/adults"))
    	{
    		int year = Integer.parseInt((String) params.get("year"));
    		int famID = FamilyDB.getFamilyID(year, (String) params.get("targetid"));
    		
    		HtmlResponse response = ServerAdultDB.getAdultsInFamilyJSONP(year, famID, (String) params.get("callback"));
    		sendHTMLResponse(t, response);
    	}
    	else if(requestURI.contains("/oncsplash"))
    	{
    		// add the required response header for a PDF file
  	      	Headers h = t.getResponseHeaders();
  	      	h.add("Content-Type", "image/gif");

  	      	// onc splash screen
  	      	String path = String.format("%s/oncsplash.gif", System.getProperty("user.dir"));
  	      	File file = new File (path);
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
    	else if(requestURI.contains("/onclogo"))
    	{
    		// add the required response header for a PDF file
  	      	Headers h = t.getResponseHeaders();
  	      	h.add("Content-Type", "image/gif");

  	      	// onc splash screen
  	      	String path = String.format("%s/onclogosmall.gif", System.getProperty("user.dir"));
  	      	File file = new File (path);
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
    	else if(requestURI.contains("/vanilla.ttf"))
    	{
    		// add the required response header for a PDF file
  	      	Headers h = t.getResponseHeaders();
  	      	h.add("Content-Type", "application/octet-stream");

  	      	// onc splash screen
  	      	String path = String.format("%s/vanilla.ttf", System.getProperty("user.dir"));
  	      	File file = new File (path);
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
    	else if(requestURI.contains("/oncstylesheet"))
    	{
    		// add the required response header for a css style sheet
  	      	Headers h = t.getResponseHeaders();
  	      	h.add("Content-Type", "text/css");

  	      	// onc style sheet
  	      	String path = String.format("%s/ONCStyleSheet.css", System.getProperty("user.dir"));
  	      	File file = new File (path);
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
    	else if(requestURI.contains("/newfamily"))
    	{
    		String sessionID = (String) params.get("token");
    		ClientManager clientMgr = ClientManager.getInstance();
    		WebClient wc;
    		String response = null;
    		
    		if((wc=clientMgr.findClient(sessionID)) != null)	
    		{
    			wc.updateTimestamp();
    			try {	
    				response = readFile(String.format("%s/%s",System.getProperty("user.dir"), REFERRAL_HTML));
    			} catch (IOException e) {
    				System.out.println("Couldn't open/find " + REFERRAL_HTML);
    				e.printStackTrace();
    			}
   			
    			//remove the place holders
/*    			response = response.replace("REPLACE_TOKEN", sessionID);
    			response = response.replace("TARGETID","NNA");
    			response = response.replace("value=\"HOHFN\"","");
    			response = response.replace("value=\"HOHLN\"", "");
*/    			
    		}
    		else
    			response = invalidTokenReceived();
    		
    		sendHTMLResponse(t, new HtmlResponse(response, HTTPCode.Ok));
    	}
    	else if(requestURI.contains("/address"))
    	{
    		String sessionID = (String) params.get("token");
    		
//			Set<String> keyset = params.keySet();
//			for(String key:keyset)
//				System.out.println(String.format("/address key=%s, value=%s", key, params.get(key)));
    		
    		ClientManager clientMgr = ClientManager.getInstance();
    		WebClient wc;
    		String response = null;
    		
    		if((wc=clientMgr.findClient(sessionID)) != null)	
    		{
    			wc.updateTimestamp();
    			response = verifyAddress(params);	//verify the address and send response
    		}
    		else
    			response = invalidTokenReceived();
    		
    		sendHTMLResponse(t, new HtmlResponse(response, HTTPCode.Ok));
    	}
    	else if(requestURI.contains("/familystatus"))
    	{
    		String sessionID = (String) params.get("token");
    		ClientManager clientMgr = ClientManager.getInstance();
    		String response = null;
    		WebClient wc;
    			
    		if((wc=clientMgr.findClient(sessionID)) != null)
    		{
    			wc.updateTimestamp();
    			try
    			{
    				response = readFile(String.format("%s/%s",System.getProperty("user.dir"), REFERRAL_STATUS_HTML));
    				response = response.replace("USER_NAME", wc.getWebUser().getFirstname());
    				response = response.replace("USER_MESSAGE", "");
    				response = response.replace("REPLACE_TOKEN", wc.getSessionID().toString());
    				response = response.replace("THANSGIVING_CUTOFF", enableReferralButton("Thanksgiving"));
    				response = response.replace("DECEMBER_CUTOFF", enableReferralButton("December"));
    				response = response.replace("EDIT_CUTOFF", enableReferralButton("Edit"));
    				response = response.replace("HOME_LINK_VISIBILITY", getHomeLinkVisibility(wc));
    			}
    			catch (IOException e) 
    			{
    				response = "<p>Family Table Unavailable</p>";
    			}
    		}
    		else
    			response = invalidTokenReceived();
    		
    		sendHTMLResponse(t, new HtmlResponse(response, HTTPCode.Ok));
    	}
    	else if(requestURI.contains("/referral"))
    	{
    		String sessionID = (String) params.get("token");
    		ClientManager clientMgr = ClientManager.getInstance();
    		String response = null;
    		WebClient wc;
    			
    		if((wc=clientMgr.findClient(sessionID)) != null)
    		{
    			wc.updateTimestamp();
    			try {	
    				response = readFile(String.format("%s/%s",System.getProperty("user.dir"), REFERRAL_HTML));
    			} catch (IOException e) {
    				// TODO Auto-generated catch block
    				e.printStackTrace();
    			}
/*    			
    			//get the family
    			int year = Integer.parseInt((String) params.get("year"));
    			String targetID = (String) params.get("targetid");
    		
    			FamilyDB famDB = FamilyDB.getInstance();
    			ONCFamily fam = famDB.getFamilyByTargetID(year, targetID);    		
    			//replace the place holders
    			response = response.replace("REPLACE_TOKEN", sessionID);
    			response = response.replace("YEAR",(String) params.get("year"));
    			response = response.replace("TARGETID",targetID);
    			response = response.replace("HOHFN",fam.getHOHFirstName());
    			response = response.replace("HOHLN", fam.getHOHLastName());
*/    			
    		}
    		else
    			response = invalidTokenReceived();
    		
    		sendHTMLResponse(t, new HtmlResponse(response, HTTPCode.Ok));
    	}
    	else if(requestURI.contains("/referfamily"))
    	{
    		String sessionID = (String) params.get("token");
    		ClientManager clientMgr = ClientManager.getInstance();
    		String response = null;
    		WebClient wc;
    		
    		if((wc=clientMgr.findClient(sessionID)) != null && 
    				t.getRequestMethod().toLowerCase().equals("post"))
    		{
    			wc.updateTimestamp();
//    			Set<String> keyset = params.keySet();
//    			for(String key:keyset)
//    				System.out.println(String.format("/referfamily key=%s, value=%s", key, params.get(key)));
    		
    			FamilyResponseCode frc = processFamilyReferral(wc, params);
    			
    			//submission processed, send the family table page back to the user
    			String userFN;
    			if(wc.getWebUser().getFirstname().equals(""))
    				userFN = wc.getWebUser().getLastname();
    			else
    				userFN = wc.getWebUser().getFirstname();
    			
    			response = getHomePageHTML(wc, userFN, frc.getMessage());
//    			response = getFamilyTableHTML();
//    			response= response.replace("USER_NAME", userFN);
//    			response= response.replace("USER_MESSAGE", frc.getMessage());
//    	    	response = response.replace("REPLACE_TOKEN", wc.getSessionID().toString());
//    	    	response = response.replace("THANSGIVING_CUTOFF", enableReferralButton("Thanksgiving"));
//    	    	response = response.replace("DECEMBER_CUTOFF", enableReferralButton("December"));
//    	    	response = response.replace("EDIT_CUTOFF", enableReferralButton("Edit"));
    		}
    		else
    			response = invalidTokenReceived();
    		
    		sendHTMLResponse(t, new HtmlResponse(response, HTTPCode.Ok));
    	}
    	else if(requestURI.contains("/familyupdate"))
    	{
    		String sessionID = (String) params.get("token");
    		ClientManager clientMgr = ClientManager.getInstance();
    		String response = null;
    		WebClient wc;
    		
    		if((wc=clientMgr.findClient(sessionID)) != null)
    		{
    			wc.updateTimestamp();
    			try {	
    				response = readFile(String.format("%s/%s",System.getProperty("user.dir"), UPDATE_HTML));
    			} catch (IOException e) {
    				// TODO Auto-generated catch block
    				e.printStackTrace();
    			}
/*    		
    			//replace the place holders
    			response = response.replace("REPLACE_TOKEN", sessionID);
    			response = response.replace("YEAR",(String) params.get("year"));
    			response = response.replace("TARGETID", (String) params.get("targetid"));
*/    			
    		}
    		else
    			response = invalidTokenReceived();
    		
    		sendHTMLResponse(t, new HtmlResponse(response, HTTPCode.Ok));
    	}
    	else if(requestURI.contains("/updatefamily"))
    	{
    		String sessionID = (String) params.get("token");
    		ClientManager clientMgr = ClientManager.getInstance();
    		String response = null;
    		WebClient wc; 		
       		
//    		Set<String> keyset = params.keySet();
//    		for(String key:keyset)
//    			System.out.println(String.format("Key=%s, value=%s", key, (String)params.get(key)));
    		
    		if((wc=clientMgr.findClient(sessionID)) != null && 
    				t.getRequestMethod().toLowerCase().equals("post"))
    		{
    			wc.updateTimestamp();
    			FamilyResponseCode frc = processFamilyUpdate(wc, params);
    			
    			//submission processed, send the family table page back to the user
    			String userFN;
    			if(wc.getWebUser().getFirstname().equals(""))
    				userFN = wc.getWebUser().getLastname();
    			else
    				userFN = wc.getWebUser().getFirstname();
    			
    			response = getHomePageHTML(wc, userFN, frc.getMessage());
//    			response = getFamilyTableHTML();
//    			response= response.replace("USER_NAME", userFN);
//    			response= response.replace("USER_MESSAGE", frc.getMessage());
//    	    	response = response.replace("REPLACE_TOKEN", wc.getSessionID().toString());
//    	    	response = response.replace("THANSGIVING_CUTOFF", enableReferralButton("Thanksgiving"));
//    	    	response = response.replace("DECEMBER_CUTOFF", enableReferralButton("December"));
//    	    	response = response.replace("EDIT_CUTOFF", enableReferralButton("Edit"));
    		}
    		else
    			response = invalidTokenReceived();
    		
    		sendHTMLResponse(t, new HtmlResponse(response, HTTPCode.Ok));
    	}
    	else if(requestURI.contains("/changepw"))
    	{
    		String sessionID = (String) params.get("token");
    		ClientManager clientMgr = ClientManager.getInstance();
    		String response = null;
    		WebClient wc;
    		
    		if((wc=clientMgr.findClient(sessionID)) != null && 
    				t.getRequestMethod().toLowerCase().equals("post"))
    		{
    			wc.updateTimestamp();
    			
//    			Set<String> keyset = params.keySet();
//    			for(String key:keyset)
//    				System.out.println("ONCHttpHandler /changepw: key: " + key);
    			
    			ServerUserDB serveruserDB = null;
    			try {
    				serveruserDB = ServerUserDB.getInstance();
    			} catch (NumberFormatException e) {
    				// TODO Auto-generated catch block
    				e.printStackTrace();
    			} catch (IOException e) {
    				// TODO Auto-generated catch block
    				e.printStackTrace();
    			}
    			 
    			int retCode = serveruserDB.changePassword((String) params.get("field1"),
    							(String) params.get("field2"), wc);
    			
    			if(retCode == 0)
    			{
    				//submission successful, send the family table page back to the user
    				String userFN;
        			if(wc.getWebUser().getFirstname().equals(""))
        				userFN = wc.getWebUser().getLastname();
        			else
        				userFN = wc.getWebUser().getFirstname();
        			response = getHomePageHTML(wc, userFN, "Your password change was successful!");
//    				response = getFamilyTableHTML();
//    				response = response.replace("USER_NAME", userFN);
//    				response = response.replace("USER_MESSAGE", "Your password change was successful!");
//    	    		response = response.replace("REPLACE_TOKEN", wc.getSessionID().toString());
//    	    		response = response.replace("THANSGIVING_CUTOFF", enableReferralButton("Thanksgiving"));
//        	    	response = response.replace("DECEMBER_CUTOFF", enableReferralButton("December"));
//        	    	response = response.replace("EDIT_CUTOFF", enableReferralButton("Edit"));
    			}
    			else if(retCode == -1)
    			{
    				response = "<!DOCTYPE html><html><head lang=\"en\"><title>Password Change Failed</title>"
    						+ "</head><body><p>Change password failed, current password incorrect. Please try again</p></body></html>";
    			}
    			else if(retCode == -2)
    			{
    				response = "<!DOCTYPE html><html><head lang=\"en\"><title>Password Change Failed</title>"
    						+ "</head><body><p>Change password failed, user couldn't be located, please contact ONC Exec Dir</p></body></html>";
    			}
    		}
    		else
    			response = invalidTokenReceived();
    		
    		sendHTMLResponse(t, new HtmlResponse(response, HTTPCode.Ok));
    	}
    	else if(requestURI.contains("/profile"))
    	{
    		String response = null;
    		try 
    		{
    			response = readFile(String.format("%s/%s",System.getProperty("user.dir"), EDIT_PROFILE_HTML));
    		}
    		catch (IOException e) 
    		{
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    		
    		sendHTMLResponse(t, new HtmlResponse(response, HTTPCode.Ok));
    	}
    }
	
	void sendHTMLResponse(HttpExchange t, HtmlResponse html) throws IOException
	{
		t.sendResponseHeaders(html.getCode(), html.getResponse().length());
		OutputStream os = t.getResponseBody();
		os.write(html.getResponse().getBytes());
		os.close();
		t.close();
	}
	
	HtmlResponse loginRequest(String method, Map<String, Object> params, HttpExchange t)
	{
		HtmlResponse response = null;
		
		ServerUserDB userDB = null;
		try {
			userDB = ServerUserDB.getInstance();
		} catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		String html = "<!DOCTYPE html><html>"
	    		+"<body>"
	    		+"<p><b><i>Welcome to Our Neighbors Child";    		
		
		if(method.toLowerCase().equals("post"))
		{
			String userID = (String) params.get("field1");
	    	String password = (String) params.get("field2");
	    	
	    	//don't want a reference here, want a new object. A user can be logged in more than once.
	    	//However, never can use this object to update a user's info
	    	ONCServerUser serverUser = (ONCServerUser) userDB.find(userID);
	  
	    	if(serverUser == null)	//can't find the user in the data base
	    	{
	    		html += "</i></b></p><p>User name not found</p></body></html>";
	    		response = new HtmlResponse(html, HTTPCode.Forbidden);
	    	}
	    	else if(serverUser != null && serverUser.getPermission() == UserPermission.INACTIVE)	//can't find the user in the data base
	    	{
	    		html += "</i></b></p><p>Inactive user account, please contact the executive director</p></body></html>";
	    		response = new HtmlResponse(html, HTTPCode.Forbidden);
	    	}
	    	else if(serverUser != null && !serverUser.pwMatch(password))	//found the user but pw is incorrect
	    	{
	    		html += "</i></b></p><p>Incorrect password, access denied</p></body></html>";
	    		response = new HtmlResponse(html, HTTPCode.Forbidden);
	    	}
	    	else if(serverUser != null && serverUser.pwMatch(password))	//user found, password matches
	    	{
	    		//get the old data before updating
	    		long nLogins = serverUser.getNSessions();
	    		Date lastLogin = serverUser.getLastLogin();
	    		
	    		serverUser.incrementSessions();	
	    		serverUser.setLastLogin(new Date());
	    		userDB.save(DEFAULT_YEAR);	//year will equal -1 at this point, but ignored. Only one user.csv
	    		
	    		ONCUser webUser = serverUser.getUserFromServerUser();
	    		ClientManager clientMgr = ClientManager.getInstance();
    			WebClient wc = clientMgr.addWebClient(t, webUser);
    			
	    		//has the current password expired? If so, send change password page
	    		if(serverUser.changePasswordRqrd())
	    		{
	    			try {	
	    				html = readFile(String.format("%s/%s",System.getProperty("user.dir"), CHANGE_PASSWORD_HTML));
	    			} catch (IOException e) {
	    				// TODO Auto-generated catch block
	    				e.printStackTrace();
	    			}
	    			
	    			Gson gson = new Gson();
	    			String loginJson = gson.toJson(webUser, ONCUser.class);
	    		
	    			String mssg = "UPDATED_USER" + loginJson;
	    			clientMgr.notifyAllClients(mssg);
	    			
	    			//replace the HTML place holders
	    			if(serverUser.getFirstname().equals(""))
	    				html = html.replace("USERFN", serverUser.getLastname());
	    			else
	    				html = html.replace("USERFN", serverUser.getFirstname());
	    			
	    			html = html.replace("REPLACE_TOKEN", wc.getSessionID().toString());
	    			html = html.replace("THANKSGIVING_CUTOFF", enableReferralButton("Thanksgiving"));
	    			html = html.replace("DECEMBER_CUTOFF", enableReferralButton("December"));
	    			html = html.replace("EDIT_CUTOFF", enableReferralButton("Edit"));
	    			
	    			response = new HtmlResponse(html, HTTPCode.Ok);
	    		}
//	    		else if(serverUser.getPermission() == UserPermission.SYS_ADMIN ) //send the elf home page
//	    		{
//	    			Gson gson = new Gson();
//	    			String loginJson = gson.toJson(webUser, ONCUser.class);
//	    		
//	    			String mssg = "UPDATED_USER" + loginJson;
//	    			clientMgr.notifyAllClients(mssg);
//	    			
//	    			//determine if user never visited or last login date
//	    			String userMssg;
//	    			if(nLogins == 0)
//	    				userMssg = "This is your first visit!";
//	    			else
//	    			{
//	    				SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM d, yyyy h:mm a z");
//	    				SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM d, yyyy");
//	    				sdf.setTimeZone(TimeZone.getDefault());
//	    				userMssg = "You last visited " + sdf.format(lastLogin);
//	    			}
//	    		
//	    			html = getONCElfPageHTML();
//	    			
//	    			if(serverUser.getFirstname().equals(""))
//	    				html = html.replace("USER_NAME", serverUser.getLastname());
//	    			else
//	    				html = html.replace("USER_NAME", serverUser.getFirstname());
//
//	    			html = html.replace("USER_MESSAGE", userMssg);
//	    			html = html.replace("REPLACE_TOKEN", wc.getSessionID().toString());
//	    			
//	    			response = new HtmlResponse(html, HTTPCode.Ok);
//	    		}
	    		else //send the home page
	    		{
	    			Gson gson = new Gson();
	    			String loginJson = gson.toJson(webUser, ONCUser.class);
	    		
	    			String mssg = "UPDATED_USER" + loginJson;
	    			clientMgr.notifyAllClients(mssg);
	    			
	    			//determine if user never visited or last login date
	    			String userMssg;
	    			if(nLogins == 0)
	    				userMssg = "This is your first visit!";
	    			else
	    			{
//	    				SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM d, yyyy h:mm a z");
	    				SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM d, yyyy");
	    				sdf.setTimeZone(TimeZone.getDefault());
	    				userMssg = "You last visited " + sdf.format(lastLogin);
	    			}
	    			
	    			String username = "";
	    			if(serverUser.getFirstname().equals(""))
	    				username = serverUser.getLastname();
	    			else
	    				username =  serverUser.getFirstname();
	    		
	    			html = getHomePageHTML(wc, username, userMssg);
	    			
//	    			html = getFamilyTableHTML();
//	    			
//	    			if(serverUser.getFirstname().equals(""))
//	    				html = html.replace("USER_NAME", serverUser.getLastname());
//	    			else
//	    				html = html.replace("USER_NAME", serverUser.getFirstname());
//
//	    			html = html.replace("USER_MESSAGE", userMssg);
//	    			html = html.replace("REPLACE_TOKEN", wc.getSessionID().toString());
//	    			html = html.replace("THANKSGIVING_CUTOFF", enableReferralButton("Thanksgiving"));
//	    			html = html.replace("DECEMBER_CUTOFF", enableReferralButton("December"));
//	    			html = html.replace("EDIT_CUTOFF", enableReferralButton("Edit"));
	    			
	    			response = new HtmlResponse(html, HTTPCode.Ok);
	    		}
	    	}   	
		}
		else
		{
			html += "<p>Invalid Request Method</p></body></html>";
			response = new HtmlResponse(html, HTTPCode.Forbidden);
		}
		
		return response;
	}
	
	String getHomePageHTML(WebClient wc, String username, String message)
	{
		String homePageHTML;
		//determine which home page, elf or agent
		if(wc.getWebUser().getPermission() == UserPermission.ADMIN ||
				wc.getWebUser().getPermission() == UserPermission.SYS_ADMIN)
		{
			//read the onc page html
			try
			{
				homePageHTML = readFile(String.format("%s/%s",System.getProperty("user.dir"), ONC_ELF_PAGE_HTML));
				homePageHTML = homePageHTML.replace("USER_NAME", username);
				homePageHTML = homePageHTML.replace("USER_MESSAGE", message);
				homePageHTML = homePageHTML.replace("REPLACE_TOKEN", wc.getSessionID().toString());
				return homePageHTML;
			}
			catch (IOException e) 
			{
				return "<p>ONC Elf Page Unavailable</p>";
			}
		}
		else
		{
			try
			{
				homePageHTML = readFile(String.format("%s/%s",System.getProperty("user.dir"), REFERRAL_STATUS_HTML));
				homePageHTML = homePageHTML.replace("USER_NAME", username);
				homePageHTML = homePageHTML.replace("USER_MESSAGE", message);
				homePageHTML = homePageHTML.replace("REPLACE_TOKEN", wc.getSessionID().toString());
				homePageHTML = homePageHTML.replace("THANSGIVING_CUTOFF", enableReferralButton("Thanksgiving"));
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
	
	String getHomeLinkVisibility(WebClient wc)
	{
		if(wc.getWebUser().getPermission() == UserPermission.ADMIN ||
				wc.getWebUser().getPermission() == UserPermission.SYS_ADMIN)
		{
			return "visible";
		}
		else
			return "hidden";
	}
	
	String getFamilyTableHTML()
	{
		//read the family table html
		String famTableHTML = null;
		try
		{
			famTableHTML = readFile(String.format("%s/%s",System.getProperty("user.dir"), REFERRAL_STATUS_HTML));
			return famTableHTML;
		}
		catch (IOException e) 
		{
			return "<p>Family Table Unavailable</p>";
		}
	}
	
	String getONCElfPageHTML()
	{
		//read the onc page html
		String oncElfPageHTML = null;
		try
		{
			oncElfPageHTML = readFile(String.format("%s/%s",System.getProperty("user.dir"), ONC_ELF_PAGE_HTML));
			return oncElfPageHTML;
		}
		catch (IOException e) 
		{
			return "<p>ONC Elf Page Unavailable</p>";
		}
	}
	
	String invalidTokenReceived()
	{
		String response = null;
		try {
			if(ONCWebServer.isWebsiteOnline())
			{
				response = readFile(String.format("%s/%s",System.getProperty("user.dir"), LOGOUT_HTML));
				response.replace("WELCOME_MESSAGE", "Your session expired, please login again:");
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

	private String readFile( String file ) throws IOException
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
	
	String verifyAddress(Map<String, Object> params)
	{
		String callback = (String) params.get("callback");
		
		String[] addressKeys = {"housenum", "street", "unit", "city", "zipcode"};
		
		Map<String, String> addressMap = createMap(params, addressKeys);
		
//		for(String key:addressMap.keySet())
//			System.out.println(String.format("ONCHttpHandler.verifyAddress: key=%s, value=%s", key, addressMap.get(key)));	

		boolean bAddressValid  = RegionDB.isAddressValid(addressMap.get("housenum"), addressMap.get("street"), addressMap.get("zipcode"));
		int errorCode = bAddressValid ? 0 : 1;
		
		int priorYear = Integer.parseInt((String)params.get("prioryear"));
		
		boolean bUnitMissing = addressMap.get("unit").isEmpty() && FamilyDB.shouldAddressHaveUnit(priorYear,
														addressMap.get("housenum"),
														addressMap.get("street"),
														addressMap.get("zipcode"));
		
		if(bUnitMissing)
			errorCode += 2;
		
//		System.out.println("HttpHandler.verifyAddress: ErrorCode: "+ errorCode);		
		boolean bAddressGood = bAddressValid && !bUnitMissing;
		
		Gson gson = new Gson();
		String json = gson.toJson(new AddressValidation(bAddressGood, errorCode), AddressValidation.class);
		
		return callback +"(" + json +")";
	}
	
	FamilyResponseCode processFamilyReferral(WebClient wc, Map<String, Object> params)
	{
		//get the agent
		int year = Integer.parseInt((String) params.get("year"));
		Agent agt = AgentDB.getAgent(year, wc.getWebUser());
		
		if(agt == null)
		{
			return new FamilyResponseCode(-1, "Family Referral Rejected: Referring Agent Not Found");
		}

		//get database references
		ServerMealDB mealDB = null;
		FamilyDB familyDB= null;
		ServerChildDB childDB = null;
		ServerAdultDB adultDB = null;
		
		try
		{
			mealDB = ServerMealDB.getInstance();
			familyDB = FamilyDB.getInstance();
			childDB = ServerChildDB.getInstance();
			adultDB = ServerAdultDB.getInstance();
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
		
		//create a meal request, if meal was requested
		ONCMeal mealReq = null, addedMeal = null;
		String[] mealKeys = {"mealtype", "dietres"};
		
		if(params.containsKey(mealKeys[0]))
		{
			Map<String, String> mealMap = createMap(params, mealKeys);

			if(!mealMap.get(mealKeys[0]).equals("No Assistance Rqrd"))
			{
				mealReq = new ONCMeal(-1, -1, MealType.valueOf(mealMap.get(mealKeys[0])),
								mealMap.get(mealKeys[1]), -1, wc.getWebUser().getLNFI(), new Date(), 3,
								"Family Referred", wc.getWebUser().getLNFI());
			
				addedMeal = mealDB.add(year, mealReq);
			}
		}
		//create the family
		String[] familyKeys = {"targetid", "language", "hohFN", "hohLN", "housenum", "street", "unit", "city",
				   "zipcode", "homephone", "cellphone", "altphone", "email","delhousenum", 
				   "delstreet","detail", "delunit", "delcity", "delzipcode", "transportation"};
		
		Map<String, String> familyMap = createMap(params, familyKeys);
		
		//check to see if this is a new family or a re-referral. Need to know this to determine 
		//whether to perform a prior year check after the family and children objects are created
		String targetID = familyMap.get("targetid");
		boolean bNewFamily = targetID.contains("NNA") || targetID.equals("");
		
		ONCFamily fam = new ONCFamily(-1, wc.getWebUser().getLNFI(), "NNA",
					familyMap.get("targetid"), "B-DI", 
					familyMap.get("language").equals("English") ? "Yes" : "No", familyMap.get("language"),
					familyMap.get("hohFN"), familyMap.get("hohLN"), familyMap.get("housenum"),
					ensureUpperCaseStreetName(familyMap.get("street")),
					familyMap.get("unit"), familyMap.get("city"),
					familyMap.get("zipcode"), familyMap.get("delhousenum"),
					ensureUpperCaseStreetName(familyMap.get("delstreet")),
					familyMap.get("delunit"), familyMap.get("delcity"), familyMap.get("delzipcode"),
					familyMap.get("homephone"), familyMap.get("cellphone"), familyMap.get("altphone"),
					familyMap.get("email"), familyMap.get("detail"), createFamilySchoolList(params),
					createWishList(params), agt.getID(), addedMeal != null ? addedMeal.getID() : -1,
					addedMeal != null ? MealStatus.Requested : MealStatus.None,
					Transportation.valueOf(familyMap.get("transportation")));
			
		ONCFamily addedFamily = familyDB.add(year, fam);
		
		List<ONCChild> addedChildList = new ArrayList<ONCChild>();
		List<ONCAdult> addedAdultList = new ArrayList<ONCAdult>();
		if(addedFamily != null)
		{
			//update the family id for the meal, if a meal was requested
			if(addedMeal != null)
			{
				addedMeal.setFamilyID(addedFamily.getID());
				mealDB.update(year, addedMeal);
			}
			
			//create the children for the family
			String childfn, childln, childDoB, childGender, childSchool;
			
			int cn = 0;
			String key = "childfn" + Integer.toString(cn);
			
			//using child first name as the iterator, create a db entry for each
			//child in the family
			while(params.containsKey(key))
			{
				childfn = (String) params.get(key);
				childln = (String) params.get("childln" + Integer.toString(cn));
				childDoB = (String) params.get("childdob" + Integer.toString(cn));
				childGender = (String) params.get("childgender" + Integer.toString(cn));
				childSchool = (String) params.get("childschool" + Integer.toString(cn));
			
				if(!childln.isEmpty())	//only add a child if the last name is provided
				{
					ONCChild child = new ONCChild(-1, addedFamily.getID(), childfn, childln, childGender, 
													createChildDOB(childDoB), childSchool, year);
				
					addedChildList.add(childDB.add(year,child));
				}
				cn++;
				key = "childfn" + Integer.toString(cn);	//get next child key
			}
			
			//now that we have added children, we can check for duplicate family in this year.
			ONCFamily dupFamily = familyDB.getDuplicateFamily(year, addedFamily, addedChildList);
			
//			if(dupFamily != null)
//				System.out.println(String.format("HttpHandler.processFamilyReferral: "
//						+ "dupFamily HOHLastName= %s, dupRef#= %s, addedFamily HOHLastName = %s, addedFamily Ref#= %s", 
//						dupFamily.getHOHLastName(), dupFamily.getODBFamilyNum(), 
//						addedFamily.getHOHLastName(), addedFamily.getODBFamilyNum()));
//			
			if(dupFamily == null)	//if not a dup, then for new families, check for prior year
			{
				//added family not in current year, check if in prior years
				//only check new families for prior year existence. If a re-referral,
				//we already know the reference id was from prior year
				ONCFamily pyFamily = null;
				if(bNewFamily)	
				{
					pyFamily = familyDB.isPriorYearFamily(year, addedFamily, addedChildList);
					if(pyFamily != null)
					{				
						//added new family was in prior year, keep the prior year reference # 
						//and reset the newly assigned target id index
						addedFamily.setODBFamilyNum(pyFamily.getODBFamilyNum());
						familyDB.decrementReferenceNumber();
					}
				}
			}
			//else if family was a dup, determine which family has the best reference number to
			//use. The family with the best reference number is retained and the family with 
			//the worst reference number is marked as duplicate
			else if(!dupFamily.getODBFamilyNum().startsWith("C") && 
						addedFamily.getODBFamilyNum().startsWith("C"))
			{
//				System.out.println(String.format("HttpHandler.processFamilyReferral, dupFamily no C: "
//						+ "dupFamily HOHLastName= %s, dupRef#= %s, addedFamily HOHLastName = %s, addedFamily Ref#= %s", 
//						dupFamily.getHOHLastName(), dupFamily.getODBFamilyNum(), 
//						addedFamily.getHOHLastName(), addedFamily.getODBFamilyNum()));
				
				//family is in current year already with an ODB referred target ID
				addedFamily.setONCNum("DEL");
				addedFamily.setDNSCode("DUP");
				addedFamily.setStoplightPos(FAMILY_STOPLIGHT_RED);
				addedFamily.setStoplightMssg("DUP of " + dupFamily.getODBFamilyNum());
				addedFamily.setODBFamilyNum(dupFamily.getODBFamilyNum());
				familyDB.decrementReferenceNumber();
			}	
			else if(dupFamily.getODBFamilyNum().startsWith("C") && 
					!addedFamily.getODBFamilyNum().startsWith("C"))
			{
//				System.out.println(String.format("HttpHandler.processFamilyReferral: dupFamily with C "
//						+ "dupFamily HOHLastName= %s, dupRef#= %s, addedFamily HOHLastName = %s, addedFamily Ref#= %s", 
//						dupFamily.getHOHLastName(), dupFamily.getODBFamilyNum(), 
//						addedFamily.getHOHLastName(), addedFamily.getODBFamilyNum()));
				
				//family is already in current year with an ONC referred target ID and added family 
				//does not have an ONC target id. In this situation, we can't decrement the assigned
				//ONC based target id and will just have to burn one.
				dupFamily.setONCNum("DEL");
				dupFamily.setDNSCode("DUP");
				dupFamily.setStoplightPos(FAMILY_STOPLIGHT_RED);
				dupFamily.setStoplightMssg("DUP of " + addedFamily.getODBFamilyNum());
				dupFamily.setStoplightChangedBy(wc.getWebUser().getLNFI());
				dupFamily.setODBFamilyNum(addedFamily.getODBFamilyNum());
			}
			else if(dupFamily.getODBFamilyNum().startsWith("C") && 
					addedFamily.getODBFamilyNum().startsWith("C"))
			{
				//which one was first?
				int dupNumber = Integer.parseInt(dupFamily.getODBFamilyNum().substring(1));
				int addedNumber = Integer.parseInt(addedFamily.getODBFamilyNum().substring(1));
				
				if(dupNumber < addedNumber)
				{
					//dup family has the correct ref #, so added family is duplicate
					addedFamily.setONCNum("DEL");
					addedFamily.setDNSCode("DUP");
					addedFamily.setStoplightPos(FAMILY_STOPLIGHT_RED);
					addedFamily.setStoplightMssg("DUP of " + dupFamily.getODBFamilyNum());
					addedFamily.setStoplightChangedBy(wc.getWebUser().getLNFI());
					addedFamily.setODBFamilyNum(dupFamily.getODBFamilyNum());
					familyDB.decrementReferenceNumber();
				}
				else
				{
					//added family has the correct ref #, so dup family is the duplicate
					dupFamily.setONCNum("DEL");
					dupFamily.setDNSCode("DUP");
					dupFamily.setStoplightPos(FAMILY_STOPLIGHT_RED);
					dupFamily.setStoplightMssg("DUP of " + addedFamily.getODBFamilyNum());
					dupFamily.setStoplightChangedBy(wc.getWebUser().getLNFI());
					dupFamily.setODBFamilyNum(addedFamily.getODBFamilyNum());
				}
			}
			
			//create the other adults in the family
			String adultName, adultGender;
			
			int an = 0;
			key = "adultname" + Integer.toString(an);
			
			//using adult name as the iterator, create a db entry for each
			//adult in the family
			while(params.containsKey(key))
			{
				adultName = (String) params.get(key);
				adultGender = (String) params.get("adultgender" + Integer.toString(an));
				
				if(!adultName.isEmpty())
				{
					ONCAdult adult = new ONCAdult(-1, addedFamily.getID(), adultName, adultGender); 
				
					addedAdultList.add(adultDB.add(year, adult));
				}
				an++;
				key = "adultname" + Integer.toString(an);	//get next adult key
			}
			
		}
		
		//successfully process meals, family, children and adults. Notify the desktop
		//clients so they refresh
		ClientManager clientMgr = ClientManager.getInstance();
		Gson gson = new Gson();
		String mssg;
		
		if(addedFamily != null)
		{
			for(ONCChild addedChild:addedChildList)
			{
				mssg = "ADDED_CHILD" + gson.toJson(addedChild, ONCChild.class);
				clientMgr.notifyAllInYearClients(year, mssg);
			}
			
			for(ONCAdult addedAdult:addedAdultList)
			{
				mssg = "ADDED_ADULT" + gson.toJson(addedAdult, ONCAdult.class);
				clientMgr.notifyAllInYearClients(year, mssg);
			}
			
			mssg = "ADDED_FAMILY" + gson.toJson(addedFamily, ONCFamily.class);
			clientMgr.notifyAllInYearClients(year, mssg);
			
			//must add family before meal, since desktop client meal ui updates 
			//based on families in database, then gets meal id from family. So
			//family must be mirrored in client local db before meal is added
			if(addedMeal != null)
			{
				mssg = "ADDED_MEAL" + gson.toJson(addedMeal, ONCMeal.class);
				clientMgr.notifyAllInYearClients(year, mssg);
			}
			
		}
		
		return new FamilyResponseCode(0, addedFamily.getHOHLastName() + " Family Referral Accepted");
	}
	
	String ensureUpperCaseStreetName(String street)
	{
		StringBuffer buff = new StringBuffer();
		String[] streetparts = street.split(" ");
		for(int i=0; i< streetparts.length; i++)
		{	
			if(streetparts[i].length() > 0)
			{
				buff.append(Character.toUpperCase(streetparts[i].charAt(0)) +
						streetparts[i].substring(1) + " ");
			}
		}
		
		return buff.toString().trim();
	}
	
	String createFamilySchoolList(Map<String, Object> params)
	{
		int cn = 0, nSchoolsAdded = 0;
		String key = "childschool" + Integer.toString(cn);
		StringBuffer buff = new StringBuffer();
		
		//using child school as the iterator, create list of unique schools
		while(params.containsKey(key))
		{
			String school = (String) params.get(key);
			if(school != null && !school.equals("") && buff.indexOf(school) == -1)
			{
				if(nSchoolsAdded > 0)
					buff.append("\r" + school);
				else
					buff.append(school);
				
				nSchoolsAdded++;
			}
			
			key = "childschool" + Integer.toString(++cn);
		}
		
		if(nSchoolsAdded > 0)
			return buff.toString();
		else
			return "";
	}
	
	FamilyResponseCode processFamilyUpdate(WebClient wc, Map<String, Object> params)
	{
		//get the agent
		int year = Integer.parseInt((String) params.get("year"));
		Agent agt = AgentDB.getAgent(year, wc.getWebUser());
		
		if(agt == null)
		{
			return new FamilyResponseCode(-1, "Family Referral Rejected: Referring Agent Not Found");
		}

		//get database references
		FamilyDB familyDB= null;
		
		try
		{
			familyDB = FamilyDB.getInstance();
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
		
		//get the family object from the database
		String targetID = (String) params.get("targetid");
		ONCFamily updateFam = familyDB.getFamilyByTargetID(year, targetID);
		
		//if found, make the changes to the family object
		if(updateFam != null)
		{
			String[] familyKeys = {"targetid", "language", "hohFN", "hohLN", "housenum", "street", "unit", "city",
					   "zipcode", "homephone", "cellphone", "altphone", "email","delhousenum", 
					   "delstreet","detail", "delunit", "delcity", "delzipcode"};
			
			Map<String, String> familyMap = createMap(params, familyKeys);
			
			updateFam.setLanguage(familyMap.get("language"));
			updateFam.setHOHFirstName(familyMap.get("hohFN"));
			updateFam.setHOHLastName(familyMap.get("hohLN"));
			updateFam.setHouseNum(familyMap.get("housenum"));
			updateFam.setStreet(ensureUpperCaseStreetName(familyMap.get("street")));
			updateFam.setUnitNum(familyMap.get("unit"));
			updateFam.setCity(familyMap.get("city"));
			updateFam.setZipCode(familyMap.get("zipcode"));
			
			if(familyMap.get("housenum").equals(familyMap.get("delhousenum")) &&
				familyMap.get("street").equals(familyMap.get("delstreet")) &&
				 familyMap.get("unit").equals(familyMap.get("delunit")) &&
				  familyMap.get("city").equals(familyMap.get("delcity")) &&
				   familyMap.get("zipcode").equals(familyMap.get("delzipcode")))
				   {
					  updateFam.setSubstituteDeliveryAddress("");
				   }
			else
			{
				String altAddress = familyMap.get("delhousenum") + "_" +
									ensureUpperCaseStreetName(familyMap.get("delstreet")) + "_" +
									familyMap.get("delunit") + "_" +
									familyMap.get("delcity") + "_" +
									familyMap.get("delzipcode");
				updateFam.setSubstituteDeliveryAddress(altAddress);
			}
			
			updateFam.setHomePhone(familyMap.get("homephone"));
			
			if(familyMap.get("altphone").equals(""))
				updateFam.setOtherPhon(familyMap.get("cellphone"));
			else
				updateFam.setOtherPhon(familyMap.get("cellphone") +"\n" + familyMap.get("altphone"));
			updateFam.setFamilyEmail(familyMap.get("email"));
			updateFam.setDetails(familyMap.get("detail"));	
			
			//if changes detected, update the family in the db
			if(familyDB.update(year, updateFam, true) != null)
			{
				//successfully updated family. Notify the desktop clients so they refresh
				ClientManager clientMgr = ClientManager.getInstance();
				Gson gson = new Gson();
				String mssg;
				mssg = "UPDATED_FAMILY" + gson.toJson(updateFam, ONCFamily.class);
				clientMgr.notifyAllInYearClients(year, mssg);
				return new FamilyResponseCode(0, updateFam.getHOHLastName() + " Family Update Accepted");
			}
			
			return new FamilyResponseCode(-1, "Family Referral Rejected: Unable to Save Update");
		}
		
		return new FamilyResponseCode(-1, "Family Referral Rejected: Family Not Found");
	}

	Map<String, String> createMap(Map<String, Object> params, String[] keys)
	{
		Map<String, String> map = new HashMap<String, String>();
		for(String key:keys)
		{
			String value = "";
			if(params.containsKey(key))
				value = (String) params.get(key) != null ? (String) params.get(key) : "";
		
			map.put(key, value);
		}
		
		return map;
	}
	
	/**************************************************************************************************
	 * This method takes a string date in one of two formats (yyyy-MM-dd or M/D/yy) and returns a Date
	 * object from the string. If the input string is not of either format, the current date is returned.
	 ***************************************************************************************************/
    Long createChildDOB(String dob)
    {
		TimeZone timezone = TimeZone.getTimeZone("GMT");
		Calendar gmtDOB = Calendar.getInstance(timezone);
		
		SimpleDateFormat websitesdf = new SimpleDateFormat();
		websitesdf.setTimeZone(TimeZone.getTimeZone("GMT"));
    	
    	//Create a date formatter to  parse the input string to create an Calendar
		//variable for DOB. If it can't be determined, set DOB = today.
		if(dob.length() == 10 && dob.contains("-"))
			websitesdf.applyPattern("MM-dd-yyyy");
		else if(dob.length() < 10 && dob.contains("-"))
			websitesdf.applyPattern("M-d-yy");
		else if(dob.length() == 10 && dob.contains("/"))
			websitesdf.applyPattern("MM/dd/yyyy");
		else
			websitesdf.applyPattern("M/d/yy");
		
		try
		{
			gmtDOB.setTime(websitesdf.parse(dob));
			gmtDOB.set(Calendar.HOUR_OF_DAY, 0);
			gmtDOB.set(Calendar.MINUTE, 0);
			gmtDOB.set(Calendar.SECOND, 0);
			gmtDOB.set(Calendar.MILLISECOND, 0);
			
			//perform a check to see that the dob is in UTC with no hours, minutes or seconds
			//if that's not true correct it.
			if(gmtDOB.getTimeInMillis() % DAYS_TO_MILLIS != 0)
			{
//				System.out.println(String.format("HttpHandler.createChildDOB: Set Time= %d",
//						gmtDOB.getTimeInMillis()));
				float badDOBinDays = gmtDOB.getTimeInMillis() / DAYS_TO_MILLIS;
				int goodDOBinDays = (int) (badDOBinDays + 0.5);
				gmtDOB.setTimeInMillis(goodDOBinDays * DAYS_TO_MILLIS);
//				System.out.println(String.format("HttpHandler.createChildDOB: Adj Time= %d",
//						gmtDOB.getTimeInMillis()));
			}
		}
		catch (ParseException e)
		{
			String errMssg = "Couldn't determine DOB from input: " + dob;
		}

    	//then convert the Calendar to a Date in Millis and return it
    	return gmtDOB.getTimeInMillis();
    }
/**** THIS WAS CREATED AS AN ALTERNATIVE TO USING SIMPLEDATEFORMAT TO PARSE DOB's
 *     
 * @param dob
 * @return
 */
 /*   
    Long newCreateChildDOB(String dob)
    {
    	//get current GMT time
    	TimeZone timezone = TimeZone.getTimeZone("GMT");
		Calendar gmtDOB = Calendar.getInstance(timezone);
		
		//set time to 0:00:00:000 (midnight GMT)
		gmtDOB.set(Calendar.HOUR_OF_DAY, 0);
		gmtDOB.set(Calendar.MINUTE, 0);
		gmtDOB.set(Calendar.SECOND, 0);
		gmtDOB.set(Calendar.MILLISECOND, 0);
		
		//begin parse tree
		String[] dateParts = null;
		if(dob.contains("/"))
			dateParts = dob.split("/");
		else if(dob.contains("-"))
			dateParts = dob.split("-");
		
		if(dateParts != null && dateParts.length == 3)
		{
			int calYear = parseYear(dateParts[2], gmtDOB.get(Calendar.YEAR));
			int calMonth = parseMonth(dateParts[0].trim());
			int calDay = Integer.parseInt(dateParts[1].trim(), calMonth);
			
			if(calYear > -1 && calMonth > -1 && calYear > -1)
			{
				gmtDOB.set(Calendar.YEAR, calYear);
				gmtDOB.set(Calendar.MONTH, calMonth);
				gmtDOB.set(Calendar.DAY_OF_MONTH, calDay);
			}
		}
		
		return gmtDOB.getTimeInMillis();
    }
    
    int parseYear(String zYear, int currentYear)
    {
    	int currentCentury = currentYear/100;
    	int yearInCentury = currentYear % 100;
    	
    	int year = -1;
    	if(zYear.length() == 2)
    	{
    		int tempYear = Integer.parseInt(zYear);
    		if(tempYear < 80)
    			year = currentCentury + yearInCentury;
    		else
    			year = currentCentury -1 + yearInCentury;		
    	}
    	else if(zYear.length() == 4)
    		year = Integer.parseInt(zYear);
    	
    	return year;	
    }
    
    int parseMonth(String zMonth)
    {
    	int month = Integer.parseInt(zMonth.trim());
    	if(month > 0 && month < 13)
    		return month-1;
    	else
    		return -1;	
    }
    
    int parseDay(String zDay, int month)
    {
    	int day = Integer.parseInt(zDay.trim());
    	if(month == 2 && (day==28 || day==29))
    		return day;
    	else if((month == 4 || month == 6 || month == 9 || month == 11) && day > 0 && day < 31)
    		return day;
    	else if(month > 0 && month < 13 && day > 0 && day < 31)
    		return day;	
    	else
    		return -1;
    }
*/	
	String createWishList(Map<String, Object> params)
	{
		StringBuffer buff = new StringBuffer();
		int cn = 0;
		String key = "childfn" + Integer.toString(cn);
		
		//using child first name as the iterator, build a wish list string for each 
		//child in the family
		while(params.containsKey(key))
		{
			buff.append((String) params.get(key));
			buff.append(" ");
			buff.append((String) params.get("childln" + Integer.toString(cn)));
			buff.append(": ");
			
			for(int wn=0; wn<4; wn++)
			{
				buff.append((String) params.get("wish" + Integer.toString(cn) + Integer.toString(wn)));
				buff.append(wn < 3 ? ", " : ";");
			}
			
			cn++;
			key = "childfn" + Integer.toString(cn);	//get next child key
			if(params.containsKey(key))	//if that wasn't the last child, add 2 newlines
				buff.append("\n\n");
			else
				break;
		}
		
		return buff.toString();
	}
	
	boolean isHOHAddressValid(Map<String, Object> params)
	{
		boolean bAddressGood = false;
		String houseNum = null, streetName = null, zipCode = null;
		if(params.containsKey("housenum") && (houseNum = (String) params.get("housenum")) != null
			&& params.containsKey("street") && (streetName = (String) params.get("street")) != null 
			 && params.containsKey("zipcode") && (zipCode = (String) params.get("zipcode")) != null)
		{
			bAddressGood = RegionDB.isAddressValid(houseNum, streetName, zipCode);
		}
		
		return bAddressGood;
	}
	
	boolean isDeliveryAddressValid(Map<String, Object> params)
	{
		boolean bAddressGood = false;
		String houseNum = null, streetName = null, zipCode = null;
		if(params.containsKey("delhousenum") && (houseNum = (String) params.get("delhousenum")) != null
			&& params.containsKey("delstreet") && (streetName = (String) params.get("delstreet")) != null
			 && params.containsKey("delzipcode") && (zipCode = (String) params.get("delzipcode")) != null)
		{
			bAddressGood = RegionDB.isAddressValid(houseNum, streetName, zipCode);
		}
		
		return bAddressGood;
	}
	
	Map<String, String> verifyReferralInformation(Map<String, Object> params)
	{
		Map<String, String> errorMap = new HashMap<String, String>();
		
		Map<String, String> rqrdMap = new HashMap<String, String>();
		rqrdMap.put("hohFN", "Error - HOH first name missing");
		rqrdMap.put("hohLN", "Error - HOH last name missing");
		rqrdMap.put("housenum", "Error - HOH house # missing");
		rqrdMap.put("street", "Error - HOH street name missing");
		rqrdMap.put("city", "Error - HOH city missing");
		rqrdMap.put("homephone", "Error - HOH primary phone # missing");
		rqrdMap.put("delhousenum", "Error - Delivery Address house # missing");
		rqrdMap.put("delstreet", "Error - Delivery Address street name missing");
		rqrdMap.put("delcity", "Error - Delivery Address city missing");
		rqrdMap.put("childfn0", "Error - Missing child 1 firstname");
		rqrdMap.put("childln0", "Error - Missing child 1 lastname");
		rqrdMap.put("childdob0", "Error - Missing child 1  birthdate");
		rqrdMap.put("wishlist0", "Error - Missing child 1 wishlist");
		
		int cn = 1;
		String key = "childfn" + Integer.toString(cn);
		while(params.containsKey(key))
		{
			rqrdMap.put("childfn" + Integer.toString(cn), "Error - Missing child " + cn+1 + " firstname");
			rqrdMap.put("childln" + Integer.toString(cn), "Error - Missing child " + cn+1 + " lastname");
			rqrdMap.put("childdob" + Integer.toString(cn), "Error - Missing child " + cn+1 + " birthdate");
			rqrdMap.put("wishlist" + Integer.toString(cn), "Error - Missing child " + cn+1 + " wishlist");
		}
		
		for (Map.Entry<String, String> entry : rqrdMap.entrySet())
		{
			String value = (String) params.get(entry.getKey());
			if(value == null  || value.isEmpty())
			{
				errorMap.put(entry.getKey(), entry.getValue());
				break;
			}
		}
		
		return errorMap;
	}
	
	String enableReferralButton(String day)
	{
		GlobalVariableDB gDB = null;
		try {
			gDB = GlobalVariableDB.getInstance();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		Calendar today = Calendar.getInstance();
		Date seasonStartDate = gDB.getSeasonStartDate(today.get(Calendar.YEAR));
		Date compareDate = gDB.getDeadline(today.get(Calendar.YEAR), day);
		
		if(gDB != null && seasonStartDate != null && compareDate != null && 
			today.getTime().compareTo(seasonStartDate) >=0 && today.getTime().compareTo(compareDate) < 0 )
			return "enabled";
		else
			return "disabled";
	}
	
	private class FamilyResponseCode
	{
//		private int returnCode;
		private String message;
		
		FamilyResponseCode(int rc, String mssg)
		{
//			this.returnCode = rc;
			this.message = mssg;
		}
		
//		int getReturnCode() { return returnCode; }
		String getMessage() { return message; }
	}
}
