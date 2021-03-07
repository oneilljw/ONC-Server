package oncserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import javax.swing.JOptionPane;

import ourneighborschild.GiftDistribution;
import ourneighborschild.ONCObject;
import ourneighborschild.ONCUser;
import ourneighborschild.ServerGVs;

import com.google.gson.Gson;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

public class ServerGlobalVariableDB extends ServerSeasonalDB
{
	private static final int GV_HEADER_LENGTH = 15;
	private static final int GV_ALTERNATE_HEADER_LENGTH = 30;
	private static final String DEFAULT_ADDRESS = "6476+Trillium+House+Lane+Centreville,VA";
	private static final int DEFAULT_GIFT = -1;
	private static final int DEFAULT_GIFTCARD_ID = -1;
	
	private static ServerGlobalVariableDB instance = null;
	private static List<GlobalVariableDBYear> globalDB;
	
	private ServerGlobalVariableDB() throws FileNotFoundException, IOException
	{
		//create the database
		globalDB = new ArrayList<GlobalVariableDBYear>();
		
		//populate the global variable data base for the last TOTAL_YEARS from persistent store
		for(int year = DBManager.getBaseSeason(); year < DBManager.getBaseSeason() + DBManager.getNumberOfYears(); year++)
		{
			//create the global variable object for each year
			String gvFile = String.format("%s/%dDB/GlobalVariables.csv", System.getProperty("user.dir"), year);
			GlobalVariableDBYear globalDBYear = new GlobalVariableDBYear(year, gvFile);
									
			//add the object for the year to the db
			globalDB.add(globalDBYear);	
		}
	}
	
	public static ServerGlobalVariableDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerGlobalVariableDB();
		
