package oncserver;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import ourneighborschild.ONCServerUser;
import ourneighborschild.ONCUser;
import ourneighborschild.UserAccess;
import ourneighborschild.UserStatus;

import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/****
 * Handles welcome, login, logout and metrics contexts
 * @author johnoneil
 *
 */
public class LoginHandler extends ONCHandlerServices implements HttpHandler 
{	
	private static final String CHANGE_PASSWORD_HTML = "Change.htm";
	
	private Map<String,String> webpageMap;
	
	public LoginHandler()
	{
		webpageMap = new HashMap<String, String>();
		try 
		{
			webpageMap.put("online", readFile(String.format("%s/%s",System.getProperty("user.dir"), LOGOUT_HTML)));
			webpageMap.put("welcome", readFile(String.format("%s/%s",System.getProperty("user.dir"), MAINTENANCE_HTML)));
			webpageMap.put("changepw", readFile(String.format("%s/%s",System.getProperty("user.dir"), CHANGE_PASSWORD_HTML)));
		} 
		catch (IOException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void handle(HttpExchange t) throws IOException 
	{
		@SuppressWarnings("unchecked")
		Map<String, Object> params = (Map<String, Object>)t.getAttribute("parameters");
    	
//		Set<String> keyset = params.keySet();
//		for(String key:keyset)
//			System.out.println(String.format("uri=%s, key=%s, value=%s", t.getRequestURI().toASCIIString(), key, params.get(key)));
    	
    	String requestURI = t.getRequestURI().toASCIIString();
    	
    	String mssg = String.format("HTTP request %s: %s:%s", t.getRemoteAddress().toString(), t.getRequestMethod(), requestURI);
		ServerUI.getInstance().addLogMessage(mssg);
    	
    	if(requestURI.equals("/welcome"))
    	{
    		String response = null;
    		if(ONCWebServer.isWebsiteOnline())
    		{
    			response = webpageMap.get("online");
    			response = response.replace("WELCOME_MESSAGE", "Welcome to Our Neighbor's Child, Please Login:");
    		}
    		else
    		{
    			response = webpageMap.get("offline");
    			response = response.replace("TIME_BACK_UP", ONCWebServer.getWebsiteTimeBackOnline());
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
    	else if(requestURI.contains("/login"))
    	{
    		sendHTMLResponse(t, loginRequest(t.getRequestMethod(), params, t));
    	}
    	else if(requestURI.contains("/onchomepage"))
    	{
    		Headers header = t.getResponseHeaders();
    		ArrayList<String> headerList = new ArrayList<String>();
    		headerList.add("http://www.ourneighborschild.org");
    		header.put("Location", headerList);
  	
    		sendHTMLResponse(t, new HtmlResponse("", HTTPCode.Redirect));
    	}
    	else if(requestURI.contains("/metrics"))
    	{
    		ClientManager clientMgr = ClientManager.getInstance();
    		WebClient wc;
    		HtmlResponse htmlResponse;
    		
    		if((wc=clientMgr.findClient((String) params.get("token"))) != null)	
    		{
    			wc.updateTimestamp();
    			int year = Integer.parseInt((String) params.get("year"));
    			String maptype = (String) params.get("maptype");
    			htmlResponse = ServerFamilyDB.getFamilyMetricsJSONP(year, maptype, (String)params.get("callback"));
    		}
    		else
    		{
    			String response = invalidTokenReceivedToJsonRequest("Error", (String) params.get("callback"));
    			htmlResponse = new HtmlResponse(response, HTTPCode.Ok);
    		}
    		
    		sendHTMLResponse(t, htmlResponse);
    	}
    	else if(requestURI.equals("/timeout"))
    	{
    		String response = null;
    		try {
    			if(ONCWebServer.isWebsiteOnline())
    			{
    				response = readFile(String.format("%s/%s",System.getProperty("user.dir"), LOGOUT_HTML));
    				response = response.replace("WELCOME_MESSAGE", "Your last session expired, please login again:");
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
	    	else if(serverUser != null && serverUser.getStatus() == UserStatus.Inactive)	//can't find the user in the data base
	    	{
	    		html += "</i></b></p><p>Inactive user account, please contact ONC at volunteer@ourneighborschild.org</p></body></html>";
	    		response = new HtmlResponse(html, HTTPCode.Forbidden);
	    	}
	    	else if(serverUser != null && !(serverUser.getAccess().equals(UserAccess.Website) ||
	    			serverUser.getAccess().equals(UserAccess.AppAndWebsite)))	//can't find the user in the data base
	    	{
	    		html += "</i></b></p><p>User account not authorized for website access, please contact ONC at volunteer@ourneighborschild.org</p></body></html>";
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
	    		Calendar lastLogin = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
	    		lastLogin.setTimeInMillis(serverUser.getLastLogin());
	    		
	    		serverUser.incrementSessions();
	    		
	    		Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
	    		serverUser.setLastLogin(calendar.getTimeInMillis());
	    		userDB.requestSave();
	    		
	    		ONCUser webUser = serverUser.getUserFromServerUser();
	    		ClientManager clientMgr = ClientManager.getInstance();
    			WebClient wc = clientMgr.addWebClient(t, serverUser);
    			
	    		//has the current password expired? If so, send change password page
	    		if(serverUser.changePasswordRqrd())
	    		{
	    			html = webpageMap.get("changepw");
	    			
	    			Gson gson = new Gson();
	    			String loginJson = gson.toJson(webUser, ONCUser.class);
	    		
	    			String mssg = "UPDATED_USER" + loginJson;
	    			clientMgr.notifyAllClients(mssg);
	    			
	    			//replace the HTML place holders
	    			if(serverUser.getFirstName().equals(""))
	    				html = html.replace("USERFN", serverUser.getLastName());
	    			else
	    				html = html.replace("USERFN", serverUser.getFirstName());
	    			
	    			html = html.replace("REPLACE_TOKEN", wc.getSessionID().toString());
	    			html = html.replace("THANKSGIVING_CUTOFF", enableReferralButton("Thanksgiving"));
	    			html = html.replace("DECEMBER_CUTOFF", enableReferralButton("December"));
	    			html = html.replace("EDIT_CUTOFF", enableReferralButton("Edit"));
	    			
	    			response = new HtmlResponse(html, HTTPCode.Ok);
	    		}
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
	    				SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM d, yyyy");
	    				sdf.setTimeZone(TimeZone.getDefault());
	    				userMssg = "You last visited " + sdf.format(lastLogin.getTime());
	    			}
	    			
	    			html = getHomePageHTML(wc, userMssg, "NNA");
	    			
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
}
