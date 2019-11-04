package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ourneighborschild.ONCUser;

public class ServerInboundSMSDB extends ServerSeasonalDB
{
	private static final int SMS_INBOUND_DB_HEADER_LENGTH = 20;
	
	private static ServerInboundSMSDB instance = null;
	private static List<SMSInboundDBYear> inboundSMSDB;
	
	private ServerInboundSMSDB() throws FileNotFoundException, IOException
	{
		//create the INBOUND SMS data base
		inboundSMSDB = new ArrayList<SMSInboundDBYear>();
						
		//populate the Inbound SMS data base for the last TOTAL_YEARS from persistent store
		for(int year = DBManager.getBaseSeason(); year < DBManager.getBaseSeason() + DBManager.getNumberOfYears(); year++)
		{
			//create the list for each year
			SMSInboundDBYear smsDBYear = new SMSInboundDBYear(year);
							
			//add the list of inbound SMS for the year to the db
			inboundSMSDB.add(smsDBYear);
							
			//import the inbound sms from persistent store
			importDB(year, String.format("%s/%dDB/InboundSMSDB.csv",
					System.getProperty("user.dir"),
						year), "Inbound SMS DB",SMS_INBOUND_DB_HEADER_LENGTH);
			
			//set the next id
			smsDBYear.setNextID(getNextID(smsDBYear.getList()));
		}
	}
	
	public static ServerInboundSMSDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerInboundSMSDB();
		
		return instance;
	}
	
	TwilioSMSReceive add(int year, TwilioSMSReceive addedSMS)
	{			
		//retrieve the data base for the year
		SMSInboundDBYear smsDBYear = inboundSMSDB.get(DBManager.offset(year));
								
		//set the new ID for the added inbound SMS
		int smsID = smsDBYear.getNextID();
		addedSMS.setID(smsID);
		
		//add the new inbound sms to the data base
		smsDBYear.add(addedSMS);
		smsDBYear.setChanged(true);
							
		return addedSMS;
	}
	
	@Override
	void createNewSeason(int year)
	{
		//create a new inboundSMS data base year for the year provided in the year parameter
		//The sms db year list is initially empty prior to receipt of in-bound SMS messages,
		//so all we do here is create a new SMSDBYear for the new year and save it.
		SMSInboundDBYear smsDBYear = new SMSInboundDBYear(year);
		inboundSMSDB.add(smsDBYear);
		smsDBYear.setChanged(true);	//mark this db for persistent saving on the next save event
	}

	@Override
	void addObject(int year, String[] nextLine)
	{
		//Get the sms list for the year and add create and add the sms
		SMSInboundDBYear smsDBYear = inboundSMSDB.get(DBManager.offset(year));
		smsDBYear.add(new TwilioSMSReceive(nextLine));
	}

	@Override
	void save(int year)
	{
		//save the INBOUND sms
		String[] header = {"ID", "AccountSid", "MessageSid", "Body", "ToZip", "ToCity",
				"FromState", "SmsSid", "To", "ToCountry", "FromCountry", "SmsMessageSid",
				"ApiVersion", "FromCity", "SmsStatus", "NumSegments", "NumMedia",
				"From", "FromZip", "Timestamp"};
		
		SMSInboundDBYear smsDBYear = inboundSMSDB.get(DBManager.offset(year));
		if(smsDBYear.isUnsaved())
		{
//			System.out.println(String.format("ServerAdultDB save() - Saving Adult DB, size= %d", adultDBYear.getList().size()));
			String path = String.format("%s/%dDB/InboundSMSDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(smsDBYear.getList(), header, path);
			smsDBYear.setChanged(false);
		}
	}
	
	private class SMSInboundDBYear extends ServerDBYear
    {
    		private List<TwilioSMSReceive> inboundSMSList;
    	
    		SMSInboundDBYear(int year)
    		{
    			super();
    			inboundSMSList = new ArrayList<TwilioSMSReceive>();
    		}
    	
    		//getters
    		List<TwilioSMSReceive> getList() { return inboundSMSList; }
    	
    		void add(TwilioSMSReceive addedSMS) { inboundSMSList.add(addedSMS); }
    }

	@Override
	String add(int year, String addjson, ONCUser client)
	{
		// TODO Auto-generated method stub
		return null;
	}
}
