package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import ourneighborschild.HistoryRequest;
import ourneighborschild.ONCChild;
import ourneighborschild.ONCChildGift;
import ourneighborschild.ONCPartner;
import ourneighborschild.ONCUser;
import ourneighborschild.GiftStatus;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerChildWishDB extends ServerSeasonalDB
{
	private static final int CHILD_WISH_DB_HEADER_LENGTH = 10;
	private static final int BASE_YEAR = 2012;
	private static final int NUMBER_OF_WISHES_PER_CHILD = 3;
	private static final int PARTNER_TYPE_ONC_SHOPPER = 6;
	private static final int WISH_INDICATOR_ALLOW_SUBSTITUE = 2;
	private static final String CHILD_WISH_DEFAULT_DETAIL = "Age appropriate";
	private static ServerChildWishDB instance = null;

	private static List<ChildWishDBYear> childwishDB;
	private ServerChildDB childDB; //Reference used to update ChildWishID's 
	private ServerPartnerDB serverPartnerDB;
	private static ClientManager clientMgr;
	
	private ServerChildWishDB() throws FileNotFoundException, IOException
	{
		//create the child wish data base
		childwishDB = new ArrayList<ChildWishDBYear>();
						
		//populate the child wish data base for the last TOTAL_YEARS from persistent store
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the child wish list for each year
			ChildWishDBYear cwDBYear = new ChildWishDBYear(year);
							
			//add the list of children for the year to the db
			childwishDB.add(cwDBYear);
							
			//import the children from persistent store
			importDB(year, String.format("%s/%dDB/ChildWishDB.csv",
					System.getProperty("user.dir"),
						year), "Child Wish DB", CHILD_WISH_DB_HEADER_LENGTH);
			
//			System.out.println(String.format("%d Wish objects imported: %d", year, cwDBYear.getList().size()));
			
			//set the next id
			cwDBYear.setNextID(getNextID(cwDBYear.getList()));
		}

		childDB = ServerChildDB.getInstance();
		serverPartnerDB = ServerPartnerDB.getInstance();
		clientMgr = ClientManager.getInstance();
	}
	
	public static ServerChildWishDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerChildWishDB();
		
		return instance;
	}
	
	List<ONCChildGift> getChildWishList(int year)
	{
		return childwishDB.get(year - BASE_YEAR).getList();
	}
/*	
	void updateWishIDs()
	{		
		ServerWishCatalog wishCatDB = null;
		try {
			 wishCatDB = ServerWishCatalog.getInstance();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		for(int year=2012; year<2015; year++)
		{
			//get the wish catalog for the year
			List<ONCWish> wishCatYear = wishCatDB.getWishCatalogYear(year);
			
			//retrieve the child wish data base for the year
			ChildWishDBYear cwDBYear = childwishDB.get(year - BASE_YEAR);
			
			//iterate thru each wish and match it to the wish in the catalog. Then
			//update the wishID
			for(ONCChildWish cw:cwDBYear.getList())
			{
				//search for wish name in the catalog matching the child wish base
				int index = 0;
//				while(index < wishCatYear.size() && 
//						!wishCatYear.get(index).getName().equals(cw.getChildWishBase()))
//					index++;
				
				if(index < wishCatYear.size())
				{
//					System.out.println(String.format("ServerChildWishDB.updateWishID: cwID: %d, wishName: %s, "
//							+ "wishID: %d", cw.getID(), cw.getChildWishBase(), wishCatYear.get(index).getID()));
					cw.setWishID(wishCatYear.get(index).getID());
				}
				else
				{
					cw.setWishID(-1);	//wish not found in wish catalog - should be impossible
				}
			}
			
			cwDBYear.setChanged(true);	//mark DB for saving
		}
			
	}
*/	
	@Override
	String add(int year, String wishjson, ONCUser client)
	{
		//Create a child wish object for the added wish
		Gson gson = new Gson();
		ONCChildGift addedWish = gson.fromJson(wishjson, ONCChildGift.class);
		
		//retrieve the child wish data base for the year
		ChildWishDBYear cwDBYear = childwishDB.get(year - BASE_YEAR);
				
		//set the new ID for the added child wish
		addedWish.setID(cwDBYear.getNextID());
				
		//retrieve the old wish being replaced
		ONCChildGift oldWish = getWish(year, addedWish.getChildID(), addedWish.getGiftNumber());
		
		//Add the new wish to the proper data base
		cwDBYear.add(addedWish);
		cwDBYear.setChanged(true);
		
		//Update the child object with new wish
		childDB.updateChildsWishID(year, addedWish);
		
		//process new wish to see if other data bases require update. They do if the wish
		//status has caused a family status change or if a partner assignment has changed
		processWishAdded(year, oldWish, addedWish);
		
		return "WISH_ADDED" + gson.toJson(addedWish, ONCChildGift.class);
	}
