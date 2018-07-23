package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ourneighborschild.Address;
import ourneighborschild.GiftCollection;
import ourneighborschild.ONCChildWish;
import ourneighborschild.ONCPartner;
import ourneighborschild.WishStatus;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerPartnerDB extends ServerSeasonalDB
{
	private static final int ORGANIZATION_DB_HEADER_LENGTH = 36;
	private static final int STATUS_CONFIRMED = 5;
	private static final int ORG_TYPE_CLOTHING = 4;
	
	private static List<PartnerDBYear> partnerDB;
	
	private static ServerPartnerDB instance = null;
	
	private ServerPartnerDB() throws FileNotFoundException, IOException
	{
		//create the partner data bases for TOTAL_YEARS number of years
		partnerDB = new ArrayList<PartnerDBYear>();
				
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the partner list for year
			PartnerDBYear partnerDBYear = new PartnerDBYear(year);
			
			//add the list for the year to the db
			partnerDB.add(partnerDBYear);
					
			importDB(year, String.format("%s/%dDB/OrgDB.csv",
						System.getProperty("user.dir"),
							year), "Partner DB", ORGANIZATION_DB_HEADER_LENGTH);
			
			//for partners, the leading 4 digits are the year the partner was added. So, must account
			//for that when determining the next ID number to assign
			int nextID = getNextID(partnerDBYear.getList());
			int nextIDinYear = year * 1000;
			if(nextID < nextIDinYear)
				partnerDBYear.setNextID(nextIDinYear);
			else
				partnerDBYear.setNextID(nextID);
		}
	}
	
	public static ServerPartnerDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerPartnerDB();
		
		return instance;
	}
		
	String getPartners(int year)
	{
		Gson gson = new Gson();
		Type listOfPartners = new TypeToken<ArrayList<ONCPartner>>(){}.getType();
		
		String response = gson.toJson(partnerDB.get(year - BASE_YEAR).getList(), listOfPartners);
		return response;	
	}
	
	static HtmlResponse getPartnersJSONP(int year, String callbackFunction)
	{		
		Gson gson = new Gson();
		Type listOfWebPartners = new TypeToken<ArrayList<ONCWebPartner>>(){}.getType();
		
		ArrayList<ONCWebPartner> webPartnerList = new ArrayList<ONCWebPartner>();
		
		for(ONCPartner p :  partnerDB.get(year-BASE_YEAR).getList())
			webPartnerList.add(new ONCWebPartner(p));
		
		Collections.sort(webPartnerList, new PartnerNameComparator());

		String response = gson.toJson(webPartnerList, listOfWebPartners);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	
	static HtmlResponse getPartnerJSONP(int year, String partnerID, String callbackFunction)
	{		
		Gson gson = new Gson();
		String response;
	
		List<ONCPartner> pAL = partnerDB.get(year-BASE_YEAR).getList();
		
		int index=0;
		while(index<pAL.size() && pAL.get(index).getID() != (Integer.parseInt(partnerID)))
			index++;
		
		if(index<pAL.size())
		{
			ONCPartner partner = pAL.get(index);
			response = gson.toJson(new ONCWebPartnerExtended(partner), ONCWebPartnerExtended.class);
		}
		else
			response = "";
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	
	String getPartner(int year, String zID)
	{
		int id = Integer.parseInt(zID);
		int index = 0;
		
		List<ONCPartner> orgAL = partnerDB.get(year-BASE_YEAR).getList();
		
		while(index < orgAL.size() && orgAL.get(index).getID() != id)
			index++;
		
		if(index < orgAL.size())
		{
			Gson gson = new Gson();
			String partnerjson = gson.toJson(orgAL.get(index), ONCPartner.class);
			
			return "PARTNER" + partnerjson;
		}
		else
			return "PARTNER_NOT_FOUND";
	}
	
	ONCPartner getPartner(int year, int partID)
	{
		List<ONCPartner> oAL = partnerDB.get(year - BASE_YEAR).getList();
		
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
		ONCPartner reqOrg = gson.fromJson(json, ONCPartner.class);
		
		//Find the position for the current family being replaced
		PartnerDBYear partnerDBYear = partnerDB.get(year - BASE_YEAR);
		List<ONCPartner> oAL = partnerDBYear.getList();
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
			ONCPartner currOrg = oAL.get(index);
			//check if partner address has changed and a region update check is required
			if(currOrg.getHouseNum() != reqOrg.getHouseNum() ||
				!currOrg.getStreet().equals(reqOrg.getStreet()) ||
				 !currOrg.getCity().equals(reqOrg.getCity()) ||
				  !currOrg.getZipCode().equals(reqOrg.getZipCode()))
			{
//				System.out.println(String.format("PartnerDB - update: region change, old region is %d", currOrg.getRegion()));
				updateRegion(reqOrg);	
			}
			oAL.set(index, reqOrg);
			partnerDBYear.setChanged(true);
			return "UPDATED_PARTNER" + gson.toJson(reqOrg, ONCPartner.class);
		}
	}
	
	ONCPartner update(int year, ONCPartner updatedPartner)
	{	
		//Find the position for the current family being replaced
		ONCPartner currPartner = null;
		PartnerDBYear partnerDBYear = partnerDB.get(year - BASE_YEAR);
		List<ONCPartner> oAL = partnerDBYear.getList();
		int index = 0;
		while(index < oAL.size() && oAL.get(index).getID() != updatedPartner.getID())
			index++;
		
		//If partner is located and the updated partner has been changed, replace the current 
		//partner with the update. Do not update the ornament request field if the current
		//partners status is CONFIRMED
		if(index < oAL.size() && !doPartnersMatch((currPartner = oAL.get(index)), updatedPartner))
		{
			//check if a change to the number of requested ornaments is allowed
			if(currPartner.getStatus() == STATUS_CONFIRMED && 
				currPartner.getNumberOfOrnamentsRequested() > 0)
			{
				updatedPartner.setStatus(currPartner.getStatus());
				updatedPartner.setNumberOfOrnamentsRequested(currPartner.getNumberOfOrnamentsRequested());	
			}
			
			//check if partner address has changed and a region update check is required
			if(currPartner.getHouseNum() != updatedPartner.getHouseNum() ||
				!currPartner.getStreet().equals(updatedPartner.getStreet()) ||
				 !currPartner.getCity().equals(updatedPartner.getCity()) ||
				  !currPartner.getZipCode().equals(updatedPartner.getZipCode()))
			{
				updateRegion(updatedPartner);	
			}
			oAL.set(index, updatedPartner);
			partnerDBYear.setChanged(true);
			return updatedPartner;
		}
		else
			return null;
	}
	
	boolean doPartnersMatch(ONCPartner currPartner, ONCPartner updatedPartner)
	{
		int c = 0;
		
		if(!currPartner.getLastName().equals(updatedPartner.getLastName())) { c = 1; }
		if(currPartner.getStatus() != updatedPartner.getStatus()) { c = 2; }
		if(currPartner.getType() != updatedPartner.getType()) { c = 3; }
		if(currPartner.getGiftCollectionType() != updatedPartner.getGiftCollectionType()) { c = 4; }
		if(!currPartner.getHouseNum().equals(updatedPartner.getHouseNum())) { c = 5; }
		if(!currPartner.getStreet().equals(updatedPartner.getStreet())) { c = 6; }
		if(!currPartner.getUnit().equals(updatedPartner.getUnit())) { c = 7; }
		if(!currPartner.getCity().equals(updatedPartner.getCity())) { c = 8; }
		if(!currPartner.getZipCode().equals(updatedPartner.getZipCode())) { c = 9; }
		if(!currPartner.getHomePhone().equals(updatedPartner.getHomePhone())) { c = 10; }
		if(!currPartner.getOther().equals(updatedPartner.getOther())) { c = 11; }
		if(!currPartner.getSpecialNotes().equals(updatedPartner.getSpecialNotes())) { c = 12; }
		if(!currPartner.getDeliverTo().equals(updatedPartner.getDeliverTo())) { c = 13; }
		if(!currPartner.getContact().equals(updatedPartner.getContact())) { c = 14; }
		if(! currPartner.getContact_email().equals(updatedPartner.getContact_email())) { c = 15; }
		if(!currPartner.getContact_phone().equals(updatedPartner.getContact_phone())) { c = 16; }
		if(!currPartner.getContact2().equals(updatedPartner.getContact2())) { c = 17; }
		if(!currPartner.getContact2_email().equals(updatedPartner.getContact2_email())) { c = 18; }
		if(!currPartner.getContact2_phone().equals(updatedPartner.getContact2_phone())) { c = 19; }
		if(currPartner.getNumberOfOrnamentsRequested() != updatedPartner.getNumberOfOrnamentsRequested()) { c = 20; }
		
//		System.out.println(String.format("ServPartDB.doPartnersMatch: Change Code= %d", c));
		return c == 0;
	}
	
	int updateRegion(ONCPartner updatedOrg)
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
			reg = RegionDB.searchForRegionMatch(new Address(updatedOrg.getHouseNum(),
											updatedOrg.getStreet(), "",  updatedOrg.getCity(),
											 updatedOrg.getZipCode()));
			updatedOrg.setRegion(reg);
		}
		
		return reg;
	}
	
	@Override
	String add(int year, String json)
	{
		//Create a organization object for the updated partner
		Gson gson = new Gson();
		ONCPartner addedPartner = gson.fromJson(json, ONCPartner.class);
	
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
			addedPartner.setRegion(RegionDB.searchForRegionMatch(new Address(addedPartner.getHouseNum(), 
															addedPartner.getStreet(), "", addedPartner.getCity(),
															addedPartner.getZipCode())));
		else
			addedPartner.setRegion(0);
		
		//add the partner to the proper database
		partnerDBYear.add(addedPartner);
		partnerDBYear.setChanged(true);
		
		return "ADDED_PARTNER" + gson.toJson(addedPartner, ONCPartner.class);
	}
	
	ONCPartner add(int year, ONCPartner addedPartner)
	{
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
			addedPartner.setRegion(RegionDB.searchForRegionMatch(new Address(addedPartner.getHouseNum(), 
															addedPartner.getStreet(), "", addedPartner.getCity(),
															addedPartner.getZipCode())));
		else
			addedPartner.setRegion(0);
		
		//add the partner to the proper database
		partnerDBYear.add(addedPartner);
		partnerDBYear.setChanged(true);
		
		return addedPartner;
	}
	
	String delete(int year, String json)
	{
		//Create a organization object for the updated partner
		Gson gson = new Gson();
		ONCPartner reqDelPartner = gson.fromJson(json, ONCPartner.class);
	
		//find the partner in the db
		PartnerDBYear partnerDBYear = partnerDB.get(year - BASE_YEAR);
		List<ONCPartner> oAL = partnerDBYear.getList();
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
	
	static int getOrnamentsRequested(int year)
	{
		PartnerDBYear partnerDBYear = partnerDB.get(year - BASE_YEAR);
		List<ONCPartner> pAL = partnerDBYear.getList();
		
		int ornReq = 0;
		for(ONCPartner p : pAL)
			if(p.getStatus() == STATUS_CONFIRMED)
				ornReq += p.getNumberOfOrnamentsRequested();
		
		return ornReq;
	}

	void updateGiftAssignees(int year, int oldPartnerID, int newPartnerID)
	{
		PartnerDBYear partnerDBYear = partnerDB.get(year - BASE_YEAR);
		
		//Find the the current partner &  decrement gift count if found
		if(oldPartnerID > 0)
		{
			ONCPartner oldPartner = getPartner(year, oldPartnerID);
			if(oldPartner != null)
			{
				oldPartner.decrementOrnAssigned();
				partnerDBYear.setChanged(true);
			}
		}
	
		if(newPartnerID > 0)
		{
			//Find the the current partner &  increment gift count if found
			ONCPartner newPartner = getPartner(year, newPartnerID);
			if(newPartner != null)
			{
				newPartner.incrementOrnAssigned();
				partnerDBYear.setChanged(true);
			}
		}	
	}
	
	void incrementGiftActionCount(int year, ONCChildWish addedWish)
	{	
		PartnerDBYear partnerDBYear = partnerDB.get(year - BASE_YEAR);
		List<ONCPartner> partnerList = partnerDBYear.getList();
		
		//Find the the current partner being decremented
		int index = 0;
		while(index < partnerList.size() && partnerList.get(index).getID() != addedWish.getChildWishAssigneeID())
			index++;
		
		//increment the gift received count for the partner being replaced
		if(index < partnerList.size())
		{  
			//found the partner, now determine which field to increment
			if(addedWish.getChildWishStatus() == WishStatus.Received)
			{
				ServerGlobalVariableDB gvDB = null;
				try 
				{
					gvDB = ServerGlobalVariableDB.getInstance();
					boolean bReceviedBeforeDeadline = addedWish.getChildWishDateChanged().before(gvDB.getDateGiftsRecivedDealdine(year));
					partnerList.get(index).incrementOrnReceived(bReceviedBeforeDeadline);
				} 
				catch (FileNotFoundException e) 
				{
					// TODO Auto-generated catch block
				} 
				catch (IOException e) 
				{
					// TODO Auto-generated catch block
				}
				
			}
			else if(addedWish.getChildWishStatus() == WishStatus.Delivered)
				partnerList.get(index).incrementOrnDelivered();
			
			partnerDBYear.setChanged(true);
		}
	}
	
	void decrementGiftsAssignedCount(int year, int partnerID)
	{
		PartnerDBYear partnerDBYear = partnerDB.get(year - BASE_YEAR);
		List<ONCPartner> oAL = partnerDBYear.getList();
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
		partnerDBYear.add(new ONCPartner(nextLine));
	}

	@Override
	void createNewYear(int newYear)
	{
		//create a new partner data base year for the year provided in the newYear parameter
		//Then copy the prior years partners to the newly created partner db year list.
		//Reset each partners status to NO_ACTION_YET
		//Mark the newly created WishCatlogDBYear for saving during the next save event
				
		//get a reference to the prior years wish catalog
		List<ONCPartner> lyPartnerList = partnerDB.get(partnerDB.size()-1).getList();
				
		//create the new PartnerDBYear
		PartnerDBYear partnerDBYear = new PartnerDBYear(newYear);
		partnerDB.add(partnerDBYear);
				
		//add last years partners to the new season partner list
		for(ONCPartner lyPartner : lyPartnerList)
		{
			ONCPartner newPartner = new ONCPartner(lyPartner);	//makes a copy of last year
			
			newPartner.setStatus(0);	//reset status to NO_ACTION_YET
			
			//set prior year performance statistics
			newPartner.setPriorYearRequested(lyPartner.getNumberOfOrnamentsRequested());
			newPartner.setPriorYearAssigned(lyPartner.getNumberOfOrnamentsAssigned());
			newPartner.setPriorYearDelivered(lyPartner.getNumberOfOrnamentsDelivered());
			newPartner.setPriorYearReceivedBeforeDeadline(lyPartner.getNumberOfOrnamentsReceivedBeforeDeadline());
			newPartner.setPriorYearReceivedAfterDeadline(lyPartner.getNumberOfOrnamentsReceivedAfterDeadline());
			
			//reset the new season performance statistics
			newPartner.setNumberOfOrnamentsRequested(0);	//reset requested to 0
			newPartner.setNumberOfOrnamentsAssigned(0);	//reset assigned to 0
			newPartner.setNumberOfOrnamentsDelivered(0);	//reset delivered to 0
			newPartner.setNumberOfOrnamentsReceivedBeforeDeadline(0);	//reset received before to 0
			newPartner.setNumberOfOrnamentsReceivedAfterDeadline(0);	//reset received after to 0
			newPartner.setPriorYearRequested(lyPartner.getNumberOfOrnamentsRequested());
			
			partnerDBYear.add(newPartner);
		}
		
		//set the nextID for the newly created PartnerDBYear
		partnerDBYear.setNextID(newYear*1000);	//all partner id's start with current year
		
		//Mark the newly created DBYear for saving during the next save event
		partnerDBYear.setChanged(true);
	}
	
	/********************************************************************************************
	 * using prior year child wish data, set the prior year ornaments requested, assigned and received
	 * class variables for each partner
	 * @param newYear
	 * @param partnerDBYear
	 ******************************************************************************************/
	void determinePriorYearPerformance(int year)
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
		//contains an assigned ID, a deliveredID, and a receivedID indicating which partner was responsible
		//for fulfilling the wish and who ONC actually received the fulfilled wish (gift) from
		List<PriorYearPartnerPerformance> pyPartnerPerformanceList = serverChildWishDB.getPriorYearPartnerPerformanceList(year+1);
		List<ONCPartner> pyPerfPartnerList = new ArrayList<ONCPartner>();
		
		//populate the current partner list
		for(ONCPartner p: partnerDB.get(year - BASE_YEAR).getList())
		{
			//make a copy of the partner and set their assigned, delivered and received counts to zero
			ONCPartner pyPerfPartner = new ONCPartner(p);
			pyPerfPartner.setNumberOfOrnamentsAssigned(0);
			pyPerfPartner.setNumberOfOrnamentsDelivered(0);
			pyPerfPartner.setNumberOfOrnamentsReceivedBeforeDeadline(0);
			pyPerfPartner.setNumberOfOrnamentsReceivedAfterDeadline(0);
			pyPerfPartnerList.add(pyPerfPartner);
		}
		
		for(PriorYearPartnerPerformance pyPerf: pyPartnerPerformanceList)
		{
			//find the partner the wish was assigned to and increment their prior year assigned count
			if(pyPerf.getPYPartnerWishAssigneeID() > -1)
			{
				ONCPartner wishAssigneePartner = (ONCPartner) find(pyPerfPartnerList, pyPerf.getPYPartnerWishAssigneeID());
				if(wishAssigneePartner != null)
					wishAssigneePartner.incrementOrnAssigned();
			}
			//find the partner the wish was delivered to and increment their prior year assigned count
			if(pyPerf.getPYPartnerWishDeliveredID() > -1)
			{
				ONCPartner wishDeliveredPartner = (ONCPartner) find(pyPerfPartnerList, pyPerf.getPYPartnerWishDeliveredID());
				if(wishDeliveredPartner != null)
					wishDeliveredPartner.incrementOrnDelivered();
			}
			
			//find the partner the wish was received from before deadline and increment their prior year received count
			if(pyPerf.getPYPartnerWishReceivedBeforeDeadlineID() > -1)
			{
				ONCPartner wishReceivedBeforePartner = (ONCPartner) find(pyPerfPartnerList, pyPerf.getPYPartnerWishReceivedBeforeDeadlineID());;
				if(wishReceivedBeforePartner != null)
					wishReceivedBeforePartner.incrementOrnReceived(true);	
			}
			//find the partner the wish was received from after deadline and increment their prior year received count
			if(pyPerf.getPYPartnerWishReceivedAfterDeadlineID() > -1)
			{
				ONCPartner wishReceivedAfterPartner = (ONCPartner) find(pyPerfPartnerList, pyPerf.getPYPartnerWishReceivedAfterDeadlineID());;
				if(wishReceivedAfterPartner != null)
					wishReceivedAfterPartner.incrementOrnReceived(false);	
			}
		}
		
		savePYPartnerPerformace(pyPerfPartnerList);
		
//		for(ONCPartner confPart : confPartList)
//			System.out.println(String.format("%s requested %d, assigned: %d, delivered: %d, received before: %d received after: %d",
//					confPart.getName(), confPart.getNumberOfOrnamentsRequested(), confPart.getNumberOfOrnamentsAssigned(), 
//					confPart.getNumberOfOrnamentsDelivered(), confPart.getNumberOfOrnamentsReceivedBeforeDeadline(),
//					confPart.getNumberOfOrnamentsReceivedAfterDeadline()));	
	}
	
	void savePYPartnerPerformace(List<ONCPartner> pyPerformancePartnerList)
	{
		String[] header = {"Part ID", "Status", "Type", "Gift Collection","Name", "Orn Delivered",
				"Street #", "Street", "Unit", "City", "Zip", "Region", "Phone",
	 			"Orn Requested", "Orn Assigned", "Orn Delivered", "Gifts Received Before",
	 			"Gifts Received After", "Other", "Deliver To", "Special Notes",
	 			"Contact", "Contact Email", "Contact Phone",
	 			"Contact2", "Contact2 Email", "Contact2 Phone",
	 			"Time Stamp", "Changed By", "Stoplight Pos", "Stoplight Mssg", "Stoplight C/B",
	 			"Prior Year Requested",	"Prior Year Assigned", "Prior Year Delivered",
	 			"Prior Year Received Before, Prior Year Received After"};
		
		String path = String.format("%s/PartnerPerformance.csv", System.getProperty("user.dir"));
		exportDBToCSV(pyPerformancePartnerList, header, path);
	}
	
	/*****************************************************************************************
	 * Creates a string of confirmed partners, as HTML options, that take ornaments, broken into
	 * two parts. The first part of the string are options that contain confirmed businesses, 
	 * churches and schools, sorted alphabetically. The last part of the string are options that
	 * contain all other confirmed partners sorted alphabetically
	 *****************************************************************************************/
	static String getConfirmedPartnerHTMLOptionList(int year, GiftCollection collectionType)
	{
		//Create two lists, the list to be returned and a temporary list
		ArrayList<ONCPartner> confirmedPartnerList = new ArrayList<ONCPartner>();
		ArrayList<ONCPartner> confirmedPartnerOtherList = new ArrayList<ONCPartner>();
		
		//Add the confirmed business, church and schools to the returned list and add all other 
		//confirmed partners to the temporary list
		for(ONCPartner o: partnerDB.get(year - BASE_YEAR).getList())
		{
			if(o.getStatus() == STATUS_CONFIRMED && o.getGiftCollectionType() == collectionType && 
				o.getType() < ORG_TYPE_CLOTHING)
				confirmedPartnerList.add(o);
			else if(o.getStatus() == STATUS_CONFIRMED && o.getGiftCollectionType() == collectionType)
				confirmedPartnerOtherList.add(o);		
		}
		
		//Sort the two lists alphabetically by partner name
		PartnerLastNameComparator nameComparator = new PartnerLastNameComparator();
		Collections.sort(confirmedPartnerList, nameComparator);	//Sort alphabetically
		Collections.sort(confirmedPartnerOtherList, nameComparator);	//Sort alphabetically
		
		//Append the all other temporary confirmed list to the bottom of the confirmed list
		for(ONCPartner otherOrg:confirmedPartnerOtherList)
			confirmedPartnerList.add(otherOrg);
		
		//create a HTML option string
		StringBuffer buff = new StringBuffer("<option value=-1>None</option>");
		for(ONCPartner p : confirmedPartnerList)
			buff.append(String.format("<option value=%d>%s</option>", p.getID(), p.getLastName()));
		
		return buff.toString();
	}
	
	private class PartnerDBYear extends ServerDBYear
	{
		private List<ONCPartner> pList;
	    	
	    PartnerDBYear(int year)
	    {
	    	super();
	    	pList = new ArrayList<ONCPartner>();
	    }
	    	
	    	//getters
	    	List<ONCPartner> getList() { return pList; }
	    	
	    	void add(ONCPartner addedOrg) { pList.add(addedOrg); }
	}

	@Override
	void save(int year)
	{
		String[] header = {"Org ID", "Status", "Type", "Gift Collection", "Name",
				"Street #", "Street", "Unit", "City", "Zip", "Region", "Phone",
	 			"Orn Requested", "Orn Assigned", "Orn Delivered", "Gifts Received Before",
	 			"Gifts Received After", "Other", "Deliver To", "Special Notes",
	 			"Contact", "Contact Email", "Contact Phone",
	 			"Contact2", "Contact2 Email", "Contact2 Phone",
	 			"Time Stamp", "Changed By", "Stoplight Pos", "Stoplight Mssg", "Stoplight C/B",
	 			"PY Requested",	"PY Assigned", "PY Delivered",
	 			"PY Received Before", "PY Received After"};
		
		PartnerDBYear partnerDBYear = partnerDB.get(year - BASE_YEAR);
		if(partnerDBYear.isUnsaved())
		{
//			System.out.println(String.format("PartnerDB save() - Saving Partner DB"));
			String path = String.format("%s/%dDB/OrgDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(partnerDBYear.getList(),  header, path);
			partnerDBYear.setChanged(false);
		}
	}
	
	private static class PartnerNameComparator implements Comparator<ONCWebPartner>
	{
		@Override
		public int compare(ONCWebPartner o1, ONCWebPartner o2)
		{			
			return o1.getName().compareTo(o2.getName());
		}
	}
	
	private static class PartnerLastNameComparator implements Comparator<ONCPartner>
	{
		@Override
		public int compare(ONCPartner o1, ONCPartner o2)
		{			
			return o1.getLastName().compareTo(o2.getLastName());
		}
	}
}
