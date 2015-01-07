package ONCServer;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.Timer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import OurNeighborsChild.DBYear;
import au.com.bytecode.opencsv.CSVReader;

public class DBManager 
{	
	private static final boolean DATABASE_AUTOSAVE_ENABLED = true;
	private static final int DBYEARSDB_NUM_OF_FIELDS = 2;
	private static final int DATABASE_SAVE_TIMER_RATE = 1000 * 60 * 5; //Five minutes
	
	private static DBManager instance = null;
	private Timer dbSaveTimer;
	private List<ONCServerDB> dbAutosaveList;
	private static List<DBYear> dbYearList;
	
	public static DBManager getInstance(ImageIcon appicon)
	{
		if(instance == null)
			instance = new DBManager(appicon);

		return instance;
	}
	
	private DBManager(ImageIcon appicon)
	{
		//Load the list of years contained in the database
		dbYearList = new ArrayList<DBYear>();
		try {
			importDBYears(String.format("%s/dbyears.csv", System.getProperty("user.dir")), "Data base Years");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
		//Load the thirteen component data bases from persistent store;
		//for the nine component data bases that are periodically saved, add them to a auto save list
		dbAutosaveList = new ArrayList<ONCServerDB>();	//list of data bases stored thru timer event
		try
		{
			ServerUserDB.getInstance();	//saved whenever its changed
			RegionDB.getInstance(appicon);	//never changed
			dbAutosaveList.add(GlobalVariableDB.getInstance());
			dbAutosaveList.add(PartnerDB.getInstance());
			dbAutosaveList.add(ServerChildDB.getInstance());
			dbAutosaveList.add(ServerChildWishDB.getInstance());
			dbAutosaveList.add(FamilyDB.getInstance());
			dbAutosaveList.add(AgentDB.getInstance());
			dbAutosaveList.add(ServerDriverDB.getInstance());
			dbAutosaveList.add(ServerDeliveryDB.getInstance());
			dbAutosaveList.add(ServerWishCatalog.getInstance());
			dbAutosaveList.add(ServerWishDetailDB.getInstance());
			dbAutosaveList.add(PriorYearDB.getInstance());	//never changed once created each season
		}
		catch (FileNotFoundException fnf) 
		{
			String error = String.format("Database FileNotFoundException %s", fnf.getMessage());
			       						JOptionPane.showMessageDialog(null, error,  "FileNotFoundException",
			       						JOptionPane.ERROR_MESSAGE, appicon);  
		} 
		catch (IOException ioe)
		{
			String error = String.format("Database I/O exception %s", ioe.getMessage());
			        JOptionPane.showMessageDialog(null, error,  "FileNotFoundException",
								JOptionPane.ERROR_MESSAGE, appicon);  
			        
		}
				
		//Create the data base save timer and enable it if DATABASE_AUTOSAVE_ENABLED
		dbSaveTimer = new Timer(DATABASE_SAVE_TIMER_RATE, new SaveTimerListener());
		if(DATABASE_AUTOSAVE_ENABLED)
			dbSaveTimer.start();
	}
	
	//create a json of DBYear objects
	String getDatabaseStatusList()
	{		
		Gson gson = new Gson();
		Type listOfDBs = new TypeToken<ArrayList<DBYear>>(){}.getType();
			
		String response = "DB_STATUS" + gson.toJson(dbYearList, listOfDBs);
		return response;		
	}
	
	String createNewYear()
	{
		//determine the year. See if it already exists. If it doesn't create it.
		//Only the current year can be created. For example, if the current date is not in 
		//2015, the 2015 data base can not be created.
		
		Calendar today = Calendar.getInstance();
		int currentYear = today.get(Calendar.YEAR);
		
		//check to see if current year is in data base, it would have to be the last one
		//if its not create a new DBYear and add it to the dbYearList
		DBYear lastDBYear = dbYearList.get(dbYearList.size()-1);
		if(lastDBYear.getYear() < currentYear)
		{
			//Lock all prior years
			for(DBYear dbYear: dbYearList)
				dbYear.setLock(true);
			
			//add the new year to the list of db years
//			dbYearList.add(new DBYear(currentYear, false));	//add a new db year to the list
			dbYearList.add(new DBYear(currentYear, true));	//add a new db year to the list - DEBUG LOCKED
			
			//now add a new component year to each of the component data bases. We can
			//conveniently use the dbAutosaveList to help with 9 of the 11 component
			//data bases that need to have a year created.
			for(ONCServerDB componentDB: dbAutosaveList)
				componentDB.createNewYear(currentYear);
			
			//return the DBYear list to the originating client
			Gson gson = new Gson();
			Type listOfDBs = new TypeToken<ArrayList<DBYear>>(){}.getType();
				
			return "ADDED_NEW_YEAR" + gson.toJson(dbYearList, listOfDBs);
		}
		else
			return "ADD_NEW_YEAR_FAILED";
	}
	
	static int getNumberOfYears() { return dbYearList.size(); }
	
	void importDBYears(String path, String name) throws FileNotFoundException, IOException
	{
    	CSVReader reader = new CSVReader(new FileReader(path));
    	String[] nextLine, header;  		
    		
    	if((header = reader.readNext()) != null)	//Does file have records? 
    	{
    		//Read the data base years file
    		if(header.length == DBYEARSDB_NUM_OF_FIELDS)	//Does the header have the right # of fields? 
    		{
    			while ((nextLine = reader.readNext()) != null)	// nextLine[] is an array of fields from the record
    			{
    				int year = (!nextLine[0].isEmpty() && nextLine[0].matches("-?\\d+(\\.\\d+)?")) ? Integer.parseInt(nextLine[0]) : 0;
    				boolean bLocked = nextLine[1].equals("yes");
    				dbYearList.add(new DBYear(year, bLocked));
    			}	
    		}
    		else
    		{
    			String error = String.format("%s file corrupted, header lentgth = %d", name, header.length);
    	       	JOptionPane.showMessageDialog(null, error,  name + "Corrupted", JOptionPane.ERROR_MESSAGE);
    		}		   			
    	}
    	else
    	{
    		String error = String.format("%s file is empty", name);
    		JOptionPane.showMessageDialog(null, error,  name + " Empty", JOptionPane.ERROR_MESSAGE);
    	}
    	
    	reader.close();
	}

	private class SaveTimerListener implements ActionListener
	{
		@Override
		public void actionPerformed(ActionEvent arg0) 
		{
			//For each year in the database that is unlocked, command each of the component 
			//databases that are periodically saved to save data to persistent store
			for(DBYear dbYear: dbYearList)	
				if(!dbYear.isLocked())
					for(ONCServerDB db: dbAutosaveList)
						db.save(dbYear.getYear());		
		}	
	}
}
