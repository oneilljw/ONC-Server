package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ourneighborschild.ONCSMS;
import ourneighborschild.ONCUser;

public class ServerSMSDB extends ServerSeasonalDB
{
private static final int SMS_RECEIVE_DB_HEADER_LENGTH = 7;
	
	private static ServerSMSDB instance = null;
	private static List<SMSDBYear> smsDB;
	
	private ServerSMSDB() throws FileNotFoundException, IOException
	{
		//create the INBOUND SMS data base
		smsDB = new ArrayList<SMSDBYear>();
						
		//populate the adult data base for the last TOTAL_YEARS from persistent store
		for(int year = DBManager.getBaseSeason(); year < DBManager.getBaseSeason() + DBManager.getNumberOfYears(); year++)
		{
			//create the meal list for each year
			SMSDBYear smsDBYear = new SMSDBYear(year);
							
			//add the list of adults for the year to the db
			smsDB.add(smsDBYear);
							
			//import the adults from persistent store
			importDB(year, String.format("%s/%dDB/SMSDB.csv",
					System.getProperty("user.dir"),
						year), "SMS DB",SMS_RECEIVE_DB_HEADER_LENGTH);
			
			//set the next id
			smsDBYear.setNextID(getNextID(smsDBYear.getList()));
		}
		
		//initialize the Twilio IF
//		TwilioIF twilioIF = TwilioIF.getInstance();
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
		String[] header = {"ID", "Type", "Entity ID", "Phone Num", "Direction", "Body", "Timestamp"};
		
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
