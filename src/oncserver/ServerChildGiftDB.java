package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ourneighborschild.HistoryRequest;
import ourneighborschild.ONCChild;
import ourneighborschild.ONCChildGift;
import ourneighborschild.ONCUser;
import ourneighborschild.GiftStatus;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerChildGiftDB extends ServerSeasonalDB
{
	private static final int CHILD_GIFT_DB_HEADER_LENGTH = 12;
	private static ServerChildGiftDB instance = null;

	private static List<ChildGiftDBYear> childGiftDB;
	private ServerFamilyDB familyDB;
	private ServerFamilyHistoryDB familyHistoryDB;
	private ServerChildDB childDB; //Reference used to update ChildGiftID's 
	private ServerPartnerDB serverPartnerDB;
	
	private ServerChildGiftDB() throws FileNotFoundException, IOException
	{
		//create the child gift data base
		childGiftDB = new ArrayList<ChildGiftDBYear>();
						
		//populate the child gift data base for the last TOTAL_YEARS from persistent store
		for(int year= DBManager.getBaseSeason(); year < DBManager.getBaseSeason() + DBManager.getNumberOfYears(); year++)
		{
			//create the child gift list for each year
			ChildGiftDBYear cwDBYear = new ChildGiftDBYear(year);
							
			//add the list of children for the year to the db
			childGiftDB.add(cwDBYear);
							
			//import the children from persistent store
			importDB(year, String.format("%s/%dDB/ChildGiftDB.csv",
					System.getProperty("user.dir"),
						year), "Child Wish DB", CHILD_GIFT_DB_HEADER_LENGTH);
							
			//set the next id
			cwDBYear.setNextID(getNextID(cwDBYear.getList()));
		}

		familyDB = ServerFamilyDB.getInstance();
		familyHistoryDB = ServerFamilyHistoryDB.getInstance();
		childDB = ServerChildDB.getInstance();
		serverPartnerDB = ServerPartnerDB.getInstance();
	}
	
	public static ServerChildGiftDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerChildGiftDB();
		
		return instance;
	}
	
	List<ONCChildGift> getChildWishList(int year)
	{
		return childGiftDB.get(DBManager.offset(year)).getList();
	}
	
	String getCurrentChildGiftList(int year)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCChildGift>>(){}.getType();
		   
		List<ONCChildGift> currChildGiftList = new ArrayList<ONCChildGift>();
		for(ONCChildGift cg : childGiftDB.get(DBManager.offset(year)).getList())
			if(cg.getNextID() == -1)	//cloned gift is last in linked list, there for is current
				currChildGiftList.add(cg);
		
		return gson.toJson(currChildGiftList, listtype);
	}

	@Override
	String add(int year, String giftjson, ONCUser client)
	{
		//Create a child gift object for the add gfit request
		Gson gson = new Gson();
		ONCChildGift addedGift = gson.fromJson(giftjson, ONCChildGift.class);
		
		//retrieve the child gift data base for the year
		ChildGiftDBYear cwDBYear = childGiftDB.get(DBManager.offset(year));
		
		//retrieve the old gift being replaced
		ONCChildGift oldGift = getCurrentChildGift(year, addedGift.getChildID(), addedGift.getGiftNumber());
				
		//set the new ID, prior and next ID's for the added child gift
		int addedGiftID = cwDBYear.getNextID();
		addedGift.setID(addedGiftID);
		addedGift.setNextID(-1);
		
		if(oldGift != null)
		{	
			oldGift.setNextID(addedGiftID);
			addedGift.setPriorID(oldGift.getID());
		}
		else
			addedGift.setPriorID(-1);
		
		//Add the new gift to the proper year data base
		cwDBYear.add(addedGift);
		cwDBYear.setChanged(true);
		
		//Update the child object to point to the addedGift
//		childDB.updateChildsWishID(year, addedGift);
		
		//process new gift to see if other data bases require update. They do if the gift
		//status has caused a family status change or if a partner assignment has changed
		processGiftAdded(year, oldGift, addedGift, client);
		
		return "WISH_ADDED" + gson.toJson(addedGift, ONCChildGift.class);
	}
	
	String addListOfGifts(int year, String giftListJson, ONCUser client)
	{
		//Create a child gift list for the added gift
		Gson gson = new Gson();
		Type listOfChildGifts = new TypeToken<ArrayList<ONCChildGift>>(){}.getType();		
		List<ONCChildGift> addedChildGiftList = gson.fromJson(giftListJson, listOfChildGifts);
		List<String> responseJsonList = new ArrayList<String>();

		//retrieve the child wish data base for the year
		ChildGiftDBYear cwDBYear = childGiftDB.get(DBManager.offset(year));
		
		for(ONCChildGift addedGift : addedChildGiftList)
		{
			//retrieve the old gift being replaced
			ONCChildGift oldGift = getCurrentChildGift(year, addedGift.getChildID(), addedGift.getGiftNumber());
			
			int addedGiftID = cwDBYear.getNextID();
			addedGift.setID(addedGiftID);
			addedGift.setNextID(-1);	//this is the last gift in the linked list
			
			if(oldGift != null)
			{	
				oldGift.setNextID(addedGiftID);	//old gift must link to the added gift
				addedGift.setPriorID(oldGift.getID());	//added gift must link to the old gift
			}
			else
				addedGift.setPriorID(-1);	//this is the first gift in the linked list
		
			//Add the new wish to the proper data base
			cwDBYear.add(addedGift);
			
			//Update the child object with new gift
//			childDB.updateChildsWishID(year, addedGift);
		
			//process added gift to see if other data bases require update. They do if the gift
			//status has caused a family status change or if a partner assignment has changed
			processGiftAdded(year, oldGift, addedGift, client);
			responseJsonList.add("WISH_ADDED" + gson.toJson(addedGift, ONCChildGift.class));
		}
		
		cwDBYear.setChanged(true);
		
		Type responseListType = new TypeToken<ArrayList<String>>(){}.getType();
		return "ADDED_GIFT_LIST" + gson.toJson(responseJsonList, responseListType);
	}
	
	List<String> addListOfSignUpGeniusImportedGifts(int year, List<ONCChildGift> addedChildGiftList, ONCUser client)
	{
		Gson gson = new Gson();
		List<String> responseJsonList = new ArrayList<String>();

		//retrieve the child wish data base for the year
		ChildGiftDBYear cwDBYear = childGiftDB.get(DBManager.offset(year));
		
		for(ONCChildGift addedGift : addedChildGiftList)
		{
			//retrieve the old gift being replaced
			ONCChildGift oldGift = getCurrentChildGift(year, addedGift.getChildID(), addedGift.getGiftNumber());
			
			int addedGiftID = cwDBYear.getNextID();
			addedGift.setID(addedGiftID);
			addedGift.setNextID(-1);	//this is the last gift in the linked list
			
			if(oldGift != null)
			{	
				oldGift.setNextID(addedGiftID);	//old gift must link to the added gift
				addedGift.setPriorID(oldGift.getID());	//added gift must link to the old gift
			}
			else
				addedGift.setPriorID(-1);	//this is the first gift in the linked list
		
			//Add the new wish to the proper data base
			cwDBYear.add(addedGift);
			
			//Update the child object with new gift
//			childDB.updateChildsWishID(year, addedGift);
		
			//process added gift to see if other data bases require update. They do if the gift
			//status has caused a family status change or if a partner assignment has changed
			processGiftAdded(year, oldGift, addedGift, client);
			responseJsonList.add("WISH_ADDED" + gson.toJson(addedGift, ONCChildGift.class));
		}
		
		cwDBYear.setChanged(true);
		
		return responseJsonList;
	}
	
