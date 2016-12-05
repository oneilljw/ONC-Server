package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import ourneighborschild.ActivityCode;
import ourneighborschild.ONCDriver;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerDriverDB extends ServerSeasonalDB
{
	private static final int DRIVER_DB_HEADER_LENGTH = 22;
	
	
	private static List<DriverDBYear> driverDB;
	private static ServerDriverDB instance = null;
	
	private static ClientManager clientMgr;
	private static ServerWarehouseDB warehouseDB;

	private ServerDriverDB() throws FileNotFoundException, IOException
	{
		//create the driver data bases for TOTAL_YEARS number of years
		driverDB = new ArrayList<DriverDBYear>();
		
		clientMgr = ClientManager.getInstance();
		warehouseDB = ServerWarehouseDB.getInstance();

		//populate the data base for the last TOTAL_YEARS from persistent store
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the child list for each year
			DriverDBYear driverDBYear = new DriverDBYear(year);
									
			//add the list of children for the year to the db
			driverDB.add(driverDBYear);
									
			//import the volunteers from persistent store
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
	
	/************
	 * Registers and signs in volunteers from the web site
	 * @param year
	 * @param params
	 * @param callbackFunction
	 * @return
	 */
	static HtmlResponse addVolunteerJSONP(int year, Map<String, String> params, 
											String callbackFunction)
	{		
		
		String fn = params.get("delFN");
		String ln = params.get("delLN");
		
		DriverDBYear volDBYear = driverDB.get(year - BASE_YEAR);
		List<ONCDriver>volList = volDBYear.getList();
		
		int index=0;
		while(index < volList.size() && !(volList.get(index).getfName().equalsIgnoreCase(fn) && 
				 volList.get(index).getlName().equalsIgnoreCase(ln)))
			index++;
		
		if(index<volList.size())
		{
			//Found the volunteer, increment their sign-ins
			ONCDriver updatedVol = volList.get(index);
			updatedVol.setSignIns(updatedVol.getSignIns() + 1);
			updatedVol.setDateChanged(new Date());
			
			if(params.get("group").equals("Other"))
				updatedVol.setGroup(params.get("groupother"));
			else
				updatedVol.setGroup(params.get("group"));
			
			if(!params.get("comment").isEmpty())
				updatedVol.setComment(params.get("comment"));
			
			
			volDBYear.setChanged(true);
			
			//notify in year clients
			Gson gson = new Gson();
			clientMgr.notifyAllInYearClients(year, "UPDATED_DRIVER" + gson.toJson(updatedVol, ONCDriver.class));
			
			warehouseDB.add(year, updatedVol);	//add the volunteer to the warehouse data base
		}
		else
		{
			//Didn't find the volunteer, create and add a new one
			String group = params.get("group").equals("Other") ? params.get("groupother") : params.get("group");
			ONCDriver addedVol = new ONCDriver(-1, "N/A", fn, ln, params.get("delemail"), 
					params.get("delhousenum"), params.get("delstreet"), params.get("delunit"),
					params.get("delcity"), params.get("delzipcode"), params.get("primaryphone"),
					params.get("primaryphone"), params.get("activity"), group,
					params.get("comment"), new Date(), "ONC Website");
			
			addedVol.setID(volDBYear.getNextID());	
			addedVol.setSignIns(addedVol.getSignIns() + 1);	
			volDBYear.add(addedVol);
			volDBYear.setChanged(true);
			
			warehouseDB.add(year, addedVol);	//add the volunteer to the warehouse data base
			
			//notify in year clients
			Gson gson = new Gson();
			clientMgr.notifyAllInYearClients(year, "ADDED_DRIVER" + gson.toJson(addedVol, ONCDriver.class));
		}
		
		
		
		
		String responseJson = String.format("{\"message\":\"Thank you, %s, for volunteering "
				+ "with Our Neighbor's Child!\"}", (String) params.get("delFN"));
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + responseJson +")", HTTPCode.Ok);		
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
		 DriverDBYear driverDBYear = driverDB.get(year - BASE_YEAR);
		 
		 if(driverDBYear.isUnsaved())
		 {
			 String[] driverHeader = {"Driver ID", "Driver Num" ,"First Name", "Last Name", "House Number", "Street",
			 			"Unit", "City", "Zip", "Email", "Home Phone", "Cell Phone", "Activity Code",
			 			"Group", "Comment", "# Del. Assigned", "#Sign-Ins", "Time Stamp", "Changed By",
			 			"Stoplight Pos", "Stoplight Mssg", "Changed By"};
			 
			String path = String.format("%s/%dDB/DriverDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(driverDBYear.getList(),  driverHeader, path);
			driverDBYear.setChanged(false);
		}
	}	
}
