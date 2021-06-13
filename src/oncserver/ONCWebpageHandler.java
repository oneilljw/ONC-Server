package oncserver;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import ourneighborschild.ServerGVs;
import ourneighborschild.UserPermission;


public abstract class ONCWebpageHandler implements HttpHandler 
{
	protected static final int HTTP_OK = 200;
	
	private static final String ONC_FAMILY_PAGE_HTML = "ONC.htm";
	private static final String UPDATE_HTML = "Edit.htm";
	private static final String REFERRAL_HTML = "FamilyReferral.htm";
	private static final String FAMILY_MGMT_HTML = "FamilyMgmt.htm";
	private static final String COMMON_FAMILY_JS_FILE = "CommonFamily.js";
	private static final String EDIT_PROFILE_JS_FILE = "EditProfile.js";
	private static final String ONC_COMMON_JS_FILE = "ONCCommon.js";
	private static final String ONC_TABLE_JS_FILE = "ONCTable.js";
	private static final String PARTNER_UPDATE_HTML = "Partner.htm";
	private static final String REGION_TABLE_HTML = "RegionTable.htm";
	private static final String REGION_UPDATE_HTML = "Region.htm";
	private static final String DRIVER_SIGN_IN_HTML = "DriverReg.htm";
	private static final String VOLUNTEER_SIGN_IN_HTML = "WarehouseSignIn.htm";
	private static final String VOLUNTEER_REGISTRATION_HTML = "VolRegistration.htm";
	private static final String REFERRAL_STATUS_HTML = "ReferralStatus.htm";
	private static final String DASHBOARD_HTML = "Dashboard.htm";
	private static final String PARTNER_TABLE_HTML = "PartnerTable.htm";
	private static final String LOGOUT_HTML = "logout.htm";
	private static final String MAINTENANCE_HTML = "maintenance.htm";
	private static final String DELIVERY_DAY_ERROR_HTML = "DefaultDeliveryActivityNotSet.htm";
	private static final String CHANGE_PASSWORD_HTML = "Change.htm";
	private static final String VERIFY_IDENTITY_HTML = "VerifyIdentity.htm";
	private static final String LOGIN_ERROR_HTML = "LoginError.htm";
	private static final String RECOVERY_LOGIN_HTML = "LoginRecovery.htm";
	private static final String PDF_VIEWER_HTML = "PDFViewer.htm";
	private static final String GIFT_DELIVERY_HTML = "GiftDelivery.htm";
	private static final String CONFIRM_DELIVERY_HTML = "ConfirmGiftDelivery.htm";
//	private static final String QRSCANNER_HTML = "QRScanner.htm";

	private static final String ONC_SPLASH_FILE = "oncsplash.gif";
	private static final String CLEAR_X_FILE = "clear_x.gif";
	private static final String ONC_LOGO_FILE = "onclogosmall.gif";
	private static final String GIFT_CARD_LOGO_FILE = "giftcard.png";
	private static final String TRUST_LOGO_FILE = "comodo_secure_seal_76x26_transp.png";
	private static final String ERROR_ICON_FILE = "if_Error_381599.png";
	private static final String CHECKMARK_ICON_FILE = "if_Checkmark_1891021.png";
	private static final String ONC_ICON_FILE = "ONC.ico";
	private static final String VANILLA_FONT_FILE = "vanilla.ttf";
	private static final String ONC_STYLE_SHEET_CSS = "ONCStyleSheet.css";
	private static final String ONC_DIALOG__STYLE_SHEET_CSS = "ONCDialogStyleSheet.css";
	private static final String ONC_LOGIN__STYLE_SHEET_CSS = "ONCLoginStyleSheet.css";
	private static final String JQUERY_JS_FILE = "jquery-3.6.0.min.js";
	private static final String LOGIN_JS_FILE = "login.js";
	private static final String STATIC_CHARTS_JS_FILE = "staticcharts.js";
	private static final String WEBCAM_EASY_JS_FILE = "webcam-easy.min.js";
	private static final String SNAP_WAV_FILE = "snap.wav";
	private static final String STOPLIGHT_ANY_FILE = "Button-Blank-Any-icon.png";
	private static final String STOPLIGHT_GRAY_FILE = "Button-Blank-Gray-icon.png";
	private static final String STOPLIGHT_GREEN_FILE = "Button-Blank-Green-icon.png";
	private static final String STOPLIGHT_YELLOW_FILE = "Button-Blank-Yellow-icon.png";
	private static final String STOPLIGHT_RED_FILE = "Button-Blank-Red-icon.png";
	private static final String DELIVERY_CARD_FILE = "DeliveryCard.pdf";
	private static final String ERROR_404_FILE = "error-404.png";
	
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
			webpageMap.put("fammgmt", readFile(String.format("%s/%s",System.getProperty("user.dir"), FAMILY_MGMT_HTML)));
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
			webpageMap.put("deliveryDayError", readFile(String.format("%s/%s",System.getProperty("user.dir"), DELIVERY_DAY_ERROR_HTML)));
			webpageMap.put("changepw", readFile(String.format("%s/%s",System.getProperty("user.dir"), CHANGE_PASSWORD_HTML)));
			webpageMap.put("/lostcredentials", readFile(String.format("%s/%s",System.getProperty("user.dir"), VERIFY_IDENTITY_HTML)));
			webpageMap.put("loginerror", readFile(String.format("%s/%s",System.getProperty("user.dir"), LOGIN_ERROR_HTML)));
			webpageMap.put("recoverylogin", readFile(String.format("%s/%s",System.getProperty("user.dir"), RECOVERY_LOGIN_HTML)));
			webpageMap.put("getdeliverycards", readFile(String.format("%s/%s",System.getProperty("user.dir"), PDF_VIEWER_HTML)));
			webpageMap.put("giftdelivery", readFile(String.format("%s/%s",System.getProperty("user.dir"), GIFT_DELIVERY_HTML)));
			webpageMap.put("confirmdelivery", readFile(String.format("%s/%s",System.getProperty("user.dir"), CONFIRM_DELIVERY_HTML)));
//			webpageMap.put("qrscanner", readFile(String.format("%s/%s",System.getProperty("user.dir"), QRSCANNER_HTML)));
			
