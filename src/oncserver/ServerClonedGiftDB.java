package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ourneighborschild.ONCUser;
import ourneighborschild.ClonedGift;
import ourneighborschild.ClonedGiftStatus;
import ourneighborschild.HistoryRequest;

public class ServerClonedGiftDB extends ServerSeasonalDB
{
	private static final int CLONED_GIFT_DB_HEADER_LENGTH = 12;
	private static ServerClonedGiftDB instance = null;

	private static List<ClonedGiftDBYear> clonedGiftDB;
	private ServerPartnerDB serverPartnerDB;
	
	private ServerClonedGiftDB() throws FileNotFoundException, IOException
	{
		//create the cloned gift data base
		clonedGiftDB = new ArrayList<ClonedGiftDBYear>();
						
		//populate the cloned gift data base for the last TOTAL_YEARS from persistent store
		for(int year= DBManager.getBaseSeason(); year < DBManager.getBaseSeason() + DBManager.getNumberOfYears(); year++)
		{
			//create the cloned gift list for each year
			ClonedGiftDBYear cwDBYear = new ClonedGiftDBYear(year);
							
			//add the list of cloned gifts for the year to the db
			clonedGiftDB.add(cwDBYear);
							
			//import the cloned gifts from persistent store
			importDB(year, String.format("%s/%dDB/ClonedGiftDB.csv",
					System.getProperty("user.dir"),
						year), "Cloned Gift DB", CLONED_GIFT_DB_HEADER_LENGTH);
			
			//set the next id
			cwDBYear.setNextID(getNextID(cwDBYear.getList()));
		}

		serverPartnerDB = ServerPartnerDB.getInstance();
	}
	
	public static ServerClonedGiftDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerClonedGiftDB();
		
		return instance;
	}
	
	List<ClonedGift> getClonedGiftList(int year)
	{
		return clonedGiftDB.get(DBManager.offset(year)).getList();
	}
	
	String getCurrentCloneGiftList(int year)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ClonedGift>>(){}.getType();
		   
		List<ClonedGift> currClonedGiftList = new ArrayList<ClonedGift>();
		for(ClonedGift cg : clonedGiftDB.get(DBManager.offset(year)).getList())
			if(cg.getNextID() == -1)	//cloned gift is last in linked list, there for is current
				currClonedGiftList.add(cg);
		
