package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ourneighborschild.DNSCode;
import ourneighborschild.EntityType;
import ourneighborschild.ONCFamily;
import ourneighborschild.ONCSMS;
import ourneighborschild.ONCUser;
import ourneighborschild.SMSDirection;
import ourneighborschild.SMSRequest;
import ourneighborschild.SMSStatus;

public class ServerSMSDB extends ServerSeasonalDB
{
	private static final int SMS_RECEIVE_DB_HEADER_LENGTH = 9;
	
	private static ServerSMSDB instance = null;
	private static List<SMSDBYear> smsDB;
	
	private ServerFamilyDB familyDB;
	
	private ServerSMSDB() throws FileNotFoundException, IOException
	{
		//create the INBOUND SMS data base
		smsDB = new ArrayList<SMSDBYear>();
						
		//populate the adult data base for the last TOTAL_YEARS from persistent store
		for(int year = DBManager.getBaseSeason(); year < DBManager.getBaseSeason() + DBManager.getNumberOfYears(); year++)
		{
			//create the meal list for each year
			SMSDBYear smsDBYear = new SMSDBYear(year);
							
			//add the list of sms for the year to the db
			smsDB.add(smsDBYear);
							
			//import the sms messages from persistent store
			importDB(year, String.format("%s/%dDB/SMSDB.csv",
					System.getProperty("user.dir"),
						year), "SMS DB",SMS_RECEIVE_DB_HEADER_LENGTH);
			
			//set the next id
			smsDBYear.setNextID(getNextID(smsDBYear.getList()));
		}
		
		//initialize the Family DB interface
		familyDB = ServerFamilyDB.getInstance();
	}
	
	public static ServerSMSDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerSMSDB();
		
