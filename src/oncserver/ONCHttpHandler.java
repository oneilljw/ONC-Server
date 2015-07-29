package oncserver;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.Map;

import ourneighborschild.ONCServerUser;
import ourneighborschild.ONCUser;
import ourneighborschild.UserPermission;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import com.google.gson.Gson;

public class ONCHttpHandler implements HttpHandler
{
	private static final String FAMILY_TABLE_HTML_FILE = "ScrollFamTable.htm";
	private static final String LOGOUT_HTML_FILE = "logout.htm";
	private static final int DEFAULT_YEAR = 2014;
	
	private static final int HTTP_OK = 200;
	
	public void handle(HttpExchange t) throws IOException 
    {
    	@SuppressWarnings("unchecked")
		Map<String, Object> params = (Map<String, Object>)t.getAttribute("parameters");
    	
		
		ServerUI serverUI = ServerUI.getInstance();
		serverUI.addLogMessage(String.format("HTTP request %s: %s:%s", 
				t.getRemoteAddress().toString(), t.getRequestMethod(), t.getRequestURI().toASCIIString()));

    	
    	if(t.getRequestURI().toString().equals("/"))
    	{
    		String response = null;
    		try {	
    			response = readFile(String.format("%s/%s",System.getProperty("user.dir"), LOGOUT_HTML_FILE));
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    		
    		sendHTMLResponse(t, new HtmlResponse(response, HTTPCode.Ok));
    	}
    	else if(t.getRequestURI().toString().contains("/logout"))
    	{
    		String sessionID = (String) params.get("token");
    		ClientManager clientMgr = ClientManager.getInstance();
    		
    		if(!clientMgr.logoutWebClient(sessionID))
    			System.out.println("ONCHttpHandler.handle: logout failure, client not found");
    		
    		String response = null;
    		try {	
    			response = readFile(String.format("%s/%s",System.getProperty("user.dir"), LOGOUT_HTML_FILE));
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    		
    		sendHTMLResponse(t, new HtmlResponse(response, HTTPCode.Ok));
    	}
    	else if(t.getRequestURI().toString().contains("/login"))
    		sendHTMLResponse(t, loginRequest(t.getRequestMethod(), params, t));
    	else if(t.getRequestURI().toString().contains("/dbStatus"))
    	{
//    		System.out.println("ONCHttpHandler: Token = " + (String) params.get("token"));
    		sendHTMLResponse(t, DBManager.getDatabaseStatusJSONP((String) params.get("callback")));
    	}
    	else if(t.getRequestURI().toString().contains("/agents"))
    	{
    		int year = Integer.parseInt((String) params.get("year"));
    		HtmlResponse response = AgentDB.getAgentsJSONP(year, (String) params.get("callback"));
    		sendHTMLResponse(t, response);
    	}
    	else if(t.getRequestURI().toString().contains("/families"))
    	{
    		int year = Integer.parseInt((String) params.get("year"));
    		HtmlResponse response = FamilyDB.getFamiliesJSONP(year, (String) params.get("callback"));
    		sendHTMLResponse(t, response);
    	}
    	else if(t.getRequestURI().toString().contains("/oncsplash"))
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
    	else if(t.getRequestURI().toString().contains("/newfamily"))
    	{
    		String html = "<!DOCTYPE html><html>"
    	    		+"<body>"
    	    		+"<p><b><i>New family form will go here</i></b></p>"
    				+"</body>"
    	    		+"</html>";
    		sendHTMLResponse(t, new HtmlResponse(html, HTTPCode.Ok));
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
	    		serverUser.setLastLogin(new Date());
	    		userDB.save(DEFAULT_YEAR);	//year will equal -1 at this point, but ignored. Only one user.csv
	    		
	    		ONCUser webUser = serverUser.getUserFromServerUser();
	    		
	    		ClientManager clientMgr = ClientManager.getInstance();
	    		WebClient wc = clientMgr.addWebClient(t, webUser);
	    		
	    		Gson gson = new Gson();
	    		String loginJson = gson.toJson(webUser, ONCUser.class);
	    		
	    		String mssg = "UPDATED_USER" + loginJson;
	    		clientMgr.notifyAllClients(mssg);
	    		
	    		String webPage = getFamilyTableHTML(DEFAULT_YEAR, -1).replace("USER_NAME_HERE", serverUser.getFirstname());
	    		html = webPage.replace("REPLACE_TOKEN", wc.getSessionID().toString());
	    				
	    		response = new HtmlResponse(html, HTTPCode.Ok);
	    	}   	
		}
		else
		{
			html += "<p>Invalid Request Method</p></body></html>";
			response = new HtmlResponse(html, HTTPCode.Forbidden);
		}
		
		return response;
	}
	
	String getFamilyTableHTML(int year, int agtID)
	{
		//read the family table html
		String famTableHTML = null;
		try
		{
			famTableHTML = readFile(String.format("%s/%s",System.getProperty("user.dir"), FAMILY_TABLE_HTML_FILE));
			return famTableHTML;
		}
		catch (IOException e) 
		{
			return "<p>Family Table Unavailable</p>";
		}
	}

	private String readFile( String file ) throws IOException
	{
	    BufferedReader reader = new BufferedReader( new FileReader (file));
	    String         line = null;
	    StringBuilder  stringBuilder = new StringBuilder();

	    while( ( line = reader.readLine() ) != null )
	    {
	        stringBuilder.append(line);
	        stringBuilder.append(System.getProperty("line.separator"));
	    }
	    
	    reader.close();
	    
//	    ServerUI sUI = ServerUI.getInstance();
//	    sUI.addLogMessage(String.format("Read %s, length = %d", file, stringBuilder.toString().length()));
	    return stringBuilder.toString();
	}	
}
