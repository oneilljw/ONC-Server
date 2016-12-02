package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import ourneighborschild.ONCDriver;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerDriverDB extends ServerSeasonalDB
{
	private static final int DRIVER_DB_HEADER_LENGTH = 19;
	
	private static List<DriverDBYear> driverDB;
	private static ServerDriverDB instance = null;
	
	private static ClientManager clientMgr;

	private ServerDriverDB() throws FileNotFoundException, IOException
	{
		//create the driver data bases for TOTAL_YEARS number of years
		driverDB = new ArrayList<DriverDBYear>();
		
		clientMgr = ClientManager.getInstance();

		//populate the data base for the last TOTAL_YEARS from persistent store
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the child list for each year
			DriverDBYear driverDBYear = new DriverDBYear(year);
									
			//add the list of children for the year to the db
			driverDB.add(driverDBYear);
									
			//import the children from persistent store
			importDB(year, String.format("%s/%dDB/DriverDB.csv",
					System.getProperty("user.dir"),
						year), "Driver DB", DRIVER_DB_HEADER_LENGTH);
									
			//set the next id
			driverDBYear.setNextID(getNextID(driverDBYear.getList()));
		}
	}
	
	public static ServerDriverDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerDriverDB();
		
		return instance;
	}
	
	//Search the database for the family. Return a json if the family is found. 
	String getDrivers(int year)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCDriver>>(){}.getType();
			
		String response = gson.toJson(driverDB.get(year - BASE_YEAR).getList(), listtype);
		return response;	
	}
	
	static HtmlResponse getDriverJSONP(int year, String fn, String ln, String callbackFunction)
	{		
		Gson gson = new Gson();
		List<ONCDriver> searchList = driverDB.get(year - BASE_YEAR).getList();
		
		String response;
		int index=0;
		while(index < searchList.size() && !(searchList.get(index).getfName().equalsIgnoreCase(fn) && 
											 searchList.get(index).getlName().equalsIgnoreCase(ln)))
		{
			index++;
		}
		
		if(index< searchList.size())
			response = gson.toJson(searchList.get(index), ONCDriver.class);
		else
			response = "{\"id\":-1}";	//send back id = -1, meaning not found
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HTTPCode.Ok);		
	}

	@Override
	String add(int year, String json) 
	{
		//Need to change this to add a check to see if the driver already exists, 
		//similar to what we do for agents.
		
		//Create a driver object for the new driver
		Gson gson = new Gson();
		ONCDriver addedDriver = gson.fromJson(json, ONCDriver.class);
				
		//set the new ID for the new driver
		DriverDBYear driverDBYear = driverDB.get(year - BASE_YEAR);
		addedDriver.setID(driverDBYear.getNextID());
		driverDBYear.add(addedDriver);
		driverDBYear.setChanged(true);
				
		return "ADDED_DRIVER" + gson.toJson(addedDriver, ONCDriver.class);
	}
	
	String update(int year, String json)
	{
		//Create a driver object for the updated driver
		Gson gson = new Gson();
		ONCDriver updatedDriver = gson.fromJson(json, ONCDriver.class);
		
		//Find the position for the current driver being updated
		DriverDBYear driverDBYear = driverDB.get(year - BASE_YEAR);
		List<ONCDriver> dAL = driverDBYear.getList();
		int index = 0;
		while(index < dAL.size() && dAL.get(index).getID() != updatedDriver.getID())
			index++;
		
		//Replace the current object with the update
		if(index < dAL.size())
		{
			dAL.set(index, updatedDriver);
			driverDBYear.setChanged(true);
			return "UPDATED_DRIVER" + gson.toJson(updatedDriver, ONCDriver.class);
		}
		else
			return "UPDATE_FAILED";
	}
	
	String delete(int year, String json)
	{
		//Create an object for the delete request
		Gson gson = new Gson();
		ONCDriver deletedDriver = gson.fromJson(json, ONCDriver.class);
		
		//find and remove the deleted child from the data base
		DriverDBYear driverDBYear = driverDB.get(year - BASE_YEAR);
		List<ONCDriver> dAL = driverDBYear.getList();
		
		int index = 0;
		while(index < dAL.size() && dAL.get(index).getID() != deletedDriver.getID())
			index++;
		
		if(index < dAL.size())
		{
			dAL.remove(index);
			driverDBYear.setChanged(true);
			return "DELETED_DRIVER" + json;
		}
		else
			return "DELETE_FAILED";	
	}
	
	ONCDriver getDriverByDriverNumber(int year, String drvNum)
	{
		List<ONCDriver> dAL = driverDB.get(year-BASE_YEAR).getList();
		
		//find the driver
		int index = 0;	
		while(index < dAL.size() && !dAL.get(index).getDrvNum().equals(drvNum))
			index++;
			
		if(index < dAL.size())
			return dAL.get(index);
		else
			return null;	
	}
	
	void updateDriverDeliveryCounts(int year, String drvNum1, String drvNum2)
	{
		ONCDriver driver;
		Gson gson = new Gson();
		String change;
		
		if(drvNum1 != null)
		{
			driver = getDriverByDriverNumber(year, drvNum1);
			if(driver != null)
			{
				driver.incrementDeliveryCount(-1);
				change = "UPDATED_DRIVER" + gson.toJson(driver, ONCDriver.class);
				clientMgr.notifyAllInYearClients(year, change);	
			}
		}
		
		if(drvNum2 != null)
		{
			driver = getDriverByDriverNumber(year, drvNum2);
			if(driver != null)
			{
				driver.incrementDeliveryCount(1);
				change = "UPDATED_DRIVER" + gson.toJson(driver, ONCDriver.class);
				clientMgr.notifyAllInYearClients(year, change);
			}
		}	
	}

	@Override
	void addObject(int year, String[] nextLine)
	{
		DriverDBYear driverDBYear = driverDB.get(year - BASE_YEAR);
		driverDBYear.add(new ONCDriver(nextLine));	
	}

	@Override
	void createNewYear(int newYear)
	{
		//create a new Driver data base year for the year provided in the newYear parameter
		//The driver db year list is initially empty prior to the import of drivers, so all we
		//do here is create a new DriverDBYear for the newYear and save it.
		DriverDBYear driverDBYear = new DriverDBYear(newYear);
		driverDB.add(driverDBYear);
		driverDBYear.setChanged(true);	//mark this db for persistent saving on the next save event
	}
	
	private class DriverDBYear extends ServerDBYear
	{
		private List<ONCDriver> dList;
	    	
	    DriverDBYear(int year)
	    {
	    	super();
	    	dList = new ArrayList<ONCDriver>();
	    }
	    
	    //getters
	    List<ONCDriver> getList() { return dList; }
	    
	    void add(ONCDriver addedDriver) { dList.add(addedDriver); }
	}

	@Override
	void save(int year)
	{
		 String[] header = {"Driver ID", "Driver Num" ,"First Name", "Last Name", "House Number", "Street",
		 			"Unit", "City", "Zip", "Email", "Home Phone", "Cell Phone", 
		 			"Driver License", "Car", "# Del. Assigned", "Time Stamp",
		 			"Stoplight Pos", "Stoplight Mssg", "Changed By"};
		 
		 DriverDBYear driverDBYear = driverDB.get(year - BASE_YEAR);
		 if(driverDBYear.isUnsaved())
		 {
//			System.out.println(String.format("DriverDB save() - Saving Driver DB"));
			String path = String.format("%s/%dDB/DriverDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(driverDBYear.getList(),  header, path);
			driverDBYear.setChanged(false);
		}
	}	
}
