package oncserver;

import java.io.IOException;
import java.net.HttpCookie;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Map;
import java.util.TimeZone;

import ourneighborschild.ONCServerUser;
import ourneighborschild.ONCUser;
import ourneighborschild.UserAccess;
import ourneighborschild.UserStatus;

import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsExchange;

/****
 * Handles contexts associated with the login and log-off processes
 * @author johnoneil
 *
 */
public class LoginHandler extends ONCWebpageHandler
{	
	private static final long ID_VALIDITY_TIME = 1000 * 60 * 10;
	private static final int LOGIN_FAILURE_LIMIT = 4;
	private ServerUserDB userDB;
	
	LoginHandler()
	{
		try
		{
			userDB = ServerUserDB.getInstance();
		}
		catch (NumberFormatException | IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Override
	public void handle(HttpExchange te) throws IOException 
	{
		HttpsExchange t = (HttpsExchange) te;
		@SuppressWarnings("unchecked")
		Map<String, Object> params = (Map<String, Object>)t.getAttribute("parameters");

		String requestURI = t.getRequestURI().toASCIIString();
		
		String mssg = String.format("HTTP request %s: %s:%s", t.getRemoteAddress().toString(), t.getRequestMethod(), requestURI);
		ServerUI.getInstance().addUIAndLogMessage(mssg);
		
		//DEBUG CODE FOR EXPIRIMENTING WITH COOKIE SECUIRTY MANAGMENT	
//		Headers reqHeaders = t.getRequestHeaders();
//		Set<String> keyset = reqHeaders.keySet();
//		for(String key:keyset)
//			System.out.println(String.format("uri=%s, key=%s, value=%s", t.getRequestURI().toASCIIString(), key, reqHeaders.get(key)));
		
		if(requestURI.equals("/welcome"))
		{
    			String response = null;
    			if(ONCSecureWebServer.isWebsiteOnline())
    			{
    				response = webpageMap.get("online");
    				response = response.replace("COLOR", "blue");
    				response = response.replaceAll("LEGEND_MESSAGE", "Our Neighbor's Child Login");
    				response = response.replace("WELCOME_MESSAGE", "Welcome to Our Neighbor's Child, Please Login:");
    				response = response.replace("LOGIN_ATTEMPT", "");
    			}
    			else
    			{
    				response = webpageMap.get("offline");
    				response = response.replace("TIME_BACK_UP", ONCSecureWebServer.getWebsiteTimeBackOnline());
    			}
    		
    			sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
		}
		else if(requestURI.startsWith("/logout"))
		{
    			Headers respHeader = t.getResponseHeaders();
    			WebClient wc;

    			if((wc=clientMgr.findAndValidateClient(t.getRequestHeaders())) != null)
    			{
    				//logout the client
    				clientMgr.logoutWebClient(wc);
    					
    				//advise the browser to delete the session cookie per RFC 6265
    				ArrayList<String> headerCookieList = new ArrayList<String>();
    				String delCookie = String.format("%sdeleted; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT", SESSION_ID_NAME);
    				headerCookieList.add(delCookie);
    				respHeader.put("Set-Cookie",  headerCookieList);	
    			}
    			else		//Handle if client has timed out or is an impostor
    				clientMgr.addLogMessage("ONCHttpHandler /logout failure: client not found");
    		
    			//send a redirect header to redirect to the public ONC webpage
    			ArrayList<String> headerLocationList = new ArrayList<String>();
    			headerLocationList.add("http://www.ourneighborschild.org");
    			respHeader.put("Location", headerLocationList);
  	
    			sendHTMLResponse(t, new HtmlResponse("", HttpCode.Redirect));
		}
		else if(requestURI.contains("/login"))
    	{
			if(t.getRequestMethod().toLowerCase().equals("post"))
				sendHTMLResponse(t, loginRequest(t, params));
			else
			{
				Headers respHeader = t.getResponseHeaders();
				ArrayList<String> headerAllowableList = new ArrayList<String>();
				headerAllowableList.add("POST");
				respHeader.put("Allow", headerAllowableList);
		
				sendHTMLResponse(t, new HtmlResponse("", HttpCode.Method_Not_Allowed));
			}
    	}
		else if(requestURI.equals("/lostcredentials"))
		{
			String response = webpageMap.get(requestURI);
			sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
		}
		else if(requestURI.equals("/verifyidentity"))
		{
			if(t.getRequestMethod().toLowerCase().equals("post"))
				sendHTMLResponse(t, identityRequest(t, params));
			else
			{
				Headers respHeader = t.getResponseHeaders();
				ArrayList<String> headerAllowableList = new ArrayList<String>();
				headerAllowableList.add("POST");
				respHeader.put("Allow", headerAllowableList);
		
				sendHTMLResponse(t, new HtmlResponse("", HttpCode.Method_Not_Allowed));
			}
		}
		else if(requestURI.contains("/onchomepage"))
		{
    			Headers header = t.getResponseHeaders();
    			ArrayList<String> headerList = new ArrayList<String>();
    			headerList.add("http://www.ourneighborschild.org");
    			header.put("Location", headerList);
  	
    			sendHTMLResponse(t, new HtmlResponse("", HttpCode.Redirect));
    		}
		else if(requestURI.contains("/metrics"))
		{
    			HtmlResponse htmlResponse;
    		
    			if(clientMgr.findAndValidateClient(t.getRequestHeaders()) != null)
    			{
    				int year = Integer.parseInt((String) params.get("year"));
    				String maptype = (String) params.get("maptype");
    				htmlResponse = ServerFamilyDB.getFamilyMetricsJSONP(year, maptype, (String)params.get("callback"));
    			}
    			else
    				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String) params.get("callback"));
    			
    			sendHTMLResponse(t, htmlResponse);
		}
		else if(requestURI.equals("/timeout"))
		{
    			String response;
    			if(ONCSecureWebServer.isWebsiteOnline())
    			{
    				response = webpageMap.get("online");
    				response = response.replace("COLOR", "blue");
    				response = response.replaceAll("LEGEND_MESSAGE", "Our Neighbor's Child Login");
    				response = response.replace("WELCOME_MESSAGE", "Your last session expired, please login again:");
    				response = response.replace("LOGIN_ATTEMPT", "");
    			}
    			else
    			{
    				response = webpageMap.get("offline");
    				response = response.replace("TIME_BACK_UP", ONCSecureWebServer.getWebsiteTimeBackOnline());
    			}
    		
    			sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
		}
		else if(requestURI.startsWith("/authenticatecode"))
		{
			//if user has sent the correct recovery code from a web page with a matching recover ID within the
			//recovery time period, respond with a change password web page
			String html;
			if(params.containsKey("recoveryID") && params.containsKey("code") && params.containsKey("offset"))
			{
				String recoveryID = (String) params.get("recoveryID");
				String code = (String) params.get("code");
			
				//find the user based on recoveryID
				ONCServerUser su = userDB.findUserByRecoveryIDAndCode(recoveryID, code);
				if(su != null)
				{
					//found user, now check if recovery id was used before time limit
					long timeNow = Calendar.getInstance().getTimeInMillis();
					if(timeNow - su.getRecoveryIDTime() <= ID_VALIDITY_TIME)
					{
						//recovery ID is still valid, user has successfully authenticated. Process their login request.
						HtmlResponse htmlResponse = processUserLoginRequest(t, params, su);
						sendHTMLResponse(t, htmlResponse);
					}
					else
					{
						//send an error message web page that time expired
						html = webpageMap.get("loginerror");
    					html = html.replace("COLOR", "red");
    					html = html.replace("LEGEND_MESSAGE", "Recovery Period Expired");
    					html = html.replace("ERROR_MESSAGE", "We are unable to process your request, please contact ONC at schoolcontact@ourneighborschild.org");
    					sendHTMLResponse(t, new HtmlResponse(html, HttpCode.Ok));
					}
				}
				else
				{
					//send an error message web page that user wasn't found
					html = webpageMap.get("loginerror");
					html = html.replace("COLOR", "red");
					html = html.replace("LEGEND_MESSAGE", "Recovery Unavailable");
					html = html.replace("ERROR_MESSAGE", "We are unable process your request, please contact ONC at schoolcontact@ourneighborschild.org");
					sendHTMLResponse(t, new HtmlResponse(html, HttpCode.Ok));
				}
			}
			else
			{
				//send an error message web page
				html = webpageMap.get("loginerror");
				html = html.replace("COLOR", "red");
				html = html.replace("LEGEND_MESSAGE", "Recovery Error");
				html = html.replace("ERROR_MESSAGE", "We are unable to process your request, please contact ONC at schoolcontact@ourneighborschild.org");
				sendHTMLResponse(t, new HtmlResponse(html, HttpCode.Ok));
			}	
		}
/*		
		else if(requestURI.startsWith("/recoverylogin"))
		{
			String html;
			
			if(params.containsKey("caseID"))
			{
				String recoveryID = (String) params.get("caseID");
			
				//find the user based on recoveryID
				ONCServerUser su = userDB.findUserByRecoveryID(recoveryID);
				if(su != null)
				{
					//found user, now check if recovery id was used before time limit
					long timeNow = Calendar.getInstance().getTimeInMillis();
					if(timeNow - su.getRecoveryIDTime() <= ID_VALIDITY_TIME)
					{
						//recovery ID is still valid, send the recovery login web page.
						html = webpageMap.get("recoverylogin");
    						html = html.replace("COLOR", "blue");
    						html = html.replace("LEGEND_MESSAGE", "Our Neighbors Child Recovery Login");
    						html = html.replace("WELCOME_MESSAGE", "Welcome to Our Neighbor's Child, Please Login:");
    						html = html.replaceAll("USERNAME", su.getUserID());
					}
					else
					{
						//send an error message web page that time expired
						html = webpageMap.get("loginerror");
	    					html = html.replace("COLOR", "red");
	    					html = html.replace("LEGEND_MESSAGE", "Recovery Time Expired");
	    					html = html.replace("ERROR_MESSAGE", "We are unable to process your request, please contact ONC at schoolcontact@ourneighborschild.org");
					}
				}
				else
				{
					//send an error message web page that user wasn't found
					html = webpageMap.get("loginerror");
    					html = html.replace("COLOR", "red");
    					html = html.replace("LEGEND_MESSAGE", "Recovery Unavailable");
    					html = html.replace("ERROR_MESSAGE", "We are unable process your request, please contact ONC at schoolcontact@ourneighborschild.org");
				}
			}
			else
			{
				//send an error message web page that recovery link was 
				html = webpageMap.get("loginerror");
				html = html.replace("COLOR", "red");
				html = html.replace("LEGEND_MESSAGE", "Recovery Error");
				html = html.replace("ERROR_MESSAGE", "We are unable to process your request, please contact ONC at schoolcontact@ourneighborschild.org");
			}
			
			sendHTMLResponse(t, new HtmlResponse(html, HttpCode.Ok));
		}
*/		
	}
	
