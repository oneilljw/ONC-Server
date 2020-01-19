package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import ourneighborschild.MealStatus;
import ourneighborschild.ONCMeal;
import ourneighborschild.ONCUser;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerMealDB extends ServerSeasonalDB
{
	private static final int MEAL_DB_HEADER_LENGTH = 11;
	private static ServerMealDB instance = null;

	private static List<MealDBYear> mealDB;
	
	private ServerMealDB() throws FileNotFoundException, IOException
	{
		//create the child wish data base
		mealDB = new ArrayList<MealDBYear>();
						
		//populate the child wish data base for the last TOTAL_YEARS from persistent store
		for(int year = DBManager.getBaseSeason(); year < DBManager.getBaseSeason() + DBManager.getNumberOfYears(); year++)
		{
			//create the meal list for each year
			MealDBYear mealDBYear = new MealDBYear(year);
							
			//add the list of meals for the year to the db
			mealDB.add(mealDBYear);
							
			//import the meals from persistent store
			importDB(year, String.format("%s/%dDB/MealDB.csv",
					System.getProperty("user.dir"),
						year), "Meal DB", MEAL_DB_HEADER_LENGTH);
			
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
		Type listOfMeals = new TypeToken<ArrayList<ONCMeal>>(){}.getType();
		
		String response = gson.toJson(mealDB.get(DBManager.offset(year)).getList(), listOfMeals);
		return response;	
	}
	
	static HtmlResponse getMealJSONP(int year, String mealID, String callbackFunction)
	{		
		Gson gson = new Gson();
		String response;
		
		List<ONCMeal> mAL = mealDB.get(DBManager.offset(year)).getList();
		int mID = Integer.parseInt(mealID);
		
		int index=0;
		while(index<mAL.size() && mAL.get(index).getID() != mID)
			index++;
		
		if(index < mAL.size())
			response = gson.toJson(mAL.get(index), ONCMeal.class);
		else
			response = "";
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	
	ONCMeal getMeal(int year, int mID)
	{		
	
		List<ONCMeal> mAL = mealDB.get(DBManager.offset(year)).getList();
		
		int index=0;
		while(index<mAL.size() && mAL.get(index).getID() != mID)
			index++;
		
		return index < mAL.size() ? mAL.get(index) : null; 	
	}
	
	//add used by desktop client to add a meal. 
	@Override
	String add(int year, String mealjson, ONCUser client)
	{
		//Create an ONCMeal object for the added meal
		Gson gson = new Gson();
		ONCMeal addedMeal = gson.fromJson(mealjson, ONCMeal.class);
				
		//retrieve the meal data base for the year
		MealDBYear mealDBYear = mealDB.get(DBManager.offset(year));
		
		//retrieve the current meal for family, if any
		ONCMeal currMeal = findCurrentMealForFamily(year, addedMeal.getFamilyID());
		
		//set the new ID and time stamp for the added ONCMeal
		addedMeal.setID(mealDBYear.getNextID());
		addedMeal.setDateChanged(System.currentTimeMillis());
		addedMeal.setChangedBy(client.getLNFI());
		addedMeal.setStoplightChangedBy(client.getLNFI());
		
		//set the status of the added meal relative to a parter change.
		//This is the rules engine that governs meal status
		if(currMeal == null)
		{
			//no prior meal for family
			if(addedMeal.getPartnerID() > -1)
				addedMeal.setMealStatus(MealStatus.Assigned);
			else
				addedMeal.setMealStatus(MealStatus.Requested);
		}
		else
		{
			if(currMeal != null && currMeal.getPartnerID() != addedMeal.getPartnerID())
			{
				if(addedMeal.getPartnerID() == -1)
					addedMeal.setMealStatus(MealStatus.Requested);
				else
					addedMeal.setMealStatus(MealStatus.Assigned);
			}
		}
		
		//notify the family database of an added meal
		ServerFamilyDB serverFamilyDB = null;
		try
		{
			serverFamilyDB = ServerFamilyDB.getInstance();
			serverFamilyDB.familyMealAdded(year, addedMeal);
		}
		catch (FileNotFoundException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (IOException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//add the new meal to the data base
		mealDBYear.add(addedMeal);
		mealDBYear.setChanged(true);
					
		return "ADDED_MEAL" + gson.toJson(addedMeal, ONCMeal.class);
	}
/*	
	MealStatus mealStatusEnginge(ONCMeal currMeal, ONCMeal addedMeal)
	{
		if(addedMeal.getPartnerID() == -1)	//no partner
			return MealStatus.None;
		else if(currMeal.getPartnerID() == -1 && addedMeal.getPartnerID() > -1)
			return MealStatus.Assigned;
		else if(currMeal.getPartnerID() > -1 && currMeal.getPartnerID() != addedMeal.getPartnerID())
			return MealStatus.Assigned;
		else if(currMeal.getPartnerID() > -1 && currMeal.getPartnerID() == addedMeal.getPartnerID())
			return addedMeal.getStatus();
		
	}
*/	
	
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
	
	/***
	 * Used by the web http handler to update a meal after the 
	 */
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
			String path = String.format("%s/%dDB/MealDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(mealDBYear.getList(), header, path);
			mealDBYear.setChanged(false);
		}
	}
	
	private class MealDBYear extends ServerDBYear
    {
    	private List<ONCMeal> mealList;
    	
    	MealDBYear(int year)
    	{
    		super();
    		mealList = new ArrayList<ONCMeal>();
    	}
    	
    	//getters
    	List<ONCMeal> getList() { return mealList; }
    	
    	void add(ONCMeal addedMeal) { mealList.add(addedMeal); }
    }
}
