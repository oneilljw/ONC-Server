package ONCServer;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import OurNeighborsChild.ONCObject;

public abstract class ONCServerDB
{
	protected static final int BASE_YEAR = 2012;
//	protected static final int TOTAL_YEARS = 3;
	
	abstract String add(int year, String userjson);
	
	abstract void createNewYear(int year);	//used to create a new year from the authorized client
	
	public <T extends ONCObject> T find(ArrayList<? extends ONCObject> list, int id, Class<T> type)
	{
		int index = 0;
		while(index < list.size() && list.get(index).getID() != id);
			index++;
		
		if(index < list.size())
			return type.cast(list.get(index));
		else 
			return null;		
	}
	
	void importDB(int year, String path, String name, int length) throws FileNotFoundException, IOException
	{
    	CSVReader reader = new CSVReader(new FileReader(path));
    	String[] nextLine, header;  		
    		
    	if((header = reader.readNext()) != null)	//Does file have records? 
    	{
    		//Read the User File
    		if(header.length == length)	//Does the record have the right # of fields? 
    		{
    			while ((nextLine = reader.readNext()) != null)	// nextLine[] is an array of fields from the record
    				addObject(year, nextLine);
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
	}
	
	abstract void addObject(int year, String[] nextLine);
	
	void exportDBToCSV(List<? extends ONCObject> list, String[] header, String path)
    {	
	    try 
	    {
	    	CSVWriter writer = new CSVWriter(new FileWriter(path));
	    	writer.writeNext(header);
	    	 
	    	for(int index=0; index < list.size(); index++)
	    	{
	    		ONCObject oncObj = (ONCObject)list.get(index);
	    		writer.writeNext(oncObj.getDBExportRow());	//Get family object row
	    	}
	    	
	    	writer.close();
	    	       	    
	    } 
	    catch (IOException x)
	    {
	    	System.err.format("IO Exception: %s%n", x);
	    }
    }
	
	abstract void save(int year);
	
	/**********************************************************************************
	 * This method searches the list for the highest ID and returns the next highest
	 * integer. It is called when initializing the server and reading the data base
	 * from disk.
	 * @param list
	 * @return
	 *********************************************************************************/
	public int getNextID(List<? extends ONCObject> list)
	{
		int hID = 0;
		for(int i=0; i< list.size(); i++)
			if(list.get(i).getID() > hID)
				hID = list.get(i).getID();
		
		return hID+1;
	}
}