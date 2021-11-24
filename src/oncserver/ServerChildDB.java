package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import ourneighborschild.ONCChild;
import ourneighborschild.ONCChildGift;
import ourneighborschild.ONCWebChild;
import ourneighborschild.ONCPartner;
import ourneighborschild.ONCUser;
import ourneighborschild.WebChildWish;
import ourneighborschild.GiftStatus;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerChildDB extends ServerSeasonalDB
{
	private static final int CHILD_DB_HEADER_LENGTH = 8;
	public static final int NUMBER_GIFTS_PER_CHILD = 3;
	
	private static List<ChildDBYear> childDB;
	private static ServerChildDB instance = null;
	
	private ServerChildDB() throws FileNotFoundException, IOException
	{
		childDB = new ArrayList<ChildDBYear>();
				
		//populate the family data base for the last TOTAL_YEARS from persistent store
		for(int year = DBManager.getBaseSeason(); year < DBManager.getBaseSeason() + DBManager.getNumberOfYears(); year++)
		{
			//create the child dbYear for each year
			ChildDBYear cDBYear = new ChildDBYear(year);
					
			//add the list of children for the year to the dbYear object
			childDB.add(cDBYear);
					
			//import the children for the year from persistent store
			importDB(year, String.format("%s/%dDB/ChildDB.csv", System.getProperty("user.dir"), year),
											"Child DB", CHILD_DB_HEADER_LENGTH);
					
			//set the next id
			cDBYear.setNextID(getNextID(cDBYear.getList()));
		}
	}
	
	public static ServerChildDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerChildDB();
		
		return instance;
	}
	
	String getChildren(int year)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCChild>>(){}.getType();
			
		String response = gson.toJson(childDB.get(DBManager.offset(year)).getList(), listtype);
		
		return response;	
	}
	
	static HtmlResponse getChildrenInFamilyJSONP(int year, int famID, boolean bIncludeSchool, String callbackFunction)
	{		
		Gson gson = new Gson();
		Type listOfChildren = new TypeToken<ArrayList<ONCWebChild>>(){}.getType();
		
		List<ONCChild> searchList = childDB.get(DBManager.offset(year)).getList();
		ArrayList<ONCWebChild> responseList = new ArrayList<ONCWebChild>();
		
		for(ONCChild c: searchList)
			if(c.getFamID() == famID)
				responseList.add(new ONCWebChild(c, bIncludeSchool));
		
		String response = gson.toJson(responseList, listOfChildren);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	
	static HtmlResponse getChildWishesJSONP(int year, int childID, String callbackFunction)
	{	
		//get references to wish catalog, partner data base
		ServerGiftCatalog wishCatalog = null;
		ServerPartnerDB serverPartnerDB = null;
		ServerChildGiftDB childGiftDB = null;
		try 
		{
			wishCatalog = ServerGiftCatalog.getInstance();
			serverPartnerDB = ServerPartnerDB.getInstance();
			childGiftDB = ServerChildGiftDB.getInstance();
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
		
		Gson gson = new Gson();
		Type listOfWishes = new TypeToken<ArrayList<WebChildWish>>(){}.getType();
		
		//get the child
		List<ONCChild> searchList = childDB.get(DBManager.offset(year)).getList();
		ArrayList<WebChildWish> responseList = new ArrayList<WebChildWish>();
		
		for(ONCChild c: searchList)
			if(c.getID() == childID)
			{
				//found child, now create child gift list
				for(int wn=0; wn < 3; wn++)
				{
					ONCChildGift cg = childGiftDB.getCurrentChildGift(year, c.getID(), wn);
					//has the wish been created?
					if(cg == null)
					{
						responseList.add(new WebChildWish());	//not created
					}
					else		
					{
						int wishRestriction = cg.getIndicator();
						String[] restrictions = {" ", "*", "#"};
						String partner;
						int partnerID;
						if(cg.getPartnerID() < 1)
						{
							partner = "";
							partnerID = -1;
						}
						else
						{
							ONCPartner org = serverPartnerDB.getPartner(year,cg.getPartnerID());
							partner = org.getLastName();
							partnerID = org.getID();
						}
						
						responseList.add(new WebChildWish(wishCatalog.findGiftNameByID(year, cg.getCatalogGiftID()),
															cg.getCatalogGiftID(),
															cg.getDetail(), 
															restrictions[wishRestriction], 
															partner, partnerID, cg.getGiftStatus()));
					}
				}
			}
		
		
		String response = gson.toJson(responseList, listOfWishes);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	
	String add(int year, String childjson, ONCUser client)
	{
		//Create a child object for the updated child
		Gson gson = new Gson();
		ONCChild addedChild = gson.fromJson(childjson, ONCChild.class);
		
		//get the child data base for the year
		ChildDBYear cDBYear = childDB.get(DBManager.offset(year));
		
		//set the new ID for the added child
		addedChild.setID(cDBYear.getNextID());
		
		//set the prior year history id for the child
		addedChild.setPriorYearChildID(searchForPriorYearMatch(year, addedChild));
		
		//add the new child to the data base
		cDBYear.add(addedChild);
		cDBYear.setChanged(true);
		
		return "ADDED_CHILD" + gson.toJson(addedChild, ONCChild.class);
		
	}
	
	ONCChild add(int year, ONCChild addedChild)
	{
		//get the child data base for the year
		ChildDBYear cDBYear = childDB.get(DBManager.offset(year));
		
		//set the new ID for the added child
		int childID = cDBYear.getNextID();
		addedChild.setID(childID);
		
		//set the prior year history id for the child
		addedChild.setPriorYearChildID(searchForPriorYearMatch(year, addedChild));
		
		//add the new child to the data base
		cDBYear.add(addedChild);
		cDBYear.setChanged(true);
		
		return addedChild;
	}
	
	void searchForLastName(int year, String s, List<FamilyReference> resultAL)
    {	
		//create the child list for the year
		ChildDBYear childDBYear = childDB.get(DBManager.offset(year));
		List<ONCChild> childAL = childDBYear.getList();

		int lastFamIDAdded = -1;	//prevent searching for same family id twice in a row
    	for(ONCChild c: childAL)
    		if(c.getFamID() != lastFamIDAdded && c.getChildLastName().toLowerCase().contains(s.toLowerCase()))
    		{
    			//check to see that the family hasn't already been found
    			//if it hasn't, add it. To do this, need to get Fam Ref ID
    			String refNum = ServerFamilyDB.getFamilyRefNum(year, c.getFamID());
    			int index=0;
    			while(refNum != null && index < resultAL.size() &&
    					!resultAL.get(index).getReferenceID().equals(refNum))
    				index++;
    			
    			if(index == resultAL.size())
    			{
    				resultAL.add(new FamilyReference(refNum)); 
    				lastFamIDAdded = c.getFamID();
    			}
    		}	
    }
	
	int searchForPriorYearMatch(int year, ONCChild child)
	{
		PriorYearDB pyDB = null;
		try {
			pyDB = PriorYearDB.getInstance();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(pyDB != null)
		{
			return pyDB.searchForMatch(year, child);
//			System.out.println(String.format("ServerChildDB: addChild - new pycID: %d", pycID));
		}
		else
			return -1;
	}
	
	String deleteChild(int year, String childjson)
	{
		//Create a child object for the deleted child
		Gson gson = new Gson();
		ONCChild deletedChild = gson.fromJson(childjson, ONCChild.class);
		
		//find and remove the deleted child from the data base
		ChildDBYear childDBYear = childDB.get(DBManager.offset(year));
		List<ONCChild> cAL = childDBYear.getList();
		
		int index = 0;
		while(index < cAL.size() && cAL.get(index).getID() != deletedChild.getID())
			index++;
		
		if(index < cAL.size())
		{
			//child found in data base. Check for and decrement partner wish assignment counts
			//and remove wishes for the about to be deleted child
			ServerChildGiftDB cwDB = null;
			ServerPartnerDB serverPartnerDB = null;
			try 
			{
				cwDB = ServerChildGiftDB.getInstance();
				serverPartnerDB = ServerPartnerDB.getInstance();
				
				//decrement partner gift assignment counts
				for(int gn= 0; gn < NUMBER_GIFTS_PER_CHILD; gn++)
				{
					ONCChildGift cg = cwDB.getCurrentChildGift(year, deletedChild.getID(),gn);
					if(cg != null && cg.getGiftStatus() == GiftStatus.Assigned)	//does the gift exist?
					{
						//if gift has been assigned, but not yet delivered or any subsequent status,
						//decrement the partner assignee count
						serverPartnerDB.decrementGiftsAssignedCount(year, cg.getPartnerID(), true);
					}
				}
			} 
			catch (FileNotFoundException e) 
			{
				
			} 
			catch (IOException e) 
			{
				
			}
			
			//now that we've handled assignment counts, remove the child from the database
			cAL.remove(index);
			childDBYear.setChanged(true);
			return "DELETED_CHILD" + childjson;
		}
		else
			return "DELETE_FAILED";	
	}
	
	String update(int year, String childjson)
	{
		//Create a child object for the updated child
		Gson gson = new Gson();
		ONCChild updatedChild = gson.fromJson(childjson, ONCChild.class);
		
		//Find the position for the current child being replaced
		ChildDBYear childDBYear = childDB.get(DBManager.offset(year));
		List<ONCChild> cAL = childDBYear.getList();
		int index = 0;
		while(index < cAL.size() && cAL.get(index).getID() != updatedChild.getID())
			index++;
		
		//Replace the current child object with the update
		if(index < cAL.size())
		{
			ONCChild replChild = cAL.get(index);
//			System.out.println(String.format("repl DOB: %d", replChild.getChildDateOfBirth()));
//			System.out.println(String.format("updt DOB: %d", updatedChild.getChildDateOfBirth()));
//			
//			if(!replChild.getChildDOB().equals(updatedChild.getChildDOB()))
//				System.out.println(updatedChild.getChildDOB());
			
			//if the parameters that effect prior year child id search changed,
			//then conduct a search to attempt to match a prior year child
			if(!replChild.getChildLastName().equals(updatedChild.getChildLastName()) ||
				!replChild.getChildDOB().equals(updatedChild.getChildDOB()) ||
				!replChild.getChildGender().equals(updatedChild.getChildGender()))
			{
//				System.out.println("Searching for Prior Year Child");
				updatedChild.setPriorYearChildID(searchForPriorYearMatch(year, updatedChild));
			}
			
				cAL.set(index, updatedChild);
				childDBYear.setChanged(true);
				return "UPDATED_CHILD" + gson.toJson(updatedChild, ONCChild.class);
		}
		else
			return "UPDATE_FAILED";
	}
	
	/**************************************************************************************
	 * This method is called to update the wish id's of the child when a wish is changed.
	 * Each child object has three wishID fields that point to the three wishes for the
	 * child in the ChildWishDB. A value of -1 indicates no wish yet assigned. When a wish
	 * changes, the id corresponding to the changed wish number must be updated. 
	 * @param year - which year's data base is being changed
	 * @param addedWish - the child wish object that was changed
	 ************************************************************************************/
/*	
	ONCChild updateChildsWishID(int year, ONCChildGift addedWish)
	{
		//Find the child using the added wish child ID
		ChildDBYear childDBYear = childDB.get(DBManager.offset(year));
		List<ONCChild> cAL = childDBYear.getList();
		int index = 0;
		while(index < cAL.size() && cAL.get(index).getID() != addedWish.getChildID())
			index++;
		
		//Replace the current childwishID with the newly added wish ID
		if(index < cAL.size())
		{
			ONCChild c = cAL.get(index);
			c.setChildGiftID(addedWish.getID(), addedWish.getGiftNumber());
			childDBYear.setChanged(true);
			return c;
		}
		else
			return null;
	}
*/
	ONCChild getChild(int year, int childid)
	{
		List<ONCChild> cAL = childDB.get(DBManager.offset(year)).getList();
		
		int index=0;
		while(index < cAL.size() && cAL.get(index).getID() != childid)
			index++;
				
		return index < cAL.size() ? cAL.get(index) : null;
	}
	
	List<ONCChild> getChildList(int year, int famid)
	{
		List<ONCChild> cAL = childDB.get(DBManager.offset(year)).getList();
		ArrayList<ONCChild> fChildrenAL = new ArrayList<ONCChild>();
		
		for(ONCChild c:cAL)
			if(c.getFamID() == famid)
				fChildrenAL.add(c);
		
		return fChildrenAL;
	}
	
//	ONCChild getChild(int year, int childID)
//	{
//		List<ONCChild> cAL = childDB.get(DBManager.offset(year)).getList();
//		
//		int index = 0;
//		while(index < cAL.size() && cAL.get(index).getID() != childID)
//			index++;
//		
//		return index < cAL.size() ? cAL.get(index) : null;
//	}
	
	List<ONCWebChild> getWebChildList(int year, int famid, boolean bIncludeSchool)
	{
		ArrayList<ONCWebChild> fChildrenAL = new ArrayList<ONCWebChild>();
		
		for(ONCChild c:childDB.get(DBManager.offset(year)).getList())
			if(c.getFamID() == famid)
				fChildrenAL.add(new ONCWebChild(c, bIncludeSchool));
		
		return fChildrenAL;
	}
	
	int getChildsFamilyID(int year, int childid)
	{
		List<ONCChild> cAL = childDB.get(DBManager.offset(year)).getList();
		
		int index = 0;
		while(index < cAL.size() && cAL.get(index).getID() != childid)
			index++;
		
		if(index < cAL.size())
			return cAL.get(index).getFamID();
		else
			return -1;	
	}

	int getNumChildren(int year) { return childDB.get(DBManager.offset(year)).getList().size(); }
	
	List<ONCChild> getList(int year)
	{
		return childDB.get(DBManager.offset(year)).getList();
	}

	@Override
	void addObject(int year, String[] nextLine)
	{
		ChildDBYear childDBYear = childDB.get(DBManager.offset(year));
		childDBYear.add(new ONCChild(year, nextLine));	
	}

	@Override
	void createNewSeason(int newYear)
	{
		//create a new child data base year for the year provided in the newYear parameter
		//The child db year list is initially empty prior to the import of families, so all we
		//do here is create a new ChildDBYear for the newYear and save it.
		ChildDBYear childDBYear = new ChildDBYear(newYear);
		childDB.add(childDBYear);
		childDBYear.setChanged(true);	//mark this db for persistent saving on the next save event	
	}
	
	private class ChildDBYear extends ServerDBYear
    {
		private List<ONCChild> cList;
	
		ChildDBYear(int year)
		{
			super();
			cList = new ArrayList<ONCChild>();
		}
	
		//getters
		List<ONCChild> getList() { return cList; }
	
		void add(ONCChild addedChild) { cList.add(addedChild); }
    }

	@Override
	void save(int year)
	{
		ChildDBYear cDBYear = childDB.get(DBManager.offset(year));
		if(cDBYear.isUnsaved())
		{
			String[] header = {"Child ID", "Family ID", "First Name", "Last Name",
		 						"Gender", "DOB", "School","Prior Year Child ID"};
			
//			System.out.println(String.format("ServerChildDB save() - Saving Server Child DB"));
			String path = String.format("%s/%dDB/ChildDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(cDBYear.getList(),  header, path);
			cDBYear.setChanged(false);
		}
	}
	
/*	
	void updateDOBs()
	{
		//get time zone at GMT
		TimeZone localTZ = TimeZone.getDefault();
		
		//for each year in the data base, update the dob's
		for(int year=2012; year < 2014; year++)
		{
			ChildDBYear cDBYear = childDB.get(year - BASE_YEAR);
			System.out.println(String.format("Processing %d Child Year DB", year));
			
			//for each child, update their dob by calculating their dob time zone
			//offset to GMT and adding it to their local time zone DOB that was previously saved
			for(ONCChild c : cDBYear.getList())
			{
				long currDOB = c.getChildDateOfBirth();
				int gmtOffset = localTZ.getOffset(currDOB);
				c.setChildDateOfBirth(currDOB + gmtOffset);
			}
		
			//mark the db for saving to persistent store
			cDBYear.setChanged(true);
		}
	}
*/	
}