			webfileMap.put("commonfamily", readFileToByteArray(COMMON_FAMILY_JS_FILE));
			webfileMap.put("oncsplash", readFileToByteArray(ONC_SPLASH_FILE));
			webfileMap.put("clearx", readFileToByteArray(CLEAR_X_FILE));
			webfileMap.put("giftcardicon", readFileToByteArray(GIFT_CARD_LOGO_FILE));
			webfileMap.put("onclogo", readFileToByteArray(ONC_LOGO_FILE));
			webfileMap.put("trustlogo", readFileToByteArray(TRUST_LOGO_FILE));
			webfileMap.put("erroricon", readFileToByteArray(ERROR_ICON_FILE));
			webfileMap.put("checkmarkicon", readFileToByteArray(CHECKMARK_ICON_FILE));
			webfileMap.put("oncicon", readFileToByteArray(ONC_ICON_FILE));
			webfileMap.put("vanilla", readFileToByteArray(VANILLA_FONT_FILE));
			webfileMap.put("oncstylesheet", readFileToByteArray(ONC_STYLE_SHEET_CSS));
			webfileMap.put("oncdialogstylesheet", readFileToByteArray(ONC_DIALOG__STYLE_SHEET_CSS));
			webfileMap.put("oncloginstylesheet", readFileToByteArray(ONC_LOGIN__STYLE_SHEET_CSS));
			webfileMap.put("onccommon", readFileToByteArray(ONC_COMMON_JS_FILE));
			webfileMap.put("onctable", readFileToByteArray(ONC_TABLE_JS_FILE));
			webfileMap.put("editprofile", readFileToByteArray(EDIT_PROFILE_JS_FILE));
			webfileMap.put("login", readFileToByteArray(LOGIN_JS_FILE));
			webfileMap.put("jquery", readFileToByteArray(JQUERY_JS_FILE));
			webfileMap.put("staticcharts", readFileToByteArray(STATIC_CHARTS_JS_FILE));
			webfileMap.put("webcam-easy", readFileToByteArray(WEBCAM_EASY_JS_FILE));
			webfileMap.put("snap.wav", readFileToByteArray(SNAP_WAV_FILE));
			webfileMap.put("stoplighticon-any", readFileToByteArray(STOPLIGHT_ANY_FILE));
			webfileMap.put("stoplighticon-gray", readFileToByteArray(STOPLIGHT_GRAY_FILE));
			webfileMap.put("stoplighticon-green", readFileToByteArray(STOPLIGHT_GREEN_FILE));
			webfileMap.put("stoplighticon-yellow", readFileToByteArray(STOPLIGHT_YELLOW_FILE));
			webfileMap.put("stoplighticon-red", readFileToByteArray(STOPLIGHT_RED_FILE));
			webfileMap.put("deliverycards", readFileToByteArray(DELIVERY_CARD_FILE));
			webfileMap.put("error-404", readFileToByteArray(GIFT_CARD_LOGO_FILE));
			
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
    		