//	ClonedGiftStatus checkForPartnerGiftStatusChange()
//	{
//		return ClonedGiftStatus.Unassigned;
//	}
	
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
/*	
	GiftStatus checkForGiftStatusChange(ONCChildGift addedWish, ONCPartner reqPartner, ONCChildGift oldWish)
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
							reqPartner.getID() > -1 && reqPartner.getGiftCollectionType() == GiftCollectionType.ONCShopper)
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
				else if(reqPartner != null && reqPartner.getGiftCollectionType() != GiftCollectionType.ONCShopper)
					newStatus = GiftStatus.Assigned;
				else if(reqPartner != null && reqPartner.getGiftCollectionType() == GiftCollectionType.ONCShopper)
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
				else if(reqPartner != null && reqPartner.getGiftCollectionType() == GiftCollectionType.ONCShopper)
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
*/	
	/*** checks for automatic change of wish detail. An automatic change is triggered if
	 * the replaced wish is of status Delivered and requested parter is of type ONC Shopper
	 * and the requested wish indicator is #. 
	 */
/*	
	String checkForDetailChange(ONCChildGift addedWish, ONCPartner reqPartner, ONCChildGift replWish)
	{	
		if(replWish != null && reqPartner != null && 
			replWish.getGiftStatus() == GiftStatus.Delivered && 
			 reqPartner.getGiftCollectionType() == GiftCollectionType.ONCShopper && 
			  addedWish.getIndicator() == GIFT_INDICATOR_ALLOW_SUBSTITUE)
		{
			return GIFT_WISH_DEFAULT_DETAIL;
		}
		else
			return addedWish.getDetail();
	}
*/	
	void processGiftAdded(int year, ONCChildGift priorGift, ONCChildGift addedGift, ONCUser client)
	{
		//ask the family data base to check to see if the new gift changes either the family gift status or
		//now qualifies the family as a gift card only family
		familyDB.checkFamilyGiftCardOnlyOnGiftAdded(year, priorGift, addedGift);
		
		int famID = childDB.getChildsFamilyID(year, addedGift.getChildID());
		familyHistoryDB.checkFamilyGiftStatusOnGiftAdded(year, priorGift, addedGift, famID, client);
		
		//test to see if gift assignee is changing, if the prior gift exists	
		if(priorGift != null && priorGift.getPartnerID() != addedGift.getPartnerID())
		{
			//assignee change -- need to adjust partner gift assignment counts in partner DB
			serverPartnerDB.updateGiftAssignees(year, priorGift, addedGift);
		}

		//test to see if gift status is changing to Received from Delivered or Shopping, or from Assigned to
		//Delivered. If, so, increment the associated gift count for the assigned partner. Once an ornament is
		//Delivered, don't decrement the Delivered count, as we expect the partner to provide the gift.
		//Similarly, once a gift is received from the partner, don't decrement the received gift count, even 
		//if we misplace the gift in the warehouse. 
		
		//That can give rise to the unusual condition that a gift is received twice. This can only happen if 
		//once a gift is received it goes missing, we are unable to find it, and have to shop for a replacement.
		//If this occurs, the total partner count of gifts received will be greater than the number of gifts
		//delivered to families. Refer to the ONC Child Wish Status Life Cycle for additional detail
		if(priorGift != null && addedGift.getGiftStatus() == GiftStatus.Received && 
		   (priorGift.getGiftStatus() == GiftStatus.Delivered || priorGift.getGiftStatus() == GiftStatus.Shopping))
		{
			serverPartnerDB.incrementGiftActionCount(year, addedGift);
		}
		else if(priorGift != null && addedGift.getGiftStatus() == GiftStatus.Delivered && 
				   priorGift.getGiftStatus() == GiftStatus.Received)
		{
			serverPartnerDB.decrementGiftActionCount(year, priorGift, addedGift);
		}
		else if(priorGift != null && addedGift.getGiftStatus() == GiftStatus.Delivered && priorGift.getGiftStatus() == GiftStatus.Assigned)
		{
			serverPartnerDB.incrementGiftActionCount(year, addedGift);
		}
	}

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
	
	/***************
	 * Method searches the child gift data base indicated by the year parameter for a match
	 * with the giftID parameter. If the gift is found, it is returned, else null is returned
	 * @param year
	 * @param wishID
	 * @return
	 */
	static ONCChildGift getGift(int year, int wishID)
	{
		List<ONCChildGift> cgAL = childGiftDB.get(DBManager.offset(year)).getList();	//Get the child gift AL for the year
		
		int index = cgAL.size() -1;	//Set the element to the last child wish in the array
		
		//Search from the bottom of the data base for speed. New gifts are added to the bottom
		while (index >= 0 && cgAL.get(index).getID() != wishID)
			index--;
		
		if(index == -1)
			return null;	//Gift wasn't found in data base
		else
			return cgAL.get(index);		
	}
	
	ONCChildGift getCurrentChildGift(int year, int childid, int giftnum)
	{
		List<ONCChildGift> cgAL = childGiftDB.get(DBManager.offset(year)).getList();	//Get the child gift list for the year
		
		//get the gift id from the Child data base
//		ONCChild child = childDB.getChild(year, childid);
//		if(child != null)
//		{	
			//Search from the bottom of the data base for speed. New gifts are added to the bottom
			int index = cgAL.size() -1;	//Set the element to the last child wish in the array
//			while(index >= 0 && cgAL.get(index).getID() != child.getChildGiftID(giftnum))
			while(index >= 0 && (cgAL.get(index).getChildID() != childid ||
								   cgAL.get(index).getGiftNumber() != giftnum ||
								    cgAL.get(index).getNextID() != -1))	
				index--;
		
			return index == -1 ? null : cgAL.get(index);

//		}
//		else
//			return null;
	}
