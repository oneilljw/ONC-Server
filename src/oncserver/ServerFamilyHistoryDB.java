package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ourneighborschild.DNSCode;
import ourneighborschild.FamilyGiftStatus;
import ourneighborschild.HistoryRequest;
import ourneighborschild.ONCChild;
import ourneighborschild.ONCChildGift;
import ourneighborschild.FamilyHistory;
import ourneighborschild.FamilyStatus;
import ourneighborschild.GiftStatus;
import ourneighborschild.ONCUser;
import ourneighborschild.SMSStatus;
import ourneighborschild.ONCFamily;
import ourneighborschild.ONCSMS;
import ourneighborschild.ONCServerUser;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerFamilyHistoryDB extends ServerSeasonalDB
{
	private static final int FAMILY_HISTORY_DB_HEADER_LENGTH = 9;

	private static List<FamilyHistoryDBYear> famHistDB;
	private static ServerGlobalVariableDB globalDB;
	private static ServerFamilyHistoryDB instance = null;
	private static FamilyHistoryTimestampComparator familyHistoryTimestampComparator;
	
	private static ClientManager clientMgr;
	
	private ServerFamilyHistoryDB() throws FileNotFoundException, IOException
	{
		//create the family history data bases for TOTAL_YEARS number of years
		famHistDB = new ArrayList<FamilyHistoryDBYear>();

		//populate the data base for the last TOTAL_YEARS from persistent store
		for(int year = DBManager.getBaseSeason(); year <DBManager.getBaseSeason() + DBManager.getNumberOfYears(); year++)
		{
			//create the family history list for each year
			FamilyHistoryDBYear fhDBYear = new FamilyHistoryDBYear(year);
							
			//add the list of children for the year to the db
			famHistDB.add(fhDBYear);
							
			//import the children from persistent store
			importDB(year, String.format("%s/%dDB/FamilyHistoryDB.csv",
					System.getProperty("user.dir"),
						year), "Delivery DB", FAMILY_HISTORY_DB_HEADER_LENGTH);
							
			//set the next id
			fhDBYear.setNextID(getNextID(fhDBYear.getList()));
			
			//initialize reference to client manager
			clientMgr = ClientManager.getInstance();
			
			//reference to global variables
			globalDB = ServerGlobalVariableDB.getInstance();
			
			//create a time stamp comparator
			familyHistoryTimestampComparator = new FamilyHistoryTimestampComparator();
		}
	}
	
	public static ServerFamilyHistoryDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerFamilyHistoryDB();
		
		return instance;
	}
/*	
	//Search the database for the family. Return a json if the family is found. 
	String getFamilyHistory(int year)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<FamilyHistory>>(){}.getType();
			
		String response = gson.toJson(famHistDB.get(DBManager.offset(year)).getList(), listtype);
		return response;	
	}
*/	
	String getFamilyHistories(int year)
	{
		Gson gson = new Gson();
		Type mapOfFamilyHistories = new TypeToken<HashMap<Integer,List<FamilyHistory>>>(){}.getType();
		return gson.toJson(famHistDB.get(DBManager.offset(year)).getMap(), mapOfFamilyHistories);	
	}