    		//add a SameSite=None cookie
//    		ArrayList<String> sameSiteList = new ArrayList<String>();
//    		headerList.add(getSameSiteCookie().toString());
//    		header.put("Set-Cookie", sameSiteList);
		}
				
		t.sendResponseHeaders(html.getCode(), html.getResponse().getBytes().length);
		OutputStream os = t.getResponseBody();
		os.write(html.getResponse().getBytes());
		os.close();
		t.close();
	}
	
	HttpCookie getSameSiteCookie()
	{
		HttpCookie cookie = new HttpCookie("SameSite", "Lax");
		cookie.setPath("/");
		cookie.setDomain(".oncdms.org");
		cookie.setSecure(true);
		cookie.setHttpOnly(true);
		
		return cookie;
	}
	
	void sendXMLResponse(HttpExchange t, HtmlResponse html) throws IOException
	{
		Headers header = t.getResponseHeaders();
		if(html.getCookie() != null)
		{
    			ArrayList<String> headerList = new ArrayList<String>();
    			headerList.add(html.getCookie().toString());
    			header.put("Set-Cookie", headerList);
		}
		
		ArrayList<String> contentTypeList = new ArrayList<String>();
		contentTypeList.add("text/xml");
		header.put("Content-Type", contentTypeList);
		
		t.sendResponseHeaders(html.getCode(), html.getResponse().getBytes().length);
		OutputStream os = t.getResponseBody();
		os.write(html.getResponse().getBytes());
		os.close();
		t.close();
	}
	
	void sendImageResponse(HttpExchange t, HtmlResponse html) throws IOException
	{
		Headers header = t.getResponseHeaders();
		if(html.getCookie() != null)
		{
    			ArrayList<String> headerList = new ArrayList<String>();
    			headerList.add(html.getCookie().toString());
    			header.put("Set-Cookie", headerList);
		}
		
		ArrayList<String> contentTypeList = new ArrayList<String>();
		contentTypeList.add("image/png");
		header.put("Content-Type", contentTypeList);
		
		t.sendResponseHeaders(html.getCode(), html.getResponse().getBytes().length);
		OutputStream os = t.getResponseBody();
		os.write(html.getResponse().getBytes());
		os.close();
		t.close();
	}
	
	Integer[] getRange(Headers requestHeaders)
	{
		int startbyte = -1, endbyte = -1;
		if(requestHeaders.containsKey("Range"))
		{
			List<String> rangeValue = requestHeaders.get("Range");
			if(rangeValue.size() == 1)	//value should have format "[bytes=x-y]". y can be an empty space
			{
				String range = rangeValue.get(0);
				
				String[] rangeParts = range.split("bytes=");	//should yield "x-y"
				
				if(rangeParts.length == 2)
				{
					String[] values = rangeParts[1].split("-");	//should yield a two string array of "x" and "y"
					
					if(values.length > 0 && values.length < 3)
					{
						try
						{
							startbyte = Integer.parseInt(values[0]);
							if(values.length == 2)
								endbyte = Integer.parseInt(values[1]);
							
							return new Integer[] {startbyte, endbyte};
						}
						catch (NumberFormatException nfe)
						{
							return null;
						}
					}
					else
						return null;
				}
				else
					return null;
			}
			else
				return null;			
		}
		else
			return null;
	}
	
	void sendNoContentResponseHeader(HttpExchange t) throws IOException
	{
		t.sendResponseHeaders(HttpCode.No_Body.code(), -1);
	}
	
	void sendBadRequestResponseHeader(HttpExchange t) throws IOException
	{
		t.sendResponseHeaders(HttpCode.Invalid.code(), -1);
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
	    
//	    String read_file = stringBuilder.toString();
//	    ServerUI.addDebugMessage(String.format("Read %s, size=%d", file, read_file.length()));
	    
	    return stringBuilder.toString();
	}
	
	static byte[] readFileToByteArray(String filename) throws IOException
	{
		//get file
	    String path = String.format("%s/%s", System.getProperty("user.dir"), filename);
	    File file = new File(path);
	    byte[] bytearray  = new byte [(int)file.length()];
	      
	    FileInputStream fis = new FileInputStream(file);
	      
	    BufferedInputStream bis = new BufferedInputStream(fis);
	    bis.read(bytearray, 0, bytearray.length);
	    bis.close();
	    
//	    ServerUI.addDebugMessage(String.format("Read Byte file %s, size=%d", file.getAbsolutePath(), bytearray.length));
	    
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
			{
				String value = (String) params.get(key);
				map.put(key, value.trim());
			}
			else
				map.put(key, "");
		}
		
		return map;
	}
	
	Map<String, String> createMap(Map<String, Object> params, List<String> keys)
	{
		Map<String, String> map = new HashMap<String, String>();
		for(String key:keys)
		{
			//code modified 10-18-16 to prevent null value exception if input map does not contain a key
			//if key is missing in input map, it is added with an empty string;
			if(params.containsKey(key) && params.get(key) != null)
			{
				String value = (String) params.get(key);
				map.put(key, value.trim());
			}
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
			return getPartnerTableWebpage(wc, "", message, false);	//send the partner table page, no dialog
		else	 
			return getReferralStatusWebpage(wc, message, message, "Sucessful Referral", bShowSuccessDialog); //send the family status page
	}
	
	String getReferralStatusWebpage(WebClient wc, String message, String successMssg, 
			String successDlgTitle, boolean bShowSuccessDialog)
	{
		Calendar deliveryDayCal = ServerGlobalVariableDB.getDeliveryDay(DBManager.getCurrentSeason());
		SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMMM d, yyyy");
		
		String response = webpageMap.get("familystatus");
		String loginMssg = String.format("Welcome %s! %s", wc.getWebUser().getFirstName(), message);
		response = response.replace("BANNER_MESSAGE", loginMssg);
		response = response.replace("HOME_LINK_VISIBILITY", getHomeLinkVisibility(wc));
		response = response.replace("DELIVERY_DATE", sdf.format(deliveryDayCal.getTime()));
		response = response.replace("SHOW_SUCCESS_DIALOG", bShowSuccessDialog ? "true" : "false");
		response = response.replace("SUCCESS_DIALOG_HEADER", successDlgTitle);
		response = response.replace("SUCCESS_DIALOG_MESSAGE", bShowSuccessDialog ? successMssg : "");
		response = response.replace("DNS_CODE_HINTS", ServerDNSCodeDB.getDNSCodeHTML());
		
		return response;
	}
	
	String getFamilyManagementWebpage(String successMssg, String successDlgTitle, boolean bShowSuccessDialog)
	{
		String response = webpageMap.get("fammgmt");
		response = response.replace("SHOW_SUCCESS_DIALOG", bShowSuccessDialog ? "true" : "false");
		response = response.replace("SUCCESS_DIALOG_HEADER", successDlgTitle);
		response = response.replace("SUCCESS_DIALOG_MESSAGE", bShowSuccessDialog ? successMssg : "");
		
		return response;
	}
	
	String getPartnerTableWebpage(WebClient wc, String dlgTitle, String message, boolean bShowSuccessDialog)
	{
		String response = webpageMap.get("partnertable");
		String loginMssg = String.format("Welcome %s! %s", wc.getWebUser().getFirstName(), message);
		response = response.replace("BANNER_MESSAGE", loginMssg);
		response = response.replace("HOME_LINK_VISIBILITY", getHomeLinkVisibility(wc));
		response = response.replace("SHOW_SUCCESS_DIALOG", bShowSuccessDialog ? "true" : "false");
		if(bShowSuccessDialog)
		{
			response = response.replace("SUCCESS_DIALOG_HEADER", dlgTitle);
			response = response.replace("SUCCESS_DIALOG_MESSAGE", message);
		}
		
		return response;
	}
	
	String getDashboardWebpage(WebClient wc, String message)
	{
		String response = webpageMap.get("dashboard");
		String loginMssg = String.format("Welcome %s! %s", wc.getWebUser().getFirstName(), message);

		return response.replace("BANNER_MESSAGE", loginMssg);
	}
	
	String enableReferralButton(String day)
	{
		ServerGlobalVariableDB gDB = null;
		try 
		{
			gDB = ServerGlobalVariableDB.getInstance();
			ServerGVs serverGVs = gDB.getServerGlobalVariables(DBManager.getCurrentSeason());
			
			//get current season
			Long today = System.currentTimeMillis();
			Long seasonStartDate = serverGVs.getSeasonStartDateMillis();
			Long compareDate = gDB.getDeadlineMillis(DBManager.getCurrentSeason(), day);
			
			if(seasonStartDate != null && compareDate != null && 
				today >= seasonStartDate && today < compareDate )
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
	
	void sendFile(HttpExchange t, String mimetype, String path) throws IOException
	{
		// add the required response header
	    Headers h = t.getResponseHeaders();
	    h.add("Content-Type", mimetype);

	    //get file   
//		String path = String.format("%s/%s", System.getProperty("user.dir"), sheetname);
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

		return new HtmlResponse(callback +"(" + json +")", HttpCode.Ok);
	}
	
	HtmlResponse invalidParameterReceivedToJsonRequest(String callback)
	{
		//send an error message json that will trigger a dialog box in the client
		String json = "{\"error\":\"Your request contains an invalid parameter\"}";

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
