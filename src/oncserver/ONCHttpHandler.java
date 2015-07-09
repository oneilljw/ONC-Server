package oncserver;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.Map;

import ourneighborschild.ONCEncryptor;
import ourneighborschild.ONCServerUser;
import ourneighborschild.ONCUser;
import ourneighborschild.UserDB;
import ourneighborschild.UserPermission;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class ONCHttpHandler implements HttpHandler
{
	public void handle(HttpExchange t) throws IOException 
    {
    	@SuppressWarnings("unchecked")
		Map<String, Object> params = (Map<String, Object>)t.getAttribute("parameters");
		
		ServerUI serverUI = ServerUI.getInstance();
		String req = String.format("HTTP request %s: %s", t.getRequestMethod() , t.getRequestURI().toASCIIString());
		serverUI.addLogMessage(req);
//    	System.out.println(t.getRequestURI().toASCIIString());
    	
    	if(t.getRequestURI().toString().equals("/") || t.getRequestURI().toString().contains("/logout"))
    	{	
    		String response = "<!DOCTYPE html><html>"
    		+"<body>"
    		+"<style>"
      		+"IMG.displayed {display: block; margin-left: auto; margin-right: auto }"
			+"</style>"
      		+"<img src= \"oncsplash.gif\" alt=\"HTML5 Icon\" class= \"displayed\">"
    		+"<p><b><i>Welcome to Our Neighbors Child</i></b></p>"
    		+"<p>Please Login</p>"
    		+"<form action=\"login\" method=\"post\">"
    		+"User Name: "
    		+"<input type=\"text\" name=\"field1\">"
    		+"<br>"
    		+"Password:   "
    		+"<input type=\"password\" name=\"field2\">"
    		+"<br><br>"
    		+"<input type=\"submit\" value=\"Login\">"
    		+"</form>"
    		+"</body>"
    		+"</html>";

    		t.sendResponseHeaders(200, response.length());
    		OutputStream os = t.getResponseBody();
    		os.write(response.getBytes());
    		os.close();
    	}
    	else if(t.getRequestURI().toString().contains("/login"))
    	{
    		String html = loginRequest(t.getRequestMethod(), params);
    		
    		String response = "<!DOCTYPE html><html>"
    		+"<body>"
    		+"<p><b><i>Welcome to Our Neighbors Child</i></b></p>"
    		+html
    		+"</body>"
    		+"</html>";

    		t.sendResponseHeaders(200, response.length());
    		OutputStream os = t.getResponseBody();
    		os.write(response.getBytes());
    		os.close();
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
    	else if(t.getRequestURI().toString().contains("/test"))
    	{
    		 String response = "<html>"
    		      		+"<body style=background-image:url('http://www.ourneighborschild.org/uploads/7/7/7/5/7775392/9738099.jpg?163')>"
    		      		+"<form><input type=\"button\" id=\"btn01\" value=\"OK\"></form>"
    		      		+"<p>Click the \"Disable\" button to disable the \"OK\" button:</p>"
    		      		+"<button onclick=\"disableElement()\">Disable</button>"
    		      		+"<script>"
    		      		+"function disableElement() {"
    		      		+"document.getElementById(\"btn01\").disabled = true;}"
    		      		+"</script>"
    		      		+"</body>"
    		      		+ "</html>";
    		  t.sendResponseHeaders(200, response.length());
    		  OutputStream os = t.getResponseBody();
    		  os.write(response.getBytes());
    		  os.close();
    	}
    }
	
	String loginRequest(String method, Map params)
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
		
		String value = "Invalid Request Method";
		
		if(method.equals("POST"))
		{
			String userID = (String) params.get("field1");
	    	String password = (String) params.get("field2");
	    	
	    	//don't want a reference here, want a new object. A user can be logged in more than once.
	    	//However, never can use this object to update a user's info
	    	ONCServerUser serverUser = (ONCServerUser) userDB.find(userID);
	  
	    	if(serverUser == null)	//can't find the user in the data base
	    	{
	    		value = "<p>User name not found</p>";
	    	}
	    	else if(serverUser != null && serverUser.getPermission() == UserPermission.INACTIVE)	//can't find the user in the data base
	    	{
	    		value = "<p>Inactive user account, please contact the executive director</p>";
	    	}
	    	else if(serverUser != null && !serverUser.pwMatch(password))	//found the user but pw is incorrect
	    	{
	    		value = "<p>Incorrect password, access denied</p>";
	    	}
	    	else if(serverUser != null && serverUser.pwMatch(password))	//user found, password matches
	    	{
	    		value = "<p>You sucessfully logged in!!</p>"
	    				+"<form action=\"logout\" method=\"get\">"
	    				+"<input type=\"submit\" value=\"Log Out\">"
	    				+"</form>";
	    	}   	
		}
		
		return value;
	}
}
