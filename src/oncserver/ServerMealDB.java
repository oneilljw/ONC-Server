package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import ourneighborschild.ONCMeal;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerMealDB extends ONCServerDB
{
	private static final int MEAL_DB_HEADER_LENGTH = 11;
	private static final int BASE_YEAR = 2012;
	private static ServerMealDB instance = null;

	private List<MealDBYear> mealDB;
	
	private ServerMealDB() throws FileNotFoundException, IOException
	{
		//create the child wish data base
		mealDB = new ArrayList<MealDBYear>();
						
		//populate the child wish data base for the last TOTAL_YEARS from persistent store
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
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
		
		String response = gson.toJson(mealDB.get(year-BASE_YEAR).getList(), listOfMeals);
		return response;	
	}
	
	@Override
	String add(int year, String mealjson)
	{
		//Create an ONCMeal object for the added meal
		Gson gson = new Gson();
		ONCMeal addedMeal = gson.fromJson(mealjson, ONCMeal.class);
				
		//retrieve the meal data base for the year
		MealDBYear mealDBYear = mealDB.get(year - BASE_YEAR);
						
		//set the new ID for the added ONCMeal
		addedMeal.setID(mealDBYear.getNextID());
					
		return "MEAL_ADDED" + gson.toJson(addedMeal, ONCMeal.class);
	}
	
	String update(int year, String mealjson)
	{
		//Create a ONCMeal object for the updated meal
		Gson gson = new Gson();
		ONCMeal updatedMeal = gson.fromJson(mealjson, ONCMeal.class);
		
		//Find the position for the current meal being updated
		MealDBYear mealDBYear = mealDB.get(year-BASE_YEAR);
		List<ONCMeal> mealAL = mealDBYear.getList();
		int index = 0;
		while(index < mealAL.size() && mealAL.get(index).getID() != updatedMeal.getID())
			index++;
		
		//Replace the current child object with the update
		if(index < mealAL.size())
		{
			//set the prior year history id for the child
			mealAL.set(index, updatedMeal);
			mealDBYear.setChanged(true);
			return "UPDATED_MEAL" + gson.toJson(updatedMeal, ONCMeal.class);
		}
		else
			return "UPDATE_FAILED";
	}
	
	String delete(int year, String mealjson)
	{
		//Create a ONCMeal object for the deleted meal
		Gson gson = new Gson();
		ONCMeal deletedMeal= gson.fromJson(mealjson, ONCMeal.class);
		
		//find and remove the deleted meal from the data base
		MealDBYear mealDBYear = mealDB.get(year-BASE_YEAR);
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

	@Override
	void createNewYear(int newYear)
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
		MealDBYear mealDBYear = mealDB.get(year - BASE_YEAR);
		mealDBYear.add(new ONCMeal(nextLine));
	}

	@Override
	void save(int year)
	{
		String[] header = {"Meal ID", "Family ID", "Type", "Status", "Partner ID",
	 			"Restrictions", "Changed By", "Time Stamp", "SL Pos",
	 			"SL Mssg", "SL Changed By"};
		
		MealDBYear mealDBYear = mealDB.get(year - BASE_YEAR);
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
