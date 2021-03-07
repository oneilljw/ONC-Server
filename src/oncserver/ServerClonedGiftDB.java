package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ourneighborschild.ONCUser;
import ourneighborschild.GiftStatus;
import ourneighborschild.HistoryRequest;
import ourneighborschild.ONCChildGift;

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
	
	List<ONCChildGift> getClonedGiftList(int year)
	{
		return clonedGiftDB.get(DBManager.offset(year)).getList();
	}
	
	String getCurrentCloneGiftList(int year)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCChildGift>>(){}.getType();
		   
		List<ONCChildGift> currClonedGiftList = new ArrayList<ONCChildGift>();
		for(ONCChildGift cg : clonedGiftDB.get(DBManager.offset(year)).getList())
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
		
		List<ONCChildGift> giftHistoryList = new ArrayList<ONCChildGift>();
		List<ONCChildGift> searchList = clonedGiftDB.get(DBManager.offset(year)).getList();
		
		//find the first cloned gift in the linked list
		int index = 0;
		while(index < searchList.size() && !(searchList.get(index).getChildID() == childID &&
				searchList.get(index).getGiftNumber() == gn && searchList.get(index).getPriorID() == -1))
		{
			index++;
		}	
		
		if(index < searchList.size())
		{
			ONCChildGift nextClonedGiftInChain = searchList.get(index);	//first clone in chain
			
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
		Type listtype = new TypeToken<ArrayList<ONCChildGift>>(){}.getType();
		String response = gson.toJson(giftHistoryList, listtype);
		return response;
	}

	@Override
	String add(int year, String wishjson, ONCUser client)
	{
		return "";
	}
	
	String addListOfGifts(int year, String giftListJson, ONCUser client, boolean bRejectDuplicates)
	{
		//Create a cloned gift list for the add cloned gift requests
		Gson gson = new Gson();
		Type listOfClonedGifts = new TypeToken<ArrayList<ONCChildGift>>(){}.getType();		
		List<ONCChildGift> reqAddClonedGiftList = gson.fromJson(giftListJson, listOfClonedGifts);
		List<String> responseJsonList = new ArrayList<String>();

		//retrieve the cloned gift data base for the year
		ClonedGiftDBYear cgDBYear = clonedGiftDB.get(DBManager.offset(year));
		
		//for each add clone gift request, check to see if the gift had already been cloned. If it 
		//hasn't already been cloned, than add it. A new clone gift request has a requested cloned gift
		//id of -1. 
		for(ONCChildGift reqAddClonedGift : reqAddClonedGiftList)
		{	
			if(reqAddClonedGift.getID() == -1 && !isDuplicateClone(year, reqAddClonedGift, bRejectDuplicates))
			{
				//set the new ID for the added cloned gift
				int newClonedGiftID = cgDBYear.getNextID();
				reqAddClonedGift.setID(newClonedGiftID);
				reqAddClonedGift.setNextID(-1);
				reqAddClonedGift.setChangedBy(client.getLNFI());
				reqAddClonedGift.setTimestamp(System.currentTimeMillis());
				
				ONCChildGift previousClonedGift = getClonedGift(year, reqAddClonedGift);
				if(previousClonedGift != null)	//replacing a previous cloned gift
				{	
					reqAddClonedGift.setPriorID(previousClonedGift.getID());
					previousClonedGift.setNextID(newClonedGiftID);
					processClonedGiftAdded(year, previousClonedGift, reqAddClonedGift);
				}
				
				cgDBYear.add(reqAddClonedGift);
				cgDBYear.setChanged(true);
				responseJsonList.add("ADDED_CLONED_GIFT" + gson.toJson(reqAddClonedGift, ONCChildGift.class));
			}
		}
		
		Type responseListType = new TypeToken<ArrayList<String>>(){}.getType();
		return "ADDED_LIST_CLONED_GIFTS" + gson.toJson(responseJsonList, responseListType);
	}
	
	boolean isDuplicateClone(int year, ONCChildGift requestedClone, boolean bRejectDuplicates)
	{
//		System.out.println(String.format("ServClonedGiftDB.isDupClone: checking: childID=%d, giftID= %d, gn=%d",
//			requestedClone.getChildID(), requestedClone.getGiftID(), requestedClone.getGiftNumber()));
		
		if(bRejectDuplicates)
		{
			//retrieve the cloned gift data base for the year
			ONCChildGift dupClone = null;
			for(ONCChildGift cg : clonedGiftDB.get(DBManager.offset(year)).getList())
			{
				if(cg.getChildID() == requestedClone.getChildID() && 
					cg.getGiftNumber() == requestedClone.getGiftNumber() &&
					 cg.getCatalogGiftID() == requestedClone.getCatalogGiftID())
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
		else
			return false;
	}
	
	void processClonedGiftAdded(int year, ONCChildGift priorGift, ONCChildGift addedGift)
	{	
		GiftStatus newStatus = priorGift.getGiftStatus();	//default is to keep status
		
		switch(priorGift.getGiftStatus())
		{
			case Unassigned:
				if(addedGift.getPartnerID() > -1)
				{	
					newStatus = GiftStatus.Assigned;
					serverPartnerDB.changeCount(year, addedGift.getPartnerID(), "ASSIGNED", 1);
				}
			break;
			
			case Assigned:
				if(priorGift.getPartnerID() > -1 && addedGift.getPartnerID() == -1)
				{	
					newStatus = GiftStatus.Unassigned;
					serverPartnerDB.changeCount(year, priorGift.getPartnerID(), "ASSIGNED", -1);
				}
				else if(priorGift.getPartnerID() > -1 && addedGift.getPartnerID() > -1 &&
						 priorGift.getPartnerID() != addedGift.getPartnerID())
				{
					newStatus = GiftStatus.Assigned;
					serverPartnerDB.changeCount(year, priorGift.getPartnerID(), "ASSIGNED", -1);
					serverPartnerDB.changeCount(year, addedGift.getPartnerID(), "ASSIGNED", 1);					
				}
				else if(priorGift.getPartnerID() > -1 &&  
						 priorGift.getPartnerID() == addedGift.getPartnerID() &&
						  addedGift.getGiftStatus() == GiftStatus.Delivered)
				{	
					newStatus = GiftStatus.Delivered;
					serverPartnerDB.changeCount(year, addedGift.getPartnerID(), "DELIVERED", 1);
				}
			break;
			
			case Delivered:
				if(priorGift.getPartnerID() == addedGift.getPartnerID() &&
				    addedGift.getGiftStatus() == GiftStatus.Assigned)
				{	
					newStatus = GiftStatus.Assigned;
					serverPartnerDB.changeCount(year, addedGift.getPartnerID(), "DELIVERED", -1);
				}
				else if(priorGift.getPartnerID() == addedGift.getPartnerID() &&
						addedGift.getGiftStatus() == GiftStatus.Received)
				{	
					newStatus = GiftStatus.Received;
					serverPartnerDB.changeCount(year, addedGift.getPartnerID(), "RECEIVED", 1);
				}
				else if(priorGift.getPartnerID() == addedGift.getPartnerID() &&
						addedGift.getGiftStatus() == GiftStatus.Returned)
				{	
					newStatus = GiftStatus.Returned;
				}
			break;
			
			case Received:
				if(priorGift.getPartnerID() == addedGift.getPartnerID() &&
			    	addedGift.getGiftStatus() == GiftStatus.Delivered)
				{	
					newStatus = GiftStatus.Delivered;
					serverPartnerDB.changeCount(year, addedGift.getPartnerID(), "RECEIVED", -1);
				}
			break;
			
			case Returned:
				if(priorGift.getPartnerID() == addedGift.getPartnerID() &&
					addedGift.getGiftStatus() == GiftStatus.Delivered)
				{	
					newStatus = GiftStatus.Delivered;
				}
			break;
			
			default:
				newStatus = priorGift.getGiftStatus();
			break;
		}
		
		addedGift.setGiftStatus(newStatus);
	}

	ONCChildGift getClonedGift(int year, int clonedGiftID)
	{
		List<ONCChildGift> cgAL = clonedGiftDB.get(DBManager.offset(year)).getList();	//Get the cloned gift list for the year
		
		//search the cloned gift data base for the cloned gift by the id
		//Search from the bottom of the data base for speed. New gifts are added to the bottom
		int index = cgAL.size()-1;
		while(index >= 0 && cgAL.get(index).getID() != clonedGiftID)
			index--;
		
		return index == -1 ? null : cgAL.get(index);
	}
	
	ONCChildGift getClonedGift(int year, int childID, int giftNumber)
	{
		List<ONCChildGift> cgAL = clonedGiftDB.get(DBManager.offset(year)).getList();	//Get the cloned gift list for the year
		
		//search the cloned gift data base for the cloned gift by child id and gift number
		//Search from the bottom of the data base for speed. New gifts are added to the bottom
		int index = cgAL.size()-1;
		while(index >= 0 && (cgAL.get(index).getChildID() != childID ||
							  cgAL.get(index).getGiftNumber() != giftNumber ||
							   cgAL.get(index).getNextID() != -1))
			index--;
		
		return index == -1 ? null : cgAL.get(index);
	}
	
	ONCChildGift getClonedGift(int year, ONCChildGift reqAddClonedGift)
	{
		List<ONCChildGift> cgAL = clonedGiftDB.get(DBManager.offset(year)).getList();	//Get the cloned gift list for the year
		
		//search the cloned gift data base for the cloned gift by child ID and gift number
		//Search from the bottom of the data base for speed. New gifts are added to the bottom
		int index = cgAL.size()-1;
		while(index >= 0 && (cgAL.get(index).getChildID() != reqAddClonedGift.getChildID() ||
							  cgAL.get(index).getGiftNumber() != reqAddClonedGift.getGiftNumber() ||
							   cgAL.get(index).getNextID() != -1))
			index--;
		
		return index == -1 ? null : cgAL.get(index);
	}

	@Override
	void addObject(int year, String[] nextLine) 
	{
		//Get the cloned gift list for the year and add the object
		ClonedGiftDBYear cgDBYear = clonedGiftDB.get(DBManager.offset(year));
		cgDBYear.add(new ONCChildGift(nextLine, true));	//true indicates this is a cloned gift
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
    		private List<ONCChildGift> cgList;
    	
    		ClonedGiftDBYear(int year)
    		{
    			super();
    			cgList = new ArrayList<ONCChildGift>();
    		}
    	
    		//getters
    		List<ONCChildGift> getList() { return cgList; }
    	
    		void add(ONCChildGift addedClonedGift) { cgList.add(addedClonedGift); }
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
