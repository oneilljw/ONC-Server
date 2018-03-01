package oncserver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsExchange;

public class VolunteerHandler extends ONCWebpageHandler
{
	public void handle(HttpExchange te) throws IOException 
    {
		HttpsExchange t = (HttpsExchange) te;
		@SuppressWarnings("unchecked")
		Map<String, Object> params = (Map<String, Object>)t.getAttribute("parameters");
    	
		String requestURI = t.getRequestURI().toASCIIString();
    	
		String mssg = String.format("HTTP request %s: %s:%s", t.getRemoteAddress().toString(), t.getRequestMethod(), requestURI);
		ServerUI serverUI = ServerUI.getInstance();
		serverUI.addLogMessage(mssg);
		
		HtmlResponse htmlResponse;
		
		if(requestURI.equals("/registervolunteer"))
		{
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
		
			htmlResponse = ServerVolunteerDB.addVolunteerJSONP(year, volParams, activityParams, 
										false, "Volunteer Registration Webpage", callbackFunction);
			sendHTMLResponse(t, htmlResponse); 
		}
		else if(requestURI.contains("/signinvolunteer"))
		{
			int year = Integer.parseInt((String)params.get("year"));
			String callbackFunction = (String) params.get("callback");
		
			String[] volKeys = {"delFN", "delLN", "groupother", "delhousenum", "delstreet", 
							"delunit", "delcity", "delzipcode", "primaryphone", "delemail",
							"group", "comment", "activity"};
		
			Map<String, String> volParams = createMap(params, volKeys);
		
			Map<String, String> activityParams = new HashMap<String, String>();
			activityParams.put("actckbox0", volParams.get("activity"));
			activityParams.put("actcomment0","New volunteer registered in warehouse");
		    		
			htmlResponse = ServerVolunteerDB.addVolunteerJSONP(year,  volParams, activityParams,
										true, "Warehouse Registration Webpage", callbackFunction);
		
			sendHTMLResponse(t, htmlResponse);  
		}
		else if(requestURI.equals("/signindriver"))
		{
			int year = Integer.parseInt((String)params.get("year"));
			String callbackFunction = (String) params.get("callback");
		    		
			String[] volKeys = {"delFN", "delLN", "groupother", "delhousenum", "delstreet", 
		    					"delunit", "delcity", "delzipcode", "primaryphone", "delemail",
		    					"group", "comment", "activity"};
		    		
			Map<String, String> volParams = createMap(params, volKeys);
		
			Map<String, String> activityParams = new HashMap<String, String>();
			activityParams.put("actckbox0",volParams.get("activity"));
			activityParams.put("actcomment0","New delivery volunteer on delivery day");
		    		
			htmlResponse = ServerVolunteerDB.addVolunteerJSONP(year, volParams, activityParams,
										true, "Delivery Day Registration Webpage", callbackFunction);
			sendHTMLResponse(t, htmlResponse); 
		}
		else		//volunteer registration, volunteer sign-in, or driver sign-in
		{
			String response = webpageMap.get(requestURI);
			response = response.replace("ERROR_MESSAGE", "Please ensure all fields are complete prior to submission");

			sendHTMLResponse(t, new HtmlResponse(response, HttpCode.Ok));
		}
    }
}
