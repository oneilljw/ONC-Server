package oncserver;

import java.io.FileNotFoundException;

import java.io.IOException;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsExchange;

import ourneighborschild.EntityType;
import ourneighborschild.ONCFamily;
import ourneighborschild.ONCSMS;
import ourneighborschild.SMSDirection;
import ourneighborschild.SMSStatus;

//handler for SMS
public class SMSHandler extends ONCWebpageHandler
{
	private ServerInboundSMSDB inboundSMSDB;
	private ServerSMSDB smsDB;
	private ServerFamilyDB familyDB;
	
	public SMSHandler() throws FileNotFoundException, IOException
	{
		this.inboundSMSDB = ServerInboundSMSDB.getInstance();
		this.smsDB = ServerSMSDB.getInstance();
		this.familyDB = ServerFamilyDB.getInstance();
//		this.partnerDB = ServerPartnerDB.getInstance();
		
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
			TwilioSMSReceive rec_SMS = new TwilioSMSReceive(createMap(params, TwilioSMSReceive.keys()));
			
			ServerUI.addDebugMessage(String.format("/sms-receive: %s", rec_SMS.toString()));
			
			inboundSMSDB.add(DBManager.getCurrentSeason(), rec_SMS);
			
			int id = -1;
			String messageID = rec_SMS.getMessageSid();
			EntityType type = EntityType.UNKNOWN;
			String name = "Anonymous";
			String body = rec_SMS.getBody();
			boolean bDeliveryConfirmed = body.equals("C") || body.toLowerCase().contains("confirmed");
			String replyContent = "We didn't not recognize the number you texted from and are unable to process your message.";
			
			SMSStatus status;
			try { status = SMSStatus.valueOf(rec_SMS.getSmsStatus().toUpperCase()); }
			catch (IllegalArgumentException iae) { status = SMSStatus.ERROR; }
			catch (NullPointerException npe) { status = SMSStatus.ERROR; }
			
			//search the familyDB for the incoming phone number
			ONCFamily fam = familyDB.smsMessageReceived(DBManager.getCurrentSeason(), rec_SMS, bDeliveryConfirmed);
			if(fam != null)
			{
				id = fam.getID();
				type = EntityType.FAMILY;
				name = fam.getFirstName() + " " + fam.getLastName();
				
				if( bDeliveryConfirmed)
					replyContent =String.format("%s, thank you for confirming ONC gift delivery on 12/15 "
												+ "between 1-4pm", name);
				else
					replyContent =String.format("%s, sorry you were unable to confirm ONC gift delivery. "
											+ "Please contact your school counselor for assistance", name);
			}
//			else
//			{
//				//search partner DB
//				ONCPartner partner = partnerDB.getPartnerByPhoneNumber(DBManager.getCurrentSeason(), rec_SMS.getFrom().substring(2));
//				if(partner != null)
//				{
//					id = partner.getID();
//					type = EntityType.PARTNER;
//					name = partner.getLastName();
//				}
//			}
			
		
			//add the received message to the SMS DB
			ONCSMS sms = new ONCSMS(-1,messageID, type, id, rec_SMS.getFrom(), SMSDirection.INBOUND, body, status);
			smsDB.add(DBManager.getCurrentSeason(), sms);					
				
			String response = String.format("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
				"<Response><Message>%s</Message></Response>", replyContent);
			
			htmlResponse = new HtmlResponse(response, HttpCode.Ok);
		
			sendXMLResponse(t, htmlResponse); 
		}
		else if(requestURI.equals("/sms-update"))
		{
			//add a received Twilio object log message, add the object to the in-bound DB, and
			//update the ONCSMS object that should have been previously added
			TwilioSMSReceive rec_SMS = new TwilioSMSReceive(createMap(params, TwilioSMSReceive.keys()));
			
			ServerUI.addDebugMessage(String.format("/sms-update: %s", rec_SMS.toString()));
			
			inboundSMSDB.add(DBManager.getCurrentSeason(), rec_SMS);
			smsDB.updateSMSMessage(DBManager.getCurrentSeason(), rec_SMS);
			
			//send a quick response back to the browser -- THIS MAY WANT TO MOVE TO THE TOP OF THE METHOD
			sendNoContentResponseHeader(t);
		}
    }
/*	
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
		String name = "Anonymous";
		String body = twilioParams.get("Body");
		String messageSID = twilioParams.get("MessageSID");
		
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
		ONCSMS sms = new ONCSMS(-1,messageSID,type, id, rec_text.getFrom(), SMSDirection.INBOUND, body, SMSStatus.RECEIVED);
		smsDB.add(DBManager.getCurrentSeason(), sms);					
		
		String response = String.format("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
			"<Response><Message>%s, a great %s, thank you for sending %s to Our Neighbor's Child</Message></Response>",
			name, type.toString().toLowerCase(), body );
		
		System.out.println(String.format("SMSHandler.SimulateSMSReceive: response= %s", response));	
	}
*/	
}
