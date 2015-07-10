package oncserver;

import java.awt.Color;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;

import ourneighborschild.Agent;
import ourneighborschild.ONCEncryptor;
import ourneighborschild.ONCFamily;
import ourneighborschild.ONCServerUser;
import ourneighborschild.ONCUser;
import ourneighborschild.UserDB;
import ourneighborschild.UserPermission;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class ONCHttpHandler implements HttpHandler
{
	private String[] famstatus = {"Unverified", "Info Verified", "Gifts Selected", "Gifts Received", "Gifts Verified", "Packaged"};
	private String[] delstatus = {"Empty", "Contacted", "Confirmed", "Assigned", "Attempted", "Returned", "Delivered", "Counselor Pick-Up"};
	
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
    		
    		String altResponse = "<!DOCTYPE html>"
    		+"<html lang=\"en\">"
    		+"<head>"
    		    +"<meta charset=\"UTF-8\">"
    		    +"<title></title>"
    		    +"<style>"
    		        +"body {"
    		            +"height: 0;"
    		            +"padding: 0;"
    		            +"padding-bottom: 75%;"
    		            +"background-image: url(oncsplash.gif);"
    		            +"background-position: center center;"
    		            +"background-size: 100%;"
    		            +"background-repeat: no-repeat;"
    		        +"}"

    		        +"label"
    		        +"{"
    		            +"width: 5em;"
    		            +"float: left;"
    		            +"text-align: right;"
    		            +"margin-right: 0.5em;"
    		            +"display: block;"
    		            +"color: black;"
    		        +"}"
    		        +".submit input"
    		        +"{"
    		            +"margin-left: 11em;"
    		        +"}"
    		        +"input"
    		        +"{"
    		            +"color: black;"
    		            +"background: #FFFFE4;"
    		            +"border: 1px solid #781351"
    		        +"}"
    		        +".submit input"
    		        +"{"
    		            +"color: #000;"
    		            +"background: #ffa20f;"
    		            +"border: 2px outset #d7b9c9"
//    		            +"margin-left: 9em;"
    		        +"}"
    		        +"fieldset"
    		        +"{"
    		            +"position: absolute;"
    		            +"top:58%;"
    		            +"left:31%;"
    		            +"border: 4px solid #781351;"
    		            +"color: #00F;"
    		            +"background: #F2F2FA;"
    		            +"width: 25em"
    		        +"}"
    		        +"legend"
    		        +"{"
    		            +"color: black;"
    		            +"background: #ffa20c;"
    		            +"border: 1px solid #781351;"
    		            +"padding: 2px 6px"
    		        +"}"
    		    +"</style>"
    		+"</head>"
    		+"<body>"
    		    +"<div class = \"login\">"
    		        +"<form action=\"login\" method=\"post\">"
    		            +"<fieldset>"
    		                +"<legend>User Login</legend>"
    		                +"<p><label for=\"username\">User Name:</label> <input type=\"text\" id=\"username\" name=\"field1\" autofocus/></p>"
    		                +"<p><label for=\"password\">Password:</label> <input type=\"password\" id=\"password\" name=\"field2\"/><br /></p>"
    		                +"<p class=\"submit\"><input type=\"submit\" value=\"Login\" /></p>"
    		            +"</fieldset>"
    		        +"</form>"
    		    +"</div>"
    		+"</body>"
    		+"</html>";

    		t.sendResponseHeaders(200, altResponse.length());
    		OutputStream os = t.getResponseBody();
    		os.write(altResponse.getBytes());
    		os.close();
    	}
    	else if(t.getRequestURI().toString().contains("/login"))
    	{
    		String html = loginRequest(t.getRequestMethod(), params);
    		
    		String response = "<!DOCTYPE html><html>"
    		+"<body>"
    		+"<p><b><i>Welcome to Our Neighbors Child</i></b></p>"
    		+ html
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
    	else if(t.getRequestURI().toString().contains("/familystatus"))
    	{
    		 String response = getFamilyTable(2014);
    		 t.sendResponseHeaders(200, response.length());
    		 OutputStream os = t.getResponseBody();
    		 os.write(response.getBytes());
    		 os.close();
    	}
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
		
		String value = "Invalid Request Method";
		
//		System.out.println(String.format("username= %s,  pw= %s", params.get("field1"), params.get("field2")));
		
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
	    				+"<p><b><i>2014 Family Table</i></b></p>"
	    				+ getFamilyTable(2014)
	    				+"<br>"
	    				+"<form action=\"logout\" method=\"get\">"
	    				+"<input type=\"submit\" value=\"Log Out\">"
	    				+"</form>";
	    	}   	
		}
		
		return value;
	}
	
	String getFamilyTable(int year)
	{
		AgentDB agentDB = null;
		FamilyDB famDB = null;
		try {
			agentDB = AgentDB.getInstance();
			famDB = FamilyDB.getInstance();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		List<ONCFamily> famList = famDB.getList(year);
		
		String tableTop = "<!DOCTYPE html><html>"
		+"<head>"
		+"<style>"
		+"table, th, td {"
		    +"border: 1px solid black;"
		    +"border-collapse: collapse;"
		+"}"
		+"th, td {"
		    +"padding: 5px;"
		+"}"
		+"</style>"
		+"</head>"
		+"<body>"
		+"<table style=\"width:80%\">"
		  +"<tr>"
		  	+"<th style=\"background-color: #99CCFF\">ONC #</th>"
		    +"<th style=\"background-color: #99CCFF\">First Name</th>"
		    +"<th style=\"background-color: #99CCFF\">Last Name</th>" 
		    +"<th style=\"background-color: #99CCFF\">DNS Code</th>" 
		    +"<th style=\"background-color: #99CCFF\">Gift Status</th>"
		    +"<th style=\"background-color: #99CCFF\">Delivery Status</th>"
		    +"<th style=\"background-color: #99CCFF\">Meal Status</th>"
		    +"<th style=\"background-color: #99CCFF\">Referred By</th>"
		  +"</tr>";
		  
		StringBuffer buff = new StringBuffer();
		for(int i=0; i<15; i++)
		{
			int agentID = famList.get(i).getAgentID();
			Agent agent = agentDB.getAgent(year, agentID);
			buff.append(getTableRow(famList.get(i), agent));
		}
		
		String tableBottom =  "</table>";
		
		return tableTop + buff.toString() + tableBottom;
	}
	
	String getTableRow(ONCFamily fam, Agent agent)
	{
		String row = "<tr>"
				+"<td>"+ fam.getONCNum()  +"</td>"
			    +"<td>"+ fam.getHOHFirstName()  +"</td>"
			    +"<td>"+ fam.getHOHLastName()  +"</td>"
			    +"<td>"+ fam.getDNSCode()  +"</td>"
			    +"<td>"+ famstatus[fam.getFamilyStatus()]  +"</td>"
			    +"<td>"+ delstatus[fam.getDeliveryStatus()]  +"</td>"
			    +"<td>"+ fam.getMealStatus().toString()  +"</td>"
			    +"<td>"+ agent.getAgentName() +"</td>"
			    +"</tr>";
		
		return row;
	}
}
