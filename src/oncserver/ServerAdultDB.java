package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ourneighborschild.AdultGender;
import ourneighborschild.ONCAdult;
import ourneighborschild.ONCUser;

public class ServerAdultDB extends ServerSeasonalDB
{
	private static final int ADULT_DB_HEADER_LENGTH = 4;
	
	private static ServerAdultDB instance = null;
	private static List<AdultDBYear> adultDB;
	
//	private static Connection dbConnection;	//for mysql database
	
	private ServerAdultDB() throws FileNotFoundException, IOException
	{
		//create the adult data base
		adultDB = new ArrayList<AdultDBYear>();
						
		//populate the adult data base for the last TOTAL_YEARS from persistent store
		for(int year = DBManager.getBaseSeason(); year < DBManager.getBaseSeason() + DBManager.getNumberOfYears(); year++)
		{
			//create the meal list for each year
			AdultDBYear adultDBYear = new AdultDBYear(year);
							
			//add the list of adults for the year to the db
			adultDB.add(adultDBYear);
							
			//import the adults from persistent store
			importDB(year, String.format("%s/%dDB/AdultDB.csv",
					System.getProperty("user.dir"),
						year), "Adult DB", ADULT_DB_HEADER_LENGTH);
			
			//set the next id
			adultDBYear.setNextID(getNextID(adultDBYear.getList()));
		}
/*		
		//set up the mysql connection
		dbConnection = null;
	
		try 
		{
		    dbConnection = DriverManager.getConnection("jdbc:mysql://localhost:3306/onc_2018DB?"
	                             + "user=root&password=oncdatabase");
		} 
		catch (SQLException ex) {
		    // handle any errors
		    System.out.println("SQLException: " + ex.getMessage());
		    System.out.println("SQLState: " + ex.getSQLState());
		    System.out.println("VendorError: " + ex.getErrorCode());
		}
*/		
	}
	
	public static ServerAdultDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerAdultDB();
		
		return instance;
	}
