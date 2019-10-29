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
			//create the twilio key map
			String[] twilioParamKeys = {"MessageSid", "SmsSid", "AccountSid", "MessagingServiceSid",
							"From", "To", "Body", "NumMedia", "FromCity", "FromState",
		    					"FromZip", "FromCountry"};
			
			Map<String, String> twilioParams = createMap(params, twilioParamKeys);
			
			//create response
			String name = "Anonymous", body = "Error";
			if(twilioParams.containsKey("From"))
			{
				if(twilioParams.get("From").equals("+15713440902"))	
					name = "John O'Neill";
				else if(twilioParams.get("From").equals("+7039262396"))
					name = "Kelly Lavin";
				
				body = twilioParams.get("Body");
			}
			
			String response = String.format("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
				"<Response><Message>%s, thank you for sending %s to Our Neighbor's Child</Message></Response>",
				name, body );
			
			htmlResponse = new HtmlResponse(response, HttpCode.Ok);
		
			sendXMLResponse(t, htmlResponse); 
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
