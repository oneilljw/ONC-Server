package oncserver;

import java.io.FileNotFoundException;

//import com.twilio.twiml.MessagingResponse;
//import com.twilio.twiml.messaging.Body;
//import com.twilio.twiml.messaging.Message;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsExchange;

import ourneighborschild.EntityType;
import ourneighborschild.ONCFamily;
import ourneighborschild.ONCPartner;
import ourneighborschild.ONCSMS;
import ourneighborschild.SMSDirection;
import ourneighborschild.SMSStatus;

//handler for SMS
public class SMSHandler extends ONCWebpageHandler
{
	private ServerInboundSMSDB inboundSMSDB;
	private ServerSMSDB smsDB;
	private ServerFamilyDB familyDB;
	private ServerPartnerDB partnerDB;
	
	public SMSHandler() throws FileNotFoundException, IOException
	{
		this.inboundSMSDB = ServerInboundSMSDB.getInstance();
		this.smsDB = ServerSMSDB.getInstance();
		this.familyDB = ServerFamilyDB.getInstance();
		this.partnerDB = ServerPartnerDB.getInstance();
		
//		simulateSMSReceive();
	}
	
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
			String[] twilioParamKeys = {"AccountSid","MessageSid","Body","ToZip","ToCity",
							"FromState","ToState","SmsSid",  "To","ToCountry","FromCountry",
		    					"SmsMessageSid", "ApiVersion", "FromCity", "SmsStatus",
		    					"NumSegments", "NumMedia", "From", "FromZip" };
			
			//create the map and object
			Map<String, String> twilioParams = createMap(params, twilioParamKeys);
		
			//create a debug string
			StringBuffer buff = new StringBuffer();
			for(String key : twilioParamKeys)
			{
				buff.append(String.format(", %s= %s", key, twilioParams.get(key)));
			}
			buff.append(String.format(", timestamp= %d", System.currentTimeMillis()));
			ServerUI.addDebugMessage(buff.toString());
			
			TwilioSMSReceive rec_text = new TwilioSMSReceive(twilioParams);
			
			//add the received message to the Inbound SMS database
			inboundSMSDB.add(DBManager.getCurrentSeason(), rec_text);
			
			int id = -1;
			EntityType type = EntityType.UNKNOWN;
			String name = "Anonymous", body = "Error";
			body = twilioParams.get("Body");
			
			//search the familyDB for the incoming phone number
			ONCFamily fam = familyDB.getFamilyByPhoneNumber(DBManager.getCurrentSeason(), rec_text.getFrom().substring(2));
			if(fam != null)
			{
				id = fam.getID();
				type = EntityType.FAMILY;
				name = fam.getFirstName() + " " + fam.getLastName();
			}
			else
			{
				//search partner DB
				ONCPartner partner = partnerDB.getPartnerByPhoneNumber(DBManager.getCurrentSeason(), rec_text.getFrom().substring(2));
				if(partner != null)
				{
					id = partner.getID();
					type = EntityType.PARTNER;
					name = partner.getLastName();
				}
			}
			
			//add the received message to the SMS DB
			SMSStatus status;
			try
			{
				status = SMSStatus.valueOf(twilioParams.get("SmsStatus").toUpperCase());
			}
			catch (IllegalArgumentException iae)
			{
				status = SMSStatus.ERROR;
			}
			catch (NullPointerException npe)
			{
				status = SMSStatus.ERROR;
			}
			
			ONCSMS sms = new ONCSMS(-1,type, id, rec_text.getFrom(), SMSDirection.INBOUND, body, status);
			smsDB.add(DBManager.getCurrentSeason(), sms);					
			
			String replyContent;
			if(body.contains("C"))
				replyContent ="thank you for confirming ONC gift delivery on 12/15 between 1-4pm";
			else
				replyContent ="sorry you were unable to confirm ONC gift delivery. Please contact "
						+ "your school counselor for assistance";
				
			String response = String.format("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
				"<Response><Message>%s, %s></Response>", name, replyContent );
			
			htmlResponse = new HtmlResponse(response, HttpCode.Ok);
		
			sendXMLResponse(t, htmlResponse); 
		}		
    }
	
	void simulateSMSReceive()
	{
		//debug map
		Map<String, String> twilioParams = new HashMap<String,String>();
		twilioParams.put("AccountSid", "AC146e471a06cd920fd91862d2965bebf8");
		twilioParams.put("MessageSid", "AC146e471a06cd920fd91862d2965bebf8");
		twilioParams.put("Body", "Test #2");
		twilioParams.put("ToZip", "");
		twilioParams.put("ToCity", "");
		twilioParams.put("FromState", "VA");
		twilioParams.put("ToState", "VA");
		twilioParams.put("SmsSid", "SM8b8fd45be055016445610c29d44f2934");
		twilioParams.put("To", "+15716654028");
		twilioParams.put("ToCountry", "US");
		twilioParams.put("FromCountry", "US");
		twilioParams.put("SmsMessageSid", "SM8b8fd45be055016445610c29d44f2934");
		twilioParams.put("ApiVersion", "2010-04-01");
		twilioParams.put("FromCity", "ARLINGTON");
		twilioParams.put("SmsStatus", "received");
		twilioParams.put("NumSegments", "1");
		twilioParams.put("NumMedia", "0");
		twilioParams.put("From", "+17039262396");
		twilioParams.put("FromZip", "22214");
		
		TwilioSMSReceive rec_text = new TwilioSMSReceive(twilioParams);
		
		//add the received message to the Inbound SMS database
		inboundSMSDB.add(DBManager.getCurrentSeason(), rec_text);
		
		int id = -1;
		EntityType type = EntityType.UNKNOWN;
		String name = "Anonymous", body = "Error";
		body = twilioParams.get("Body");
		
		//search the familyDB for the incoming phone number
		ONCFamily fam = familyDB.getFamilyByPhoneNumber(DBManager.getCurrentSeason(), rec_text.getFrom().substring(2));
		if(fam != null)
		{
			id = fam.getID();
			type = EntityType.FAMILY;
			name = fam.getFirstName() + " " + fam.getLastName();
		}
		else
		{
			//search partner DB
			ONCPartner partner = partnerDB.getPartnerByPhoneNumber(DBManager.getCurrentSeason(), rec_text.getFrom().substring(2));
			if(partner != null)
			{
				id = partner.getID();
				type = EntityType.PARTNER;
				name = partner.getLastName();
			}
		}
		
		//add the received message to the SMS DB
		ONCSMS sms = new ONCSMS(-1,type, id, rec_text.getFrom(), SMSDirection.INBOUND, body, SMSStatus.RECEIVED);
		smsDB.add(DBManager.getCurrentSeason(), sms);					
		
		String response = String.format("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
			"<Response><Message>%s, a great %s, thank you for sending %s to Our Neighbor's Child</Message></Response>",
			name, type.toString().toLowerCase(), body );
		
		System.out.println(String.format("SMSHandler.SimulateSMSReceive: response= %s", response));	
	}
}