/*	
	FamilyHistory getLastFamilyHistory(int year, int famID)
	{
		FamilyHistory latestFamilyHistoryObj = null;
		
		FamilyHistoryDBYear histDBYear = famHistDB.get(DBManager.offset(year));
		for(FamilyHistory fhObj : histDBYear.getList())
		{
			if(fhObj.getFamID() == famID)
			{
				if(latestFamilyHistoryObj == null ||
				    fhObj.getTimestamp() > latestFamilyHistoryObj.getTimestamp())
				{
					latestFamilyHistoryObj = fhObj;
				}
			}
		}
		
		return latestFamilyHistoryObj;
	}
*/
	
	static FamilyHistory getCurrentFamilyHistory(int year, int famID)
	{
		List<FamilyHistory> familyHistoryList = famHistDB.get(DBManager.offset(year)).getFamilyHistoryList(famID);
		if(familyHistoryList != null && !familyHistoryList.isEmpty())
		{
			if(familyHistoryList.size() == 1)
				return familyHistoryList.get(0);
			else
			{
				Collections.sort(familyHistoryList, familyHistoryTimestampComparator);
				return familyHistoryList.get(familyHistoryList.size()-1);
			}
		}
		else
			return null;
	}
	
	FamilyHistory getLastFamilyHistory(int year, int famID)
	{
		List<FamilyHistory> familyHistoryList = famHistDB.get(DBManager.offset(year)).getFamilyHistoryList(famID);
		if(familyHistoryList != null && !familyHistoryList.isEmpty())
		{
			if(familyHistoryList.size() == 1)
				return familyHistoryList.get(0);
			else
			{
				Collections.sort(familyHistoryList, familyHistoryTimestampComparator);
				return familyHistoryList.get(familyHistoryList.size()-1);
			}
		}
		else
			return null;
	}
	
	@Override 
	String add(int year, String json, ONCUser client)
	{
		//Create a history object for the new delivery
		Gson gson = new Gson();
		FamilyHistory addedHistoryObj = gson.fromJson(json, FamilyHistory.class);
		
		return add(year, addedHistoryObj, client);
	}

	String add(int year, FamilyHistory addHistoryReq, ONCUser client)
	{
		
		FamilyHistoryDBYear histDBYear = famHistDB.get(DBManager.offset(year));
		FamilyHistory priorFHObj = getLastFamilyHistory(year, addHistoryReq.getFamID());
		
		//add the new object to the data base
		addHistoryReq.setID(histDBYear.getNextID());
		addHistoryReq.setDateChanged(System.currentTimeMillis());
		addHistoryReq.setChangedBy(client.getLNFI());
		
		if(priorFHObj != null)
		{
			//If requested delivered by field is null or the requested family gift status is Delivered,
			//Attempted or Counselor Pickup, retain the assignee
			if(addHistoryReq.getdDelBy() == null || addHistoryReq.getGiftStatus() == FamilyGiftStatus.Delivered || 
				addHistoryReq.getGiftStatus() == FamilyGiftStatus.Attempted ||
				 addHistoryReq.getGiftStatus() == FamilyGiftStatus.CounselorPickUp)
			{
				addHistoryReq.setDeliveredBy(priorFHObj.getdDelBy());
			}
			
			//notify the corresponding family that delivery has changed and
			//check to see if new delivery assigned or removed a delivery from a driver
			ServerVolunteerDB volunteerDB = null;
			try 
			{
				volunteerDB = ServerVolunteerDB.getInstance();
				
				//If prior status == ASSIGNED && new status < ASSIGNED, decrement the prior delivery driver
				//else if the prior status == ASSIGNED and new status == ASSIGNED and the driver number changed,
				//Decrement the prior driver deliveries and increment the new driver deliveries. Else, if the 
				//prior status < ASSIGNED and the new status == ASSIGNED, increment the new driver deliveries
				if(priorFHObj.getGiftStatus().compareTo(FamilyGiftStatus.Assigned) < 0 && 
						addHistoryReq.getGiftStatus() == FamilyGiftStatus.Assigned)
				{
					volunteerDB.updateVolunteerDriverDeliveryCounts(year, null, addHistoryReq.getdDelBy());
				}
				else if(priorFHObj.getGiftStatus().compareTo(FamilyGiftStatus.Assigned) >= 0 && 
						 addHistoryReq.getGiftStatus() == FamilyGiftStatus.Assigned && 
						  !priorFHObj.getdDelBy().equals(addHistoryReq.getdDelBy()))
				{
					volunteerDB.updateVolunteerDriverDeliveryCounts(year, priorFHObj.getdDelBy(), addHistoryReq.getdDelBy());
				}
				else if(priorFHObj.getGiftStatus() == FamilyGiftStatus.Assigned && 
						 addHistoryReq.getGiftStatus().compareTo(FamilyGiftStatus.Assigned) < 0)
				{
					volunteerDB.updateVolunteerDriverDeliveryCounts(year, priorFHObj.getdDelBy(), null);
				}
			}
			catch (FileNotFoundException e) 
			{
				e.printStackTrace();
			}
			catch (IOException e) 
			{
				e.printStackTrace();
			}
		}
		
		//add the item to the proper year's list and mark the list as changed
		histDBYear.add(addHistoryReq);
		histDBYear.setChanged(true);
		
		Gson gson = new Gson();
		return "ADDED_DELIVERY" + gson.toJson(addHistoryReq, FamilyHistory.class);
	}
	
	String addFamilyHistoryList(int year, String historyGroupJson, ONCUser client)
	{
		ClientManager clientMgr = ClientManager.getInstance();
		
		//un-bundle to list of ONCFamilyHistory objects
		Gson gson = new Gson();
		Type listOfHistoryObjects = new TypeToken<ArrayList<FamilyHistory>>(){}.getType();
		List<FamilyHistory> famHistoryList = gson.fromJson(historyGroupJson, listOfHistoryObjects);
		
		//create a list of response strings to send to clients
		List<String> responseList = new ArrayList<String>();
		
		//for each history object in the list, add it to the history database 		
		for(FamilyHistory addFamHistReq : famHistoryList)
			responseList.add(add(year, addFamHistReq, client));
		
		if(!responseList.isEmpty())
		{
			clientMgr.notifyAllInYearClients(year, responseList);
			return "ADDED_FAMILY_HISTORY_GROUP";
		}
		else
			return "ADD_FAMILY_HISTORY_GROUP_FAILED";
	}
	
