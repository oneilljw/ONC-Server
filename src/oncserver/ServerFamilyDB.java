package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ourneighborschild.Address;
import ourneighborschild.ONCChild;
import ourneighborschild.ONCChildWish;
import ourneighborschild.ONCDelivery;
import ourneighborschild.ONCFamily;
import ourneighborschild.ONCMeal;
import ourneighborschild.ONCUser;
import ourneighborschild.ONCWebsiteFamily;
import ourneighborschild.ONCWebsiteFamilyExtended;
import ourneighborschild.WishStatus;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerFamilyDB extends ONCServerDB
{
	private static final int FAMILYDB_HEADER_LENGTH = 42;
	
	private static final int FAMILY_STATUS_UNVERIFIED = 0;
	private static final int FAMILY_STATUS_INFO_VERIFIED = 1;
	private static final int FAMILY_STATUS_GIFTS_SELECTED = 2;
	private static final int FAMILY_STATUS_GIFTS_RECEIVED = 3;
	private static final int FAMILY_STATUS_GIFTS_VERIFIED = 4;
	
	private static final int FAMILY_STOPLIGHT_RED = 2;
	
	private static final int NUMBER_OF_WISHES_PER_CHILD = 3;
	private static final String GIFT_CARD_WISH_NAME = "Gift Card";
	
	private static List<FamilyDBYear> familyDB;
	private static ServerFamilyDB instance = null;
	private static int highestRefNum;
//	private static List<int[]> oncNumRanges;
	
	private static ServerChildDB childDB;
//	private static ServerChildWishDB childwishDB;
	
	private static ClientManager clientMgr;
	
	//THIS IS A TEMPORARY HACK FOR 2015 - NEED TO HAVE ONC NUM RANGES GENERATED AUTOMATICALLY
	int[] oncnumRegionRanges = {1025,100,124,142,234,444,454,479,514,587,619,643,650,656,662,
								671,791,817,941,944,949,954,966,990,1000,1001,1002};
	
	private ServerFamilyDB() throws FileNotFoundException, IOException
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
		
		//set the reference number
		highestRefNum = initializeHighestReferenceNumber();
		
		//set references to associated data bases
		childDB = ServerChildDB.getInstance();
//		childwishDB = ServerChildWishDB.getInstance();
		
		clientMgr = ClientManager.getInstance();
	}
	
	public static ServerFamilyDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerFamilyDB();

		return instance;
	}
		
	String getFamilies(int year)
	{
		Gson gson = new Gson();
		Type listOfFamilies = new TypeToken<ArrayList<ONCFamily>>(){}.getType();
		
		String response = gson.toJson(familyDB.get(year-BASE_YEAR).getList(), listOfFamilies);
		return response;	
	}
	
	static HtmlResponse getFamiliesJSONP(int year, String callbackFunction)
	{		
		Gson gson = new Gson();
		Type listOfWebsiteFamilies = new TypeToken<ArrayList<ONCWebsiteFamily>>(){}.getType();
		
		List<ONCFamily> searchList = familyDB.get(year-BASE_YEAR).getList();
		ArrayList<ONCWebsiteFamily> responseList = new ArrayList<ONCWebsiteFamily>();
		
		for(int i=0; i<searchList.size(); i++)
			responseList.add(new ONCWebsiteFamily(searchList.get(i)));
		
		//sort the list by HoH last name
		Collections.sort(responseList, new ONCWebsiteFamilyLNComparator());
		
		String response = gson.toJson(responseList, listOfWebsiteFamilies);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HTTPCode.Ok);		
	}
	
	static HtmlResponse getFamilyReferencesJSONP(int year, String callbackFunction)
	{		
		Gson gson = new Gson();
		Type listOfFamilyReferences = new TypeToken<ArrayList<FamilyReference>>(){}.getType();
		
		List<ONCFamily> searchList = familyDB.get(year-BASE_YEAR).getList();
		ArrayList<FamilyReference> responseList = new ArrayList<FamilyReference>();
		
		//sort the search list by ONC Number
		Collections.sort(searchList, new ONCFamilyONCNumComparator());
		
		for(int i=0; i<searchList.size(); i++)
			responseList.add(new FamilyReference(searchList.get(i).getODBFamilyNum()));
		
		String response = gson.toJson(responseList, listOfFamilyReferences);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HTTPCode.Ok);		
	}
	
	static HtmlResponse searchForFamilyReferencesJSONP(int year, String s, String callbackFunction)
	{
    	
		Gson gson = new Gson();
		Type listOfFamilyReferences = new TypeToken<ArrayList<FamilyReference>>(){}.getType();
		
    	List<ONCFamily> oncFamAL = familyDB.get(year-BASE_YEAR).getList();
    	List<FamilyReference> resultList = new ArrayList<FamilyReference>();
    	
		//Determine the type of search based on characteristics of search string
		if(s.matches("-?\\d+(\\.\\d+)?") && s.length() < 5)
		{
			for(ONCFamily f: oncFamAL)
	    		if(s.equals(f.getONCNum()))
	    			resultList.add(new FamilyReference(f.getODBFamilyNum()));	
		}
		else if((s.matches("-?\\d+(\\.\\d+)?")) && s.length() < 7)
		{
			for(ONCFamily f: oncFamAL)
	    		if(s.equals(f.getODBFamilyNum()))
	    			resultList.add(new FamilyReference(f.getODBFamilyNum())); 
		}
		else if((s.startsWith("C") && s.substring(1).matches("-?\\d+(\\.\\d+)?")) && s.length() < 7)
		{
			for(ONCFamily f: oncFamAL)
	    		if(s.equals(f.getODBFamilyNum()))
	    			resultList.add(new FamilyReference(f.getODBFamilyNum())); 
		}
		else if((s.startsWith("W") && s.substring(1).matches("-?\\d+(\\.\\d+)?")) && s.length() < 6)
		{
			for(ONCFamily f: oncFamAL)
	    		if(s.equals(f.getODBFamilyNum()))
	    			resultList.add(new FamilyReference(f.getODBFamilyNum())); 
		}
		else if(s.matches("-?\\d+(\\.\\d+)?") && s.length() < 13)
		{
			for(ONCFamily f:oncFamAL)
	    	{
	    		//Ensure just 10 digits, no dashes in numbers
	    		String hp = f.getHomePhone().replaceAll("-", "");
	    		String op = f.getOtherPhon().replaceAll("-", "");
	    		String target = s.replaceAll("-", "");
	    		
	    		if(hp.contains(target) || op.contains(target))
	    			resultList.add(new FamilyReference(f.getODBFamilyNum()));
	    	}
		}
		else
		{
			//search the family db
	    	for(ONCFamily f: oncFamAL)
	    		if(f.getClientFamily().toLowerCase().contains(s.toLowerCase()))
	    			resultList.add(new FamilyReference(f.getODBFamilyNum()));
	    	
	    	//search the child db
	    	childDB.searchForLastName(year, s, resultList);
		}
		
		String response = gson.toJson(resultList, listOfFamilyReferences);
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HTTPCode.Ok);		
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
			
			//check if the reference number has changed to a Cxxxxx number greater than
			//the current highestReferenceNumber. If it is, reset highestRefNum
			if(updatedFamily.getODBFamilyNum().startsWith("C") && 
				!currFam.getODBFamilyNum().equals(updatedFamily.getODBFamilyNum()))
			{
				int updatedFamilyRefNum = Integer.parseInt(updatedFamily.getODBFamilyNum().substring(1));
				if(updatedFamilyRefNum > highestRefNum)
					highestRefNum = updatedFamilyRefNum;
			}
			
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
	
	ONCFamily update(int year, ONCFamily updatedFamily, boolean bAutoAssign)
	{
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
			return updatedFamily;
		}
		else
			return null;
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
			reg = RegionDB.searchForRegionMatch(new Address(updatedFamily.getHouseNum(),
											updatedFamily.getStreet(), updatedFamily.getUnitNum(),
											  updatedFamily.getCity(), updatedFamily.getZipCode()));
			
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
			
			//check to see if the reference number is provided, if not, generate one
			if(addedFam.getODBFamilyNum().equals("NNA"))
				addedFam.setODBFamilyNum(generateReferenceNumber());
			
			//check to see if family is already in the data base, if so, mark it as
			//a duplicate family. 
			
			
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
	
	ONCFamily add(int year, ONCFamily addedFam)
	{
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
			int famID = fDBYear.getNextID();
			addedFam.setID(famID);
			
			String targetID = addedFam.getODBFamilyNum();
			if(targetID.contains("NNA") || targetID.equals(""))
			{
				targetID = generateReferenceNumber();
				addedFam.setODBFamilyNum(targetID);
			}
			
			//add to the family data base
			fDBYear.add(addedFam);
			fDBYear.setChanged(true);
			
			//return the new family
			return addedFam;
		}
		else
			return null;
	}
	
	String checkForDuplicateFamily(int year, String json, ONCUser user)
	{
		//Create a family object for the family to check
		Gson gson = new Gson();
		ONCFamily reqFamToCheck = gson.fromJson(json, ONCFamily.class);
		
		String result = "UNIQUE_FAMILY";
		
		//verify requested family to check is in database
		ONCFamily famToCheck = getFamily(year, reqFamToCheck.getID());
		if(famToCheck != null)
		{
			//get the children to check from the family to check
			List<ONCChild> famChildrenToCheck = ServerChildDB.getChildList(year, famToCheck.getID());
			
			ONCFamily dupFamily = getDuplicateFamily(year, famToCheck, famChildrenToCheck);
			if(dupFamily != null)
			{
				//family to check is a duplicate, mark them as such and notify clients
				//update the other. Use reference # to determine
				famToCheck.setONCNum("DEL");
				famToCheck.setDNSCode("DUP");
				famToCheck.setStoplightPos(FAMILY_STOPLIGHT_RED);
				famToCheck.setStoplightMssg("DUP of " + dupFamily.getODBFamilyNum());
				famToCheck.setStoplightChangedBy(user.getLNFI());
				famToCheck.setODBFamilyNum(dupFamily.getODBFamilyNum());
				
				//notify all in year clients of change to famToCheck
				String famToCheckJson = gson.toJson(famToCheck, ONCFamily.class);
				clientMgr.notifyAllInYearClients(year, "UPDATED_FAMILY" + famToCheckJson);
				
				result = "DUPLICATE_FAMILY";
			}		
		}
		
		return result;
			
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
	
	static HtmlResponse getFamilyJSONP(int year, String targetID, String callbackFunction)
	{		
		Gson gson = new Gson();
		String response;
	
		List<ONCFamily> fAL = familyDB.get(year-BASE_YEAR).getList();
		
		int index=0;
		while(index<fAL.size() && !fAL.get(index).getODBFamilyNum().equals(targetID))
			index++;
		
		if(index<fAL.size())
		{
			ONCFamily fam = fAL.get(index);
			ONCWebsiteFamilyExtended webFam = new ONCWebsiteFamilyExtended(fam,RegionDB.getRegion(fam.getRegion()));
			response = gson.toJson(webFam, ONCWebsiteFamilyExtended.class);
		}
		else
			response = "";
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HTTPCode.Ok);		
	}
	
	ONCFamily getFamily(int year, int id)	//id number set each year
	{
		List<ONCFamily> fAL = familyDB.get(year-BASE_YEAR).getList();
		int index = 0;	
		while(index < fAL.size() && fAL.get(index).getID() != id)
			index++;
		
		if(index < fAL.size())
			return fAL.get(index);
		else
			return null;
	}
	
	static String getFamilyRefNum(int year, int id)	//id number set each year
	{
		List<ONCFamily> fAL = familyDB.get(year-BASE_YEAR).getList();
		int index = 0;	
		while(index < fAL.size() && fAL.get(index).getID() != id)
			index++;
		
		if(index < fAL.size())
			return fAL.get(index).getODBFamilyNum();
		else
			return null;
	}
	
	ONCFamily getFamilyByMealID(int year, int mealID)
	{
		List<ONCFamily> fAL = familyDB.get(year-BASE_YEAR).getList();
		int index = 0;	
		while(index < fAL.size() && fAL.get(index).getMealID() != mealID)
			index++;
		
		if(index < fAL.size())
			return fAL.get(index);
		else
			return null;
	}

	
	ONCFamily getFamilyByTargetID(int year, String targetID)	//Persistent odb, wfcm or onc id number string
	{
		List<ONCFamily> fAL = familyDB.get(year-BASE_YEAR).getList();
		int index = 0;	
		while(index < fAL.size() && !fAL.get(index).getODBFamilyNum().equals(targetID))
			index++;
		
		if(index < fAL.size())
			return fAL.get(index);
		else
			return null;
	}
	
	void checkFamilyStatusAndGiftCardOnlyOnWishAdded(int year, int childid)
	{
		int famID = childDB.getChildsFamilyID(year, childid);
		
		ONCFamily fam = getFamily(year, famID);
		
	    //determine the proper family status for the family after adding the wish
	    int newStatus = getLowestFamilyStatus(year, famID);
	    
	    //determine if the families gift card only status after adding the wish
	    boolean bNewGiftCardOnlyFamily = isGiftCardOnlyFamily(year, famID);
	   
	    //if family status has changed, update the data base and notify clients
	    if(newStatus != fam.getFamilyStatus() || bNewGiftCardOnlyFamily != fam.isGiftCardOnly())
	    {
	    	fam.setFamilyStatus(newStatus);
	    	fam.setGiftCardOnly(bNewGiftCardOnlyFamily);
	    	familyDB.get(year - BASE_YEAR).setChanged(true);
	    	
	    	Gson gson = new Gson();
	    	String change = "UPDATED_FAMILY" + gson.toJson(fam, ONCFamily.class);
	    	clientMgr.notifyAllInYearClients(year, change);	//null to notify all clients
	    }
	}
	
	/********
	 * Whenever meal is added, the meal database notifies the family database to update
	 * the id for the families meal and the meal status field.
	 * The meal status field is redundant in the ONC Family object for performance considerations. 
	 * @param year
	 * @param addedMeal
	 */
	void familyMealAdded(int year, ONCMeal addedMeal)
	{
		ONCFamily fam = getFamily(year, addedMeal.getFamilyID());
		if(fam != null && fam.getMealStatus() != addedMeal.getStatus())
		{
			fam.setMealID(addedMeal.getID());
			fam.setMealStatus(addedMeal.getStatus());
			familyDB.get(year - BASE_YEAR).setChanged(true);
			
			Gson gson = new Gson();
	    	String changeJson = "UPDATED_FAMILY" + gson.toJson(fam, ONCFamily.class);
	    	clientMgr.notifyAllInYearClients(year, changeJson);	//null to notify all clients
		}
	}
	
	boolean isGiftCardOnlyFamily(int year, int famid)
	{
		//set a return variable to true. If we find one instance, we'll set it to false
		boolean bGiftCardOnlyFamily = true;
		
		//first, determine if gift cards are even in the catalog. If they aren't, return false as
		//it can't be a gift card only family
		int giftCardID = ServerWishCatalog.findWishIDByName(year, GIFT_CARD_WISH_NAME);
		if(giftCardID == -1)
			bGiftCardOnlyFamily = false;
		else
		{	
			List<ONCChild> childList = ServerChildDB.getChildList(year, famid);	//get the children in the family
		
			//examine each child to see if their assigned gifts are gift cards. If gift is not
			//assigned or not a gift card, then it's not a gift card only family
			int childindex=0;
			while(childindex < childList.size() && bGiftCardOnlyFamily)
			{
				ONCChild c = childList.get(childindex++);	//get the child
				
				//if all gifts aren't assigned, then not a gift card only family
				int wn = 0;
				while(wn < NUMBER_OF_WISHES_PER_CHILD && bGiftCardOnlyFamily)
					if(c.getChildWishID(wn++) == -1)
						bGiftCardOnlyFamily = false;
				
				//if all are assigned, examine each gift to see if it's a gift card
				int giftindex = 0;
				while(giftindex < NUMBER_OF_WISHES_PER_CHILD && bGiftCardOnlyFamily)
				{
					ONCChildWish cw = ServerChildWishDB.getWish(year, c.getChildWishID(giftindex++));
					if(cw.getWishID() != giftCardID)	//gift card?
						bGiftCardOnlyFamily = false;
				}	
			}
		}
		
		return bGiftCardOnlyFamily;
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
		int[] wishstatusmatrix = {FAMILY_STATUS_UNVERIFIED,	//WishStatus Index = 0;
								 FAMILY_STATUS_INFO_VERIFIED,	//WishStatus Index = 1;
								 FAMILY_STATUS_GIFTS_SELECTED,	//WishStatus Index = 2;
								 FAMILY_STATUS_GIFTS_SELECTED,	//WishStatus Index = 3;
								 FAMILY_STATUS_GIFTS_SELECTED,	//WishStatus Index = 4;
								 FAMILY_STATUS_GIFTS_SELECTED,	//WishStatus Index = 5;
								 FAMILY_STATUS_GIFTS_SELECTED,	//WishStatus Index = 6;
								 FAMILY_STATUS_GIFTS_RECEIVED,	//WishStatus Index = 7;
								 FAMILY_STATUS_GIFTS_RECEIVED,	//WishStatus Index = 8;
								 FAMILY_STATUS_GIFTS_SELECTED,	//WishStatus Index = 9;
								 FAMILY_STATUS_GIFTS_VERIFIED};	//WishStatus Index = 10;
			
		//Check for all gifts selected
		int lowestfamstatus = FAMILY_STATUS_GIFTS_VERIFIED;
		for(ONCChild c:ServerChildDB.getChildList(year, famid))
		{
			for(int wn=0; wn< NUMBER_OF_WISHES_PER_CHILD; wn++)
			{
				ONCChildWish cw = ServerChildWishDB.getWish(year, c.getChildWishID(wn));
				
				//if cw is null, it means that the wish doesn't exist yet. If that's the case, 
				//set the status to the lowest status possible as if the wish existed
				WishStatus childwishstatus = WishStatus.Not_Selected;	//Lowest possible child wish status
				if(cw != null)
					childwishstatus = ServerChildWishDB.getWish(year, c.getChildWishID(wn)).getChildWishStatus();
					
				if(wishstatusmatrix[childwishstatus.statusIndex()] < lowestfamstatus)
					lowestfamstatus = wishstatusmatrix[childwishstatus.statusIndex()];
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
/*	
	void updateFamilyMeal(int year, ONCMeal addedMeal)
	{
		//try to find the family the meal belongs to. In order to add the meal, the family
		//must exist
		ONCFamily fam = getFamily(year, addedMeal.getFamilyID());				
		
		if(fam != null && addedMeal != null)
		{
			fam.setMealID(addedMeal.getID());
			
			if(addedMeal.getID() == -1)
				fam.setMealStatus(MealStatus.None);
			else if(addedMeal.getPartnerID() == -1)
				fam.setMealStatus(MealStatus.Requested);
			else if(fam.getMealStatus() == MealStatus.Requested && addedMeal.getPartnerID() > -1)
				fam.setMealStatus(MealStatus.Assigned);
			
			familyDB.get(year - BASE_YEAR).setChanged(true);
		
			//notify the in year clients of the family update
			Gson gson = new Gson();
			String change = "UPDATED_FAMILY" + gson.toJson(fam, ONCFamily.class);
			clientMgr.notifyAllInYearClients(year, change);	//null to notify all clients
		}
	}
*/	

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
				"Adopted For", "Agent ID", "Delivery ID", "Meal ID", "Meal Status", "# of Bags", "# of Large Items", 
				"Stoplight Pos", "Stoplight Mssg", "Stoplight C/B", "Transportation", "Gift Card Only"};
		
		FamilyDBYear fDBYear = familyDB.get(year - BASE_YEAR);
		if(fDBYear.isUnsaved())
			
		{
//			System.out.println(String.format("FamilyDB saveDB - Saving Family DB"));
			String path = String.format("%s/%dDB/FamilyDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(fDBYear.getList(), header, path);
			fDBYear.setChanged(false);
		}
	}
	
	static boolean didAgentReferInYear(int agentID, int year)
	{
		List<ONCFamily> famList = familyDB.get(year-BASE_YEAR).getList();
		int index = 0;
		while(index < famList.size() && famList.get(index).getAgentID() != agentID)
			index++;
		
		return index < famList.size();	//true if agentID referred family	
	}
	
	/****
	 * Checks to see if an address should have a unit number. Looks at all prior years stored
	 * in the database and tries to find a match for the address without unit. If it finds a
	 * match it stops the search. Then, using the matched address, determines if the stored 
	 * address had an apt/unit. If so, returns true. The address match search matches house
	 * number, street name without suffix and city. 
	 * @param priorYear
	 * @param housenum
	 * @param street
	 * @param zip
	 * @return
	 */
	static boolean shouldAddressHaveUnit(int priorYear, String housenum, String street, String zip)
	{
		boolean bAddressHadUnit = false;
		if(priorYear > BASE_YEAR)
		{
			List<ONCFamily> famList = familyDB.get(priorYear-BASE_YEAR).getList();
			int index = 0;
			while(index < famList.size() &&
				   !(famList.get(index).getHouseNum().equals(housenum) &&
//					 famList.get(index).getStreet().toLowerCase().equals(street.toLowerCase())))
					 removeStreetSuffix(famList.get(index).getStreet()).equals(removeStreetSuffix(street))))				
//					 && famList.get(index).getZipCode().equals(zip)))
				index++;
			
			if(index < famList.size())
			{
//				ONCFamily fam = famList.get(index);
//				System.out.println(String.format("FamilyDB.shouldAddressHaveUnit: Prior Year = %d, ONC#= %s, Unit=%s, Unit.length=%d", priorYear, fam.getONCNum(), fam.getUnitNum(), fam.getUnitNum().trim().length()));
				bAddressHadUnit = !famList.get(index).getUnitNum().trim().isEmpty();
			}
		}
		
		return bAddressHadUnit;
	}
	
	static String removeStreetSuffix(String streetName)
	{
		if(!streetName.isEmpty())
		{
			String[] nameParts = streetName.split(" ");
			StringBuffer buff = new StringBuffer(nameParts[0]);
			for(int i=0; i<nameParts.length-1; i++)
				buff.append(" " + nameParts[i]);
			
			return buff.toString().toLowerCase();
		}
		else
			return "";
	}
	
	//convert targetID to familyID
	static int getFamilyID(int year, String targetID)
	{
		List<ONCFamily> famList = familyDB.get(year-BASE_YEAR).getList();
		
		int index = 0;
		while(index < famList.size() && !famList.get(index).getODBFamilyNum().equals(targetID))
			index++;
		
		if(index < famList.size())
			return famList.get(index).getID();	//return famID
		else
			return -1;
	}
	
	List<ONCFamily> getList(int year)
	{
		return familyDB.get(year-BASE_YEAR).getList();
	}

	@Override
	void createNewYear(int newYear)
	{
		//test to see if prior year existed. If it did, need to add to the ApartmentDB the families who's
		//addresses had units.
		if(!familyDB.isEmpty())
		{
			FamilyDBYear fDBYear = familyDB.get(familyDB.size()-1);	//prior year familyDBYear
			ApartmentDB.addPriorYearApartments(fDBYear.getList());	//prior year list of ONCFamily
		}
		
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
    
    /***
     * Check to see if family is already in a family data base. 
     */
    ONCFamily getDuplicateFamily(int year, ONCFamily addedFamily, List<ONCChild> addedChildList)
    {
//    	System.out.println(String.format("FamilyDB.getDuplicateFamily: "
//				+ "year= %d, addedFamily HOHLastName = %s, addedFamily Ref#= %s", 
//				year, addedFamily.getHOHLastName(), addedFamily.getODBFamilyNum()));
    	
    	ONCFamily dupFamily = null;
    	boolean bFamilyDuplicate = false;
    	//check to see if family exists in year. 
    	List<ONCFamily> famList = familyDB.get(year-BASE_YEAR).getList();
    	
//    	System.out.println("getDuplicateFamiiy: got famList, size= " + famList.size());
    	
    	int dupFamilyIndex = 0;
    	
    	while(dupFamilyIndex < famList.size() && 
    		   addedFamily.getID() != famList.get(dupFamilyIndex).getID() &&
    			!bFamilyDuplicate)
    	{
    		dupFamily = famList.get(dupFamilyIndex++);
    		
//    		System.out.println(String.format("FamiyDB.getDuplicateFamily: Checking dupFamily id= %d "
//					+ "dupFamily HOHLastName= %s, dupRef#= %s, against addedFamily HOHLastName = %s, addedFamily Ref#= %s", 
//					dupFamily.getID(), dupFamily.getHOHLastName(), dupFamily.getODBFamilyNum(), 
//					addedFamily.getHOHLastName(), addedFamily.getODBFamilyNum()));
    		
    		List<ONCChild> dupChildList = ServerChildDB.getChildList(year, dupFamily.getID());
    		
//    		if(dupChildList == null)
//   			System.out.println("FamilyDB.getDuplicateFamily: dupChildList is null");
//    		else
//    			System.out.println(String.format("FamiyDB.getDuplicateFamily: #children in %s family = %d ",
//					dupFamily.getHOHLastName(), dupChildList.size()));
    	
    		bFamilyDuplicate = areFamiliesTheSame(dupFamily, dupChildList, addedFamily, addedChildList);
    	}
    	
//    	if(dupFamily != null)
//    		System.out.println(String.format("FamiyDB.getDuplicateFamily: "
//					+ "dupFamily HOHLastName= %s, dupRef#= %s, addedFamily HOHLastName = %s, addedFamily Ref#= %s", 
//					dupFamily.getHOHLastName(), dupFamily.getODBFamilyNum(), 
//					addedFamily.getHOHLastName(), addedFamily.getODBFamilyNum()));
//    	
//    	else
//    		System.out.println("FamiyDB.getDuplicateFamily: dupFamiy is null");
    	
    	return bFamilyDuplicate ? dupFamily : null;	
    }
    
    /****
     * Checks each prior year in data base to see if an addedFamily matches. Once an added 
     * family matches the search ends and the method returns the ODB# for the family. If
     * no match is found, null is returned
     * @param year
     * @param addedFamily
     * @param addedChildList
     * @return
     */
    ONCFamily isPriorYearFamily(int year, ONCFamily addedFamily, List<ONCChild> addedChildList)
    {
    	boolean bFamilyIsInPriorYear = false;
    	ONCFamily pyFamily = null;
    	int yearIndex = year-1;
    	
    	//check each prior year for a match
    	while(yearIndex >= BASE_YEAR && !bFamilyIsInPriorYear)
    	{
    		List<ONCFamily> pyFamilyList = familyDB.get(yearIndex-BASE_YEAR).getList();
    		
    		//check each family in year for a match
    		int pyFamilyIndex = 0;
    		while(pyFamilyIndex < pyFamilyList.size() && !bFamilyIsInPriorYear)
    		{
    			pyFamily = pyFamilyList.get(pyFamilyIndex++);
    			List<ONCChild> pyChildList = ServerChildDB.getChildList(yearIndex, pyFamily.getID());
    			
    			bFamilyIsInPriorYear = areFamiliesTheSame(pyFamily, pyChildList, addedFamily, addedChildList);	
    		}
    		
    		yearIndex--;
    	}
    	
    	return bFamilyIsInPriorYear ? pyFamily : null;
    	
    }
    
    boolean areFamiliesTheSame(ONCFamily checkFamily, List<ONCChild> checkChildList, 
    							ONCFamily addedFamily, List<ONCChild> addedChildList)
    {
    	
//    	System.out.println(String.format("FamiyDB.areFamiliesThe Same: Checking family "
//				+ "checkFamily HOHLastName= %s, checkFamilyRef#= %s, against addedFamily HOHLastName = %s, addedFamily Ref#= %s", 
//				checkFamily.getHOHLastName(), checkFamily.getODBFamilyNum(), 
//				addedFamily.getHOHLastName(), addedFamily.getODBFamilyNum()));
    	
    	return checkFamily.getHOHFirstName().equalsIgnoreCase(addedFamily.getHOHFirstName()) &&
    			checkFamily.getHOHLastName().equalsIgnoreCase(addedFamily.getHOHLastName()) &&
    			areChildrenTheSame(checkChildList, addedChildList);
    }
    
    boolean areChildrenTheSame(List<ONCChild> checkChildList, List<ONCChild> addedChildList)
    {
    	boolean bChildrenAreTheSame = true;
    	
    	int checkChildIndex = 0;
    	while(checkChildIndex < checkChildList.size() && bChildrenAreTheSame)
    	{
    		ONCChild checkChild = checkChildList.get(checkChildIndex);
    		if(!isChildInList(checkChild, addedChildList))
    			bChildrenAreTheSame = false;
    		else
    			checkChildIndex++;
    	}
    	
    	return bChildrenAreTheSame;
    }
    
    
    boolean isChildInList(ONCChild checkChild, List<ONCChild> addedChildList)
    {
    	boolean bChildIsInList = false;
    	int addedChildIndex = 0;
        	
    	while(addedChildIndex < addedChildList.size()  && !bChildIsInList)
    	{
    		ONCChild addedChild = addedChildList.get(addedChildIndex);
    		
    		if(checkChild.getChildLastName().equalsIgnoreCase(addedChild.getChildLastName()) &&
    				checkChild.getChildDOB().equals(addedChild.getChildDOB()) &&
    					checkChild.getChildGender().equalsIgnoreCase(addedChild.getChildGender()))
    			bChildIsInList = true;
    		else
    			addedChildIndex++;
    	}	
    			
    	return bChildIsInList;
    }
    
    
    /******************************************************************************************************
     * This method generates a family reference number prior to import of external data. Each family
     * has one reference number, which remains constant from year to year
     ******************************************************************************************************/
    String generateReferenceNumber()
    {
    	//increment the last reference number used and format it to a five digit string
    	//that starts with the letter 'C'
    	highestRefNum++;
    	
    	if(highestRefNum < 10)
    		return "C0000" + Integer.toString(highestRefNum);
    	else if(highestRefNum >= 10 && highestRefNum < 100)
    		return "C000" + Integer.toString(highestRefNum);
    	else if(highestRefNum >= 100 && highestRefNum < 1000)
    		return "C00" + Integer.toString(highestRefNum);
    	else if(highestRefNum >= 1000 && highestRefNum < 10000)
    		return "C0" + Integer.toString(highestRefNum);
    	else
    		return "C" + Integer.toString(highestRefNum);
    }
    
    void decrementReferenceNumber() { highestRefNum--; }

    /*******
     * This method looks at every year in the data base and determines the highest ONC family
     * reference number used to date. ONC reference numbers start with the letter 'C'
     * and have the format Cxxxxx, where x's are digits 0-9.
     ******************/
    int initializeHighestReferenceNumber()
    {
    	int highestRefNum = 0;
    	for(FamilyDBYear dbYear: familyDB)
    	{
    		List<ONCFamily> yearListOfFamilies = dbYear.getList();
    		for(ONCFamily f: yearListOfFamilies)
    		{
    			if(f.getODBFamilyNum().startsWith("C"))
    			{
    				int refNum = Integer.parseInt(f.getODBFamilyNum().substring(1));
    				if(refNum > highestRefNum)
    					highestRefNum = refNum;
    			}
    		}
    	}
    	
    	return highestRefNum;
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
    
    protected static boolean isNumeric(String str)
	{
		if(str == null || str.isEmpty())
			return false;
		else
			return str.matches("-?\\d+(\\.\\d+)?");  //match a number with optional '-' and decimal.
	}
    
    private static class ONCFamilyONCNumComparator implements Comparator<ONCFamily>
	{
		@Override
		public int compare(ONCFamily o1, ONCFamily o2)
		{
			if(isNumeric(o1.getONCNum()) && isNumeric(o2.getONCNum()))
			{
				Integer onc1 = Integer.parseInt(o1.getONCNum());
				Integer onc2 = Integer.parseInt(o2.getONCNum());
				return onc1.compareTo(onc2);
			}
			else if(isNumeric(o1.getONCNum()) && !isNumeric(o2.getONCNum()))
				return -1;
			else if(!isNumeric(o1.getONCNum()) && isNumeric(o2.getONCNum()))
				return 1;
			else
				return o1.getONCNum().compareTo(o2.getONCNum());
		}
	}
    private static class ONCWebsiteFamilyLNComparator implements Comparator<ONCWebsiteFamily>
	{
		@Override
		public int compare(ONCWebsiteFamily o1, ONCWebsiteFamily o2)
		{
			return o1.getHOHLastName().toLowerCase().compareTo(o2.getHOHLastName().toLowerCase());
		}
	}
}