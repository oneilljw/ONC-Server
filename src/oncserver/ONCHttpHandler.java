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
import java.util.Date;
import java.util.Map;
import java.util.Set;

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
	private static final String FAMILY_TABLE_HTML_FILE = "ScrollFamTable.htm";
	private static final String LOGOUT_HTML_FILE = "logout.htm";
	private static final String EXISTING_FAMILY_FILE = "ExistingFamilyReferral.htm";
	private static final String NEW_FAMILY_FILE = "NewFamilyReferral.htm";
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
    			response = readFile(String.format("%s/%s",System.getProperty("user.dir"), LOGOUT_HTML_FILE));
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    		
    		sendHTMLResponse(t, new HtmlResponse(response, HTTPCode.Ok));
    	}
    	else if(requestURI.contains("/logout"))
    	{
    		String sessionID = (String) params.get("token");
    		ClientManager clientMgr = ClientManager.getInstance();
    		
    		if(!clientMgr.logoutWebClient(sessionID))
    			System.out.println("ONCHttpHandler.handle/logut: logout failure, client not found");
    		
    		String response = null;
    		try {	
    			response = readFile(String.format("%s/%s",System.getProperty("user.dir"), LOGOUT_HTML_FILE));
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    		
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
    	else if(requestURI.contains("/newfamily"))
    	{
    		String sessionID = (String) params.get("token");
    		ClientManager clientMgr = ClientManager.getInstance();
    		String response = null;
    		
    		System.out.println(sessionID);
    		
    		if(clientMgr.findClient(sessionID) != null)	
    		{
    			try {	
    				response = readFile(String.format("%s/%s",System.getProperty("user.dir"), NEW_FAMILY_FILE));
    			} catch (IOException e) {
    				System.out.println("Couldn't open/find " + NEW_FAMILY_FILE);
    				e.printStackTrace();
    			}
    			
    			System.out.println(response.length());
    			
    			//remove the place holders
    			response = response.replace("REPLACE_TOKEN", sessionID);
//    			response = response.replace("YEAR",(String) params.get("year"));
    			response = response.replace("value=\"HOHFIRSTNAME\"","");
    			response = response.replace("value=\"HOHLASTNAME\"", "");
    		}
    		else
    			response = invalidTokenReceived();
    		
    		System.out.println(response);
    		sendHTMLResponse(t, new HtmlResponse(response, HTTPCode.Ok));
    	}
    	else if(requestURI.contains("/referral"))
    	{
    		String sessionID = (String) params.get("token");
    		ClientManager clientMgr = ClientManager.getInstance();
    		String response = null;
    		
    		if(clientMgr.findClient(sessionID) != null)
    		{
    			try {	
    				response = readFile(String.format("%s/%s",System.getProperty("user.dir"), EXISTING_FAMILY_FILE));
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
    			response = response.replace("HOHFIRSTNAME",fam.getHOHFirstName());
    			response = response.replace("HOHLASTNAME", fam.getHOHLastName());
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
    		
    		if((wc=clientMgr.findClient(sessionID)) != null)
    		{
    			Set<String> keyset = params.keySet();
    			for(String key:keyset)
    				System.out.println(String.format("/referfamily key=%s, value=%s", key, params.get(key)));
    		
    			processFamilyReferral(wc, params);
    		
    			response = "<!DOCTYPE html><html><head lang=\"en\"><title>ONC Family Request Received</title></head><body><p>Family Referral Received, Thank You!</p></body></html>";
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
	
	String invalidTokenReceived()
	{
		return "<!DOCTYPE html><html><body><p>Invalid Token Received</p></body></html>";
		
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
	
	void processFamilyReferral(WebClient wc, Map<String, Object> params)
	{
		//get the agent
		int year = Integer.parseInt((String) params.get("year"));
		Agent agt = AgentDB.getAgent(year, wc.getWebUser());
		System.out.println("ONCHttpHandler.processFamilyReferral: Agent: " + agt.getAgentName());
		
		//verify that the HOH address is good
		System.out.println("ONCHttpHandler.processFamilyReferral: HOH Address: " + isHOHAddressValid(params));
		
		//verify that the Delivery address is good
		System.out.println("ONCHttpHandler.processFamilyReferral: Delivery Address: " + isDeliveryAddressValid(params));
		
		
		//create a family request
		String language = (String) params.get("language");
		String hohfn = (String) params.get("hohfn");
		String hohln = (String) params.get("hohln");
		String housenum = (String) params.get("housenum");
		String street = (String) params.get("street");
		String unit = (String) params.get("unit");
		String city = (String) params.get("city");
		String zipcode = (String) params.get("zipcode");
		String homephone = (String) params.get("homephone");
		String cellphone = (String) params.get("cellphone");
		String email = (String) params.get("email");
		String detail = (String) params.get("detail");
		String mealtype = (String) params.get("mealtype");
		String mealres = (String) params.get("dietres");
		
		int mealID = -1;
		//create a meal request, if meal was requested
		if(!mealtype.equals("No Assistance Rqrd"))
		{
			try {
				ServerMealDB mealDB = ServerMealDB.getInstance();
				ONCMeal mealReq = new ONCMeal(-1, -1, MealType.valueOf(mealtype), mealres, -1,
						agt.getAgentName(), new Date(), 3, "Family Referred", 
						agt.getAgentName());
				mealID = mealDB.add(year, mealReq);
			
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		//create the family
		int famID = -1;
		try {
			FamilyDB familyDB = FamilyDB.getInstance();
			ONCFamily fam = new ONCFamily(-1, agt.getAgentName(), "NNA", "ONCxxxxx", "B-ONC", 
					language.equals("English") ? "Yes" : "No", language,
					hohfn, hohln, housenum, street, unit, city, zipcode, homephone, cellphone, email,
					detail, createWishList(params), agt.getID(), mealID, 
					mealID == -1 ? MealStatus.None : MealStatus.Requested);
			famID = familyDB.add(year, fam);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(famID > -1)
		{
			//create the children for the family
			String childfn, childln, childDoB, childGender, childSchool;
			
			int cn = 0;
			String key = "childfn" + Integer.toString(cn);
			
			//using child first name as the iterator, create a db entry for each
			//child in the family
			try {
				ServerChildDB childDB = ServerChildDB.getInstance();
				while(params.containsKey(key))
				{
					childfn = (String) params.get(key);
					childln = (String) params.get("childln" + Integer.toString(cn));
					childDoB = (String) params.get("childdob" + Integer.toString(cn));
					childGender = (String) params.get("childgender" + Integer.toString(cn));
					childSchool = (String) params.get("childschool" + Integer.toString(cn));
					
					ONCChild child = new ONCChild(-1, famID, childfn, childln, childGender, 
													dateToMillis(childDoB), childSchool, year);
					
					childDB.add(year,child);
					
					cn++;
					key = "childfn" + Integer.toString(cn);	//get next child key
				}
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		
			//create the other adults
			//create the children for the family
			String adultName, adultGender;
			
			int an = 0;
			key = "adultname" + Integer.toString(an);
			
			//using child first name as the iterator, create a db entry for each
			//child in the family
			try {
				ServerAdultDB adultDB = ServerAdultDB.getInstance();
				while(params.containsKey(key))
				{
					adultName = (String) params.get(key);
					adultGender = (String) params.get("adultgender" + Integer.toString(cn));
					
					ONCAdult adult = new ONCAdult(-1, famID, adultName, adultGender); 
													
					adultDB.add(year, adult);
					
					cn++;
					key = "childfn" + Integer.toString(cn);	//get next child key
				}
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	long dateToMillis(String dob)
	{
		SimpleDateFormat sdf = new SimpleDateFormat();
		
		if(dob.contains("-"))
			 sdf.applyPattern("MM-dd-yyyy");
		else if(dob.contains("/"))
			sdf.applyPattern("MM/dd/yyyy");
		else if(dob.contains("."))
			sdf.applyPattern("MM.dd.yyyy");
		
		Date date = new Date();
		try {
			date = sdf.parse(dob);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return date.getTime();	
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
			buff.append(":");
			buff.append((String) params.get("wishlist" + Integer.toString(cn)));
			cn++;
			key = "childfn" + Integer.toString(cn);	//get next child key
			if(params.containsKey(key))	//if that wasn't the last child, add newline
				buff.append("\n");
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
	
	void verifyReferralInformation()
	{
		
	}
	
}
