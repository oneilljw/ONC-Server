package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

import ourneighborschild.Address;
import ourneighborschild.AddressValidation;
import ourneighborschild.GiftCollection;
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
	private static final int STATUS_CONFIRMED = 5;
	
	public void handle(HttpExchange te) throws IOException 
    {
		HttpsExchange t = (HttpsExchange) te;
		@SuppressWarnings("unchecked")
		Map<String, Object> params = (Map<String, Object>)t.getAttribute("parameters");
    	
		String requestURI = t.getRequestURI().toASCIIString();
    	
		String mssg = String.format("HTTP request %s: %s:%s", t.getRemoteAddress().toString(), t.getRequestMethod(), requestURI);
		ServerUI serverUI = ServerUI.getInstance();
		serverUI.addLogMessage(mssg);
		
		WebClient wc;
		HtmlResponse htmlResponse;
		
		if(requestURI.contains("/startpage"))
    		{
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
    		else if(requestURI.contains("/partners"))
    		{
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
    			{
    				int year = Integer.parseInt((String) params.get("year"));
    				htmlResponse = ServerPartnerDB.getPartnersJSONP(year, (String) params.get("callback"));
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
    				htmlResponse = RegionDB.getAddressesJSONP(zipCode, (String) params.get("callback"));
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error Message", (String)params.get("callback"));
    			
    			sendHTMLResponse(t, htmlResponse);
    		}
    		else if(requestURI.contains("/zipcodes"))
    		{
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
    				htmlResponse = RegionDB.getZipCodeJSONP((String) params.get("callback"));
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
        		
    				htmlResponse = RegionDB.getRegionJSONP(regionID, (String) params.get("callback"));
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
    				htmlResponse =  ServerChildDB.getChildrenInFamilyJSONP(year, famID, (String) params.get("callback"));
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
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String)params.get("callback"));
        			
    			sendHTMLResponse(t, htmlResponse);
    		}
    		else if(requestURI.contains("/activities"))
    		{
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
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
    			{
    				int year = Integer.parseInt((String) params.get("year"));
    				String fn = (String) params.get("delFN") != null ? (String) params.get("delFN") : "";
    				String ln = (String) params.get("delLN") != null ? (String) params.get("delLN") : "";
    				String cell = (String) params.get("cell") != null ? (String) params.get("cell") : "";
    				String callback = (String) params.get("callback");
    		
    				htmlResponse = ServerVolunteerDB.getVolunteerJSONP(year, fn, ln, cell, callback);
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String)params.get("callback"));
    				
    			sendHTMLResponse(t, htmlResponse);
    		}
    		else if(requestURI.contains("/address"))
    		{
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)	
    				htmlResponse = verifyAddress(params);	//verify the address and send response
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
