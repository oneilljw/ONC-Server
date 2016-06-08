package oncserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ourneighborschild.HistoryRequest;
import ourneighborschild.InventoryItem;
import ourneighborschild.ONCChild;
import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

public class ServerInventoryDB extends ONCServerDB
{
	private static final String INVENTORYDB_FILENAME = "/inventoryDB.csv";
	private static final int INVENTORY_DB_RECORD_LENGTH = 9;
	private static final String API_KEY = "c41c148aae17866d16c9e278968539b3";
	private static final String UPC_LOOKUP_URL = "http://api.upcdatabase.org/json/%s/%s";
	
	private static ServerInventoryDB instance = null;
	private static List<InventoryItem> invList;
	private int id;
	
	private static List<Integer> hashIndex;
	private static char[] streetLetter = {'?','A','B','C','D','E','F','G','H','I','J','K','L','M',
										 'N','O','P','Q','R','S','T','U','V','W','X','Y','Z'};
	
	ClientManager clientMgr;

	public ServerInventoryDB() throws FileNotFoundException, IOException
	{
		clientMgr = ClientManager.getInstance();
		
		invList = new ArrayList<InventoryItem>();
//		System.out.println(String.format("ServerInventoryDB filename: %s", System.getProperty("user.dir") + INVENTORYDB_FILENAME));
		importDB(0, System.getProperty("user.dir") + INVENTORYDB_FILENAME, "User DB", INVENTORY_DB_RECORD_LENGTH);
		id = getNextID(invList);
		
		if(invList.size() == 0)
		{
//			createHashTable();	
		}
/*		
		String upcJson = getUPCData("38000311109");
		Gson gson = new Gson();
		UPCDatabaseItem upcDBItem= gson.fromJson(upcJson, UPCDatabaseItem.class);
		InventoryItem ii = new InventoryItem(0, upcDBItem);
		System.out.println(String.format("ServerInventoryDB.constructor: ItemID %d, number %s, item %s", ii.getID(), ii.getNumber(), ii.getItemName()));
*/
	}
	
	public static ServerInventoryDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerInventoryDB();
		
		return instance;
	}
	
	static void createHashTable()
	{
		//build hash index array. Used to quickly search the regions list. Hash is based on first character
		//in street name.			
		hashIndex = new ArrayList<Integer>();
		int letterIndex = 1;	//used to iterate the regions array
						
		hashIndex.add(0);	//add the first row to the hash, it's the streets that begin with a number
		while(letterIndex < streetLetter.length)		
		{
			//find street that starts with the current street letter
			int index = 0;
			while(index < invList.size() && invList.get(index).getItemName().charAt(0) != streetLetter[letterIndex])
				index++;
			
			if(index < invList.size())	//first street found
			hashIndex.add(index++);
			else	//no street found that starts with that letter, set the hashIndex = to it's size
				hashIndex.add(invList.size());
			
			letterIndex++;
		}
		
		hashIndex.add(invList.size());	//add the last index into the hash table
	}
	
	//Get a json of the list of inventory in the data base 
	String getInventory()
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<List<InventoryItem>>(){}.getType();
				
		String response = gson.toJson(invList, listtype);
		return response;	
	}
	
	@Override
	void addObject(int year, String[] nextLine) 
	{
		invList.add(new InventoryItem(nextLine));
	}	
	
	@Override
	String add(int year, String barcode)
	{
		//go to the external UPC database and attempt to get the item.
		
		Gson gson = new Gson();
		
		InventoryItem response = new InventoryItem(0, null);
		
		return "ADDED_INVENTORY" + gson.toJson(response, InventoryItem.class);
	}
	
	String increment(int id)
	{
		Gson gson = new Gson();
		
		HistoryRequest response = new HistoryRequest(0, 0);
		
		return "INCREMENTED_INVENTORY" + gson.toJson(response, HistoryRequest.class);
	}
	
	String decrement(int id)
	{
		Gson gson = new Gson();
		
		HistoryRequest response = new HistoryRequest(0, 0);
		
		return "DECREMENTED_INVENTORY" + gson.toJson(response, HistoryRequest.class);
	}
	
	String update(String json)
	{
		//go to the external UPC database and attempt to get the item.
		Gson gson = new Gson();
				
		InventoryItem response = new InventoryItem(0, null);
				
		return "UPDATED_INVENTORY" + gson.toJson(response, InventoryItem.class);
	}
	
	String delete(String json)
	{
		//go to the external UPC database and attempt to get the item.
		Gson gson = new Gson();
				
		InventoryItem response = new InventoryItem(0, null);
				
		return "DELETED_INVENTORY" + gson.toJson(response, InventoryItem.class);
	}

	@Override
	void save(int year)
	{
		String[] header = {"ID", "Count", "Number", "Item", "Alias", "Description",
							"Avg. Price", "Rate Up", "Rate Down"};
		
		String path = System.getProperty("user.dir") + "/InventoryDB.csv";
		File oncwritefile = new File(path);
			
		try 
	    {
	    	CSVWriter writer = new CSVWriter(new FileWriter(oncwritefile.getAbsoluteFile()));
	    	writer.writeNext(header);
	    	
	    	for(InventoryItem i: invList)
	    		writer.writeNext(i.getExportRow());	//Write server Apartment row
	    	
	    	writer.close();
	    } 
	    catch (IOException x)
	    {
	    	System.err.format("IO Exception: %s%n", x);
	    }
	}
	
	/*****
	 * caller
	 * @param barcode - String representing the bar code number
	 * @return - String that contains the JSON response from UPC Database
	 */
	String getUPCData(String barcode)
	{
		StringBuffer response = new StringBuffer(1024);
		 
		//Turn the string into a valid URL
	    URL modAPIUrl = null;
		try
		{
			String stringURL = String.format(UPC_LOOKUP_URL, API_KEY, barcode);
			modAPIUrl = new URL(stringURL);
		} 
		catch (MalformedURLException e2)
		{
			JOptionPane.showMessageDialog(null, "Can't form UPC Database API URL",
					"UPC Database API Issue", JOptionPane.ERROR_MESSAGE);
			
			return null;
		}
		
		//Attempt to open the URL via a network connection to the Internet
	    HttpURLConnection httpconn = null;
		try
		{
			httpconn = (HttpURLConnection)modAPIUrl.openConnection();
		} 
		catch (IOException e1)
		{
			JOptionPane.showMessageDialog(null, "Can't access UPC Database API",
					"UPC Database Access Issue", JOptionPane.ERROR_MESSAGE);
			
			return null;
		}
		
		//It opened successfully, get the data via HTTP GET
	    try
	    {
	    	if (httpconn.getResponseCode() == HttpURLConnection.HTTP_OK)
	    	{
	    		BufferedReader input = new BufferedReader(new InputStreamReader(httpconn.getInputStream()), 1024);
	    		String strLine = null;
	    		while ((strLine = input.readLine()) != null)
				    response.append(strLine);
	    			
	    		input.close();
			}
	    }
		catch (IOException e1)
		{
			 JOptionPane.showMessageDialog(null, "Can't get UPC Database Item",
						"UPC Database Issue", JOptionPane.ERROR_MESSAGE);
		}
	    
	    //return the response as a string
//	    System.out.println(String.format("InventoryDB.getUPCData: response: %s", response.toString()));
	    return response.toString();
	}

	@Override
	void createNewYear(int year) 
	{
		//only one Inventory data base, does not have year based data
	}
}
