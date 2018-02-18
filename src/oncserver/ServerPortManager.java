package oncserver;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JOptionPane;

import au.com.bytecode.opencsv.CSVReader;

public class ServerPortManager
{
	private static final int NUM_OF_FIELDS = 1;
	
	private static ServerPortManager instance;
	private static Map<String, Integer> portMap;
	
	public static ServerPortManager getInstance()
	{
		if(instance == null)
			instance = new ServerPortManager();
		
		return instance;
	}
	
	private ServerPortManager()
	{
		portMap = new HashMap<String, Integer>();
		
		try 
		{
			importPorts(String.format("%s/Ports.csv", System.getProperty("user.dir")), "Port List");
		} 
		catch (FileNotFoundException e) 
		{
			portMap.put("Public Port", 80);
			portMap.put("SSL Port", 8902);
		} 
		catch (IOException e) 
		{
			portMap.put("Public Port", 80);
			portMap.put("SSL Port", 8902);
		}
	}
	
	void importPorts(String path, String name) throws FileNotFoundException, IOException
	{
		CSVReader reader = new CSVReader(new FileReader(path));
    		String[] nextLine, header;  		
    		
    		if((header = reader.readNext()) != null)	//Does file have records? 
    		{
    			//Read the data base years file
    			if(header.length == NUM_OF_FIELDS)	//Does the header have the right # of fields? 
    			{
    				if((nextLine = reader.readNext()) != null)
    					portMap.put("Public Port", Integer.parseInt(nextLine[0]));
    				
    				if((nextLine = reader.readNext()) != null)
    					portMap.put("SSL Port", Integer.parseInt(nextLine[0]));
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
	
	//getters
	static Integer getPort(String key)
	{
		return portMap.containsKey(key) ? portMap.get(key) : null;
	}
}
