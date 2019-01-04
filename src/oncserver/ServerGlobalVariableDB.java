package oncserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.swing.JOptionPane;

import ourneighborschild.ONCObject;
import ourneighborschild.ServerGVs;

import com.google.gson.Gson;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

public class ServerGlobalVariableDB extends ServerSeasonalDB
{
	private static final int GV_HEADER_LENGTH = 11;
	private static final int GV_ALTERNATE_HEADER_LENGTH = 24;
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
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
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
	
	String getGlobalVariables(int year)
	{
		String gvjson = null;
		Gson gson = new Gson();
		
		gvjson = gson.toJson(globalDB.get(year - BASE_YEAR).getServerGVs(), ServerGVs.class);
		
		if(gvjson != null)
			return "GLOBALS" + gvjson;
		else
			return "NO_GLOBALS";
	}
	
	static HtmlResponse getServerGVsJSONP(int year, String callbackFunction)
	{		
		Gson gson = new Gson();
	
		ServerGVs serverGVs = globalDB.get(year-BASE_YEAR).getServerGVs();
		String response = gson.toJson(serverGVs, ServerGVs.class);
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	
	Date getSeasonStartDate(int year) { return globalDB.get(year - BASE_YEAR).getServerGVs().getSeasonStartDate(); }
	Calendar getSeasonStartCal(int year) { return globalDB.get(year - BASE_YEAR).getServerGVs().getSeasonStartCal(); }
	int getGiftCardID(int year) { return globalDB.get(year - BASE_YEAR).getServerGVs().getDefaultGiftCardID(); }
	
	Calendar getDateGiftsRecivedDealdine(int year)
	{
		return globalDB.get(year - BASE_YEAR).getServerGVs().getGiftsReceivedDeadline();
	}
	
	Date getDeadline(int year, String deadline)
	{
		//check if year is in database. If it's not, return null
		if(year - BASE_YEAR < globalDB.size())
		{
			if(deadline.equals("Thanksgiving Meal"))
				return globalDB.get(year - BASE_YEAR).getServerGVs().getThanksgivingMealDeadline();
			else if(deadline.equals("December Meal"))
				return globalDB.get(year - BASE_YEAR).getServerGVs().getDecemberMealDeadline();
			else if(deadline.equals("December Gift"))
				return globalDB.get(year - BASE_YEAR).getServerGVs().getDecemberGiftDeadline();
			else if(deadline.equals("Waitlist Gift"))
				return globalDB.get(year - BASE_YEAR).getServerGVs().getWaitListGiftDeadline();
			else if(deadline.equals("Edit"))
				return globalDB.get(year - BASE_YEAR).getServerGVs().getFamilyEditDeadline();
			else
				return Calendar.getInstance().getTime();
		}
		else
			return null;
	}
	
	static boolean isDeliveryDay(int year)
	{
		//check if today is delivery day
		Calendar delDayCal = globalDB.get(year - BASE_YEAR).getServerGVs().getDeliveryDateCal();
		Calendar today = Calendar.getInstance();

		return delDayCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
				delDayCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
	}
	
	static Calendar getDeliveryDay(int year)
	{
		return globalDB.get(year - BASE_YEAR).getServerGVs().getDeliveryDateCal();
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
    					Calendar xmasDay = Calendar.getInstance();
    					xmasDay.set(year, Calendar.DECEMBER, 25, 0, 0, 0);
    					xmasDay.set(Calendar.MILLISECOND, 0);
    					
    					Calendar seasonStart = Calendar.getInstance();
    					seasonStart.set(year, Calendar.SEPTEMBER, 1, 0, 0, 0);
    					seasonStart.set(Calendar.MILLISECOND, 0);
    					
    					//Read first line, it's the gv's
    					gvs = new ServerGVs(
    							nextLine[0].isEmpty() ? xmasDay.getTimeInMillis() : Long.parseLong(nextLine[0]),
    							nextLine[1].isEmpty() ? seasonStart.getTimeInMillis() : Long.parseLong(nextLine[1]),
    							nextLine[2].isEmpty() ? DEFAULT_ADDRESS : nextLine[2],
    							nextLine[3].isEmpty() ? xmasDay.getTimeInMillis() : Long.parseLong(nextLine[3]),
    							nextLine[4].isEmpty() ? xmasDay.getTimeInMillis() : Long.parseLong(nextLine[4]),
    							nextLine[5].isEmpty() ? xmasDay.getTimeInMillis() : Long.parseLong(nextLine[5]),
    							nextLine[6].isEmpty() ? xmasDay.getTimeInMillis() : Long.parseLong(nextLine[6]),
    							nextLine[7].isEmpty() ? DEFAULT_GIFT : Integer.parseInt(nextLine[7]),
    							nextLine[8].isEmpty() ? DEFAULT_GIFTCARD_ID : Integer.parseInt(nextLine[8]),
    							nextLine[9].isEmpty() ? xmasDay.getTimeInMillis() : Long.parseLong(nextLine[9]),
    							nextLine[10].isEmpty() ? xmasDay.getTimeInMillis() : Long.parseLong(nextLine[10]));
    				
    				//Read the second line, it's the oncnumRegionRanges
//    				nextLine = reader.readNext();			
    			}	
    		}
    		else
    		{
    			String error = String.format("GlobalVariablesDB file corrupted, header lentgth = %d", header.length);
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
		GlobalVariableDBYear gvDBYear = globalDB.get(year - BASE_YEAR);
		
		gvDBYear.setServerGVs(reqObj);
		gvDBYear.setChanged(true);
		
		return "UPDATED_GLOBALS" + json;
	}

