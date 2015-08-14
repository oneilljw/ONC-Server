package oncserver;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import ourneighborschild.UserPermission;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.google.gson.Gson;

public class ONCHttpHandler implements HttpHandler
{
	private static final String REFERRAL_STATUS_HTML = "ScrollFamTable.htm";
	private static final String LOGOUT_HTML = "logout.htm";
	private static final String EXISTING_FAMILY_HTML = "ExistingFamilyReferral.htm";
	private static final String CHANGE_PASSWORD_HTML = "Change.htm";
	private static final String NEW_FAMILY_HTML = "NewFamilyReferral.htm";
	private static final String INPUT_NORMAL_BACKGROUND = "#FFFFFF";
	private static final String INPUT_ERROR_BACKGROUND = "#FFC0CB";
	private static final int DEFAULT_YEAR = 2014;
	
	private static final int HTTP_OK = 200;
	
	public void handle(HttpExchange t) throws IOException 
    {
    	@SuppressWarnings("unchecked")
		Map<String, Object> params = (Map<String, Object>)t.getAttribute("parameters");
    	
    	String requestURI = t.getRequestURI().toASCIIString();
    	
    	String mssg = String.format("HTTP request %s: %s:%s", t.getRemoteAddress().toString(), t.getRequestMethod(), requestURI);
		ServerUI serverUI = ServerUI.getInstance();
		serverUI.addLogMessage(mssg);
    	
    	if(requestURI.equals("/"))
    	{
    		String response = null;
    		try {	
    			response = readFile(String.format("%s/%s",System.getProperty("user.dir"), LOGOUT_HTML));
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    		
    		response = response.replace("WELCOME_MESSAGE", "Welcome to Our Neighbor's Child, Please Login:");
    		
    		sendHTMLResponse(t, new HtmlResponse(response, HTTPCode.Ok));
    	}
    	else if(requestURI.contains("/logout"))
    	{
    		String sessionID = (String) params.get("token");
    		ClientManager clientMgr = ClientManager.getInstance();
    		
    		if(!clientMgr.logoutWebClient(sessionID))
    			clientMgr.addLogMessage(String.format("ONCHttpHandler.handle/logut: logout failure, client %s not found", sessionID));
    		
    		String response = null;
    		try {	
    			response = readFile(String.format("%s/%s",System.getProperty("user.dir"), LOGOUT_HTML));
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    		
    		response = response.replace("WELCOME_MESSAGE", "Welcome to Our Neighbor's Child, Please Login:");
    		sendHTMLResponse(t, new HtmlResponse(response, HTTPCode.Ok));
    	}
    	else if(requestURI.contains("/login"))
    		sendHTMLResponse(t, loginRequest(t.getRequestMethod(), params, t));
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
    	else if(requestURI.contains("/children"))
    	{
    		int year = Integer.parseInt((String) params.get("year"));
    		int famID = Integer.parseInt((String) params.get("famid"));
    		
    		HtmlResponse response = ServerChildDB.getChildrenInFamilyJSONP(year, famID, (String) params.get("callback"));
    		sendHTMLResponse(t, response);
    	}
    	else if(requestURI.contains("/adults"))
    	{
    		int year = Integer.parseInt((String) params.get("year"));
    		int famID = Integer.parseInt((String) params.get("famid"));
    		
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
    				response = readFile(String.format("%s/%s",System.getProperty("user.dir"), NEW_FAMILY_HTML));
    			} catch (IOException e) {
    				System.out.println("Couldn't open/find " + NEW_FAMILY_HTML);
    				e.printStackTrace();
    			}
    			
    			//remove the place holders
    			response = response.replace("REPLACE_TOKEN", sessionID);
    			response = response.replace("value=\"HOHFN\"","");
    			response = response.replace("value=\"HOHLN\"", "");
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
    				response = readFile(String.format("%s/%s",System.getProperty("user.dir"), EXISTING_FAMILY_HTML));
    			} catch (IOException e) {
    				// TODO Auto-generated catch block
    				e.printStackTrace();
    			}
    			
    			//get the family
    			int year = Integer.parseInt((String) params.get("year"));
    			int famID = Integer.parseInt((String) params.get("famID"));
    		
    			FamilyDB famDB = FamilyDB.getInstance();
    			ONCFamily fam = famDB.getFamily(year, famID);
    		
    			//replace the place holders
    			response = response.replace("REPLACE_TOKEN", sessionID);
    			response = response.replace("YEAR",(String) params.get("year"));
    			response = response.replace("FAMID",(String) params.get("famID"));
    			response = response.replace("HOHFN",fam.getHOHFirstName());
    			response = response.replace("HOHLN", fam.getHOHLastName());
    			response = response.replace("ADDRESS_COLOR", INPUT_NORMAL_BACKGROUND);
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
    			Set<String> keyset = params.keySet();
    			for(String key:keyset)
    				System.out.println(String.format("/referfamily key=%s, value=%s", key, params.get(key)));
    		
    			FamilyResponseCode frc = processFamilyReferral(wc, params);
    			
    			//submission processed, send the family table page back to the user
    			String userFN = wc.getWebUser().getFirstname();
    			response = getFamilyTableHTML(DEFAULT_YEAR, -1);
    			response= response.replace("USER_NAME", userFN);
    			response= response.replace("USER_MESSAGE", frc.getMessage());
    	    	response = response.replace("REPLACE_TOKEN", wc.getSessionID().toString());
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
    				String userFN = wc.getWebUser().getFirstname();
    				response = getFamilyTableHTML(DEFAULT_YEAR, -1);
    				response = response.replace("USER_NAME", userFN);
    				response = response.replace("USER_MESSAGE", "Your password change was successful!");
    	    		response = response.replace("REPLACE_TOKEN", wc.getSessionID().toString());
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
	    			html = html.replace("USERFN", serverUser.getFirstname());
	    			html = html.replace("REPLACE_TOKEN", wc.getSessionID().toString());
	    			
	    			response = new HtmlResponse(html, HTTPCode.Ok);
	    		}
	    		else //send the referral status page
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
	    				SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM d, yyyy h:mm a z");
	    				sdf.setTimeZone(TimeZone.getDefault());
	    				userMssg = "You last visited " + sdf.format(lastLogin);
	    			}
	    		