/*	
	List<PriorYearPartnerPerformance> getPriorYearPartnerPerformanceList(int newYear)
	{
		//create the list to be returned
		List<PriorYearPartnerPerformance> pyPerformanceList = new ArrayList<PriorYearPartnerPerformance>();
		
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
			for(int gn=0; gn < ServerChildDB.NUMBER_GIFTS_PER_CHILD; gn++)
			{
				//reset the assigned and received partner ID's
				pyPartnerReceivedBeforeID = -1;
				pyPartnerReceivedAfterID = -1;
				pyPartnerDeliveredID = -1;
				pyPartnerAssignedID = -1;
				
				//get the prior year gift history for the child and gift number
				List<ONCChildGift> pycGH = getChildGiftHistory(newYear-1, pyc.getID(), gn);
				
				//work down the list from earliest gifts to most recent. For all gifts that were
				//created prior to the gifts received deadline, record the ID's of the partner who
				//the gift was last received from and last assigned to prior to the deadline. Each
				//id is recorded, however, as a newer id is found, the older id is overwritten
				int index = 0;
				while(index < pycGH.size())
				{
					if(pycGH.get(index).getGiftStatus() == GiftStatus.Assigned)
					{
						pyPartnerAssignedID = pycGH.get(index).getPartnerID();
					}
					else if(pycGH.get(index).getGiftStatus() == GiftStatus.Delivered)
					{
						pyPartnerDeliveredID = pycGH.get(index).getPartnerID();
					}
					else if(pycGH.get(index).getGiftStatus() == GiftStatus.Received)
					{
						if(pycGH.get(index).getDateChanged().compareTo(gvDB.getDateGiftsRecivedDealdine(newYear-1)) <= 0)
							pyPartnerReceivedBeforeID = pycGH.get(index).getPartnerID();
						else
							pyPartnerReceivedAfterID = pycGH.get(index).getPartnerID();
					}
				
					index++;
				}
				
				//create a new pyPartnerPerformance object if either the AssignedID, DelliveredID, or the Received
				//From ID is an actual partner ID and not -1 or 0 
				if(pyPartnerAssignedID > -1 || pyPartnerDeliveredID > -1 || 
					pyPartnerReceivedBeforeID > -1 || pyPartnerReceivedAfterID > -1)
				{
					pyPerformanceList.add(new PriorYearPartnerPerformance(pyc.getID(), gn, pyPartnerAssignedID,
										pyPartnerDeliveredID, pyPartnerReceivedBeforeID, pyPartnerReceivedAfterID));
				}
			}
			
		return pyPerformanceList;
	}
*/
	@Override
	void addObject(int year, String[] nextLine) 
	{
		//Get the child gift list for the year and add the object
		ChildGiftDBYear cgDBYear = childGiftDB.get(DBManager.offset(year));
		cgDBYear.add(new ONCChildGift(nextLine, false));	//false indicates this is not a cloned gift
	}

	@Override
	void createNewSeason(int newYear) 
	{
		//create a new child gift data base year for the year provided in the newYear parameter
		//The child gift db year list is initially empty prior to the selection of child gifts,
		//so all we do here is create a new ChildWishDBYear for the newYear and save it.
		ChildGiftDBYear childGiftDBYear = new ChildGiftDBYear(newYear);
		childGiftDB.add(childGiftDBYear);
		childGiftDBYear.setChanged(true);	//mark this db for persistent saving on the next save event
	}
	
	void convertToLinkedLists()
	{
		int[] years = {2012,2013,2014,2015,2016,2017,2018,2019,2020};
		int[] giftNumbers = {0, 1, 2};
		for(int year : years)
		{
			List<ONCChildGift> resultList = new ArrayList<ONCChildGift>();
			for(ONCChild c : childDB.getList(year))
			{	
				for(int gn : giftNumbers)
				{
					List<ONCChildGift> sourceList = new ArrayList<ONCChildGift>();
					for(ONCChildGift cg : childGiftDB.get(DBManager.offset(year)).getList())
						if(cg.getChildID() == c.getID() && cg.getGiftNumber() == gn)
							sourceList.add(cg);
					
					if(!sourceList.isEmpty())
					{
						//now that we have a source list, sort it chronologically
						Collections.sort(sourceList, new ChildGiftTimestampComparator());
						
						//now that we have a time ordered child gift list, add the prior and next id's
						int index = 0;
						while(index < sourceList.size())
						{
							sourceList.get(index).setPriorID(index == 0 ? -1 : sourceList.get(index-1).getID());
							sourceList.get(index).setNextID(index == sourceList.size()-1 ? -1 : sourceList.get(index+1).getID());
							resultList.add(sourceList.get(index));
							index++;
						}	
					}
				}
			}
			
			//now that we have a linked list, sort it by id and save it
			Collections.sort(resultList, new ChildGiftIDComparator());
			
			//save it to a new file
			String[] header = {"Child Gift ID", "Child ID", "Gift ID", "Detail",
		 			"Gift #", "Restrictions", "Status","Changed By", "Time Stamp",
		 			"PartnerID", "Prior ID", "Next ID"};
			
			String path = String.format("%s/%dDB/ChildGiftDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(resultList, header, path);	
		}
	}
	
	private class ChildGiftDateChangedComparator implements Comparator<ONCChildGift>
	{
		@Override
		public int compare(ONCChildGift cw1, ONCChildGift cw2)
		{
			return cw1.getDateChanged().compareTo(cw2.getDateChanged());
		}
	}
	
	private class ChildGiftTimestampComparator implements Comparator<ONCChildGift>
	{
		@Override
		public int compare(ONCChildGift cw1, ONCChildGift cw2)
		{
			return cw1.getTimestamp().compareTo(cw2.getTimestamp());
		}
	}
	
	private class ChildGiftIDComparator implements Comparator<ONCChildGift>
	{
		@Override
		public int compare(ONCChildGift cw1, ONCChildGift cw2)
		{
			if(cw1.getID() < cw2.getID())
				return -1;
			else if(cw1.getID() == cw2.getID())
				return 0;
			else
				return 1;
		}
	}
	
	private class ChildGiftDBYear extends ServerDBYear
    {
    		private List<ONCChildGift> cgList;
    	
    		ChildGiftDBYear(int year)
    		{
    			super();
    			cgList = new ArrayList<ONCChildGift>();
    		}
    	
    		//getters
    		List<ONCChildGift> getList() { return cgList; }
    	
    		void add(ONCChildGift addedGift) { cgList.add(addedGift); }
    }

	@Override
	void save(int year)
	{
		String[] header = {"Child Gift ID", "Child ID", "Gift ID", "Detail",
	 			"Gift #", "Restrictions", "Status","Changed By", "Time Stamp",
	 			"PartnerID", "Prior ID", "Next ID"};
		
		ChildGiftDBYear gwDBYear = childGiftDB.get(DBManager.offset(year));
		if(gwDBYear.isUnsaved())
		{
			String path = String.format("%s/%dDB/ChildGiftDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(gwDBYear.getList(), header, path);
			gwDBYear.setChanged(false);
		}
	}
}
