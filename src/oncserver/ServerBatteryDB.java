package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ourneighborschild.Battery;

public class ServerBatteryDB extends ServerSeasonalDB
{
	private static final int BATTERY_DB_HEADER_LENGTH = 5;
	
	private static List<BatteryDBYear> batteryDB;
	private static ServerBatteryDB instance = null;
	
	private ServerBatteryDB() throws FileNotFoundException, IOException
	{
		batteryDB = new ArrayList<BatteryDBYear>();
				
		//populate the battery data base for the last TOTAL_YEARS from persistent store
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the battery list for each year
			BatteryDBYear batteryDBYear = new BatteryDBYear(year);
					
			//add the list of batteries for the year to the db
			batteryDB.add(batteryDBYear);
					
			//import the batteries from persistent store
			importDB(year, String.format("%s/%dDB/BatteryDB.csv",
					System.getProperty("user.dir"), year), "Battery DB", BATTERY_DB_HEADER_LENGTH);
					
			//set the next id
			batteryDBYear.setNextID(getNextID(batteryDBYear.getList()));
		}
	}
	
	public static ServerBatteryDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerBatteryDB();
		
		return instance;
	}
	
	String getBatteries(int year)
	{
		Gson gson = new Gson();
		Type listOfBatteriess = new TypeToken<ArrayList<Battery>>(){}.getType();
		
		String response = gson.toJson(batteryDB.get(year-BASE_YEAR).getList(), listOfBatteriess);
		return response;	
	}

	String add(int year, String batteryjson)
	{
		//Create a battery object
		Gson gson = new Gson();
		Battery addedBattery = gson.fromJson(batteryjson, Battery.class);
		
		//get the battery data base for the year
		BatteryDBYear batteryDBYear = batteryDB.get(year - BASE_YEAR);
		
		//set the new ID for the added battery
		addedBattery.setID(batteryDBYear.getNextID());
		
		//add the new battery to the data base
		batteryDBYear.add(addedBattery);
		batteryDBYear.setChanged(true);
		
		return "ADDED_BATTERY" + gson.toJson(addedBattery, Battery.class);
	}
	
	String update(int year, String adultjson)
	{
		//Create a Battery object for the updated battery
		Gson gson = new Gson();
		Battery updatedBattery = gson.fromJson(adultjson, Battery.class);
		
		//Find the position for the current battery being updated
		BatteryDBYear batteryDBYear = batteryDB.get(year-BASE_YEAR);
		List<Battery> batteryList = batteryDBYear.getList();
		int index = 0;
		while(index < batteryList.size() && batteryList.get(index).getID() != updatedBattery.getID())
			index++;
		
		//Replace the current battery object with the update
		if(index < batteryList.size())
		{
			batteryList.set(index, updatedBattery);
			batteryDBYear.setChanged(true);
			return "UPDATED_BATTERY" + gson.toJson(updatedBattery, Battery.class);
		}
		else
			return "UPDATE_FAILED";
	}
	
	String delete(int year, String batteryjson)
	{
		//Create a Battery object for the deleted battery
		Gson gson = new Gson();
		Battery deletedBattery = gson.fromJson(batteryjson, Battery.class);
		
		//find and remove the deleted battery from the data base
		BatteryDBYear batteryDBYear = batteryDB.get(year-BASE_YEAR);
		List<Battery> batteryList = batteryDBYear.getList();
		
		int index = 0;
		while(index < batteryList.size() && batteryList.get(index).getID() != deletedBattery.getID())
			index++;
		
		if(index < batteryList.size())
		{
			batteryList.remove(index);
			batteryDBYear.setChanged(true);
			return "DELETED_BATTERY" + batteryjson;
		}
		else
			return "DELETE_FAILED";	
	}

	@Override
	void createNewYear(int newYear)
	{
		//create a new battery data base year for the year provided in the newYear parameter
		//The battery db year list is initially empty
		BatteryDBYear batteryDBYear = new BatteryDBYear(newYear);
		batteryDB.add(batteryDBYear);
		batteryDBYear.setChanged(true);	//mark this db for persistent saving on the next save event	
	}

	@Override
	void addObject(int year, String[] nextLine)
	{
		BatteryDBYear batteryDBYear = batteryDB.get(year-BASE_YEAR);
		batteryDBYear.add(new Battery(nextLine));	
	}

	@Override
	void save(int year)
	{
		BatteryDBYear batteryDBYear = batteryDB.get(year - BASE_YEAR);
		if(batteryDBYear.isUnsaved())
		{
			String[] header = {"Battery ID", "Child ID", "Wish #", "Size", "Quantity"};
			
			String path = String.format("%s/%dDB/BatteryDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(batteryDBYear.getList(),  header, path);
			batteryDBYear.setChanged(false);
		}
	}
	
	private class BatteryDBYear extends ServerDBYear
    {
    		private List<Battery> batteryList;
    	
    		BatteryDBYear(int year)
    		{
    			super();
    			batteryList = new ArrayList<Battery>();
    		}
    	
    		//getters
    		List<Battery> getList() { return batteryList; }
    	
    		void add(Battery addedBattery) { batteryList.add(addedBattery); }
    }	
}
