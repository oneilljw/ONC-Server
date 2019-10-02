package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

import ourneighborschild.Address;
import ourneighborschild.AddressValidation;
import ourneighborschild.GiftCollectionType;
import ourneighborschild.ONCPartner;
import ourneighborschild.ONCServerUser;
import ourneighborschild.ONCUser;
import ourneighborschild.Region;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsExchange;
import com.google.gson.Gson;

public class ONCWebHttpHandler extends ONCWebpageHandler
{
	private static final int RC_ADDRESS_IS_VALID = 0;
	private static final int RC_ADDRESS_IS_SCHOOL = 1;
	private static final int RC_ADDRESS_NOT_VALID = 2;
	private static final int RC_ADDRESS_MISSING_UNIT = 3;
	private static final int RC_ADDRESS_NOT_IN_SERVED_ZIPCODE = 4;
	private static final int RC_ADDRESS_NOT_IN_SERVED_PYRAMID = 5;
	
	private static final int EC_ADDRESS_NOT_VALID = 1;
	private static final int EC_ADDRESS_MISSING_UNIT = 2;
	private static final int EC_ADDRESS_NOT_IN_SERVED_ZIPCODE = 4;
	private static final int EC_ADDRESS_NOT_IN_SERVED_PYRAMID = 8;
	
