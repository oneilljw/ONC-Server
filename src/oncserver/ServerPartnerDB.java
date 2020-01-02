package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import ourneighborschild.Address;
import ourneighborschild.GiftCollectionType;
import ourneighborschild.ONCChildGift;
import ourneighborschild.ONCPartner;
import ourneighborschild.ONCUser;
import ourneighborschild.GiftStatus;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerPartnerDB extends ServerSeasonalDB
{
	private static final int ORGANIZATION_DB_HEADER_LENGTH = 36;
	private static final int STATUS_CONFIRMED = 5;
	private static final int ORG_TYPE_CLOTHING = 4;
	
	private static List<PartnerDBYear> partnerDB;
	
	private static ServerPartnerDB instance = null;
	
	private ClientManager clientMgr;
	private ServerGlobalVariableDB globalDB;
	
	private ServerPartnerDB() throws FileNotFoundException, IOException
	{
		//create the partner data bases for TOTAL_YEARS number of years
		partnerDB = new ArrayList<PartnerDBYear>();
				
		for(int year = DBManager.getBaseSeason(); year < DBManager.getBaseSeason() + DBManager.getNumberOfYears(); year++)
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
		
		clientMgr = ClientManager.getInstance();
		globalDB = ServerGlobalVariableDB.getInstance();
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
		
		String response = gson.toJson(partnerDB.get(DBManager.offset(year)).getList(), listOfPartners);
		return response;	
	}
	
	List<ONCPartner> clone(int year)
	{
		List<ONCPartner> partnerList = partnerDB.get(DBManager.offset(year)).getList();
		List<ONCPartner> cloneList = new ArrayList<ONCPartner>();
		
		for(ONCPartner p :partnerList)
			cloneList.add(new ONCPartner(p));
		
		return cloneList;		
	}
	
	static HtmlResponse getPartnersJSONP(int year, boolean bConfirmedOnly, String callbackFunction)
	{	
		String response = "";
		Gson gson = new Gson();
			
		if(bConfirmedOnly)	//return a list of confirmed partners who have ornament collection types.
		{
			//Create two lists, the list to be returned and a temporary list
			ArrayList<ONCWebPartner> confirmedWebPartnerList = new ArrayList<ONCWebPartner>();
			ArrayList<ONCWebPartner> confirmedWebPartnerOtherList = new ArrayList<ONCWebPartner>();
			Type listOfWebPartners = new TypeToken<ArrayList<ONCWebPartner>>(){}.getType();
			
			//Add the confirmed business, church and schools to the returned list and add all other 
			//confirmed partners to the temporary list
			for(ONCPartner p: partnerDB.get(DBManager.offset(year)).getList())
			{
				if(p.getStatus() == STATUS_CONFIRMED && p.getGiftCollectionType() == GiftCollectionType.Ornament
						&& p.getType() < ORG_TYPE_CLOTHING)
				{
					confirmedWebPartnerList.add(new ONCWebPartner(p));
				}
				else if(p.getStatus() == STATUS_CONFIRMED && p.getGiftCollectionType() == GiftCollectionType.Ornament)
					confirmedWebPartnerOtherList.add(new ONCWebPartner(p));		
			}
			
			//Sort the two lists alphabetically by partner name
			Collections.sort(confirmedWebPartnerList, new PartnerNameComparator());	//Sort alphabetically
			Collections.sort(confirmedWebPartnerOtherList, new PartnerNameComparator());	//Sort alphabetically
			
			//Append the all other temporary confirmed list to the bottom of the confirmed list
			for(ONCWebPartner otherOrg:confirmedWebPartnerOtherList)
				confirmedWebPartnerList.add(otherOrg);
			
			response = gson.toJson(confirmedWebPartnerList, listOfWebPartners);
		}
		else		//return a list of all in year partners sorted alphabetically
		{
			Type listOfWebPartnersExtended = new TypeToken<ArrayList<ONCWebPartnerExtended>>(){}.getType();
			ArrayList<ONCWebPartnerExtended> webPartnerExtendedList = new ArrayList<ONCWebPartnerExtended>();
			for(ONCPartner p :  partnerDB.get(DBManager.offset(year)).getList())
			{
				webPartnerExtendedList.add(new ONCWebPartnerExtended(p));
				Collections.sort(webPartnerExtendedList, new PartnerNameComparator());
				response = gson.toJson(webPartnerExtendedList, listOfWebPartnersExtended);
			}
		}
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	
	static HtmlResponse getPartnerJSONP(int year, String partnerID, String callbackFunction)
	{		
		Gson gson = new Gson();
		String response;
	
		List<ONCPartner> pAL = partnerDB.get(DBManager.offset(year)).getList();
		
		int index=0;
		while(index<pAL.size() && pAL.get(index).getID() != (Integer.parseInt(partnerID)))
			index++;
		
		if(index<pAL.size())
		{
			ONCPartner partner = pAL.get(index);
			response = gson.toJson(new ONCWebPartnerFull(partner), ONCWebPartnerFull.class);
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
		
		List<ONCPartner> orgAL = partnerDB.get(DBManager.offset(year)).getList();
		
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
		List<ONCPartner> oAL = partnerDB.get(DBManager.offset(year)).getList();
		
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
	
	ONCPartner getPartnerByPhoneNumber(int year, String phoneNum)
	{
		String formatedPhoneNum = formatPhoneNumber(phoneNum);
		
		List<ONCPartner> pAL = partnerDB.get(DBManager.offset(year)).getList();
		int i;
		for(i=0; i<pAL.size(); i++)
			if(formatPhoneNumber(pAL.get(i).getHomePhone()).equals(formatedPhoneNum) || 
				formatPhoneNumber(pAL.get(i).getContact_phone()).equals(formatedPhoneNum) ||
				 formatPhoneNumber( pAL.get(i).getContact2_phone()).equals(formatedPhoneNum) )
				break;
		
		if(i < pAL.size())
			return pAL.get(i);
		else
			return null;
	}
	
	ONCPartner getPartnerFromSignUpActivity(int year, ONCPartner suaPartner)
	{
		List<ONCPartner> pAL = partnerDB.get(DBManager.offset(year)).getList();
		int i;
		for(i=0; i<pAL.size(); i++)
			if(pAL.get(i).matches(suaPartner))
				break;
		
		if(i < pAL.size())
			return pAL.get(i);
		else
			return null;
	}
	
	String update(int year, String json)
	{
		//Create a organization object for the updated partner
		Gson gson = new Gson();
		ONCPartner reqOrg = gson.fromJson(json, ONCPartner.class);
		
		//Find the position for the current family being replaced
		PartnerDBYear partnerDBYear = partnerDB.get(DBManager.offset(year));
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
		//Find the position for the current partner being replaced
		ONCPartner currPartner = null;
		PartnerDBYear partnerDBYear = partnerDB.get(DBManager.offset(year));
		List<ONCPartner> oAL = partnerDBYear.getList();
		int index = 0;
		while(index < oAL.size() && oAL.get(index).getID() != updatedPartner.getID())
			index++;
		
		//If partner is located and the updated partner has been changed, replace the current 
		//partner with the update. A partner's status can only be changed from confirmed to a lesser
		//status if their assigned count is zero. And, the request count can never be less then the
		//assigned count
		
//		if(index < oAL.size() && !doPartnersMatch((currPartner = oAL.get(index)), updatedPartner))
		if(index < oAL.size())
		{
			//check if a change to the partner status is allowed
			currPartner = oAL.get(index);
			int currAssignedDeliveredCount = currPartner.getNumberOfOrnamentsAssigned() + currPartner.getNumberOfOrnamentsDelivered();
			if(currPartner.getStatus() == STATUS_CONFIRMED && updatedPartner.getStatus() != STATUS_CONFIRMED &&
				currAssignedDeliveredCount > 0)	
			{
				updatedPartner.setStatus(currPartner.getStatus());	
			}
			
			//check to see if a reduction to the number of ornaments requested must be modified to
			//be no less then the higher number of ornaments already assigned or delivered to the partner
			int minRequestedCount;
			int currAssignedCount = currPartner.getNumberOfOrnamentsAssigned();
			int currDeliveredCount = currPartner.getNumberOfOrnamentsDelivered();
			
			if(currAssignedCount > currDeliveredCount)
				minRequestedCount = currAssignedCount;
			else
				minRequestedCount = currDeliveredCount;
			
			if(minRequestedCount > updatedPartner.getNumberOfOrnamentsRequested())
				updatedPartner.setNumberOfOrnamentsRequested(minRequestedCount);
			
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
	
	void updateRegion(ONCPartner updatedOrg)
	{
		//address is new or has changed, update the region
		RegionAndSchoolCode rSC = ServerRegionDB.searchForRegionMatch(new Address(updatedOrg.getHouseNum(),
					updatedOrg.getStreet(), "",  updatedOrg.getCity(), updatedOrg.getZipCode()));
		updatedOrg.setRegion(rSC.getRegion());
	}
	
	@Override
	String add(int year, String json, ONCUser client)
	{
		//Create a organization object for the updated partner
		Gson gson = new Gson();
		ONCPartner addedPartner = gson.fromJson(json, ONCPartner.class);
	
		//set the new ID for the catalog wish
		PartnerDBYear partnerDBYear = partnerDB.get(DBManager.offset(year));
		addedPartner.setID(partnerDBYear.getNextID());
		addedPartner.setDateChanged(System.currentTimeMillis());
		addedPartner.setChangedBy(client.getLNFI());
		addedPartner.setStoplightChangedBy(client.getLNFI());
		
		//set the region for the new partner
		ServerRegionDB serverRegionDB = null;
		try {
			serverRegionDB = ServerRegionDB.getInstance();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(serverRegionDB != null)
		{
			RegionAndSchoolCode rSC = ServerRegionDB.searchForRegionMatch(new Address(addedPartner.getHouseNum(), 
					addedPartner.getStreet(), "", addedPartner.getCity(),
					addedPartner.getZipCode()));
			
			addedPartner.setRegion(rSC.getRegion());
		}
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
		PartnerDBYear partnerDBYear = partnerDB.get(DBManager.offset(year));
		addedPartner.setID(partnerDBYear.getNextID());
		
		//set the region for the new partner
		ServerRegionDB serverRegionDB = null;
		try {
			serverRegionDB = ServerRegionDB.getInstance();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(serverRegionDB != null)
		{
			RegionAndSchoolCode rSC = (ServerRegionDB.searchForRegionMatch(new Address(addedPartner.getHouseNum(), 
					addedPartner.getStreet(), "", addedPartner.getCity(),
					addedPartner.getZipCode())));
		
			addedPartner.setRegion(rSC.getRegion());
		}
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
		PartnerDBYear partnerDBYear = partnerDB.get(DBManager.offset(year));
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
		PartnerDBYear partnerDBYear = partnerDB.get(DBManager.offset(year));
		List<ONCPartner> pAL = partnerDBYear.getList();
		
		int ornReq = 0;
		for(ONCPartner p : pAL)
			if(p.getStatus() == STATUS_CONFIRMED)
				ornReq += p.getNumberOfOrnamentsRequested();
		
		return ornReq;
	}

	void updateGiftAssignees(int year, ONCChildGift oldGift, ONCChildGift newGift)
	{
		PartnerDBYear partnerDBYear = partnerDB.get(DBManager.offset(year));
		
		//Find the the current partner & decrement gift assigned count if gift ornament 
		//hasn't already been delivered to the partner and the partner is found
		if(oldGift != null && oldGift.getPartnerID() > 0 
			&& oldGift.getGiftStatus().compareTo(GiftStatus.Delivered) < 0)
		{
			ONCPartner oldPartner = getPartner(year, oldGift.getPartnerID());
			if(oldPartner != null)
			{
				oldPartner.decrementOrnAssigned();
				partnerDBYear.setChanged(true);
			}
		}
	
		//Find the the new partner & increment gift assigned count if the partner is found
		if(newGift != null && newGift.getPartnerID() > 0)
		{
			ONCPartner newPartner = getPartner(year, newGift.getPartnerID());
			if(newPartner != null)
			{
				newPartner.incrementOrnAssigned();
				partnerDBYear.setChanged(true);
			}
		}	
	}
	
	void incrementGiftActionCount(int year, ONCChildGift addedGift)
	{	
		PartnerDBYear partnerDBYear = partnerDB.get(DBManager.offset(year));
		List<ONCPartner> partnerList = partnerDBYear.getList();
		
		//Find the the current partner being incremented
		int index = 0;
		while(index < partnerList.size() && partnerList.get(index).getID() != addedGift.getPartnerID())
			index++;
		
		//increment the gift received count for the partner being replaced
		if(index < partnerList.size())
		{  
			//found the partner, now determine which field to increment
			if(addedGift.getGiftStatus() == GiftStatus.Received)
			{
				boolean bReceviedBeforeDeadline = addedGift.getDateChanged().before(globalDB.getDateGiftsRecivedDealdine(year));
				partnerList.get(index).incrementOrnReceived(bReceviedBeforeDeadline);
			}
			else if(addedGift.getGiftStatus() == GiftStatus.Delivered)
				partnerList.get(index).incrementOrnDelivered();
			
			partnerDBYear.setChanged(true);
		}
	}
	
	String decrementGiftsAssignedCount(int year, int partnerID, boolean bNotifyClients)
	{
		PartnerDBYear partnerDBYear = partnerDB.get(DBManager.offset(year));
		List<ONCPartner> partnerList = partnerDBYear.getList();
		int index=0;
		while(index < partnerList.size() && partnerList.get(index).getID() != partnerID)
			index ++;
		
		//if partner was found, decrement the count. If not found, ignore the request. 
		if(index < partnerList.size())
		{
			partnerList.get(index).decrementOrnAssigned();
			partnerDBYear.setChanged(true);
			
			Gson gson = new Gson();
			String response =  "UPDATED_PARTNER" + gson.toJson(partnerList.get(index), ONCPartner.class);
			
			if(bNotifyClients)
				clientMgr.notifyAllInYearClients(year, response);
			
			return response;
		}
		
		return "FAILED_UPDATE_PARTNER";
	}

	@Override
	void addObject(int year, String[] nextLine)
	{
		PartnerDBYear partnerDBYear = partnerDB.get(DBManager.offset(year));
		partnerDBYear.add(new ONCPartner(nextLine));
	}

	@Override
	void createNewSeason(int newYear)
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
/*	
	void determinePriorYearPerformance(int year)
	{
		//get the child wish data base reference
		ServerChildGiftDB serverChildGiftDB = null;
		try {
			serverChildGiftDB = ServerChildGiftDB.getInstance();
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
		List<PriorYearPartnerPerformance> pyPartnerPerformanceList = serverChildGiftDB.getPriorYearPartnerPerformanceList(year+1);
		List<ONCPartner> pyPerfPartnerList = new ArrayList<ONCPartner>();
		
		//populate the current partner list
		for(ONCPartner p: partnerDB.get(DBManager.offset(year)).getList())
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
*/	
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
		
		PartnerDBYear partnerDBYear = partnerDB.get(DBManager.offset(year));
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
}
