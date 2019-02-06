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

public class ServerGiftCatalog extends ServerSeasonalDB
{
	private static final int DB_RECORD_LENGTH = 7;
	private static final String DB_FILENAME = "WishCatalog.csv";
	private static List<GiftCatalogDBYear> catalogDB;
	private static ServerGiftCatalog instance = null;
	
	private ServerGiftCatalog() throws FileNotFoundException, IOException
	{
		//create the catalog data bases for TOTAL_YEARS number of years
		catalogDB = new ArrayList<GiftCatalogDBYear>();
	
		//populate each year in the db
		for(int year = DBManager.getBaseSeason(); year < DBManager.getBaseSeason() + DBManager.getNumberOfYears(); year++)
		{
			//create the gift catalog list for year
			GiftCatalogDBYear catalogDBYear = new GiftCatalogDBYear(year);
			
			//add the list for the year to the db
			catalogDB.add(catalogDBYear);
						
			importDB(year, String.format("%s/%dDB/%s",
						System.getProperty("user.dir"),
							year, DB_FILENAME), "Wish Catalog", DB_RECORD_LENGTH);
													
			catalogDBYear.setNextID(getNextID(catalogDBYear.getList()));
		}
	}
	
	public static ServerGiftCatalog getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerGiftCatalog();
		
		return instance;
	}
	
	//return the catalog list as a json
	String getGiftCatalog(int year)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCGift>>(){}.getType();
		
		GiftCatalogDBYear catalogDBYear = catalogDB.get(DBManager.offset(year));
		List<ONCGift> catAL = catalogDBYear.getList();
			
		String response = gson.toJson(catAL, listtype);
		return response;	
	}
	
	String update(int year, String json)
	{
		//Create an object for the request ONCGift update
		Gson gson = new Gson();
		ONCGift reqWish = gson.fromJson(json, ONCGift.class);
		
		//Find the position for the current gift being replaced
		GiftCatalogDBYear catalogDBYear = catalogDB.get(DBManager.offset(year));
		List<ONCGift> catAL = catalogDBYear.getList();
		int index = 0;
		while(index < catAL.size() && catAL.get(index).getID() != reqWish.getID())
			index++;
		
		//If catalog gift is located, replace with the update.
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
		//Create an ONCGift object to add to the catalog
		Gson gson = new Gson();
		ONCGift addGiftReq = gson.fromJson(json, ONCGift.class);
	
		//set the new ID for the catalog gift
		GiftCatalogDBYear catalogDBYear = catalogDB.get(DBManager.offset(year));
		addGiftReq.setID(catalogDBYear.getNextID());
		
		//add the new gift
		catalogDBYear.add(addGiftReq);
		catalogDBYear.setChanged(true);

		return "ADDED_CATALOG_WISH" + gson.toJson(addGiftReq, ONCGift.class);
	}
	
	String delete(int year, String json)
	{
		//Create a catalog ONCGift object for the delete catalog gift request
		Gson gson = new Gson();
		ONCGift reqDelGift = gson.fromJson(json, ONCGift.class);
	
		//find the gift in the catalog
		GiftCatalogDBYear catalogDBYear = catalogDB.get(DBManager.offset(year));
		List<ONCGift> catAL = catalogDBYear.getList();
		
		int index = 0;
		while(index < catAL.size() && catAL.get(index).getID() != reqDelGift.getID())
			index++;
		
		//gift must be present in catalog to be deleted
		if(index < catAL.size())
		{
			catAL.remove(index);
			catalogDBYear.setChanged(true);
			return "DELETED_CATALOG_WISH" + json;
		}
		else
			return "DELETE_CATALOG_WISH_FAILED" + json;
	}
	
	String findGiftNameByID(int year, int wishID)
	{
		//get list for year
		GiftCatalogDBYear catalogDBYear = catalogDB.get(DBManager.offset(year));
		List<ONCGift> giftList = catalogDBYear.getList();
		
		//find the ONCGift by ID
		int index = 0;
		while(index < giftList.size() && giftList.get(index).getID() != wishID)
			index++;
		
		if(index < giftList.size())
			return giftList.get(index).getName();
		else
			return "No Wish Found";
	}
	
	static String getGiftCatalogJSONP(int year, String callbackFunction)
	{
		List<ONCGift> websiteGiftList = new ArrayList<ONCGift>();
		
		//get list for year
		GiftCatalogDBYear catalogDBYear = catalogDB.get(DBManager.offset(year));
		List<ONCGift> wishList = catalogDBYear.getList();
		
		for(ONCGift w : wishList)
			if(w.getListindex() > 0)
				websiteGiftList.add(w);
		
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCGift>>(){}.getType();
		String json = gson.toJson(websiteGiftList, listtype);
		return json;
	}
	
	static String getGiftHTMLOptions(int year, int wn)
	{
		StringBuffer buff = new StringBuffer();
		
		//get list for year
		GiftCatalogDBYear catalogDBYear = catalogDB.get(DBManager.offset(year));
		List<ONCGift> giftList = catalogDBYear.getList();
		
		for(ONCGift w : giftList)
			if(w.canBeGift(wn))
				buff.append(String.format("<option value=%d>%s</option>", w.getID(), w.getName()));
		
		return buff.toString();
	}
	
	@Override
	void createNewSeason(int newYear)
	{
		//create a new gift catalog data base year for the year provided in the newYear parameter
		//The catalog db year list is copy of the prior year, so get the list from the prior 
		//year and make a deep copy, then save it as the new year
		
		//get a reference to last seasons gift catalog before adding a new season
		List<ONCGift> lyCatList = catalogDB.get(catalogDB.size()-1).getList();
		
		//create the new GiftCatalogDBYear
		GiftCatalogDBYear catDBYear = new GiftCatalogDBYear(newYear);
		catalogDB.add(catDBYear);
						
		//create a deep copy of last seasons gifts and add it to the new season gift list
		for(ONCGift lyGift : lyCatList)
			catDBYear.add(new ONCGift(lyGift));	
					
		catDBYear.setChanged(true);	//mark this db for persistent saving on the next save event	
	}

	@Override
	void addObject(int year, String[] nextLine) 
	{
		GiftCatalogDBYear catDBYear = catalogDB.get(DBManager.offset(year));
		catDBYear.add(new ONCGift(nextLine));
	}
	
	@Override
	void save(int year)
	{
		String[] header = {"Gift Detail ID", "Name", "List Index", "Gift Detail 1 ID",
				"Gift Detail 2 ID", "Gift Detail 3 ID", "Gift Detail 4 ID"};
		
		GiftCatalogDBYear catDBYear = catalogDB.get(DBManager.offset(year));
		if(catDBYear.isUnsaved())
		{
			String path = String.format("%s/%dDB/%s", System.getProperty("user.dir"), year, DB_FILENAME);
			exportDBToCSV(catDBYear.getList(), header, path);
			catDBYear.setChanged(false);
		}
	}

	private class GiftCatalogDBYear extends ServerDBYear
	{
		private List<ONCGift> catList;
	    	
	    GiftCatalogDBYear(int year)
	    {
	    		super();
	    		catList = new ArrayList<ONCGift>();
	    }
	    	
	    //getters
	    List<ONCGift> getList() { return catList; }
	
	    void add(ONCGift addedWish) { catList.add(addedWish); }
	}
}
