package oncserver;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ourneighborschild.Address;
import ourneighborschild.AddressValidation;
import ourneighborschild.GiftCollection;
import ourneighborschild.ONCPartner;
import ourneighborschild.ONCServerUser;
import ourneighborschild.ONCUser;
import ourneighborschild.Region;
import ourneighborschild.UserPermission;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsExchange;
import com.google.gson.Gson;

public class ONCWebHttpHandler extends ONCWebpageHandler
{
	private static final int STATUS_CONFIRMED = 5;
	
	public void handle(HttpExchange te) throws IOException 
    {
		HttpsExchange t = (HttpsExchange) te;
		Map<String, Object> params = (Map<String, Object>)t.getAttribute("parameters");
    	
//		Set<String> keyset = params.keySet();
//		for(String key:keyset)
//			System.out.println(String.format("uri=%s, key=%s, value=%s", t.getRequestURI().toASCIIString(), key, params.get(key)));
    	
		String requestURI = t.getRequestURI().toASCIIString();
    	
		String mssg = String.format("HTTP request %s: %s:%s", t.getRemoteAddress().toString(), t.getRequestMethod(), requestURI);
		ServerUI serverUI = ServerUI.getInstance();
		serverUI.addLogMessage(mssg);

		if(requestURI.contains("/startpage"))
    		{
    			WebClient wc;
    			if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)
    			{
    				String response = getHomePageHTML(wc, "");
    				sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
    			}
    			else
    			{	
    				//send the user back to the ONC general web site
    				Headers header = t.getResponseHeaders();
    				ArrayList<String> headerList = new ArrayList<String>();
    				headerList.add("http://www.ourneighborschild.org");
    				header.put("Location", headerList);
    				sendHTMLResponse(t, new HtmlResponse("", HttpCode.Redirect));
    			}	
    		}
    		else if(t.getRequestURI().toString().contains("/dbStatus"))
    		{
    			//check to see that it's an active, authenticated session. If so, send db status
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
    				sendHTMLResponse(t, DBManager.getDatabaseStatusJSONP((String) params.get("callback")));
    			else
    			{
    				//handle an imposter or a timed-out client
    			}
    		}
    		else if(requestURI.contains("/agents"))
    		{
    			int year = Integer.parseInt((String) params.get("year"));
    			int groupID = Integer.parseInt((String) params.get("groupid"));
    		
    			WebClient wc;
    			HtmlResponse htmlResponse;
    		
    			if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)
    				htmlResponse = ServerFamilyDB.getAgentsWhoReferredJSONP(year, wc.getWebUser(), groupID, (String) params.get("callback"));
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error Message", (String)params.get("callback"));
   
    			sendHTMLResponse(t, htmlResponse);	
    		}
    		else if(requestURI.contains("/groups"))
    		{
    			WebClient wc;
    			HtmlResponse htmlResponse;
    		
    			if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)
    			{
    				int agentID = Integer.parseInt((String) params.get("agentid"));
    	    		
        			//test to see if a default value should be added to the top of the group list
        			boolean bDefault = false;
        			if(params.containsKey("default"))
        			{
        				String includeDefault = (String) params.get("default");
        				bDefault = includeDefault.equalsIgnoreCase("on") ? true : false;
        			}
    				htmlResponse = ServerGroupDB.getGroupListJSONP(wc.getWebUser(), agentID, bDefault, (String) params.get("callback"));
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error Message", (String)params.get("callback"));
    				
    			sendHTMLResponse(t, htmlResponse);	
    		}