//		System.out.println(currClonedGiftList.size());
//		
//		String json = gson.toJson(currClonedGiftList, listtype);
//		System.out.println(json.length());
		
		return gson.toJson(currClonedGiftList, listtype);
	}
	
	String getGiftHistory(int year, String reqjson)
	{
		//Convert list to json and return it
		Gson gson = new Gson();
		HistoryRequest ghRequest = gson.fromJson(reqjson, HistoryRequest.class);
		
		
		int childID = ghRequest.getID();
		int gn = ghRequest.getNumber();
		
		List<ClonedGift> giftHistoryList = new ArrayList<ClonedGift>();
		List<ClonedGift> searchList = clonedGiftDB.get(DBManager.offset(year)).getList();
		
		//find the first cloned gift in the linked list
		int index = 0;
		while(index < searchList.size() && !(searchList.get(index).getChildID() == childID &&
				searchList.get(index).getGiftNumber() == gn && searchList.get(index).getPriorID() == -1))
		{
			index++;
		}	
		
		if(index < searchList.size())
		{
			ClonedGift nextClonedGiftInChain = searchList.get(index);	//first clone in chain
			
			giftHistoryList.add(nextClonedGiftInChain);
			
			//find the next cloned gift in the linked list until the end of the chain
			while(nextClonedGiftInChain != null && nextClonedGiftInChain.getNextID() != -1)
			{
				nextClonedGiftInChain = getClonedGift(year, nextClonedGiftInChain.getNextID());
				
				if(nextClonedGiftInChain != null)
				{
					giftHistoryList.add(nextClonedGiftInChain);
				}
			}	
		}
		
		//Convert gift history list to json and return it
		Type listtype = new TypeToken<ArrayList<ClonedGift>>(){}.getType();
		String response = gson.toJson(giftHistoryList, listtype);
		return response;
	}

	@Override
	String add(int year, String wishjson, ONCUser client)
	{
		return "";
	}
	
	String addListOfGifts(int year, String giftListJson, ONCUser client)
	{
		//Create a cloned gift list for the add cloned gift requests
		Gson gson = new Gson();
		Type listOfClonedGifts = new TypeToken<ArrayList<ClonedGift>>(){}.getType();		
		List<ClonedGift> reqAddClonedGiftList = gson.fromJson(giftListJson, listOfClonedGifts);
		List<String> responseJsonList = new ArrayList<String>();

		//retrieve the cloned gift data base for the year
		ClonedGiftDBYear cgDBYear = clonedGiftDB.get(DBManager.offset(year));
		
		//for each add clone gift request, check to see if the gift had already been cloned. If it 
		//hasn't already been cloned, than add it. A new clone gift request has a requested cloned gift
		//id of -1. 
		for(ClonedGift reqAddClonedGift : reqAddClonedGiftList)
		{
//			System.out.println(String.format("ServClonedGiftDB.addListOfGifts: isDuplicate=%b, childID=%d, gn=%d",
//					isDuplicateClone(year, reqAddClonedGift), reqAddClonedGift.getChildID(), reqAddClonedGift.getGiftNumber()));
			
			if(reqAddClonedGift.getID() > -1 || reqAddClonedGift.getID() == -1 && !isDuplicateClone(year, reqAddClonedGift))
			{
				ClonedGift replacedClonedGift = null;
				if(reqAddClonedGift.getID() > -1)	//replacing a previous cloned gift
				{	
					replacedClonedGift = getClonedGift(year, reqAddClonedGift.getID());
					
					if(replacedClonedGift != null)
						reqAddClonedGift.setPriorID(replacedClonedGift.getID());
				}
						
				//set the new ID for the added cloned gift
				reqAddClonedGift.setID(cgDBYear.getNextID());
				
				//set the replaced cloned gift next ID in the chain and determine if other
				//data bases require update.
				if(replacedClonedGift != null)
				{	
					replacedClonedGift.setNextID(reqAddClonedGift.getID());
					responseJsonList.add("UPDATED_CLONED_GIFT" + gson.toJson(replacedClonedGift, ClonedGift.class));
					processClonedGiftAdded(year, replacedClonedGift, reqAddClonedGift);	
				}
			
				cgDBYear.add(reqAddClonedGift);
				cgDBYear.setChanged(true);
				responseJsonList.add("ADDED_CLONED_GIFT" + gson.toJson(reqAddClonedGift, ClonedGift.class));
			}
		}
		
		Type responseListType = new TypeToken<ArrayList<String>>(){}.getType();
		return "ADDED_LIST_CLONED_GIFTS" + gson.toJson(responseJsonList, responseListType);
	}
	
	boolean isDuplicateClone(int year, ClonedGift requestedClone)
	{
//		System.out.println(String.format("ServClonedGiftDB.isDupClone: checking: childID=%d, giftID= %d, gn=%d",
//			requestedClone.getChildID(), requestedClone.getGiftID(), requestedClone.getGiftNumber()));
		
		//retrieve the cloned gift data base for the year
		ClonedGift dupClone = null;
		for(ClonedGift cg : clonedGiftDB.get(DBManager.offset(year)).getList())
		{
			if(cg.getPriorID() == -1 && cg.getChildID() == requestedClone.getChildID() && 
				cg.getGiftNumber() == requestedClone.getGiftNumber() &&
				cg.getGiftID() == requestedClone.getGiftID())
			{
				dupClone = cg;
				break;
			}
		}
		
//		if(dupClone != null)
//			System.out.println(String.format("ServClonedGiftDB.isDupClone: dup found, childID=%d, giftID= %d, gn=%d",
//						dupClone.getChildID(), dupClone.getGiftID(), dupClone.getGiftNumber()));
			
		return dupClone != null;
	}
	
	void processClonedGiftAdded(int year, ClonedGift replacedGift, ClonedGift addedGift)
	{	
		ClonedGiftStatus newStatus = replacedGift.getGiftStatus();	//default is to keep status
		
		switch(replacedGift.getGiftStatus())
		{
			case Unassigned:
				if(addedGift.getPartnerID() > -1)
				{	
					newStatus = ClonedGiftStatus.Assigned;
					serverPartnerDB.changeCount(year, addedGift.getPartnerID(), "ASSIGNED", 1);
				}
			break;
			
			case Assigned:
				if(replacedGift.getPartnerID() > -1 && addedGift.getPartnerID() == -1)
				{	
					newStatus = ClonedGiftStatus.Unassigned;
					serverPartnerDB.changeCount(year, replacedGift.getPartnerID(), "ASSIGNED", -1);
				}
				else if(replacedGift.getPartnerID() > -1 && addedGift.getPartnerID() > -1 &&
						 replacedGift.getPartnerID() != addedGift.getPartnerID())
				{
					newStatus = ClonedGiftStatus.Assigned;
					serverPartnerDB.changeCount(year, replacedGift.getPartnerID(), "ASSIGNED", -1);
					serverPartnerDB.changeCount(year, addedGift.getPartnerID(), "ASSIGNED", 1);					
				}
				else if(replacedGift.getPartnerID() > -1 &&  
						 replacedGift.getPartnerID() == addedGift.getPartnerID() &&
						  addedGift.getGiftStatus() == ClonedGiftStatus.Delivered)
				{	
					newStatus = ClonedGiftStatus.Delivered;
					serverPartnerDB.changeCount(year, addedGift.getPartnerID(), "DELIVERED", 1);
				}
			break;
			
			case Delivered:
				if(replacedGift.getPartnerID() == addedGift.getPartnerID() &&
				    addedGift.getGiftStatus() == ClonedGiftStatus.Assigned)
				{	
					newStatus = ClonedGiftStatus.Assigned;
					serverPartnerDB.changeCount(year, addedGift.getPartnerID(), "DELIVERED", -1);
				}
				else if(replacedGift.getPartnerID() == addedGift.getPartnerID() &&
						addedGift.getGiftStatus() == ClonedGiftStatus.Received)
				{	
					newStatus = ClonedGiftStatus.Received;
					serverPartnerDB.changeCount(year, addedGift.getPartnerID(), "RECEIVED", 1);
				}
				else if(replacedGift.getPartnerID() == addedGift.getPartnerID() &&
						addedGift.getGiftStatus() == ClonedGiftStatus.Returned)
				{	
					newStatus = ClonedGiftStatus.Returned;
				}
			break;
			
			case Received:
				if(replacedGift.getPartnerID() == addedGift.getPartnerID() &&
			    	addedGift.getGiftStatus() == ClonedGiftStatus.Delivered)
				{	
					newStatus = ClonedGiftStatus.Delivered;
					serverPartnerDB.changeCount(year, addedGift.getPartnerID(), "RECEIVED", -1);
				}
			break;
			
			case Returned:
				if(replacedGift.getPartnerID() == addedGift.getPartnerID() &&
					addedGift.getGiftStatus() == ClonedGiftStatus.Delivered)
				{	
					newStatus = ClonedGiftStatus.Delivered;
				}
			break;
			
			default:
				newStatus = replacedGift.getGiftStatus();
			break;
		}
		
		addedGift.setGiftStatus(newStatus);
	}
