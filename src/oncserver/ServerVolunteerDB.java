package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import ourneighborschild.ONCVolunteer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerVolunteerDB extends ServerSeasonalDB
{
	private static final int DRIVER_DB_HEADER_LENGTH = 24;
	private static final int ACTIVITY_STRING_COL = 13;
	private static final int COMMENTS_STRING_COL = 15;
	
	private static List<VolunteerDBYear> driverDB;
	private static ServerVolunteerDB instance = null;
	
	private static ClientManager clientMgr;
	private static ServerWarehouseDB warehouseDB;
	private static ServerActivityDB activityDB;

	private ServerVolunteerDB() throws FileNotFoundException, IOException
	{
		//create the driver data bases for TOTAL_YEARS number of years
		driverDB = new ArrayList<VolunteerDBYear>();
		
		clientMgr = ClientManager.getInstance();
		warehouseDB = ServerWarehouseDB.getInstance();
		activityDB = ServerActivityDB.getInstance();

		//populate the data base for the last TOTAL_YEARS from persistent store
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the child list for each year
			VolunteerDBYear volunteerDBYear = new VolunteerDBYear(year);
									
			//add the list of children for the year to the db
			driverDB.add(volunteerDBYear);
									
			//import the volunteers from persistent store
			importDB(year, String.format("%s/%dDB/DriverDB.csv",
					System.getProperty("user.dir"),
						year), "Driver DB", DRIVER_DB_HEADER_LENGTH);
		
			//set the next id
			volunteerDBYear.setNextID(getNextID(volunteerDBYear.getList()));
		}
	}
	
	public static ServerVolunteerDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerVolunteerDB();
		
		return instance;
	}
	
	//Search the database for the family. Return a json if the family is found. 
	String getDrivers(int year)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCVolunteer>>(){}.getType();
			
		String response = gson.toJson(driverDB.get(year - BASE_YEAR).getList(), listtype);
		return response;	
	}
	
	static HtmlResponse getVolunteerJSONP(int year, String fn, String ln, String cell, String callbackFunction)
	{		
		Gson gson = new Gson();
		List<ONCVolunteer> searchList = driverDB.get(year - BASE_YEAR).getList();
		
		String response;
		int index=0;
		
		//determine whether to find the volunteer by first and last name or by cell phone
		if(fn.isEmpty() || ln.isEmpty())
		{
			//either first or last name is empty, search for cell phone match
			while(index < searchList.size() && !searchList.get(index).doesCellPhoneMatch(cell))
			{
				index++;
			}
		}
		else
		{
			//search for first and last name match
			while(index < searchList.size() && !(searchList.get(index).getfName().equalsIgnoreCase(fn) && 
					 searchList.get(index).getlName().equalsIgnoreCase(ln)))
			{
				index++;
			}
		}
		
		if(index< searchList.size())
			response = gson.toJson(searchList.get(index), ONCVolunteer.class);
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
		ONCVolunteer addedDriver = gson.fromJson(json, ONCVolunteer.class);
				
		//set the new ID for the new driver
		VolunteerDBYear volunteerDBYear = driverDB.get(year - BASE_YEAR);
		addedDriver.setID(volunteerDBYear.getNextID());
		volunteerDBYear.add(addedDriver);
		volunteerDBYear.setChanged(true);
				
		return "ADDED_DRIVER" + gson.toJson(addedDriver, ONCVolunteer.class);
	}
	
	String add(int year, ONCVolunteer addedVol) 
	{		
		//set the new ID for the new driver
		VolunteerDBYear volunteerDBYear = driverDB.get(year - BASE_YEAR);
		addedVol.setID(volunteerDBYear.getNextID());
		volunteerDBYear.add(addedVol);
		volunteerDBYear.setChanged(true);
		
		Gson gson = new Gson();
		return "ADDED_DRIVER" + gson.toJson(addedVol, ONCVolunteer.class);
	}
	
	String addVolunteerGroup(int year, String volunteerGroupJson, DesktopClient currClient)
	{
		//get the current year volunteer list for the proper year
		List<ONCVolunteer> cyVolList = driverDB.get(year - BASE_YEAR).getList();
		
		//create the response list of jsons
		List<String> jsonResponseList = new ArrayList<String>();
		
		//un-bundle the input list of ONCVolunteer objects
		Gson gson = new Gson();
		Type listOfVolunteers = new TypeToken<ArrayList<ONCVolunteer>>(){}.getType();		
		List<ONCVolunteer> inputVolList = gson.fromJson(volunteerGroupJson, listOfVolunteers);
		
		//for each volunteer in the list, check to see if it's a duplicate of an existing 
		//volunteer. If not add it.
		for(ONCVolunteer inputVol : inputVolList)
		{
			int index = 0;
			while(index < cyVolList.size() && !inputVol.equals(cyVolList.get(index)))
				index++;
							
			if(index == cyVolList.size())
			{
				//no match found, add the input volunteer to the current year list
				String response = add(year, inputVol);
				jsonResponseList.add(response);
			}
		}
		
		//notify all other clients of the imported volunteer objects
		clientMgr.notifyAllOtherInYearClients(currClient, jsonResponseList);
		
		Type listOfChanges = new TypeToken<ArrayList<String>>(){}.getType();
		return "ADDED_VOLUNTEER_GROUP" + gson.toJson(jsonResponseList, listOfChanges);
	}
	
	/************
	 * Registers and signs in volunteers from the web site
	 * @param year
	 * @param volParams
	 * @param callbackFunction
	 * @return
	 */
	static HtmlResponse addVolunteerJSONP(int year, Map<String, String> volParams,
											Map<String, String> activityParams, 
											String website, String callbackFunction)
	{		
		String fn = volParams.get("delFN");
		String ln = volParams.get("delLN");
		
		VolunteerDBYear volDBYear = driverDB.get(year - BASE_YEAR);
		List<ONCVolunteer>volList = volDBYear.getList();
		
		int index=0;
		while(index < volList.size() && !(volList.get(index).getfName().equalsIgnoreCase(fn) && 
				 volList.get(index).getlName().equalsIgnoreCase(ln)))
			index++;
		
		if(index<volList.size())
		{
			//Found the volunteer, update their contact info and increment their sign-ins
			ONCVolunteer updatedVol = volList.get(index);
			updatedVol.setSignIns(updatedVol.getSignIns() + 1);
			updatedVol.setDateChanged(new Date());
			
			if(volParams.get("group").equals("Other"))
				updatedVol.setGroup(volParams.get("groupother"));
			else
				updatedVol.setGroup(volParams.get("group"));
			
			if(volParams.get("group").equals("Self") || volParams.get("group").equals("Other"))
			{
				if(!volParams.get("delhousenum").isEmpty())
					updatedVol.sethNum(volParams.get("delhousenum"));
				if(!volParams.get("delstreet").isEmpty())
					updatedVol.setStreet(volParams.get("delstreet"));
				if(!volParams.get("delunit").isEmpty())
					updatedVol.setUnit(volParams.get("delunit"));
				if(!volParams.get("delcity").isEmpty())
					updatedVol.setCity(volParams.get("delcity"));
				if(!volParams.get("delzipcode").isEmpty())
					updatedVol.setZipcode(volParams.get("delzipcode")); 
				if(!volParams.get("delemail").isEmpty())
					updatedVol.setEmail(volParams.get("delemail"));
				if(!volParams.get("comment").isEmpty())
					updatedVol.setComment(volParams.get("comment"));
				
				//check if the single phone provided in the sign-in matches either of the current
				//phones. If it doesn't, assume it's a cell phone and update it.
				String webphone = volParams.get("primaryphone");
				if(!webphone.isEmpty() && !webphone.equals(updatedVol.getHomePhone()) && 
					!webphone.equals(updatedVol.getCellPhone()))
					updatedVol.setCellPhone(volParams.get("primaryphone"));
				
				//create a new activity list and update replace the prior list
				updatedVol.setActivityList(activityDB.createActivityList(year, activityParams));
			}
			
			volDBYear.setChanged(true);
			
			//notify in year clients
			Gson gson = new Gson();
			clientMgr.notifyAllInYearClients(year, "UPDATED_DRIVER" + gson.toJson(updatedVol, ONCVolunteer.class));
		}
		else
		{
			//Didn't find the volunteer, create and add a new one, including their activity list
			String group = volParams.get("group").equals("Other") ? volParams.get("groupother") : volParams.get("group");
			ONCVolunteer addedVol = new ONCVolunteer(-1, "N/A", fn, ln, volParams.get("delemail"), 
					volParams.get("delhousenum"), volParams.get("delstreet"), volParams.get("delunit"),
					volParams.get("delcity"), volParams.get("delzipcode"), volParams.get("primaryphone"),
					volParams.get("primaryphone"), "1", activityDB.createActivityList(year, activityParams), 
					group, volParams.get("comment"), new Date(), website);
			
			addedVol.setID(volDBYear.getNextID());	
			addedVol.setSignIns(addedVol.getSignIns() + 1);	
			volDBYear.add(addedVol);
			volDBYear.setChanged(true);
			
			//notify in year clients
			Gson gson = new Gson();
			clientMgr.notifyAllInYearClients(year, "ADDED_DRIVER" + gson.toJson(addedVol, ONCVolunteer.class));
		}
		
		String responseJson = String.format("{\"message\":\"Thank you, %s, for volunteering "
				+ "with Our Neighbor's Child!\"}", volParams.get("delFN"));
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + responseJson +")", HTTPCode.Ok);		
	}
	
	String update(int year, String json)
	{
		//Create a driver object for the updated driver
		Gson gson = new Gson();
		ONCVolunteer updatedDriver = gson.fromJson(json, ONCVolunteer.class);
		
		//Find the position for the current driver being updated
		VolunteerDBYear volunteerDBYear = driverDB.get(year - BASE_YEAR);
		List<ONCVolunteer> dAL = volunteerDBYear.getList();
		int index = 0;
		while(index < dAL.size() && dAL.get(index).getID() != updatedDriver.getID())
			index++;
		
		//Replace the current object with the update
		if(index < dAL.size())
		{
			dAL.set(index, updatedDriver);
			volunteerDBYear.setChanged(true);
			return "UPDATED_DRIVER" + gson.toJson(updatedDriver, ONCVolunteer.class);
		}
		else
			return "UPDATE_FAILED";
	}
	
	String delete(int year, String json)
	{
		//Create an object for the delete request
		Gson gson = new Gson();
		ONCVolunteer deletedDriver = gson.fromJson(json, ONCVolunteer.class);
		
		//find and remove the deleted child from the data base
		VolunteerDBYear volunteerDBYear = driverDB.get(year - BASE_YEAR);
		List<ONCVolunteer> dAL = volunteerDBYear.getList();
		
		int index = 0;
		while(index < dAL.size() && dAL.get(index).getID() != deletedDriver.getID())
			index++;
		
		if(index < dAL.size())
		{
			dAL.remove(index);
			volunteerDBYear.setChanged(true);
			return "DELETED_DRIVER" + json;
		}
		else
			return "DELETE_FAILED";	
	}
	
	ONCVolunteer getDriverByDriverNumber(int year, String drvNum)
	{
		List<ONCVolunteer> dAL = driverDB.get(year-BASE_YEAR).getList();
		
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
		ONCVolunteer driver;
		Gson gson = new Gson();
		String change;
		
		if(drvNum1 != null)
		{
			driver = getDriverByDriverNumber(year, drvNum1);
			if(driver != null)
			{
				driver.incrementDeliveryCount(-1);
				change = "UPDATED_DRIVER" + gson.toJson(driver, ONCVolunteer.class);
				clientMgr.notifyAllInYearClients(year, change);	
			}
		}
		
		if(drvNum2 != null)
		{
			driver = getDriverByDriverNumber(year, drvNum2);
			if(driver != null)
			{
				driver.incrementDeliveryCount(1);
				change = "UPDATED_DRIVER" + gson.toJson(driver, ONCVolunteer.class);
				clientMgr.notifyAllInYearClients(year, change);
			}
		}	
	}

	@Override
	void addObject(int year, String[] nextLine)
	{
		VolunteerDBYear volunteerDBYear = driverDB.get(year - BASE_YEAR);
		volunteerDBYear.add(new ONCVolunteer(nextLine, activityDB.createActivityList(year, nextLine[ACTIVITY_STRING_COL], nextLine[COMMENTS_STRING_COL])));	
	}

	@Override
	void createNewYear(int newYear)
	{
		//create a new Driver data base year for the year provided in the newYear parameter
		//The driver db year list is initially empty prior to the import of drivers, so all we
		//do here is create a new DriverDBYear for the newYear and save it.
		VolunteerDBYear volunteerDBYear = new VolunteerDBYear(newYear);
		driverDB.add(volunteerDBYear);
		volunteerDBYear.setChanged(true);	//mark this db for persistent saving on the next save event
	}
	
	private class VolunteerDBYear extends ServerDBYear
	{
		private List<ONCVolunteer> volList;
	    	
	    VolunteerDBYear(int year)
	    {
	    	super();
	    	volList = new ArrayList<ONCVolunteer>();
	    }
	    
	    //getters
	    List<ONCVolunteer> getList() { return volList; }
	    
	    void add(ONCVolunteer addedDriver) { volList.add(addedDriver); }
	}

	@Override
	void save(int year)
	{
		 VolunteerDBYear volunteerDBYear = driverDB.get(year - BASE_YEAR);
		 
		 if(volunteerDBYear.isUnsaved())
		 {
			 String[] driverHeader = {"Driver ID", "Driver Num" ,"First Name", "Last Name", "House Number", "Street",
			 			"Unit", "City", "Zip", "Email", "Home Phone", "Cell Phone", "Comment", "Activity Code",
			 			"Group", "Act Comments", "Qty", "# Del. Assigned", "#Sign-Ins", "Time Stamp", "Changed By",
			 			"Stoplight Pos", "Stoplight Mssg", "Changed By"};
			 
			String path = String.format("%s/%dDB/DriverDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(volunteerDBYear.getList(),  driverHeader, path);
			volunteerDBYear.setChanged(false);
		}
	}	
}
