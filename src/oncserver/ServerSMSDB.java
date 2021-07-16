package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

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
	private ServerFamilyHistoryDB familyHistoryDB;
	
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
		
		//initialize the FamilyDB and GlobalVariableDB interface
		familyDB = ServerFamilyDB.getInstance();
		familyHistoryDB = ServerFamilyHistoryDB.getInstance();
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
		   
		if(request.getMessageID() == -1 && request.getPhoneChoice() > 0  && request.getPhoneChoice() < 3 &&
				 request.getEntityType() == EntityType.FAMILY)
		{
			//single sms message
			//for each family in the request, create a ONCSMS request 
			for(Integer famID : request.getEntityIDList() )
			{
				ONCFamily f = familyDB.getFamily(request.getYear(), famID);
				
				if(f != null)
				{
					String twilioFormattedPhoneNum = getTwilioFormattedPhoneNumber(f, request.getPhoneChoice());
					
					if(twilioFormattedPhoneNum != null && request.hasAttachment())
					{
						String mediaURL = String.format("https://oncdms.org:8902/deliveryimage?year=%d&famid=%d",
								TwilioIF.getSMSBaseURL(), request.getYear(), f.getID());
						
						smsRequestList.add(new ONCSMS(-1, "", EntityType.FAMILY, famID, twilioFormattedPhoneNum,
							SMSDirection.OUTBOUND_API, request.getMessage(), mediaURL, SMSStatus.REQUESTED));
					}
					else if(twilioFormattedPhoneNum != null && !request.hasAttachment())
					{
						smsRequestList.add(new ONCSMS(-1, "", EntityType.FAMILY, famID, twilioFormattedPhoneNum,
								SMSDirection.OUTBOUND_API, request.getMessage(), SMSStatus.REQUESTED));
					}
				}
			}
		}
		else if(request.getMessageID() > 0  && request.getMessageID() < 6 &&
			request.getPhoneChoice() > 0  && request.getPhoneChoice() < 3 &&
			 request.getEntityType() == EntityType.FAMILY)
	    {
			//multiple sms message
			//get the pickup locations
			PickUpLocations puLocations = new PickUpLocations();
			
			//for each family in the request, create a ONCSMS request 
			for(Integer famID : request.getEntityIDList() )
			{
				ONCFamily f = familyDB.getFamily(request.getYear(), famID);
				
				if(f != null)
				{
					String twilioFormattedPhoneNum = getTwilioFormattedPhoneNumber(f, request.getPhoneChoice());
					String message = get2021SMSBody(f, request, puLocations.getPickUpLocation(f));
					
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
/*	
	String getSMSBody(ONCFamily f, SMSRequest request) //int messageID)
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
		String zDeliveryDate = globalVarDB.getDeliveryDayOfMonth(request.getYear(), f.getLanguage());
		
		//determine which message to send based on language and message ID
		if(f.getLanguage().equals("Spanish"))
		{	
			if(request.getMessageID() == 1)
			{
				return String.format("Our Neighbor's Child (ONC): Responde \"YES\" para confirmar que un adulto "
					+ "estará en casa para recibir los regalos de sus hijos el domingo %s diciembre. "
					+ "Los voluntarios entregarán a %s %s %s entre la 1 y las 4 de la tarde. Responde \"NO\" "
					+ "si no puedes confirmar que un adulto estará en casa para la entrega de regalos el %s diciembre.",
					zDeliveryDate, houseNum, street, unit, zDeliveryDate);
			}
//			else if(request.getMessageID() == 1 && f.getDNSCode() == DNSCode.WAITLIST)
//			{
//				return String.format("Our Neighbor's Child (ONC): La remisión de la lista de espera "
//				 		+ "ha sido aceptado. Responda \"YES\" para confirmar que un adulto estará en casa para "
//				 		+ "recibir los regalos por edad para los ninos con menos de doce años el domingo, "
//				 		+ "%s diciembre. Los voluntarios entregarán a %s %s %s entre la 1 y las 4 de la tarde. "
//				 		+ "Responda \"NO\" si no puedes confirmar que un adulto estará en casa para la entrega "
//				 		+ "de los regalos el %s diciembre.", 
//				 		zDeliveryDate, houseNum, street, unit, zDeliveryDate);
//			}
			else
			{
				//message ID must be 2
				return String.format("POR FAVOR, NO RESPONDA.\n" 
						+ "Our Neighbor's Child (ONC): Este mensaje es un recordatorio de entrega de regalos. "
						+ "Un voluntario de ONC entregará los regalos de sus hijos mañana a %s %s %s entre "
						+ "las horas de 1 y 4pm. Un adulto debe estar presente en casa para aceptar la entrega.",
						houseNum, street, unit);
			}
		}
		else
		{	
			//if the family speaks any other primary language besides Spanish, we'll send SMS in English.
			if(request.getMessageID() == 1)
			{	
//				return String.format("Our Neighbor's Child (ONC): Reply \"YES\" to confirm an adult will be "
//					+ "home to receive your children's gifts on Sunday, December %s. Volunteers will "
//					+ "deliver to %s %s %s anytime between 1 and 4PM. Reply \"NO\" if you are unable to confirm an "
//					+ "adult will be home for gift delivery on December %s.",
//					zDeliveryDate, houseNum, street, unit, zDeliveryDate);
//				
				return String.format("Our Neighbor's Child (ONC) recently sent you an email with instructions on where "
						+ "to pick up gifts for your family. Reply \"YES\" to confirm an adult will be "
						+ "able to pick up your children's gifts on Sunday, December %s anytime between 1 and 4PM. "
						+ "After checking your spam or junk folder, reply \"NO\" if you did not receive an email from ONC.",
						zDeliveryDate, houseNum, street, unit, zDeliveryDate);
			}
//			else if(request.getMessageID() == 1 && f.getDNSCode() == DNSCode.WAITLIST)
//			{	
//				return String.format("Our Neighbor's Child (ONC): Your \"Wait List\" referral has been "
//					+ "accepted. Reply \"YES\" to confirm an adult will be home to receive age appropriate gifts "
//					+ "for each child 12 and under on Sunday, December %s. Volunteers will deliver to %s %s %s "
//					+ "anytime between 1 and 4PM. Reply \"NO\" if you are unable to confirm an adult will "
//					+ "be home for gift delivery on December %s.", 
//					zDeliveryDate, houseNum, street, unit, zDeliveryDate);
//			}
			else
			{
				//message id must be 2
				return String.format("Our Neighbor’s Child (ONC): This is a gift pick reminder. PLEASE DO "
						+ "NOT RESPOND. Your children's gifts must be picked up tomorrow at LOCATION "
						+ "anytime between 1 and 4pm.",
						houseNum, street, unit);
			}
		}
	}
*/
/*
	String get2020SMSBody(ONCFamily f, SMSRequest request, PickUpLocation puLocation) //int messageID)
	{
		//determine which message to send based on message ID
		if(request.getMessageID() == 1)
		{		
			return String.format("Our Neighbor's Child (ONC) received the request you sent to your child's school for holiday GIFT assistance.\n\n"
					+ "ONC sent an email with instuctions for Gift Pick Up to this email address: %s.\n\n"
					+ "If this is correct and you received and understood the email, please reply YES.\n\n"
					+ "If the email address is incorrect, please reply with the correct email address or reply NO if you do not have an email address."
					, f.getEmail().trim());
		}
		else if(request.getMessageID() == 2)
		{	
			return String.format("Our Neighbor's Child (ONC) received the request you sent to your child's school for holiday GIFT assistance.\n\n"
				+ "The truck containing your child's gifts will be in the parking lot of %s (%s) on Sunday, December 13 from 1PM to 4PM.\n%s\n\n"
				+ "You (or someone you send) may receive your gifts if you bring your ONC Family #: %s and the name of the Head of Household who applied for assistance: %s %s.\n\n"
				+ "Please write this information on a piece of paper and place it visibly in your vehicle's dashboard.\n\n"
				+ "Only you or the person picking up your gifts should have this information. Vehicle occupants must wear a mask(s) and remain inside the vehicle for additional instructions.\n\n"
				+ "Please reply YES if you understand these instructions and someone will bring this information to pick up your gifts on Sunday, December 13 between 1PM and 4PM."
				,puLocation.getName(), puLocation.getAddress(), puLocation.getGoogleMapURL(), f.getONCNum(), f.getFirstName(), f.getLastName() );
		}
		else if(request.getMessageID() == 3 && f.getLanguage().equals("Spanish"))
		{
			return String.format("Esto es un recordatorio de Our Neighbor's Child (ONC) sobre los regalos navideños de su(s) hijo(s).\n\n"
				+ "Los regalos de sus hijos deben recoger mañana entre la 1:00 de la tarde y las 4:00 de la tarde.\n"
				+ "Un camión con los regalos de su hijo estará en el estacionamiento de la %s en el %s hasta las 4:00 de la tarde.\n\n"
				+ "Por favor traiga esta información con usted mañana: El número de ONC: %s, y el nombre el jefe o la jefa de la familia: %s %s.\n\n"
				+ "Los ocupantes del vehículo tienen que usar máscaras, seguir las señales direccionales, y quedarse dentro del vehículo para obtener instrucciones adicionales."
				,puLocation.getName(), puLocation.getAddress(), f.getONCNum(), f.getFirstName(), f.getLastName());
		}
		else if(request.getMessageID() == 3 && !f.getLanguage().equals("Spanish"))
		{
			return String.format("This is a reminder from Our Neighbor's Child (ONC) about your child(ren)'s holiday gifts.\n\n"
					+ "Your children's gifts must be picked tomorrow between 1:00pm and  4:00pm.\n"
					+ "A truck with your child's gifts will be in the %s parking lot at %s until 4:00pm.\n\n"
					+ "Please bring this information with you tomorrow: ONC # %s, Head of Household Name: %s %s.\n\n"
					+ "Vehicle occupants must wear masks, follow directional signs and remain inside the vehicle for additional instructions."
					,puLocation.getName(), puLocation.getAddress(), f.getONCNum(), f.getFirstName(), f.getLastName());
		}
		else if(request.getMessageID() == 4 && f.getLanguage().contentEquals("Spanish"))
		{
			return String.format("Esto es un recordatorio de Our Neighbor's Child (ONC) sobre los regalos navideños de su(s) hijo(s).\n\n"
					+ "¡Importante: deben recoger los regalos de sus hijos antes de las 4:00 de la tarde de hoy.\n\n"
					+ "El camión con los regalos de su hijo está en el estacionamiento de la %s en %s hasta las 4:00 de la tarde.\n\n"
					+ "Por favor traiga esta información con usted: El número de ONC: %s, y el nombre el jefe o la jefa de la familia: %s %s.\n\n"
					+ "Los ocupantes del vehículo tienen que usar máscaras, seguir las señales direccionales, y quedarse dentro del vehículo para obtener instrucciones adicionales."
					,puLocation.getName(), puLocation.getAddress(), f.getONCNum(), f.getFirstName(), f.getLastName());
		}
		else
			return String.format("This is a reminder from Our Neighbor's Child(ONC) about your child(ren)'s holiday gifts.\n\n"
					+ "All gifts MUST be picked up by 4PM today.\n"
					+ "The truck with your child's gifts is in the %s parking lot at %s until 4PM.\n\n"
					+ "Please bring this information with you: ONC # %s, Head of Household Name: %s %s.\n\n"
					+ "Vehicle occupants must wear masks, follow directional signs and remain inside the vehicle for additional instructions."
					,puLocation.getName(), puLocation.getAddress(), f.getONCNum(), f.getFirstName(), f.getLastName());		
	}
*/	
	String get2021SMSBody(ONCFamily f, SMSRequest request, PickUpLocation puLocation) //int messageID)
	{
		//determine which message to send based on message ID
		if(request.getMessageID() == 1)
		{		
			return String.format("Our Neighbor's Child (ONC) received the request you sent to your child's school for holiday GIFT assistance.\n\n"
					+ "ONC sent an email with instuctions for Gift Pick Up to this email address: %s.\n\n"
					+ "If this is correct and you received and understood the email, please reply YES.\n\n"
					+ "If the email address is incorrect, please reply with the correct email address or reply NO if you do not have an email address."
					, f.getEmail().trim());
		}
		else if(request.getMessageID() == 2)
		{	
			return String.format("Our Neighbor's Child (ONC) received the request you sent to your child's school for holiday GIFT assistance.\n\n"
				+ "The truck containing your child's gifts will be in the parking lot of %s (%s) on Sunday, December 13 from 1PM to 4PM.\n%s\n\n"
				+ "You (or someone you send) may receive your gifts if you bring your ONC Family #: %s and the name of the Head of Household who applied for assistance: %s %s.\n\n"
				+ "Please write this information on a piece of paper and place it visibly in your vehicle's dashboard.\n\n"
				+ "Only you or the person picking up your gifts should have this information. Vehicle occupants must wear a mask(s) and remain inside the vehicle for additional instructions.\n\n"
				+ "Please reply YES if you understand these instructions and someone will bring this information to pick up your gifts on Sunday, December 13 between 1PM and 4PM."
				,puLocation.getName(), puLocation.getAddress(), puLocation.getGoogleMapURL(), f.getONCNum(), f.getFirstName(), f.getLastName() );
		}
		else if(request.getMessageID() == 3 && f.getLanguage().equals("Spanish"))
		{
			return String.format("Esto es un recordatorio de Our Neighbor's Child (ONC) sobre los regalos navideños de su(s) hijo(s).\n\n"
				+ "Los regalos de sus hijos deben recoger mañana entre la 1:00 de la tarde y las 4:00 de la tarde.\n"
				+ "Un camión con los regalos de su hijo estará en el estacionamiento de la %s en el %s hasta las 4:00 de la tarde.\n\n"
				+ "Por favor traiga esta información con usted mañana: El número de ONC: %s, y el nombre el jefe o la jefa de la familia: %s %s.\n\n"
				+ "Los ocupantes del vehículo tienen que usar máscaras, seguir las señales direccionales, y quedarse dentro del vehículo para obtener instrucciones adicionales."
				,puLocation.getName(), puLocation.getAddress(), f.getONCNum(), f.getFirstName(), f.getLastName());
		}
		else if(request.getMessageID() == 3 && !f.getLanguage().equals("Spanish"))
		{
			return String.format("This is a reminder from Our Neighbor's Child (ONC) about your child(ren)'s holiday gifts.\n\n"
					+ "Your children's gifts must be picked tomorrow between 1:00pm and  4:00pm.\n"
					+ "A truck with your child's gifts will be in the %s parking lot at %s until 4:00pm.\n\n"
					+ "Please bring this information with you tomorrow: ONC # %s, Head of Household Name: %s %s.\n\n"
					+ "Vehicle occupants must wear masks, follow directional signs and remain inside the vehicle for additional instructions."
					,puLocation.getName(), puLocation.getAddress(), f.getONCNum(), f.getFirstName(), f.getLastName());
		}
		else if(request.getMessageID() == 4 && f.getLanguage().contentEquals("Spanish"))
		{
			return String.format("Esto es un recordatorio de Our Neighbor's Child (ONC) sobre los regalos navideños de su(s) hijo(s).\n\n"
					+ "¡Importante: deben recoger los regalos de sus hijos antes de las 4:00 de la tarde de hoy.\n\n"
					+ "El camión con los regalos de su hijo está en el estacionamiento de la %s en %s hasta las 4:00 de la tarde.\n\n"
					+ "Por favor traiga esta información con usted: El número de ONC: %s, y el nombre el jefe o la jefa de la familia: %s %s.\n\n"
					+ "Los ocupantes del vehículo tienen que usar máscaras, seguir las señales direccionales, y quedarse dentro del vehículo para obtener instrucciones adicionales."
					,puLocation.getName(), puLocation.getAddress(), f.getONCNum(), f.getFirstName(), f.getLastName());
		}
		else if(request.getMessageID() == 4 && !f.getLanguage().contentEquals("Spanish"))
			return String.format("This is a reminder from Our Neighbor's Child(ONC) about your child(ren)'s holiday gifts.\n\n"
					+ "All gifts MUST be picked up by 4PM today.\n"
					+ "The truck with your child's gifts is in the %s parking lot at %s until 4PM.\n\n"
					+ "Please bring this information with you: ONC # %s, Head of Household Name: %s %s.\n\n"
					+ "Vehicle occupants must wear masks, follow directional signs and remain inside the vehicle for additional instructions."
					,puLocation.getName(), puLocation.getAddress(), f.getONCNum(), f.getFirstName(), f.getLastName());
		else
			return String.format("%s %s, this text is to confirm Our Neighbor's Child(ONC) has delivered your child(ren)'s holiday gifts.\n\n"
					+ "The attached photo shows the name of the adult who signed for the gifts at your delivery address or pick-up location.\n\n"
					+ "ONC wishes you and your family a very happy holiday season!"
					,f.getFirstName(), f.getLastName());
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
			{
				familyHistoryDB.checkFamilyStatusOnSMSStatusCallback(year, updateSMS);
			
				return updateSMS;
			}
			else
				return null;
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
	
	private class PickUpLocations
	{
		private List<PickUpLocation> locations;
		
		PickUpLocations()
		{
			locations = new ArrayList<PickUpLocation>();
			
			locations.add(new PickUpLocation("Centreville Baptist Church","15100 Lee Highway, Centreville", "https://goo.gl/maps/Khbgv2i4Tk1ZKjgT8"));	//index 0
			locations.add(new PickUpLocation("Centreville United Methodist Church","6400 Old Centreville Road, Centreville", "https://goo.gl/maps/WMNjKxeHVHCsC4vP6"));	//index 1
			locations.add(new PickUpLocation("Saint Andrew Lutheran Church","14640 Soucy Place, Centreville", "https://goo.gl/maps/TL48uuGFqiuUjxZv7"));	//index 2
			locations.add(new PickUpLocation("A&A Transfer ","44200 Lavin Lane, Chantilly", "https://goo.gl/maps/WquCeZ95FurKcXJp6"));	//index 3
			locations.add(new PickUpLocation("Saint Andrew Lutheran Church","14640 Soucy Place, Centreville", "https://goo.gl/maps/TL48uuGFqiuUjxZv7"));	//index 4
			locations.add(new PickUpLocation("Centreville Baptist Church","15100 Lee Highway, Centreville", "https://goo.gl/maps/Khbgv2i4Tk1ZKjgT8"));	//index 5
			locations.add(new PickUpLocation("King of Kings Lutheran Church","4025 Kings Way, Fairfax", "https://goo.gl/maps/DmcQqiCniUALDnQv8"));	//index 6
		}
		
		PickUpLocation getPickUpLocation(ONCFamily fam)
		{
			int oncNum = Integer.parseInt(fam.getONCNum());
			if(oncNum < 250)
				return locations.get(0);	//100 to 250: CBC
			else if(oncNum < 539)
				return locations.get(1);	//251 to 539: CUMC
			else if(oncNum < 590)
				return locations.get(2);	//540 to 590: Saint Andrew
			else if(oncNum < 741)
				return locations.get(3);	//591 to 741: A&A 
			else if(oncNum < 792)
				return locations.get(4);	//742 to 792: Saint Andrew
			else if(oncNum < 1018)
				return locations.get(5);	//793 to 999: CBC
			else
				return locations.get(6);	//1000+: King of Kings
		}
	}
	
	private class PickUpLocation
	{
		private String name;
		private String address;
		private String googleMapURL;
		
		PickUpLocation(String name, String address, String url)
		{
			this.name = name;
			this.address = address;
			this.googleMapURL = url;
		}
		
		//getters
		String getName() { return name; }
		String getAddress() { return address; }
		String getGoogleMapURL() { return googleMapURL; }
	}
}
