package oncserver;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import ourneighborschild.ONCObject;
import au.com.bytecode.opencsv.CSVWriter;

public abstract class ONCServerDB
{
	protected static final int USER_UNCHANGED = 0;
	protected static final int USER_UPDATED = 1;
	protected static final int USER_ADDED = 2;

	ONCObject find(List<? extends ONCObject> list, int id)
	{
		int index = 0;
		while(index < list.size() && id != list.get(index).getID())
			index++;
		
		if(index == list.size())
			return null;
		else
			return list.get(index);		
	}
	
	boolean exportDBToCSV(List<? extends ONCObject> list, String[] header, String path)
    {	
	    try 
	    {
	    		CSVWriter writer = new CSVWriter(new FileWriter(path));
	    		writer.writeNext(header);

	    		for(ONCObject o : list)
	    			writer.writeNext(o.getExportRow());	//Get ONCObject row
	    		
	    		writer.close();
	    	
	    		return true;
	    	       	    
	    } 
	    catch (IOException x)
	    {
	    		System.err.format("IO Exception: %s%n", x);
	    		return false;
	    }
    }
	
	/**********************************************************************************
	 * This method searches the list for the highest ID and returns the next highest
	 * integer. It is called when initializing the server and reading the data base
	 * from disk.
	 * @param list
	 * @return
	 *********************************************************************************/
	int getNextID(List<? extends ONCObject> list)
	{
		int hID = 0;
		for(int i=0; i< list.size(); i++)
			if(list.get(i).getID() > hID)
				hID = list.get(i).getID();
		
		return hID+1;
	}
	
	static boolean isNumeric(String str)
	{
		if(str == null || str.isEmpty())
			return false;
		else
			return str.matches("-?\\d+(\\.\\d+)?");  //match a number with optional '-' and decimal.
	}
	
	long parseSciNotationToLong(String input)
	{
		//determine if the input string is in scientific notation or not
		if(input.indexOf('E') > -1)
			return Long.parseLong(String.format("%.0f", Double.parseDouble(input)));
		else if(input.indexOf('.') > 1)
			return Long.parseLong(input.substring(0, input.indexOf('.')));
		else
			return Long.parseLong(input);
	}
	
	String toTitleCase(String inputString)
	{
	    String[] arr = inputString.trim().toLowerCase().split(" ");
	    StringBuffer sb = new StringBuffer();

	    for (int i = 0; i < arr.length; i++) 
	        sb.append(Character.toUpperCase(arr[i].charAt(0))).append(arr[i].substring(1)).append(" ");
	             
	    return sb.toString().trim();
	}
	
	String getStringParam(Map<String, Object> params, String key)
	{
		if(params.containsKey(key) && params.get(key) instanceof String && (String) params.get(key) != null)
			return (String) params.get(key);
		else
			return "";
	}
}