	    			html = getFamilyTableHTML(DEFAULT_YEAR, -1);
	    			html = html.replace("USER_NAME", serverUser.getFirstname());
	    			html = html.replace("USER_MESSAGE", userMssg);
	    			html = html.replace("REPLACE_TOKEN", wc.getSessionID().toString());
	    				
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
	
	String getFamilyTableHTML(int year, int agtID)
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
	
	String invalidTokenReceived()
	{
		return "<!DOCTYPE html><html><body><p>Invalid Token Received</p></body></html>";
		
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
		ONCMeal mealReq = null;
		
		String[] mealKeys = {"mealtype", "dietres"};
		Map<String, String> mealMap = createMap(params, mealKeys);

		ONCMeal addedMeal = null;
		if(!mealMap.get(mealKeys[0]).equals("No Assistance Rqrd"))
		{
			mealReq = new ONCMeal(-1, -1, MealType.valueOf(mealMap.get(mealKeys[0])),
							mealMap.get(mealKeys[1]), -1, agt.getAgentName(), new Date(), 3,
							"Family Referred", agt.getAgentName());
			
			addedMeal = mealDB.add(year, mealReq);
		}

		//create the family
		String[] familyKeys = {"language", "hohFN", "hohLN", "housenum", "street", "unit", "city",
				   "zipcode", "homephone", "cellphone", "email","delhousenum", "delstreet","detail",
				   "delunit", "delcity", "delzipcode"};
		
		Map<String, String> familyMap = createMap(params, familyKeys);
		
		ONCFamily fam = new ONCFamily(-1, wc.getWebUser().getLNFI(), "NNA", "O000000", "B-DI", 
					familyMap.get("language").equals("English") ? "Yes" : "No", familyMap.get("language"),
					familyMap.get("hohFN"), familyMap.get("hohLN"), familyMap.get("housenum"),
					familyMap.get("street"), familyMap.get("unit"), familyMap.get("city"),
					familyMap.get("zipcode"), familyMap.get("delhousenum"), familyMap.get("delstreet"),
					familyMap.get("delunit"), familyMap.get("delcity"), familyMap.get("delzipcode"),
					familyMap.get("homephone"), familyMap.get("cellphone"),
					familyMap.get("email"), familyMap.get("detail"), createWishList(params),
					agt.getID(), addedMeal != null ? addedMeal.getID() : -1,
					addedMeal != null ? MealStatus.Requested : MealStatus.None);
			
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
			
				ONCChild child = new ONCChild(-1, addedFamily.getID(), childfn, childln, childGender, 
													createChildDOB(childDoB), childSchool, year);
				
				addedChildList.add(childDB.add(year,child));
				cn++;
				key = "childfn" + Integer.toString(cn);	//get next child key
			}
			
