package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import ourneighborschild.GiftDetail;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerWishDetailDB extends ServerSeasonalDB
{
	private static final int DB_RECORD_LENGTH = 3;
	private static final String DB_FILENAME = "WishDetailDB.csv";

	private static List<WishDetailDBYear> wdDB;
//	private static List<WishDetail> wdDB;
//	int nextID;
//	boolean bSaveRequired;
	private static ServerWishDetailDB instance = null;
	
	private ServerWishDetailDB() throws FileNotFoundException, IOException
	{
		//create the wish detail data bases for TOTAL_YEARS number of years
		wdDB = new ArrayList<WishDetailDBYear>();
//		wdDB = new ArrayList<WishDetail>();
														
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the wish detail list for year
			WishDetailDBYear detailDBYear = new WishDetailDBYear(year);
			
			//add the list for the year to the db
			wdDB.add(detailDBYear);
			
			//import the data from persistent store	
			importDB(year, String.format("%s/%dDB/%s", System.getProperty("user.dir"),
							year, DB_FILENAME), "Wish Detail", DB_RECORD_LENGTH);
			
			//set the next ID
			detailDBYear.setNextID(getNextID(detailDBYear.getList()));
		}

/*
		importDB(String.format("%s/PermanentDB/%s", System.getProperty("user.dir"), DETAIL_FILENAME), "Wish Detail", DETAIL_RECORD_LENGTH);
		nextID = getNextID(wdDB);
		bSaveRequired = false;
*/		
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
		GiftDetail reqWishDetail = gson.fromJson(json, GiftDetail.class);
		
		//Find the position for the current wish detail being updated
		WishDetailDBYear detailDBYear = wdDB.get(year - BASE_YEAR);
		List<GiftDetail> wishdetailAL = detailDBYear.getList();
		int index = 0;
		while(index < wishdetailAL.size() && wishdetailAL.get(index).getID() != reqWishDetail.getID())
			index++;
		
		//If wish detail is located, replace the current wish detail with the update.
		if(index == wishdetailAL.size()) 
		{
			GiftDetail currWishDetail = wishdetailAL.get(index);
			return "UPDATE_FAILED" + gson.toJson(currWishDetail , GiftDetail.class);
		}
		else
		{
			wishdetailAL.set(index, reqWishDetail);
//			bSaveRequired = true;
			return "UPDATED_WISH_DETAIL" + json;
		}
	}
	
	@Override
	String add(int year, String json)
	{
		//Create a wish detail object
		Gson gson = new Gson();
		GiftDetail addedWishDetail = gson.fromJson(json, GiftDetail.class);
	
		//set the new id for the detail and add to the year data base
		WishDetailDBYear detailDBYear = wdDB.get(year - BASE_YEAR);
		List<GiftDetail> wishdetailAL = detailDBYear.getList();
		
		addedWishDetail.setID(detailDBYear.getNextID());
		wishdetailAL.add(addedWishDetail);
//		bSaveRequired = true;
		detailDBYear.setChanged(true);
		
		return "ADDED_WISH_DETAIL" + gson.toJson(addedWishDetail, GiftDetail.class);
	}

	String delete(int year, String json)
	{
		//Create a wish detail object for the deleted wish detail request
		Gson gson = new Gson();
		GiftDetail reqDelWishDetail = gson.fromJson(json, GiftDetail.class);
		
		//find the detail in the db
		WishDetailDBYear detailDBYear = wdDB.get(year - BASE_YEAR);
		List<GiftDetail> wishdetailAL = detailDBYear.getList();
	
		int index = 0;
		while(index < wishdetailAL.size() && wishdetailAL.get(index).getID() != reqDelWishDetail.getID())
			index++;
		
		//partner must be present and have no ornaments assigned to be deleted
		if(index < wishdetailAL.size())
		{
			wishdetailAL.remove(index);
//			bSaveRequired = true;
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
		Type listtype = new TypeToken<ArrayList<GiftDetail>>(){}.getType();
		
		//find the detail year in the db
		WishDetailDBYear detailDBYear = wdDB.get(year - BASE_YEAR);
		List<GiftDetail> wishdetailAL = detailDBYear.getList();
			
		String response = gson.toJson(wishdetailAL, listtype);
		
		return response;	
	}

	@Override
	void addObject(int year, String[] nextLine) 
	{
		WishDetailDBYear detailDBYear = wdDB.get(year - BASE_YEAR);
		detailDBYear.add(new GiftDetail(nextLine));
//		wdDB.add(new WishDetail(nextLine));
	}
	
	private class WishDetailDBYear extends ServerDBYear
	{
		private List<GiftDetail> wdList;
	    	
	    WishDetailDBYear(int year)
	    {
	    		super();
	    		wdList = new ArrayList<GiftDetail>();
	    }
	    	
	    //getters
	    List<GiftDetail> getList() { return wdList; }
	
	    void add(GiftDetail addedDetail) { wdList.add(addedDetail); }
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
	
	@Override
	void createNewYear(int newYear)
	{
		//create a new wish detail data base year for the year provided in the newYear parameter
		//The wish detail db year list is copy of the prior year, so get the list from the prior 
		//year and make a deep copy, then save it as the new year
					
		//create the new PartnerDBYear
		WishDetailDBYear wdDBYear = new WishDetailDBYear(newYear);
		wdDB.add(wdDBYear);
						
		//add a deep copy of each wish detail from last year the new season wish detail list
		List<GiftDetail> lyDetailList = wdDB.get(wdDB.size()-1).getList();
		for(GiftDetail lyWD : lyDetailList)
			wdDBYear.add(new GiftDetail(lyWD));	//makes a deep copy of last year
					
		wdDBYear.setChanged(true);	//mark this db for persistent saving on the next save event	
	}

/*	
	@Override
	void save()
	{
		if(bSaveRequired)
		{
			String[] header = {"Wish Detail ID", "Name", "Choices"};
			
			String path = System.getProperty("user.dir") + DETAIL_FILENAME;
			File oncwritefile = new File(path);
			
			try 
			{
				CSVWriter writer = new CSVWriter(new FileWriter(oncwritefile.getAbsoluteFile()));
				writer.writeNext(header);
	    	
				for(WishDetail wd: wdDB)
					writer.writeNext(wd.getExportRow());	//Write server Apartment row
	    	
				writer.close();
				
				bSaveRequired = false;
			} 
			catch (IOException x)
			{
				System.err.format("IO Exception: %s%n", x);
			}
		}
	}
	
	@Override
	String[] getExportHeader() { return new String[] {"Wish Detail ID", "Name", "Choices"}; }
	
	@Override
	String getFileName() { return DETAIL_FILENAME; }
	
	@Override
	List<? extends ONCObject> getONCObjectList() { return wdDB; }
*/	
}