/*	
	static HtmlResponse getAdultsInFamilyJSONP(int year, int famID, String callbackFunction) throws SQLException 
	{
		Gson gson = new Gson();
		Type listOfAdults = new TypeToken<ArrayList<ONCAdult>>(){}.getType();
		List<ONCAdult> responseList = new ArrayList<ONCAdult>();
		
		PreparedStatement preparedStatement = null;
		String selectAdultByFamIDSQL = String.format("SELECT * FROM Adults WHERE FamID=%d", famID);

		try 
		{
			preparedStatement = dbConnection.prepareStatement(selectAdultByFamIDSQL);

			// execute select SQL statement
			ResultSet rs = preparedStatement.executeQuery();

			while (rs.next()) 
				responseList.add(new ONCAdult(rs.getInt(1), rs.getInt(2), rs.getString(3), AdultGender.valueOf(rs.getString(4))));
			
			//wrap the json in the callback function per the JSONP protocol
			return new HtmlResponse(callbackFunction +"(" + gson.toJson(responseList, listOfAdults) +")", HttpCode.Ok);		

		}
		catch (SQLException e) 
		{
			return new HtmlResponse(callbackFunction +"(" + gson.toJson(responseList, listOfAdults) +")", HttpCode.Ok);	
		} 
		finally 
		{
			if (preparedStatement != null) 
				preparedStatement.close();
		}
	}
*/
	String getAdults(int year)
	{
		Gson gson = new Gson();
		Type listOfAdults = new TypeToken<ArrayList<ONCAdult>>(){}.getType();
		
		String response = gson.toJson(adultDB.get(DBManager.offset(year)).getList(), listOfAdults);
		return response;	
	}
	
	static HtmlResponse getAdultsInFamilyJSONP(int year, int famID, String callbackFunction)
	{		
		Gson gson = new Gson();
		Type listOfAdults = new TypeToken<ArrayList<ONCAdult>>(){}.getType();
		
		List<ONCAdult> searchList = adultDB.get(DBManager.offset(year)).getList();
		ArrayList<ONCAdult> responseList = new ArrayList<ONCAdult>();
		
		for(ONCAdult a: searchList)
			if(a.getFamID() == famID)
				responseList.add(a);
		
		String response = gson.toJson(responseList, listOfAdults);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}	
	
	List<ONCAdult>getAdultsInFamily(int year, int famID)
	{
		ArrayList<ONCAdult> responseList = new ArrayList<ONCAdult>();
		
		for(ONCAdult a: adultDB.get(DBManager.offset(year)).getList())
			if(a.getFamID() == famID)
				responseList.add(a);
		
		return responseList;		
	}

	@Override
	String add(int year, String adultjson, ONCUser client )
	{
		//Create an ONCAdult object for the added adult
		Gson gson = new Gson();
		ONCAdult addedAdult = gson.fromJson(adultjson, ONCAdult.class);
						
		//retrieve the adult data base for the year
		AdultDBYear adultDBYear = adultDB.get(DBManager.offset(year));
								
		//set the new ID for the added ONCAdult
		addedAdult.setID(adultDBYear.getNextID());
		
		//add the new adult to the data base
		adultDBYear.add(addedAdult);
		adultDBYear.setChanged(true);
							
		return "ADDED_ADULT" + gson.toJson(addedAdult, ONCAdult.class);
	}
	
	ONCAdult add(int year, ONCAdult addedAdult)
	{			
		//retrieve the adult data base for the year
		AdultDBYear adultDBYear = adultDB.get(DBManager.offset(year));
								
		//set the new ID for the added ONCAdult
		int adultID = adultDBYear.getNextID();
		addedAdult.setID(adultID);
		
		//add the new adult to the data base
		adultDBYear.add(addedAdult);
		adultDBYear.setChanged(true);
							
		return addedAdult;
	}
	
	String update(int year, String adultjson)
	{
		//Create a ONCAdult object for the updated adult
		Gson gson = new Gson();
		ONCAdult updatedAdult = gson.fromJson(adultjson, ONCAdult.class);
		
		//Find the position for the current adult being updated
		AdultDBYear adultDBYear = adultDB.get(DBManager.offset(year));
		List<ONCAdult> adultAL = adultDBYear.getList();
		int index = 0;
		while(index < adultAL.size() && adultAL.get(index).getID() != updatedAdult.getID())
			index++;
		
		//Replace the current adult object with the update
		if(index < adultAL.size())
		{
			adultAL.set(index, updatedAdult);
			adultDBYear.setChanged(true);
			return "UPDATED_ADULT" + gson.toJson(updatedAdult, ONCAdult.class);
		}
		else
			return "UPDATE_FAILED";
	}
	
	String delete(int year, String adultjson)
	{
		//Create a ONCAdult object for the deleted adult
		Gson gson = new Gson();
		ONCAdult deletedAdult = gson.fromJson(adultjson, ONCAdult.class);
		
		//find and remove the deleted adult from the data base
		AdultDBYear adultDBYear = adultDB.get(DBManager.offset(year));
		List<ONCAdult> adultAL = adultDBYear.getList();
		
		int index = 0;
		while(index < adultAL.size() && adultAL.get(index).getID() != deletedAdult.getID())
			index++;
		
		if(index < adultAL.size())
		{
			adultAL.remove(index);
			adultDBYear.setChanged(true);
			return "DELETED_ADULT" + adultjson;
		}
		else
			return "DELETE_FAILED";	
	}

	@Override
	void createNewSeason(int year)
	{
		//create a new ONCAdult data base year for the year provided in the year parameter
		//The adult db year list is initially empty prior to the intake of families,
		//so all we do here is create a new AdultDBYear for the new year and save it.
		AdultDBYear adultDBYear = new AdultDBYear(year);
		adultDB.add(adultDBYear);
		adultDBYear.setChanged(true);	//mark this db for persistent saving on the next save event
	}

	@Override
	void addObject(int year, String[] nextLine)
	{
		//Get the adult list for the year and add create and add the adult
		AdultDBYear adultDBYear = adultDB.get(DBManager.offset(year));
		adultDBYear.add(new ONCAdult(nextLine));
	}

	@Override
	void save(int year)
	{
		String[] header = {"ID", "Fam ID", "Name", "Gender"};
		
		AdultDBYear adultDBYear = adultDB.get(DBManager.offset(year));
		if(adultDBYear.isUnsaved())
		{
//			System.out.println(String.format("ServerAdultDB save() - Saving Adult DB, size= %d", adultDBYear.getList().size()));
			String path = String.format("%s/%dDB/AdultDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(adultDBYear.getList(), header, path);
			adultDBYear.setChanged(false);
		}
	}
	
	private class AdultDBYear extends ServerDBYear
    {
    	private List<ONCAdult> adultList;
    	
    	AdultDBYear(int year)
    	{
    		super();
    		adultList = new ArrayList<ONCAdult>();
    	}
    	
    	//getters
    	List<ONCAdult> getList() { return adultList; }
    	
    	void add(ONCAdult addedAdult) { adultList.add(addedAdult); }
    }
}