			//create the other adults in the family
			String adultName, adultGender;
			
			int an = 0;
			key = "adultname" + Integer.toString(an);
			
			//using adultnameX as the iterator, create a db entry for each
			//adult in the family
			while(params.containsKey(key))
			{
				adultName = (String) params.get(key);
				adultGender = (String) params.get("adultgender" + Integer.toString(an));
					
				ONCAdult adult = new ONCAdult(-1, addedFamily.getID(), adultName, adultGender); 
				
				addedAdultList.add(adultDB.add(year, adult));	
				an++;
				key = "adultname" + Integer.toString(an);	//get next child key
			}
			
		}
		
		//successfully process meals, family, children and adults. Notify the desktop
		//clients so they refresh
		ClientManager clientMgr = ClientManager.getInstance();
		Gson gson = new Gson();
		String mssg;
		
		if(addedFamily != null)
		{
			if(addedMeal != null)
			{
				mssg = "ADDED_MEAL" + gson.toJson(addedMeal, ONCMeal.class);
				clientMgr.notifyAllInYearClients(year, mssg);
			}
			
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
		}
		
		return new FamilyResponseCode(0, addedFamily.getHOHLastName() + " Family Referral Accepted");
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
    	
    	//First, parse the input string based on format to create an Calendar variable for DOB
    	//If it can't be determined, set DOB = today. 
    	if(dob.length() == 10 && dob.contains("-"))	//format one
    	{			
    		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
    		try
    		{
				gmtDOB.setTime(sdf.parse(dob));
			}
    		catch (ParseException e)
    		{
    			String errMssg = "Couldn't determine DOB from input: " + dob;
			}
    	}
    	else if(dob.contains("/"))	//format two
    	{
    		SimpleDateFormat oncdf = new SimpleDateFormat("M/d/yy");
    		oncdf.setTimeZone(TimeZone.getTimeZone("GMT"));
    		try
    		{
				gmtDOB.setTime(oncdf.parse(dob));
			}
    		catch (ParseException e)
    		{
    			String errMssg = "Couldn't determine DOB from input: " + dob;
			}
    	}
    	
    	//then convert the Calendar to a Date and return it
    	return gmtDOB.getTimeInMillis();
    }
	
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
				buff.append(wn < 2 ? ", " : ";");
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
		String houseNum = null, streetName = null;
		if(params.containsKey("housenum") && (houseNum = (String) params.get("housenum")) != null
			&& params.containsKey("street") && (streetName = (String) params.get("street")) != null )
		{
			bAddressGood = RegionDB.isAddressValid(houseNum, streetName);
		}
		
		return bAddressGood;
	}
	
	boolean isDeliveryAddressValid(Map<String, Object> params)
	{
		boolean bAddressGood = false;
		String houseNum = null, streetName = null;
		if(params.containsKey("delhousenum") && (houseNum = (String) params.get("delhousenum")) != null
			&& params.containsKey("delstreet") && (streetName = (String) params.get("delstreet")) != null )
		{
			bAddressGood = RegionDB.isAddressValid(houseNum, streetName);
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
				System.out.println(entry.getValue());
				break;
			}
		}
		
		return errorMap;
	}
	
	private class FamilyResponseCode
	{
		private int returnCode;
		private String message;
		
		FamilyResponseCode(int rc, String mssg)
		{
			this.returnCode = rc;
			this.message = mssg;
		}
		
		int getReturnCode() { return returnCode; }
		String getMessage() { return message; }
	}
}
