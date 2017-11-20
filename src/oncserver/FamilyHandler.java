package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import ourneighborschild.Address;
import ourneighborschild.AddressValidation;
import ourneighborschild.AdultGender;
import ourneighborschild.MealStatus;
import ourneighborschild.MealType;
import ourneighborschild.ONCAdult;
import ourneighborschild.ONCChild;
import ourneighborschild.ONCFamily;
import ourneighborschild.ONCMeal;
import ourneighborschild.Transportation;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;

public class FamilyHandler extends ONCWebpageHandlerServices
{
	private static final String ONC_FAMILY_PAGE_HTML = "ONC.htm";
	private static final String UPDATE_HTML = "NewEdit.htm";
	private static final String REFERRAL_HTML = "NewFamilyReferral.htm";
	private static final String COMMON_FAMILY_JS_FILE = "NewCommonFamily.js";
	
	private static final int FAMILY_STOPLIGHT_RED = 2;
	private static final int NUM_OF_WISHES_PROVIDED = 4;
	private static final String GIFTS_REQUESTED_KEY = "giftreq";
	private static final String NO_WISH_PROVIDED_TEXT = "none";
	private static final String NO_GIFTS_REQUESTED_TEXT = "Gift assistance not requested";
	
	private static final long DAYS_TO_MILLIS = 1000 * 60 * 60 * 24;
	
	private ONCFamily lastFamilyAdded = null;
	