	public void handle(HttpExchange te) throws IOException 
    {
		HttpsExchange t = (HttpsExchange) te;
		@SuppressWarnings("unchecked")
		Map<String, Object> params = (Map<String, Object>)t.getAttribute("parameters");
    	
		String requestURI = t.getRequestURI().toASCIIString();
    	
		String mssg = String.format("HTTP request %s: %s:%s", t.getRemoteAddress().toString(), t.getRequestMethod(), requestURI);
		ServerUI serverUI = ServerUI.getInstance();
		serverUI.addUIAndLogMessage(mssg);
		
		WebClient wc;
		HtmlResponse htmlResponse = null;
		
		if(requestURI.contains("/startpage"))
    		{
    			if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)
    			{
    				String response = getHomePageHTML(wc, "", false);
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
    			if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)
    			{
    				int year = Integer.parseInt((String) params.get("year"));
        			int groupID = Integer.parseInt((String) params.get("groupid"));
    				htmlResponse = ServerFamilyDB.getAgentsWhoReferredJSONP(year, wc.getWebUser(), groupID, (String) params.get("callback"));
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error Message", (String)params.get("callback"));
   
    			sendHTMLResponse(t, htmlResponse);	
    		}    		
    		else if(requestURI.contains("/groups"))
    		{
    			if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)
    			{
    				int agentID = Integer.parseInt((String) params.get("agentid"));
    	    		
        			//test to see if a default value should be added to the top of the group list
        			boolean bDefault = false, bProfile = false;
        			if(params.containsKey("default"))
        			{
        				String includeDefault = (String) params.get("default");
        				bDefault = includeDefault.equalsIgnoreCase("on") ? true : false;
        			}
        			if(params.containsKey("profile"))
        			{
        				String includeProfile = (String) params.get("profile");
        				bProfile = includeProfile.equalsIgnoreCase("yes") ? true : false;
        			}
    				htmlResponse = ServerGroupDB.getGroupListJSONP(wc.getWebUser(), agentID, bDefault,
    																bProfile, (String) params.get("callback"));
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error Message", (String)params.get("callback"));
    				
    			sendHTMLResponse(t, htmlResponse);	
    		}
    		else if(requestURI.contains("/volunteergroups"))
    		{
    			htmlResponse = ServerGroupDB.getVolunteerGroupListJSONP((String) params.get("callback"));
    			sendHTMLResponse(t, htmlResponse);	
    		}
    		else if(requestURI.contains("/partners"))
    		{
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null &&
    					params.containsKey("year") && params.containsKey("confirmed"))
    			{
    				int year = Integer.parseInt((String) params.get("year"));
    				String confirmed = (String) params.get("confirmed");
    				boolean bConfirmedOnly = confirmed.equalsIgnoreCase("true");
    				htmlResponse = ServerPartnerDB.getPartnersJSONP(year, bConfirmedOnly, (String) params.get("callback"));
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error Message", (String)params.get("callback"));
    			
    			sendHTMLResponse(t, htmlResponse);
    		}
    		else if(requestURI.contains("/regions"))
    		{
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
    			{
    				String zipCode = (String) params.get("zipcode");
    				htmlResponse = ServerRegionDB.getAddressesJSONP(zipCode, (String) params.get("callback"));
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error Message", (String)params.get("callback"));
    			
    			sendHTMLResponse(t, htmlResponse);
    		}
    		else if(requestURI.contains("/zipcodes"))
    		{
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
    				htmlResponse = ServerRegionDB.getZipCodeJSONP((String) params.get("callback"));
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error Message", (String)params.get("callback"));
    			
    			sendHTMLResponse(t, htmlResponse);
    		}
    		else if(requestURI.contains("/getagent"))
    		{
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
    			{
    				int agtID = Integer.parseInt((String) params.get("agentid"));
    				htmlResponse = ServerUserDB.getAgentJSONP(agtID, (String) params.get("callback"));
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error Message", (String)params.get("callback"));
    			
    			sendHTMLResponse(t, htmlResponse);
    		}
    		else if(requestURI.contains("/getstatus"))
    		{
    			if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)
    				htmlResponse = ClientManager.getUserStatusJSONP(wc, (String) params.get("callback"));
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String) params.get("callback"));
    				
    			sendHTMLResponse(t, htmlResponse);
    		}    			
    		else if(requestURI.contains("/getuser"))
    		{
    			if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)
    				htmlResponse = ClientManager.getClientJSONP(wc, (String) params.get("callback"));
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String) params.get("callback"));
    			
    			sendHTMLResponse(t, htmlResponse);
    		}		
    		else if(requestURI.contains("/updateuser"))
    		{	
    			String responseJson;
    			
    			//determine if a valid request from a logged in user. If so, process the update
    			if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)
    			{
    				ServerUserDB userDB = ServerUserDB.getInstance();
    				ONCUser updatedUser = userDB.updateProfile(wc.getWebUser(), params);
    			
    				if(updatedUser != null)	//test to see if the update was required and successful
    				{
    					//if successful, notify all Clients of update and return a message to the web user
    					Gson gson = new Gson();
    					responseJson = gson.toJson(new ONCUser(updatedUser), ONCUser.class);
    					clientMgr.notifyAllClients("UPDATED_USER" + responseJson);
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
    			if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)	
    			{
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
    			if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)	
    			{
    				String regionID = (String) params.get("regionid");
        		
    				htmlResponse = ServerRegionDB.getRegionJSONP(regionID, (String) params.get("callback"));
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String) params.get("callback"));
    				
    			sendHTMLResponse(t, htmlResponse);
    		}
    		else if(requestURI.contains("/profileunchanged"))
    		{	
    			String responseJson;
    			
    			//determine if a valid request from a logged in user. If so, process the update
    			if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null) 
    			{
    				ServerUserDB userDB = ServerUserDB.getInstance();
    				ONCServerUser updatedUser = userDB.reviewedProfile(wc.getWebUser());
    			
    				if(updatedUser != null)	//test to see if the status update was required and successful
    				{
    					//if successful, notify all Clients of update and return a message to the web user
    					Gson gson = new Gson();
    					clientMgr.notifyAllClients("UPDATED_USER" + gson.toJson(new ONCUser(updatedUser), 
    											ONCUser.class));
    				
    					//return response
    					responseJson =  "{\"message\":\"User Profile Information Validated\"}";
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
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
    			{
    				int year = Integer.parseInt((String) params.get("year"));
        			int famID = ServerFamilyDB.getFamilyID(year, (String) params.get("targetid"));
        			boolean bIncludeSchool = ((String) params.get("schools")).equalsIgnoreCase("true") ? true : false;
    				htmlResponse =  ServerChildDB.getChildrenInFamilyJSONP(year, famID,  bIncludeSchool, (String) params.get("callback"));
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String) params.get("callback"));
    				
    			sendHTMLResponse(t, htmlResponse);
    		}
    		else if(requestURI.contains("/wishes"))
    		{
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
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)	
    			{
    				int year = Integer.parseInt((String) params.get("year"));
    				int famID = ServerFamilyDB.getFamilyID(year, (String) params.get("targetid"));
    				htmlResponse = ServerAdultDB.getAdultsInFamilyJSONP(year, famID, (String) params.get("callback"));
//    				try
//					{
//						htmlResponse = ServerAdultDB.getAdultsInFamilyJSONP(year, famID, (String) params.get("callback"));
//					}
//					catch (SQLException e)
//					{
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String)params.get("callback"));
        			
    			sendHTMLResponse(t, htmlResponse);
    		}
