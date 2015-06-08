package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import OurNeighborsChild.ONCWish;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerWishCatalog extends ONCServerDB
{
	private static final int WISH_CATALOG_HEADER_LENGTH = 7;
	private static List<WishCatalogDBYear> catalogDB;
	private static ServerWishCatalog instance = null;
	
	private ServerWishCatalog() throws FileNotFoundException, IOException
	{
		//create the wish catalog data bases for TOTAL_YEARS number of years
		catalogDB = new ArrayList<WishCatalogDBYear>();
	
		//populate the last TOTAL_YEARS
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the wish catalog list for year
			WishCatalogDBYear catalogDBYear = new WishCatalogDBYear(year);
			
			//add the list for the year to the db
			catalogDB.add(catalogDBYear);
						
			importDB(year, String.format("%s/%dDB/WishCatalog.csv",
						System.getProperty("user.dir"),
							year), "Wish Catalog", WISH_CATALOG_HEADER_LENGTH);
													
			catalogDBYear.setNextID(getNextID(catalogDBYear.getList()));
		}
	}
	
	public static ServerWishCatalog getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerWishCatalog();
		
		return instance;
	}
	
	List<ONCWish> getWishCatalogYear(int year)
	{
		return catalogDB.get(year - BASE_YEAR).getList();
	}
	
	//Search the database for the family. Return a json if the family is found. 
	String getWishCatalog(int year)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCWish>>(){}.getType();
			
		String response = gson.toJson(catalogDB.get(year - BASE_YEAR).getList(), listtype);
		return response;	
	}
	
	String update(int year, String json)
	{
		//Create a wish detail object for the request wish detail update
		Gson gson = new Gson();
		ONCWish reqWish = gson.fromJson(json, ONCWish.class);
		
		//Find the position for the current family being replaced
		WishCatalogDBYear catalogDBYear = catalogDB.get(year - BASE_YEAR);
		List<ONCWish> catAL = catalogDBYear.getList();
		int index = 0;
		while(index < catAL.size() && catAL.get(index).getID() != reqWish.getID())
			index++;
		
		//If catalog wish is located, replace the wish with the update.
		if(index == catAL.size()) 
		{
			ONCWish currWish = catAL.get(index);
			return "UPDATE_FAILED" + gson.toJson(currWish , ONCWish.class);
		}
		else
		{
			catAL.set(index, reqWish);
			catalogDBYear.setChanged(true);
			return "UPDATED_CATALOG_WISH" + json;
		}
	}
	
	@Override
	String add(int year, String json)
	{
		//Create a wish object to add to the catalog
		Gson gson = new Gson();
		ONCWish addedWish = gson.fromJson(json, ONCWish.class);
	
		//set the new ID for the catalog wish
		WishCatalogDBYear catalogDBYear = catalogDB.get(year-BASE_YEAR);
		addedWish.setID(catalogDBYear.getNextID());
		
		//add the new wish
		catalogDBYear.add(addedWish);
		catalogDBYear.setChanged(true);
		
		return "ADDED_CATALOG_WISH" + gson.toJson(addedWish, ONCWish.class);
	}
	
	String delete(int year, String json)
	{
		//Create a catalog wish  object for the deleted catalog wish request
		Gson gson = new Gson();
		ONCWish reqDelWish = gson.fromJson(json, ONCWish.class);
	
		//find the wish in the catalog
		WishCatalogDBYear catalogDBYear = catalogDB.get(year - BASE_YEAR);
		List<ONCWish> catAL = catalogDBYear.getList();
		int index = 0;
		while(index < catAL.size() && catAL.get(index).getID() != reqDelWish.getID())
			index++;
		
		//wish must be present in catalog to be deleted
		if(index < catAL.size())
		{
			catAL.remove(index);
			catalogDBYear.setChanged(true);
			return "DELETED_CATALOG_WISH" + json;
		}
		else
			return "DELETE_CATALOG_WISH_FAILED" + json;
	}
	
	String findWishNameByID(int year, int wishID)
	{
		//get list for year
		WishCatalogDBYear catalogDBYear = catalogDB.get(year - BASE_YEAR);
		List<ONCWish> wishList = catalogDBYear.getList();
		
		//find the ONCWish by ID
		int index = 0;
		while(index < wishList.size() && wishList.get(index).getID() != wishID)
			index++;
		
		if(index < wishList.size())
			return wishList.get(index).getName();
		else
			return "No Wish Found";
	}
	

	@Override
	void addObject(int year, String[] nextLine)
	{
		WishCatalogDBYear catalogDBYear = catalogDB.get(year - BASE_YEAR);
		catalogDBYear.add(new ONCWish(nextLine));
	}

	@Override
	void createNewYear(int newYear)
	{
		//create a new wish catalog base year for the year provided in the newYear parameter
		//Then copy the prior years wish catalog to the newly created catalog db year list.
		//Mark the newly created WishCatlogDBYear for saving during the next save event
		
		//get a reference to the prior years wish catalog
		List<ONCWish> lyCatalogList = catalogDB.get(catalogDB.size()-1).getList();
		
		//create the new WishCatalogDBYear
		WishCatalogDBYear wishcatalogDBYear = new WishCatalogDBYear(newYear);
		catalogDB.add(wishcatalogDBYear);
		
		//add a copy of last years catalog wishes to the new years catalog
		for(ONCWish lyCatalogWish : lyCatalogList)
			wishcatalogDBYear.add(new ONCWish(lyCatalogWish));
		
		//set the nextID for the newly created WishCatalogDBYear
		wishcatalogDBYear.setNextID(getNextID(wishcatalogDBYear.getList()));
		
		//Mark the newly created WishCatlogDBYear for saving during the next save event
		wishcatalogDBYear.setChanged(true);
	}
	
	private class WishCatalogDBYear extends ServerDBYear
	{
		private List<ONCWish> wcList;
	    	
	    WishCatalogDBYear(int year)
	    {
	    	super();
	    	wcList = new ArrayList<ONCWish>();
	    }
	    	
	    //getters
	    List<ONCWish> getList() { return wcList; }
	    	
	    void add(ONCWish addedWish) {wcList.add(addedWish); }
	}

	@Override
	void save(int year)
	{
		String[] header = {"Wish ID", "Name", "List Index", "Wish Detail 1 ID", 
		 			"Wish Detail 2 ID", "Wish Detail 3 ID", "Wish Detail 4 ID"};
		 
		WishCatalogDBYear catDBYear = catalogDB.get(year - BASE_YEAR);
		if(catDBYear.isUnsaved())
		{
//			System.out.println(String.format("ServerWishCatalog save() - Saving Server Wish Catalog"));
			String path = String.format("%s/%dDB/WishCatalog.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(catDBYear.getList(),  header, path);
			catDBYear.setChanged(false);
		}
	}
}
