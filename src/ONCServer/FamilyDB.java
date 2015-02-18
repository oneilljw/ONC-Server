package ONCServer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import OurNeighborsChild.ONCChild;
import OurNeighborsChild.ONCChildWish;
import OurNeighborsChild.ONCDelivery;
import OurNeighborsChild.ONCFamily;

public class FamilyDB extends ONCServerDB
{
	private static final int FAMILYDB_HEADER_LENGTH = 38;
	
	private static final int FAMILY_STATUS_UNVERIFIED = 0;
	private static final int FAMILY_STATUS_INFO_VERIFIED = 1;
	private static final int FAMILY_STATUS_GIFTS_SELECTED = 2;
	private static final int FAMILY_STATUS_GIFTS_RECEIVED = 3;
	private static final int FAMILY_STATUS_GIFTS_VERIFIED = 4;
	
	private static final int NUMBER_OF_WISHES_PER_CHILD = 3;
	
	private static List<FamilyDBYear> familyDB;
	private static FamilyDB instance = null;
//	private static List<int[]> oncNumRanges;
	
	private static ServerChildDB childDB;
	private static ServerChildWishDB childwishDB;
	
	private static ClientManager clientMgr;
	
	//THIS IS TEMPORARY  FOR 2015 - NEED TO HAVE ONC NUM RANGES GENERATED AUTOMATICALLY
	int[] oncnumRegionRanges = {1000,100,124,142,234,444,454,479,514,587,619,643,650,656,662,
								671,791,817,941,944,949,954,966,990};
	
	private FamilyDB() throws FileNotFoundException, IOException
	{
		//create the family data bases for the years of ONC Server operation starting with 2012
//		oncNumRanges = new ArrayList<int[]>();

		familyDB = new ArrayList<FamilyDBYear>();
		
		//populate the family data base for the last TOTAL_YEARS from persistent store
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the family list for year
			FamilyDBYear fDBYear = new FamilyDBYear(year);
			
			//add the family list for the year to the db
			familyDB.add(fDBYear);
			
			//import the families from persistent store
			importDB(year, String.format("%s/%dDB/FamilyDB.csv",
					System.getProperty("user.dir"),
						year), "FamilyDB", FAMILYDB_HEADER_LENGTH);
			
			//set the next id
			fDBYear.setNextID(getNextID(fDBYear.getList()));
		}

		//set references to associated data bases
		childDB = ServerChildDB.getInstance();
		childwishDB = ServerChildWishDB.getInstance();
		