	HtmlResponse loginRequest(HttpExchange t, Map<String, Object> params)
	{
		HtmlResponse response = null;
		
		String html;		
		
		String userID = (String) params.get("field1");
		String password = (String) params.get("field2");
	    	
		ONCServerUser serverUser = (ONCServerUser) userDB.find(userID);
		if(serverUser == null)	//can't find the user in the data base
	    {
			html = webpageMap.get("online");
			html = html.replace("COLOR", "red");
			html = html.replace("LEGEND_MESSAGE", "Invalid  User Name or Password");
			html = html.replace("WELCOME_MESSAGE", "You have entered an incorrect User Name<br>or Password. Please try again.");
			html = html.replace("LOGIN_ATTEMPT", "");
	    	response = new HtmlResponse(html, HttpCode.Ok);
	    }
		else if(serverUser != null && !serverUser.pwMatch(password))	//found the user but pw is incorrect
    	{
			serverUser.setFailedLoginCount(serverUser.getFailedLoginCount() + 1);
			
			if(serverUser.getFailedLoginCount() >= LOGIN_FAILURE_LIMIT)
			{
				//lock the users account and send them back a notification web page. This will require 
				//notifying clients of updated user object
				serverUser.setStatus(UserStatus.Inactive);

    			Gson gson = new Gson();
    			String loginJson = gson.toJson(serverUser.getUserFromServerUser(), ONCUser.class);
				String mssg = "UPDATED_USER" + loginJson;
    			clientMgr.notifyAllClients(mssg);
				
				html = webpageMap.get("loginerror");
	    		html = html.replace("COLOR", "red");
	    		html = html.replace("LEGEND_MESSAGE", "Failed Login Limit Exceded");
	    		html = html.replace("ERROR_MESSAGE", "To protect you, we've locked your account, please contact ONC at schoolcontact@ourneighborschild.org");
	    		response = new HtmlResponse(html, HttpCode.Ok);
			}
			else
			{	
				html = webpageMap.get("online");
				html = html.replace("COLOR", "red");
				html = html.replace("LEGEND_MESSAGE", "Invalid  User Name/Password");
				html = html.replace("WELCOME_MESSAGE", "You have entered an incorrect User Name<br>or Password. Please try again.");
				String plural = LOGIN_FAILURE_LIMIT - serverUser.getFailedLoginCount() > 1 ? "attempts":"attempt";
				String attMssg = String.format("You have %d %s remaining", LOGIN_FAILURE_LIMIT - serverUser.getFailedLoginCount(), plural);
				html = html.replace("LOGIN_ATTEMPT", attMssg);
				response = new HtmlResponse(html, HttpCode.Ok);
			}
			
			userDB.requestSave();
    	}
		else if(serverUser != null && serverUser.pwMatch(password))
		{
			response = processUserLoginRequest(t, params, serverUser);
		}
//		Code broken into login request and processUserLoginRequest methods		
//	    else if(serverUser != null && serverUser.pwMatch(password) && 
//	    			serverUser.getStatus() == UserStatus.Inactive)	//found user but account is locked
//	    {
//	    	html = webpageMap.get("loginerror");
//	    	html = html.replace("COLOR", "red");
//  		html = html.replace("LEGEND_MESSAGE", "Locked Account");
//    		html = html.replace("ERROR_MESSAGE", "This account is locked, please contact ONC at schoolcontact@ourneighborschild.org");
//    		response = new HtmlResponse(html, HttpCode.Forbidden);
//	    }
//	    else if(serverUser != null && serverUser.pwMatch(password) && 
//	    			!(serverUser.getAccess().equals(UserAccess.Website) || serverUser.getAccess().equals(UserAccess.AppAndWebsite)))
//	    {
//  		html = webpageMap.get("loginerror");	//found user, but they don't have web site access
//    		html = html.replace("COLOR", "red");
//			html = html.replace("LEGEND_MESSAGE", "Unauthorized Access");
//			html = html.replace("ERROR_MESSAGE", "Website access not authorized for this account,<br>please contact ONC at volunteer@ourneighborschild.org");
//			response = new HtmlResponse(html, HttpCode.Forbidden);
//    	}
//    	else if(serverUser != null && serverUser.pwMatch(password))	//user found, password matches
//    	{
//    		//get prior last login time for welcome message before setting new
//    		//last login time
//    		long priorLastLogin = serverUser.getLastLogin();
//    		serverUser.setLastLogin(System.currentTimeMillis());
//
//    		serverUser.incrementSessions();
//    		serverUser.setFailedLoginCount(0);
//    		serverUser.setClientYear(DBManager.getCurrentSeason());
//    		
//    		userDB.requestSave();
//	    		
//    		ONCUser webUser = serverUser.getUserFromServerUser();
//    		WebClient wc = clientMgr.addWebClient(t, serverUser);
//			
//    		//has the current password expired? If so, send change password page
//    		if(serverUser.changePasswordRqrd())
//    		{
//    			Gson gson = new Gson();
//    			String loginJson = gson.toJson(webUser, ONCUser.class);
//    		
//    			String mssg = "UPDATED_USER" + loginJson;
//    			clientMgr.notifyAllClients(mssg);
//    			
//    			html = webpageMap.get("changepw");
//    			html = html.replaceAll("USERFN", getUserFirstName(wc));
//    				
//   			response = new HtmlResponse(html, HttpCode.Ok, getSIDCookie(wc));
//    		}
//    		else //send the home page
//    		{
//    			Gson gson = new Gson();
//    			String loginJson = gson.toJson(webUser, ONCUser.class);
//    		
//    			String mssg = "UPDATED_USER" + loginJson;
//    			clientMgr.notifyAllClients(mssg);
//    			
//    			//determine if user never visited or last login date
//    			String userMssg;
//    			if(serverUser.getNSessions() == 1)
//    				userMssg = "This is your first visit!";
//    			else
//    			{
//    				//get UTC offset to where the user is located
//    	    		long userOffsetInMillis = 0;
//   					if(params.containsKey("offset") && isNumeric((String)params.get("offset")))
//    					userOffsetInMillis = (Integer.parseInt((String)params.get("offset"))) * 60 * 1000 * -1;
//    				
//    				Calendar lastLoginCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
//    				lastLoginCal.setTimeInMillis(priorLastLogin + userOffsetInMillis);
//    				
//    				SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMMM d, yyyy, h:mm a");
//    				sdf.setTimeZone(TimeZone.getDefault());
//    				userMssg = "Your last login was on " + sdf.format(lastLoginCal.getTime()) +".";
//    			}
//
//    			response = new HtmlResponse(getHomePageHTML(wc, userMssg, false), HttpCode.Ok, getSIDCookie(wc));
//    		}
//    	}   	
		
		return response;
	}
	
