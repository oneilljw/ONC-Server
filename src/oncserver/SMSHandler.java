package oncserver;

//import com.twilio.twiml.MessagingResponse;
//import com.twilio.twiml.messaging.Body;
//import com.twilio.twiml.messaging.Message;

import java.io.IOException;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsExchange;

//handler for SMS
public class SMSHandler extends ONCWebpageHandler
{
	public void handle(HttpExchange te) throws IOException 
    {
		HttpsExchange t = (HttpsExchange) te;
		@SuppressWarnings("unchecked")
		Map<String, Object> params = (Map<String, Object>)t.getAttribute("parameters");
    	
		String requestURI = t.getRequestURI().toASCIIString();
    	
		String mssg = String.format("HTTP request %s: %s:%s", t.getRemoteAddress().toString(), t.getRequestMethod(), requestURI);
		ServerUI serverUI = ServerUI.getInstance();
		serverUI.addUIAndLogMessage(mssg);
		
		HtmlResponse htmlResponse;
		
		if(requestURI.equals("/sms-receive"))
		{
			printParameters(t);	//DEBUG
		
			//create the twilio key map
			String[] twilioParamKeys = {"MessageSid", "SmsSid", "AccountSid", "MessagingServiceSid",
							"From", "To", "Body", "NumMedia", "FromCity", "FromState",
		    					"FromZip", "FromCountry"};
			
			Map<String, String> twilioParams = createMap(params, twilioParamKeys);
			
			//create response
			StringBuffer buff = new StringBuffer("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
			buff.append("<Response><Message>Thanks for your text</Message></Response>");
			
			htmlResponse = new HtmlResponse(buff.toString(), HttpCode.Ok);
		
			sendHTMLResponse(t, htmlResponse); 
		}
/*		
		else if(requestURI.contains("/sms-identity"))
		{
			int year = Integer.parseInt((String)params.get("year"));
			String callbackFunction = (String) params.get("callback");
			
//			printParameters(t);	//DEBUG
		
			String[] volKeys = {"delFN", "delLN", "groupother", "delhousenum", "delstreet", 
							"delunit", "delcity", "delzipcode", "primaryphone", "delemail",
							"group", "comment", "activity"};
		
			Map<String, String> volParams = createMap(params, volKeys);
		    		
			htmlResponse = ServerVolunteerDB.signInVolunteerJSONP(year, volParams, true,
											"Volunteer Sign-In Webpage", callbackFunction);
		
			sendHTMLResponse(t, htmlResponse);  
		}
*/		
    }
}
