package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import ourneighborschild.Address;
import ourneighborschild.AdultGender;
import ourneighborschild.MealStatus;
import ourneighborschild.MealType;
import ourneighborschild.ONCAdult;
import ourneighborschild.ONCChild;
import ourneighborschild.ONCFamily;
import ourneighborschild.ONCFamilyHistory;
import ourneighborschild.ONCMeal;
import ourneighborschild.ONCServerUser;
import ourneighborschild.Transportation;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsExchange;

public class FamilyHandler extends ONCWebpageHandler
{
	private static final int FAMILY_STOPLIGHT_RED = 2;
	private static final int NUM_OF_WISHES_PROVIDED = 4;
	private static final String GIFTS_REQUESTED_KEY = "giftreq";
	private static final String NO_WISH_PROVIDED_TEXT = "none";
	private static final String NO_GIFTS_REQUESTED_TEXT = "Gift assistance not requested";
	private static final String DNS_CODE_WAITLIST = "WL";
	
	private static final long DAYS_TO_MILLIS = 1000 * 60 * 60 * 24;
	
	private ONCFamily lastFamilyAdded = null;
	private String lastReferralUUIDAccepted = "";

	@Override
	public void handle(HttpExchange te) throws IOException
	{
		HttpsExchange t = (HttpsExchange) te;
		@SuppressWarnings("unchecked")
		Map<String, Object> params = (Map<String, Object>)t.getAttribute("parameters");
	
		String requestURI = t.getRequestURI().toASCIIString();
    	
		String mssg = String.format("HTTP request %s: %s:%s", t.getRemoteAddress().toString(), t.getRequestMethod(), requestURI);
		ServerUI.getInstance().addUIAndLogMessage(mssg);
		
		if(requestURI.contains("/families"))
		{
			HtmlResponse htmlResponse;
    			WebClient wc = clientMgr.findAndValidateClient(t.getRequestHeaders());
			if(wc != null)
			{
    				int year = Integer.parseInt((String) params.get("year"));
    				
    				String zAgentID = (String) params.get("agentid");
    				Integer agentID = zAgentID == null? null : Integer.parseInt((String) params.get("agentid"));
    				
    				String zGroupID = (String) params.get("agentid");
    				Integer groupID = zGroupID==null? null : Integer.parseInt((String) params.get("groupid"));
    		
    				htmlResponse = ServerFamilyDB.getFamiliesJSONP(year, agentID, wc.getWebUser(), groupID, (String) params.get("callback"));
			}
			else
				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String) params.get("callback"));
			
