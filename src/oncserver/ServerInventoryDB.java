package oncserver;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
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

import ourneighborschild.InventoryChange;
import ourneighborschild.InventoryItem;
import ourneighborschild.InventoryRequest;
import ourneighborschild.ONCObject;
import ourneighborschild.UPCDatabaseItem;
import ourneighborschild.UPCFailure;

public class ServerInventoryDB extends ServerPermanentDB
{
	private static final String INVENTORYDB_FILENAME = "/InventoryDB.csv";
	private static final int INVENTORY_DB_RECORD_LENGTH = 11;
	private static final String API_KEY = "c41c148aae17866d16c9e278968539b3";
	private static final String UPC_LOOKUP_URL = "http://api.upcdatabase.org/json/%s/%s";
	
	private static ServerInventoryDB instance = null;
	private static List<InventoryItem> invList;
//	private static int nextID;
//	private boolean bSaveRequired;
	
	private static List<Integer> hashIndex;
	private static char[] streetLetter = {'?','A','B','C','D','E','F','G','H','I','J','K','L','M',
										 'N','O','P','Q','R','S','T','U','V','W','X','Y','Z'};
	
	ClientManager clientMgr;

	public ServerInventoryDB() throws FileNotFoundException, IOException
	{
		clientMgr = ClientManager.getInstance();
		
		invList = new ArrayList<InventoryItem>();
		importDB(System.getProperty("user.dir") + INVENTORYDB_FILENAME, "Inventory DB", INVENTORY_DB_RECORD_LENGTH);
		nextID = getNextID(invList);
		bSaveRequired = false;
		
		if(invList.size() == 0)
		{
//			createHashTable();	
		}
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
	void addObject(String[] nextLine) 
	{
		invList.add(new InventoryItem(nextLine));
	}
	
	String addFromBarcodeScan(String json)
	{
		//its a bar code add request using InventoryRequest object
		Gson gson = new Gson();
		InventoryRequest addReq = gson.fromJson(json,  InventoryRequest.class);
		
		//determine if the bar code is present in the data base. If it is, 
		//increment the count. Eliminate leading zeros from the bar code since we
		//don't store leading zeros and the UPC Database web site doesn't use leading
		//zeros in the submitted URL
		String searchCode = addReq.getBarcode().replaceFirst("^0+(?!$)", "");
		int index = 0;
		while(index < invList.size() && !invList.get(index).getNumber().equals(searchCode))
			index++;
		
		if(index < invList.size())	//bar code is already in inventory, update the count
		{
			//ensure the item isn't already at a zero count. If it is, return a failure
			if(addReq.getCount() + invList.get(index).getCount() < 0)
			{
				UPCFailure upcFailure = new UPCFailure("false", "Count cannot be less then 0");
				return "UPC_LOOKUP_FAILED" + gson.toJson(upcFailure, UPCFailure.class);
			}
			else
			{
				int updatedCount = invList.get(index).incrementCount(addReq.getCount());
				int updatedCommits = invList.get(index).incrementCommits(addReq.getCommits());
				InventoryChange incItem = new InventoryChange(invList.get(index).getID(), updatedCount, updatedCommits);
				return "INCREMENTED_INVENTORY_ITEM" + gson.toJson(incItem, InventoryChange.class);
			}
		}
		else	//bar code is not in inventory, attempt to get it from an external data base
		{
			String upcDBItemJson = getUPCDataJson(searchCode);
			if(upcDBItemJson == null)
			{
				UPCFailure upcFailure = new UPCFailure("false", "UPC Website did not respond");
				return "UPC_LOOKUP_FAILED" + gson.toJson(upcFailure, UPCFailure.class);
			}
			else if(upcDBItemJson.contains("\"valid\":\"false\""))
			{
				return "ADD_INVENTORY_FALIED" + upcDBItemJson;
			}
			else
			{
				//found the item, create a new InventoryItem, set it's ID, add it to the
				//inventory data base, mark the DB as changed and return the added item
				//and return it. Eliminate any leading zeros in the bar code number prior
				//to storing it in the data base
				UPCDatabaseItem upcDBItem = gson.fromJson(upcDBItemJson, UPCDatabaseItem.class);
				upcDBItem.setNumber(upcDBItem.getNumber().replaceFirst("^0+(?!$)", ""));
				InventoryItem addedItem = new InventoryItem(nextID, upcDBItem);
				
				invList.add(addedItem);
				nextID++;
				bSaveRequired = true;
				
				return "ADDED_INVENTORY_ITEM" + gson.toJson(addedItem, InventoryItem.class);
				
			}
		}
	}
	
	@Override
	String add(String json)
	{
		//it's a manual add request using InventoryItem object. Add it to the db and
		//return it.
		Gson gson = new Gson();
		InventoryItem addItemReq = gson.fromJson(json,  InventoryItem.class);
		
		addItemReq.setID(nextID++);
		invList.add(addItemReq);
		bSaveRequired = true;
			
		return "ADDED_INVENTORY_ITEM" + gson.toJson(addItemReq, InventoryItem.class);
	}
	
	String update(String json)
	{
		//go to the external UPC database and attempt to get the item.
		Gson gson = new Gson();
		InventoryItem updateItemReq = gson.fromJson(json, InventoryItem.class);
		
		//find the item
		int index = 0;
		while(index < invList.size() && invList.get(index).getID() != updateItemReq.getID())
			index++;
		
		if(index < invList.size())
		{
			invList.set(index, updateItemReq);
			return "UPDATED_INVENTORY_ITEM" + gson.toJson(updateItemReq, InventoryItem.class);
		}
		else
			return "UPDATE_INVENTORY_FAILED";	
	}
	
	String delete(String json)
	{
		//delete the item from the database. Check to insure the current inventory count is zero
		Gson gson = new Gson();
		InventoryItem delItemReq = gson.fromJson(json, InventoryItem.class);
		delItemReq.setNumber(delItemReq.getNumber().replaceFirst("^0+(?!$)", ""));
		
		//find the item and if found, ensure quantity is zero
		int index = 0;
		while(index < invList.size() && !invList.get(index).getNumber().equals(delItemReq.getNumber()))
			index++;
		
		if(index < invList.size() && invList.get(index).getCount() == 0)
			return "DELETED_INVENTORY_ITEM" + gson.toJson(delItemReq, InventoryItem.class);
		else
			return "DELETE_INVENTORY_FAILED";
	}
/*
	@Override
	void save()
	{
		if(bSaveRequired)
		{
			String[] header = {"ID", "Count", "Commits", "Number", "Item", "Wish ID", "Alias", "Description",
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
				
				bSaveRequired = false;
			} 
			catch (IOException x)
			{
				System.err.format("IO Exception: %s%n", x);
			}
		}
	}
*/		
	@Override
	String[] getExportHeader()
	{
		return new String[] {"ID", "Count", "Commits", "Number", "Item", "Wish ID", "Alias", "Description",
				"Avg. Price", "Rate Up", "Rate Down"};
	}
		
	@Override
	String getFileName() { return INVENTORYDB_FILENAME; }
	
	@Override
	List<? extends ONCObject> getONCObjectList() { return invList; }
	
	/*****
	 * caller
	 * @param barcode - String representing the bar code number
	 * @return - String that contains the JSON response from UPC Database
	 */
	String getUPCDataJson(String barcode)
	{
		StringBuffer responseJson = new StringBuffer(1024);
		 
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
				    responseJson.append(strLine);
	    			
	    		input.close();
	    		httpconn.disconnect();
			}
	    }
		catch (IOException e1)
		{
			 JOptionPane.showMessageDialog(null, "Can't get UPC Database Item",
						"UPC Database Issue", JOptionPane.ERROR_MESSAGE);
		}
	    
	    //return the UPCDatabaseItem Json
	    return responseJson.toString();
	}
}
