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
import ourneighborschild.ONCChild;
import ourneighborschild.ONCChildGift;
import ourneighborschild.ONCFamily;
import ourneighborschild.ONCGift;

public class ServerClonedGiftDB extends ServerSeasonalDB
{
	private static final int CLONED_GIFT_DB_HEADER_LENGTH = 12;
	private static final int MAX_LABEL_LINE_LENGTH = 26;
	private static ServerClonedGiftDB instance = null;

	private static List<ClonedGiftDBYear> clonedGiftDB;
	
	private ServerFamilyDB familyDB;
	private ServerChildDB childDB; //Reference used to update ChildGiftID's 
	private ServerPartnerDB serverPartnerDB;
	private ServerGiftCatalog catalogDB;
	private ClientManager clientMgr;
	
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

		familyDB = ServerFamilyDB.getInstance();
		childDB = ServerChildDB.getInstance();
		serverPartnerDB = ServerPartnerDB.getInstance();
		catalogDB = ServerGiftCatalog.getInstance();
		clientMgr = ClientManager.getInstance();
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
	
	ONCChildGift getPreviousClonedChildGift(int year, ONCChildGift currentClonedGift)
	{
		List<ONCChildGift> cgAL = clonedGiftDB.get(DBManager.offset(year)).getList();	//Get the cloned gift list for the year
		
		if(currentClonedGift.getPriorID() == -1)
			return null;
		else
		{	
			int index = cgAL.size() -1;	//Set the element to the last child wish in the array
			while(index >= 0 && cgAL.get(index).getID() != currentClonedGift.getPriorID())	
				index--;
	
			return index == -1 ? null : cgAL.get(index);
		}
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
	
	ONCChildGift getPreviousClonedGift(int year, ONCChildGift currentChildGift)
	{
		List<ONCChildGift> cgAL = clonedGiftDB.get(DBManager.offset(year)).getList();	//Get the child gift list for the year
		
		if(currentChildGift.getPriorID() == -1)
			return null;
		else
		{	
			int index = cgAL.size() -1;	//Set the element to the last child wish in the array
			while(index >= 0 && cgAL.get(index).getID() != currentChildGift.getPriorID())	
				index--;
	
			return index == -1 ? null : cgAL.get(index);
		}
	}
	
	WebGift receiveClonedGiftJSONP(int year, int childid, int giftnum, int action, WebClient wc)
	{
		//retrieve the cloned gift data base for the year
		ClonedGiftDBYear clonedGiftDBYear = clonedGiftDB.get(DBManager.offset(year));
		List<ONCChildGift> cgAL = clonedGiftDBYear.getList();
		
		//Search from the bottom of the data base for speed. New gifts are added to the bottom
		int index = cgAL.size() -1;	//Set the element to the last child wish in the array
		while(index >= 0 && (cgAL.get(index).getChildID() != childid ||
							   cgAL.get(index).getGiftNumber() != giftnum ||
							    cgAL.get(index).getNextID() != -1))	
			index--;
	
		if(index == -1)
		{
			return new WebGift(false, "ERROR: Unable to find gift in database");
		}
		else
		{
			ONCChildGift currentGift = cgAL.get(index);
			ONCChild c = childDB.getChild(year, childid);
			
			//make sure the gift hasn't already been received. If it has, return success with
			//an already received message
			if(c == null)
			{
				return new WebGift(false, "ERROR: Unable to find the child this gift was selected for");
			}
			else if(action == 0 && currentGift.getGiftStatus() == GiftStatus.Received)
			{
				WebGift wg = getWebGift(year, currentGift, c, "Gift already received");
				return wg;
			}
			else if(action == 1 && currentGift.getGiftStatus() == GiftStatus.Delivered ||
									 currentGift.getGiftStatus() == GiftStatus.Shopping ||
									  currentGift.getGiftStatus() == GiftStatus.Missing)
			{
				WebGift wg = getWebGift(year, currentGift, c, "Gift already undone");
				return wg;
			}
			else if(action == 0 && currentGift.getGiftStatus() == GiftStatus.Delivered ||
					 action == 1 & currentGift.getGiftStatus() == GiftStatus.Received)
			{
				//receive or undo reception of the gift and create the WebGift to return to the web user.
				//First, make a copy of the existing gift
				ONCChildGift receivedGift = new ONCChildGift(currentGift);
				
				if(action == 0)
					receivedGift.setGiftStatus(GiftStatus.Received);
				else
				{
					ONCChildGift previousGift = getPreviousClonedGift(year, currentGift);
					receivedGift.setGiftStatus(previousGift.getGiftStatus());
				}
					
				receivedGift.setChangedBy(wc.getWebUser().getLNFI());
				receivedGift.setTimestamp(System.currentTimeMillis());					
				
				//set the new ID, prior and next ID's for the added child gift
				int addedGiftID = clonedGiftDBYear.getNextID();
				receivedGift.setID(addedGiftID);
				receivedGift.setNextID(-1);
				
				//update the linked list references
				currentGift.setNextID(addedGiftID);
				receivedGift.setPriorID(currentGift.getID());
				
				//Add the received gift to the proper year data base
				clonedGiftDBYear.add(receivedGift);
				clonedGiftDBYear.setChanged(true);
				
				//process received gift to see if other data bases require update. They do if the gift
				//status has caused a family status change or if a partner assignment has changed
				processClonedGiftAdded(year, currentGift, receivedGift);
				
				//add the json for the cloned gift to the response list
				Gson gson = new Gson();
				List<String> responseJsonList = new ArrayList<String>();
				responseJsonList.add("ADDED_CLONED_GIFT" + gson.toJson(receivedGift, ONCChildGift.class));
				
				//notify the in year clients that a new clone gift has been added
				Type responseListType = new TypeToken<ArrayList<String>>(){}.getType();
				String response =  "ADDED_LIST_CLONED_GIFTS" + gson.toJson(responseJsonList, responseListType);
				clientMgr.notifyAllInYearClients(year, response);
				
				String message = action == 0 ?  "Gift successfully received" : "Gift receive successfully undone";
				return getWebGift(year, currentGift, c, message);
			}
			else
				return new WebGift(false, "ERROR: Can't receive gift, it's status is " + currentGift.getGiftStatus().toString());
		}
	}
	
	//return a JSON of a list with all current child's gifts
	String getCurrentClonedGiftsJSONP(int year, int childid, int giftnum)
	{
		Gson gson = new Gson();
		Type listOfWebGifts = new TypeToken<ArrayList<WebGift>>(){}.getType();
		List<WebGift> webGiftList = new ArrayList<WebGift>();
		
		//Find all gifts for the child
		ONCChild c = childDB.getChild(year, childid);
		if(giftnum < 0 && giftnum >= ServerGlobalVariableDB.getNumGiftsPerChild(year))
			webGiftList.add(new WebGift(false, "ERROR: Invalid gift number in barcode"));
		else if(c == null)
			webGiftList.add(new WebGift(false, "ERROR: Unable to find the child this gift was selected for"));
		else
		{
			//find all the child's current gifts
			for(ONCChildGift childGift : clonedGiftDB.get(DBManager.offset(year)).getList())
				if(childGift.getGiftNumber() == giftnum && childGift.getPriorID() > -1 && childGift.getNextID() == -1)
					webGiftList.add(getWebGift(year, childGift, c, ""));
		}
		
		return gson.toJson(webGiftList, listOfWebGifts);
	}
	
	WebGift getWebGift(int year, ONCChildGift currentGift, ONCChild c, String message)
	{
		//create the WebGift object to return to the web client
		String line1, line2 = "", line3 = " ";
		
		ONCFamily f = familyDB.getFamily(year, c.getFamID());
		ONCGift g = catalogDB.getGift(year, currentGift.getCatalogGiftID());

		String line0 = c.getChildAge() + " " + c.getChildGender();
		
		if(currentGift.getDetail().isEmpty())
		{
			line1 = g.getName() + "- ";
			line2 = "ONC " + Integer.toString(year) + " |  Family # " + f.getONCNum();

			return new WebGift(true, currentGift.getGiftNumber(), line0, line1, line2, "", message);
		}	
		else
		{
			String gift = g.getName().equals("-") ? currentGift.getDetail() :  g.getName() + "- " + currentGift.getDetail();
	
			//does it fit on one line?
			if(gift.length() <= MAX_LABEL_LINE_LENGTH)
			{
				line1 = gift.trim();
			}
			else	//split into two lines
			{
				int chIndex = MAX_LABEL_LINE_LENGTH;
				while(chIndex > 0 && gift.charAt(chIndex) != ' ')	//find the line break
					chIndex--;
	
				line1 = gift.substring(0, chIndex);
				line2 = gift.substring(chIndex);
				if(line2.length() > MAX_LABEL_LINE_LENGTH)
					line2 = gift.substring(chIndex, chIndex + MAX_LABEL_LINE_LENGTH);
			}

			//If the gift required two lines make the ONC Year line 4
			//else make the ONC Year line 3
			if(!line2.isEmpty())
			{
				line3 = "ONC " + Integer.toString(year) + " |  Family # " + f.getONCNum();
			}
			else
			{			
				line2 = "ONC " + Integer.toString(year) + " |  Family # " + f.getONCNum();
			}
			
			return new WebGift(true, currentGift.getGiftNumber(), line0, line1, line2, line3, message);
		}
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