	FamilyHandler()
	{
		loadWebpages();
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
		
		if(requestURI.contains("/families"))
    	{
//    		Set<String> keyset = params.keySet();
//			for(String key:keyset)
//				System.out.println(String.format("/updateuser key=%s, value=%s", key, params.get(key)));
			
    		int year = Integer.parseInt((String) params.get("year"));
    		int agentID = Integer.parseInt((String) params.get("agentid"));
    		int groupID = Integer.parseInt((String) params.get("groupid"));
    		
    		HtmlResponse response = ServerFamilyDB.getFamiliesJSONP(year, agentID, groupID, (String) params.get("callback"));
    		sendHTMLResponse(t, response);
    	}
		else if(requestURI.contains("/references"))
    	{
    		//update the client time stamp
    		ClientManager clientMgr = ClientManager.getInstance();
    		WebClient wc;
    		HtmlResponse htmlResponse;
    		
    		if((wc=clientMgr.findClient((String) params.get("token"))) != null)	
    		{
    			wc.updateTimestamp();
    			//get the JSON of family reference list
    			int year = Integer.parseInt((String) params.get("year"));
    		
    			htmlResponse = ServerFamilyDB.getFamilyReferencesJSONP(year, (String) params.get("callback"));
    		}
    		else
    		{
    			String response = invalidTokenReceivedToJsonRequest("Error", (String) params.get("callback"));
    			htmlResponse = new HtmlResponse(response, HTTPCode.Ok);
    		}
    		sendHTMLResponse(t, htmlResponse);
    	}
    	else if(requestURI.contains("/familysearch"))
    	{
    		//update the client time stamp
    		ClientManager clientMgr = ClientManager.getInstance();
    		WebClient wc;
    		HtmlResponse htmlResponse;
    		
    		if((wc=clientMgr.findClient((String) params.get("token"))) != null)	
    		{
    			wc.updateTimestamp();
    			//get the JSON of family reference list
    			htmlResponse = ServerFamilyDB.searchForFamilyReferencesJSONP(
						Integer.parseInt((String) params.get("year")),
						 (String) params.get("searchstring"),
						  (String) params.get("callback"));
    		}
    		else
    		{
    			String response = invalidTokenReceivedToJsonRequest("Error", (String) params.get("callback"));
    			htmlResponse = new HtmlResponse(response, HTTPCode.Ok);
    		}
    		sendHTMLResponse(t, htmlResponse);
    	}
    	else if(requestURI.contains("/getfamily"))
    	{
    		//update the client time stamp
    		ClientManager clientMgr = ClientManager.getInstance();
    		WebClient wc;
    		HtmlResponse htmlResponse;
    		
    		if((wc=clientMgr.findClient((String) params.get("token"))) != null)	
    		{
    			wc.updateTimestamp();
    			int year = Integer.parseInt((String) params.get("year"));
        		String targetID = (String) params.get("targetid");
        		
        		htmlResponse = ServerFamilyDB.getFamilyJSONP(year, targetID, (String) params.get("callback"));
    		}
    		else
    		{
    			String response = invalidTokenReceivedToJsonRequest("Error", (String) params.get("callback"));
    			htmlResponse = new HtmlResponse(response, HTTPCode.Ok);
    		}
    		sendHTMLResponse(t, htmlResponse);
    	}
    	else if(requestURI.contains("/commonfamily.js"))
    	{
    		sendFile(t, "text/javascript", COMMON_FAMILY_JS_FILE);
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
    			response = webpageMap.get("referral");
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
    				response = response.replace("USER_NAME", wc.getWebUser().getFirstName());
    				response = response.replace("USER_MESSAGE", "");
    				response = response.replace("REPLACE_TOKEN", wc.getSessionID().toString());
    				response = response.replace("THANKSGIVING_CUTOFF", enableReferralButton("Thanksgiving"));
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
    			response = webpageMap.get("referral");	
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
    		
    		if(t.getRequestMethod().toLowerCase().equals("post") && 
    			params.containsKey("token") && params.containsKey("year") &&
    			 (wc=clientMgr.findClient(sessionID)) != null) 
    		{
    			wc.updateTimestamp();
//    			Set<String> keyset = params.keySet();
//    			for(String key:keyset)
//    				System.out.println(String.format("/referfamily key=%s, value=%s", key, params.get(key)));
    		
    			ResponseCode frc = processFamilyReferral(wc, params);
    			
    			//submission processed, send the family table page back to the user
//    			response = getHomePageHTML(wc, frc.getMessage(), frc.getFamRef());
    			
    			try
    			{
    				String userFN;
    				if(wc.getWebUser().getFirstName().equals(""))
    					userFN = wc.getWebUser().getLastName();
    				else
    					userFN = wc.getWebUser().getFirstName();
    				
    				response = readFile(String.format("%s/%s",System.getProperty("user.dir"), REFERRAL_STATUS_HTML));
    				response = response.replace("USER_NAME", userFN);
    				response = response.replace("USER_MESSAGE", frc.getMessage());
    				response = response.replace("REPLACE_TOKEN", wc.getSessionID().toString());
    				response = response.replace("THANKSGIVING_CUTOFF", enableReferralButton("Thanksgiving"));
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
    	else if(requestURI.contains("/familyview"))
    	{
    		String sessionID = (String) params.get("token");
    		ClientManager clientMgr = ClientManager.getInstance();
    		String response = null;
    		WebClient wc;
    		
    		if((wc=clientMgr.findClient(sessionID)) != null)
    		{
    			wc.updateTimestamp();
    			
    			//send the onc web page
    			String userFN;
        		if(wc.getWebUser().getFirstName().equals(""))
        			userFN = wc.getWebUser().getLastName();
        		else
        			userFN = wc.getWebUser().getFirstName();
        			
        		response = webpageMap.get("family");
    			response = response.replace("USER_NAME", userFN);
    			response = response.replace("USER_MESSAGE", "");
    			response = response.replaceAll("REPLACE_TOKEN", wc.getSessionID().toString());
    			response = response.replace("REPLACE_FAM_REF", "NNA");
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
    			response = webpageMap.get("update");
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
    		
    		if(t.getRequestMethod().toLowerCase().equals("post") && params.containsKey("token") &&
    				params.containsKey("year") && params.containsKey("targetid") &&
    				(wc=clientMgr.findClient(sessionID)) != null)
    		{
    			wc.updateTimestamp();
    			ResponseCode frc = processFamilyUpdate(wc, params);
    			
    			//submission processed, send the family table page back to the user
    			response = getHomePageHTML(wc, frc.getMessage(), (String) params.get("targetid"));
    		}
    		else
    			response = invalidTokenReceived();
    		
    		sendHTMLResponse(t, new HtmlResponse(response, HTTPCode.Ok));
    	}
	}
	ResponseCode processFamilyReferral(WebClient wc, Map<String, Object> params)
	{
		//get the agent/user
		int year = Integer.parseInt((String) params.get("year"));
		
		//get database references
		ServerMealDB mealDB = null;
		ServerFamilyDB serverFamilyDB= null;
		ServerChildDB childDB = null;
		ServerAdultDB adultDB = null;
		
		try
		{
			mealDB = ServerMealDB.getInstance();
			serverFamilyDB = ServerFamilyDB.getInstance();
			childDB = ServerChildDB.getInstance();
			adultDB = ServerAdultDB.getInstance();
		} 
		catch (FileNotFoundException e) 
		{
			return new ResponseCode("Family Referral Failed: Component Database Not Accessible");
		}
		catch (IOException e) 
		{
			return new ResponseCode("Family Referral Failed: Server Database I/O Error");
		}
		
		//create the family map
		String[] familyKeys = {"targetid", "language", "hohFN", "hohLN", "housenum", "street", "unit", "city",
						   "zipcode", "homephone", "cellphone", "altphone", "email","delhousenum", 
						   "delstreet","detail", "delunit", "delcity", "delzipcode", "transportation"};
				
		Map<String, String> familyMap = createMap(params, familyKeys);
		
		//check to see if this family was added within the last second. If so, don't do a thing.
//		if(wasThisFamilyAlreadyAddedWithinASecond(wc, familyMap))
//		{
//			new ResponseCode(String.format("%s Family Referral Accepted, ONC# %s",
//					lastFamilyAdded.getLastName(), lastFamilyAdded.getONCNum()));
//		}
		
		//create a meal request, if meal was requested
		ONCMeal mealReq = null, addedMeal = null;
		String[] mealKeys = {"mealtype", "dietres"};
		
		if(params.containsKey("mealtype"))
		{
			Map<String, String> mealMap = createMap(params, mealKeys);
			String dietRestrictions = "";

			if(!mealMap.get("mealtype").equals("No Assistance Rqrd"))
			{
				if(mealMap.containsKey("dietres"))
					dietRestrictions = mealMap.get("dietres");
				
				mealReq = new ONCMeal(-1, -1, MealStatus.Requested, MealType.valueOf(mealMap.get("mealtype")),
								dietRestrictions, -1, wc.getWebUser().getLNFI(), new Date(), 3,
								"Family Referred", wc.getWebUser().getLNFI());
			
				addedMeal = mealDB.add(year, mealReq);
			}
		}
		
		//check to see if this is a new family or a re-referral. Need to know this to determine 
		//whether to perform a prior year check after the family and children objects are created
		boolean bNewFamily = familyMap.get("targetid").contains("NNA") || familyMap.get("targetid").equals("");
		
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
					params.containsKey(GIFTS_REQUESTED_KEY) && params.get(GIFTS_REQUESTED_KEY).equals("on"),
					createWishList(params), wc.getWebUser().getID(),
					addedMeal != null ? addedMeal.getID() : -1,
					addedMeal != null ? MealStatus.Requested : MealStatus.None,
					Transportation.valueOf(familyMap.get("transportation")));
			
		ONCFamily addedFamily = serverFamilyDB.add(year, fam);
		
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
			String key = "childln" + Integer.toString(cn);
			
			//using child last name as the iterator, create a db entry for each
			//child in the family. Protect against null or missing keys.
			while(params.containsKey(key))
			{
				childln = params.get(key) != null ? (String) params.get(key) : "";
				childfn = params.get("childfn" + Integer.toString(cn)) != null ? (String) params.get("childfn" + Integer.toString(cn)) : "";
				childDoB = params.get("childdob" + Integer.toString(cn)) != null ? (String) params.get("childdob" + Integer.toString(cn)) : "";
				childGender = params.get("childgender" + Integer.toString(cn)) != null ? (String) params.get("childgender" + Integer.toString(cn)) : "";
				childSchool = params.get("childschool" + Integer.toString(cn)) != null ? (String) params.get("childschool" + Integer.toString(cn)) : "";
				
//				childDoB = (String) params.get("childdob" + Integer.toString(cn));
//				childGender = (String) params.get("childgender" + Integer.toString(cn));
//				childSchool = (String) params.get("childschool" + Integer.toString(cn));
			
				if(!childln.isEmpty())	//only add a child if the last name is provided
				{
					ONCChild child = new ONCChild(-1, addedFamily.getID(), childfn.trim(), childln.trim(), childGender, 
													createChildDOB(childDoB), childSchool.trim(), year);
				
					addedChildList.add(childDB.add(year,child));
				}
				
				cn++;
				key = "childln" + Integer.toString(cn);	//get next child key
			}
			
			//now that we have added children, we can check for duplicate family in this year.
			ONCFamily dupFamily = serverFamilyDB.getDuplicateFamily(year, addedFamily, addedChildList);
			
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
					pyFamily = serverFamilyDB.isPriorYearFamily(year, addedFamily, addedChildList);
					if(pyFamily != null)
					{				
						//added new family was in prior year, keep the prior year reference # 
						//and reset the newly assigned target id index
						addedFamily.setReferenceNum(pyFamily.getReferenceNum());
						serverFamilyDB.decrementReferenceNumber();
					}
				}
			}
			//else if family was a dup, determine which family has the best reference number to
			//use. The family with the best reference number is retained and the family with 
			//the worst reference number is marked as duplicate
			else if(!dupFamily.getReferenceNum().startsWith("C") && 
						addedFamily.getReferenceNum().startsWith("C"))
			{
//				System.out.println(String.format("HttpHandler.processFamilyReferral, dupFamily no C: "
//						+ "dupFamily HOHLastName= %s, dupRef#= %s, addedFamily HOHLastName = %s, addedFamily Ref#= %s", 
//						dupFamily.getHOHLastName(), dupFamily.getODBFamilyNum(), 
//						addedFamily.getHOHLastName(), addedFamily.getODBFamilyNum()));
				
				//family is in current year already with an ODB referred target ID
				addedFamily.setONCNum("DEL");
				addedFamily.setDNSCode("DUP");
				addedFamily.setStoplightPos(FAMILY_STOPLIGHT_RED);
				addedFamily.setStoplightMssg("DUP of " + dupFamily.getReferenceNum());
				addedFamily.setReferenceNum(dupFamily.getReferenceNum());
				serverFamilyDB.decrementReferenceNumber();
			}	
			else if(dupFamily.getReferenceNum().startsWith("C") && 
					!addedFamily.getReferenceNum().startsWith("C"))
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
				dupFamily.setStoplightMssg("DUP of " + addedFamily.getReferenceNum());
				dupFamily.setStoplightChangedBy(wc.getWebUser().getLNFI());
				dupFamily.setReferenceNum(addedFamily.getReferenceNum());
			}
			else if(dupFamily.getReferenceNum().startsWith("C") && 
					addedFamily.getReferenceNum().startsWith("C"))
			{
				//which one was first?
				int dupNumber = Integer.parseInt(dupFamily.getReferenceNum().substring(1));
				int addedNumber = Integer.parseInt(addedFamily.getReferenceNum().substring(1));
				
				if(dupNumber < addedNumber)
				{
					//dup family has the correct ref #, so added family is duplicate
					addedFamily.setONCNum("DEL");
					addedFamily.setDNSCode("DUP");
					addedFamily.setStoplightPos(FAMILY_STOPLIGHT_RED);
					addedFamily.setStoplightMssg("DUP of " + dupFamily.getReferenceNum());
					addedFamily.setStoplightChangedBy(wc.getWebUser().getLNFI());
					addedFamily.setReferenceNum(dupFamily.getReferenceNum());
					serverFamilyDB.decrementReferenceNumber();
				}
				else
				{
					//added family has the correct ref #, so dup family is the duplicate
					dupFamily.setONCNum("DEL");
					dupFamily.setDNSCode("DUP");
					dupFamily.setStoplightPos(FAMILY_STOPLIGHT_RED);
					dupFamily.setStoplightMssg("DUP of " + addedFamily.getReferenceNum());
					dupFamily.setStoplightChangedBy(wc.getWebUser().getLNFI());
					dupFamily.setReferenceNum(addedFamily.getReferenceNum());
				}
			}
			
			//create the other adults in the family
			String adultName;
			AdultGender adultGender;
			
			int an = 0;
			key = "adultname" + Integer.toString(an);
			
			//using adult name as the iterator, create a db entry for each
			//adult in the family
			while(params.containsKey(key))
			{
				adultName = params.get(key) != null ? (String) params.get(key) : "";
				adultGender = params.get("adultgender" + Integer.toString(an)) != null ?
					AdultGender.valueOf((String) params.get("adultgender" + Integer.toString(an))) : AdultGender.Unknown;
				
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
			
			return new ResponseCode(String.format("%s Family Referral Accepted, ONC# %s",
									addedFamily.getLastName(), addedFamily.getONCNum()));
		}
		
		return new ResponseCode("Family Referral Failure: Unable to Process Referral");
	}
	