//    		else if(requestURI.contains("/activities"))
//    		{
//    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
//    			{
//    				int year = Integer.parseInt((String) params.get("year"));
//    				htmlResponse = ServerActivityDB.getActivitesJSONP(year, (String) params.get("callback"));
//    			}
//    			else
//    				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String)params.get("callback"));
//    			
//    			sendHTMLResponse(t, htmlResponse);
//    		}
//    		else if(requestURI.contains("/activitydays"))
//    		{
//    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
//    			{
//    				int year = Integer.parseInt((String) params.get("year"));
//    				htmlResponse = ServerActivityDB.getActivityDayJSONP(year, (String) params.get("callback"));
//    			}
//    			else
//    				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String)params.get("callback"));
//        			
//    			sendHTMLResponse(t, htmlResponse);
//    		}
    		else if(requestURI.contains("/address"))
    		{
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
    			{
    				htmlResponse = verifyAddress(params);	//verify the address and send response
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error Message", (String)params.get("callback"));
    		
    			sendHTMLResponse(t, htmlResponse);
    		}
    		else if(requestURI.contains("/checkaddresses"))
    		{
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
    			{
//    			htmlResponse = verifyAddress(params);	//verify the address and send response
    				htmlResponse = verifyHoHAndDeliveryAddress(params);
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error Message", (String)params.get("callback"));
    		
    			sendHTMLResponse(t, htmlResponse);
    		}
    		else if(requestURI.contains("/partnertable"))
    		{
    			String response = null;
    			
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
    			String response;
    			
    			if(t.getRequestMethod().toLowerCase().equals("post") && params.containsKey("year") &&
    				(wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)	
    			{
    				//read the partner table web page
    				ResponseCode rc = processPartnerUpdate(wc, params);
    				response = getPartnerTableWebpage(wc, rc.getMessage());	
    			}
    			else
    				response = invalidTokenReceived();
    		
    			sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
    		}
    		else if(requestURI.contains("/regiontable"))
    		{
    			String response;
    			
    			if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)
    			{
    				response = webpageMap.get("regiontable");
    				response = response.replace("USER_MESSAGE", "");
    			}
    			else
    				response = invalidTokenReceived();
    		
    			sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
    		}
    		else if(requestURI.contains("/regionupdate"))
    		{
    			String response;
    			
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
    				response = webpageMap.get("updateregion");
    			else
    				response = invalidTokenReceived();
    		
    			sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
    		}
    		else if(requestURI.contains("/updateregion"))
    		{
    			String response;
    		
    			if(t.getRequestMethod().toLowerCase().equals("post") &&  params.containsKey("year") &&
    				(wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null) 
    			{
    				//process the region request
    				ResponseCode rc = processRegionUpdate(wc, params);
    			
    				//submission processed, send the region table page back to the user
    				response = webpageMap.get("regiontable");
    				response = response.replace("USER_MESSAGE", rc.getMessage());
    			}
    			else
    				response = invalidTokenReceived();
    		
    			sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
    		}
    		else if(requestURI.contains("/changepw"))	//from separate page
    		{
    			String response = null;
    		
    			if(t.getRequestMethod().toLowerCase().equals("post") &&
    				(wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)
    			{
    				ServerUserDB serveruserDB = ServerUserDB.getInstance();
 
    				int retCode = serveruserDB.changePassword((String) params.get("field1"),
    														(String) params.get("field2"), wc);
    			
    				if(retCode == 0)
    				{
    					//submission successful, send the family table page back to the user
    					response = getHomePageHTML(wc, "Your password change was successful!", false);
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
    		
    			//determine if a valid request from a logged in user. If so, process the pw change
    			if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)
    			{
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
    					response = "Password Change Failed: Current password incorrect";		
    				else
    					response = "Password Change Failed: User couldn't be located";  			
    			}
    			else	//invalid user, return a failure message
    				response =  "Invalid Session ID";
    		
    			HtmlResponse htmlresponse = new HtmlResponse(response, HttpCode.Ok);
    			sendHTMLResponse(t, htmlresponse);
    		}
    		else if(requestURI.contains("/dashboard"))
    		{
    			String response;
    			if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)
    				response = getDashboardWebpage(wc, "");
    			else
        			response = invalidTokenReceived();
    			
    			HtmlResponse htmlresponse = new HtmlResponse(response, HttpCode.Ok);
    			sendHTMLResponse(t, htmlresponse);
    		} 
    		else if(requestURI.contains("/giftcatalog"))
    		{
    			String response;
    			if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null  &&
    					params.containsKey("year") && params.containsKey("callback"))
    			{	
    				int year = Integer.parseInt((String) params.get("year"));
    				response = ServerGiftCatalog.getGiftCatalogJSONP(year, (String) params.get("callback"));
    			}
    			else
        			response = invalidTokenReceived();
    			
    			String callback = (String) params.get("callback");
    			HtmlResponse htmlresponse = new HtmlResponse(callback +"(" + response +")", HttpCode.Ok);
    			sendHTMLResponse(t, htmlresponse); 
    		}
    }

	HtmlResponse verifyAddress(Map<String, Object> params)
	{
		String callback = (String) params.get("callback");
		
		String[] addressKeys = {"housenum", "street", "unit", "city", "zipcode", "type"};
		Map<String, String> addressMap = createMap(params, addressKeys);
		
//		for(String key:addressMap.keySet())
//			System.out.println(String.format("ONCHttpHandler.verifyAddress: key=%s, value=%s", key, addressMap.get(key)));
		
		int chkAddressResult = checkAddress(new Address(addressMap.get("housenum"), addressMap.get("street"),
								addressMap.get("unit"), addressMap.get("city"), addressMap.get("zipcode")));
		
		Gson gson = new Gson();
		String json;
		String errMssg;
		if(chkAddressResult == RC_ADDRESS_IS_SCHOOL)	//if address is school, address is valid
		{
			if(addressMap.get("type").equals("del"))
			{
				json = gson.toJson(new AddressValidation(0, "Address is a school in school pyramids "
						+ "served by ONC"), AddressValidation.class);
			}
			else
			{
				errMssg = "Address is a school, it must be a residence in Fairfax County. "
						+ "Only the delivery address can be a school in ONC served school pyramids.";
				json = gson.toJson(new AddressValidation(1, errMssg), AddressValidation.class);
			}
		}
		else if(chkAddressResult == RC_ADDRESS_NOT_VALID)	//address is not in the database
		{	
			errMssg = "Address is not a residence in Fairfax County";
			json = gson.toJson(new AddressValidation(1, errMssg), AddressValidation.class);
		}
		else if(chkAddressResult == RC_ADDRESS_MISSING_UNIT)
		{	
			errMssg = "Address requires an apartment or unit number.";
			json = gson.toJson(new AddressValidation(2, errMssg), AddressValidation.class);
		}
		else if(chkAddressResult == RC_ADDRESS_NOT_IN_SERVED_ZIPCODE)
		{
			errMssg = "Address is not a residence in ONC's serving area";
			json = gson.toJson(new AddressValidation(3, errMssg), AddressValidation.class);
		}
		else if(chkAddressResult == RC_ADDRESS_NOT_IN_SERVED_PYRAMID)
		{	
			errMssg = "Address is not in ONC's served school pyramids.";
			json = gson.toJson(new AddressValidation(3, errMssg), AddressValidation.class);
		}
		else	//address is good to accept
			json = gson.toJson(new AddressValidation(0, "Address is a residence served by ONC"), AddressValidation.class);	

		return new HtmlResponse(callback +"(" + json +")", HttpCode.Ok);
	}
	
	HtmlResponse verifyHoHAndDeliveryAddress(Map<String, Object> params)
	{
		String callback = (String) params.get("callback");
		
		String[] addressKeys = {"housenum", "street", "unit", "city", "zipcode", "sameaddress",
								"delhousenum", "delstreet", "delunit", "delcity", "delzipcode"};
		Map<String, String> addressMap = createMap(params, addressKeys);
		
//		System.out.println(String.format("ONCHttpHandler..verifyHoHAndDelAdd: #params keys= %d", params.size()));
//		for(String key:addressMap.keySet())
//			System.out.println(String.format("ONCHttpHandler..verifyHoHAndDelAdd: key=%s, value=%s", key, addressMap.get(key)));
		
		int delAddrCheckResult = checkAddress(new Address(addressMap.get("delhousenum"), addressMap.get("delstreet"),
								addressMap.get("delunit"), addressMap.get("delcity"), addressMap.get("delzipcode")));
		
		//diagnostic print
//		System.out.println(String.format("WebHdlr.verAdd: chkAddress= %s", chkAddress.getPrintableAddress()));
		
		//First, check to see if the address is in the database. If it is, check to see if it requires
		//an apartment. it might be missing. If the apartment check passes, then 
		//check the school code. If the code is Y, then the school is in the ONC served zip codes
		//but is not in one of the three pyramids. If the code is Z, then the school is not in an onc
		//zip code. If the code is A thru S, then all the checks pass.
		Gson gson = new Gson();
		String json;
		int returnCode = 0;

		String errMssg = "";
		AddressValidation delAddrErrorResult = new AddressValidation();
		AddressValidation hohAddrErrorResult = new AddressValidation();
		
		if(delAddrCheckResult != RC_ADDRESS_IS_SCHOOL)		//if address is school, address is valid
		{
			//del address isn't school, check the result against the region street data base
			if(delAddrCheckResult > RC_ADDRESS_IS_SCHOOL)
			{
				//delivery address isn't valid and isn't a school, so process the error
				delAddrErrorResult = processAddressError(delAddrCheckResult, "Delivery");
			}
			
			//check the HoH address if not the same as the delivery address
			if(addressMap.get("sameaddress").equals("false"))
			{
				//check the HoH address as well
				int hohAddrCheckResult = checkAddress(new Address(addressMap.get("housenum"), addressMap.get("street"),
						addressMap.get("unit"), addressMap.get("city"), addressMap.get("zipcode")));
				
				if(hohAddrCheckResult > RC_ADDRESS_IS_SCHOOL) //hoh address has an error
					hohAddrErrorResult = processAddressError(hohAddrCheckResult, "HOH");
				
//				System.out.println(String.format("ONCWebHdlr: hohCkResult=%d, HohAddrErrorRC=%d, hohAddrErrorMssg=%s",
//						hohAddrCheckResult, hohAddrErrorResult.getReturnCode(), hohAddrErrorResult.getErrorMessage()));
			}
			
			//create the combined hoh and del return code. Only add the hoh return code if the hoh
			//address check had an invalid or missing unit error.
			errMssg = delAddrErrorResult.getErrorMessage();
			
			if(hohAddrErrorResult.getReturnCode() == EC_ADDRESS_NOT_VALID ||
				hohAddrErrorResult.getReturnCode() == EC_ADDRESS_MISSING_UNIT)
			{	
					returnCode = hohAddrErrorResult.getReturnCode()  << 4;
					if(delAddrErrorResult.getErrorMessage().isEmpty())
						errMssg = errMssg.concat(hohAddrErrorResult.getErrorMessage());
					else	
						errMssg = errMssg.replace(".", ", and " + hohAddrErrorResult.getErrorMessage());
			}
			
			returnCode = returnCode | delAddrErrorResult.getReturnCode();
		}
		else
		{
			//Delivery address is a school. HoH address cannot be a school, it can be anything else
			int hohAddrCheckResult = checkAddress(new Address(addressMap.get("housenum"), addressMap.get("street"),
					addressMap.get("unit"), addressMap.get("city"), addressMap.get("zipcode")));
			
			if(hohAddrCheckResult == RC_ADDRESS_IS_SCHOOL) //Error: hoh can't be a school if del address is a school
			{
				errMssg = String.format("The HoH address cannot be a school address in Fairfax County. "
						+ "Please provide the family's actual, valid Fairfax County address. To refer "
						+ "a family without a vaild Fairfax County residential address, please send an email "
						+ "to schoolcontact@ourneighborschild.org");
				
				returnCode = returnCode | EC_ADDRESS_NOT_VALID ;
			}	
		}
		
//		System.out.println(String.format("ONCWebHttpHdlr.verifyHoHAndDelAdd: rc= %d, mssg= %s", returnCode, errMssg));
		
		json = gson.toJson(new AddressValidation(returnCode, errMssg), AddressValidation.class);
		return new HtmlResponse(callback +"(" + json +")", HttpCode.Ok);
	}
	
	AddressValidation processAddressError(int delAddressResult, String addrType)
	{
		String delErrMssg = "";
		int returnCode = 0;
		
		if(delAddressResult == RC_ADDRESS_NOT_VALID)	//address is not in the database
		{	
			delErrMssg = String.format("The %s address is not a valid residential address in Fairfax County. "
									+ "Please provide a valid address in ONC's serving area.", addrType);
			returnCode = returnCode | EC_ADDRESS_NOT_VALID ;
		}
		else if(delAddressResult == RC_ADDRESS_MISSING_UNIT)
		{	
			delErrMssg = String.format("The %s address has multiple residences, an apartment or unit number "
										+ "is requried.", addrType);
			returnCode = returnCode | EC_ADDRESS_MISSING_UNIT;
		}
		else if(delAddressResult == RC_ADDRESS_NOT_IN_SERVED_ZIPCODE)
		{
			delErrMssg = String.format("The %s address entered is not within zip codes served by ONC. "
					+ "Please refer this family to a service provider serving this address. You may also enter "
					+ "your school's address and ONC will bring the gifts to you for distribution.", addrType);
			returnCode = returnCode | EC_ADDRESS_NOT_IN_SERVED_ZIPCODE;
		}
		else if(delAddressResult == RC_ADDRESS_NOT_IN_SERVED_PYRAMID)
		{	
			delErrMssg = String.format("The %s address entered is not in a school pyramid served by ONC. "
					+ "Please refer this family to a service provider serving this address. You may also enter "
					+ "your school's address and ONC will bring the gifts to you for distribution.", addrType);
			returnCode = returnCode | EC_ADDRESS_NOT_IN_SERVED_PYRAMID;
		}
		
		return new AddressValidation(returnCode, delErrMssg);
	}
	
	int checkAddress(Address chkAddress)
	{
		if(ServerRegionDB.isAddressServedSchool(chkAddress))
			return RC_ADDRESS_IS_SCHOOL;
		else
		{	
			RegionAndSchoolCode rSC = ServerRegionDB.searchForRegionMatch(chkAddress);
			if(rSC.getRegion() == 0)
				return  RC_ADDRESS_NOT_VALID;
			else if(chkAddress.getUnit().trim().isEmpty() && ApartmentDB.isAddressAnApartment(chkAddress))	
				return  RC_ADDRESS_MISSING_UNIT;
			else if(rSC.getSchoolCode().equals("Z"))
				return  RC_ADDRESS_NOT_IN_SERVED_ZIPCODE;
			else if(rSC.getSchoolCode().equals("Y"))	
				return RC_ADDRESS_NOT_IN_SERVED_PYRAMID;
			else	//address is good to accept
				return RC_ADDRESS_IS_VALID;
		}
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
					ONCWebPartnerExtended.getStatus(partnerMap.get("status")),
					ONCWebPartnerExtended.getType(partnerMap.get("type")),
					GiftCollectionType.valueOf(partnerMap.get("collection")), partName,
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
				updatePartner.setType(ONCWebPartnerExtended.getType(partnerMap.get("type")));
				updatePartner.setGiftCollectionType(GiftCollectionType.valueOf(partnerMap.get("collection")));
				updatePartner.setLastName(partName);
				updatePartner.setHouseNum(partnerMap.get("housenum"));
				updatePartner.setStreet(partnerMap.get("street"));
				updatePartner.setUnit(partnerMap.get("unit"));
				updatePartner.setCity(partnerMap.get("city"));
				updatePartner.setZipCode(partnerMap.get("zipcode"));	
				updatePartner.setHomePhone(partnerMap.get("phone"));
				updatePartner.setStatus(ONCWebPartnerExtended.getStatus(partnerMap.get("status")));
				updatePartner.setNumberOfOrnamentsRequested(orn_req);				
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
		ServerRegionDB serverRegionDB= null;
				
		try
		{
			serverRegionDB = ServerRegionDB.getInstance();
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
//			System.out.println(String.format("ONCWebHdlr.processRegUpdate: regionMap key=%s, value=%s", key, regionMap.get(key)));
		
		String[] regionLine = new String[13];
		regionLine[0] = regionMap.get("regionid").equals("New") ? "-1" : regionMap.get("regionid");
		regionLine[1] = regionMap.get("street").trim().toUpperCase();
		regionLine[2] = regionMap.get("type");
		regionLine[3] = regionMap.get("region");
		regionLine[4] = "";	//schoolRegion is assigned when added
		regionLine[5] = "";	//school is assigned when added
		regionLine[6] = "";	//location is assigned when added
		regionLine[7] = regionMap.get("dir");
		regionLine[8] = regionMap.get("postdir");
		regionLine[9] = regionMap.get("addlo").trim();
		regionLine[10] = regionMap.get("addhi").trim();
		regionLine[11] = "";
		regionLine[12] = regionMap.get("zipcode");
		
		Region returnedRegion = new Region(regionLine);
		ResponseCode rc = null;

		//determine if its an add region request or a region update request
		if(regionMap.get("regionid").equals("New"))
		{
			Region addedRegion = serverRegionDB.add(returnedRegion);
			
			if(addedRegion != null)
			{
				rc = new ResponseCode(String.format("Region %d, %s successfully added to the database", 
									addedRegion.getID(), addedRegion.getStreetName()));
			}
			else
				rc = new ResponseCode("Region was unable to be added to the database");
		}
		else
		{
			Region updatedRegion = serverRegionDB.update(returnedRegion);
			if(updatedRegion != null)
			{
				rc = new ResponseCode(String.format("Region %d, %s successfully updated", 
										updatedRegion.getID(), updatedRegion.getStreetName()));
			}
			else
				rc = new ResponseCode("Region was not able to be updated");
		}
		
		return rc;	
	}
}
