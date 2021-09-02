package oncserver;

import java.io.FileNotFoundException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsExchange;

import ourneighborschild.EntityType;
import ourneighborschild.FamilyHistory;
import ourneighborschild.FamilyStatus;
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
	private ServerFamilyHistoryDB familyHistoryDB;
	
	public SMSHandler() throws FileNotFoundException, IOException
	{
		this.inboundSMSDB = ServerInboundSMSDB.getInstance();
		this.smsDB = ServerSMSDB.getInstance();
		this.familyDB = ServerFamilyDB.getInstance();
		this.familyHistoryDB = ServerFamilyHistoryDB.getInstance();
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
			//create a list of the keys
			List<String> keyList = new ArrayList<String>();
			StringBuffer buff = new StringBuffer("/sms-receive: ");
			for(String key : params.keySet())
			{
				keyList.add(key);
				buff.append(String.format("%s= %s, ", key, params.get(key)));
			}
			
			ServerUI.addDebugMessage(buff.toString());
			TwilioSMSReceive rec_SMS = new TwilioSMSReceive(createMap(params, keyList));
			
			inboundSMSDB.add(DBManager.getCurrentSeason(), rec_SMS);
			
			int id = -1;
			String messageID = rec_SMS.getMessageSid();
			EntityType type = EntityType.UNKNOWN;
			String name = "";
			String body = rec_SMS.getBody().trim();
			String replyContent = "Thank you for your response.";
			
			SMSStatus status;
			try { status = SMSStatus.valueOf(rec_SMS.getSmsStatus().toUpperCase()); }
			catch (IllegalArgumentException iae) { status = SMSStatus.ERROR; }
			catch (NullPointerException npe) { status = SMSStatus.ERROR; }
			
			//determine if this is a response to the delivery day reminder
			boolean bDeliveryTimeframe = ServerGlobalVariableDB.isDayBeforeOrDeliveryDay(DBManager.getCurrentSeason());
			boolean bConfirmingBody = body.equals("yes") || body.equals("YES") || body.equals("Yes");
			boolean bDecliningBody = body.equals("no") || body.equals("NO") || body.equals("No");
	
			//search the familyDB for the incoming phone number
			ONCFamily fam = familyDB.getFamilyBySMS(DBManager.getCurrentSeason(), rec_SMS);
			FamilyHistory lastFamHistory = familyHistoryDB.getLastFamilyHistory(DBManager.getCurrentSeason(), fam.getID());

			if(fam != null)
			{
				id = fam.getID();
				type = EntityType.FAMILY;
				name = fam.getFirstName() + " " + fam.getLastName();
		
				if(bDeliveryTimeframe && lastFamHistory.getFamilyStatus() == FamilyStatus.Confirmed)
					replyContent = String.format("%s, our automated messaging system is not monitored and not able to process your response. Thank you.", name);
				else if(bConfirmingBody)
					replyContent = String.format("%s, thank you for your response", name);
				else if(bDecliningBody)
					replyContent = String.format("%s, thank you for your response", name);
				else
					replyContent = "Thank you for your response";
				
				familyHistoryDB.checkFamilyStatusOnSmsReceived(DBManager.getCurrentSeason(), fam, bDeliveryTimeframe,
														bConfirmingBody, bDecliningBody);
			}
			
			//add the received message to the SMS DB
			ONCSMS sms = new ONCSMS(-1,messageID, type, id, rec_SMS.getFrom(), SMSDirection.INBOUND, body, status);
			smsDB.add(DBManager.getCurrentSeason(), sms);					
				
			String response = String.format("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
				"<Response><Message><Body>%s</Body></Message></Response>", replyContent);
			
			htmlResponse = new HtmlResponse(response, HttpCode.Ok);
		
			sendXMLResponse(t, htmlResponse); 
		}
		else if(requestURI.equals("/sms-update"))
		{
			///show the params & create a list of the keys
			List<String> keyList = new ArrayList<String>();
			StringBuffer buff = new StringBuffer("/sms-update: ");
			for(String key : params.keySet())
			{
				keyList.add(key);
				buff.append(String.format("%s= %s, ", key, params.get(key)));
			}
			
			ServerUI.addDebugMessage(buff.toString());
			
			//add a received Twilio object log message, add the object to the in-bound DB, and
			//update the ONCSMS object that should have been previously added
			TwilioSMSReceive rec_SMS = new TwilioSMSReceive(createMap(params, keyList));
		
			inboundSMSDB.add(DBManager.getCurrentSeason(), rec_SMS);
			smsDB.updateSMSMessage(DBManager.getCurrentSeason(), rec_SMS);
			
			//send a quick response back to the browser -- THIS MAY WANT TO MOVE TO THE TOP OF THE METHOD
			sendNoContentResponseHeader(t);
		}
		else if(requestURI.startsWith("/deliveryimage"))
		{
			//callback to get the  MediaURL png image
			if(params.containsKey("year") && params.containsKey("famid"))
			{
				//get the .png image from the database
				String year = (String) params.get("year");
				String famid = (String) params.get("famid");
				String path = String.format("%s/%sDB/Confirmations/confirmed%s.png", System.getProperty("user.dir"),year,famid);
				try
				{
					sendFile(t, "image/png", path);
				}
				catch (IOException ioe)
				{
					sendCachedFile(t, "image/png", "error-404", false);
				}
			}
			else
				sendCachedFile(t, "image/png", "error-404", false);
		}
		else if(requestURI.startsWith("/lookup"))
		{
			//callback to perform a phone number lookup
    		WebClient wc = clientMgr.findAndValidateClient(t.getRequestHeaders());
			if(wc != null & params.containsKey("phonenumber"))
			{
				htmlResponse = ServerSMSDB.getPhoneNumberJSONP((String) params.get("phonenumber"), (String) params.get("callback"));
				
			}
			else
				htmlResponse = invalidTokenReceivedToJsonRequest("Error", (String) params.get("callback"));
			
    		sendHTMLResponse(t, htmlResponse);
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
