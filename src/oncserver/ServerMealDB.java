package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ourneighborschild.MealStatus;
import ourneighborschild.ONCFamily;
import ourneighborschild.ONCMeal;
import ourneighborschild.ONCUser;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerMealDB extends ServerSeasonalDB
{
	private static final int MEAL_DB_HEADER_LENGTH = 11;
	private static ServerMealDB instance = null;
	private static final String FILE_NAME = "MealDB.csv";

	private static List<MealDBYear> mealDB;
	
	private ServerMealDB() throws FileNotFoundException, IOException
	{
		//create the meal data base
		mealDB = new ArrayList<MealDBYear>();
						
		//populate the meal data base for the last TOTAL_YEARS from persistent store
		for(int year = DBManager.getBaseSeason(); year < DBManager.getBaseSeason() + DBManager.getNumberOfYears(); year++)
		{
			//create the meal list for each year
			MealDBYear mealDBYear = new MealDBYear(year);
							
			//add the list of meals for the year to the db
			mealDB.add(mealDBYear);
							
			//import the meals from persistent store
			importDB(year, String.format("%s/%dDB/%s",
					System.getProperty("user.dir"), year, FILE_NAME), "Meal DB", MEAL_DB_HEADER_LENGTH);
			
			//set the next id
			mealDBYear.setNextID(getNextID(mealDBYear.getList()));
		}
	}
	
	public static ServerMealDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerMealDB();
		
		return instance;
	}
	
	String getMeals(int year)
	{
		Gson gson = new Gson();
		Type mapOfMeals = new TypeToken<HashMap<Integer,List<ONCMeal>>>(){}.getType();
		return gson.toJson(mealDB.get(DBManager.offset(year)).getMap(), mapOfMeals);	
	}
	
	static HtmlResponse getMealJSONP(int year, String famid, String callbackFunction)
	{		
		Gson gson = new Gson();
		String response;
		
		int famID = Integer.parseInt(famid);
		MealDBYear mDBYear = mealDB.get(DBManager.offset(year));
		List<ONCMeal> famMealList = mDBYear.getFamilyMealList(famID);	//returns null if famID isn't a key in map
		
		//last meal is the current meal
		if(famMealList != null && !famMealList.isEmpty())
			response = gson.toJson(famMealList.get(famMealList.size()-1), ONCMeal.class);
		else
			response = "{\"no_meal_req\":\"Not Requested\"}";
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
/*	
	ONCMeal getMeal(int year, int mID)
	{		
	
		List<ONCMeal> mAL = mealDB.get(DBManager.offset(year)).getList();
		
		int index=0;
		while(index<mAL.size() && mAL.get(index).getID() != mID)
			index++;
		
		return index < mAL.size() ? mAL.get(index) : null; 	
	}
	
	ONCMeal getFamiliesCurrentMeal(int year, int famID)
	{			
		List<ONCMeal> mAL = mealDB.get(DBManager.offset(year)).getList();
		
		int index = mAL.size()-1;
		while(index >= 0 && (mAL.get(index).getFamilyID() != famID || mAL.get(index).getNextID() != -1))
			index--;
		
		if(index >= 0)
		{
			System.out.println(String.format("ServMealDB.getFamCurrMeal: year: %d, famID: %d, mealID: %d",
					year, famID, mAL.get(index).getID()));
			return mAL.get(index);
		}
		else
		{
			System.out.println(String.format("ServMealDB.getFamCurrMeal: year: %d, famID: %d, mealID: %s",
					year, famID, "null meal"));
			return null;
		}
	}
*/	
	ONCMeal getFamiliesCurrentMeal(int year, int famID)
	{	
		MealDBYear mDBYear = mealDB.get(DBManager.offset(year));
		List<ONCMeal> famMealList = mDBYear.getFamilyMealList(famID);	//returns null if famID isn't a key in map
		
		//last meal is the current meal
		return famMealList != null && !famMealList.isEmpty() ? famMealList.get(famMealList.size()-1) : null;		
	}
	
	//add used by desktop client to add a meal. 
	@Override
	String add(int year, String mealjson, ONCUser client)
	{
		//Create an ONCMeal object for the added meal
		Gson gson = new Gson();
		ONCMeal addedMeal = gson.fromJson(mealjson, ONCMeal.class);
				
		//retrieve the meal map for the year
		MealDBYear mealDBYear = mealDB.get(DBManager.offset(year));
		
		//retrieve the current meal for family, if any
//		ONCMeal currMeal = findCurrentMealForFamily(year, addedMeal.getFamilyID());
		ONCMeal priorMeal = getFamiliesCurrentMeal(year, addedMeal.getFamilyID());
		
		//set the new ID and time stamp for the added ONCMeal
		addedMeal.setID(mealDBYear.getNextID());
//		addedMeal.setNextID(-1);
		addedMeal.setDateChanged(System.currentTimeMillis());
		addedMeal.setChangedBy(client.getLNFI());
		addedMeal.setStoplightChangedBy(client.getLNFI());
		
		//set the status of the added meal relative to a parter change.
		//This is the rules engine that governs meal status
		if(priorMeal == null)
		{
			//no prior meal for family
//			addedMeal.setPriorID(-1);
			if(addedMeal.getPartnerID() > -1)
				addedMeal.setMealStatus(MealStatus.Assigned);
			else
				addedMeal.setMealStatus(MealStatus.Requested);
		}
		else
		{
//			addedMeal.setPriorID(priorMeal.getID());
//			priorMeal.setNextID(addedMeal.getID());
			if(priorMeal != null && priorMeal.getPartnerID() != addedMeal.getPartnerID())
			{
				if(addedMeal.getPartnerID() == -1)
					addedMeal.setMealStatus(MealStatus.Requested);
				else
					addedMeal.setMealStatus(MealStatus.Assigned);
			}
		}
		
		//add the new meal to the data base
		mealDBYear.add(addedMeal);
		mealDBYear.setChanged(true);
					
		return "ADDED_MEAL" + gson.toJson(addedMeal, ONCMeal.class);
	}
	
	//adds list of meals changes from client. Only the assignee or status may change and those changes are
	//temporally mutually exclusive 
	String addListOfMealChanges(int year, String mealListJson, ONCUser client)
	{
		//Create an add meal list 
		Gson gson = new Gson();
		Type listOfMeals = new TypeToken<ArrayList<ONCMeal>>(){}.getType();		
		List<ONCMeal> addedMealList = gson.fromJson(mealListJson, listOfMeals);
		List<String> responseJsonList = new ArrayList<String>();

		//retrieve the meal data base for the year
		MealDBYear mealDBYear = mealDB.get(DBManager.offset(year));
		
		for(ONCMeal addedMeal : addedMealList)
		{
			//set the new ID and time stamp for the added ONCMeal
			addedMeal.setID(mealDBYear.getNextID());
//			addedMeal.setNextID(-1);
			addedMeal.setDateChanged(System.currentTimeMillis());
			addedMeal.setChangedBy(client.getLNFI());
			addedMeal.setStoplightChangedBy(client.getLNFI());
		
			//retrieve the current meal being replaced. Set the status of the added meal relative to 
			//a parter change. This is the rules engine that governs meal status
			ONCMeal priorMeal = getFamiliesCurrentMeal(year, addedMeal.getFamilyID());
			if(priorMeal == null)
			{
				//no prior meal for family
//				addedMeal.setPriorID(-1);
				if(addedMeal.getPartnerID() > -1)
					addedMeal.setMealStatus(MealStatus.Assigned);
				else
					addedMeal.setMealStatus(MealStatus.Requested);
			}
			else
			{
//				addedMeal.setPriorID(priorMeal.getID());
//				priorMeal.setNextID(addedMeal.getID());
				if(priorMeal != null && priorMeal.getPartnerID() != addedMeal.getPartnerID())
				{
					if(addedMeal.getPartnerID() == -1)
						addedMeal.setMealStatus(MealStatus.Requested);
					else
						addedMeal.setMealStatus(MealStatus.Assigned);
				}
			}
	
			//add the new meal to the data base
			mealDBYear.add(addedMeal);
			mealDBYear.setChanged(true);
			
			responseJsonList.add("ADDED_MEAL" + gson.toJson(addedMeal, ONCMeal.class));
		}
		
		Type responseListType = new TypeToken<ArrayList<String>>(){}.getType();
		return "ADDED_LIST_MEALS" + gson.toJson(responseJsonList, responseListType);
	}	
	
	//add used by the Web Client. It can only add a new meal when it adds a family
	ONCMeal add(int year, ONCMeal addedMeal)
	{
		//retrieve the meal data base for the year
		MealDBYear mealDBYear = mealDB.get(DBManager.offset(year));
						
		//set the new ID for the added ONCMeal
		int mealID = mealDBYear.getNextID();
		addedMeal.setID(mealID);
		
		//add the new meal to the data base
		mealDBYear.add(addedMeal);
		mealDBYear.setChanged(true);
					
		return addedMeal;
	}

/*
	boolean update(int year, ONCMeal updatedMeal)
	{
		//Find the position for the current meal being updated
		MealDBYear mealDBYear = mealDB.get(DBManager.offset(year));
		List<ONCMeal> mealAL = mealDBYear.getList();
		int index = 0;
		while(index < mealAL.size() && mealAL.get(index).getID() != updatedMeal.getID())
			index++;
		
		//Replace the current meal object with the update
		if(index < mealAL.size())
		{
			mealAL.set(index, updatedMeal);
			mealDBYear.setChanged(true);
			return true;
		}
		else
			return false;
	}
	
	String delete(int year, String mealjson)
	{
		//Create a ONCMeal object for the deleted meal
		Gson gson = new Gson();
		ONCMeal deletedMeal= gson.fromJson(mealjson, ONCMeal.class);
		
		//find and remove the deleted meal from the data base
		MealDBYear mealDBYear = mealDB.get(DBManager.offset(year));
		List<ONCMeal> mealAL = mealDBYear.getList();
		
		int index = 0;
		while(index < mealAL.size() && mealAL.get(index).getID() != deletedMeal.getID())
			index++;
		
		if(index < mealAL.size())
		{
			mealAL.remove(index);
			mealDBYear.setChanged(true);
			return "DELETED_MEAL" + mealjson;
		}
		else
			return "DELETE_FAILED";	
	}
	
	ONCMeal findCurrentMealForFamily(int year, int famid)
	{
		ONCMeal currMeal = null;
		
		MealDBYear mealDBYear = mealDB.get(DBManager.offset(year));
		List<ONCMeal> mealAL = mealDBYear.getList();
		
		//go thru each meal in the db to determine the most current meal for family
		for(ONCMeal meal:mealAL)
			if(meal.getFamilyID() == famid && (currMeal == null || currMeal != null &&
				currMeal.getTimestampDate().before(meal.getTimestampDate())))
			{
				currMeal = meal;	//found a more recent meal for family
			}
		
		return currMeal;
	}
*/	
	void convertToLinkedLists()
	{
		try
		{
			ServerFamilyDB familyDB = ServerFamilyDB.getInstance();
			int[] years = {2015,2016,2017,2018,2019};
			for(int year : years)
			{
				List<ONCMeal> resultList = new ArrayList<ONCMeal>();
				for(ONCFamily f : familyDB.getList(year))
				{	
					List<ONCMeal> sourceList = new ArrayList<ONCMeal>();
					for(ONCMeal m : mealDB.get(DBManager.offset(year)).getList())
						if(f.getID() == m.getFamilyID())
							sourceList.add(m);
					
					if(!sourceList.isEmpty())
					{
						//now that we have a source list, sort it chronologically
						Collections.sort(sourceList, new MealTimestampComparator());
						
						//now that we have a time ordered child gift list, add the prior and next id's
						int index = 0;
						while(index < sourceList.size())
						{
//							sourceList.get(index).setPriorID(index == 0 ? -1 : sourceList.get(index-1).getID());
//							sourceList.get(index).setNextID(index == sourceList.size()-1 ? -1 : sourceList.get(index+1).getID());
							resultList.add(sourceList.get(index));
							index++;
						}	
					}
				}
				
				//now that we have a linked list, sort it by id and save it
				Collections.sort(resultList, new MealIDComparator());
				
				//save it to a new file
				String[] header = {"Meal ID", "Family ID", "Status", "Type",
			 			"Partner ID", "Restrictions", "Changed By", "Time Stamp",
			 			"SL Pos", "SL Mssg", "SL Changed By", "Prior ID", "Next ID"};
				
				String path = String.format("%s/%dDB/NewMealDB.csv", System.getProperty("user.dir"), year);
				exportDBToCSV(resultList, header, path);	
			}
		}
		catch (FileNotFoundException e) 
		{
			
		}
		catch (IOException e) 
		{
			
		}
	}
	
	private class MealIDComparator implements Comparator<ONCMeal>
	{
		@Override
		public int compare(ONCMeal m1, ONCMeal m2)
		{
			if(m1.getID() < m2.getID())
				return -1;
			else if(m1.getID() == m2.getID())
				return 0;
			else
				return 1;
		}
	}
	
	private class MealTimestampComparator implements Comparator<ONCMeal>
	{
		@Override
		public int compare(ONCMeal m1, ONCMeal m2)
		{
			if(m1.getTimestamp() < m2.getTimestamp())
				return -1;
			else if(m1.getTimestamp() == m2.getTimestamp())
				return 0;
			else
				return 1;
		}
	}

	@Override
	void createNewSeason(int newYear)
	{
		//create a new ONCMeal data base year for the year provided in the newYear parameter
		//The meal db year list is initially empty prior to the input of meals,
		//so all we do here is create a new MealDBYear for the newYear and save it.
		MealDBYear mealDBYear = new MealDBYear(newYear);
		mealDB.add(mealDBYear);
		mealDBYear.setChanged(true);	//mark this db for persistent saving on the next save event
	}

	@Override
	void addObject(int year, String[] nextLine)
	{
		//Get the meal list for the year and add create and add the meal
		MealDBYear mealDBYear = mealDB.get(DBManager.offset(year));
		mealDBYear.add(new ONCMeal(nextLine));
	}

	@Override
	void save(int year)
	{
		String[] header = {"Meal ID", "Family ID", "Status", "Type", "Partner ID",
	 			"Restrictions", "Changed By", "Time Stamp", "SL Pos",
	 			"SL Mssg", "SL Changed By"};
		
		MealDBYear mealDBYear = mealDB.get(DBManager.offset(year));
		if(mealDBYear.isUnsaved())
		{
//			System.out.println(String.format("ServerMealDB save() - Saving Meal DB"));
			String path = String.format("%s/%dDB/%s", System.getProperty("user.dir"), year, FILE_NAME);
			exportDBToCSV(mealDBYear.getList(), header, path);
			mealDBYear.setChanged(false);
		}
	}
	
	private class MealDBYear extends ServerDBYear
    {
//    	private List<ONCMeal> mealList;
    	private Map<Integer, List<ONCMeal>> mealMap;
    	
    	MealDBYear(int year)
    	{
    		super();
//    		mealList = new ArrayList<ONCMeal>();
    		mealMap = new HashMap<Integer, List<ONCMeal>>();
    	}
    	
    	//getters
    	List<ONCMeal> getList()
    	{ 
    		List<ONCMeal> allMealsList = new ArrayList<ONCMeal>();
    		for(Map.Entry<Integer,List<ONCMeal>> entry : mealMap.entrySet())
    			for(ONCMeal m : entry.getValue())
    				allMealsList.add(m);
    		
    		return allMealsList;
    	}
    	List<ONCMeal> getFamilyMealList(int famID) { return mealMap.get(famID); }
    	Map<Integer, List<ONCMeal>> getMap() { return mealMap; }
    	
//    	void add(ONCMeal addedMeal) { mealList.add(addedMeal); }
    	void add(ONCMeal addedMeal)
    	{
    		
    		//check to see if famID is a key in the map, if not, add a new linked list
    		if(mealMap.containsKey(addedMeal.getFamilyID()))
    		{
//    			System.out.println(String.format("MealDBYear.add to existing list: famID= %d, priorID= %d, nextID= %d",
//    					addedMeal.getFamilyID(), addedMeal.getPriorID(), addedMeal.getNextID()));
    			mealMap.get(addedMeal.getFamilyID()).add(addedMeal);
    		}
    		else
    		{
//    			System.out.println(String.format("MealDBYear.add to new list: famID= %d, priorID= %d, nextID= %d",
//    					addedMeal.getFamilyID(), addedMeal.getPriorID(), addedMeal.getNextID()));
    			List<ONCMeal> famMealList = new ArrayList<ONCMeal>();
    			famMealList.add(addedMeal);
    			mealMap.put(addedMeal.getFamilyID(), famMealList);
    		}
    	}
    }
}
