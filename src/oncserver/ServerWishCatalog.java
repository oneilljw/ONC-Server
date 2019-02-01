package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ourneighborschild.ONCGift;
import ourneighborschild.ONCUser;

public class ServerWishCatalog extends ServerSeasonalDB
{
	private static final int DB_RECORD_LENGTH = 7;
	private static final String DB_FILENAME = "WishCatalog.csv";
	private static List<WishCatalogDBYear> catalogDB;
	private static ServerWishCatalog instance = null;
	
	private ServerWishCatalog() throws FileNotFoundException, IOException
	{
		//create the wish catalog data bases for TOTAL_YEARS number of years
		catalogDB = new ArrayList<WishCatalogDBYear>();
	
		//populate each year in the db
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the wish catalog list for year
			WishCatalogDBYear catalogDBYear = new WishCatalogDBYear(year);
			
			//add the list for the year to the db
			catalogDB.add(catalogDBYear);
						
			importDB(year, String.format("%s/%dDB/%s",
						System.getProperty("user.dir"),
							year, DB_FILENAME), "Wish Catalog", DB_RECORD_LENGTH);
													
			catalogDBYear.setNextID(getNextID(catalogDBYear.getList()));
		}
	}
	
	public static ServerWishCatalog getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerWishCatalog();
		
		return instance;
	}
	
	//return the catalog list as a json
	String getWishCatalog(int year)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCGift>>(){}.getType();
		
		WishCatalogDBYear catalogDBYear = catalogDB.get(year - BASE_YEAR);
		List<ONCGift> catAL = catalogDBYear.getList();
			
		String response = gson.toJson(catAL, listtype);
		return response;	
	}
	
	String update(int year, String json)
	{
		//Create a wish detail object for the request wish detail update
		Gson gson = new Gson();
		ONCGift reqWish = gson.fromJson(json, ONCGift.class);
		
		//Find the position for the current family being replaced
		WishCatalogDBYear catalogDBYear = catalogDB.get(year - BASE_YEAR);
		List<ONCGift> catAL = catalogDBYear.getList();
		int index = 0;
		while(index < catAL.size() && catAL.get(index).getID() != reqWish.getID())
			index++;
		
		//If catalog wish is located, replace the wish with the update.
		if(index == catAL.size()) 
		{
			ONCGift currWish = catAL.get(index);
			return "UPDATE_FAILED" + gson.toJson(currWish , ONCGift.class);
		}
		else
		{
			catAL.set(index, reqWish);
			catalogDBYear.setChanged(true);
			return "UPDATED_CATALOG_WISH" + json;
		}
	}
	
	@Override
	String add(int year, String json, ONCUser client)
	{
		//Create a wish object to add to the catalog
		Gson gson = new Gson();
		ONCGift addWishReq = gson.fromJson(json, ONCGift.class);
	
		//set the new ID for the catalog wish
		WishCatalogDBYear catalogDBYear = catalogDB.get(year-BASE_YEAR);
		addWishReq.setID(catalogDBYear.getNextID());
		
		//add the new wish
		catalogDBYear.add(addWishReq);
		catalogDBYear.setChanged(true);

		return "ADDED_CATALOG_WISH" + gson.toJson(addWishReq, ONCGift.class);
	}
	
	String delete(int year, String json)
	{
		//Create a catalog wish  object for the deleted catalog wish request
		Gson gson = new Gson();
		ONCGift reqDelWish = gson.fromJson(json, ONCGift.class);
	
		//find the wish in the catalog
		WishCatalogDBYear catalogDBYear = catalogDB.get(year - BASE_YEAR);
		List<ONCGift> catAL = catalogDBYear.getList();
		
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
		List<ONCGift> wishList = catalogDBYear.getList();
		
		//find the ONCWish by ID
		int index = 0;
		while(index < wishList.size() && wishList.get(index).getID() != wishID)
			index++;
		
		if(index < wishList.size())
			return wishList.get(index).getName();
		else
			return "No Wish Found";
	}
	
	static String getGiftCatalogJSONP(int year, String callbackFunction)
	{
		List<ONCGift> websiteGiftList = new ArrayList<ONCGift>();
		
		//get list for year
		WishCatalogDBYear catalogDBYear = catalogDB.get(year - BASE_YEAR);
		List<ONCGift> wishList = catalogDBYear.getList();
		
		for(ONCGift w : wishList)
			if(w.getListindex() > 0)
				websiteGiftList.add(w);
		
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCGift>>(){}.getType();
		String json = gson.toJson(websiteGiftList, listtype);
		return json;
	}
	
	static String getWishHTMLOptions(int year, int wn)
	{
		StringBuffer buff = new StringBuffer();
		
		//get list for year
		WishCatalogDBYear catalogDBYear = catalogDB.get(year - BASE_YEAR);
		List<ONCGift> wishList = catalogDBYear.getList();
		
		for(ONCGift w : wishList)
			if(w.canBeGift(wn))
				buff.append(String.format("<option value=%d>%s</option>", w.getID(), w.getName()));
		
		return buff.toString();
	}
	
	@Override
	void createNewYear(int newYear)
	{
		//create a new wish catalob data base year for the year provided in the newYear parameter
		//The wish catalog db year list is copy of the prior year, so get the list from the prior 
		//year and make a deep copy, then save it as the new year
					
		//create the new WishCatalogDBYear
		WishCatalogDBYear catDBYear = new WishCatalogDBYear(newYear);
		catalogDB.add(catDBYear);
						
		//add a deep copy of each wish detail from last year the new season wish detail list
		List<ONCGift> lyCatList = catalogDB.get(catalogDB.size()-1).getList();
		for(ONCGift lyWish : lyCatList)
			catDBYear.add(new ONCGift(lyWish));	//makes a deep copy of last year
					
		catDBYear.setChanged(true);	//mark this db for persistent saving on the next save event	
	}

	@Override
	void addObject(int year, String[] nextLine) 
	{
		WishCatalogDBYear catDBYear = catalogDB.get(year - BASE_YEAR);
		catDBYear.add(new ONCGift(nextLine));
	}
	
	@Override
	void save(int year)
	{
		String[] header = {"Wish Detail ID", "Name", "List Index", "Wish Detail 1 ID",
				"Wish Detail 2 ID", "Wish Detail 3 ID", "Wish Detail 4 ID"};
		
		WishCatalogDBYear catDBYear = catalogDB.get(year - BASE_YEAR);
		if(catDBYear.isUnsaved())
		{
			String path = String.format("%s/%dDB/%s", System.getProperty("user.dir"), year, DB_FILENAME);
			exportDBToCSV(catDBYear.getList(), header, path);
			catDBYear.setChanged(false);
		}
	}

	private class WishCatalogDBYear extends ServerDBYear
	{
		private List<ONCGift> catList;
	    	
	    WishCatalogDBYear(int year)
	    {
	    		super();
	    		catList = new ArrayList<ONCGift>();
	    }
	    	
	    //getters
	    List<ONCGift> getList() { return catList; }
	
	    void add(ONCGift addedWish) { catList.add(addedWish); }
	}
}