		clientMgr = ClientManager.getInstance();
	}
	
	public static FamilyDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new FamilyDB();

		return instance;
	}
		
	String getFamilies(int year)
	{
		Gson gson = new Gson();
		Type listOfFamilies = new TypeToken<ArrayList<ONCFamily>>(){}.getType();
		
		String response = gson.toJson(familyDB.get(year-BASE_YEAR).getList(), listOfFamilies);
		return response;	
	}
	
	String update(int year, String familyjson, boolean bAutoAssign)
	{
		//Create a family object for the updated family
		Gson gson = new Gson();
		ONCFamily updatedFamily = gson.fromJson(familyjson, ONCFamily.class);
		
		//Find the position for the current family being replaced
		FamilyDBYear fDBYear = familyDB.get(year - BASE_YEAR);
		List<ONCFamily> fAL = fDBYear.getList();
		int index = 0;
		while(index < fAL.size() && fAL.get(index).getID() != updatedFamily.getID())
			index++;
		
		//replace the current family object with the update. First, check for address change.
		//if address has changed, update the region. 
		if(index < fAL.size())
		{
			ONCFamily currFam = fAL.get(index);
			
			//check if the address has changed and a region update check is required
			if(!currFam.getHouseNum().equals(updatedFamily.getHouseNum()) ||
				!currFam.getStreet().equals(updatedFamily.getStreet()) ||
				 !currFam.getCity().equals(updatedFamily.getCity()) ||
				  !currFam.getZipCode().equals(updatedFamily.getZipCode()))
			{
//				System.out.println(String.format("FamilyDB - update: region change, old region is %d", currFam.getRegion()));
				updateRegion(updatedFamily);	
			}
			
			//check if the update is requesting automatic assignment of an ONC family number
			//can only auto assign if ONC Number is not number and is not "DEL" and the
			//region is valid
			if(bAutoAssign && !updatedFamily.getONCNum().equals("DEL") && updatedFamily.getRegion() != 0 &&
						  !Character.isDigit(updatedFamily.getONCNum().charAt(0)))
			{
				updatedFamily.setONCNum(generateONCNumber(year, updatedFamily.getRegion()));
			}
			
			fAL.set(index, updatedFamily);
			fDBYear.setChanged(true);
			return "UPDATED_FAMILY" + gson.toJson(updatedFamily, ONCFamily.class);
		}
		else
			return "UPDATE_FAILED";
	}
	
	int updateRegion(ONCFamily updatedFamily)
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
			reg = regionDB.getRegionMatch(updatedFamily.getHouseNum(), updatedFamily.getStreet());
			updatedFamily.setRegion(reg);
		}
		
		return reg;
	}
	
	@Override
	String add(int year, String json)
	{
		//Create a family object for the add family request
		Gson gson = new Gson();
		ONCFamily addedFam = gson.fromJson(json, ONCFamily.class);
		
		if(addedFam != null)
		{
			//get the family data base for the correct year
			FamilyDBYear fDBYear = familyDB.get(year - BASE_YEAR);
			
			//set region for family
			int region = updateRegion(addedFam);
			addedFam.setRegion(region);
		
			//create the ONC number
			String oncNum = generateONCNumber(year, region);
			addedFam.setONCNum(oncNum);
			
			//set the new ID for the added family
			addedFam.setID(fDBYear.getNextID());
			
			//add to the family data base
			fDBYear.add(addedFam);
			fDBYear.setChanged(true);
		
			//return the new family
			return "ADDED_FAMILY" + gson.toJson(addedFam, ONCFamily.class);
		}
		else
			return "ADD_FAMILY_FAILED";
	}
	
	String getFamily(int year, String zFamID)
	{
		int oncID = Integer.parseInt(zFamID);
		List<ONCFamily> fAL = familyDB.get(year-BASE_YEAR).getList();
		
		int index = 0;	
		while(index < fAL.size() && fAL.get(index).getID() != oncID)
			index++;
		
		if(index < fAL.size())
		{
			Gson gson = new Gson();
			String familyjson = gson.toJson(fAL.get(index), ONCFamily.class);
			
			return "FAMILY" + familyjson;
		}
		else
			return "FAMILY_NOT_FOUND";
	}
	
	ONCFamily getFamily(int year, int famID)
	{
		List<ONCFamily> fAL = familyDB.get(year-BASE_YEAR).getList();
		int index = 0;	
		while(index < fAL.size() && fAL.get(index).getID() != famID)
			index++;
		
		if(index < fAL.size())
			return fAL.get(index);
		else
			return null;
	}
	
	void checkFamilyStatusOnWishStatusChange(int year, int childid)
	{
		int famID = childDB.getChildsFamilyID(year, childid);
		
		ONCFamily fam = getFamily(year, famID);
		
	    //determine the proper family status for the family the child is in 	
	    int newStatus = getLowestFamilyStatus(year, famID);
	   
	    //if family status has changed, update the data base and notify clients
	    if(newStatus != fam.getFamilyStatus())
	    {
	    	fam.setFamilyStatus(newStatus);
	    	familyDB.get(year - BASE_YEAR).setChanged(true);
	    	
	    	Gson gson = new Gson();
	    	String change = "UPDATED_FAMILY" + gson.toJson(fam, ONCFamily.class);
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
	int getLowestFamilyStatus(int year, int famid)
	{
		//This matrix correlates a child wish status to the family status.
		int[] wishstatusmatrix = {FAMILY_STATUS_UNVERIFIED,FAMILY_STATUS_INFO_VERIFIED,
								 FAMILY_STATUS_GIFTS_SELECTED, FAMILY_STATUS_GIFTS_SELECTED,
								 FAMILY_STATUS_GIFTS_RECEIVED, FAMILY_STATUS_GIFTS_RECEIVED,
								 FAMILY_STATUS_GIFTS_VERIFIED};
			
		//Check for all gifts selected
		int lowestfamstatus = FAMILY_STATUS_GIFTS_VERIFIED;
		for(ONCChild c:childDB.getChildList(year, famid))
		{
			for(int wn=0; wn< NUMBER_OF_WISHES_PER_CHILD; wn++)
			{
				ONCChildWish cw = childwishDB.getWish(year, c.getChildWishID(wn));
				
				//if cw is null, it means that the wish doesn't exist yet. If that's the case, 
				//set the status to the lowest status possible as if the wish existed
				int childwishstatus = 1;	//Lowest possible child wish status
				if(cw != null)
					childwishstatus = childwishDB.getWish(year, c.getChildWishID(wn)).getChildWishStatus();
					
				if(wishstatusmatrix[childwishstatus] < lowestfamstatus)
					lowestfamstatus = wishstatusmatrix[childwishstatus];
			}
		}
			
		return lowestfamstatus;
	}
	
	void updateFamilyDelivery(int year, ONCDelivery addedDelivery)
	{
		//find the family
		FamilyDBYear famDBYear = familyDB.get(year - BASE_YEAR);
		ONCFamily fam = getFamily(year, addedDelivery.getFamID());
		
		//update the delivery ID and delivery status
		fam.setDeliveryID(addedDelivery.getID());
		fam.setDeliveryStatus(addedDelivery.getdStatus());
		famDBYear.setChanged(true);
		
		//notify in year clients of change
		Gson gson = new Gson();
    	String change = "UPDATED_FAMILY" + gson.toJson(fam, ONCFamily.class);
    	clientMgr.notifyAllInYearClients(year, change);	//null to notify all clients	
	}

	void addObject(int year, String[] nextLine)
	{
		FamilyDBYear famDBYear = familyDB.get(year - BASE_YEAR);

		Calendar date_changed = Calendar.getInstance();	//No date_changed in ONCFamily yet
		if(!nextLine[6].isEmpty())
			date_changed.setTimeInMillis(Long.parseLong(nextLine[6]));
			
		famDBYear.add(new ONCFamily(nextLine));
	}
	
	void save(int year)
	{
		String[] header = {"ONC ID", "ONCNum", "Region", "ODB Family #", "Batch #", "DNS Code", "Family Status", "Delivery Status",
				"Speak English?","Language if No", "Caller", "Notes", "Delivery Instructions",
				"Client Family", "First Name", "Last Name", "House #", "Street", "Unit #", "City", "Zip Code",
				"Substitute Delivery Address", "All Phone #'s", "Home Phone", "Other Phone", "Family Email", 
				"ODB Details", "Children Names", "Schools", "ODB WishList",
				"Adopted For", "Agent ID", "Delivery ID", "# of Bags", "# of Large Items", 
				"Stoplight Pos", "Stoplight Mssg", "Stopligh C/B"};
		
		FamilyDBYear fDBYear = familyDB.get(year - BASE_YEAR);
		if(fDBYear.isUnsaved())
			
		{
//			System.out.println(String.format("FamilyDB saveDB - Saving Family DB"));
			String path = String.format("%s/%dDB/FamilyDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(fDBYear.getList(), header, path);
			fDBYear.setChanged(false);
		}
	}
	
	List<ONCFamily> getList(int year)
	{
		return familyDB.get(year-BASE_YEAR).getList();
	}

	@Override
	void createNewYear(int newYear)
	{
		//create a new family data base year for the year provided in the newYear parameter
		//The family db year is initially empty prior to the import of families, so all we
		//do here is create a new FamilyDBYear for the newYear and save it.
		FamilyDBYear famDBYear = new FamilyDBYear(newYear);
		familyDB.add(famDBYear);
		famDBYear.setChanged(true);	//mark this db for persistent saving on the next save event	
	}
	
	 /******************************************************************************************
     * This method automatically generates an ONC Number for family that does not have an ONC
     * number already assigned. The method uses the integer region number passed (after checking
     * to see if it is in the valid range) and indexes into the oncnumRegionRanges array to get
     * the starting ONC number for the region. It then queries the family array to see if that
     * number is already in use. If it's in use, it goes to the next number to check again. It 
     * continues until it finds an unused number or reaches the end of the range for that region.
     * The first unused ONC number in the range is returned. If the end of the range is reached 
     * and all numbers have been assigned, it will display an error dialog and after the user
     * acknowledges the error, it will return string "OOR" for out of range.
     * If the region isn't valid, it will complain and then return the string "RNV" for region
     * not valid 
     * @param region
     * @return
     ********************************************************************************************/
    String generateONCNumber(int year, int region)
    {
    	String oncNum = null;
    	//Verify region number is valid. If it's not return an error
    	RegionDB regionDB = null;
		try {
			regionDB = RegionDB.getInstance(null);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	//assume RegionDB already created
    	 
    	if(region == 0)		//Don't assign numbers without known valid region addresses
    		oncNum = "NNA";
    	else if(regionDB != null && regionDB.isRegionValid(region))
    	{
    		int start = oncnumRegionRanges[region];
    		int	end = oncnumRegionRanges[(region+1) % regionDB.getNumberOfRegions()];
    		
    		String searchstring = Integer.toString(start);
    		while(start < end && searchForONCNumber(year, searchstring) != -1)
    			searchstring = Integer.toString(++start);
    		
    		if(start==end)
    		{
    			oncNum = "OOR";		//Not enough size in range
//    			System.out.println(String.format("ERROR: Too many families in region %d," + 
//    					", can't automatically assign an ONC Nubmer", region));
    		}
    		else
    			oncNum = Integer.toString(start);	
    	}
    	else
    	{
//   		System.out.println(String.format("ERROR: ONC Region Invalid: Region %d is not in " +
//					"the vaild range", region));
    		oncNum = "RNV";
    	}	
    	
    	return oncNum;
    }
    
    int searchForONCNumber(int year, String oncnum)
    {
    	List<ONCFamily> oncFamAL = familyDB.get(year-BASE_YEAR).getList();
    	
    	int index = 0;
    	while(index < oncFamAL.size() && !oncnum.equals(oncFamAL.get(index).getONCNum()))
    		index++;
    	
    	return index == oncFamAL.size() ? -1 : index;   		
    }
    
    private class FamilyDBYear extends ServerDBYear
    {
    	private List<ONCFamily> fList;
    	
    	FamilyDBYear(int year)
    	{
    		super();
    		fList = new ArrayList<ONCFamily>();
    	}
    	
    	//getters
    	List<ONCFamily> getList() { return fList; }
    	
    	void add(ONCFamily addedFamily) { fList.add(addedFamily); }
    }
    
    int getDelAttemptedCounts(int year, String drvNum)
    {
    	//get family data base for the year
    			ServerDeliveryDB deliveryDB = null;
    			try {
    				deliveryDB = ServerDeliveryDB.getInstance();
    			} catch (FileNotFoundException e1) {
    				// TODO Auto-generated catch block
    				e1.printStackTrace();
    			} catch (IOException e1) {
    				// TODO Auto-generated catch block
    				e1.printStackTrace();
    			}
    			
    	int delCount = 0;
    	for(ONCFamily f:familyDB.get(year-BASE_YEAR).getList())
    	{
    		if(f.getDeliveryID() > -1 && f.getDeliveryStatus() >= 3)
    		{
    			//get delivery for family
    			ONCDelivery del = deliveryDB.getDelivery(year, f.getDeliveryID());
    			if(del != null && del.getdDelBy().equals(drvNum))
    				delCount++;
    		}
    	}
    	
    	return delCount;
    }
}
