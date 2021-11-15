package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ourneighborschild.ONCUser;
import ourneighborschild.DistributionCenter;

public class ServerDistributionCenterDB extends ServerSeasonalDB
{
	private static final int PICKUP_LOCATION_DB_HEADER_LENGTH = 14;
	private static ServerDistributionCenterDB instance = null;
	private static final String FILE_NAME = "DistributionCenterDB.csv";

	private static List<DistributionCenterDBYear> centerDB;
	
	private ServerDistributionCenterDB() throws FileNotFoundException, IOException
	{
		//create the data base
		centerDB = new ArrayList<DistributionCenterDBYear>();
						
		//populate the data base for the last TOTAL_YEARS from persistent store
		for(int year = DBManager.getBaseSeason(); year < DBManager.getBaseSeason() + DBManager.getNumberOfYears(); year++)
		{
			//create the list for each year
			DistributionCenterDBYear distCenterDBYear = new DistributionCenterDBYear(year);
							
			//add the list for the year to the db
			centerDB.add(distCenterDBYear);
							
			//import the objects from persistent store
			importDB(year, String.format("%s/%dDB/%s",
					System.getProperty("user.dir"), year, FILE_NAME), "Distribution Center DB", PICKUP_LOCATION_DB_HEADER_LENGTH);
			
			//set the next id
			distCenterDBYear.setNextID(getNextID(distCenterDBYear.getList()));
		}
	}
	
	public static ServerDistributionCenterDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerDistributionCenterDB();
		
		return instance;
	}
	
	String getDistributionCenters(int year)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<DistributionCenter>>(){}.getType();
			
		String response = gson.toJson(centerDB.get(DBManager.offset(year)).getList(), listtype);
		
		return response;	
	}
	
	DistributionCenter getDistributionCenter(int year, int id)
	{
		//get the data base for the year
		DistributionCenterDBYear dcDBYear = centerDB.get(DBManager.offset(year));
		List<DistributionCenter> centerList = dcDBYear.getList();
		
		int index = 0;
		while(index < centerList.size() && centerList.get(index).getID() != id)
			index++;
		
		return index < centerList.size() ? centerList.get(index) : null;
	}

	@Override
	String add(int year, String addjson, ONCUser client)
	{
		//Create a DistributionCenter object
		Gson gson = new Gson();
		DistributionCenter addedCenter = gson.fromJson(addjson, DistributionCenter.class);
		
		//get the data base for the year
		DistributionCenterDBYear dcDBYear = centerDB.get(DBManager.offset(year));
		
		//set the new ID for the added center
		addedCenter.setID(dcDBYear.getNextID());
		
		//add the new center to the data base
		dcDBYear.add(addedCenter);
		dcDBYear.setChanged(true);
		
		return "ADDED_CENTER" + gson.toJson(addedCenter, DistributionCenter.class);
	}
	
	String update(int year, String updatejson, ONCUser user)
	{
		//Create an object for the update
		Gson gson = new Gson();
		DistributionCenter updatedCenter = gson.fromJson(updatejson, DistributionCenter.class);
		
		//Find the position for the current object being replaced
		DistributionCenterDBYear dcDBYear = centerDB.get(DBManager.offset(year));
		List<DistributionCenter> dcList = dcDBYear.getList();
		int index = 0;
		while(index < dcList.size() && dcList.get(index).getID() != updatedCenter.getID())
			index++;
		
		//Replace the current object with the update
		if(index < dcList.size())
		{
			updatedCenter.setDateChanged(System.currentTimeMillis());
			updatedCenter.setChangedBy(user.getLNFI());
			dcList.set(index, updatedCenter);
			dcDBYear.setChanged(true);
			return "UPDATED_CENTER" + gson.toJson(updatedCenter, DistributionCenter.class);
		}
		else
			return "UPDATE_FAILED";
	}
	
	String delete(int year, String json)
	{
		//Create an object for the deleted center
		Gson gson = new Gson();
		DistributionCenter deletedLocation = gson.fromJson(json, DistributionCenter.class);
		
		//check with the family db to see that the center has not been assigned to a family.
		//Only unused locations can be deleted.
		//ADD CODE HERE
		
		//find and remove the deleted center from the data base
		DistributionCenterDBYear dcDBYear = centerDB.get(DBManager.offset(year));
		List<DistributionCenter> dcList = dcDBYear.getList();
		
		int index = 0;
		while(index < dcList.size() && dcList.get(index).getID() != deletedLocation.getID())
			index++;
		
		if(index < dcList.size())
		{
			//center found in data base, remove from the database
			dcList.remove(index);
			dcDBYear.setChanged(true);
			return "DELETED_CENTER" + json;
		}
		else
			return "DELETE_FAILED";	
	}

	@Override
	void createNewSeason(int newYear)
	{
		//create a new distribution center database base for the year provided in the newYear parameter
		//The db year list is initially empty prior determination of locations willing to let ONC use
		//facilities to distribute, so all we /do here is create a new DistributionCenterDBYear and save it.
		DistributionCenterDBYear dcDBYear = new DistributionCenterDBYear(newYear);
		centerDB.add(dcDBYear);
		dcDBYear.setChanged(true);	//mark this db for persistent saving on the next save event	
	}

	@Override
	void addObject(int year, String[] nextLine)
	{
		DistributionCenterDBYear dcDBYear = centerDB.get(DBManager.offset(year));
		dcDBYear.add(new DistributionCenter(nextLine));	
	}

	@Override
	void save(int year)
	{
		DistributionCenterDBYear dcDBYear = centerDB.get(DBManager.offset(year));
		if(dcDBYear.isUnsaved())
		{
			String[] header = {"ID", "Name", "Acronym", "Street #", "Street", "Suffix", "City", "Zipcode",
					"Google Map URL", "Changed By", "Timestamp", "SL Pos", "SL Mssg", "SL Changed By"};
			
			String path = String.format("%s/%dDB/%s", System.getProperty("user.dir"), year, FILE_NAME);
			exportDBToCSV(dcDBYear.getList(), header, path);
			dcDBYear.setChanged(false);
		}
	}
	
	private class DistributionCenterDBYear extends ServerDBYear
    {
		private List<DistributionCenter> centerList;
	
		DistributionCenterDBYear(int year)
		{
			super();
			centerList = new ArrayList<DistributionCenter>();
		}
	
		//getters
		List<DistributionCenter> getList() { return centerList; }
	
		void add(DistributionCenter addedLocation) { centerList.add(addedLocation); }
    }
}