	@Override
	String add(int year, String userjson) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	void createNewYear(int newYear) 
	{
		//create the new year ServerGVs
		Calendar seasonStartDate = Calendar.getInstance();
		seasonStartDate.set(Calendar.YEAR, newYear);
		seasonStartDate.set(Calendar.MONTH, Calendar.SEPTEMBER);
		seasonStartDate.set(Calendar.DAY_OF_MONTH, 1);
		seasonStartDate.set(Calendar.HOUR_OF_DAY, 0);
		seasonStartDate.set(Calendar.MINUTE, 0);
		seasonStartDate.set(Calendar.SECOND, 0);
		seasonStartDate.set(Calendar.MILLISECOND, 0);
		
		Calendar deliveryDate = Calendar.getInstance();
		deliveryDate.set(Calendar.YEAR, newYear);
		deliveryDate.set(Calendar.MONTH, Calendar.DECEMBER);
		deliveryDate.set(Calendar.DAY_OF_MONTH, 25);
		deliveryDate.set(Calendar.HOUR_OF_DAY, 0);
		deliveryDate.set(Calendar.MINUTE, 0);
		deliveryDate.set(Calendar.SECOND, 0);
		deliveryDate.set(Calendar.MILLISECOND, 0);
		
		Calendar giftsreceivedDate = Calendar.getInstance();
		giftsreceivedDate.set(Calendar.YEAR, newYear);
		giftsreceivedDate.set(Calendar.MONTH, Calendar.DECEMBER);
		giftsreceivedDate.set(Calendar.DAY_OF_MONTH, 25);
		giftsreceivedDate.set(Calendar.HOUR_OF_DAY, 0);
		giftsreceivedDate.set(Calendar.MINUTE, 0);
		giftsreceivedDate.set(Calendar.SECOND, 0);
		giftsreceivedDate.set(Calendar.MILLISECOND, 0);
		
		Calendar thanksgivingMealDeadline = Calendar.getInstance();
		thanksgivingMealDeadline.set(Calendar.YEAR, newYear);
		thanksgivingMealDeadline.set(Calendar.MONTH, Calendar.DECEMBER);
		thanksgivingMealDeadline.set(Calendar.DAY_OF_MONTH, 25);
		thanksgivingMealDeadline.set(Calendar.HOUR_OF_DAY, 0);
		thanksgivingMealDeadline.set(Calendar.MINUTE, 0);
		thanksgivingMealDeadline.set(Calendar.SECOND, 0);
		thanksgivingMealDeadline.set(Calendar.MILLISECOND, 0);
		
		Calendar decemberGiftDeadline = Calendar.getInstance();
		decemberGiftDeadline.set(Calendar.YEAR, newYear);
		decemberGiftDeadline.set(Calendar.MONTH, Calendar.DECEMBER);
		decemberGiftDeadline.set(Calendar.DAY_OF_MONTH, 25);
		decemberGiftDeadline.set(Calendar.HOUR_OF_DAY, 0);
		decemberGiftDeadline.set(Calendar.MINUTE, 0);
		decemberGiftDeadline.set(Calendar.SECOND, 0);
		decemberGiftDeadline.set(Calendar.MILLISECOND, 0);
		
		Calendar familyEditDeadline = Calendar.getInstance();
		familyEditDeadline.set(Calendar.YEAR, newYear);
		familyEditDeadline.set(Calendar.MONTH, Calendar.DECEMBER);
		familyEditDeadline.set(Calendar.DAY_OF_MONTH, 25);
		familyEditDeadline.set(Calendar.HOUR_OF_DAY, 0);
		familyEditDeadline.set(Calendar.MINUTE, 0);
		familyEditDeadline.set(Calendar.SECOND, 0);
		familyEditDeadline.set(Calendar.MILLISECOND, 0);
		
		Calendar decemberMealDeadline = Calendar.getInstance();
		decemberMealDeadline.set(Calendar.YEAR, newYear);
		decemberMealDeadline.set(Calendar.MONTH, Calendar.DECEMBER);
		decemberMealDeadline.set(Calendar.DAY_OF_MONTH, 25);
		decemberMealDeadline.set(Calendar.HOUR_OF_DAY, 0);
		decemberMealDeadline.set(Calendar.MINUTE, 0);
		decemberMealDeadline.set(Calendar.SECOND, 0);
		decemberMealDeadline.set(Calendar.MILLISECOND, 0);
		
		Calendar waitlistGiftDeadline = Calendar.getInstance();
		waitlistGiftDeadline.set(Calendar.YEAR, newYear);
		waitlistGiftDeadline.set(Calendar.MONTH, Calendar.DECEMBER);
		waitlistGiftDeadline.set(Calendar.DAY_OF_MONTH, 25);
		waitlistGiftDeadline.set(Calendar.HOUR_OF_DAY, 0);
		waitlistGiftDeadline.set(Calendar.MINUTE, 0);
		waitlistGiftDeadline.set(Calendar.SECOND, 0);
		waitlistGiftDeadline.set(Calendar.MILLISECOND, 0);

		
		ServerGVs newYearServerGVs = new ServerGVs(deliveryDate.getTime(), seasonStartDate.getTime(),
													DEFAULT_ADDRESS, giftsreceivedDate.getTime(),
													thanksgivingMealDeadline.getTime(),
													decemberGiftDeadline.getTime(),
													familyEditDeadline.getTime(), -1, -1,
													decemberMealDeadline.getTime(),
													waitlistGiftDeadline.getTime());
		
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
							"Defalut Gift Card", "December Meal Deadline", "WaitList Gift Deadline"};
		
		GlobalVariableDBYear gvDBYear = globalDB.get(year - BASE_YEAR);
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
