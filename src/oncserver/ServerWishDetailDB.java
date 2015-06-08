package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import OurNeighborsChild.WishDetail;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerWishDetailDB extends ONCServerDB
{
	private static final int WISH_DETAIL_HEADER_LENGTH = 3;

	private static List<WishDetailDBYear> wdDB;
	private static ServerWishDetailDB instance = null;
	
	private ServerWishDetailDB() throws FileNotFoundException, IOException
	{
		//create the wish detail data bases for TOTAL_YEARS number of years
		wdDB = new ArrayList<WishDetailDBYear>();
														
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the wish detail list for year
			WishDetailDBYear detailDBYear = new WishDetailDBYear(year);
			
			//add the list for the year to the db
			wdDB.add(detailDBYear);
			
			//import the data from persistent store	
			importDB(year, String.format("%s/%dDB/WishDetailDB.csv", System.getProperty("user.dir"),
							year), "Wish Catalog", WISH_DETAIL_HEADER_LENGTH);
			
			//set the next ID
			detailDBYear.setNextID(getNextID(detailDBYear.getList()));
		}
	}
	
	public static ServerWishDetailDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerWishDetailDB();
		
		return instance;
	}
	
	String update(int year, String json)
	{
		//Create a wish detail object for the request wish detail update
		Gson gson = new Gson();
		WishDetail reqWishDetail = gson.fromJson(json, WishDetail.class);
		
		//Find the position for the current wish detail being updated
		WishDetailDBYear detailDBYear = wdDB.get(year - BASE_YEAR);
		List<WishDetail> wishdetailAL = detailDBYear.getList();
		int index = 0;
		while(index < wishdetailAL.size() && wishdetailAL.get(index).getID() != reqWishDetail.getID())
			index++;
		
		//If wish detail is located, replace the current wish detail with the update.
		if(index == wishdetailAL.size()) 
		{
			WishDetail currWishDetail = wishdetailAL.get(index);
			return "UPDATE_FAILED" + gson.toJson(currWishDetail , WishDetail.class);
		}
		else
		{
			wishdetailAL.set(index, reqWishDetail);
			detailDBYear.setChanged(true);
			return "UPDATED_WISH_DETAIL" + json;
		}
	}
	
	@Override
	String add(int year, String json)
	{
		//Create a wish detail object
		Gson gson = new Gson();
		WishDetail addedWishDetail = gson.fromJson(json, WishDetail.class);
	
		//set the new id for the detail and add to the year data base
		WishDetailDBYear detailDBYear = wdDB.get(year - BASE_YEAR);
		addedWishDetail.setID(detailDBYear.getNextID());
		detailDBYear.add(addedWishDetail);
		detailDBYear.setChanged(true);
		
		return "ADDED_WISH_DETAIL" + gson.toJson(addedWishDetail, WishDetail.class);
	}

	String delete(int year, String json)
	{
		//Create a wish detail object for the deleted wish detail request
		Gson gson = new Gson();
		WishDetail reqDelWishDetail = gson.fromJson(json, WishDetail.class);
		
		//find the partner in the db
		WishDetailDBYear detailDBYear = wdDB.get(year - BASE_YEAR);
		List<WishDetail> wishdetailAL = detailDBYear.getList();
	
		int index = 0;
		while(index < wishdetailAL.size() && wishdetailAL.get(index).getID() != reqDelWishDetail.getID())
			index++;
		
		//partner must be present and have no ornaments assigned to be deleted
		if(index < wishdetailAL.size())
		{
			wishdetailAL.remove(index);
			detailDBYear.setChanged(true);
			return "DELETED_WISH_DETAIL" + json;
		}
		else
			return "DELETE_FAILED" + json;
	}
	
	//Search the database for the family. Return a json if the family is found. 
	String getWishDetail(int year)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<WishDetail>>(){}.getType();
			
		String response = gson.toJson(wdDB.get(year - BASE_YEAR).getList(), listtype);
		return response;	
	}

	@Override
	void addObject(int year, String[] nextLine)
	{
		WishDetailDBYear detailDBYear = wdDB.get(year - BASE_YEAR);
		detailDBYear.add(new WishDetail(nextLine));
	}

	@Override
	void createNewYear(int newYear)
	{
		//create a new wish detail data base year for the year provided in the newYear parameter
		//Then copy the prior years wish detail list to the newly created wish detail db year list.
		//Mark the newly created WishDetailDBYear for saving during the next save event
				
		//get a reference to the prior years wish detail
		List<WishDetail> lyWishDetailList = wdDB.get(wdDB.size()-1).getList();
				
		//create the new WishCatalogDBYear
		WishDetailDBYear wishdetailDBYear = new WishDetailDBYear(newYear);
		wdDB.add(wishdetailDBYear);
				
		//add a copy of last years catalog wishes to the new years catalog
		for(WishDetail lyWD : lyWishDetailList)
			wishdetailDBYear.add(new WishDetail(lyWD));
		
		//set the nextID for the newly created WishDetailDBYear
		wishdetailDBYear.setNextID(getNextID(wishdetailDBYear.getList()));
		
		//mark the newly created WishDetailDBYear for saving
		wishdetailDBYear.setChanged(true);
	}
	
	private class WishDetailDBYear extends ServerDBYear
	{
		private List<WishDetail> wdList;
	    	
	    WishDetailDBYear(int year)
	    {
	    	super();
	    	wdList = new ArrayList<WishDetail>();
	    }
	    	
	    //getters
	    List<WishDetail> getList() { return wdList; }
	    	
	    void add(WishDetail addedDetail) {wdList.add(addedDetail); }
	}

	@Override
	void save(int year)
	{
		String[] header = {"Wish Detail ID", "Name", "Choices"};
		
		WishDetailDBYear detailDBYear = wdDB.get(year - BASE_YEAR);
		if(detailDBYear.isUnsaved())
		{
			String path = String.format("%s/%dDB/WishDetailDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(detailDBYear.getList(), header, path);
			detailDBYear.setChanged(false);
		}
	}
}