/*	
	//used when adding processing automated call results
	String add(int year, ONCFamilyHistory addedHisoryObj)
	{
		//add the new object to the data base
		FamilyHistoryDBYear histDBYear = famHistDB.get(year - BASE_YEAR);
		addedHisoryObj.setID(histDBYear.getNextID());
		histDBYear.add(addedHisoryObj);
		histDBYear.setChanged(true);
		
		//notify the corresponding family that history object has changed and
		//check to see if the object was a new delivery assigned or removed a delivery from a driver
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
		ONCFamily fam = serverFamilyDB.getFamily(year, addedHisoryObj.getFamID());
		ONCFamilyHistory priorDelivery = getHistoryObject(year, fam.getDeliveryID());
		
		//if there was a prior delivery, then update the status and counts
		if(priorDelivery != null)
		{
			//If prior status == ASSIGNED && new status < ASSIGNED, decrement the prior delivery driver
			//else if the prior status == ASSIGNED and new status == ASSIGNED and the driver number changed,
			//Decrement the prior driver deliveries and increment the new driver deliveries. Else, if the 
			//prior status < ASSIGNED and the new status == ASSIGNED, increment the new driver deliveries
			if(priorDelivery.getGiftStatus().compareTo(FamilyGiftStatus.Assigned) < 0 && 
					addedHisoryObj.getGiftStatus() == FamilyGiftStatus.Assigned)
			{
				driverDB.updateVolunteerDriverDeliveryCounts(year, null, addedHisoryObj.getdDelBy());
			}
			else if(priorDelivery.getGiftStatus().compareTo(FamilyGiftStatus.Assigned) >= 0 && 
					 addedHisoryObj.getGiftStatus() == FamilyGiftStatus.Assigned && 
					  !priorDelivery.getdDelBy().equals(addedHisoryObj.getdDelBy()))
			{
				driverDB.updateVolunteerDriverDeliveryCounts(year, priorDelivery.getdDelBy(), addedHisoryObj.getdDelBy());
			}
			else if(priorDelivery.getGiftStatus() == FamilyGiftStatus.Assigned && 
					 addedHisoryObj.getGiftStatus().compareTo(FamilyGiftStatus.Assigned) < 0)
			{
				driverDB.updateVolunteerDriverDeliveryCounts(year, priorDelivery.getdDelBy(), null);
			}
		}
		
		//Update the family object with new family history object reference
		if(serverFamilyDB != null)
			serverFamilyDB.updateFamilyHistory(year, addedHisoryObj);

		Gson gson = new Gson();
		return "ADDED_DELIVERY" + gson.toJson(addedHisoryObj, ONCFamilyHistory.class);
	}
*/	
/*	
	String addFamilyHistoryList(int year, String historyGroupJson)
	{
		ClientManager clientMgr = ClientManager.getInstance();
		
		//un-bundle to list of ONCFamilyHistory objects
		Gson gson = new Gson();
		Type listOfHistoryObjects = new TypeToken<ArrayList<ONCFamilyHistory>>(){}.getType();
		
		List<ONCFamilyHistory> famHistoryList = gson.fromJson(historyGroupJson, listOfHistoryObjects);
		
		//for each history object in the list, add it to the database and notify all clients that
		//it was added
		for(ONCFamilyHistory addedHistoryObj:famHistoryList)
		{
			String response = add(year, addedHistoryObj);
			//if add was successful, need to q the change to all in-year clients
			//notify in year clients of change
	    	clientMgr.notifyAllInYearClients(year, response);
		}
		
		return "ADDED_GROUP_DELIVERIES";
	}
*/	
	FamilyHistory addFamilyHistoryObject(int year, FamilyHistory addedFamHistObj, ONCUser user, boolean bNotify)
	{
		ClientManager clientMgr = ClientManager.getInstance();
		
		//add the new object to the data base
		FamilyHistoryDBYear histDBYear = famHistDB.get(DBManager.offset(year));
		
		addedFamHistObj.setID(histDBYear.getNextID());
		addedFamHistObj.setDateChanged(System.currentTimeMillis());
		addedFamHistObj.setChangedBy(user.getLNFI());
		
		if(addedFamHistObj.getdDelBy() == null || 
			addedFamHistObj.getGiftStatus() == FamilyGiftStatus.Delivered || 
			 addedFamHistObj.getGiftStatus() == FamilyGiftStatus.Attempted ||
			  addedFamHistObj.getGiftStatus() == FamilyGiftStatus.CounselorPickUp)
		{
			//find the last object, there has to be one for the status > Assigned
			FamilyHistory latestFHObj = getLastFamilyHistory(year, addedFamHistObj.getFamID());
			if(latestFHObj != null)
				addedFamHistObj.setDeliveredBy(latestFHObj.getdDelBy());
		}
		
		histDBYear.add(addedFamHistObj);
		histDBYear.setChanged(true);
		
		if(bNotify)
		{	
			Gson gson = new Gson();
			clientMgr.notifyAllInYearClients(year, "ADDED_DELIVERY"+ gson.toJson(addedFamHistObj, FamilyHistory.class));
		}
		
		return addedFamHistObj;
	}
	
	FamilyHistory addFamilyHistoryObject(int year, FamilyHistory addedFamHistObj)
	{
		//add the new object to the data base
		FamilyHistoryDBYear histDBYear = famHistDB.get(DBManager.offset(year));
		
		addedFamHistObj.setID(histDBYear.getNextID());
		
		histDBYear.add(addedFamHistObj);
		histDBYear.setChanged(true);
		
		
		return addedFamHistObj;
	}
	
	FamilyHistory getHistoryObject(int year, int histID)
	{
		List<FamilyHistory> histAL = famHistDB.get(DBManager.offset(year)).getList();
		int index = 0;	
		while(index < histAL.size() && histAL.get(index).getID() != histID)
			index++;
		
		if(index < histAL.size())
			return histAL.get(index);
		else
			return null;
	}
	
	String update(int year, String json)
	{
		//Create an object for the updated family history
		Gson gson = new Gson();
		FamilyHistory updatedHistory = gson.fromJson(json, FamilyHistory.class);
		
		//Find the position for the current object being replaced
		FamilyHistoryDBYear histDBYear = famHistDB.get(DBManager.offset(year));
		List<FamilyHistory> histAL = histDBYear.getList();
		int index = 0;
		while(index < histAL.size() && histAL.get(index).getID() != updatedHistory.getID())
			index++;
		
		//Replace the current object with the update
		if(index < histAL.size())
		{
			histAL.set(index, updatedHistory);
			histDBYear.setChanged(true);
			return "UPDATED_DELIVERY" + gson.toJson(updatedHistory, FamilyHistory.class);
		}
		else
			return "UPDATE_FAILED";
	}
	
	List<String> updateListOfFamilies(Map<String, Object> params, ONCServerUser client)
	{
		//process the request
		int year = Integer.parseInt((String) params.get("year"));
		
		//get the list of family id's the web client requested to update
		List<Integer> famIDList = new ArrayList<Integer>();
		int index = 0;
		while(params.containsKey("famid" + Integer.toString(index)))
		{
			String zID = (String) params.get("famid" + Integer.toString(index++));
			famIDList.add(Integer.parseInt(zID));
		}
		
		Gson gson = new Gson();
		List<String> responseJsonList = new ArrayList<String>();
		
		//retrieve the family data base for the year
		FamilyHistoryDBYear famHistDBYear = famHistDB.get(DBManager.offset(year));
			
		for(Integer famID : famIDList)
		{
			FamilyHistory currHistoryObj = getLastFamilyHistory(year, famID);
			FamilyHistory addedHistoryObj = new FamilyHistory(currHistoryObj);
			
			addedHistoryObj.setDateChanged(System.currentTimeMillis());
			addedHistoryObj.setChangedBy(client.getLNFI());
			
			//try to set the new DNS Code if it was requested by the web client
			if(params.containsKey("dnschangeselect"))
			{
				String zDNSCode = (String) params.get("dnschangeselect");
				if(isNumeric(zDNSCode))
				{
					int dnsCode = Integer.parseInt(zDNSCode);
					if(dnsCode > -2)
					{	
						addedHistoryObj.setDNSCode(dnsCode);
						addedHistoryObj.setdNotes("DNS Code Change");
					}
				}
			}
			
			//set the new family status if the fam status key was sent from web page
			if(params.containsKey("famstatuschangeselect"))
			{
				String updatedFamStatus = (String) params.get("famstatuschangeselect");
				if(!updatedFamStatus.contentEquals("No_Change"))
				{
					addedHistoryObj.setFamilyStatus(FamilyStatus.valueOf(updatedFamStatus));
					addedHistoryObj.setdNotes("Status Change");
				}
			}
			
			//set the new family gift status if the fam gift status key was sent from web page
			if(params.containsKey("giftstatuschangeselect"))
			{
				String updatedFamGiftStatus = (String) params.get("giftstatuschangeselect");
				if(!updatedFamGiftStatus.contentEquals("No_Change"))
				{
					addedHistoryObj.setFamilyGiftStatus(FamilyGiftStatus.valueOf(updatedFamGiftStatus));
					addedHistoryObj.setdNotes("Status Change");
				}
			}
				
			famHistDBYear.add(addedHistoryObj);
			famHistDBYear.setChanged(true);

			responseJsonList.add("ADDED_DELIVERY" + gson.toJson(addedHistoryObj, FamilyHistory.class));
		}
		
		return responseJsonList;
	}
	
	//Search the database for the family history. Return a json of List<ONCDelivery>
	String getFamilyHistory(int year, String reqjson)
	{
		//Convert list to json and return it
		Gson gson = new Gson();
		HistoryRequest histReq = gson.fromJson(reqjson, HistoryRequest.class);
			
		List<FamilyHistory> famHistory = new ArrayList<FamilyHistory>();
		List<FamilyHistory> famHistAL = famHistDB.get(DBManager.offset(year)).getList();
			
		//Search for deliveries that match the delivery Family ID
		for(FamilyHistory fh:famHistAL)
			if(fh.getFamID() == histReq.getID())
				famHistory.add(fh);
			
		//Convert list to json and return it
		Type listtype = new TypeToken<ArrayList<FamilyHistory>>(){}.getType();
			
		String response = gson.toJson(famHistory, listtype);
		return response;
	}
	
	void checkFamilyGiftStatusOnGiftAdded(int year, ONCChildGift priorGift, ONCChildGift addedGift, int famID, ONCUser client)
	{
		FamilyHistory fh = getLastFamilyHistory(year,  famID);
		
	    //determine the proper family gift status for the family after adding the wish. If the
		//family gifts have already been packaged, then don't perform the test
	    FamilyGiftStatus newGiftStatus;
	    if(fh.getGiftStatus().compareTo(FamilyGiftStatus.Packaged) < 0)
	    	newGiftStatus = getLowestGiftStatus(year, famID);
	    else
	    	newGiftStatus = fh.getGiftStatus();
	    
	    
    	if(newGiftStatus != fh.getGiftStatus())
    	{
    		FamilyHistoryDBYear histDBYear = famHistDB.get(DBManager.offset(year));
    		
    		//add a new family history with gift status change
    		FamilyHistory addedHistoryObj = new FamilyHistory(fh);
    		addedHistoryObj.setID(histDBYear.getNextID());
    		addedHistoryObj.setFamilyGiftStatus(newGiftStatus);
    		if(client != null)
    			addedHistoryObj.setChangedBy(client.getLNFI());
    		addedHistoryObj.setDateChanged(System.currentTimeMillis());
    		
    		histDBYear.add(addedHistoryObj);
    		histDBYear.setChanged(true);
    	
    		Gson gson = new Gson();
    		String change = "ADDED_DELIVERY" + gson.toJson(addedHistoryObj, FamilyHistory.class);
    		clientMgr.notifyAllInYearClients(year, change);	//null to notify all clients
	    }
	}
	
	/*************************************************************************************************************
	* This method is called when a child's wish status changes due to a user change. The method
	* implements a set of rules that returns the family status when all children in the family
	* wishes/gifts attain a certain status. For example, when all children's gifts are selected,
	* the returned family status is FAMILY_STATUS_GIFTS_SELECTED. A matrix that correlates child
	* gift status to family status is used. There are 7 possible setting for child wish status.
	* The seven correspond to five family status choices. The method finds the lowest family
	* status setting based on the children's wish status and returns it. 
	**********************************************************************************************************/
	FamilyGiftStatus getLowestGiftStatus(int year, int famid)
	{
		//This matrix correlates a child wish status to the family status.
		FamilyGiftStatus[] wishstatusmatrix = {FamilyGiftStatus.Requested,	//WishStatus Index = 0;
								FamilyGiftStatus.Requested,	//WishStatus Index = 1;
								FamilyGiftStatus.Selected,	//WishStatus Index = 2;
								FamilyGiftStatus.Selected,	//WishStatus Index = 3;
								FamilyGiftStatus.Selected,	//WishStatus Index = 4;
								FamilyGiftStatus.Selected,	//WishStatus Index = 5;
								FamilyGiftStatus.Selected,	//WishStatus Index = 6;
								FamilyGiftStatus.Received,	//WishStatus Index = 7;
								FamilyGiftStatus.Received,	//WishStatus Index = 8;
								FamilyGiftStatus.Selected,	//WishStatus Index = 9;
								FamilyGiftStatus.Verified};	//WishStatus Index = 10;
			
		//Check for all gifts selected
		FamilyGiftStatus lowestfamstatus = FamilyGiftStatus.Verified;
		
		ServerChildGiftDB childGiftDB = null;
		ServerChildDB childDB = null; 
		try 
		{
			childGiftDB = ServerChildGiftDB.getInstance();
			childDB = ServerChildDB.getInstance();
			int numGiftsPerChild = globalDB.getServerGlobalVariables(year).getNumberOfGiftsPerChild();
			
			for(ONCChild c : childDB.getChildList(year, famid))
			{
//				for(int wn=0; wn< NUMBER_OF_WISHES_PER_CHILD; wn++)
				for(int wn=0; wn < numGiftsPerChild; wn++)	
				{
					ONCChildGift cw = childGiftDB.getCurrentChildGift(year, c.getID(), wn);
					
					//if cw is null, it means that the wish doesn't exist yet. If that's the case, 
					//set the status to the lowest status possible as if the wish existed
					GiftStatus childwishstatus = GiftStatus.Not_Selected;	//Lowest possible child wish status
					if(cw != null)
						childwishstatus = cw.getGiftStatus();
						
					if(wishstatusmatrix[childwishstatus.statusIndex()].compareTo(lowestfamstatus) < 0)
						lowestfamstatus = wishstatusmatrix[childwishstatus.statusIndex()];
				}
			}
				
			return lowestfamstatus;
		} 
		catch (FileNotFoundException e) 
		{
			return FamilyGiftStatus.Requested;
		} 
		catch (IOException e) 
		{
			return FamilyGiftStatus.Requested;
		}
	}
	
	void checkFamilyStatusOnSmsReceived(int year, ONCFamily fam, boolean bDeliveryTimeframe, boolean bConfirmingBody,
			boolean bDecliningBody)
	{
		if(fam != null);
		{
			FamilyHistory lastHistoryObj = getLastFamilyHistory(year, fam.getID());
			FamilyHistory updatedHistoryObj = new FamilyHistory(lastHistoryObj);
			
			if(!(bDeliveryTimeframe && lastHistoryObj.getFamilyStatus() == FamilyStatus.Confirmed))
			{
				//it's not around delivery day or the family wasn't already confirmed, 
				//so we potentially want to change family status.
				if(bConfirmingBody && (lastHistoryObj.getFamilyStatus() == FamilyStatus.Contacted ||
							lastHistoryObj.getFamilyStatus() == FamilyStatus.Verified ||
							 lastHistoryObj.getFamilyStatus() == FamilyStatus.Waitlist))
				{
					updatedHistoryObj.setFamilyStatus(FamilyStatus.Confirmed);
					FamilyHistoryDBYear fhDBYear = famHistDB.get(DBManager.offset(year));
					fhDBYear.add(updatedHistoryObj);
					fhDBYear.setChanged(true);
					
					//notify all in-year clients of the status change
					Gson gson = new Gson();
					String change = "UPDATED_DELIVERY" + gson.toJson(updatedHistoryObj, FamilyHistory.class);
					clientMgr.notifyAllInYearClients(year, change);	//null to notify all clients
				}
				else if(bDecliningBody && (lastHistoryObj.getFamilyStatus() == FamilyStatus.Confirmed ||
											lastHistoryObj.getFamilyStatus() == FamilyStatus.Verified ||
											 lastHistoryObj.getFamilyStatus() == FamilyStatus.Waitlist))
				{
					updatedHistoryObj.setFamilyStatus(FamilyStatus.Contacted);
					FamilyHistoryDBYear fhDBYear = famHistDB.get(DBManager.offset(year));
					fhDBYear.add(updatedHistoryObj);
					fhDBYear.setChanged(true);
					
					//notify all in-year clients of the status change
					Gson gson = new Gson();
					String change = "UPDATED_DELIVERY" + gson.toJson(fam, FamilyHistory.class);
					clientMgr.notifyAllInYearClients(year, change);	//null to notify all clients
				}
			}
		}
	}
	
	void checkFamilyStatusOnSMSStatusCallback(int year, ONCSMS receivedSMS)
	{
		FamilyHistory fam = getLastFamilyHistory(year, receivedSMS.getEntityID());
		
		//we only consider changing family status to Contacted based on outgoing message status if the family 
		//has not yet confirmed delivery
		if(fam != null && fam.getFamilyStatus() != FamilyStatus.Confirmed)
		{	
			if((receivedSMS.getStatus() == SMSStatus.SENT ||  receivedSMS.getStatus() == SMSStatus.DELIVERED) && 
				(fam.getFamilyStatus() == FamilyStatus.Waitlist || fam.getFamilyStatus() == FamilyStatus.Verified))
			{
				//sometimes we don't get a DELIVERD status from the super carrier, so we'll accept a SENT status.
				fam.setFamilyStatus(FamilyStatus.Contacted);
				famHistDB.get(DBManager.offset(year)).setChanged(true);
			
				//notify all in-year clients of the status change
				Gson gson = new Gson();
    				String change = "UPDATED_FAMILY" + gson.toJson(fam, ONCFamily.class);
    				clientMgr.notifyAllInYearClients(year, change);	//null to notify all clients
			}
			else if((receivedSMS.getStatus() == SMSStatus.UNDELIVERED || receivedSMS.getStatus() == SMSStatus.FAILED) && 
					fam.getFamilyStatus() == FamilyStatus.Contacted )
			{
				//message status updated to UNDELIVERED. If we already changed the family status to Contacted, change
				//it back to either Waitlist or Verified.
				FamilyStatus newStatus = fam.getDNSCode() == DNSCode.WAITLIST ? FamilyStatus.Waitlist : FamilyStatus.Verified;
				fam.setFamilyStatus(newStatus);
				famHistDB.get(DBManager.offset(year)).setChanged(true);
			
				//notify all in-year clients of the status change
				Gson gson = new Gson();
    				String change = "UPDATED_FAMILY" + gson.toJson(fam, ONCFamily.class);
    				clientMgr.notifyAllInYearClients(year, change);	//null to notify all clients
			}
		}
	}
	
	HtmlResponse confirmFamilyGiftDelivery(int year, int famid, WebClient wc, String callbackFunction)
	{
		String response;
		FamilyHistory lastHistoryObj =  getLastFamilyHistory(year, famid);

		if(lastHistoryObj != null)
		{
			if(lastHistoryObj.getGiftStatus() == FamilyGiftStatus.Assigned)
			{
				
				//add a history item
				FamilyHistory reqFamHistObj = new FamilyHistory(lastHistoryObj);
				reqFamHistObj.setFamilyGiftStatus(FamilyGiftStatus.Delivered);
				reqFamHistObj.setdNotes("Gift Status Change");
				
				addFamilyHistoryObject(year, reqFamHistObj, wc.getWebUser(), true);

				response = "{\"message\":\"Gifts Successfully Delivered!\"}"; 
			}
			else if(lastHistoryObj.getGiftStatus() == FamilyGiftStatus.Delivered)
				response = "{\"message\":\"Gifts Already Delivered\"}"; 
			else
				response = String.format("{\"message\":\"Error: Invalid Family Gift Status: %s\"}",
						lastHistoryObj.getGiftStatus().toString());
		}
		else
		{
			response = "{\"message\":\"Error: Family Not Found\"}";
		}
		
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);
	}
	
	@Override
	void addObject(int year, String[] nextLine)
	{
		FamilyHistoryDBYear histDBYear = famHistDB.get(DBManager.offset(year));
		histDBYear.add(new FamilyHistory(nextLine));	
	}
	
	@Override
	void createNewSeason(int newYear)
	{
		//create a new family history data base year for the year provided in the newYear parameter
		//The history db year list is initially empty prior to the import of families, so all we
		//do here is create a new FamilyHistoryDBYear for the newYear and save it.
		FamilyHistoryDBYear famHistDBYear = new FamilyHistoryDBYear(newYear);
		famHistDB.add(famHistDBYear);
		famHistDBYear.setChanged(true);	//mark this db for persistent saving on the next save event
	}

	@Override
	void save(int year)
	{
		String[] header = {"History ID", "Family ID", "Family Status", "Gift Status", "Del By", 
	 			"Notes", "Changed By", "Time Stamp", "DNS Code"};
		
		FamilyHistoryDBYear histDBYear = famHistDB.get(DBManager.offset(year));
		if(histDBYear.isUnsaved())
		{
//			System.out.println(String.format("DeliveryDB save() - Saving Delivery DB"));
			String path = String.format("%s/%dDB/FamilyHistoryDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(histDBYear.getList(),  header, path);
			histDBYear.setChanged(false);
		}
	}
/*	
	void updateDNSCodes()
	{
		try
		{
			ServerFamilyDB familyDB = ServerFamilyDB.getInstance();
			int[] years = {2012,2013,2014,2015, 2016,2017,2018};
			for(int year : years)
			{
				List<FamilyHistory> updatedFHList = new ArrayList<FamilyHistory>();
				
				for(FamilyHistory fh: famHistDB.get(DBManager.offset(year)).getList())
				{	
					//find the family in the family list
					ONCFamily fam = familyDB.getFamily(year, fh.getFamID());
					if(fam != null)
						fh.setDNSCode(fam.getDNSCode());
					else
						fh.setDNSCode(-1);
					
					updatedFHList.add(fh);
				}
					
				if(!updatedFHList.isEmpty())
				{
					//save it to a new file
					String[] header = {"History ID", "Family ID", "Family Status", "Gift Status", "Del By", 
				 			"Notes", "Changed By", "Time Stamp", "DNS Code"};
					
					String path = String.format("%s/%dDB/UpdatedFamilyHistoryDB.csv", System.getProperty("user.dir"), year);
					exportDBToCSV(updatedFHList, header, path);		
						
				}	
			}
		}
		catch (FileNotFoundException e) 
		{
			
		}
		catch (IOException e) 
		{
			
		}
	}
*/	
	private class FamilyHistoryDBYear extends ServerDBYear
    {
//    	private List<ONCMeal> mealList;
    	private Map<Integer, List<FamilyHistory>> familyHistoryMap;
    	
    	FamilyHistoryDBYear(int year)
    	{
    		super();
//    		mealList = new ArrayList<ONCMeal>();
    		familyHistoryMap = new HashMap<Integer, List<FamilyHistory>>();
    	}
    	
    	//getters
    	List<FamilyHistory> getList()
    	{ 
    		List<FamilyHistory> allFamilyHistoryList = new ArrayList<FamilyHistory>();
    		for(Map.Entry<Integer,List<FamilyHistory>> entry : familyHistoryMap.entrySet())
    			for(FamilyHistory fh : entry.getValue())
    				allFamilyHistoryList.add(fh);
    		
    		return allFamilyHistoryList;
    	}
    	
    	List<FamilyHistory> getFamilyHistoryList(int famID) { return familyHistoryMap.get(famID); }
    	Map<Integer, List<FamilyHistory>> getMap() { return familyHistoryMap; }
    	
    	void add(FamilyHistory addedFamilyHistory)
    	{
    		
    		//check to see if famID is a key in the map, if not, add a new linked list
    		if(familyHistoryMap.containsKey(addedFamilyHistory.getFamID()))
    		{
    			familyHistoryMap.get(addedFamilyHistory.getFamID()).add(addedFamilyHistory);
    		}
    		else
    		{
    			List<FamilyHistory> famMealList = new ArrayList<FamilyHistory>();
    			famMealList.add(addedFamilyHistory);
    			familyHistoryMap.put(addedFamilyHistory.getFamID(), famMealList);
    		}
    	}
    }
	
	private static class FamilyHistoryTimestampComparator implements Comparator<FamilyHistory>
	{
		@Override
		public int compare(FamilyHistory o1, FamilyHistory o2)
		{
			if(o2.getTimestamp() > o1.getTimestamp())
				return -1;
			else if(o2.getTimestamp() == o1.getTimestamp())
				return 0;
			else
				return 1;
		}
	}
/*	
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
    	
    	int getNewFamStatus() { return famStatus; }
    	int getNewGiftStatus() { return giftStatus; }
    }
    
    public class FamilyHistory
    {
    	//This class implements the data structure for Family History objects. When an ONC Family objects 
    	//FamilyStatus or FamilyGift Status changes, this object is created and stored to archive the change
    	
    	int histID;
    	int famID;
    	int famStatus;
    	int giftStatus;
    	String dDelBy;
    	String dNotes;
    	String dChangedBy;
    	long dDateChanged;

    	//Constructor used after separating ONC Deliveries from ONC Families
    	FamilyHistory(int id, int famid, int fStat, int dStat, String dBy, String notes, String cb, long dateChanged)
    	{
    		histID = id;
    		famID = famid;
    		famStatus = fStat;	
    		giftStatus = dStat;			
    		dDelBy = dBy;
    		dNotes = notes;		
    		dChangedBy = cb;
    		dDateChanged = dateChanged;
    	}
    	
    	public String[] getExportRow()
    	{
    		String[] exportRow = {Integer.toString(histID), Integer.toString(famID), 
    							  Integer.toString(famStatus), Integer.toString(giftStatus),
    							  dDelBy, dNotes, dChangedBy, Long.toString(dDateChanged)};
    		
    		return exportRow;
    	}
    }
*/        
}