	boolean wasThisFamilyAlreadyAddedWithinASecond(WebClient wc, Map<String,String> familyMap)
	{
		return lastFamilyAdded != null &&
				familyMap.get("hohFN").equals(lastFamilyAdded.getFirstName()) &&
				familyMap.get("hohLN").equals(lastFamilyAdded.getLastName()) &&
				wc.getWebUser().getID() == lastFamilyAdded.getAgentID() &&
				familyMap.get("housenum").equals(lastFamilyAdded.getHouseNum()) &&
				ensureUpperCaseStreetName(familyMap.get("street")).equals(lastFamilyAdded.getStreet()) &&
				familyMap.get("zipcode").equals(lastFamilyAdded.getZipCode()) &&
				familyMap.get("detail").equals(lastFamilyAdded.getDetails()) &&
				areReferralsWithinASecond(System.currentTimeMillis(), lastFamilyAdded.getTimeInMillis());
	}
	
	boolean areReferralsWithinASecond(long millis1, long millis2)
	{
		return ((millis1 - millis2 >= 0 && millis1 - millis2 < 1000) ||
				(millis2 - millis1 >= 0 && millis2 - millis1 < 1000));
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
		String childfnkey = "childfn" + Integer.toString(cn);
		String key = "childschool" + Integer.toString(cn);
		StringBuffer buff = new StringBuffer();
		
		//using child school as the iterator, create list of unique schools
		while(params.containsKey(key))
		{
			String school = (String) params.get(key);
			String childfn = (String) params.get(childfnkey);
			if(school != null && !school.equals("") && buff.indexOf(school) == -1)
			{
				if(nSchoolsAdded > 0)
					buff.append("\r" + childfn + ": " + school);
				else
					buff.append(childfn + ": " + school);
				
				nSchoolsAdded++;
			}

			key = "childschool" + Integer.toString(++cn);
			childfnkey = "childfn" + Integer.toString(cn);
		}
		
		if(nSchoolsAdded > 0)
			return buff.toString();
		else
			return "";
	}
	
