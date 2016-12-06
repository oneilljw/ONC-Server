package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import ourneighborschild.HistoryRequest;
import ourneighborschild.ONCDelivery;
import ourneighborschild.ONCFamily;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerDeliveryDB extends ServerSeasonalDB
{
	private static final int DELIVERY_DB_HEADER_LENGTH = 7;
	private static final int DELIVERY_STATUS_ASSIGNED = 3;

	private static List<DeliveryDBYear> deliveryDB;
	
//	private static List<List<ONCDelivery>> deliveryAL;
	private static ServerDeliveryDB instance = null;
//	private static List<Integer> nextID;
	
	private ServerDeliveryDB() throws FileNotFoundException, IOException
	{
		//create the delivery data bases for TOTAL_YEARS number of years
		deliveryDB = new ArrayList<DeliveryDBYear>();
		
//		deliveryAL = new ArrayList<List<ONCDelivery>>();
//		nextID = new ArrayList<Integer>();
//								
//		for(int year = BASE_YEAR; year < BASE_YEAR + TOTAL_YEARS; year++)
//		{
//			deliveryAL.add(new ArrayList<ONCDelivery>());			
//			importDB(year, String.format("%s/%d DB/DeliveryDB.csv",
//						System.getProperty("user.dir"),
//							year), "Delivery DB", DELIVERY_DB_HEADER_LENGTH);
//			
//			nextID.add(setNextID(deliveryAL.get(year - BASE_YEAR)));
//		}
		//populate the data base for the last TOTAL_YEARS from persistent store
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the child list for each year
			DeliveryDBYear delDBYear = new DeliveryDBYear(year);
							
			//add the list of children for the year to the db
			deliveryDB.add(delDBYear);
							
			//import the children from persistent store
			importDB(year, String.format("%s/%dDB/DeliveryDB.csv",
					System.getProperty("user.dir"),
						year), "Delivery DB", DELIVERY_DB_HEADER_LENGTH);
							
			//set the next id
			delDBYear.setNextID(getNextID(delDBYear.getList()));
		}
	}
	
	public static ServerDeliveryDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerDeliveryDB();
		
		return instance;
	}
	
	//Search the database for the family. Return a json if the family is found. 
	String getDeliveries(int year)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCDelivery>>(){}.getType();
			
		String response = gson.toJson(deliveryDB.get(year - BASE_YEAR).getList(), listtype);
		return response;	
	}