    			sendHTMLResponse(t, htmlResponse);
		}
		else if(requestURI.contains("/references"))
		{
    			HtmlResponse htmlResponse;
    		
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
    			{
    				//get the JSON of family reference list
    				int year = Integer.parseInt((String) params.get("year"));
    				htmlResponse = ServerFamilyDB.getFamilyReferencesJSONP(year, (String) params.get("callback"));
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String) params.get("callback"));
    			
    			sendHTMLResponse(t, htmlResponse);
		}
		else if(requestURI.contains("/familysearch"))
		{
    			HtmlResponse htmlResponse;
    		
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
    			{
    				//get the JSON of family reference list
    				htmlResponse = ServerFamilyDB.searchForFamilyReferencesJSONP(
						Integer.parseInt((String) params.get("year")),
						 (String) params.get("searchstring"),
						  (String) params.get("callback"));
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String) params.get("callback"));
    				
    			sendHTMLResponse(t, htmlResponse);
		}
		else if(requestURI.contains("/getfamily"))
    		{
			HtmlResponse htmlResponse;
    		
			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)	
			{
				int year = Integer.parseInt((String) params.get("year"));
				String targetID = (String) params.get("targetid");
				boolean bIncludeSchool = ((String) params.get("schools")).equalsIgnoreCase("true") ? true : false;
        		
				htmlResponse = ServerFamilyDB.getFamilyJSONP(year, targetID, bIncludeSchool, (String) params.get("callback"));
			}
			else
				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String) params.get("callback"));
				
			sendHTMLResponse(t, htmlResponse);
    		}
		else if(requestURI.contains("/commonfamily.js"))
		{
			sendCachedFile(t, "text/javascript", "commonfamily", false);
		}
		else if(requestURI.contains("/newfamily"))
		{
    			String response;
    		
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
    			{	
    				response = webpageMap.get("referral");
    				response = response.replace("SERVER_GENERATED_UUID", UUID.randomUUID().toString());
    			}
    			else
    				response = invalidTokenReceived();
    		
    			sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
    		}
		else if(requestURI.contains("/familystatus"))
    		{
    			String response;
    			WebClient wc;
    			
    			if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)
    				response = getFamilyStatusWebpage(wc, "", "", "", false);
    			else
    				response = invalidTokenReceived();
    		
    			sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
    		}
		else if(requestURI.contains("/referral"))
		{
    			String response;
    			
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
    			{	
    				response = webpageMap.get("referral");
    				response = response.replace("SERVER_GENERATED_UUID", UUID.randomUUID().toString());
    			}
    			else
    				response = invalidTokenReceived();
    		
    			sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
		}
		else if(requestURI.contains("/referfamily"))
    		{
    			String response;
    			WebClient wc;

    			if(t.getRequestMethod().toLowerCase().equals("post") && params.containsKey("year") &&
    					((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)) 
    			{
    				
    				//process referral and send family status web page back to client
    				logParameters(params, requestURI);
    				ResponseCode frc = processFamilyReferral(wc, params);
    			    				
    				response = getFamilyStatusWebpage(wc, frc.getMessage(), frc.getSuccessMessage(), frc.getTitle(), true);
    			}
    			else
    				response = invalidTokenReceived();
    		
    			sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
    		}
		else if(requestURI.contains("/familyview"))
		{
    			String response = null;
    			WebClient wc;
    		
    			if(params.containsKey("year") &&
    					(wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)
    			{
    				//send the onc web page
    				String userFN;
    				if(wc.getWebUser().getFirstName().equals(""))
    					userFN = wc.getWebUser().getLastName();
    				else
    					userFN = wc.getWebUser().getFirstName();
        			
    				response = webpageMap.get("family");
    				response = response.replace("USER_NAME", userFN);
    				response = response.replace("USER_MESSAGE", "");
    				response = response.replace("REPLACE_FAM_REF", "NNA");
    			}
    			else
    				response = invalidTokenReceived();
    		
    			sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
		}
		else if(requestURI.contains("/familyupdate"))
		{
    			String response;
    			
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
    				response = webpageMap.get("update");
    			else
    				response = invalidTokenReceived();
    		
    			sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
		}
		else if(requestURI.contains("/updatefamily"))
		{
    			String response = null;
    			WebClient wc; 		

    			if(t.getRequestMethod().toLowerCase().equals("post") && params.containsKey("year") &&
    				((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null))
    			{
    				//submission processed, send the home page back to the user
    				ResponseCode frc = processFamilyUpdate(wc, params);
    				response = getFamilyStatusWebpage(wc, frc.getMessage(), frc.getMessage(), "Family Update Successful",true);
    			}
    			else
    				response = invalidTokenReceived();
    		
    			sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
    		}
		else if(requestURI.contains("/familynotes"))
		{
			WebClient wc;
    			HtmlResponse htmlResponse;
    		
    			if((wc = clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)
    			{
    				//get the JSON of family reference list
    				int year = Integer.parseInt((String) params.get("year"));
    				int famID = Integer.parseInt((String) params.get("famid"));
    				htmlResponse = ServerNoteDB.getLastNoteForFamilyJSONP(year, famID, wc, (String) params.get("callback"));
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String) params.get("callback"));
    			
    			sendHTMLResponse(t, htmlResponse);
		}
		else if(requestURI.contains("/noteresponse"))
		{
			WebClient wc;
			HtmlResponse htmlResponse;
			if((wc = clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)
			{
				//get the JSON for response to response submission
				int year = Integer.parseInt((String) params.get("year"));
				int noteID = Integer.parseInt((String) params.get("noteID"));
				htmlResponse = ServerNoteDB.processNoteResponseJSONP(year, noteID, wc, 
								(String) params.get("response"), (String) params.get("callback"));
			}
			else
				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String) params.get("callback"));
			
			sendHTMLResponse(t, htmlResponse);
		}
		else if(requestURI.contains("/dnscode"))
		{
			HtmlResponse htmlResponse;
			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
			{
				//get the JSON for response to response submission
				String code = (String) params.get("code");
				htmlResponse = ServerNoteDB.getDNSCodeJSONP(code, (String) params.get("callback"));
			}
			else
				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String) params.get("callback"));
			
			sendHTMLResponse(t, htmlResponse);
		}
	}
	
	ResponseCode processFamilyReferral(WebClient wc, Map<String, Object> params)
	{
		//get the year
		int year = Integer.parseInt((String) params.get("year"));
		
		//get database references
		ServerMealDB mealDB = null;
		ServerFamilyDB serverFamilyDB= null;
		ServerChildDB childDB = null;
		ServerAdultDB adultDB = null;
		ServerFamilyHistoryDB famHistDB = null;
		ServerGlobalVariableDB globalDB = null;
		
		try
		{
			mealDB = ServerMealDB.getInstance();
			serverFamilyDB = ServerFamilyDB.getInstance();
			childDB = ServerChildDB.getInstance();
			adultDB = ServerAdultDB.getInstance();
			famHistDB = ServerFamilyHistoryDB.getInstance();
			globalDB = ServerGlobalVariableDB.getInstance();
		} 
		catch (FileNotFoundException e) 
		{
			return new ResponseCode("Family Referral Failed: Component Database Not Accessible");
		}
		catch (IOException e) 
		{
			return new ResponseCode("Family Referral Failed: Server Database I/O Error");
		}
		
		//determine if the referral was after the home delivery service deadline
		boolean bWaitlistFamily = new Date().after(globalDB.getDeadline(year, "December Gift"));
		
		//create the family map
		String[] familyKeys = {"targetid", "language", "hohfn", "hohln", "housenum", "street", "unit", "city",
						   "zipcode", "homephone", "cellphone", "altphone", "email","delhousenum", 
						   "delstreet","detail", "delunit", "delcity", "delzipcode", "transportation", "uuid"};
				
		Map<String, String> familyMap = createMap(params, familyKeys);

		//check to see if this family was the last family added. If so, don't do a thing other then
		//return the appropriate success message. If it's not the last family, process it.
		if(!lastReferralUUIDAccepted.equals(familyMap.get("uuid")))
		{
			//create a meal request, if meal was requested
			ONCMeal mealReq = null, addedMeal = null;
			String[] mealKeys = {"mealtype", "dietres"};
		
			if(!bWaitlistFamily && params.containsKey("mealtype"))
			{
				Map<String, String> mealMap = createMap(params, mealKeys);
				String dietRestrictions = "";

				if(!mealMap.get("mealtype").equals("No Assistance Rqrd") || 
					!mealMap.get("mealtype").equals("Unavailable"))
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
		
			//make sure we got a group from the rest call. if not, figure out the group by the agent
			int group = -1;
			ONCServerUser agent = wc.getWebUser();
			if(params.containsKey("groupcb") && isNumeric((String) params.get("groupcb")))
				group = Integer.parseInt((String) params.get("groupcb"));
			else
			{
				List<Integer> groupIDList = agent.getGroupList();
				if(!groupIDList.isEmpty())
					group = groupIDList.get(0);
			}
		
			ONCFamily fam = new ONCFamily(-1, wc.getWebUser().getLNFI(), "NNA",
					familyMap.get("targetid"), "B-DI", bWaitlistFamily ? DNS_CODE_WAITLIST : "",
					familyMap.get("language").equals("English") ? "Yes" : "No", familyMap.get("language"),
					familyMap.get("hohfn"), familyMap.get("hohln"), familyMap.get("housenum"),
					ensureUpperCaseStreetName(familyMap.get("street")),
					familyMap.get("unit"), familyMap.get("city"),
					familyMap.get("zipcode"), familyMap.get("delhousenum"),
					ensureUpperCaseStreetName(familyMap.get("delstreet")),
					familyMap.get("delunit"), familyMap.get("delcity"), familyMap.get("delzipcode"),
					familyMap.get("homephone"), familyMap.get("cellphone"), familyMap.get("altphone"),
					familyMap.get("email"), familyMap.get("detail"), createFamilySchoolList(params),
					params.containsKey(GIFTS_REQUESTED_KEY) && params.get(GIFTS_REQUESTED_KEY).equals("on"),
					createWishList(params), agent.getID(), group, 
					addedMeal != null ? addedMeal.getID() : -1,
					addedMeal != null ? MealStatus.Requested : MealStatus.None,
					Transportation.valueOf(familyMap.get("transportation")));
		
			//add the family and family history to the data base
			ONCFamily addedFamily = serverFamilyDB.add(year, fam);
			if(addedFamily != null)
			{
				lastFamilyAdded = addedFamily;
				lastReferralUUIDAccepted = familyMap.get("uuid");
				
				//add the family history object and update the family history object id
				ONCFamilyHistory famHistory = new ONCFamilyHistory(-1, addedFamily.getID(), 
															addedFamily.getFamilyStatus(),
															addedFamily.getGiftStatus(),
															"", "Family Referred", 
															addedFamily.getChangedBy(), 
															Calendar.getInstance(TimeZone.getTimeZone("UTC")),
															addedFamily.getDNSCode());
		
				ONCFamilyHistory addedFamHistory = famHistDB.addFamilyHistoryObject(year, famHistory, false);
				if(addedFamHistory != null)
					addedFamily.setDeliveryID(addedFamHistory.getID());
		
				List<ONCChild> addedChildList = new ArrayList<ONCChild>();
				List<ONCAdult> addedAdultList = new ArrayList<ONCAdult>();
		
				//update the family id for the meal, if a meal was requested
				if(addedMeal != null)
				{
					addedMeal.setFamilyID(addedFamily.getID());
					mealDB.update(year, addedMeal);
				}
			
				//create the children for the family
				String childfn, childln, childDoB, childGender, childSchool;
				int cn = 0;
				String key = "childln0";
			
				//using child last name as the iterator, create a db entry for each
				//child in the family. Protect against null or missing keys.
				while(params.containsKey(key))
				{
					childln = params.get(key) != null ? (String) params.get(key) : "";
					childfn = params.get("childfn" + Integer.toString(cn)) != null ? (String) params.get("childfn" + Integer.toString(cn)) : "";
					childDoB = params.get("childdob" + Integer.toString(cn)) != null ? (String) params.get("childdob" + Integer.toString(cn)) : "";
					childGender = params.get("childgender" + Integer.toString(cn)) != null ? (String) params.get("childgender" + Integer.toString(cn)) : "";
					childSchool = params.get("childschool" + Integer.toString(cn)) != null ? (String) params.get("childschool" + Integer.toString(cn)) : "";

					if(!childln.isEmpty())	//only add a child if the last name is provided
					{
						ONCChild child = new ONCChild(-1, addedFamily.getID(), childfn.trim(), childln.trim(), childGender, 
													createChildDOB(childDoB), childSchool.trim(), year);
				
						addedChildList.add(childDB.add(year,child));
					}
					key = "childln" + Integer.toString(++cn);	//get next child key
				}
			
				//now that we have added children, we can check for duplicate family in this year.
				ONCFamily dupFamily = serverFamilyDB.getDuplicateFamily(year, addedFamily, addedChildList);	
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
//					System.out.println(String.format("HttpHandler.processFamilyReferral, dupFamily no C: "
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
//					System.out.println(String.format("HttpHandler.processFamilyReferral: dupFamily with C "
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
				key = "adultname0";
			
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
						key = "adultname" + Integer.toString(++an);	//get next adult key
					}
		
				//successfully process meals, family, history, children and adults. Notify the desktop
				//clients so they refresh
				Gson gson = new Gson();

				List<String> mssgList = new ArrayList<String>();
				mssgList.add("ADDED_FAMILY" + gson.toJson(addedFamily, ONCFamily.class));
				for(ONCChild addedChild:addedChildList)
					mssgList.add("ADDED_CHILD" + gson.toJson(addedChild, ONCChild.class));
				
				for(ONCAdult addedAdult:addedAdultList)
					mssgList.add("ADDED_ADULT" + gson.toJson(addedAdult, ONCAdult.class));
		
				if(addedFamHistory != null)
					mssgList.add("ADDED_DELIVERY" + gson.toJson(addedFamHistory, ONCFamilyHistory.class));
				
				if(addedMeal != null)
					mssgList.add("ADDED_MEAL" + gson.toJson(addedMeal, ONCMeal.class));
				
				clientMgr.notifyAllInYearClients(year, mssgList);
			
				String mssg, successMssg, title;
				if(!bWaitlistFamily)
				{
					mssg = String.format("%s Family Referral Accepted, ONC# %s",
						addedFamily.getLastName(), addedFamily.getONCNum());
				
					successMssg = mssg;
					title = "Referral Received";
				}
				else
				{
					mssg = String.format("%s Family Referral Received to ONC's Wait List, ONC# %s.",
						addedFamily.getLastName(), addedFamily.getONCNum());
				
					successMssg = String.format("%s Family Referral received and is on our Waitlist, ONC# %s. "
					+ "Once Family Status is marked \"Verified\" please notify the family that gift pickup will "
					+ "take place on 12/22 from 10am-noon at the Centreville Regional Library. Photo ID "
					+ "matching HOH required. This is for wait list GIFTS only. Please contact WFCM for "
					+ "post-deadline food requests.",
					addedFamily.getLastName(), addedFamily.getONCNum());
				
					title = "Waitlist Referral Received";
				}

				return new ResponseCode(mssg, successMssg, title, addedFamily.getONCNum());
			}
			else
				return new ResponseCode("Family Referral Failure: Unable to Process Referral");
		}
		else
		{
			//family was the last one received, no processing occurred
			String mssg, successMssg, title;
			if(!bWaitlistFamily)
			{
				mssg = String.format("%s Family Referral Accepted, ONC# %s",
					lastFamilyAdded.getLastName(), lastFamilyAdded.getONCNum());
			
				successMssg = mssg;
				title = "Referral Received";
			}
			else
			{
				mssg = String.format("%s Family Referral Received to ONC's Wait List, ONC# %s.",
						lastFamilyAdded.getLastName(), lastFamilyAdded.getONCNum());
			
				successMssg = String.format("%s Family Referral received and is on our Waitlist, ONC# %s. "
				+ "Once Family Status is marked \"Verified\" please notify the family that gift pickup will "
				+ "take place on 12/22 from 10am-noon at the Centreville Regional Library. Photo ID "
				+ "matching HOH required. This is for wait list GIFTS only. Please contact WFCM for "
				+ "post-deadline food requests.",
				lastFamilyAdded.getLastName(), lastFamilyAdded.getONCNum());
			
				title = "Waitlist Referral Received";
			}

			return new ResponseCode(mssg, successMssg, title, lastFamilyAdded.getONCNum());
		}
	}
	
	boolean wasThisFamilyAlreadyAddedWithinASecond(WebClient wc, Map<String,String> familyMap)
	{
		return lastFamilyAdded != null &&
				familyMap.get("hohfn").equals(lastFamilyAdded.getFirstName()) &&
				familyMap.get("hohln").equals(lastFamilyAdded.getLastName()) &&
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
		//get the year
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
		
		//get the family object from the database. If not found, return an error message. If found, try
		//to process the update
		String targetID = (String) params.get("targetid");
		ONCFamily editedFam = serverFamilyDB.getFamilyByTargetID(year, targetID);
		
		if(editedFam != null)
		{	
			//make a copy for update
			ONCFamily updateFam  = new ONCFamily(editedFam);
		
			//family was found, continue processing to see if any data was changed
			String[] familyKeys = {"targetid", "language", "hohfn", "hohln", "housenum", "street", "unit", "city",
								"zipcode", "homephone", "cellphone", "altphone", "email","delhousenum", 
								"delstreet","detail", "delunit", "delcity", "delzipcode"};
			
			Map<String, String> familyMap = createMap(params, familyKeys);
			
			String streetname = ensureUpperCaseStreetName(familyMap.get("street"));
			int cc = 0;
			
			if(!updateFam.getLanguage().equals(familyMap.get("language"))) { updateFam.setLanguage(familyMap.get("language")); cc = cc | 1;}
			if(!updateFam.getFirstName().equals(familyMap.get("hohfn"))) {updateFam.setHOHFirstName(familyMap.get("hohfn")); cc = cc | 2;}
			if(!updateFam.getLastName().equals(familyMap.get("hohln"))) {updateFam.setHOHFirstName(familyMap.get("hohln")); cc = cc | 4;} 
			if(!updateFam.getHouseNum().equals(familyMap.get("housenum"))) {updateFam.setHouseNum(familyMap.get("housenum")); cc = cc | 8;}
			if(!updateFam.getStreet().equals(streetname)) {updateFam.setStreet(streetname); cc = cc | 16;}
			if(!updateFam.getUnit().equals(familyMap.get("unit"))) {updateFam.setUnitNum(familyMap.get("unit")); cc = cc | 32;}
			if(!updateFam.getCity().equals(familyMap.get("city"))) {updateFam.setCity(familyMap.get("city")); cc = cc | 64;}
			if(!updateFam.getZipCode().equals(familyMap.get("zipcode"))) {updateFam.setZipCode(familyMap.get("zipcode")); cc = cc | 128;}
			
			//set the alternate delivery address for the update, then compare to the original address to 
			//detect a change
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
		
			if(!updateFam.getSubstituteDeliveryAddress().equals(editedFam.getSubstituteDeliveryAddress())) {cc = cc | 256; }
		
			//create the home and cell phones, then check the home and alt phones to see if they have changed.
			//check the email and detail fields as well
			updateFam.setHomePhone(familyMap.get("homephone"));
			
			if(familyMap.get("altphone").equals(""))
				updateFam.setOtherPhon(familyMap.get("cellphone"));
			else
				updateFam.setOtherPhon(familyMap.get("cellphone") +"\n" + familyMap.get("altphone"));
		
			if(!updateFam.getHomePhone().equals(editedFam.getHomePhone())) {cc = cc | 512; }
			if(!updateFam.getCellPhone().equals(editedFam.getCellPhone())) {cc = cc | 1024; }
			if(!updateFam.getEmail().equals(familyMap.get("email"))) {updateFam.setFamilyEmail(familyMap.get("email")); cc = cc | 2048;}
			if(!updateFam.getDetails().equals(familyMap.get("detail"))) {updateFam.setDetails(familyMap.get("detail")); cc = cc | 4096;}	
			
			//test to see if a change was detected, if so, process it
			if(cc > 0)
			{	
				//changes were detected, update the family in the db
				ONCFamily updatedFamily = serverFamilyDB.update(year, updateFam, wc, true, "Agent updated family info");
				if(updatedFamily != null)
				{
					//successfully updated family. Notify the desktop clients so they refresh
					Gson gson = new Gson();
					String mssg;
					mssg = "UPDATED_FAMILY" + gson.toJson(updatedFamily, ONCFamily.class);
					clientMgr.notifyAllInYearClients(year, mssg);
					return new ResponseCode(updateFam.getLastName() + " Family Update Accepted", null, null);
				}
				else
					return new ResponseCode("Family Referral Rejected: Unable to Save Update", null, null);
			}
			else
				return new ResponseCode("Family Referral Unchanged: No change was detected", null, null);	
		}
		else
			return new ResponseCode("Family Referral Rejected: Family Not Found in Database", null, null);	
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
			bAddressGood = ServerRegionDB.isAddressValid(chkAddress);
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
			bAddressGood = ServerRegionDB.isAddressValid(new Address(houseNum, streetName, "", "", zipCode));
		}
		
		return bAddressGood;
	}
/*	
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
	
	String getWishOptionsHTML(int wn)
	{
		return ServerWishCatalog.getWishHTMLOptions(wn);
	}
*/	
}