/*	
	void deleteChildGifts(int year, int childID)
	{
		ChildGiftDBYear cwDBYear = childGiftDB.get(DBManager.offset(year));
		List<ONCChildGift> cwAL = cwDBYear.getList();
		for(int index = cwAL.size()-1; index >= 0; index--) 
			if(cwAL.get(index).getChildID() == childID)
			{
				cwAL.remove(index);
				cwDBYear.setChanged(true);
			}
	}
*/
/*	
	//Search the database for the child's gift history. Return a json of ArrayList<ONCChildGift>
	String getChildGiftHistory(int year, String reqjson)
	{
		//Convert list to json and return it
		Gson gson = new Gson();
		HistoryRequest ghRequest = gson.fromJson(reqjson, HistoryRequest.class);
		
		List<ONCChildGift> cgHistory = new ArrayList<ONCChildGift>();
		List<ONCChildGift> cgAL = childGiftDB.get(DBManager.offset(year)).getList();
		
		//Search for child gifts that match the child id
		for(ONCChildGift cw: cgAL)
			if(cw.getChildID() == ghRequest.getID() && cw.getGiftNumber() == ghRequest.getNumber())
				cgHistory.add(cw);
			
		//Convert list to json and return it
		Type listtype = new TypeToken<ArrayList<ONCChildGift>>(){}.getType();
		
		String response = gson.toJson(cgHistory, listtype);
		return response;
	}
*/
/*
	List<ONCChildGift> getChildGiftHistory(int year, int childID, int wn)
	{
		//create the return list
		List<ONCChildGift> cgHistory = new ArrayList<ONCChildGift>();
		List<ONCChildGift> cgAL = childGiftDB.get(DBManager.offset(year)).getList();
		
		//Search for child gifts that match the child id and gift number. For each 
		//gift that matches the child id and gift number, add it to the gift history list
		for(ONCChildGift gw: cgAL)
			if(gw.getChildID() == childID && gw.getGiftNumber() == wn)
				cgHistory.add(gw);
		
		//sort the gift chronologically, most recent gift first in the list
		Collections.sort(cgHistory, new ChildGiftDateChangedComparator());
		
		return cgHistory;
	}
*/
	
	ClonedGift getClonedGift(int year, int clonedGiftID)
	{
		List<ClonedGift> cgAL = clonedGiftDB.get(DBManager.offset(year)).getList();	//Get the cloned gift list for the year
		
		//search the cloned gift data base for the cloned gift by the id
		//Search from the bottom of the data base for speed. New gifts are added to the bottom
		int index = cgAL.size()-1;
		while(index >= 0 && cgAL.get(index).getID() != clonedGiftID)
			index--;
		
		return index == -1 ? null : cgAL.get(index);
	}

	@Override
	void addObject(int year, String[] nextLine) 
	{
		//Get the cloned gift list for the year and add the object
		ClonedGiftDBYear cgDBYear = clonedGiftDB.get(DBManager.offset(year));
		cgDBYear.add(new ClonedGift(nextLine));
	}

	@Override
	void createNewSeason(int newYear) 
	{
		//create a new cloned gift data base year for the year provided in the newYear parameter
		//The cloned gift db year list is initially empty prior to the selection of cloned gifts,
		//so all we do here is create a new ClonedWishDBYear for the newYear and save it.
		ClonedGiftDBYear clonedGiftDBYear = new ClonedGiftDBYear(newYear);
		clonedGiftDB.add(clonedGiftDBYear);
		clonedGiftDBYear.setChanged(true);	//mark this db for persistent saving on the next save event
	}
	
	private class ClonedGiftDBYear extends ServerDBYear
    {
    		private List<ClonedGift> cgList;
    	
    		ClonedGiftDBYear(int year)
    		{
    			super();
    			cgList = new ArrayList<ClonedGift>();
    		}
    	
    		//getters
    		List<ClonedGift> getList() { return cgList; }
    	
    		void add(ClonedGift addedClonedGift) { cgList.add(addedClonedGift); }
    }

	@Override
	void save(int year)
	{
		String[] header = {"Cloned Gift ID", "Child ID", "Gift ID", "Detail",
	 			"Gift #", "Restrictions", "Status","Changed By", "Time Stamp",
	 			"PartnerID", "Prior ID", "Next ID"};
		
		ClonedGiftDBYear cgDBYear = clonedGiftDB.get(DBManager.offset(year));
		if(cgDBYear.isUnsaved())
		{
			String path = String.format("%s/%dDB/ClonedGiftDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(cgDBYear.getList(), header, path);
			cgDBYear.setChanged(false);
		}
	}
}
