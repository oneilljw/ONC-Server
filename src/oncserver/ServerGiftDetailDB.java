package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import ourneighborschild.GiftDetail;
import ourneighborschild.ONCUser;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerGiftDetailDB extends ServerSeasonalDB
{
	private static final int DB_RECORD_LENGTH = 3;
	private static final String DB_FILENAME = "WishDetailDB.csv";

	private static List<GiftDetailDBYear> gdDB;
	private static ServerGiftDetailDB instance = null;
	
	private ServerGiftDetailDB() throws FileNotFoundException, IOException
	{
		//create the wish gift data bases for total # number of years in the DB
		gdDB = new ArrayList<GiftDetailDBYear>();
														
		for(int year = BASE_SEASON; year < BASE_SEASON + DBManager.getNumberOfYears(); year++)
		{
			//create the gift detail list for year
			GiftDetailDBYear detailDBYear = new GiftDetailDBYear(year);
			
			//add the list for the year to the db
			gdDB.add(detailDBYear);
			
			//import the data from persistent store	
			importDB(year, String.format("%s/%dDB/%s", System.getProperty("user.dir"),
							year, DB_FILENAME), "Wish Detail", DB_RECORD_LENGTH);
			
			//set the next ID
			detailDBYear.setNextID(getNextID(detailDBYear.getList()));
		}
	}
	
	public static ServerGiftDetailDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerGiftDetailDB();
		
		return instance;
	}
	
	String update(int year, String json)
	{
		//Create a gift detail object for the request gift detail update
		Gson gson = new Gson();
		GiftDetail reqGiftDetail = gson.fromJson(json, GiftDetail.class);
		
		//Find the position for the current gift detail being updated
		GiftDetailDBYear detailDBYear = gdDB.get(year - BASE_SEASON);
		List<GiftDetail> giftDetailAL = detailDBYear.getList();
		int index = 0;
		while(index < giftDetailAL.size() && giftDetailAL.get(index).getID() != reqGiftDetail.getID())
			index++;
		
		//If wish detail is located, replace the current wish detail with the update.
		if(index == giftDetailAL.size()) 
		{
			GiftDetail currGiftDetail = giftDetailAL.get(index);
			return "UPDATE_FAILED" + gson.toJson(currGiftDetail , GiftDetail.class);
		}
		else
		{
			giftDetailAL.set(index, reqGiftDetail);
//			bSaveRequired = true;
			return "UPDATED_WISH_DETAIL" + json;
		}
	}
	
	@Override
	String add(int year, String json, ONCUser client)
	{
		//Create a gift detail object
		Gson gson = new Gson();
		GiftDetail addedGiftDetail = gson.fromJson(json, GiftDetail.class);
	
		//set the new id for the detail and add to the year data base
		GiftDetailDBYear detailDBYear = gdDB.get(year - BASE_SEASON);
		List<GiftDetail> giftDetailAL = detailDBYear.getList();
		
		addedGiftDetail.setID(detailDBYear.getNextID());
		giftDetailAL.add(addedGiftDetail);
//		bSaveRequired = true;
		detailDBYear.setChanged(true);
		
		return "ADDED_WISH_DETAIL" + gson.toJson(addedGiftDetail, GiftDetail.class);
	}

	String delete(int year, String json)
	{
		//Create a gift detail object for the deleted gift detail request
		Gson gson = new Gson();
		GiftDetail reqDelWishDetail = gson.fromJson(json, GiftDetail.class);
		
		//find the detail in the db
		GiftDetailDBYear detailDBYear = gdDB.get(year - BASE_SEASON);
		List<GiftDetail> giftDetailAL = detailDBYear.getList();
	
		int index = 0;
		while(index < giftDetailAL.size() && giftDetailAL.get(index).getID() != reqDelWishDetail.getID())
			index++;
		
		//detail must be present to be deleted
		if(index < giftDetailAL.size())
		{
			giftDetailAL.remove(index);
//			bSaveRequired = true;
			detailDBYear.setChanged(true);
			return "DELETED_WISH_DETAIL" + json;
		}
		else
			return "DELETE_FAILED" + json;
	}
	
	//Search the database for the gift detail. Return a json if it's found. 
	String getWishDetail(int year)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<GiftDetail>>(){}.getType();
		
		//find the detail year in the db
		GiftDetailDBYear detailDBYear = gdDB.get(year - BASE_SEASON);
		List<GiftDetail> giftDetailAL = detailDBYear.getList();
			
		String response = gson.toJson(giftDetailAL, listtype);
		
		return response;	
	}

	@Override
	void addObject(int year, String[] nextLine) 
	{
		GiftDetailDBYear detailDBYear = gdDB.get(year - BASE_SEASON);
		detailDBYear.add(new GiftDetail(nextLine));
	}
	
	private class GiftDetailDBYear extends ServerDBYear
	{
		private List<GiftDetail> gdList;
	    	
	    GiftDetailDBYear(int year)
	    {
	    		super();
	    		gdList = new ArrayList<GiftDetail>();
	    }
	    	
	    //getters
	    List<GiftDetail> getList() { return gdList; }
	
	    void add(GiftDetail addedDetail) { gdList.add(addedDetail); }
	}

	@Override
	void save(int year)
	{
		String[] header = {"Gift Detail ID", "Name", "Choices"};
		
		GiftDetailDBYear detailDBYear = gdDB.get(year - BASE_SEASON);
		if(detailDBYear.isUnsaved())
		{
			String path = String.format("%s/%dDB/WishDetailDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(detailDBYear.getList(), header, path);
			detailDBYear.setChanged(false);
		}
	}
	
	@Override
	void createNewSeason(int newYear)
	{
		//create a new gift detail data base year for the year provided in the newYear parameter
		//The gift detail db year list is copy of the prior year, so get the list from the prior 
		//year and make a deep copy, then save it as the new year
		
		//get a reference to prior seasons gift detail list prior to creating a new season dbYear.
		List<GiftDetail> lyDetailList = gdDB.get(gdDB.size()-1).getList();
					
		//create the new GiftDetailDBYear
		GiftDetailDBYear gdDBYear = new GiftDetailDBYear(newYear);
		gdDB.add(gdDBYear);
						
		//add a deep copy of each gift detail from last year
		for(GiftDetail lyWD : lyDetailList)
			gdDBYear.add(new GiftDetail(lyWD));
					
		gdDBYear.setChanged(true);	//mark this db for persistent saving on the next save event	
	}
}
