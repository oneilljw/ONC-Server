package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import ourneighborschild.Address;
import ourneighborschild.DataChange;
import ourneighborschild.Organization;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class PartnerDB extends ONCServerDB
{
	private static final int ORGANIZATION_DB_HEADER_LENGTH = 33;
	private static final int STATUS_CONFIRMED = 5;
	
	private static List<PartnerDBYear> partnerDB;
	
//	private static List<List<Organization>> orgAL;
	private static PartnerDB instance = null;
//	private static List<Integer> nextID;
	
	private PartnerDB() throws FileNotFoundException, IOException
	{
		//create the partner data bases for TOTAL_YEARS number of years
		partnerDB = new ArrayList<PartnerDBYear>();
		
//		orgAL = new ArrayList<List<Organization>>();
//		nextID = new ArrayList<Integer>();
						
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the partner list for year
			PartnerDBYear partnerDBYear = new PartnerDBYear(year);
			
			//add the list for the year to the db
			partnerDB.add(partnerDBYear);
					
			importDB(year, String.format("%s/%dDB/OrgDB.csv",
						System.getProperty("user.dir"),
							year), "Partner DB", ORGANIZATION_DB_HEADER_LENGTH);
			
			//for partners, the leading 4 digits are the year added. So, must account
			//for that when determining the next ID number to assign
			int nextID = getNextID(partnerDBYear.getList());
			int nextIDinYear = year * 1000;
			if(nextID < nextIDinYear)
			{
				partnerDBYear.setNextID(nextIDinYear);
//				System.out.println(String.format("For year %d, next ID is %d, highest found was %d", 
//													year, nextIDinYear, nextID));
			}
			else
			{
				partnerDBYear.setNextID(nextID);
//				System.out.println(String.format("For year %d, next ID is %d", year, nextID));
			}
		}
	}
	
	public static PartnerDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new PartnerDB();
		
		return instance;
	}
		
	String getPartners(int year)
	{
		Gson gson = new Gson();
		Type listOfPartners = new TypeToken<ArrayList<Organization>>(){}.getType();
		
		String response = gson.toJson(partnerDB.get(year - BASE_YEAR).getList(), listOfPartners);
		return response;	
	}
	
	String getPartner(int year, String zID)
	{
		int id = Integer.parseInt(zID);
		int index = 0;
		
		List<Organization> orgAL = partnerDB.get(year-BASE_YEAR).getList();
		
		while(index < orgAL.size() && orgAL.get(index).getID() != id)
			index++;
		
		if(index < orgAL.size())
		{
			Gson gson = new Gson();
			String partnerjson = gson.toJson(orgAL.get(index), Organization.class);
			
			return "PARTNER" + partnerjson;
		}
		else
			return "PARTNER_NOT_FOUND";
	}
	
	Organization getPartner(int year, int partID)
	{
		List<Organization> oAL = partnerDB.get(year - BASE_YEAR).getList();
		
		int index = 0;
		while(index < oAL.size() && oAL.get(index).getID() != partID)
			index++;
		
		if(index < oAL.size())
		{	
			return oAL.get(index);
		}
		else
			return null;
	}
	
	String update(int year, String json)
	{
		//Create a organization object for the updated partner
		Gson gson = new Gson();
		Organization reqOrg = gson.fromJson(json, Organization.class);
		
		//Find the position for the current family being replaced
		PartnerDBYear partnerDBYear = partnerDB.get(year - BASE_YEAR);
		List<Organization> oAL = partnerDBYear.getList();
		int index = 0;
		while(index < oAL.size() && oAL.get(index).getID() != reqOrg.getID())
			index++;
		
		//If partner is located, replace the current partner with the update. Check to 
		//ensure the partner status isn't confirmed with gifts assigned. If so, deny the change. 
		if(index == oAL.size() || (reqOrg.getStatus() != STATUS_CONFIRMED && 
									oAL.get(index).getNumberOfOrnamentsAssigned() > 0)) 
		{
			
			return "UPDATE_FAILED";
		}
		else
		{
			Organization currOrg = oAL.get(index);
			//check if partner address has changed and a region update check is required
			if(currOrg.getStreetnum() != reqOrg.getStreetnum() ||
				!currOrg.getStreetname().equals(reqOrg.getStreetname()) ||
				 !currOrg.getCity().equals(reqOrg.getCity()) ||
				  !currOrg.getZipcode().equals(reqOrg.getZipcode()))
			{
//				System.out.println(String.format("PartnerDB - update: region change, old region is %d", currOrg.getRegion()));
				updateRegion(reqOrg);	
			}
			oAL.set(index, reqOrg);
			partnerDBYear.setChanged(true);
			return "UPDATED_PARTNER" + gson.toJson(reqOrg, Organization.class);
		}
	}
	
	int updateRegion(Organization updatedOrg)
	{
		int reg = 0; //initialize return value to no region found
		
		//address is new or has changed, update the region
		RegionDB regionDB = null;
		try {
			regionDB = RegionDB.getInstance(null);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(regionDB != null)
		{
			reg = RegionDB.searchForRegionMatch(new Address(Integer.toString(updatedOrg.getStreetnum()),
											updatedOrg.getStreetname(), "",  updatedOrg.getCity(),
											 updatedOrg.getZipcode()));
			updatedOrg.setRegion(reg);
		}
		
		return reg;
	}
	
	@Override
	String add(int year, String json)
	{
		//Create a organization object for the updated partner
		Gson gson = new Gson();
		Organization addedPartner = gson.fromJson(json, Organization.class);
	
		//set the new ID for the catalog wish
		PartnerDBYear partnerDBYear = partnerDB.get(year - BASE_YEAR);
		addedPartner.setID(partnerDBYear.getNextID());
		
		//set the region for the new partner
		ClientManager clientMgr = ClientManager.getInstance();
		RegionDB regionDB = null;
		try {
			regionDB = RegionDB.getInstance(clientMgr.getAppIcon());
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(regionDB != null)
			addedPartner.setRegion(RegionDB.searchForRegionMatch(new Address(Integer.toString(addedPartner.getStreetnum()), 
															addedPartner.getStreetname(), "", addedPartner.getCity(),
															addedPartner.getZipcode())));
		else
			addedPartner.setRegion(0);
		
		//add the partner to the proper database
		partnerDBYear.add(addedPartner);
		partnerDBYear.setChanged(true);
		
		return "ADDED_PARTNER" + gson.toJson(addedPartner, Organization.class);
	}
	
	String delete(int year, String json)
	{
		//Create a organization object for the updated partner
		Gson gson = new Gson();
		Organization reqDelPartner = gson.fromJson(json, Organization.class);
	
		//find the partner in the db
		PartnerDBYear partnerDBYear = partnerDB.get(year - BASE_YEAR);
		List<Organization> oAL = partnerDBYear.getList();
		int index = 0;
		while(index < oAL.size() && oAL.get(index).getID() != reqDelPartner.getID())
			index++;
		
		//partner must be present and have no ornaments assigned to be deleted
		if(index < oAL.size() && oAL.get(index).getStatus() != STATUS_CONFIRMED &&
				oAL.get(index).getNumberOfOrnamentsAssigned() == 0)
		{
			oAL.remove(index);
			partnerDBYear.setChanged(true);
			return "DELETED_PARTNER" + json;
		}
		else
			return "DELETE_PARTNER_FAILED" + json;
	}

	void updateGiftAssignees(int year, int oldPartnerID, int newPartnerID)
	{
		PartnerDBYear partnerDBYear = partnerDB.get(year - BASE_YEAR);
		
		//Find the the current partner &  decrement gift count if found
		if(oldPartnerID > 0)
		{
			Organization oldPartner = getPartner(year, oldPartnerID);
			if(oldPartner != null)
			{
				oldPartner.decrementOrnAssigned();
				partnerDBYear.setChanged(true);
			}
		}
	
		if(newPartnerID > 0)
		{
			//Find the the current partner &  increment gift count if found
			Organization newPartner = getPartner(year, newPartnerID);
			if(newPartner != null)
			{
				newPartner.incrementOrnAssigned();
				partnerDBYear.setChanged(true);
			}
		}	
	}
	
	String updateGiftCounts(int year, String json)
	{
		//Create a change object for the updated counts
		Gson gson = new Gson();
		DataChange change = gson.fromJson(json, DataChange.class);
		
		PartnerDBYear partnerDBYear = partnerDB.get(year - BASE_YEAR);
		List<Organization> oAL = partnerDBYear.getList();
		
		//Find the the current partner being decremented
		int index = 0;
		while(index < oAL.size() && oAL.get(index).getID() != change.getOldData())
			index++;
		
		//Decrement the gift assigned count for the partner being replaced
		if(index < oAL.size())
		{
			oAL.get(index).decrementOrnAssigned();
			partnerDBYear.setChanged(true);
		}
		
		//Find the the current partner being incremented
		index = 0;
		while(index < oAL.size() && oAL.get(index).getID() != change.getNewData())
			index++;
				
		//Increment the gift assigned count for the partner being replaced
		if(index < oAL.size())
		{
			oAL.get(index).incrementOrnAssigned();
			partnerDBYear.setChanged(true);
		}
		
		return "WISH_PARTNER_CHANGED" + json;
	}
	
	void decrementGiftCount(int year, int partnerID)
	{
		PartnerDBYear partnerDBYear = partnerDB.get(year - BASE_YEAR);
		List<Organization> oAL = partnerDBYear.getList();
		int index=0;
		while(index < oAL.size() && oAL.get(index).getID() != partnerID)
			index ++;
		
		//if partner was found, decrment the count. If not found, ignore the request
		if(index < oAL.size())
		{
			oAL.get(index).decrementOrnAssigned();
			partnerDBYear.setChanged(true);
		}
	}

	@Override
	void addObject(int year, String[] nextLine)
	{
		PartnerDBYear partnerDBYear = partnerDB.get(year - BASE_YEAR);
		partnerDBYear.add(new Organization(nextLine));
	}

	@Override
	void createNewYear(int newYear)
	{
		//create a new partner data base year for the year provided in the newYear parameter
		//Then copy the prior years partners to the newly created partner db year list.
		//Reset each partners status to NO_ACTION_YET
		//Mark the newly created WishCatlogDBYear for saving during the next save event
				
		//get a reference to the prior years wish catalog
		List<Organization> lyPartnerList = partnerDB.get(partnerDB.size()-1).getList();
				
		//create the new PartnerDBYear
		PartnerDBYear partnerDBYear = new PartnerDBYear(newYear);
		partnerDB.add(partnerDBYear);
				
		//add last years catalog wishes to the new years catalog
		for(Organization lyPartner : lyPartnerList)
		{
			Organization newPartner = new Organization(lyPartner);	//makes a copy of last year
			newPartner.setStatus(0);	//reset status to NO_ACTION_YET
			newPartner.setNumberOfOrnamentsRequested(0);	//reset requested to 0
			newPartner.setNumberOfOrnamentsAssigned(0);	//reset assigned to 0
			newPartner.setNumberOfOrnamentsReceived(0);	//reset received to 0
			newPartner.setPriorYearRequested(lyPartner.getNumberOfOrnamentsRequested());
			partnerDBYear.add(newPartner);
		}
		
		//set the nextID for the newly created WishCatalogDBYear
		partnerDBYear.setNextID(newYear*1000);	//all partner id's start with current year
		
		//determine the prior year performance for each partner
		determinePriorYearPerformance(newYear);
		
		//Mark the newly created WishCatlogDBYear for saving during the next save event
		partnerDBYear.setChanged(true);
	}
	/********************************************************************************************
	 * using prior year child wish data, set the prior year ornaments requested, assigned and received
	 * class variables for each partner
	 * @param newYear
	 * @param partnerDBYear
	 ******************************************************************************************/
	void determinePriorYearPerformance(int newYear)
	{
		//get the child wish data base reference
		ServerChildWishDB serverChildWishDB = null;
		try {
			serverChildWishDB = ServerChildWishDB.getInstance();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
		//iterate thru the list of prior year performance objects updating counts for each partner 
		//accordingly. The list contains one object for each wish from the prior year. Each object 
		//contains an assigned ID and a receivedID indicating which partner was responsible for
		//fulfilling the wish and who ONC actually received the fulfilled wish (gift) from
//		SimpleDateFormat sdf = new SimpleDateFormat("M:d:yyyy H:m:s");
		List<PriorYearPartnerPerformance> pyPartnerPerformanceList = serverChildWishDB.getPriorYearPartnerPerformanceList(newYear);
		for(PriorYearPartnerPerformance pyPerf: pyPartnerPerformanceList)
		{
			//find the partner the wish was assigned to and increment their prior year assigned count
			Organization wishAssigneePartner = getPartner(newYear, pyPerf.getPYPartnerWishAssigneeID());
			if(wishAssigneePartner != null)
				wishAssigneePartner.incrementPYAssigned();
			
			//find the partner the wish was received from and increment their prior year received count
			Organization wishReceivedPartner = getPartner(newYear, pyPerf.getPYPartnerWishReceivedID());
			if(wishReceivedPartner != null)
				wishReceivedPartner.incrementPYReceived();
		}
		
		//Mark the newly created PartnerDBYear for saving during the next save event
		PartnerDBYear partnerDBYear = partnerDB.get(newYear - BASE_YEAR);
		partnerDBYear.setChanged(true);
	}
	
	private class PartnerDBYear extends ServerDBYear
	{
		private List<Organization> pList;
	    	
	    PartnerDBYear(int year)
	    {
	    	super();
	    	pList = new ArrayList<Organization>();
	    }
	    	
	    	//getters
	    	List<Organization> getList() { return pList; }
	    	
	    	void add(Organization addedOrg) { pList.add(addedOrg); }
	}

	@Override
	void save(int year)
	{
		String[] header = {"Org ID", "Status", "Type", "Gift Collection","Name", "Orn Delivered",
				"Street #", "Street", "Unit", "City", "Zip", "Region", "Phone",
	 			"Orn Requested", "Orn Assigned", "Gifts Received", "Other",
	 			"Deliver To", "Special Notes",
	 			"Contact", "Contact Email", "Contact Phone",
	 			"Contact2", "Contact2 Email", "Contact2 Phone",
	 			"Time Stamp", "Changed By", "Stoplight Pos", "Stoplight Mssg", "Stoplight C/B",
	 			"Prior Year Requested",	"Prior Year Assigned", "Prior Year Received"};
		
		PartnerDBYear partnerDBYear = partnerDB.get(year - BASE_YEAR);
		if(partnerDBYear.isUnsaved())
		{
//			System.out.println(String.format("PartnerDB save() - Saving Partner DB"));
			String path = String.format("%s/%dDB/OrgDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(partnerDBYear.getList(),  header, path);
			partnerDBYear.setChanged(false);
		}
	}
}