/*    	
    		else if(requestURI.contains("/families"))
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
*/    	
    		else if(requestURI.contains("/partners"))
    		{
//    		Set<String> keyset = params.keySet();
//			for(String key:keyset)
//				System.out.println(String.format("/updateuser key=%s, value=%s", key, params.get(key)));
			
    			int year = Integer.parseInt((String) params.get("year"));
    		
    			HtmlResponse response = ServerPartnerDB.getPartnersJSONP(year, (String) params.get("callback"));
    			sendHTMLResponse(t, response);
    		}
    		else if(requestURI.contains("/regions"))
    		{
//    		Set<String> keyset = params.keySet();
//			for(String key:keyset)
//				System.out.println(String.format("/updateuser key=%s, value=%s", key, params.get(key)));
    		
    			String zipCode = (String) params.get("zipcode");
    		
    			HtmlResponse response = RegionDB.getAddressesJSONP(zipCode, (String) params.get("callback"));
    			sendHTMLResponse(t, response);
    		}
    		else if(requestURI.contains("/zipcodes"))
    		{
//    		Set<String> keyset = params.keySet();
//			for(String key:keyset)
//				System.out.println(String.format("/updateuser key=%s, value=%s", key, params.get(key)));

    			HtmlResponse response = RegionDB.getZipCodeJSONP((String) params.get("callback"));
    			sendHTMLResponse(t, response);
    		}
    		else if(requestURI.contains("/getagent"))
    		{
    			int agtID = Integer.parseInt((String) params.get("agentid"));
    			HtmlResponse response = ServerUserDB.getAgentJSONP(agtID, (String) params.get("callback"));
    			sendHTMLResponse(t, response);
    		}
    		else if(requestURI.contains("/getstatus"))
    		{
    			HtmlResponse htmlResponse;
    			
    			WebClient wc = clientMgr.findAndValidateClient(t.getRequestHeaders());
    			if(wc != null)
    				htmlResponse = ClientManager.getUserStatusJSONP(wc, (String) params.get("callback"));
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String) params.get("callback"));
    				
    			sendHTMLResponse(t, htmlResponse);
    		}
    		else if(requestURI.contains("/getuser"))
    		{
    			WebClient wc;
    			HtmlResponse htmlResponse;
    		
    			if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)
    				htmlResponse = ClientManager.getClientJSONP(wc, (String) params.get("callback"));
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String) params.get("callback"));
    			
    			sendHTMLResponse(t, htmlResponse);
    		}
    		else if(requestURI.contains("/updateuser"))
    		{	
    			String responseJson;
    			WebClient wc;
    		
    			//determine if a valid request from a logged in user. If so, process the update
    			if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)
    			{
//    			Set<String> keyset = params.keySet();
//    			for(String key:keyset)
//    				System.out.println(String.format("/updateuser key=%s, value=%s", key, params.get(key)));
    			
    				ServerUserDB userDB = ServerUserDB.getInstance();
    				ONCServerUser updatedUser = userDB.updateProfile(wc.getWebUser(), params);
    			
    				if(updatedUser != null)	//test to see if the update was required and successful
    				{
    					//if successful, notify all Clients of update and return a message to the web user
    					Gson gson = new Gson();
    					clientMgr.notifyAllClients("UPDATED_USER" + gson.toJson(new ONCUser(updatedUser), 
    											ONCUser.class));
    				
    					//return response
    					responseJson =  "{\"message\":\"User Profile Review/Update Sucessful\"}";
    				}
    				else	//no change detected 
    					responseJson =  "{\"message\":\"User Profile Unchanged, No Change Made\"}";
    			}
    			else	//invalid user, return a failure message
    				responseJson =  "{\"message\":\"Invalid User\"}";
    		
    			HtmlResponse htmlresponse = new HtmlResponse((String) params.get("callback") +"(" + responseJson +")", 
    					HttpCode.Ok);
    			sendHTMLResponse(t, htmlresponse);
    		}
    		else if(requestURI.contains("/getpartner"))
    		{
    			//update the client time stamp
    			WebClient wc;
    			HtmlResponse htmlResponse;
    		
    			if((wc=clientMgr.findClient((String) params.get("token"))) != null)	
    			{
    				wc.updateTimestamp();
    				int year = Integer.parseInt((String) params.get("year"));
    				String partnerID = (String) params.get("partnerid");
        		
    				htmlResponse = ServerPartnerDB.getPartnerJSONP(year, partnerID, (String) params.get("callback"));
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String) params.get("callback"));
    				
    			sendHTMLResponse(t, htmlResponse);
    		}
    		else if(requestURI.contains("/getregion"))
    		{
    			//update the client time stamp
    			WebClient wc;
    			HtmlResponse htmlResponse;
    		
    			if((wc=clientMgr.findClient((String) params.get("token"))) != null)	
    			{
    				wc.updateTimestamp();
    				String regionID = (String) params.get("regionid");
        		
    				htmlResponse = RegionDB.getRegionJSONP(regionID, (String) params.get("callback"));
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String) params.get("callback"));
    				
    			sendHTMLResponse(t, htmlResponse);
    		}
    		else if(requestURI.contains("/profileunchanged"))
    		{	
    			String responseJson;
    			WebClient wc;
    		
    			//determine if a valid request from a logged in user. If so, process the update
    			if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null) 
    			{
//    			Set<String> keyset = params.keySet();
//    			for(String key:keyset)
//    				System.out.println(String.format("/updateuser key=%s, value=%s", key, params.get(key)));
    			
    				ServerUserDB userDB = ServerUserDB.getInstance();
    				ONCServerUser updatedUser = userDB.reviewedProfile(wc.getWebUser());
    			
    				if(updatedUser != null)	//test to see if the status update was required and successful
    				{
    					//if successful, notify all Clients of update and return a message to the web user
    					Gson gson = new Gson();
    					clientMgr.notifyAllClients("UPDATED_USER" + gson.toJson(new ONCUser(updatedUser), 
    											ONCUser.class));
    				
    					//return response
    					responseJson =  "{\"message\":\"User Profile Reviewed\"}";
    				}
    				else	//no change detected 
    					responseJson =  "{\"message\":\"User Profile Unchanged, No Change Made\"}";
    			}
    			else	//invalid user, return a failure message
    				responseJson =  "{\"message\":\"Invalid User\"}";
    		
    			HtmlResponse htmlresponse = new HtmlResponse((String) params.get("callback") +"(" + responseJson +")", 
					HttpCode.Ok);
    			sendHTMLResponse(t, htmlresponse);
    		}
    		else if(requestURI.contains("/getmeal"))
    		{
    			HtmlResponse htmlResponse;
    		
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
    			{
    				int year = Integer.parseInt((String) params.get("year"));
        			String targetID = (String) params.get("mealid");
    				htmlResponse = ServerMealDB.getMealJSONP(year, targetID, (String) params.get("callback"));
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String) params.get("callback"));
    				
    			sendHTMLResponse(t, htmlResponse);
    		}
    		else if(requestURI.contains("/children"))
    		{
    			HtmlResponse htmlResponse;
    			
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
    			{
    				int year = Integer.parseInt((String) params.get("year"));
        			int famID = ServerFamilyDB.getFamilyID(year, (String) params.get("targetid"));
    				htmlResponse =  ServerChildDB.getChildrenInFamilyJSONP(year, famID, (String) params.get("callback"));
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String) params.get("callback"));
    				
    			sendHTMLResponse(t, htmlResponse);
    		}
    		else if(requestURI.contains("/wishes"))
    		{
    			//update the client time stamp
    			HtmlResponse htmlResponse;
    		
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
    			{	
    				int year = Integer.parseInt((String) params.get("year"));
    				int childID = Integer.parseInt((String) params.get("childid"));
    		
    				htmlResponse = ServerChildDB.getChildWishesJSONP(year, childID, (String) params.get("callback"));
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String)params.get("callback"));
    				
    			sendHTMLResponse(t, htmlResponse);
    		}
    		else if(requestURI.contains("/adults"))
    		{
    			HtmlResponse htmlResponse;
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)	
    			{
    				int year = Integer.parseInt((String) params.get("year"));
    				int famID = ServerFamilyDB.getFamilyID(year, (String) params.get("targetid"));
    		
    				htmlResponse = ServerAdultDB.getAdultsInFamilyJSONP(year, famID, (String) params.get("callback"));
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String)params.get("callback"));
        			
    			sendHTMLResponse(t, htmlResponse);
    		}
    		else if(requestURI.contains("/activities"))
    		{
    			HtmlResponse htmlResponse;
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
    			{
    				int year = Integer.parseInt((String) params.get("year"));
    				htmlResponse = ServerActivityDB.getActivitesJSONP(year, (String) params.get("callback"));
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String)params.get("callback"));
    			
    			sendHTMLResponse(t, htmlResponse);
    		}
    		else if(requestURI.contains("/activitydays"))
    		{
    			HtmlResponse htmlResponse;
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
    			{
    				int year = Integer.parseInt((String) params.get("year"));
    				htmlResponse = ServerActivityDB.getActivityDayJSONP(year, (String) params.get("callback"));
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String)params.get("callback"));
        			
    			sendHTMLResponse(t, htmlResponse);
    		}
    		else if(requestURI.contains("/contactinfo"))
    		{
    			int year = Integer.parseInt((String) params.get("year"));
    			String fn = (String) params.get("delFN") != null ? (String) params.get("delFN") : "";
    			String ln = (String) params.get("delLN") != null ? (String) params.get("delLN") : "";
    			String cell = (String) params.get("cell") != null ? (String) params.get("cell") : "";
    			String callback = (String) params.get("callback");
    		
    			HtmlResponse response = ServerVolunteerDB.getVolunteerJSONP(year, fn, ln, cell, callback);
    			sendHTMLResponse(t, response);
    		}
    		else if(requestURI.contains("/address"))
    		{
    			HtmlResponse htmlResponse;
    		
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)	
    				htmlResponse = verifyAddress(params);	//verify the address and send response
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error Message", (String)params.get("callback"));
    		
    			sendHTMLResponse(t, htmlResponse);
    		}
    		else if(requestURI.contains("/partnertable"))
    		{
    			String response = null;
    			WebClient wc;
    			
    			if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)
    				response = getPartnerTableWebpage(wc, "");	
    			else
    				response = invalidTokenReceived();
    		
    			sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
    		}
    		else if(requestURI.contains("/partnerupdate"))
    		{
    			String response;
    		
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
    				response = webpageMap.get("updatepartner");
    			else
    				response = invalidTokenReceived();
    		
    			sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
    		}
    		else if(requestURI.contains("/updatepartner"))
    		{
    			String sessionID = (String) params.get("token");
    			String response = null;
    			WebClient wc;
    		
    			if(t.getRequestMethod().toLowerCase().equals("post") && 
    					params.containsKey("token") && params.containsKey("year") &&
    						(wc=clientMgr.findClient(sessionID)) != null) 
    			{
    				wc.updateTimestamp();
//				Set<String> keyset = params.keySet();
//				for(String key:keyset)
//				System.out.println(String.format("/referfamily key=%s, value=%s", key, params.get(key)));
    		
    				ResponseCode rc = processPartnerUpdate(wc, params);
    			
    				//submission processed, send the partner table page back to the user
    				String userFN;
    				if(wc.getWebUser().getFirstName().equals(""))
    					userFN = wc.getWebUser().getLastName();
    				else
    					userFN = wc.getWebUser().getFirstName();
    			
    				//read the partner table web page
    				try
    				{
    					response = readFile(String.format("%s/%s",System.getProperty("user.dir"), PARTNER_TABLE_HTML));
    					response = response.replace("USER_NAME", userFN);
    					response = response.replace("USER_MESSAGE", rc.getMessage());
    					response = response.replace("REPLACE_TOKEN", wc.getSessionID().toString());
    					response = response.replace("HOME_LINK_VISIBILITY", getHomeLinkVisibility(wc));
    				}
    				catch (IOException e) 
    				{
    					response =  "<p>Partner Table Unavailable</p>";
    				}
    			
//				response = getHomePageHTML(wc, userFN, frc.getMessage(), (String) params.get("year"),
//				frc.getFamRef());
    			}
    			else
    				response = invalidTokenReceived();
    		
    			sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
    		}
    		else if(requestURI.contains("/regiontable"))
    		{
    			String response = null;
    			WebClient wc;
    			
    			if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)
    			{
    				wc.updateTimestamp();
    				response = webpageMap.get("regiontable");
    				response = response.replace("USER_MESSAGE", "");
				response = response.replace("REPLACE_TOKEN", wc.getSessionID().toString());
    			}
    			else
    				response = invalidTokenReceived();
    		
    			sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
    		}
    	else if(requestURI.contains("/regionupdate"))
    	{
    		String sessionID = (String) params.get("token");
    		String response = null;
    		WebClient wc;
    		
    		if((wc=clientMgr.findClient(sessionID)) != null)
    		{
    			wc.updateTimestamp();
    			response = webpageMap.get("updateregion");
    		}
    		else
    			response = invalidTokenReceived();
    		
    		sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
    	}
    	else if(requestURI.contains("/updateregion"))
    	{
    		String sessionID = (String) params.get("token");
    		String response = null;
    		WebClient wc;
    		
    		if(t.getRequestMethod().toLowerCase().equals("post") && 
    			params.containsKey("token") && params.containsKey("year") &&
    			 (wc=clientMgr.findClient(sessionID)) != null) 
    		{
    			wc.updateTimestamp();
    	//		Set<String> keyset = params.keySet();
    	//		for(String key:keyset)
    	//			System.out.println(String.format("/referfamily key=%s, value=%s", key, params.get(key)));
    		
    			//process the region request
    			ResponseCode rc = processRegionUpdate(wc, params);
    			
    			//submission processed, send the region table page back to the user
    			response = webpageMap.get("regiontable");
    			response = response.replace("USER_MESSAGE", rc.getMessage());
    			response = response.replace("REPLACE_TOKEN", wc.getSessionID().toString());
    		}
    		else
    			response = invalidTokenReceived();
    		
    		sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
    	}
    	else if(requestURI.equals("/volunteerregistration"))
    	{
    		String response = webpageMap.get("volunteerregistration");
       		response = response.replace("ERROR_MESSAGE", "Please ensure all fields are complete prior to submission");

    		sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
    	}
    	else if(requestURI.equals("/registervolunteer"))
    	{
//			Set<String> keyset = params.keySet();
//			for(String key:keyset)
//				System.out.println(String.format("Key=%s, value=%s", key, (String)params.get(key)));

    		int year = Integer.parseInt((String)params.get("year"));
    		String callbackFunction = (String) params.get("callback");
    		
    		//create the volunteer key map
    		String[] volKeys = {"delFN", "delLN", "groupother", "delhousenum", "delstreet", 
    		    				"delunit", "delcity", "delzipcode", "primaryphone", "delemail",
    		    				"group", "comment"};
    		Map<String, String> volParams = createMap(params, volKeys);
    		
    		//create the activity key map
    		List<String> activityKeyList = new ArrayList<String>();
    		for(int i=0 ; i<ServerActivityDB.size(year); i++)
    			if(params.containsKey("actckbox" + Integer.toString(i)))
    			{
    				activityKeyList.add("actckbox" + Integer.toString(i));
    				activityKeyList.add("actcomment" + Integer.toString(i));
    			}
    		
    		String[] activityKeys = new String[activityKeyList.size()];
    		activityKeys= activityKeyList.toArray(activityKeys);
    		Map<String, String> activityParams = createMap(params, activityKeys);
    		
//    		Set<String> actkeyset = activityParams.keySet();
//			for(String key:actkeyset)
//				System.out.println(String.format("Act Key=%s, act value=%s", key, (String)activityParams.get(key)));
    			
    		HtmlResponse htmlResponse = ServerVolunteerDB.addVolunteerJSONP(year, volParams, activityParams, 
    										false, "Volunteer Registration Webpage", callbackFunction);
    		sendHTMLResponse(t, htmlResponse); 
    	}
    	else if(requestURI.equals("/driversignin"))
    	{
    		String response = webpageMap.get("driversignin");
    		response = response.replace("ERROR_MESSAGE", "Please ensure all fields are complete prior to submission");

    		sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
    	}
    	else if(requestURI.equals("/signindriver"))
    	{
//			Set<String> keyset = params.keySet();
//   		for(String key:keyset)
//   			System.out.println(String.format("Key=%s, value=%s", key, (String)params.get(key)));

    		int year = Integer.parseInt((String)params.get("year"));
    		String callbackFunction = (String) params.get("callback");
    		    		
    		String[] volKeys = {"delFN", "delLN", "groupother", "delhousenum", "delstreet", 
    		    				"delunit", "delcity", "delzipcode", "primaryphone", "delemail",
    		    				"group", "comment", "activity"};
    		    		
    		Map<String, String> volParams = createMap(params, volKeys);
    		
    		Map<String, String> activityParams = new HashMap<String, String>();
    		activityParams.put("actckbox0",volParams.get("activity"));
    		activityParams.put("actcomment0","New delivery volunteer on delivery day");
    		    		
    		HtmlResponse htmlResponse = ServerVolunteerDB.addVolunteerJSONP(year, volParams, activityParams,
    										true, "Delivery Day Registration Webpage", callbackFunction);
    		sendHTMLResponse(t, htmlResponse); 
    	}
    	else if(requestURI.equals("/volunteersignin"))
    	{
    		String response = webpageMap.get("volunteersignin");
    		response = response.replace("ERROR_MESSAGE", "Please ensure all fields are complete prior to submission");
    		
    		sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
    	}
    	else if(requestURI.contains("/signinvolunteer"))
    	{
 //   		Set<String> keyset = params.keySet();
 //   		for(String key:keyset)
 //   			System.out.println(String.format("Key=%s, value=%s", key, (String)params.get(key)));

    		int year = Integer.parseInt((String)params.get("year"));
    		String callbackFunction = (String) params.get("callback");
    		
    		String[] volKeys = {"delFN", "delLN", "groupother", "delhousenum", "delstreet", 
    							"delunit", "delcity", "delzipcode", "primaryphone", "delemail",
    							"group", "comment", "activity"};
    		
    		Map<String, String> volParams = createMap(params, volKeys);
    		
    		Map<String, String> activityParams = new HashMap<String, String>();
    		activityParams.put("actckbox0", volParams.get("activity"));
    		activityParams.put("actcomment0","New volunteer registered in warehouse");
    		    		
    		HtmlResponse htmlResponse = ServerVolunteerDB.addVolunteerJSONP(year,  volParams, activityParams,
    										true, "Warehouse Registration Webpage", callbackFunction);
    		
    		sendHTMLResponse(t, htmlResponse);  
    	}
    	else if(requestURI.contains("/changepw"))	//from separate page
    	{
    		String sessionID = (String) params.get("token");
    		String response = null;
    		WebClient wc;
    		
    		if(t.getRequestMethod().toLowerCase().equals("post") && params.containsKey("token") &&
    				(wc=clientMgr.findClient(sessionID)) != null)
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
    				response = getHomePageHTML(wc, "Your password change was successful!");
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
    		
    		sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
    	}
    	else if(requestURI.contains("/reqchangepw"))	//from web dialog box
    	{
    		String response;
    		WebClient wc;
    		
    		//determine if a valid request from a logged in user. If so, process the pw change
    		if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)
    		{
//    		Set<String> keyset = params.keySet();
//    		for(String key:keyset)
//    			System.out.println(String.format("/updateuser key=%s, value=%s", key, params.get(key)));
    			
    			ServerUserDB userDB = ServerUserDB.getInstance();
    			int retCode = userDB.changePassword((String) params.get("field1"), (String) params.get("field2"), wc);
    			
    			if(retCode == 0)
    			{
    				//submission successful, send the family table page back to the user
    				String userFN = wc.getWebUser().getFirstName().equals("") ? 
        				 wc.getWebUser().getLastName() : wc.getWebUser().getFirstName();
    				response =  String.format("%s, your password was successfully changed!", userFN);
    			}
    			else if(retCode == -1)
    			{
    				response = "Password Change Failed: Current password incorrect";		
    			}
    			else
    			{
    				response = "Password Change Failed: User couldn't be located";
    			}    			
    		}
    		else	//invalid user, return a failure message
    			response =  "Invalid Session ID";
    		
    		HtmlResponse htmlresponse = new HtmlResponse(response, HttpCode.Ok);
    		sendHTMLResponse(t, htmlresponse);
    	}
    	else if(requestURI.contains("/dashboard"))
    	{
    		String response = "Invalid Session ID";;
    		WebClient wc;
    		
    		if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)
    		{
    			String userFN;
        		if(wc.getWebUser().getFirstName().equals(""))
        			userFN = wc.getWebUser().getLastName();
        		else
        			userFN = wc.getWebUser().getFirstName();
        		
        		//determine which home page, elf or agent
        		if(wc.getWebUser().getPermission() == UserPermission.Admin ||
        				wc.getWebUser().getPermission() == UserPermission.Sys_Admin)
        		{
        			//read the onc page html
        			try
        			{
        				response = readFile(String.format("%s/%s",System.getProperty("user.dir"), DASHBOARD_HTML));
        				response = response.replace("USER_NAME", userFN);
        				response = response.replace("USER_MESSAGE", "");
        				response = response.replaceAll("REPLACE_TOKEN", wc.getSessionID().toString());
        				response = response.replace("REPLACE_YEAR", "2017");
        			}
        			catch (IOException e) 
        			{
        				response = "<p>ONC Family Page Unavailable</p>";
        			}
        		}
    		}
    		
    		HtmlResponse htmlresponse = new HtmlResponse(response, HttpCode.Ok);
    		sendHTMLResponse(t, htmlresponse);
    	}			
    }

	HtmlResponse verifyAddress(Map<String, Object> params)
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
		