	HtmlResponse processUserLoginRequest(HttpExchange t, Map<String, Object> params, ONCServerUser serverUser)
	{
		HtmlResponse response = null;
		String html;
		
	    if(serverUser.getStatus() == UserStatus.Inactive)	//found user but account is locked
	    {
	    	html = webpageMap.get("loginerror");
	    	html = html.replace("COLOR", "red");
    		html = html.replace("LEGEND_MESSAGE", "Locked Account");
    		html = html.replace("ERROR_MESSAGE", "This account is locked, please contact ONC at schoolcontact@ourneighborschild.org");
    		response = new HtmlResponse(html, HttpCode.Forbidden);
	    }
	    else if(!(serverUser.getAccess().equals(UserAccess.Website) || serverUser.getAccess().equals(UserAccess.AppAndWebsite)))
	    {
    		html = webpageMap.get("loginerror");	//found user, but they don't have web site access
    		html = html.replace("COLOR", "red");
			html = html.replace("LEGEND_MESSAGE", "Unauthorized Access");
			html = html.replace("ERROR_MESSAGE", "Website access not authorized for this account,<br>please contact ONC at volunteer@ourneighborschild.org");
			response = new HtmlResponse(html, HttpCode.Forbidden);
    	}
    	else
    	{
    		//get prior last login time for welcome message before setting new
    		//last login time
    		long priorLastLogin = serverUser.getLastLogin();
    		serverUser.setLastLogin(System.currentTimeMillis());

    		serverUser.incrementSessions();
    		serverUser.setFailedLoginCount(0);
    		serverUser.setClientYear(DBManager.getCurrentSeason());
    		
    		userDB.requestSave();
	    		
    		ONCUser webUser = serverUser.getUserFromServerUser();
    		WebClient wc = clientMgr.addWebClient(t, serverUser);
			
    		//has the current password expired? If so, send change password page
    		if(serverUser.changePasswordRqrd() || serverUser.getStatus() == UserStatus.Recovery)
    		{
    			Gson gson = new Gson();
    			String loginJson = gson.toJson(webUser, ONCUser.class);
    		
    			String mssg = "UPDATED_USER" + loginJson;
    			clientMgr.notifyAllClients(mssg);
    			
    			html = webpageMap.get("changepw");
    			html = html.replaceAll("USERFN", getUserFirstName(wc));
    			if(serverUser.getStatus() == UserStatus.Recovery)
    			{
    				html = html.replace("CHANGE_PW_MODE", "'RECOVERY'");
    				html = html.replace("RECOVERY_CODE", serverUser.getUserPW());
    				
    				serverUser.setStatus(UserStatus.Change_PW);
        			userDB.requestSave();
    			}
    			else
    			{
    				//clear the current password and set the display to block
    				html = html.replace("CHANGE_PW_MODE", "'NORMAL'");
    				html = html.replace("RECOVERY_CODE", "");
    			}
    			
    			response = new HtmlResponse(html, HttpCode.Ok, getSIDCookie(wc));
    		}
    		else //send the home page
    		{
    			Gson gson = new Gson();
    			String loginJson = gson.toJson(webUser, ONCUser.class);
    		
    			String mssg = "UPDATED_USER" + loginJson;
    			clientMgr.notifyAllClients(mssg);
    			
    			//determine if user never visited or last login date
    			String userMssg;
    			if(serverUser.getNSessions() == 1)
    				userMssg = "This is your first visit!";
    			else
    			{
    				//get UTC offset to where the user is located
    	    		long userOffsetInMillis = 0;
   					if(params.containsKey("offset") && isNumeric((String)params.get("offset")))
    					userOffsetInMillis = (Integer.parseInt((String)params.get("offset"))) * 60 * 1000 * -1;
    				
    				Calendar lastLoginCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    				lastLoginCal.setTimeInMillis(priorLastLogin + userOffsetInMillis);
    				
    				SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMMM d, yyyy, h:mm a");
//	    				System.out.println(String.format("Last Login Millis: %d, Time: %s", priorLastLogin, sdf.format(lastLoginCal.getTime())));
//	    				System.out.println(lastLoginCal);
    				sdf.setTimeZone(TimeZone.getDefault());
    				userMssg = "Your last login was on " + sdf.format(lastLoginCal.getTime()) +".";
    			}

    			response = new HtmlResponse(getHomePageHTML(wc, userMssg, false), HttpCode.Ok, getSIDCookie(wc));
    		}
    	}
	    
	    return response;
	}
	