/*
	void exportDB(ArrayList<ONCDelivery>eAL, String path)
    {	
	    try 
	    {
	    	CSVWriter writer = new CSVWriter(new FileWriter(path));
	    		
	    	String[] header = {"Delivery ID", "Family ID", "Status", "Del By", 
		 			"Notes", "Changed By", "Time Stamp"};
	    	writer.writeNext(header);
	    	    
	    	for(ONCDelivery d:eAL)
	    		writer.writeNext(d.getDeliverytExportRow());	//Get family object row
	    	 
	    	writer.close();
	    	       	    
	    } 
	    catch (IOException x)
	    {
	    		System.err.format("IO Exception: %s%n", x);
	    }
    }
*/
	@Override
	String add(int year, String json)
	{
		//Create a delivery object for the new delivery
		Gson gson = new Gson();
		ONCDelivery addedDelivery = gson.fromJson(json, ONCDelivery.class);
		
		//add the new delivery to the data base
		DeliveryDBYear delDBYear = deliveryDB.get(year - BASE_YEAR);
		addedDelivery.setID(delDBYear.getNextID());
		delDBYear.add(addedDelivery);
		delDBYear.setChanged(true);
		
		
		//notify the corresponding family that delivery has changed and
		//check to see if new delivery assigned or removed a delivery from a driver
		ServerFamilyDB serverFamilyDB = null;
		ServerVolunteerDB driverDB = null;
		try {
			serverFamilyDB = ServerFamilyDB.getInstance();
			driverDB = ServerVolunteerDB.getInstance();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//get prior delivery for this family
		ONCFamily fam = serverFamilyDB.getFamily(year, addedDelivery.getFamID());
		ONCDelivery priorDelivery = getDelivery(year, fam.getDeliveryID());
		
		//if there was a prior delivery, then update the status and counts
		if(priorDelivery != null)
		{
			//If prior status == ASSIGNED && new status < ASSIGNED, decrement the prior delivery driver
			//else if the prior status == ASSIGNED and new status == ASSIGNED and the driver number changed,
			//Decrement the prior driver deliveries and increment the new driver deliveries. Else, if the 
			//prior status < ASSIGNED and the new status == ASSIGNED, increment the new driver deliveries
			if(priorDelivery.getdStatus() < DELIVERY_STATUS_ASSIGNED && 
					addedDelivery.getdStatus() == DELIVERY_STATUS_ASSIGNED)
			{
				driverDB.updateDriverDeliveryCounts(year, null, addedDelivery.getdDelBy());
			}
			else if(priorDelivery.getdStatus() >= DELIVERY_STATUS_ASSIGNED && 
					 addedDelivery.getdStatus() == DELIVERY_STATUS_ASSIGNED && 
					  !priorDelivery.getdDelBy().equals(addedDelivery.getdDelBy()))
			{
				driverDB.updateDriverDeliveryCounts(year, priorDelivery.getdDelBy(), addedDelivery.getdDelBy());
			}
			else if(priorDelivery.getdStatus() == DELIVERY_STATUS_ASSIGNED && 
					 addedDelivery.getdStatus() < DELIVERY_STATUS_ASSIGNED)
			{
				driverDB.updateDriverDeliveryCounts(year, priorDelivery.getdDelBy(), addedDelivery.getdDelBy());
			}
		}
		//Update the family object with new delivery
		if(serverFamilyDB != null)
			serverFamilyDB.updateFamilyDelivery(year, addedDelivery);
					
		return "ADDED_DELIVERY" + gson.toJson(addedDelivery, ONCDelivery.class);
	}
	
	String add(int year, ONCDelivery addedDelivery)
	{
		//add the new delivery to the data base
		DeliveryDBYear delDBYear = deliveryDB.get(year - BASE_YEAR);
		addedDelivery.setID(delDBYear.getNextID());
		delDBYear.add(addedDelivery);
		delDBYear.setChanged(true);
		
		
		//notify the corresponding family that delivery has changed and
		//check to see if new delivery assigned or removed a delivery from a driver
		ServerFamilyDB serverFamilyDB = null;
		ServerVolunteerDB driverDB = null;
		try {
			serverFamilyDB = ServerFamilyDB.getInstance();
			driverDB = ServerVolunteerDB.getInstance();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//get prior delivery for this family
		ONCFamily fam = serverFamilyDB.getFamily(year, addedDelivery.getFamID());
		ONCDelivery priorDelivery = getDelivery(year, fam.getDeliveryID());
		
		//if there was a prior delivery, then update the status and counts
		if(priorDelivery != null)
		{
			//If prior status == ASSIGNED && new status < ASSIGNED, decrement the prior delivery driver
			//else if the prior status == ASSIGNED and new status == ASSIGNED and the driver number changed,
			//Decrement the prior driver deliveries and increment the new driver deliveries. Else, if the 
			//prior status < ASSIGNED and the new status == ASSIGNED, increment the new driver deliveries
			if(priorDelivery.getdStatus() < DELIVERY_STATUS_ASSIGNED && 
					addedDelivery.getdStatus() == DELIVERY_STATUS_ASSIGNED)
			{
				driverDB.updateDriverDeliveryCounts(year, null, addedDelivery.getdDelBy());
			}
			else if(priorDelivery.getdStatus() >= DELIVERY_STATUS_ASSIGNED && 
					 addedDelivery.getdStatus() == DELIVERY_STATUS_ASSIGNED && 
					  !priorDelivery.getdDelBy().equals(addedDelivery.getdDelBy()))
			{
				driverDB.updateDriverDeliveryCounts(year, priorDelivery.getdDelBy(), addedDelivery.getdDelBy());
			}
			else if(priorDelivery.getdStatus() == DELIVERY_STATUS_ASSIGNED && 
					 addedDelivery.getdStatus() < DELIVERY_STATUS_ASSIGNED)
			{
				driverDB.updateDriverDeliveryCounts(year, priorDelivery.getdDelBy(), addedDelivery.getdDelBy());
			}
		}
		//Update the family object with new delivery
		if(serverFamilyDB != null)
			serverFamilyDB.updateFamilyDelivery(year, addedDelivery);

		Gson gson = new Gson();
		return "ADDED_DELIVERY" + gson.toJson(addedDelivery, ONCDelivery.class);
	}
	
	String addDeliveryGroup(int year, String deliveryGroupJson)
	{
		ClientManager clientMgr = ClientManager.getInstance();
		
		//un-bundle to list of ONCDelivery objects
		Gson gson = new Gson();
		Type listOfDeliveries = new TypeToken<ArrayList<ONCDelivery>>(){}.getType();
		
		List<ONCDelivery> deliveryList = gson.fromJson(deliveryGroupJson, listOfDeliveries);
		
		//for each delivery in the list, add it to the database and notify all clients that
		//it was added
		for(ONCDelivery addedDelivery:deliveryList)
		{
			String response = add(year, addedDelivery);
			//if add was successful, need to q the change to all in-year clients
			//notify in year clients of change
	    	clientMgr.notifyAllInYearClients(year, response);
		}
		
		return "ADDED_GROUP_DELIVERIES";
	}
	
	ONCDelivery getDelivery(int year, int delID)
	{
		List<ONCDelivery> dAL = deliveryDB.get(year-BASE_YEAR).getList();
		int index = 0;	
		while(index < dAL.size() && dAL.get(index).getID() != delID)
			index++;
		
		if(index < dAL.size())
			return dAL.get(index);
		else
			return null;
	}
	
	String update(int year, String json)
	{
		//Create a delivery object for the updated delivery
		Gson gson = new Gson();
		ONCDelivery updatedDelivery = gson.fromJson(json, ONCDelivery.class);
		
		//Find the position for the current delivery being replaced
		DeliveryDBYear deliveryDBYear = deliveryDB.get(year - BASE_YEAR);
		List<ONCDelivery> dAL = deliveryDBYear.getList();
		int index = 0;
		while(index < dAL.size() && dAL.get(index).getID() != updatedDelivery.getID())
			index++;
		
		//Replace the current delivery object with the update
		if(index < dAL.size())
		{
			dAL.set(index, updatedDelivery);
			deliveryDBYear.setChanged(true);
			return "UPDATED_DELIVERY" + gson.toJson(updatedDelivery, ONCDelivery.class);
		}
		else
			return "UPDATE_FAILED";
	}
	
	//Search the database for the family delivery history. Return a json of List<ONCDelivery>
	String getDeliveryHistory(int year, String reqjson)
	{
		//Convert list to json and return it
		Gson gson = new Gson();
		HistoryRequest histReq = gson.fromJson(reqjson, HistoryRequest.class);
			
		List<ONCDelivery> delHistory = new ArrayList<ONCDelivery>();
		List<ONCDelivery> delAL = deliveryDB.get(year - BASE_YEAR).getList();
			
		//Search for deliveries that match the delivery Family ID
		for(ONCDelivery del:delAL)
			if(del.getFamID() == histReq.getID())
				delHistory.add(del);
			
		//Convert list to json and return it
		Type listtype = new TypeToken<ArrayList<ONCDelivery>>(){}.getType();
			
		String response = gson.toJson(delHistory, listtype);
		return response;
	}
	
	
	@Override
	void addObject(int year, String[] nextLine)
	{
		DeliveryDBYear delDBYear = deliveryDB.get(year - BASE_YEAR);
		delDBYear.add(new ONCDelivery(nextLine));	
	}
	
	
	@Override
	void createNewYear(int newYear)
	{
		//create a new Delivery data base year for the year provided in the newYear parameter
		//The delivery db year list is initially empty prior to the import of families, so all we
		//do here is create a new DeliveryDBYear for the newYear and save it.
		DeliveryDBYear deliveryDBYear = new DeliveryDBYear(newYear);
		deliveryDB.add(deliveryDBYear);
		deliveryDBYear.setChanged(true);	//mark this db for persistent saving on the next save event
	}
	
	 private class DeliveryDBYear extends ServerDBYear
	 {
		private List<ONCDelivery> delList;
	    	
	    DeliveryDBYear(int year)
	    {
	    	super();
	    	delList = new ArrayList<ONCDelivery>();
	    }
	    
	    //getters
	    List<ONCDelivery> getList() { return delList; }
	    
	    void add(ONCDelivery addedDel) { delList.add(addedDel); }
	 }

	@Override
	void save(int year)
	{
		String[] header = {"Delivery ID", "Family ID", "Status", "Del By", 
	 			"Notes", "Changed By", "Time Stamp"};
		
		DeliveryDBYear delDBYear = deliveryDB.get(year - BASE_YEAR);
		if(delDBYear.isUnsaved())
		{
//			System.out.println(String.format("DeliveryDB save() - Saving Delivery DB"));
			String path = String.format("%s/%dDB/DeliveryDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(delDBYear.getList(),  header, path);
			delDBYear.setChanged(false);
		}
	}
}