//		return callback +"(" + json +")";
		return new HtmlResponse(callback +"(" + json +")", HttpCode.Ok);
	}
	
	ResponseCode processPartnerUpdate(WebClient wc, Map<String, Object> params)
	{	
		//get the year
		int year = Integer.parseInt((String) params.get("year"));
		String partnerID = (String) params.get("partnerid");
				
		//get database references
		ServerPartnerDB serverPartnerDB= null;
				
		try
		{
			serverPartnerDB = ServerPartnerDB.getInstance();
		} 
		catch (FileNotFoundException e) 
		{
			e.printStackTrace();
		}
		catch (IOException e) 
		{
			e.printStackTrace();
		}
		
		String[] partnerKeys = {"partnerid", "firstname", "lastname", "type", "status", "collection", 
				"housenum", "street", "unit", "city", "zipcode", "phone",
				"firstcontactname", "firstcontactemail", "firstcontactphone",
				"secondcontactname", "secondcontactemail", "secondcontactphone",
				"genTA", "cyTA", "delTA", "cyReq"};
		
		Map<String, String> partnerMap = createMap(params, partnerKeys);
		
		String partName = partnerMap.get("lastname").trim();
		if(!partnerMap.get("firstname").isEmpty())
			partName = partName + ", " + partnerMap.get("firstname").trim();
		
		int orn_req = 0;
		if(!partnerMap.get("cyReq").isEmpty())
			orn_req = Integer.parseInt(partnerMap.get("cyReq"));
		
//		Set<String> keyset = partnerMap.keySet();
//		for(String key:keyset)
//			System.out.println(String.format("partnerMap key=%s, value=%s", key, partnerMap.get(key)));
		
		ResponseCode rc = null;
		ONCPartner returnedPartner = null;
		Gson gson = new Gson();
		String mssg;
		//determine if its an add partner request or a partner update request
		if(partnerID.equals("New"))
		{			
			ONCPartner addPartner = new ONCPartner(-1, new Date(),
					wc.getWebUser().getLNFI(), 3,
					"New partner", wc.getWebUser().getLNFI(), 
					ONCWebPartner.getStatus(partnerMap.get("status")),
					ONCWebPartner.getType(partnerMap.get("type")),
					GiftCollection.valueOf(partnerMap.get("collection")), partName,
					partnerMap.get("housenum"), partnerMap.get("street"), partnerMap.get("unit"),
					partnerMap.get("city"), partnerMap.get("zipcode"), partnerMap.get("phone"),
					orn_req, partnerMap.get("genTA"), partnerMap.get("delTA"), partnerMap.get("cyTA"), 
					partnerMap.get("firstcontactname"), partnerMap.get("firstcontactemail"), partnerMap.get("firstcontactphone"),
					partnerMap.get("secondcontactname"), partnerMap.get("secondcontactemail"), partnerMap.get("secondcontactphone"));
			
			returnedPartner = serverPartnerDB.add(year, addPartner);
			
			if(returnedPartner != null)
			{
				rc = new ResponseCode(String.format("Partner %d, %s successfully added to the database", 
									returnedPartner.getID(), returnedPartner.getLastName()));
				mssg = "ADDED_PARTNER" + gson.toJson(returnedPartner, ONCPartner.class);
				clientMgr.notifyAllInYearClients(year, mssg);
			}
			else
				rc = new ResponseCode("Partner was unable to be added to the database");
		}
		else
		{
			//get current partner from the DB and update the fields from the web page submit
			//only allow status or ornament request changes if not CONFIMED and > 0
			ONCPartner currPartner = serverPartnerDB.getPartner(year, Integer.parseInt(partnerID));
			if(currPartner != null)
			{
				ONCPartner updatePartner = new ONCPartner(currPartner);
				updatePartner.setDateChanged(new Date());
				updatePartner.setChangedBy(wc.getWebUser().getLNFI());
				updatePartner.setStoplightPos(3);
				updatePartner.setStoplightMssg("Update Partner via website");
				updatePartner.setStoplightMssg(wc.getWebUser().getLNFI());
				updatePartner.setType(ONCWebPartner.getType(partnerMap.get("type")));
				updatePartner.setGiftCollectionType(GiftCollection.valueOf(partnerMap.get("collection")));
				updatePartner.setLastName(partName);
				updatePartner.setHouseNum(partnerMap.get("housenum"));
				updatePartner.setStreet(partnerMap.get("street"));
				updatePartner.setUnit(partnerMap.get("unit"));
				updatePartner.setCity(partnerMap.get("city"));
				updatePartner.setZipCode(partnerMap.get("zipcode"));	
				updatePartner.setHomePhone(partnerMap.get("phone"));
				
				if(!(currPartner.getStatus() == STATUS_CONFIRMED && 
					currPartner.getNumberOfOrnamentsRequested() > 0))
				{
					updatePartner.setStatus(ONCWebPartner.getStatus(partnerMap.get("status")));
					updatePartner.setNumberOfOrnamentsRequested(orn_req);
				}
				
				updatePartner.setOther(partnerMap.get("genTA"));
				updatePartner.setDeliverTo(partnerMap.get("delTA"));
				updatePartner.setSpecialNotes(partnerMap.get("cyTA"));
				updatePartner.setContact(partnerMap.get("firstcontactname"));
				updatePartner.setContact_email(partnerMap.get("firstcontactemail"));
				updatePartner.setContact_phone(partnerMap.get("firstcontactphone"));
				updatePartner.setContact2(partnerMap.get("secondcontactname"));
				updatePartner.setContact2_email(partnerMap.get("secondcontactemail"));
				updatePartner.setContact2_phone(partnerMap.get("secondcontactphone"));
			
				returnedPartner = serverPartnerDB.update(year, updatePartner);
			
				if(returnedPartner != null)
				{
					rc = new ResponseCode(String.format("Partner %d, %s successfully updated", 
									returnedPartner.getID(), returnedPartner.getLastName()));
				
					mssg = "UPDATED_PARTNER" + gson.toJson(returnedPartner, ONCPartner.class);
					clientMgr.notifyAllInYearClients(year, mssg);
				}
				else
					rc = new ResponseCode(String.format("No changes detected for %s", currPartner.getLastName()));
			}
			else
				rc = new ResponseCode("Partner was not found in the database");
		}

		return rc;	
	}
	
	ResponseCode processRegionUpdate(WebClient wc, Map<String, Object> params)
	{				
		//get database references
		RegionDB regionDB= null;
				
		try
		{
			regionDB = RegionDB.getInstance(null);
		} 
		catch (FileNotFoundException e) 
		{
			e.printStackTrace();
		}
		catch (IOException e) 
		{
			e.printStackTrace();
		}
		
		String[] regionKeys = {"regionid", "dir", "street", "type", "postdir", "zipcode", 
								"region", "addlo", "addhi"};
		Map<String, String> regionMap = createMap(params, regionKeys);
		
//		Set<String> keyset = regionMap.keySet();
//		for(String key:keyset)
//			System.out.println(String.format("regionMap key=%s, value=%s", key, regionMap.get(key)));
		
		
		String[] regionLine = new String[10];
		regionLine[0] = regionMap.get("regionid").equals("New") ? "-1" : regionMap.get("regionid");
		regionLine[1] = regionMap.get("street").trim().toUpperCase();
		regionLine[2] = regionMap.get("type");
		regionLine[3] = regionMap.get("region");
		regionLine[4] = regionMap.get("dir");
		regionLine[5] = regionMap.get("postdir");
		regionLine[6] = regionMap.get("addlo").trim();
		regionLine[7] = regionMap.get("addhi").trim();
		regionLine[8] = "";
		regionLine[9] = regionMap.get("zipcode");
		
		Region returnedRegion = new Region(regionLine);
		
		ResponseCode rc = null;
//		Gson gson = new Gson();
//		String mssg;
		//determine if its an add partner request or a partner update request
		if(regionMap.get("regionid").equals("New"))
		{
			Region addedRegion = regionDB.add(returnedRegion);
			
			if(addedRegion != null)
			{
				rc = new ResponseCode(String.format("Region %d, %s successfully added to the database", 
									addedRegion.getID(), addedRegion.getStreetName()));
//				mssg = "ADDED_REGION" + gson.toJson(addedRegion, Region.class);
//				clientMgr.notifyAllInYearClients(year, mssg);
			}
			else
				rc = new ResponseCode("Region was unable to be added to the database");
		}
		else
		{
			Region updatedRegion = regionDB.update(returnedRegion);
			if(updatedRegion != null)
			{
				rc = new ResponseCode(String.format("Region %d, %s successfully updated", 
										updatedRegion.getID(), updatedRegion.getStreetName()));
				
//				mssg = "UPDATED_REGION" + gson.toJson(updatedRegion, Region.class);
//				clientMgr.notifyAllInYearClients(year, mssg);
			}
			else
				rc = new ResponseCode("Region was not found in the database or was unchanged");
		}
		
		return rc;	
	}
}