		return instance;
	}
	
	ServerGVs getServerGlobalVariables(int year)
	{
		return globalDB.get(DBManager.offset(year)).getServerGVs();
	}
	
	String getGlobalVariables(int year)
	{
		String gvjson = null;
		Gson gson = new Gson();
		
		gvjson = gson.toJson(globalDB.get(DBManager.offset(year)).getServerGVs(), ServerGVs.class);
		
		if(gvjson != null)
			return "GLOBALS" + gvjson;
		else
			return "NO_GLOBALS";
	}
	
	static HtmlResponse getServerGVsJSONP(int year, String callbackFunction)
	{		
		Gson gson = new Gson();
	
		ServerGVs serverGVs = globalDB.get(DBManager.offset(year)).getServerGVs();
		String response = gson.toJson(serverGVs, ServerGVs.class);
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	
	Long getSeasonStartDate(int year) { return globalDB.get(DBManager.offset(year)).getServerGVs().getSeasonStartDateMillis(); }
	int getGiftCardID(int year) { return globalDB.get(DBManager.offset(year)).getServerGVs().getDefaultGiftCardID(); }
	static int getNumGiftsPerChild(int year) { return globalDB.get(DBManager.offset(year)).getServerGVs().getNumberOfGiftsPerChild(); }

	static HtmlResponse getSeasonParameterJSONP(int offset, String callbackFunction)
	{		
		Gson gson = new Gson();
		
		String response;
		ServerGVs serverGVs = globalDB.get(DBManager.offset(DBManager.getCurrentSeason())).getServerGVs();
		SeasonParameters sd = new SeasonParameters(serverGVs, offset);
		response = gson.toJson(sd, SeasonParameters.class);
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);	
	}
	
	Long getDeadlineMillis(int year, String deadline)
	{
		//check if year is in database. If it's not, return null
		if(DBManager.offset(year) < globalDB.size())
		{
			if(deadline.equals("Thanksgiving Meal"))
				return globalDB.get(DBManager.offset(year)).getServerGVs().getThanksgivingMealDeadlineMillis();
			else if(deadline.equals("December Meal"))
				return globalDB.get(DBManager.offset(year)).getServerGVs().getDecemberMealDeadlineMillis();
			else if(deadline.equals("December Gift"))
				return globalDB.get(DBManager.offset(year)).getServerGVs().getDecemberGiftDeadlineMillis();
			else if(deadline.equals("Waitlist Gift"))
				return globalDB.get(DBManager.offset(year)).getServerGVs().getWaitListGiftDeadlineMillis();
			else if(deadline.equals("Edit"))
				return globalDB.get(DBManager.offset(year)).getServerGVs().getFamilyEditDeadlineMillis();
			else
				return System.currentTimeMillis();
		}
		else
			return null;
	}

	String getDeliveryDayOfMonth(int year, String language)
	{
		//returns a string of the day of the month of delivery with suffix
		Calendar delDayCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		long delDayMillis = globalDB.get(DBManager.offset(year)).getServerGVs().getDeliveryDayMillis();
		delDayCal.setTimeInMillis(delDayMillis);
		int deliveryDayOfMonth = delDayCal.get(Calendar.DAY_OF_MONTH);
		
		if(language.equals("Spanish"))
		{
			return Integer.toString(deliveryDayOfMonth) + " de";
		}
		else
		{
			if(deliveryDayOfMonth == 1 || deliveryDayOfMonth == 21 || deliveryDayOfMonth == 31)
				return Integer.toString(deliveryDayOfMonth) + "st";
			if(deliveryDayOfMonth == 2 || deliveryDayOfMonth == 22)
				return Integer.toString(deliveryDayOfMonth) +"nd";
			else if(deliveryDayOfMonth == 3 || deliveryDayOfMonth == 23)
				return Integer.toString(deliveryDayOfMonth) + "rd";
			else
				return Integer.toString(deliveryDayOfMonth)+ "th";
		}
	}
	
	static boolean isDeliveryDay(int year)
	{
		return globalDB.get(DBManager.offset(year)).getServerGVs().isDeliveryDay();
	}
	
	//method determines if today is within one day of delivery day. Both today and delivery day
	//must be in the same calendar year.
	static boolean isDayBeforeOrDeliveryDay(int year)
	{
		return globalDB.get(DBManager.offset(year)).getServerGVs().isDeliveryDayOrDayBefore();
	}
	
	static Calendar getDeliveryDay(int year)
	{
		Calendar delDay = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		delDay.setTimeInMillis(globalDB.get(DBManager.offset(year)).getServerGVs().getDeliveryDayMillis());
		return delDay;
	}

	/***
	 * @param year - used to create a default date for the year if the file is empty
	 * @param file
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private ServerGVs readGVs(int year, String file) throws FileNotFoundException, IOException
	{
		ServerGVs gvs = null;
		
		CSVReader reader = new CSVReader(new FileReader(file));
		String[] nextLine, header;
    	
		if((header = reader.readNext()) != null)	//Does file have records? 
		{
			//Read the User File
			if(header.length == GV_HEADER_LENGTH || 
				header.length == GV_ALTERNATE_HEADER_LENGTH)	//Does the record have the right # of fields? 
			{
				if((nextLine = reader.readNext()) != null)
				{
					Calendar xmasDay = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
					xmasDay.set(year, Calendar.DECEMBER, 25, 0, 0, 0);
					xmasDay.set(Calendar.MILLISECOND, 0);
					
					Calendar seasonStart = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
					seasonStart.set(year, Calendar.SEPTEMBER, 1, 0, 0, 0);
					seasonStart.set(Calendar.MILLISECOND, 0);
					
					gvs = new ServerGVs(
							nextLine[0].isEmpty() ? xmasDay.getTimeInMillis() : parseSciNotationToLong(nextLine[0]),
							nextLine[1].isEmpty() ? seasonStart.getTimeInMillis() : parseSciNotationToLong(nextLine[1]),
							nextLine[2].isEmpty() ? DEFAULT_ADDRESS : nextLine[2],
							nextLine[3].isEmpty() ? xmasDay.getTimeInMillis() : parseSciNotationToLong(nextLine[3]),
							nextLine[4].isEmpty() ? xmasDay.getTimeInMillis() : parseSciNotationToLong(nextLine[4]),
							nextLine[5].isEmpty() ? xmasDay.getTimeInMillis() : parseSciNotationToLong(nextLine[5]),
							nextLine[6].isEmpty() ? xmasDay.getTimeInMillis() : parseSciNotationToLong(nextLine[6]),
							nextLine[7].isEmpty() ? DEFAULT_GIFT : Integer.parseInt(nextLine[7]),
							nextLine[8].isEmpty() ? DEFAULT_GIFTCARD_ID : Integer.parseInt(nextLine[8]),
							nextLine[9].isEmpty() ? xmasDay.getTimeInMillis() : parseSciNotationToLong(nextLine[9]),
							nextLine[10].isEmpty() ? xmasDay.getTimeInMillis() : parseSciNotationToLong(nextLine[10]),
							nextLine[11].isEmpty() ? 3 : Integer.parseInt(nextLine[11]),
							nextLine[12].isEmpty() ? 0xFFFF : Integer.parseInt(nextLine[12]),	//default is all agent selected	
							nextLine[13].isEmpty() ? GiftDistribution.None : GiftDistribution.distribution(Integer.parseInt(nextLine[13])),
							nextLine[14].isEmpty() ? 0 : Integer.parseInt(nextLine[14])
							);
				
				//Read the second line, it's the oncnumRegionRanges
//    				nextLine = reader.readNext();			
				}	
			}
			else
			{
				String error = String.format("GlobalVariablesDB file corrupted, %s header length = %d", file, header.length);
				JOptionPane.showMessageDialog(null, error,  "Global Variables Corrupted",
	       								JOptionPane.ERROR_MESSAGE);
			}		   			
		}
		else
		{
			String error = String.format("GlobalVariablesDB file is empty");
			JOptionPane.showMessageDialog(null, error,  "Global Variables DB Empty",
   								JOptionPane.ERROR_MESSAGE);
		}
    	
		reader.close();
		return gvs;
	}
	
	String update(int year, String json)
	{
		//Create an object for the requested serverGV update
		Gson gson = new Gson();
		ServerGVs reqObj = gson.fromJson(json, ServerGVs.class);
		
		//Find the  requested object being replaced
		GlobalVariableDBYear gvDBYear = globalDB.get(DBManager.offset(year));
		
		gvDBYear.setServerGVs(reqObj);
		gvDBYear.setChanged(true);
		
		return "UPDATED_GLOBALS" + json;
	}

	@Override
	String add(int year, String userjson, ONCUser client) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	void createNewSeason(int newYear) 
	{
		//create the new year ServerGVs
		Calendar seasonStartDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		seasonStartDate.set(Calendar.YEAR, newYear);
		seasonStartDate.set(Calendar.MONTH, Calendar.SEPTEMBER);
		seasonStartDate.set(Calendar.DAY_OF_MONTH, 1);
		seasonStartDate.set(Calendar.HOUR_OF_DAY, 0);
		seasonStartDate.set(Calendar.MINUTE, 0);
		seasonStartDate.set(Calendar.SECOND, 0);
		seasonStartDate.set(Calendar.MILLISECOND, 0);
		
		Calendar xmasDay = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		xmasDay.set(Calendar.YEAR, newYear);
		xmasDay.set(Calendar.MONTH, Calendar.DECEMBER);
		xmasDay.set(Calendar.DAY_OF_MONTH, 25);
		xmasDay.set(Calendar.HOUR_OF_DAY, 0);
		xmasDay.set(Calendar.MINUTE, 0);
		xmasDay.set(Calendar.SECOND, 0);
		xmasDay.set(Calendar.MILLISECOND, 0);
		
		ServerGVs newYearServerGVs = new ServerGVs(xmasDay.getTimeInMillis(), seasonStartDate.getTimeInMillis(),
													DEFAULT_ADDRESS, xmasDay.getTimeInMillis(),
													xmasDay.getTimeInMillis(),
													xmasDay.getTimeInMillis(),
													xmasDay.getTimeInMillis(), -1, -1,
													xmasDay.getTimeInMillis(),
													xmasDay.getTimeInMillis(), 3,
													0xFFFF,	//initialize to all wish in take being Agent Selected
													GiftDistribution.None,
													0
													);
		
		GlobalVariableDBYear newGVDBYear = new GlobalVariableDBYear(newYear, newYearServerGVs);
		globalDB.add(newGVDBYear);
		newGVDBYear.setChanged(true);
	}

	@Override
	void addObject(int year, String[] nextLine) {
		// TODO Auto-generated method stub
		
	}

	@Override
	void save(int year)
	{
		String[] header = {"Delivery Date", "Season Start Date", "Warehouse Address", "Gifts Received Deadline",
							"Thanksgiving Deadline", "December Deadline", "Info Edit Deadline", "Default Gift",
							"Defalut Gift Card", "December Meal Deadline", "WaitList Gift Deadline",
							"Number Gifts Per Child", "Wish Intake Config",
							"Gift Distribution", "Meal Intake"};
		
		GlobalVariableDBYear gvDBYear = globalDB.get(DBManager.offset(year));
		if(gvDBYear.isUnsaved())
		{
			String path = String.format("%s/%dDB/GlobalVariables.csv", System.getProperty("user.dir"), year);
			File oncwritefile = new File(path);
			
			try 
	    		{
	    			CSVWriter writer = new CSVWriter(new FileWriter(oncwritefile.getAbsoluteFile()));
	    			writer.writeNext(header);
	    			writer.writeNext(gvDBYear.getServerGVs().getExportRow());	//Write server gv row
	    			writer.close();
	    	    
	    			gvDBYear.setChanged(false);
	    		} 
			catch (IOException x)
			{
	    			System.err.format("IO Exception: %s%n", x);
			}
		}
	}
	
	private class GlobalVariableDBYear extends ServerDBYear
	{
		private ServerGVs serverGVs;
	    	
		GlobalVariableDBYear(int year, String file) throws FileNotFoundException, IOException
	    {
			super();
			this.serverGVs = readGVs(year, file);
	    }
		
		GlobalVariableDBYear(int year, ServerGVs serverGVs)
		{
			super();
			this.serverGVs = serverGVs;
		}

		@Override
		List<? extends ONCObject> getList() {
			// TODO Auto-generated method stub
			return null;
		}
	    
	    //getters
		ServerGVs getServerGVs() { return serverGVs; }	
		
		//setters
		void setServerGVs(ServerGVs servGVs) { serverGVs = servGVs; }
	}
}
