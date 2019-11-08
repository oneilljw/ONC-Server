package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ourneighborschild.EntityType;
import ourneighborschild.ONCSMS;
import ourneighborschild.ONCUser;
import ourneighborschild.SMSDirection;
import ourneighborschild.SMSRequest;
import ourneighborschild.SMSStatus;

public class ServerSMSDB extends ServerSeasonalDB
{
private static final int SMS_RECEIVE_DB_HEADER_LENGTH = 8;
	
	private static ServerSMSDB instance = null;
	private static List<SMSDBYear> smsDB;
	
	private ServerFamilyDB familyDB;
	private TwilioIF twilioIF;
	
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
		
		//initialize the Twilio IF
//		twilioIF = TwilioIF.getInstance();
//		twilioIF.sendSMS("+15713440902", "Our Neighbor's Child SMS server started");
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
							
		return addedSMS;
	}
	
	String processSMSRequest(String json)
	{
		Gson gson = new Gson();
		SMSRequest request = gson.fromJson(json, SMSRequest.class);
		
		List<String> addedSMSList = new ArrayList<String>();
		
		List<ONCSMS> smsRequestList = new ArrayList<ONCSMS>();
		   
		if(request.getMessage() != null && request.getEntityType() == EntityType.FAMILY)
	    {
			//for each family in the request, create a ONCSMS request 
			for(Integer famID : request.getEntityIDList() )
			{
				String twilioFormattedPhoneNum = familyDB.getTwilioFormattedPhoneNumber(request.getYear(), famID, request.getPhoneChoice());
				if(twilioFormattedPhoneNum != null)
					smsRequestList.add(new ONCSMS(-1, EntityType.FAMILY, famID, twilioFormattedPhoneNum,
							SMSDirection.UNKNOWN, request.getMessage(), SMSStatus.REQUESTED));
				else
					smsRequestList.add(new ONCSMS(-1, EntityType.FAMILY, famID, twilioFormattedPhoneNum,
							SMSDirection.UNKNOWN, request.getMessage(), SMSStatus.ERR_NO_PHONE));
			}
	    }	
		
		//if the request list isn't empty, add the ONCSMS request list to the database, send the sms 
		//requests in the background and notify the clients of the new requests.
		String response = "SMS_REQUEST_FAILED";
		if(!smsRequestList.isEmpty())
		{
			//create the list to strings to send to clients
			addedSMSList = addSMSRequestList(request.getYear(), smsRequestList);
			
			//ask twilio to send the SMS's
			TwilioIF twilioIF;
			try
			{
				twilioIF = TwilioIF.getInstance();
				response = twilioIF.sendSMSList(request, smsRequestList);
				
				//notify all in year clients
				ClientManager clientMgr = ClientManager.getInstance();
				clientMgr.notifyAllInYearClients(request.getYear(), addedSMSList);
				
			}
			catch (IOException e)
			{
				response = "TWILIO IF Error " + e.getMessage();
			}
		}
			
	    return response;
	}
	
	//callback from Twilio IF when background task completes
	void twilioRequestComplete(SMSRequest request, List<ONCSMS> sentSMSList)
	{
//		for(ONCSMS resultSMS : sentSMSList)
//			System.out.println(resultSMS.toString());
		
		//create the updated string of client json messages
		List<String> clientSMSUpdateList = new ArrayList<String>();
		Gson gson = new Gson();
		
		for(ONCSMS completedSMS : sentSMSList)
			clientSMSUpdateList.add("UPDATED_SMS" + gson.toJson(completedSMS, ONCSMS.class));
		
		//notify all in year clients of SMS updates
		ClientManager clientMgr = ClientManager.getInstance();
		clientMgr.notifyAllInYearClients(request.getYear(), clientSMSUpdateList);
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
		String[] header = {"ID", "Type", "Entity ID", "Phone Num", "Direction", "Body", "Status", "Timestamp"};
		
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
    			smsList = new ArrayList<ONCSMS
    					>();
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
