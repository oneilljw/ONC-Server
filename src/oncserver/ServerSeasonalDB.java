package oncserver;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import javax.swing.JOptionPane;

import au.com.bytecode.opencsv.CSVReader;
import ourneighborschild.ONCUser;

public abstract class ServerSeasonalDB extends ONCServerDB
{
//	protected static final int BASE_SEASON = 2012;
	
	protected static final int AGENT_UNCHANGED = 0;
	protected static final int AGENT_UPDATED = 1;
	protected static final int AGENT_ADDED = 2;
	
	abstract String add(int year, String addjson, ONCUser client);
	
	abstract void createNewSeason(int year);	//used to create a new season from the authorized client
	
	void importDB(int year, String path, String name, int length) throws FileNotFoundException, IOException
	{
    		CSVReader reader = new CSVReader(new FileReader(path));
    		String[] nextLine, header;  		
    		
    		if((header = reader.readNext()) != null)	//Does file have records? 
    		{
    			//Read the file File
    			if(header.length == length)	//Does the record have the right # of fields? 
    			{
    				while ((nextLine = reader.readNext()) != null)	// nextLine[] is an array of fields from the record
    					addObject(year, nextLine);
    			}
    			else
    			{
    				String error = String.format("%s file corrupted, header length = %d", path, header.length);
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
	
	/***
	 * returns a numeric string
	 * @param num
	 * @return
	 */
	String removeNonDigitsFromPhoneNumber(String num)
	{
		//remove all but digits of a phone number
		String noNonDigits = num.replaceAll("[\\D]", "");
		return noNonDigits.trim();
	}
	
	abstract void addObject(int year, String[] nextLine);
	
	abstract void save(int year);
}
