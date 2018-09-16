package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ourneighborschild.ONCWish;

public class ServerWishCatalog extends ServerSeasonalDB
{
//	private static final int WISH_CATALOG_HEADER_LENGTH = 7;
	private static final int DB_RECORD_LENGTH = 7;
	private static final String DB_FILENAME = "WishCatalog.csv";
	private static List<WishCatalogDBYear> catalogDB;
//	private static List<ONCWish> catalogDB;
//	private int nextID;
//	private boolean bSaveRequired;
	private static ServerWishCatalog instance = null;
	
	private ServerWishCatalog() throws FileNotFoundException, IOException
	{
		//create the wish catalog data bases for TOTAL_YEARS number of years
		catalogDB = new ArrayList<WishCatalogDBYear>();
//		catalogDB = new ArrayList<ONCWish>();
	
//		//populate each year in the db
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
		
//		importDB(String.format("%s/PermanentDB/%s", System.getProperty("user.dir"), CATALOG_FILENAME), "Wish Catalog", CATALOG_RECORD_LENGTH);
//		nextID = getNextID(catalogDB);
//		bSaveRequired = false;
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
		Type listtype = new TypeToken<ArrayList<ONCWish>>(){}.getType();
		
		WishCatalogDBYear catalogDBYear = catalogDB.get(year - BASE_YEAR);
		List<ONCWish> catAL = catalogDBYear.getList();
			
		String response = gson.toJson(catAL, listtype);
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
//			bSaveRequired = true;
			return "UPDATED_CATALOG_WISH" + json;
		}
	}
	
	@Override
	String add(int year, String json)
	{
		//Create a wish object to add to the catalog
		Gson gson = new Gson();
		ONCWish addWishReq = gson.fromJson(json, ONCWish.class);
	
		//set the new ID for the catalog wish
		WishCatalogDBYear catalogDBYear = catalogDB.get(year-BASE_YEAR);
		addWishReq.setID(catalogDBYear.getNextID());
		
		//add the new wish
		catalogDBYear.add(addWishReq);
		catalogDBYear.setChanged(true);
		
//		addWishReq.setID(nextID++);
//		catalogDB.add(addWishReq);
//		bSaveRequired = true;
		
		return "ADDED_CATALOG_WISH" + gson.toJson(addWishReq, ONCWish.class);
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
//			bSaveRequired = true;
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
	
	static int findWishIDByName(int year, String wishname)
	{
		//get list for year
		WishCatalogDBYear catalogDBYear = catalogDB.get(year - BASE_YEAR);
		List<ONCWish> wishList = catalogDBYear.getList();
				
		int index=0;
		while(index < wishList.size() &&!wishList.get(index).getName().equals(wishname))
			index++;
				
		if(index < wishList.size())
			return wishList.get(index).getID();
		else
			return -1;
	}
	
	static String getWishHTMLOptions(int year, int wn)
	{
		StringBuffer buff = new StringBuffer();
		
		//get list for year
		WishCatalogDBYear catalogDBYear = catalogDB.get(year - BASE_YEAR);
		List<ONCWish> wishList = catalogDBYear.getList();
		
		for(ONCWish w : wishList)
			if(w.canBeWish(wn))
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
		List<ONCWish> lyCatList = catalogDB.get(catalogDB.size()-1).getList();
		for(ONCWish lyWish : lyCatList)
			catDBYear.add(new ONCWish(lyWish));	//makes a deep copy of last year
					
		catDBYear.setChanged(true);	//mark this db for persistent saving on the next save event	
	}

	@Override
	void addObject(int year, String[] nextLine) 
	{
		WishCatalogDBYear catDBYear = catalogDB.get(year - BASE_YEAR);
		catDBYear.add(new ONCWish(nextLine));
	}
	
	@Override
	void save(int year)
	{
		String[] header = {"Wish Detail ID", "Name", "Choices"};
		
		WishCatalogDBYear catDBYear = catalogDB.get(year - BASE_YEAR);
		if(catDBYear.isUnsaved())
		{
			String path = String.format("%s/%dDB/%s", System.getProperty("user.dir"), year, DB_FILENAME);
			exportDBToCSV(catDBYear.getList(), header, path);
			catDBYear.setChanged(false);
		}
	}
/*
	@Override
	void save()
	{
		if(bSaveRequired)
		{
			String[] header = {"Wish ID", "Name", "List Index", "Wish Detail 1 ID", 
		 			"Wish Detail 2 ID", "Wish Detail 3 ID", "Wish Detail 4 ID"};
			
			String path = System.getProperty("user.dir") + "/WishCatalog.csv";
			File oncwritefile = new File(path);
			
			try 
			{
				CSVWriter writer = new CSVWriter(new FileWriter(oncwritefile.getAbsoluteFile()));
				writer.writeNext(header);
	    	
				for(ONCWish w: catalogDB)
					writer.writeNext(w.getExportRow());	//Write server Apartment row
	    	
				writer.close();
				
				bSaveRequired = false;
			} 
			catch (IOException x)
			{
				System.err.format("IO Exception: %s%n", x);
			}
		}
	}

/*	
	@Override
	String[] getExportHeader()
	{
		return new String[] {"Wish ID", "Name", "List Index", "Wish Detail 1 ID", 
	 						"Wish Detail 2 ID", "Wish Detail 3 ID", "Wish Detail 4 ID"};
	}
	
	@Override
	String getFileName() { return CATALOG_FILENAME; }
	
	@Override
	List<? extends ONCObject> getONCObjectList() { return catalogDB; }
*/	
	private class WishCatalogDBYear extends ServerDBYear
	{
		private List<ONCWish> catList;
	    	
	    WishCatalogDBYear(int year)
	    {
	    		super();
	    		catList = new ArrayList<ONCWish>();
	    }
	    	
	    //getters
	    List<ONCWish> getList() { return catList; }
	
	    void add(ONCWish addedWish) { catList.add(addedWish); }
	}
}
