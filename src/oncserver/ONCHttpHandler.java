package oncserver;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import ourneighborschild.ONCServerUser;
import ourneighborschild.UserPermission;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class ONCHttpHandler implements HttpHandler
{
	private static final String FAMILY_TABLE_HTML_FILE = "ScrollFamTable.htm";
	private static final String LOGOUT_HTML_FILE = "logout.htm";
	private static final String JSTEST_HTML_FILE = "Jstest.htm";
	private static final int DEFAULT_YEAR = 2014;
	
//	private final String[] famstatus = {"Unverified", "Info Verified", "Gifts Selected", "Gifts Received", "Gifts Verified", "Packaged"};
//	private final String[] delstatus = {"Empty", "Contacted", "Confirmed", "Assigned", "Attempted", "Returned", "Delivered", "Counselor Pick-Up"};
/*	
	ONCHttpHandler()
	{
		//read the family table into memory once
		try {
			famTableHTML = readFile(String.format("%s/%s",System.getProperty("user.dir"), FAMILY_TABLE_HTML_FILE));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
*/	
	public void handle(HttpExchange t) throws IOException 
    {
    	@SuppressWarnings("unchecked")
		Map<String, Object> params = (Map<String, Object>)t.getAttribute("parameters");
    	String cleintIP = t.getRemoteAddress().toString();
		
		ServerUI serverUI = ServerUI.getInstance();
		serverUI.addLogMessage(String.format("HTTP request %s: %s:%s", 
				t.getRemoteAddress().toString(), t.getRequestMethod(), t.getRequestURI().toASCIIString()));
    	
    	if(t.getRequestURI().toString().equals("/") || t.getRequestURI().toString().contains("/logout"))
    	{
    		String response = null;
    		try {	
    			response = readFile(String.format("%s/%s",System.getProperty("user.dir"), LOGOUT_HTML_FILE));
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    		
    		sendHTMLResponse(t, response);
    	}
    	else if(t.getRequestURI().toString().equals("/") || t.getRequestURI().toString().contains("/jstest"))
    	{
    		String response = null;
    		try {	
    			response = readFile(String.format("%s/%s",System.getProperty("user.dir"), JSTEST_HTML_FILE));
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}

    		sendHTMLResponse(t,  response);
    	}
    	else if(t.getRequestURI().toString().contains("/login"))
    		sendHTMLResponse(t, loginRequest(t.getRequestMethod(), params));
    	else if(t.getRequestURI().toString().contains("/dbStatus"))
    		sendHTMLResponse(t, DBManager.getDatabaseStatusJSONP((String) params.get("callback")));
    	else if(t.getRequestURI().toString().contains("/agents"))
    	{
    		int year = Integer.parseInt((String) params.get("year"));
    		String response = AgentDB.getAgentsJSONP(year, (String) params.get("callback"));
    		sendHTMLResponse(t, response);
    	}
    	else if(t.getRequestURI().toString().contains("/families"))
    	{
    		int year = Integer.parseInt((String) params.get("year"));
    		String response = FamilyDB.getFamiliesJSONP(year, (String) params.get("callback"));
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
  	      	t.sendResponseHeaders(200, file.length());
  	      	OutputStream os = t.getResponseBody();
  	      	os.write(bytearray,0,bytearray.length);
  	      	os.close();
    	}
    	else if(t.getRequestURI().toString().contains("/refresh"))
    	{
    		if(t.getRequestMethod().toLowerCase().equals("post"))
    		{
    			int dbYear = Integer.parseInt((String) params.get("dbYear"));
    	    	int agentID = Integer.parseInt((String) params.get("agtID"));
    	    	System.out.println(dbYear + " " + agentID);
    	    	
    			String response = getFamilyTable(2014, agentID);
    			sendHTMLResponse(t, response);
    		}
    	}
    }
	
	void sendHTMLResponse(HttpExchange t, String response) throws IOException
	{
		t.sendResponseHeaders(200, response.length());
		OutputStream os = t.getResponseBody();
		os.write(response.getBytes());
		os.close();
	}
	
	String loginRequest(String method, Map<String, Object> params)
	{
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
		
//		System.out.println(String.format("username= %s,  pw= %s", params.get("field1"), params.get("field2")));
		
		if(method.toLowerCase().equals("post"))
		{
			String userID = (String) params.get("field1");
	    	String password = (String) params.get("field2");
	    	
	    	//don't want a reference here, want a new object. A user can be logged in more than once.
	    	//However, never can use this object to update a user's info
	    	ONCServerUser serverUser = (ONCServerUser) userDB.find(userID);
	  
	    	if(serverUser == null)	//can't find the user in the data base
	    	{
	    		html += "</i></b></p><p>User name not found</p>";
	    	}
	    	else if(serverUser != null && serverUser.getPermission() == UserPermission.INACTIVE)	//can't find the user in the data base
	    	{
	    		html += "</i></b></p><p>Inactive user account, please contact the executive director</p>";
	    	}
	    	else if(serverUser != null && !serverUser.pwMatch(password))	//found the user but pw is incorrect
	    	{
	    		html += "</i></b></p><p>Incorrect password, access denied</p>";
	    	}
	    	else if(serverUser != null && serverUser.pwMatch(password))	//user found, password matches
	    	{
	    		html += String.format(", %s!</i></b></p>", serverUser.getFirstname());
	    		html += getFamilyTable(DEFAULT_YEAR, -1);
	    	}   	
		}
		else
			html += "<p>Invalid Request Method</p>";
		
		return html +"</body></html>";
	}
	
	String getFamilyTable(int year, int agtID)
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
		ServerUI sUI = ServerUI.getInstance();
		
	    BufferedReader reader = new BufferedReader( new FileReader (file));
	    String         line = null;
	    StringBuilder  stringBuilder = new StringBuilder();
	    String         ls = System.getProperty("line.separator");

	    while( ( line = reader.readLine() ) != null )
	    {
	        stringBuilder.append(line);
	        stringBuilder.append(ls);
	    }
	    
	    reader.close();
	    
	    sUI.addLogMessage(String.format("Read %s, length = %d", file, stringBuilder.toString().length()));
	    return stringBuilder.toString();
	}	
}