/*	
	String add(int year, String wishjson, ONCUser user)
	{
		//Create a child wish object for the added wish
		Gson gson = new Gson();
		ONCChildWish addedWish = gson.fromJson(wishjson, ONCChildWish.class);
		
		//retrieve the child wish data base for the year and set the new ID for the added child wish
		ChildWishDBYear cwDBYear = childwishDB.get(year - BASE_YEAR);
		addedWish.setID(cwDBYear.getNextID());
				
		//retrieve the old wish being replaced
		ONCChildWish oldWish = getWish(year, addedWish.getChildID(), addedWish.getWishNumber());
		
		//determine if we need to change the partner id. If the wish is changing, a new partner must
		//be assigned in a second step. Set this wish partner to NO_PARTNER (-1). A prior wish must
		//have existed in order to assign a partner.  
		boolean bPartnerChanged = false;
		ONCPartner newPartner = new ONCPartner(-1, "None", "None");
		int newPartnerID;
		if(oldWish != null && oldWish.getWishID() != addedWish.getWishID())
			newPartner = new ONCPartner(-1, "None", "None");
//			newPartnerID = -1;
		else if(oldWish != null && oldWish.getChildWishAssigneeID() != addedWish.getChildWishAssigneeID())
		{
//			newPartnerID = addedWish.getChildWishAssigneeID();
			newPartner =  serverPartnerDB.getPartner(year,  addedWish.getChildWishAssigneeID());
		}
		
		if(oldWish != null)
		{
			//determine if the detail needs to change
			String updatedDetail = checkForDetailChange(addedWish, newPartner, oldWish);
			addedWish.setChildWishDetail(updatedDetail);
		
			//determine if the status needs to change
			WishStatus updatedStatus = checkForStatusChange(addedWish, newPartner, oldWish);
			addedWish.setChildWishStatus(updatedStatus);
		}
		
		
		//determine if adding the wish changes any other wish for this child
		//must check to see if new wish is wish 0 and has changed to/from a 
		//Bike. If so, wish 2 must become a Helmet/Empty
		ONCChild child = childDB.getChild(year, addedWish.getChildID());
		int bikeID = ServerWishCatalog.findWishIDByName(year, "Bike");
		int helmetID = ServerWishCatalog.findWishIDByName(year, "Helmet");
		ONCChildWish corollaryWish = null, oldCorollaryWish = null;
		if(addedWish.getWishNumber() == 0 && oldWish != null && oldWish.getWishID() != bikeID && 
				addedWish.getWishID() == bikeID || addedWish.getWishNumber() == 0 && oldWish == null &&
					addedWish.getWishID() == bikeID)		
		{
			//add Helmet as the childs second gift (gift 1)
			oldCorollaryWish = getWish(year, addedWish.getChildID(), 1);
			corollaryWish = new ONCChildWish(cwDBYear.getNextID(), child.getID(), helmetID, "", 1, 1, 
					WishStatus.Selected, -1, user.getLNFI(), new Date());
			
//			helmetResponse = serverIF.sendRequest("POST<childwish>" + gson.toJson(helmetCW, ONCChildWish.class));
//			if(helmetResponse != null && helmetResponse.startsWith("WISH_ADDED"))
//					processAddedWish(this, helmetResponse.substring(10));
		}
		//if replaced wish was a bike and now isn't and wish 1 was a helmet, make
		//wish one empty
		else if(addedWish.getWishNumber() == 0 && oldWish != null && child.getChildGiftID(1) > -1 &&
				 oldWish.getWishID() == bikeID && addedWish.getWishID() != bikeID)
		{
			//replace Helmet as the child's second gift (gift 1) with None
			oldCorollaryWish = getWish(year, addedWish.getChildID(), 1);
			corollaryWish = new ONCChildWish(cwDBYear.getNextID(), child.getID(), -1, "", 1, 0, 
					WishStatus.Not_Selected, -1, user.getLNFI(), new Date());
//			helmetResponse = serverIF.sendRequest("POST<childwish>" + gson.toJson(helmetCW, ONCChildWish.class));
//			if(helmetResponse != null && helmetResponse.startsWith("WISH_ADDED"))
//					processAddedWish(this, helmetResponse.substring(10));
		}
		
		//Add the new wish to the proper data base
		cwDBYear.add(addedWish);
		cwDBYear.setChanged(true);
		
		//Update the child object with new wish
		childDB.updateChildsWishID(year, addedWish);
		
		//process new wish to see if other data bases require update. They do if the wish
		//status has caused a family status change or if a partner assignment has changed
		processWishAdded(year, oldWish, addedWish);
		
		//if the was a corollary wish change, handle it as well. This includes notifying in-year
		//clients of the change
		if(corollaryWish != null);
		{
			cwDBYear.add(corollaryWish);
			childDB.updateChildsWishID(year, corollaryWish);
			processWishAdded(year, oldCorollaryWish, corollaryWish);
			clientMgr.notifyAllInYearClients(year, "WISH_ADDED" + gson.toJson(corollaryWish, ONCChildWish.class));
		}
		
		return "WISH_ADDED" + gson.toJson(addedWish, ONCChildWish.class);
	}
*/	
	/*******************************************************************************************
	 * This method implements a rules engine governing the relationship between a wish type and
	 * wish status and wish assignment and wish status. It is called when a child's wish or
	 * assignee changes and implements an automatic change of wish status.
	 * 
	 * For example, if a child's base wish is empty and it is changing to a wish selected from
	 * the catalog, this method will set the wish status to CHILD_WISH_SELECTED. Conversely, if
	 * a wish was selected from the catalog and is reset to empty, the wish status is set to
	 * CHILD_WISH_EMPTY.
	 ************************************************************************************************************/
	GiftStatus checkForStatusChange(ONCChildGift addedWish, ONCPartner reqPartner, ONCChildGift oldWish)
	{
		GiftStatus currStatus, newStatus;
		
		if(oldWish == null)	//Creating first wish
			currStatus = GiftStatus.Not_Selected;
		else	
			currStatus = oldWish.getGiftStatus();
		
		//set new status = current status for default return
		newStatus = currStatus;
		
		switch(currStatus)
		{
			case Not_Selected:
				if(addedWish.getGiftID() > -1 && reqPartner != null && reqPartner.getID() != -1)
					newStatus = GiftStatus.Assigned;	//wish assigned from inventory
				else if(addedWish.getGiftID() > -1)
					newStatus = GiftStatus.Selected;
				break;
				
			case Selected:
				if(addedWish.getGiftID() == -1)
					newStatus = GiftStatus.Not_Selected;
				else if(reqPartner != null && reqPartner.getID() != -1)
					newStatus = GiftStatus.Assigned;
				break;
				
			case Assigned:
				if(addedWish.getGiftID() == -1)
					newStatus = GiftStatus.Not_Selected;
				else if(oldWish.getGiftID() != addedWish.getGiftID())
					newStatus = GiftStatus.Selected;
				else if(reqPartner == null || reqPartner != null && reqPartner.getID() == -1)
					newStatus = GiftStatus.Selected;
				else if(addedWish.getGiftStatus() == GiftStatus.Delivered)
					newStatus = GiftStatus.Delivered;
				break;
				
			case Delivered:
				if(addedWish.getGiftStatus() == GiftStatus.Returned)
					newStatus = GiftStatus.Returned;
				else if(addedWish.getGiftStatus() == GiftStatus.Delivered && reqPartner != null && 
							reqPartner.getID() > -1 && reqPartner.getType() == PARTNER_TYPE_ONC_SHOPPER)
					newStatus = GiftStatus.Shopping;
				else if(addedWish.getGiftStatus() == GiftStatus.Delivered && reqPartner != null && reqPartner.getID() > -1)
					newStatus = GiftStatus.Assigned;
				else if(addedWish.getGiftStatus() == GiftStatus.Shopping)
					newStatus = GiftStatus.Shopping;
				else if(addedWish.getGiftStatus() == GiftStatus.Received)
					newStatus = GiftStatus.Received;
				break;
				
			case Returned:
				if(addedWish.getGiftID() == -1)
					newStatus = GiftStatus.Not_Selected;
				else if(reqPartner != null && reqPartner.getID() == -1)
					newStatus = GiftStatus.Selected;
				else if(reqPartner != null && reqPartner.getType() != PARTNER_TYPE_ONC_SHOPPER)
					newStatus = GiftStatus.Assigned;
				else if(reqPartner != null && reqPartner.getType() == PARTNER_TYPE_ONC_SHOPPER)
					newStatus = GiftStatus.Shopping;
				break;
				
			case Shopping:
				if(addedWish.getGiftStatus() == GiftStatus.Returned)
					newStatus = GiftStatus.Returned;
				else if(addedWish.getGiftStatus() == GiftStatus.Received)
					newStatus = GiftStatus.Received;
				break;
				
			case Received:
				if(addedWish.getGiftStatus() == GiftStatus.Missing)
					newStatus = GiftStatus.Missing;
				else if(addedWish.getGiftStatus() == GiftStatus.Distributed)
					newStatus = GiftStatus.Distributed;
				else if(addedWish.getGiftStatus() == GiftStatus.Delivered)
					newStatus = GiftStatus.Delivered;
				break;
				
			case Distributed:
				if(addedWish.getGiftStatus() == GiftStatus.Missing)
					newStatus = GiftStatus.Missing;
				else if(addedWish.getGiftStatus() == GiftStatus.Verified)
					newStatus = GiftStatus.Verified;
				break;
			
			case Missing:
				if(addedWish.getGiftStatus() == GiftStatus.Received)
					newStatus = GiftStatus.Received;
				else if(reqPartner != null && reqPartner.getType() == PARTNER_TYPE_ONC_SHOPPER)
					newStatus = GiftStatus.Shopping;
				else if(addedWish.getGiftStatus() == GiftStatus.Assigned && reqPartner != null && reqPartner.getID() > -1)
					newStatus = GiftStatus.Assigned;
				break;
				
			case Verified:
				if(addedWish.getGiftStatus() == GiftStatus.Missing)
					newStatus = GiftStatus.Missing;
				break;
				
			default:
				break;
		}
		
		return newStatus;			
	}
	
	/*** checks for automatic change of wish detail. An automatic change is triggered if
	 * the replaced wish is of status Delivered and requested parter is of type ONC Shopper
	 * and the requested wish indicator is #. 
	 */
	String checkForDetailChange(ONCChildGift addedWish, ONCPartner reqPartner, ONCChildGift replWish)
	{
//		if(replWish != null && reqPartner != null)
//			System.out.println(String.format("ChildWishDB.checkforDetailChange: replWishStatus= %s, reqWishInd= %d, reqPartnerType = %d, reqDetail= %s",
//				replWish.getChildWishStatus().toString(), reqWishRes, reqPartner.getType(),
//				reqWishDetail));
		
		if(replWish != null && reqPartner != null && 
			replWish.getGiftStatus() == GiftStatus.Delivered && 
			 reqPartner.getType() == PARTNER_TYPE_ONC_SHOPPER && 
			  addedWish.getIndicator() == WISH_INDICATOR_ALLOW_SUBSTITUE)
		{
			return CHILD_WISH_DEFAULT_DETAIL;
		}
		else
			return addedWish.getDetail();
	}
	
	void processWishAdded(int year, ONCChildGift oldWish, ONCChildGift addedWish)
	{
		//test to see if the family status needs to change
		ServerFamilyDB serverFamilyDB = null;
		try {
			serverFamilyDB = ServerFamilyDB.getInstance();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
		serverFamilyDB.checkFamilyGiftStatusAndGiftCardOnlyOnWishAdded(year, addedWish.getChildID());
		
		//test to see if assignee are changing, if the old wish exists	
		if(oldWish != null && oldWish.getPartnerID() != addedWish.getPartnerID())
		{
			//assignee change -- need to adjust partner gift assignment counts in partner DB
			serverPartnerDB.updateGiftAssignees(year, oldWish.getPartnerID(), 
												addedWish.getPartnerID());
		}

		//test to see if wish status is changing to Received from Delivered or Shopping, or from Assigned to
		//Delivered. If, so, increment the associated gift count for the assigned partner. Once an ornament is
		//Delivered, don't decrement the Delivered count, as we expect the partner to provide the gift.
		//Similarly, once a gift is received from the partner, don't decrement the received gift count, even 
		//if we misplace the gift in the warehouse. 
		
		//That can give rise to the unusual condition that a gift is received twice. This can only happen if 
		//once a gift is received it goes missing, we are unable to find it, and have to shop for a replacement.
		//If this occurs, the total partner count of gifts received will be greater than the number of gifts
		//delivered to families. Refer to the ONC Child Wish Status Life Cycle for additional detail
		if(oldWish != null && addedWish.getGiftStatus() == GiftStatus.Received && 
		   (oldWish.getGiftStatus() == GiftStatus.Delivered || oldWish.getGiftStatus() == GiftStatus.Shopping))
		{
			serverPartnerDB.incrementGiftActionCount(year, addedWish);
		}
		else if(oldWish != null && addedWish.getGiftStatus() == GiftStatus.Delivered && oldWish.getGiftStatus() == GiftStatus.Assigned)
		{
			serverPartnerDB.incrementGiftActionCount(year, addedWish);
		}
	}
	
	void deleteChildWishes(int year, int childID)
	{
		ChildWishDBYear cwDBYear = childwishDB.get(year - BASE_YEAR);
		List<ONCChildGift> cwAL = cwDBYear.getList();
		for(int index = cwAL.size()-1; index >= 0; index--) 
			if(cwAL.get(index).getChildID() == childID)
			{
				cwAL.remove(index);
				cwDBYear.setChanged(true);
			}
	}

	
	//Search the database for the child's wish history. Return a json of ArrayList<ONCChildWish>
	String getChildWishHistory(int year, String reqjson)
	{
		//Convert list to json and return it
		Gson gson = new Gson();
		HistoryRequest whRequest = gson.fromJson(reqjson, HistoryRequest.class);
		
		List<ONCChildGift> cwHistory = new ArrayList<ONCChildGift>();
		List<ONCChildGift> cwAL = childwishDB.get(year - BASE_YEAR).getList();
		
		//Search for child wishes that match the child id
		for(ONCChildGift cw: cwAL)
			if(cw.getChildID() == whRequest.getID() && cw.getGiftNumber() == whRequest.getNumber())
				cwHistory.add(cw);
			
		//Convert list to json and return it
		Type listtype = new TypeToken<ArrayList<ONCChildGift>>(){}.getType();
		
		String response = gson.toJson(cwHistory, listtype);
		return response;
	}
	
	List<ONCChildGift> getChildWishHistory(int year, int childID, int wn)
	{
		//create the return list
		List<ONCChildGift> cwHistory = new ArrayList<ONCChildGift>();
		List<ONCChildGift> cwAL = childwishDB.get(year - BASE_YEAR).getList();
		
		//Search for child wishes that match the child id and wish number. For each 
		//wish that matches the child id and wish number, add it to the wish history list
		for(ONCChildGift cw: cwAL)
			if(cw.getChildID() == childID && cw.getGiftNumber() == wn)
				cwHistory.add(cw);
		
		//sort the wish chronologically, most recent wish first in the list
		Collections.sort(cwHistory, new ChildWishDateChangedComparator());
		
		return cwHistory;
	}
	
	/***************
	 * Method searches the child wish data base indicated by the year parameter for a match
	 * with the wishID parameter. If the wish is found, it is returned, else null is returned
	 * @param year
	 * @param wishID
	 * @return
	 */
	static ONCChildGift getWish(int year, int wishID)
	{
		List<ONCChildGift> cwAL = childwishDB.get(year - BASE_YEAR).getList();	//Get the child wish AL for the year
		
		int index = cwAL.size() -1;	//Set the element to the last child wish in the array
		
		//Search from the bottom of the data base for speed. New wishes are added to the bottom
		while (index >= 0 && cwAL.get(index).getID() != wishID)
			index--;
		
		if(index == -1)
			return null;	//Wish wasn't found in data base
		else
			return cwAL.get(index);		
	}
	
	ONCChildGift getWish(int year, int childid, int wishnum)
	{
		List<ONCChildGift> cwAL = childwishDB.get(year - BASE_YEAR).getList();	//Get the child wish AL for the year
		
		int index = cwAL.size() -1;	//Set the element to the last child wish in the array
		
		//Search from the bottom of the data base for speed. New wishes are added to the bottom
		while (index >= 0 && (cwAL.get(index).getChildID() != childid || 
				cwAL.get(index).getGiftNumber() != wishnum))
			index--;
		
		if(index == -1)
			return null;	//Wish wasn't found in data base
		else
			return cwAL.get(index);		
	}
	
	List<PriorYearPartnerPerformance> getPriorYearPartnerPerformanceList(int newYear)
	{
		//create the list to be returned
		List<PriorYearPartnerPerformance> pyPerformanceList = new ArrayList<PriorYearPartnerPerformance>();
		
		//get a Calendar object gifts received deadline
		
		//get the receive gift deadline for the prior year
		ServerGlobalVariableDB gvDB = null;
		try 
		{
			gvDB = ServerGlobalVariableDB.getInstance();
		}
		catch (FileNotFoundException e) 
		{
			// TODO Auto-generated catch block
		} 
		catch (IOException e) 
		{
			// TODO Auto-generated catch block
		}
		
		//set the received gift deadline. All gifts must be received prior to this date/time
		
		//set up variables for each of the parameters of interest
		int pyPartnerReceivedBeforeID, pyPartnerReceivedAfterID, pyPartnerDeliveredID, pyPartnerAssignedID;
		
		//for each child wish, get the prior year wish history for the child's wish 
		for(ONCChild pyc: childDB.getList(newYear-1))
			for(int wn=0; wn < NUMBER_OF_WISHES_PER_CHILD; wn++)
			{
				//reset the assigned and received partner ID's
				pyPartnerReceivedBeforeID = -1;
				pyPartnerReceivedAfterID = -1;
				pyPartnerDeliveredID = -1;
				pyPartnerAssignedID = -1;
				
				//get the prior year wish history for the child and wish number
				List<ONCChildGift> pycWH = getChildWishHistory(newYear-1, pyc.getID(), wn);
				
				//work down the list from earliest wishes to most recent. For all wishes that were
				//created prior to the gifts received deadline, record the ID's of the partner who
				//the wish was last received from and last assigned to prior to the deadline. Each
				//id is recorded, however, as a newer id is found, the older id is overwritten
				int index = 0;
				while(index < pycWH.size())
				{
					if(pycWH.get(index).getGiftStatus() == GiftStatus.Assigned)
					{
						pyPartnerAssignedID = pycWH.get(index).getPartnerID();
					}
					else if(pycWH.get(index).getGiftStatus() == GiftStatus.Delivered)
					{
						pyPartnerDeliveredID = pycWH.get(index).getPartnerID();
					}
					else if(pycWH.get(index).getGiftStatus() == GiftStatus.Received)
					{
						if(pycWH.get(index).getDateChanged().compareTo(gvDB.getDateGiftsRecivedDealdine(newYear-1)) <= 0)
							pyPartnerReceivedBeforeID = pycWH.get(index).getPartnerID();
						else
							pyPartnerReceivedAfterID = pycWH.get(index).getPartnerID();
					}
				
					index++;
				}
				
				//create a new pyPartnerPerformance object if either the AssignedID, DelliveredID, or the Received
				//From ID is an actual partner ID and not -1 or 0 
				if(pyPartnerAssignedID > -1 || pyPartnerDeliveredID > -1 || 
					pyPartnerReceivedBeforeID > -1 || pyPartnerReceivedAfterID > -1)
				{
					pyPerformanceList.add(new PriorYearPartnerPerformance(pyc.getID(), wn, pyPartnerAssignedID,
										pyPartnerDeliveredID, pyPartnerReceivedBeforeID, pyPartnerReceivedAfterID));
				}
			}
		
//		for(PriorYearPartnerPerformance pyPartPerf : pyPerformanceList)
//			if(pyPartPerf.getPYPartnerWishReceivedAfterDeadlineID() > -1)
//				System.out.println(String.format("Child %d, Wish %d received after deadline from %d",
//						pyPartPerf.getPYChildID(), pyPartPerf.getPYPWishNumber(),
//						pyPartPerf.getPYPartnerWishReceivedAfterDeadlineID()));
		
		return pyPerformanceList;
	}

	@Override
	void addObject(int year, String[] nextLine) 
	{
		//Get the child wish AL for the year and add the object
		ChildWishDBYear cwDBYear = childwishDB.get(year - BASE_YEAR);
		cwDBYear.add(new ONCChildGift(nextLine));
	}

	@Override
	void createNewSeason(int newYear) 
	{
		//create a new child wish data base year for the year provided in the newYear parameter
		//The child wish db year list is initially empty prior to the selection of child wishes,
		//so all we do here is create a new ChildWishDBYear for the newYear and save it.
		ChildWishDBYear childwishDBYear = new ChildWishDBYear(newYear);
		childwishDB.add(childwishDBYear);
		childwishDBYear.setChanged(true);	//mark this db for persistent saving on the next save event
	}
	
	private class ChildWishDateChangedComparator implements Comparator<ONCChildGift>
	{
		@Override
		public int compare(ONCChildGift cw1, ONCChildGift cw2)
		{
			return cw1.getDateChanged().compareTo(cw2.getDateChanged());
		}
	}
	
	private class ChildWishDBYear extends ServerDBYear
    {
    	private List<ONCChildGift> cwList;
    	
    	ChildWishDBYear(int year)
    	{
    		super();
    		cwList = new ArrayList<ONCChildGift>();
    	}
    	
    	//getters
    	List<ONCChildGift> getList() { return cwList; }
    	
    	void add(ONCChildGift addedWish) { cwList.add(addedWish); }
    }

	@Override
	void save(int year)
	{
		String[] header = {"Child Wish ID", "Child ID", "Wish ID", "Detail",
	 			"Wish #", "Restrictions", "Status",
	 			"Changed By", "Time Stamp", "Org ID"};
		
		ChildWishDBYear cwDBYear = childwishDB.get(year - BASE_YEAR);
		if(cwDBYear.isUnsaved())
		{
//			System.out.println(String.format("ServerChildWishDB save() - Saving Child Wish DB"));
			String path = String.format("%s/%dDB/ChildWishDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(cwDBYear.getList(),  header, path);
			cwDBYear.setChanged(false);
		}
	}
	
	void convertWishStatus()
	{
		
	}
}