	ResponseCode processFamilyUpdate(WebClient wc, Map<String, Object> params)
	{
		//get the agent
		int year = Integer.parseInt((String) params.get("year"));
		
		//get database references
		ServerFamilyDB serverFamilyDB= null;
		
		try
		{
			serverFamilyDB = ServerFamilyDB.getInstance();
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
		ONCFamily updateFam = serverFamilyDB.getFamilyByTargetID(year, targetID);
		
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
			if(serverFamilyDB.update(year, updateFam, true) != null)
			{
				//successfully updated family. Notify the desktop clients so they refresh
				ClientManager clientMgr = ClientManager.getInstance();
				Gson gson = new Gson();
				String mssg;
				mssg = "UPDATED_FAMILY" + gson.toJson(updateFam, ONCFamily.class);
				clientMgr.notifyAllInYearClients(year, mssg);
				return new ResponseCode(updateFam.getLastName() + " Family Update Accepted");
			}
			
			return new ResponseCode("Family Referral Rejected: Unable to Save Update");
		}
		
		return new ResponseCode("Family Referral Rejected: Family Not Found");
	}
	
	/**************************************************************************************************
	 * This method takes a string date in one of two formats (yyyy-MM-dd or M/D/yy) and returns a Date
	 * object from the string. If the input string is not of either format, the current date is returned.
	 ***************************************************************************************************/
    Long createChildDOB(String dob)
    {
		TimeZone timezone = TimeZone.getTimeZone("GMT");
		Calendar gmtDOB = Calendar.getInstance(timezone);
		gmtDOB.set(Calendar.HOUR_OF_DAY, 0);
		gmtDOB.set(Calendar.MINUTE, 0);
		gmtDOB.set(Calendar.SECOND, 0);
		gmtDOB.set(Calendar.MILLISECOND, 0);
		
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
//			String errMssg = "Couldn't determine DOB from input: " + dob;
			return gmtDOB.getTimeInMillis();
		}