		return instance;
	}
	
	String getSMSMessages(int year)
	{
		Gson gson = new Gson();
		Type listOfSMS = new TypeToken<ArrayList<ONCSMS>>(){}.getType();
		
		String response = gson.toJson(smsDB.get(DBManager.offset(year)).getList(), listOfSMS);
		return response;	
	}
	
	ONCSMS add(int year, ONCSMS addedSMS)
	{			
		//retrieve the sms data base for the year
		SMSDBYear smsDBYear = smsDB.get(DBManager.offset(year));
								
		//set the new ID for the added SMS
		int smsID = smsDBYear.getNextID();
		addedSMS.setID(smsID);
		
		//add the new sms to the data base
		smsDBYear.add(addedSMS);
		smsDBYear.setChanged(true);
		
		//notify all in-year clients of the add
		Gson gson = new Gson();
		ClientManager clientMgr = ClientManager.getInstance();
		clientMgr.notifyAllInYearClients(year, "ADDED_SMS" + gson.toJson(addedSMS, ONCSMS.class));
							
		return addedSMS;
	}
	
	/*****
	 * Must add to the DB and send back to clients before asking twilio to send. 
	 * @param json
	 * @return
	 */
	String processSMSRequest(String json)
	{
		Gson gson = new Gson();
		SMSRequest request = gson.fromJson(json, SMSRequest.class);

		List<ONCSMS> smsRequestList = new ArrayList<ONCSMS>();
		   
		if(request.getMessageID() > 0  && request.getMessageID() < 3 &&
			request.getPhoneChoice() > 0  && request.getPhoneChoice() < 3 &&
			 request.getEntityType() == EntityType.FAMILY)
	    {
			//for each family in the request, create a ONCSMS request 
			for(Integer famID : request.getEntityIDList() )
			{
				ONCFamily f = familyDB.getFamily(request.getYear(), famID);
				
				if(f != null)
				{
					String twilioFormattedPhoneNum = getTwilioFormattedPhoneNumber(f, request.getPhoneChoice());
					String message = getSMSBody(f, request.getMessageID());
					
					if(twilioFormattedPhoneNum != null)
						smsRequestList.add(new ONCSMS(-1, "", EntityType.FAMILY, famID, twilioFormattedPhoneNum,
							SMSDirection.OUTBOUND_API, message, SMSStatus.REQUESTED));
					else
						smsRequestList.add(new ONCSMS(-1, "", EntityType.FAMILY, famID, twilioFormattedPhoneNum,
							SMSDirection.OUTBOUND_API, message, SMSStatus.ERR_NO_PHONE));
				}
			}
	    }	

		//if the request list isn't empty, ask twilio to validate the phone numbers in the request list
		//can accept SMS messages.
		String response = "SMS_REQUEST_FAILED";
		if(!smsRequestList.isEmpty())
		{
			//ask twilio to send the SMS's
			TwilioIF twilioIF;
			try
			{
				twilioIF = TwilioIF.getInstance();
				response = twilioIF.validateSMSList(request, smsRequestList);
			}
			catch (IOException e)
			{
				response = "TWILIO IF Error " + e.getMessage();
			}
		}
			
	    return response;
	}
	
	String getTwilioFormattedPhoneNumber(ONCFamily f, int phoneChoice)
	{
		String twilioFormattedPhoneNum = null;	//initialize returned number
		
		if(phoneChoice > 0 && phoneChoice < 3)	//validate the phone choice range
		{	
			String[] phones = f.getCellPhone().split("\\r?\\n");
			String cellPhone = phones[0];
			
			if(phoneChoice == 1)	//primary phone
			{
				if(!f.getHomePhone().isEmpty() && f.getHomePhone().trim().length() == 12)
					twilioFormattedPhoneNum = String.format("+1%s", formatPhoneNumber(f.getHomePhone()));
				else if(!cellPhone.isEmpty() && cellPhone.trim().length() == 12)
					twilioFormattedPhoneNum = String.format("+1%s", formatPhoneNumber(cellPhone));
			}
			
			//we've found the family, now check to see if we have a phone number to use
			//if the request is to use the alternate phone, use it if it's valid
			//if not add the primary phone.
			if(phoneChoice == 2)	//alternate phone
			{
				if(!cellPhone.isEmpty() && cellPhone.trim().length() == 12)
					twilioFormattedPhoneNum = String.format("+1%s", formatPhoneNumber(cellPhone));
				else if(!f.getHomePhone().isEmpty() && f.getHomePhone().trim().length() == 12)
					twilioFormattedPhoneNum = String.format("+1%s", formatPhoneNumber(f.getHomePhone()));
			}
		}
		
		return twilioFormattedPhoneNum;
	}
	
	String getSMSBody(ONCFamily f, int messageID)
	{
		//determine if the family has a different address for delivery
		String houseNum, street, unit;
		String[] addressPart;
		if(!f.getSubstituteDeliveryAddress().isEmpty() && 
			(addressPart = f.getSubstituteDeliveryAddress().split("_")).length == 5)
		{
			houseNum = addressPart[0].trim();
			street = addressPart[1].trim();
			unit = addressPart[2].trim();
		}
		else
		{
			houseNum = f.getHouseNum().trim();
			street = f.getStreet().trim();
			unit = f.getUnit().trim();
		}
		
		//determine which message to send based on language and message ID
		if(f.getLanguage().equals("Spanish"))
		{	
			if(messageID == 1 && f.getDNSCode() == -1)
			{
				return String.format("Our Neighbor's Child (ONC): Responde \"YES\" para confirmar que un adulto "
					+ "estará en casa para recibir los regalos de sus hijos el domingo 15 de diciembre. "
					+ "Los voluntarios entregarán a %s %s %s entre la 1 y las 4 de la tarde. Responde \"NO\" "
					+ "si no puedes confirmar que un adulto estará en casa para la entrega de regalos el 15 de diciembre.",
					houseNum, street, unit);
			}
			else if(messageID == 1 && f.getDNSCode() == DNSCode.DNS_CODE_WAITLIST)
			{
				return String.format("Our Neighbor's Child (ONC): La remisión de la lista de espera "
				 		+ "ha sido aceptado. Responda \"YES\" para confirmar que un adulto estará en casa para "
				 		+ "recibir los regalos por edad para los ninos con menos de doce años el domingo, "
				 		+ "15 de diciembre. Los voluntarios entregarán a %s %s %s entre la 1 y las 4 de la tarde. "
				 		+ "Responda \"NO\" si no puedes confirmar que un adulto estará en casa para la entrega "
				 		+ "de los regalos el 15 de diciembre.", 
						houseNum, street, unit);
			}
			else
			{
				//message ID must be 2
				return String.format("POR FAVOR, NO RESPONDA.\n" 
						+ "Our Neighbor's Child (ONC): Este mensaje es un recordatorio de entrega de regalos. "
						+ "Un voluntario de ONC entregará los regalos de sus hijos mañana a %s %s %s entre "
						+ "las horas de 1 y 4pm. Un adulto debe estar presente en casa para aceptar la entrega.",
						houseNum, street, unit);
//				return String.format("Our Neighbors Child (ONC): Este es un recordatorio de entrega de regalos. "
//						+ "POR FAVOR NO RESPONDA. Un voluntario de ONC entregará los regalos de sus hijos mañana "
//						+ "a %s %s %s en cualquier momento entre la 1 y las 4 p.m. Un adulto debe estar en "
//						+ "casa para aceptar la entrega de regalos.",
//						houseNum, street, unit);
			}
		}
		else
		{	
			//if the family speaks any other primary language besides Spanish, we'll send SMS in English.
			if(messageID == 1 && f.getDNSCode() == -1)
			{	
				return String.format("Our Neighbor's Child (ONC): Reply \"YES\" to confirm an adult will be "
					+ "home to receive your children's gifts on Sunday, December 15. Volunteers will "
					+ "deliver to %s %s %s anytime between 1 and 4PM. Reply \"NO\" if you are unable to confirm an "
					+ "adult will be home for gift delivery on December 15.",
					houseNum, street, unit);
			}
			else if(messageID == 1 && f.getDNSCode() == DNSCode.DNS_CODE_WAITLIST)
			{	
				return String.format("Our Neighbor's Child (ONC): Your \"Wait List\" referral has been "
					+ "accepted. Reply \"YES\" to confirm an adult will be home to receive age appropriate gifts "
					+ "for each child 12 and under on Sunday, December 15. Volunteers will deliver to %s %s %s "
					+ "anytime between 1 and 4PM. Reply \"NO\" if you are unable to confirm an adult will "
					+ "be home for gift delivery on December 15.",
					houseNum, street, unit);
			}
			else
			{
				//message id must be 2
				return String.format("Our Neighbor’s Child (ONC): This is a gift delivery reminder. PLEASE DO "
						+ "NOT RESPOND. An ONC volunteer will deliver your children's gifts tomorrow to "
						+ "%s %s %s anytime between 1 and 4pm. An adult must be home to accept gift delivery.",
						houseNum, street, unit);
			}
		}
	}
	
	//callback from Twilio IF when validation task completes
	void twilioValidationRequestComplete(SMSRequest request, List<ONCSMS> postValidationSMSRequestList)
	{
		//add the list of valid and invalid SMS requests to the database
		List<String> addedSMSList = addSMSRequestList(request.getYear(), postValidationSMSRequestList);
		
		//notify all in year clients of added SMS messages. This is the first time the messages
		//are sent to clients. The request list status will show whether the request had a phone
		//number and if so, if it was a mobile phone able to accept SMS messages. 
		if(addedSMSList != null && !addedSMSList.isEmpty())
		{
			ClientManager clientMgr = ClientManager.getInstance();
			clientMgr.notifyAllInYearClients(request.getYear(), addedSMSList);
		}
		
		//ask twilio to send the validated sms list. Only need to send requests that have
		//status = SMSStatus.VALIDATED
		List<ONCSMS> sendRequestList = new ArrayList<ONCSMS>();
		for(ONCSMS sms : postValidationSMSRequestList)
			if(sms.getStatus() == SMSStatus.VALIDATED)
				sendRequestList.add(sms);
		
		String response = "SMS_REQUEST_FAILED";
		if(!sendRequestList.isEmpty())
		{
			//ask twilio to send the SMS's
			TwilioIF twilioIF;
			try
			{
				twilioIF = TwilioIF.getInstance();
				response = twilioIF.sendSMSList(request,sendRequestList);
			}
			catch (IOException e)
			{
				response = "TWILIO IF Error " + e.getMessage();
			}
		}
		
		ServerUI.addDebugMessage(response);
	}

	List<String> addSMSRequestList(int year, List<ONCSMS> reqList)
	{	
		Gson gson = new Gson();
		List<String> addedSMSList = new ArrayList<String>();
	
		if(!reqList.isEmpty())
		{
			//retrieve the sms data base for the year
			SMSDBYear smsDBYear = smsDB.get(DBManager.offset(year));
		
			for(ONCSMS reqSMS : reqList)
			{
				//set the new ID for each requested SMS
				int smsID = smsDBYear.getNextID();
				reqSMS.setID(smsID);
					
				//add the new sms to the data base and add the json to the response list
				smsDBYear.add(reqSMS);
				addedSMSList.add(String.format("ADDED_SMS%s", gson.toJson(reqSMS, ONCSMS.class)));
			}
		
			smsDBYear.setChanged(true);
		}
		
		return addedSMSList;
	}
	
	ONCSMS getSMSMessageBySID(int year, String mssgSID)
	{
		//retrieve the sms data base for the year
		List<ONCSMS> searchList = smsDB.get(DBManager.offset(year)).getList();
		
		int index = 0;
		while(index < searchList.size() && !searchList.get(index).getMessageSID().equals(mssgSID))
			index++;
		
		return index < searchList.size() ? searchList.get(index) : null;
	}
	
	ONCSMS updateSMSMessage(int year, TwilioSMSReceive rec_text)
	{
		//retrieve the sms data base for the year
		SMSDBYear smsDBYear = smsDB.get(DBManager.offset(year));
		List<ONCSMS> searchList = smsDBYear.getList();
		
		int i;
		for(i = searchList.size()-1; i >= 0; i--)
			if(searchList.get(i).getDirection() == SMSDirection.OUTBOUND_API &&
				searchList.get(i).getPhoneNum().equals(rec_text.getTo()))
				break;
		
		//Only need to update the SMS if we can find it and it hasn't already been delivered.
		ONCSMS updateSMS;
		if(i >= 0 && (updateSMS = searchList.get(i)).getStatus().compareTo(SMSStatus.DELIVERED) < 0)
		{
			//update the ONCSMS object with new status
			try
			{
				SMSStatus newStatus = SMSStatus.valueOf(rec_text.getSmsStatus().toUpperCase());
				updateSMS.setStatus(newStatus);
				updateSMS.setTimestamp(rec_text.getTimestamp());
				
				smsDBYear.setChanged(true);
			}
			catch(IllegalArgumentException iae)
			{
				//don't perform the update
				ServerUI.addLogMessage(String.format("SMSHdlr: SMSStatus exception for received status %s",
						rec_text.getSmsStatus()));
			}
			catch(NullPointerException ioe)
			{
				//don't perform the update
				ServerUI.addLogMessage(String.format("SMSHdlr: SMSStatus exception for received status %s",
						rec_text.getSmsStatus()));
			}
			
			Gson gson = new Gson();
			ClientManager clientMgr = ClientManager.getInstance();
			clientMgr.notifyAllInYearClients(year, "UPDATED_SMS" + gson.toJson(updateSMS, ONCSMS.class));
			
			//if the SMS Status has changed to "Delivered" notify the Family DB to check to see if
			//the family status should change
			if(updateSMS.getStatus() == SMSStatus.DELIVERED)
				familyDB.checkFamilyStatusOnSMSStatusCallback(year, updateSMS);
			
			return updateSMS;
		}
		else
		{
			if(i == 0)
				ServerUI.addLogMessage(String.format("ServSMSDB: unable to find ONCSMS phone#= %s", rec_text.getTo()));	
			return null;
		}
	}
	
	@Override
	void createNewSeason(int year)
	{
		//create a new SMS data base year for the year provided in the year parameter
		//The sms db year list is initially empty prior to receipt of in-bound SMS messages,
		//so all we do here is create a new SMSDBYear for the new year and save it.
		SMSDBYear smsDBYear = new SMSDBYear(year);
		smsDB.add(smsDBYear);
		smsDBYear.setChanged(true);	//mark this db for persistent saving on the next save event
	}

	@Override
	void addObject(int year, String[] nextLine)
	{
		//Get the sms list for the year and add create and add the sms
		SMSDBYear smsDBYear = smsDB.get(DBManager.offset(year));
		smsDBYear.add(new ONCSMS(nextLine));
	}

	@Override
	void save(int year)
	{
		//save the INBOUND sms
		String[] header = {"ID", "Message SID", "Type", "Entity ID", "Phone Num", "Direction", "Body", "Status", "Timestamp"};
		
		SMSDBYear smsDBYear = smsDB.get(DBManager.offset(year));
		if(smsDBYear.isUnsaved())
		{
//			System.out.println(String.format("ServerAdultDB save() - Saving Adult DB, size= %d", adultDBYear.getList().size()));
			String path = String.format("%s/%dDB/SMSDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(smsDBYear.getList(), header, path);
			smsDBYear.setChanged(false);
		}
	}
	
	private class SMSDBYear extends ServerDBYear
    {
    		private List<ONCSMS> smsList;
    	
    		SMSDBYear(int year)
    		{
    			super();
    			smsList = new ArrayList<ONCSMS>();
    		}
    	
    		//getters
    		List<ONCSMS> getList() { return smsList; }
    	
    		void add(ONCSMS addedSMS) { smsList.add(addedSMS); }
    }

	@Override
	String add(int year, String addjson, ONCUser client)
	{
		// TODO Auto-generated method stub
		return null;
	}
}