	HttpCookie getSIDCookie(WebClient wc)
	{
		HttpCookie cookie = new HttpCookie("SID", wc.getSessionID());
		cookie.setPath("/");
		cookie.setDomain(".oncdms.org");
		cookie.setSecure(true);
		cookie.setHttpOnly(true);
		
		return cookie;
	}
	
	HtmlResponse identityRequest(HttpExchange t, Map<String, Object> params)
	{
		HtmlResponse response = null; 
		String html = null;
		
		String email = (String) params.get("field1");
		String phone = (String) params.get("field2");
		
	    //don't want a reference here, want a new object. A user can be logged in more then once.
	    //However, never use this object to update a user's info
		ONCServerUser serverUser = (ONCServerUser) userDB.findUserByEmailAndPhone(email, phone);
		if(serverUser != null)
	    {
			ServerUI.addLogMessage(String.format("Found user id=%d, %s %s via identity lookup",
					serverUser.getID(), serverUser.getFirstName(), serverUser.getLastName()));
			
			if(serverUser.getAccess() == UserAccess.App)
			{
				//send a webpage that informs user that we've sent them an email
				html = webpageMap.get("loginerror");
				html = html.replace("COLOR", "red");
				html = html.replace("LEGEND_MESSAGE", "Identity Verified, Access Unauthorized");
				html = html.replace("ERROR_MESSAGE", "We found your account, however, website access isn't authorized. "
									+ "Please contact ONC at schoolcontact@ourneighborschild.org to authorize website access.");
				response = new HtmlResponse(html, HttpCode.Ok);
			}
			else if(serverUser.getStatus() == UserStatus.Inactive)
			{
				//send a webpage that informs user that we've sent them an email
				html = webpageMap.get("loginerror");
				html = html.replace("COLOR", "red");
				html = html.replace("LEGEND_MESSAGE", "Identity Verified, Account Locked");
				html = html.replace("ERROR_MESSAGE", "Please contact ONC at schoolcontact@ourneighborschild.org to unlock your account.");
				response = new HtmlResponse(html, HttpCode.Ok);
			}
			else
			{	
				//send a authentication code text to the user's cell phone
				serverUser.createRecoveryIDAndTime();
				serverUser.setRecoveryStatusAndPassword();
				userDB.requestSave();
			
				Gson gson = new Gson();
				String loginJson = gson.toJson(serverUser.getUserFromServerUser(), ONCUser.class);
				String mssg = "UPDATED_USER" + loginJson;
				clientMgr.notifyAllClients(mssg);
			
//				userDB.createAndSendRecoveryEmail(serverUser);
				userDB.createAndSendAccountRecoverySMS(serverUser);
			
				//send a webpage that informs user that we've sent them a text message and allows them to enter
				//a verification code
    			html = webpageMap.get("codeauthentication");
    			html = html.replace("RECOVERY_ID", serverUser.getRecoveryID());

    			response = new HtmlResponse(html, HttpCode.Ok);
			}
	    }
	    else
	    {
	    	//send a web page that informs user that we can't find them
	    	html = webpageMap.get("loginerror");
	    	html = html.replace("COLOR", "red");
	    	html = html.replace("LEGEND_MESSAGE", "Unable to Verify Identity");
	    	html = html.replace("ERROR_MESSAGE", "We are unable to verify your identity.<br>"
	    								+ "Please contact ONC at<br>schoolcontact@ourneighborschild.org");
	    	response = new HtmlResponse(html, HttpCode.Forbidden);
	    }
		
		return response;
	}
}