    	//then convert the Calendar to a Date in Millis and return it
    	return gmtDOB.getTimeInMillis();
    }

	String createWishList(Map<String, Object> params)
	{
		//check to see if gift assistance was requested. If not, simply return a message saying that
		if(params.containsKey(GIFTS_REQUESTED_KEY) && params.get(GIFTS_REQUESTED_KEY).equals("on"))
		{
			//gift assistance was requested
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
				
				for(int wn=0; wn<NUM_OF_WISHES_PROVIDED; wn++)
				{
					String wishtext = (String) params.get("wish" + Integer.toString(cn) + Integer.toString(wn));
					if(wishtext == null || wishtext.equals("null"))
						buff.append(NO_WISH_PROVIDED_TEXT);
					else
						buff.append(wishtext);
					buff.append(wn < NUM_OF_WISHES_PROVIDED-1 ? ", " : ";");
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
		else
			return NO_GIFTS_REQUESTED_TEXT;
		
	}
	
	boolean isHOHAddressValid(Map<String, Object> params)
	{
		boolean bAddressGood = false;
		String houseNum = null, streetName = null, zipCode = null;
		if(params.containsKey("housenum") && (houseNum = (String) params.get("housenum")) != null
			&& params.containsKey("street") && (streetName = (String) params.get("street")) != null 
			 && params.containsKey("zipcode") && (zipCode = (String) params.get("zipcode")) != null)
		{
			//dont need unit and city in Address to check region validity
			Address chkAddress = new Address(houseNum, streetName, "", "", zipCode);
			
			//diagnostic print
//			String postDir = chkAddress.getStreetPostDir().isEmpty() ? "" : "-" + chkAddress.getStreetPostDir();
//			String direction = chkAddress.getStreetDir().isEmpty() ? "" : chkAddress.getStreetDir() + ".";
//			System.out.println(String.format("%s%s %s%s %s %s %s %s",chkAddress.getStreetNum(), postDir, direction, 
//						chkAddress.getStreetName(), chkAddress.getStreetType(), chkAddress.getUnit(), chkAddress.getCity(), chkAddress.getZipCode()));
			bAddressGood = RegionDB.isAddressValid(chkAddress);
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
			//don't need unit or city to check region validity
			bAddressGood = RegionDB.isAddressValid(new Address(houseNum, streetName, "", "", zipCode));
		}
		
		return bAddressGood;
	}
	String verifyAddress(Map<String, Object> params)
	{
		String callback = (String) params.get("callback");
		
		String[] addressKeys = {"housenum", "street", "unit", "city", "zipcode"};
		Map<String, String> addressMap = createMap(params, addressKeys);
		
//		for(String key:addressMap.keySet())
//			System.out.println(String.format("ONCHttpHandler.verifyAddress: key=%s, value=%s", key, addressMap.get(key)));
		
		Address chkAddress = new Address(addressMap.get("housenum"), addressMap.get("street"),
								addressMap.get("unit"), addressMap.get("city"), addressMap.get("zipcode"));
		
		//diagnostic print
//		System.out.println(chkAddress.getPrintableAddress());

		boolean bAddressValid  = RegionDB.isAddressValid(chkAddress);
		int errorCode = bAddressValid ? 0 : 1;
		
		//check that a unit might be missing. If a unit is already provided, no need to perform the check.
		boolean bUnitMissing = addressMap.get("unit").trim().isEmpty() && ApartmentDB.isAddressAnApartment(chkAddress);
		
		if(bUnitMissing)
			errorCode += 2;
		
//		System.out.println("HttpHandler.verifyAddress: ErrorCode: "+ errorCode);		
		boolean bAddressGood = bAddressValid && !bUnitMissing;
		
		Gson gson = new Gson();
		String json = gson.toJson(new AddressValidation(bAddressGood, errorCode), AddressValidation.class);
		
		return callback +"(" + json +")";
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

	@Override
	public void loadWebpages()
	{
		webpageMap = new HashMap<String, String>();
		try 
		{
			webpageMap.put("family", readFile(String.format("%s/%s",System.getProperty("user.dir"), ONC_FAMILY_PAGE_HTML)));
			webpageMap.put("referral", readFile(String.format("%s/%s",System.getProperty("user.dir"), REFERRAL_HTML)));
			webpageMap.put("update", readFile(String.format("%s/%s",System.getProperty("user.dir"), UPDATE_HTML)));
		} 
		catch (IOException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
