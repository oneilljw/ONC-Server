package oncserver;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ourneighborschild.UserPermission;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public abstract class ONCWebpageHandler implements HttpHandler 
{
	protected static final int HTTP_OK = 200;
	
	private static final String ONC_FAMILY_PAGE_HTML = "ONC.htm";
	private static final String UPDATE_HTML = "Edit.htm";
	private static final String REFERRAL_HTML = "FamilyReferral.htm";
	private static final String COMMON_FAMILY_JS_FILE = "CommonFamily.js";
	private static final String PARTNER_UPDATE_HTML = "Partner.htm";
	private static final String REGION_TABLE_HTML = "RegionTable.htm";
	private static final String REGION_UPDATE_HTML = "Region.htm";
	private static final String DRIVER_SIGN_IN_HTML = "DriverReg.htm";
	private static final String VOLUNTEER_SIGN_IN_HTML = "WarehouseSignIn.htm";
	private static final String VOLUNTEER_REGISTRATION_HTML = "VolRegistration.htm";
	private static final String REFERRAL_STATUS_HTML = "ScrollFamTable.htm";
	private static final String DASHBOARD_HTML = "Dashboard.htm";
	private static final String PARTNER_TABLE_HTML = "PartnerTable.htm";
	private static final String LOGOUT_HTML = "logout.htm";
	private static final String MAINTENANCE_HTML = "maintenance.htm";
	private static final String CHANGE_PASSWORD_HTML = "Change.htm";
	private static final String VERIFY_IDENTITY_HTML = "VerifyIdentity.htm";
	private static final String LOGIN_ERROR_HTML = "LoginError.htm";
	private static final String RECOVERY_LOGIN_HTML = "LoginRecovery.htm";

	private static final String ONC_SPLASH_FILE = "oncsplash.gif";
	private static final String CLEAR_X_FILE = "clear_x.gif";
	private static final String ONC_LOGO_FILE = "onclogosmall.gif";
	private static final String TRUST_LOGO_FILE = "comodo_secure_seal_76x26_transp.png";
	private static final String ERROR_ICON_FILE = "if_Error_381599.png";
	private static final String CHECKMARK_ICON_FILE = "if_Checkmark_1891021.png";
	private static final String ONC_ICON_FILE = "ONC.ico";
	private static final String VANILLA_FONT_FILE = "vanilla.ttf";
	private static final String ONC_STYLE_SHEET_CSS = "ONCStyleSheet.css";
	private static final String ONC_DIALOG__STYLE_SHEET_CSS = "ONCDialogStyleSheet.css";
	private static final String ONC_LOGIN__STYLE_SHEET_CSS = "ONCLoginStyleSheet.css";
	private static final String JQUERY_JS_FILE = "jquery-1.11.3.js";
	
	protected static final String SESSION_ID_NAME = "SID=";
	
	protected ClientManager clientMgr;
	protected static Map<String,String> webpageMap;
	protected static Map<String, byte[]> webfileMap;
	
	ONCWebpageHandler()
	{
		clientMgr = ClientManager.getInstance();
		
		if(webpageMap == null)
			webpageMap = new HashMap<String, String>();
		
		if(webfileMap == null)
			webfileMap = new HashMap<String, byte[]>();
		
		if(webpageMap.isEmpty() || webfileMap.isEmpty())
		{
			//clear them both and load them. It should be impossible for one 
			//to be empty and one  to have content. As a protection, we clear both.
			webpageMap.clear();
			webfileMap.clear();
			loadWebpages();
		}
	}
	
	static String loadWebpages()
	{
		try 
		{
			webpageMap.put("dashboard", readFile(String.format("%s/%s",System.getProperty("user.dir"), DASHBOARD_HTML)));
			webpageMap.put("familystatus", readFile(String.format("%s/%s",System.getProperty("user.dir"), REFERRAL_STATUS_HTML)));
			webpageMap.put("family", readFile(String.format("%s/%s",System.getProperty("user.dir"), ONC_FAMILY_PAGE_HTML)));
			webpageMap.put("referral", readFile(String.format("%s/%s",System.getProperty("user.dir"), REFERRAL_HTML)));
			webpageMap.put("update", readFile(String.format("%s/%s",System.getProperty("user.dir"), UPDATE_HTML)));
			webpageMap.put("partnertable", readFile(String.format("%s/%s",System.getProperty("user.dir"), PARTNER_TABLE_HTML)));
			webpageMap.put("updatepartner", readFile(String.format("%s/%s",System.getProperty("user.dir"), PARTNER_UPDATE_HTML)));
			webpageMap.put("regiontable", readFile(String.format("%s/%s",System.getProperty("user.dir"), REGION_TABLE_HTML)));
			webpageMap.put("updateregion", readFile(String.format("%s/%s",System.getProperty("user.dir"), REGION_UPDATE_HTML)));
			webpageMap.put("/driversignin", readFile(String.format("%s/%s",System.getProperty("user.dir"), DRIVER_SIGN_IN_HTML)));
			webpageMap.put("/volunteersignin", readFile(String.format("%s/%s",System.getProperty("user.dir"), VOLUNTEER_SIGN_IN_HTML)));
			webpageMap.put("/volunteerregistration", readFile(String.format("%s/%s",System.getProperty("user.dir"), VOLUNTEER_REGISTRATION_HTML)));
			webpageMap.put("online", readFile(String.format("%s/%s",System.getProperty("user.dir"), LOGOUT_HTML)));
			webpageMap.put("offline", readFile(String.format("%s/%s",System.getProperty("user.dir"), MAINTENANCE_HTML)));
			webpageMap.put("changepw", readFile(String.format("%s/%s",System.getProperty("user.dir"), CHANGE_PASSWORD_HTML)));
			webpageMap.put("/lostcredentials", readFile(String.format("%s/%s",System.getProperty("user.dir"), VERIFY_IDENTITY_HTML)));
			webpageMap.put("loginerror", readFile(String.format("%s/%s",System.getProperty("user.dir"), LOGIN_ERROR_HTML)));
			webpageMap.put("recoverylogin", readFile(String.format("%s/%s",System.getProperty("user.dir"), RECOVERY_LOGIN_HTML)));
			
			webfileMap.put("commonfamily", readFileToByteArray(COMMON_FAMILY_JS_FILE));
			webfileMap.put("oncsplash", readFileToByteArray(ONC_SPLASH_FILE));
			webfileMap.put("clearx", readFileToByteArray(CLEAR_X_FILE));
			webfileMap.put("onclogo", readFileToByteArray(ONC_LOGO_FILE));
			webfileMap.put("trustlogo", readFileToByteArray(TRUST_LOGO_FILE));
			webfileMap.put("erroricon", readFileToByteArray(ERROR_ICON_FILE));
			webfileMap.put("checkmarkicon", readFileToByteArray(CHECKMARK_ICON_FILE));
			webfileMap.put("oncicon", readFileToByteArray(ONC_ICON_FILE));
			webfileMap.put("vanilla", readFileToByteArray(VANILLA_FONT_FILE));
			webfileMap.put("oncstylesheet", readFileToByteArray(ONC_STYLE_SHEET_CSS));
			webfileMap.put("oncdialogstylesheet", readFileToByteArray(ONC_DIALOG__STYLE_SHEET_CSS));
			webfileMap.put("oncloginstylesheet", readFileToByteArray(ONC_LOGIN__STYLE_SHEET_CSS));
			webfileMap.put("jquery", readFileToByteArray(JQUERY_JS_FILE));
			
			return "UPDATED_WEBPAGES";
		} 
		catch (IOException e) 
		{
			return "UPDATE_FAILED";
		}
	}
	
	static String reloadWebpagesAndWebfiles()
	{
		String response = "UPDATE_FAILED";
		if(webpageMap != null && webfileMap != null)
		{
			webpageMap.clear();
			webfileMap.clear();
			response = loadWebpages();
		}
		
		return response;
	}
	
	void sendHTMLResponse(HttpExchange t, HtmlResponse html) throws IOException
	{
		if(html.getCookie() != null)
		{
			Headers header = t.getResponseHeaders();
    			ArrayList<String> headerList = new ArrayList<String>();
    			headerList.add(html.getCookie().toString());
    			header.put("Set-Cookie", headerList);
		}
		
		t.sendResponseHeaders(html.getCode(), html.getResponse().getBytes().length);
		OutputStream os = t.getResponseBody();
		os.write(html.getResponse().getBytes());
		os.close();
		t.close();
	}
	
	static String readFile(String file) throws IOException
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
	
	static byte[] readFileToByteArray(String filename) throws IOException
	{
		//get file
	    String path = String.format("%s/%s", System.getProperty("user.dir"), filename);
	    File file = new File(path);
	    byte [] bytearray  = new byte [(int)file.length()];
	      
	    FileInputStream fis = new FileInputStream(file);
	      
	    BufferedInputStream bis = new BufferedInputStream(fis);
	    bis.read(bytearray, 0, bytearray.length);
	    bis.close();
	    
		return bytearray;
	}
	
	Map<String, String> createMap(Map<String, Object> params, String[] keys)
	{
		Map<String, String> map = new HashMap<String, String>();
		for(String key:keys)
		{
			//code modified 10-18-16 to prevent null value exception if input map does not contain a key
			//if key is missing in input map, it is added with an empty string;
			if(params.containsKey(key) && params.get(key) != null)
				map.put(key, (String) params.get(key));
			else
				map.put(key, "");
		}
		
		return map;
	}
	
	String getHomePageHTML(WebClient wc, String message, boolean bShowSuccessDialog)
	{
		//determine which home page to send to client based on UserPermission
		if(wc.getWebUser().getPermission() == UserPermission.Sys_Admin)
			return getDashboardWebpage(wc, message);
		else if(wc.getWebUser().getPermission() == UserPermission.Admin)
			return getDashboardWebpage(wc, message);
		else if(wc.getWebUser().getPermission() == UserPermission.General)
			return getPartnerTableWebpage(wc, message);	//send the partner table page
		else	 
			return getFamilyStatusWebpage(wc, message, message, "Sucessful Referral", bShowSuccessDialog); //send the family status page
	}
	
	String getFamilyStatusWebpage(WebClient wc, String message, String successMssg, 
			String successDlgTitle, boolean bShowSuccessDialog)
	{
		Calendar deliveryDay = ServerGlobalVariableDB.getDeliveryDay(DBManager.getCurrentYear());
		SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMMM d, yyyy");
		
		String response = webpageMap.get("familystatus");
		response = response.replace("USER_NAME", wc.getWebUser().getFirstName());
		response = response.replace("USER_MESSAGE", message);
		response = response.replace("THANKSGIVING_MEAL_CUTOFF", enableReferralButton("Thanksgiving Meal"));
		response = response.replace("DECEMBER_MEAL_CUTOFF", enableReferralButton("December Meal"));
		response = response.replace("WAITLIST_GIFT_CUTOFF", enableReferralButton("Waitlist Gift"));
		response = response.replace("EDIT_CUTOFF", enableReferralButton("Edit"));
		response = response.replace("DELIVERY_DATE", sdf.format(deliveryDay.getTime()));
		response = response.replace("HOME_LINK_VISIBILITY", getHomeLinkVisibility(wc));
		response = response.replace("SHOW_SUCCESS_DIALOG", bShowSuccessDialog ? "true" : "false");
		response = response.replace("SUCCESS_DIALOG_HEADER", successDlgTitle);
//		String successMssg = String.format("%s", message);
		response = response.replace("SUCCESS_DIALOG_MESSAGE", bShowSuccessDialog ? successMssg : "");
		
		return response;
	}
	
	String getPartnerTableWebpage(WebClient wc, String message)
	{
		String response = webpageMap.get("partnertable");
		response = response.replace("USER_NAME", getUserFirstName(wc));
		response = response.replace("USER_MESSAGE", message);
		response = response.replace("HOME_LINK_VISIBILITY", getHomeLinkVisibility(wc));
		
		return response;
	}
	
	String getDashboardWebpage(WebClient wc, String message)
	{
		String response = webpageMap.get("dashboard");
		response = response.replace("USER_NAME", getUserFirstName(wc));
		response = response.replace("USER_MESSAGE", message);
		
		return response;
	}
	
	String enableReferralButton(String day)
	{
		ServerGlobalVariableDB gDB = null;
		try 
		{
			gDB = ServerGlobalVariableDB.getInstance();
			
			//get current season
			int currSeason = DBManager.getCurrentYear();
			Calendar today = Calendar.getInstance();
			Date seasonStartDate = gDB.getSeasonStartDate(currSeason);
			Date compareDate = gDB.getDeadline(currSeason, day);
			
			if(seasonStartDate != null && compareDate != null && 
				today.getTime().compareTo(seasonStartDate) >=0 && today.getTime().compareTo(compareDate) < 0 )
				return "enabled";
			else
				return "disabled";
		}
		catch (FileNotFoundException e) 
		{
			return "disabled";
		}
		catch (IOException e) 
		{
			return "disabled";
		}
	}
	
	void sendCachedFile(HttpExchange t, String mimetype, String sheetname, boolean debugMode) throws IOException
	{
		if(debugMode)
			ServerUI.addDebugMessage(String.format("WebpageHldr.sendCachedFile: sheetname= %s", sheetname));
		// add the required response header
	    Headers h = t.getResponseHeaders();
	    h.add("Content-Type", mimetype);
	    
	    if(debugMode)
	    	ServerUI.addDebugMessage(String.format("WebpageHldr.sendCachedFile: header content added, type= %s", mimetype));

	    //get file
	    byte[] bytearray = webfileMap.get(sheetname);
	    
	    if(debugMode)
	    		ServerUI.addDebugMessage(String.format("WebpageHldr.sendCachedFile: bytearray size= %d", bytearray.length));
	    	    
//		String path = String.format("%s/%s", System.getProperty("user.dir"), sheetname);
//		File file = new File(path);
//		byte [] bytearray  = new byte [(int)file.length()];
//	      
//	    FileInputStream fis = new FileInputStream(file);
//	      
//	    BufferedInputStream bis = new BufferedInputStream(fis);
//	    bis.read(bytearray, 0, bytearray.length);
//	    bis.close();
	      	
	    //send the response.
//	    t.sendResponseHeaders(HTTP_OK, file.length());
	    t.sendResponseHeaders(HTTP_OK, bytearray.length);
	    
	    if(debugMode)
	    		ServerUI.addDebugMessage(String.format("WebpageHldr.sendCachedFile: sent response headers, code= %d, length= %d", HTTP_OK, bytearray.length)); 
	    
	    OutputStream os = t.getResponseBody();
	    
	    if(debugMode)
	    		ServerUI.addDebugMessage(String.format("WebpageHldr.sendCachedFile: got response body"));
	    
	    os.write(bytearray,0,bytearray.length);
	    
	    if(debugMode)
	    		ServerUI.addDebugMessage(String.format("WebpageHldr.sendCachedFile: wrote response, length= %d", bytearray.length));
	    
	    os.close();
	    
	    if(debugMode)
	    		ServerUI.addDebugMessage(String.format("WebpageHldr.sendCachedFile: closed output stream"));
	    
	    t.close();
	    
	    if(debugMode)
	    		ServerUI.addDebugMessage(String.format("WebpageHldr.sendCachedFile: closed HttpExchange"));
	}
	
	void sendFile(HttpExchange t, String mimetype, String sheetname) throws IOException
	{
		// add the required response header
	    Headers h = t.getResponseHeaders();
	    h.add("Content-Type", mimetype);

	    //get file   
		String path = String.format("%s/%s", System.getProperty("user.dir"), sheetname);
		File file = new File(path);
		byte [] bytearray  = new byte [(int)file.length()];
	      
	    FileInputStream fis = new FileInputStream(file);
	      
	    BufferedInputStream bis = new BufferedInputStream(fis);
	    bis.read(bytearray, 0, bytearray.length);
	    bis.close();
	      	
	    //send the response.
	    t.sendResponseHeaders(HTTP_OK, file.length());
	    OutputStream os = t.getResponseBody();
	    os.write(bytearray, 0, bytearray.length);
	    os.close();
	    t.close();
	}
	
	String invalidTokenReceived()
	{
		String response;
		if(ONCSecureWebServer.isWebsiteOnline())
		{
			response = webpageMap.get("online");
			response = response.replace("COLOR", "red");
			response = response.replaceAll("LEGEND_MESSAGE", "Our Neighbor's Child Login");
			response = response.replace("WELCOME_MESSAGE", "Your session expired, please login again:");
			response = response.replace("LOGIN_ATTEMPT", "");
		}
		else
		{
			response = webpageMap.get("offline");
			response = response.replace("TIME_BACK_UP", ONCSecureWebServer.getWebsiteTimeBackOnline());
		}
		
		return response;
	}

	HtmlResponse invalidTokenReceivedToJsonRequest(String mssg, String callback)
	{
		//send an error message json that will trigger a dialog box in the client
		String json = "{\"error\":\"Your session expired due to inactivity\"}";
		
//		return callback +"(" + json +")";
		
		return new HtmlResponse(callback +"(" + json +")", HttpCode.Ok);
	}
	
	String getHomeLinkVisibility(WebClient wc)
	{
		if(wc.getWebUser().getPermission() == UserPermission.Admin ||
				wc.getWebUser().getPermission() == UserPermission.Sys_Admin)
		{
			return "visible";
		}
		else
			return "hidden";
	}
	
	/***********
	 * Method takes the request headers from HttpExchange and examines each header for one named
	 * Cookie in accordance with the RFC's. It then takes the Cookie header and searches for a string
	 * that starts with the SESSION_ID_NAME. If it finds that string, it returns the sessionID from
	 * that string. The sessionID string will have format SESSION_ID_NAME="xxxx-xxxx-xxxx", where the x's
	 * represent hexadecimal characters. The method uses String.substring to eliminate the SESSION_ID_NAME and
	 * the quotation marks the encapsulate the sessionID in the cookie.
	 * 
	 * If the passed Headers parameter doesn't contain a "Cookie" header, the method returns null.
	 * @param reqHeaders
	 * @return sessionID
	 */
	String getSessionID(Headers reqHeaders)
	{
		String sessionID = null;
		if(reqHeaders.containsKey("Cookie"))
		{
			List<String> reqHeaderList = reqHeaders.get("Cookie");
			for(String s: reqHeaderList)
				if(s.startsWith(SESSION_ID_NAME))
					sessionID = s.substring(SESSION_ID_NAME.length()+1, s.length()-1);
		}
		
		return sessionID;
	}
	
	String getUserFirstName(WebClient wc)
	{
		if(wc != null && wc.getWebUser().getFirstName().equals(""))
			 return wc.getWebUser().getLastName();
		else if(wc != null)
			return wc.getWebUser().getFirstName();
		else
			return "";
	}
	
	void printParameters(HttpExchange t)
	{
		@SuppressWarnings("unchecked")
		Map<String, Object> params = (Map<String, Object>)t.getAttribute("parameters");
		Set<String> keyset = params.keySet();
		for(String key:keyset)
			System.out.println(String.format("uri=%s, key=%s, value=%s", t.getRequestURI().toASCIIString(), key, params.get(key)));
	}
	
	void logParameters(Map<String, Object> map, String URI)
	{
		StringBuffer buff = new StringBuffer(URI + "?");
		for(String key : map.keySet())
		{
			Object obj = map.get(key);
			if(obj instanceof String)
				buff.append(String.format("%s=%s&", key, map.get(key)));
		}
		
		ServerUI.addLogMessage(buff.toString());
		ServerUI.addDebugMessage(buff.toString());
	}
	
	static boolean isNumeric(String str)
	{
		if(str == null || str.isEmpty())
			return false;
		else
			return str.matches("-?\\d+(\\.\\d+)?");  //match a number with optional '-' and decimal.
	}
}
