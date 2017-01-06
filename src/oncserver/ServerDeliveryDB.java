package oncserver;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import ourneighborschild.FamilyGiftStatus;
import ourneighborschild.HistoryRequest;
import ourneighborschild.ONCFamilyHistory;
import ourneighborschild.ONCFamily;
import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerDeliveryDB extends ServerSeasonalDB
{
	private static final int DELIVERY_DB_HEADER_LENGTH = 7;

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
		Type listtype = new TypeToken<ArrayList<ONCFamilyHistory>>(){}.getType();
			
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
		ONCFamilyHistory addedDelivery = gson.fromJson(json, ONCFamilyHistory.class);
		
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
		ONCFamilyHistory priorDelivery = getDelivery(year, fam.getDeliveryID());
		
		//if there was a prior delivery, then update the status and counts
		if(priorDelivery != null)
		{
			//If prior status == ASSIGNED && new status < ASSIGNED, decrement the prior delivery driver
			//else if the prior status == ASSIGNED and new status == ASSIGNED and the driver number changed,
			//Decrement the prior driver deliveries and increment the new driver deliveries. Else, if the 
			//prior status < ASSIGNED and the new status == ASSIGNED, increment the new driver deliveries
			if(priorDelivery.getdStatus().compareTo(FamilyGiftStatus.Assigned) < 0 && 
					addedDelivery.getdStatus() == FamilyGiftStatus.Assigned)
			{
				driverDB.updateDriverDeliveryCounts(year, null, addedDelivery.getdDelBy());
			}
			else if(priorDelivery.getdStatus().compareTo(FamilyGiftStatus.Assigned) >= 0 && 
					 addedDelivery.getdStatus() == FamilyGiftStatus.Assigned && 
					  !priorDelivery.getdDelBy().equals(addedDelivery.getdDelBy()))
			{
				driverDB.updateDriverDeliveryCounts(year, priorDelivery.getdDelBy(), addedDelivery.getdDelBy());
			}
			else if(priorDelivery.getdStatus() == FamilyGiftStatus.Assigned && 
					 addedDelivery.getdStatus().compareTo(FamilyGiftStatus.Assigned) < 0)
			{
				driverDB.updateDriverDeliveryCounts(year, priorDelivery.getdDelBy(), null);
			}
		}
		//Update the family object with new delivery
		if(serverFamilyDB != null)
			serverFamilyDB.updateFamilyDelivery(year, addedDelivery);
					
		return "ADDED_DELIVERY" + gson.toJson(addedDelivery, ONCFamilyHistory.class);
	}
	
	String add(int year, ONCFamilyHistory addedDelivery)
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
		ONCFamilyHistory priorDelivery = getDelivery(year, fam.getDeliveryID());
		
		//if there was a prior delivery, then update the status and counts
		if(priorDelivery != null)
		{
			//If prior status == ASSIGNED && new status < ASSIGNED, decrement the prior delivery driver
			//else if the prior status == ASSIGNED and new status == ASSIGNED and the driver number changed,
			//Decrement the prior driver deliveries and increment the new driver deliveries. Else, if the 
			//prior status < ASSIGNED and the new status == ASSIGNED, increment the new driver deliveries
			if(priorDelivery.getdStatus().compareTo(FamilyGiftStatus.Assigned) < 0 && 
					addedDelivery.getdStatus() == FamilyGiftStatus.Assigned)
			{
				driverDB.updateDriverDeliveryCounts(year, null, addedDelivery.getdDelBy());
			}
			else if(priorDelivery.getdStatus().compareTo(FamilyGiftStatus.Assigned) >= 0 && 
					 addedDelivery.getdStatus() == FamilyGiftStatus.Assigned && 
					  !priorDelivery.getdDelBy().equals(addedDelivery.getdDelBy()))
			{
				driverDB.updateDriverDeliveryCounts(year, priorDelivery.getdDelBy(), addedDelivery.getdDelBy());
			}
			else if(priorDelivery.getdStatus() == FamilyGiftStatus.Assigned && 
					 addedDelivery.getdStatus().compareTo(FamilyGiftStatus.Assigned) < 0)
			{
				driverDB.updateDriverDeliveryCounts(year, priorDelivery.getdDelBy(), null);
			}
		}
		//Update the family object with new delivery
		if(serverFamilyDB != null)
			serverFamilyDB.updateFamilyDelivery(year, addedDelivery);

		Gson gson = new Gson();
		return "ADDED_DELIVERY" + gson.toJson(addedDelivery, ONCFamilyHistory.class);
	}
	
	String addDeliveryGroup(int year, String deliveryGroupJson)
	{
		ClientManager clientMgr = ClientManager.getInstance();
		
		//un-bundle to list of ONCDelivery objects
		Gson gson = new Gson();
		Type listOfDeliveries = new TypeToken<ArrayList<ONCFamilyHistory>>(){}.getType();
		
		List<ONCFamilyHistory> deliveryList = gson.fromJson(deliveryGroupJson, listOfDeliveries);
		
		//for each delivery in the list, add it to the database and notify all clients that
		//it was added
		for(ONCFamilyHistory addedDelivery:deliveryList)
		{
			String response = add(year, addedDelivery);
			//if add was successful, need to q the change to all in-year clients
			//notify in year clients of change
	    	clientMgr.notifyAllInYearClients(year, response);
		}
		
		return "ADDED_GROUP_DELIVERIES";
	}
	
	ONCFamilyHistory getDelivery(int year, int delID)
	{
		List<ONCFamilyHistory> dAL = deliveryDB.get(year-BASE_YEAR).getList();
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
		ONCFamilyHistory updatedDelivery = gson.fromJson(json, ONCFamilyHistory.class);
		
		//Find the position for the current delivery being replaced
		DeliveryDBYear deliveryDBYear = deliveryDB.get(year - BASE_YEAR);
		List<ONCFamilyHistory> dAL = deliveryDBYear.getList();
		int index = 0;
		while(index < dAL.size() && dAL.get(index).getID() != updatedDelivery.getID())
			index++;
		
		//Replace the current delivery object with the update
		if(index < dAL.size())
		{
			dAL.set(index, updatedDelivery);
			deliveryDBYear.setChanged(true);
			return "UPDATED_DELIVERY" + gson.toJson(updatedDelivery, ONCFamilyHistory.class);
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
			
		List<ONCFamilyHistory> delHistory = new ArrayList<ONCFamilyHistory>();
		List<ONCFamilyHistory> delAL = deliveryDB.get(year - BASE_YEAR).getList();
			
		//Search for deliveries that match the delivery Family ID
		for(ONCFamilyHistory del:delAL)
			if(del.getFamID() == histReq.getID())
				delHistory.add(del);
			
		//Convert list to json and return it
		Type listtype = new TypeToken<ArrayList<ONCFamilyHistory>>(){}.getType();
			
		String response = gson.toJson(delHistory, listtype);
		return response;
	}
	
	
	@Override
	void addObject(int year, String[] nextLine)
	{
		DeliveryDBYear delDBYear = deliveryDB.get(year - BASE_YEAR);
		delDBYear.add(new ONCFamilyHistory(nextLine));	
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
		private List<ONCFamilyHistory> delList;
	    	
	    DeliveryDBYear(int year)
	    {
	    	super();
	    	delList = new ArrayList<ONCFamilyHistory>();
	    }
	    
	    //getters
	    List<ONCFamilyHistory> getList() { return delList; }
	    
	    void add(ONCFamilyHistory addedDel) { delList.add(addedDel); }
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
	
	 void convertDeliveryDBForStatusChanges(int year)
	    {
	    	String[] header, nextLine;
	    	List<String[]> outputList = new ArrayList<String[]>();
	    	
	    	//open the current year file
	    	String path = String.format("%s/%dDB/DeliveryDB.csv", System.getProperty("user.dir"), year);
	    	CSVReader reader;
			try 
			{
				reader = new CSVReader(new FileReader(path));
				if((header = reader.readNext()) != null)	//Does file have records? 
		    	{
		    		//Read the User File
		    		if(header.length == DELIVERY_DB_HEADER_LENGTH)	//Does the record have the right # of fields? 
		    		{
		    			String[] outLine = new String[8];
		    			while ((nextLine = reader.readNext()) != null)	// nextLine[] is an array of fields from the record
		    			{
		    				NewFamStatus nfs = getNewFamStatus(nextLine[2]);
		    				outLine[0] = nextLine[0];
		    				outLine[1] = nextLine[1];
		    				outLine[2] = nfs.getNewFamStatus();
		    				outLine[3] = nfs.getNewGiftStatus();
		    				outLine[4] = nextLine[3];
		    				outLine[5] = nextLine[4];
		    				outLine[6] = nextLine[5];
		    				outLine[7] = nextLine[6];
		    				
		    				outputList.add(outLine);
		    			}
		    		}
		    		else
		    		{
		    			String error = String.format("%s file corrupted, header length = %d", path, header.length);
		    	       	JOptionPane.showMessageDialog(null, error,  path + "Corrupted", JOptionPane.ERROR_MESSAGE);
		    		}		   			
		    	}
		    	else
		    	{
		    		String error = String.format("%s file is empty", path);
		    		JOptionPane.showMessageDialog(null, error,  path + " Empty", JOptionPane.ERROR_MESSAGE);
		    	}
		    	
		    	reader.close();
		    	
			} 
			catch (FileNotFoundException e) 
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			catch (IOException e) 
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			//now that we should have an output list of converted String[] for each family, write it
			
			String[] outHeader = {"Delivery ID", "Family ID", "Fam Status", "Gift Status", "Del By", 
		 			"Notes", "Changed By", "Time Stamp"};
			
			System.out.println(String.format("FamilyHistoryDB saveDB - Saving %d New FamilyHistory DB", year));
			String outPath = String.format("%s/%dDB/FamilyHistoryDB.csv", System.getProperty("user.dir"), year);
			
			 try 
			    {
			    	CSVWriter writer = new CSVWriter(new FileWriter(outPath));
			    	writer.writeNext(outHeader);
			    	 
			    	for(int index=0; index < outputList.size(); index++)
			    		writer.writeNext(outputList.get(index));
			    	
			    	writer.close();
			    	       	    
			    } 
			    catch (IOException x)
			    {
			    	System.err.format("IO Exception: %s%n", x);
			    }	
	    }
	
	 /***
     * Used to convert ServerDeliverDB from old delivery status to new family and family gift status
     * @param ofs
     * @param odels
     * @return
     */
    NewFamStatus getNewFamStatus(String odels)
    {
    	int oldGiftStatus = Integer.parseInt(odels);
    	
    	if(oldGiftStatus == 0)
    		return new NewFamStatus(1,1);
    	else if(oldGiftStatus == 1)
    		return new NewFamStatus(2,2);
    	else if(oldGiftStatus == 2)
    		return new NewFamStatus(3,2);
    	else if(oldGiftStatus == 3)
    		return new NewFamStatus(3,6);
    	else if(oldGiftStatus == 4)
    		return new NewFamStatus(3,8);
    	else if(oldGiftStatus == 5)
    		return new NewFamStatus(3,9);
    	else if(oldGiftStatus == 6)
    		return new NewFamStatus(3,7);
    	else if(oldGiftStatus == 7)
    		return new NewFamStatus(3,10);
    	else
    		return null;
    }
    
    private class NewFamStatus
    {
    	private int famStatus;
    	private int giftStatus;
    	
    	NewFamStatus(int famStatus, int giftStatus)
    	{
    		this.famStatus = famStatus;
    		this.giftStatus = giftStatus;
    	}
    	
    	String getNewFamStatus() { return Integer.toString(famStatus); }
    	String getNewGiftStatus() { return Integer.toString(giftStatus); }
    }
}
