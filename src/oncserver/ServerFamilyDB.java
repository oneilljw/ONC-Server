package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import ourneighborschild.Address;
import ourneighborschild.AdultGender;
import ourneighborschild.BritepathFamily;
import ourneighborschild.FamilyGiftStatus;
import ourneighborschild.FamilyStatus;
import ourneighborschild.MealStatus;
import ourneighborschild.ONCAdult;
import ourneighborschild.ONCChild;
import ourneighborschild.ONCChildWish;
import ourneighborschild.ONCFamilyHistory;
import ourneighborschild.ONCGroup;
import ourneighborschild.ONCFamily;
import ourneighborschild.ONCMeal;
import ourneighborschild.ONCServerUser;
import ourneighborschild.ONCUser;
import ourneighborschild.ONCWebChild;
import ourneighborschild.ONCWebsiteFamily;
import ourneighborschild.UserPermission;
import ourneighborschild.WishStatus;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerFamilyDB extends ServerSeasonalDB
{
	private static final int FAMILYDB_HEADER_LENGTH = 44;
	
	private static final int FAMILY_STOPLIGHT_RED = 2;
	private static final int NUMBER_OF_WISHES_PER_CHILD = 3;
	private static final String ODB_FAMILY_MEMBER_COLUMN_SEPARATOR = " - ";
	private static final int DEFAULT_GROUP_ID = 62;
	
	private static List<FamilyDBYear> familyDB;
	private static ServerFamilyDB instance = null;
	private static int highestRefNum;
	private static Map<String, ONCNumRange> oncnumRangeMap;
	
	private static ServerFamilyHistoryDB familyHistoryDB;
	private static ServerUserDB userDB;
	private static ServerChildDB childDB;
	private static ServerAdultDB adultDB;
	private static ServerMealDB mealDB;
	private static ServerGlobalVariableDB globalDB;
	
	private static ClientManager clientMgr;
	
	private ServerFamilyDB() throws FileNotFoundException, IOException
	{
		//create the ONC number range map. The map key is the school code for each school in ONC's
		//3 school pyramids. The start and end range values are based on an analysis of number 
		//of families referred from 2015 - 2018
		//THIS IS A TEMPORARY HACK FOR 2018 - NEED TO HAVE ONC NUM RANGES GENERATED AUTOMATICALLY
		//WHEN A NEW YEAR IS CREATED
		oncnumRangeMap = new HashMap<String, ONCNumRange>();
		oncnumRangeMap.put("A", new ONCNumRange(100, 250));
		oncnumRangeMap.put("B", new ONCNumRange(251, 426));
		oncnumRangeMap.put("C", new ONCNumRange(427, 477));
		oncnumRangeMap.put("D", new ONCNumRange(478, 528));
		oncnumRangeMap.put("E", new ONCNumRange(529, 539));
		oncnumRangeMap.put("F", new ONCNumRange(540, 590));
		oncnumRangeMap.put("G", new ONCNumRange(591, 741));
		oncnumRangeMap.put("H", new ONCNumRange(742, 792));
		oncnumRangeMap.put("I", new ONCNumRange(793, 1018));
		oncnumRangeMap.put("J", new ONCNumRange(1019, 1164));
		oncnumRangeMap.put("K", new ONCNumRange(1165, 1195));
		oncnumRangeMap.put("L", new ONCNumRange(1196, 1221));
		oncnumRangeMap.put("M", new ONCNumRange(1222, 1247));
		oncnumRangeMap.put("N", new ONCNumRange(1248, 1268));
		oncnumRangeMap.put("O", new ONCNumRange(1269, 1274));
		oncnumRangeMap.put("P", new ONCNumRange(1275, 1280));
		oncnumRangeMap.put("Q", new ONCNumRange(1281, 1286));
		oncnumRangeMap.put("R", new ONCNumRange(1287, 1292));
		oncnumRangeMap.put("S", new ONCNumRange(1293, 1299));
		oncnumRangeMap.put("Y", new ONCNumRange(1300, 1399));
		oncnumRangeMap.put("Z", new ONCNumRange(1400, 1499));
	
		familyDB = new ArrayList<FamilyDBYear>();
		
		//populate the family data base for the last TOTAL_YEARS from persistent store
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the family list for year
			FamilyDBYear fDBYear = new FamilyDBYear(year);
			
			//add the family list for the year to the db
			familyDB.add(fDBYear);
			
			//import the families from persistent store
			importDB(year, String.format("%s/%dDB/NewFamilyDB.csv",
					System.getProperty("user.dir"),
						year), "FamilyDB", FAMILYDB_HEADER_LENGTH);
			
			//set the next id
			fDBYear.setNextID(getNextID(fDBYear.getList()));
		}
		
		//set the reference number
		highestRefNum = initializeHighestReferenceNumber();
		
		//set references to associated data bases
		familyHistoryDB = ServerFamilyHistoryDB.getInstance();
		childDB = ServerChildDB.getInstance();
		adultDB = ServerAdultDB.getInstance();
		userDB = ServerUserDB.getInstance();
		mealDB = ServerMealDB.getInstance();
		globalDB = ServerGlobalVariableDB.getInstance();

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
		{
			ONCWebsiteFamily webFam = new ONCWebsiteFamily(searchList.get(i));
			responseList.add(webFam);
		}
		
		//sort the list by HoH last name
		Collections.sort(responseList, new ONCWebsiteFamilyLNComparator());
		
		String response = gson.toJson(responseList, listOfWebsiteFamilies);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	
	static HtmlResponse getFamiliesJSONP(int year, Integer reqAgentID, ONCServerUser loggedInUser, Integer reqGroupID, String callbackFunction)
	{	
		Gson gson = new Gson();
		Type listOfWebsiteFamilies = new TypeToken<ArrayList<ONCWebsiteFamily>>(){}.getType();
		
		List<ONCFamily> searchList = familyDB.get(year-BASE_YEAR).getList();
		ArrayList<ONCWebsiteFamily> responseList = new ArrayList<ONCWebsiteFamily>();
		
		if(reqAgentID == null || reqGroupID == null)
		{
			
		}
		else if(loggedInUser.getPermission().compareTo(UserPermission.Agent) > 0 &&
			reqAgentID < 0 && reqGroupID < 0)
		{
			//case: admin or higher login, requested agent = ANY, request group = ANY
			//can only happen if loggedInUser permission > AGENT. If the requested agent is
			//the logged-in user and their permissions are higher then Agent return all families
			for(ONCFamily f : searchList)
				responseList.add(new ONCWebsiteFamily(f));
		}
		else if(loggedInUser.getPermission().compareTo(UserPermission.Agent) > 0 &&
				reqAgentID >= 0 && reqGroupID < 0)
		{
			//case: admin or higher login, logged in user, specific agent, any group
			//return all referrals from the agent.
			for(ONCFamily f : searchList)
				if(f.getAgentID() == reqAgentID)
					responseList.add(new ONCWebsiteFamily(f));
		}
		else if(loggedInUser.getPermission().compareTo(UserPermission.Agent) > 0 &&
				reqAgentID < 0 && reqGroupID >= 0)
		{
			//case: admin or higher login, logged in user, any agent, specific group
			//return all referrals from the group.
			for(ONCFamily f : searchList)
				if(f.getGroupID() == reqGroupID)
					responseList.add(new ONCWebsiteFamily(f));
		}
		else if(loggedInUser.getPermission().compareTo(UserPermission.Agent) > 0 &&
				reqAgentID >= 0 && reqGroupID >= 0)
		{
			//case: admin or higher login, logged in user, any agent, specific group
			//return all referrals from the group.
			for(ONCFamily f : searchList)
				if(f.getAgentID() == reqAgentID && f.getGroupID() == reqGroupID)
					responseList.add(new ONCWebsiteFamily(f));
		}
		else if(loggedInUser.getPermission().compareTo(UserPermission.Agent) == 0 && 
				reqAgentID < 0 && reqGroupID >= 0)
		{
			//case: agent logged in, requested agent = ANY, specific group request
			//if logged in user is a member of the requested group and the group is sharing, 
			//return all families referred in the group
			ONCGroup group = ServerGroupDB.getGroup(reqGroupID);
			if(loggedInUser.isInGroup(reqGroupID) && group.groupSharesInfo())
			{
				for(ONCFamily f : searchList)
					if(f.getGroupID() == reqGroupID)
						responseList.add(new ONCWebsiteFamily(f));
			}
		}
		else if(loggedInUser.getPermission().compareTo(UserPermission.Agent) == 0 && 
				 reqAgentID >= 0 && reqGroupID < 0)
		{
			//case: agent logged in, specific requested agent, requested group = ANY
			//if the requested user is the logged in user, return all families referred by the agent
			//regardless of group. 
			for(ONCFamily f : searchList)
				if(f.getAgentID() == loggedInUser.getID())
					responseList.add(new ONCWebsiteFamily(f));
		}
		else if(loggedInUser.getPermission().compareTo(UserPermission.Agent) == 0 && 
				 reqAgentID >= 0 && reqGroupID >=0)
		{
			//case: agent logged in, specific requested agent, specific requested group
			//if the requested agent is the logged in user and is in the requested group, return the
			//families referred by the agent. If the requested agent is not the logged in user and the
			//requested agent is in the requested group and the group is sharing, return the requested 
			//agents referrals in the group.
			ONCGroup reqGroup = ServerGroupDB.getGroup(reqGroupID);
			ONCServerUser reqAgent = ServerUserDB.getServerUser(reqAgentID);
			if(reqAgentID == loggedInUser.getID() && loggedInUser.isInGroup(reqGroupID))
			{	
				for(ONCFamily f : searchList)
					if(f.getAgentID() == reqAgentID && f.getGroupID() == reqGroupID)
						responseList.add(new ONCWebsiteFamily(f));
			}
			else if(reqAgentID != loggedInUser.getID() && reqAgent.isInGroup(reqGroupID) && 
					reqGroup.groupSharesInfo())
			{	
				for(ONCFamily f : searchList)
					if(f.getAgentID() == reqAgentID && f.getGroupID() == reqGroupID)
						responseList.add(new ONCWebsiteFamily(f));
			}
		}
/*		
		if(loggedInUser.getID() > -1)
		{
			//add only the families referred by that agent
			for(ONCFamily f : searchList)
				if(f.getAgentID() == loggedInUser.getID())
					responseList.add(new ONCWebsiteFamily(f));
		}
		else if(loggedInUser.getID() == -1 && groupID <= -1)
		{
			//This is only allowed for users with permission > AGENT. If so, add all families 
			//referred in that year, else send back all families from the logged in agent
			for(ONCFamily f : searchList)
				responseList.add(new ONCWebsiteFamily(f));
		}
		else if(groupID > -1)
		{
			//add only the families referred by each agent in the group that referred that year
//			for(Integer userID : userDB.getUserIDsInGroup(groupID))
//				if(didAgentReferInYear(year, userID))
//					for(ONCFamily f : searchList)
//						if(f.getAgentID() == userID)
//							responseList.add(new ONCWebsiteFamily(f));
			
			//check to see if group is sharing. If it is, add all families in the group. If 
			//it's not sharing, add the families referred by the agent.
			ONCGroup group = ServerGroupDB.getGroup(groupID);
			if(group.getPermission() == ONCGroup.SHARING )
			{
				for(ONCFamily f : searchList)
					if(f.getGroupID() == groupID)
						responseList.add(new ONCWebsiteFamily(f));	
			}
			else
			{
				for(ONCFamily f : searchList)
				if(f.getAgentID() == loggedInUser.getID())
					responseList.add(new ONCWebsiteFamily(f));
			}	
		}
*/		
		//sort the list by HoH last name
		Collections.sort(responseList, new ONCWebsiteFamilyLNComparator());
		
		String response = gson.toJson(responseList, listOfWebsiteFamilies);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
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
			responseList.add(new FamilyReference(searchList.get(i).getReferenceNum()));
		
		String response = gson.toJson(responseList, listOfFamilyReferences);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	static HtmlResponse getAgentsWhoReferredJSONP(int year, ONCServerUser loggedInAgent, int groupID, String callbackFunction)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCWebAgent>>(){}.getType();
		List<ONCWebAgent> agentReferredInYearList = new LinkedList<ONCWebAgent>();
		
		List<ONCFamily> searchList = familyDB.get(year-BASE_YEAR).getList();
		
		if(loggedInAgent.getPermission().compareTo(UserPermission.Admin) >= 0 && groupID == -1)
		{
			//Admin or higher user, group selection was "All"
			for(ONCFamily f : searchList)
				if(!isInList(f.getAgentID(), agentReferredInYearList))
					agentReferredInYearList.add(new ONCWebAgent(ServerUserDB.getServerUser(f.getAgentID())));
		}
		else if(loggedInAgent.getPermission().compareTo(UserPermission.Admin) >= 0 && groupID > -1)
		{
			//Admin or higher user, specific group selected
			for(ONCFamily f : searchList)
				if(f.getGroupID() == groupID && !isInList(f.getAgentID(), agentReferredInYearList))
					agentReferredInYearList.add(new ONCWebAgent(ServerUserDB.getServerUser(f.getAgentID())));
		}
		else if(loggedInAgent.getPermission() == UserPermission.Agent && groupID > -1 &&
				ServerGroupDB.getGroup(groupID).groupSharesInfo())
		{
			//Agent user, specific group selected and group is sharing
			for(ONCFamily f : searchList)
				if(f.getGroupID() == groupID && !isInList(f.getAgentID(), agentReferredInYearList))
					agentReferredInYearList.add(new ONCWebAgent(ServerUserDB.getServerUser(f.getAgentID())));
		}
		else
		{
			//just return a list with only the user, regardless of who referred, group, etc.
			agentReferredInYearList.add(new ONCWebAgent(loggedInAgent));
		}
		
		//sort the list by name and add an "anyone" to the top of the list
		if(agentReferredInYearList.size() > 1)
			Collections.sort(agentReferredInYearList, new ONCAgentNameComparator());

		String response = gson.toJson(agentReferredInYearList, listtype);
	
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);
	}
	
	private static class ONCAgentNameComparator implements Comparator<ONCWebAgent>
	{
		@Override
		public int compare(ONCWebAgent o1, ONCWebAgent o2)
		{
			return o1.getLastname().compareTo(o2.getLastname());
		}
	}	
	
	static boolean isInList(int agtID, List<ONCWebAgent> agtList)
	{
		int index = 0;
		while(index < agtList.size() && agtList.get(index).getID() != agtID)
			index++;
		
		return index < agtList.size();
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
	    			resultList.add(new FamilyReference(f.getReferenceNum()));	
		}
		else if((s.matches("-?\\d+(\\.\\d+)?")) && s.length() < 7)
		{
			for(ONCFamily f: oncFamAL)
	    		if(s.equals(f.getReferenceNum()))
	    			resultList.add(new FamilyReference(f.getReferenceNum())); 
		}
		else if((s.startsWith("C") && s.substring(1).matches("-?\\d+(\\.\\d+)?")) && s.length() < 7)
		{
			for(ONCFamily f: oncFamAL)
	    		if(s.equals(f.getReferenceNum()))
	    			resultList.add(new FamilyReference(f.getReferenceNum())); 
		}
		else if((s.startsWith("W") && s.substring(1).matches("-?\\d+(\\.\\d+)?")) && s.length() < 6)
		{
			for(ONCFamily f: oncFamAL)
	    		if(s.equals(f.getReferenceNum()))
	    			resultList.add(new FamilyReference(f.getReferenceNum())); 
		}
		else if(s.matches("-?\\d+(\\.\\d+)?") && s.length() < 13)
		{
			for(ONCFamily f:oncFamAL)
	    	{
	    		//Ensure just 10 digits, no dashes in numbers
	    		String hp = f.getHomePhone().replaceAll("-", "");
	    		String op = f.getCellPhone().replaceAll("-", "");
	    		String target = s.replaceAll("-", "");
	    		
	    		if(hp.contains(target) || op.contains(target))
	    			resultList.add(new FamilyReference(f.getReferenceNum()));
	    	}
		}
		else
		{
			//search the family db
	    	for(ONCFamily f: oncFamAL)
	    		if(f.getClientFamily().toLowerCase().contains(s.toLowerCase()))
	    			resultList.add(new FamilyReference(f.getReferenceNum()));
	    	
	    	//search the child db
	    	childDB.searchForLastName(year, s, resultList);
		}
		
		String response = gson.toJson(resultList, listOfFamilyReferences);
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	
	/***
	 * Returns a list of Metrics that include DNS, Served, etc.
	 * @param year
	 * @param callbackFunction
	 * @return
	 */
	static HtmlResponse getFamilyMetricsJSONP(int year, String maptype, String callbackFunction)
	{		
		Gson gson = new Gson();
		
		int served = 0;
		List<Metric> metricList = new ArrayList<Metric>();

		if(maptype.equals("family"))
		{
			int dns = 0, unverified = 0, waitlist = 0, verified = 0, contacted = 0, confirmed = 0;
			
			for(ONCFamily f : familyDB.get(year-BASE_YEAR).getList())
			{
				if(!f.getDNSCode().isEmpty() || !isNumeric(f.getONCNum())) { dns++; }
				else if(f.getFamilyStatus() == FamilyStatus.Unverified) { served++; unverified++; }
				else if(f.getFamilyStatus() == FamilyStatus.Waitlist) { served++; waitlist++; }
				else if(f.getFamilyStatus() == FamilyStatus.Verified) { served++; verified++; }
				else if(f.getFamilyStatus() == FamilyStatus.Contacted) {  served++; contacted++; }
				else if(f.getFamilyStatus() == FamilyStatus.Confirmed) {served++; confirmed++; }
			}
			
			metricList.add(new Metric("DNS", dns));
			metricList.add(new Metric("Served", served));
			metricList.add(new Metric("Unverified", unverified));
			metricList.add(new Metric("Waitlist", waitlist));
			metricList.add(new Metric("Verfied", verified));
			metricList.add(new Metric("Contacted", contacted));
			metricList.add(new Metric("Confirmed", confirmed));
		}
		else if(maptype.equals("gift"))
		{
			int notreq = 0, req = 0, sel = 0, rec = 0, pck = 0, ref = 0; 
			
			for(ONCFamily f : familyDB.get(year-BASE_YEAR).getList())
			{
				if(f.getDNSCode().isEmpty() && isNumeric(f.getONCNum()))
				{
					//served families only
					if(f.getGiftStatus() == FamilyGiftStatus.NotRequested) { notreq++; }
					else if(f.getGiftStatus() == FamilyGiftStatus.Requested) { req++; }
					else if(f.getGiftStatus() == FamilyGiftStatus.Selected) { sel++; }
					else if(f.getGiftStatus() == FamilyGiftStatus.Received) { rec++; }
					
					//if gift status is PACKAGED or higher, but not REFERRED, count it as PACKAGED
					else if(f.getGiftStatus().compareTo(FamilyGiftStatus.Packaged) >= 0 &&
							f.getGiftStatus().compareTo(FamilyGiftStatus.Referred) < 0)
					{
						pck++;
					}
					else if(f.getGiftStatus() == FamilyGiftStatus.Referred) { ref++; }
				}
			}
			
			metricList.add(new Metric("Not Requested", notreq));
			metricList.add(new Metric("Requested", req));
			metricList.add(new Metric("Selected", sel));
			metricList.add(new Metric("Received", rec));
			metricList.add(new Metric("Packaged", pck));
			metricList.add(new Metric("Referred", ref));
			metricList.add(new Metric("Orn. Req", ServerPartnerDB.getOrnamentsRequested(year)));
		
		}
		else if(maptype.equals("meal"))
		{
			int notreq = 0, req = 0, assg = 0, ref = 0; 
			
			for(ONCFamily f : familyDB.get(year-BASE_YEAR).getList())
			{
				if(f.getDNSCode().isEmpty() && isNumeric(f.getONCNum()))
				{
					//served families only
					if(f.getMealStatus() == MealStatus.None) { notreq++; }
					else if(f.getMealStatus() == MealStatus.Requested) { req++; }
					else if(f.getMealStatus() == MealStatus.Assigned) { assg++; }
					else if(f.getMealStatus() == MealStatus.Referred) {ref++; }
				}	
			}
			
			metricList.add(new Metric("Not Requested", notreq));
			metricList.add(new Metric("Requested", req));
			metricList.add(new Metric("Assigned", assg));
			metricList.add(new Metric("Referred", ref));		
		}
		else if(maptype.equals("delivery"))
		{
			int assg = 0, del = 0, att = 0, ret = 0, cpu = 0;
			
			for(ONCFamily f : familyDB.get(year-BASE_YEAR).getList())
			{
				if(f.getDNSCode().isEmpty() && isNumeric(f.getONCNum()))
				{
					//served families only
					served++;
					if(f.getGiftStatus() == FamilyGiftStatus.Assigned) { assg++; }
					else if(f.getGiftStatus() == FamilyGiftStatus.Delivered) { del++; }
					else if(f.getGiftStatus() == FamilyGiftStatus.Attempted) {att++; }
					else if(f.getGiftStatus() == FamilyGiftStatus.Returned) { ret++; }
					else if(f.getGiftStatus() == FamilyGiftStatus.CounselorPickUp) { cpu++; }
				}	
			}
			
			metricList.add(new Metric("Served", served));
			metricList.add(new Metric("Assigned", assg));
			metricList.add(new Metric("Delivered", del));
			metricList.add(new Metric("Attempted", att));
			metricList.add(new Metric("Returned", ret));
			metricList.add(new Metric("Counselor PU",cpu));
		}
		
		String response = gson.toJson( new DataTable(metricList), DataTable.class);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
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
			if(updatedFamily.getReferenceNum().startsWith("C") && 
				!currFam.getReferenceNum().equals(updatedFamily.getReferenceNum()))
			{
				int updatedFamilyRefNum = Integer.parseInt(updatedFamily.getReferenceNum().substring(1));
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
				updateRegionAndSchoolCode(updatedFamily);	
			}
			
			//check if the update is requesting automatic assignment of an ONC family number
			//can only auto assign if ONC Number is not number and is not "DEL" and the
			//region is valid
			if(bAutoAssign && !updatedFamily.getONCNum().equals("DEL") && updatedFamily.getRegion() != 0 &&
						  !Character.isDigit(updatedFamily.getONCNum().charAt(0)))
			{
				updatedFamily.setONCNum(generateONCNumber(year, updatedFamily.getSchoolCode()));
			}
			
			//check to see if either status is changing, if so, add a history item
			if(currFam != null && 
				(currFam.getFamilyStatus() != updatedFamily.getFamilyStatus() || currFam.getGiftStatus() != updatedFamily.getGiftStatus()))
			{
				ONCFamilyHistory histItem = addHistoryItem(year, updatedFamily.getID(), updatedFamily.getFamilyStatus(), 
						updatedFamily.getGiftStatus(), "", "Status Changed", updatedFamily.getChangedBy(), true);
				updatedFamily.setDeliveryID(histItem.getID());
			}
			
			fAL.set(index, updatedFamily);
			fDBYear.setChanged(true);
			return "UPDATED_FAMILY" + gson.toJson(updatedFamily, ONCFamily.class);
		}
		else
			return "UPDATE_FAILED";
	}
	
	ONCFamily update(int year, ONCFamily updatedFamily, WebClient wc, boolean bAutoAssign, String updateNote)
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
				updateRegionAndSchoolCode(updatedFamily);	
			}
			
			//check if the update is requesting automatic assignment of an ONC family number
			//can only auto assign if ONC Number is not number and is not "DEL" and the
			//region is valid
			if(bAutoAssign && !updatedFamily.getONCNum().equals("DEL") && updatedFamily.getRegion() != 0 &&
						  !Character.isDigit(updatedFamily.getONCNum().charAt(0)))
			{
				updatedFamily.setONCNum(generateONCNumber(year, updatedFamily.getSchoolCode()));
			}
			
			//add a history item so change can be tracked. Changed by is the web user who made the change
			if(currFam != null) 
			{
				ONCFamilyHistory histItem = addHistoryItem(year, updatedFamily.getID(), updatedFamily.getFamilyStatus(), 
								updatedFamily.getGiftStatus(), "", updateNote, wc.getWebUser().getLNFI(), true);
				updatedFamily.setDeliveryID(histItem.getID());
			}
			
			fAL.set(index, updatedFamily);
			fDBYear.setChanged(true);
			return updatedFamily;
		}
		else
			return null;
	}
	
	RegionAndSchoolCode updateRegionAndSchoolCode(ONCFamily updatedFamily)
	{		
		//address is new or has changed, set or update the region and school code
		RegionAndSchoolCode rSC = ServerRegionDB.searchForRegionMatch(new Address(updatedFamily.getHouseNum(),
					updatedFamily.getStreet(), updatedFamily.getUnit(),
					  updatedFamily.getCity(), updatedFamily.getZipCode()));

		updatedFamily.setRegion(rSC.getRegion());
		updatedFamily.setSchoolCode(rSC.getSchoolCode());
			
		return rSC;
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
			if(addedFam.getReferenceNum().equals("NNA"))
				addedFam.setReferenceNum(generateReferenceNumber());
			
			//check to see if family is already in the data base, if so, mark it as
			//a duplicate family. 
			
			
			//set region and school code for family
			updateRegionAndSchoolCode(addedFam);
		
			//create the ONC number
			addedFam.setONCNum(generateONCNumber(year, addedFam.getSchoolCode()));
			
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
	
	String addFamilyGroup(int year, String familyGroupJson, DesktopClient currClient)
	{
		//create the response list of jsons
		List<String> jsonResponseList = new ArrayList<String>();
		
		//create list of Britepath family objects to add
		Gson gson = new Gson();
		Type listOfBritepathFamilies = new TypeToken<ArrayList<BritepathFamily>>(){}.getType();		
		List<BritepathFamily> bpFamilyList = gson.fromJson(familyGroupJson, listOfBritepathFamilies);
		
		//for each family in the list, parse it and add it to the component databases
		for(BritepathFamily bpFam:bpFamilyList)
		{
			//potentially add the referring agent as a user to the Server User DB
			ImportONCObjectResponse userResponse = userDB.processImportedReferringAgent(bpFam, currClient);
			
			//if a user was added or updated, add the change to the response list
			if(userResponse.getImportResult() != USER_UNCHANGED)
				jsonResponseList.add(userResponse.getJsonResponse());
			
			//add the family to the Family DB
			ONCFamily reqAddFam = new ONCFamily(bpFam.referringAgentName, bpFam.referringAgentOrg,
				bpFam.referringAgentTitle, bpFam.clientFamily, bpFam.headOfHousehold,
				bpFam.familyMembers, bpFam.referringAgentEmail, bpFam.clientFamilyEmail, 
				bpFam.clientFamilyPhone, bpFam.referringAgentPhone, bpFam.dietartyRestrictions,
				bpFam.schoolsAttended, bpFam.details, bpFam.assigneeContactID,
				bpFam.deliveryStreetAddress, bpFam.deliveryAddressLine2, bpFam.deliveryCity,
				bpFam.deliveryZip, bpFam.deliveryState, bpFam.adoptedFor, bpFam.numberOfAdults,
				bpFam.numberOfChildren, bpFam.wishlist, bpFam.speaksEnglish, bpFam.language,
				bpFam.hasTransportation, bpFam.batchNum, new Date(), -1, "NNA", -1, 
				currClient.getClientUser().getLNFI(), -1, DEFAULT_GROUP_ID);
			
			ONCFamily addedFam = add(year, reqAddFam);
			if(addedFam != null)
			{
				//if family was added successfully, add the family history object 
				//and update the family history object id
				ONCFamilyHistory famHistory = new ONCFamilyHistory(-1, addedFam.getID(), 
																addedFam.getFamilyStatus(),
																addedFam.getGiftStatus(),
																"", "Family Referred thru Britepaths", 
																addedFam.getChangedBy(), 
																Calendar.getInstance(TimeZone.getTimeZone("UTC")));
			
				ONCFamilyHistory addedFamHistory = familyHistoryDB.addFamilyHistoryObject(year, famHistory, false);
				if(addedFamHistory != null)
					addedFam.setDeliveryID(addedFamHistory.getID());
				
				//if the family was added successfully, add the history item, adults and children
				jsonResponseList.add("ADDED_FAMILY" + gson.toJson(addedFam, ONCFamily.class));
				
				if(addedFamHistory != null)
					jsonResponseList.add("ADDED_DELIVERY" + gson.toJson(addedFamHistory, ONCFamilyHistory.class));
		
				String[] members = bpFam.getFamilyMembers().trim().split("\n");					
				for(int i=0; i<members.length; i++)
				{
					if(!members[i].isEmpty() && members[i].toLowerCase().contains("adult"))
					{
						//create the add adult request object
						String[] adult = members[i].split(ODB_FAMILY_MEMBER_COLUMN_SEPARATOR, 3);
						if(adult.length == 3)
						{
							//determine the gender, could be anything from Britepaths!
							AdultGender gender;
							if(adult[1].toLowerCase().contains("female") || adult[1].toLowerCase().contains("girl"))
								gender = AdultGender.Female;
							else if(adult[1].toLowerCase().contains("male") || adult[1].toLowerCase().contains("boy"))
								gender = AdultGender.Male;
							else
								gender = AdultGender.Unknown;
												
							ONCAdult reqAddAdult = new ONCAdult(-1, addedFam.getID(), adult[0], gender);
							
							//interact with the server to add the adult
							String addedAdultJson = gson.toJson(adultDB.add(year, reqAddAdult));
							jsonResponseList.add("ADDED_ADULT" + addedAdultJson);
						}
					}
					else if(!members[i].isEmpty())
					{
						//create the add child request object
						ONCChild reqAddChild = new ONCChild(-1, addedFam.getID(), members[i], year);
							
						//interact with the server to add the child
						String addedChildJson = gson.toJson(childDB.add(year, reqAddChild));
						jsonResponseList.add("ADDED_CHILD" + addedChildJson);
					}
				}
			}
		}
		
		//notify all other clients of the imported agent, family, adult and child objects
		clientMgr.notifyAllOtherInYearClients(currClient, jsonResponseList);
		
		Type listOfChanges = new TypeToken<ArrayList<String>>(){}.getType();
		return "ADDED_BRITEPATH_FAMILIES" + gson.toJson(jsonResponseList, listOfChanges);
	}
	
	ONCFamily add(int year, ONCFamily addedFam)
	{
		if(addedFam != null)
		{
			//get the family data base for the correct year
			FamilyDBYear fDBYear = familyDB.get(year - BASE_YEAR);
			
			//set region and school code for family
			updateRegionAndSchoolCode(addedFam);
			
			//create the ONC number
			String newONCNum = generateONCNumber(year, addedFam.getSchoolCode());
			addedFam.setONCNum(newONCNum);
			
			//set the new ID for the added family
			int famID = fDBYear.getNextID();
			addedFam.setID(famID);
			
			String targetID = addedFam.getReferenceNum();
			if(targetID.contains("NNA") || targetID.equals(""))
			{
				targetID = generateReferenceNumber();
				addedFam.setReferenceNum(targetID);
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
	
	String addFamilyHistoryList(int year, String historyGroupJson)
	{
		ClientManager clientMgr = ClientManager.getInstance();
		
		//un-bundle to list of ONCFamilyHistory objects
		Gson gson = new Gson();
		Type listOfHistoryObjects = new TypeToken<ArrayList<ONCFamilyHistory>>(){}.getType();
		
		List<ONCFamilyHistory> famHistoryList = gson.fromJson(historyGroupJson, listOfHistoryObjects);
		
		ServerFamilyHistoryDB famHistDB;
		try 
		{
			famHistDB = ServerFamilyHistoryDB.getInstance();
			
			//for each history object in the list, add it to the history database 
			for(ONCFamilyHistory reqFamHistoryObj:famHistoryList)
			{
				ONCFamilyHistory addedFamHistObj = famHistDB.addFamilyHistoryObject(year, reqFamHistoryObj, false);
				
				//find the family
				FamilyDBYear fDBYear = familyDB.get(year - BASE_YEAR);
				ONCFamily updatedFam = (ONCFamily) find(fDBYear.getList(), addedFamHistObj.getFamID());
				
				if(updatedFam != null)
				{
					updatedFam.setDeliveryID(addedFamHistObj.getID());
					updatedFam.setFamilyStatus(addedFamHistObj.getFamilyStatus());
					fDBYear.setChanged(true);
					
					//if update was successful, need to q the change to all in-year clients
					//notify in year clients of change
					String response = "UPDATED_FAMILY" + gson.toJson(updatedFam, ONCFamily.class);
					clientMgr.notifyAllInYearClients(year, response);
					response = "ADDED_DELIVERY"+ gson.toJson(addedFamHistObj, ONCFamilyHistory.class);
					clientMgr.notifyAllInYearClients(year, response);
				}
			}
			
			return "ADDED_GROUP_DELIVERIES";
		}
		catch (FileNotFoundException e) 
		{
			return "ADD_GROUP_DELIVERIES_FAILED";
		} 
		catch (IOException e) 
		{
			return "ADD_GROUP_DELIVERIES_FAILED";
		}
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
			List<ONCChild> famChildrenToCheck = childDB.getChildList(year, famToCheck.getID());
			
			ONCFamily dupFamily = getDuplicateFamily(year, famToCheck, famChildrenToCheck);
			if(dupFamily != null)
			{
				//family to check is a duplicate, mark them as such and notify clients
				//update the other. Use reference # to determine
				famToCheck.setONCNum("DEL");
				famToCheck.setDNSCode("DUP");
				famToCheck.setStoplightPos(FAMILY_STOPLIGHT_RED);
				famToCheck.setStoplightMssg("DUP of " + dupFamily.getReferenceNum());
				famToCheck.setStoplightChangedBy(user.getLNFI());
				famToCheck.setReferenceNum(dupFamily.getReferenceNum());
				
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
	
	static HtmlResponse getFamilyJSONP(int year, String targetID, boolean bIncludeSchools, String callbackFunction)
	{		
		Gson gson = new Gson();
		String response;
	
		List<ONCFamily> fAL = familyDB.get(year-BASE_YEAR).getList();
		
		int index=0;
		while(index<fAL.size() && !fAL.get(index).getReferenceNum().equals(targetID))
			index++;
		
		if(index<fAL.size())
		{
			ONCFamily fam = fAL.get(index);
			
			//get a list of the children, list of adults, agent and meal, if there is a meal
			List<ONCWebChild> childList = childDB.getWebChildList(year, fam.getID(), bIncludeSchools);
			List<ONCAdult> adultList = adultDB.getAdultsInFamily(year, fam.getID());
			ONCWebAgent famAgent = userDB.getWebAgent(fam.getAgentID());
			ONCMeal famMeal = fam.getMealID() > -1 ? mealDB.getMeal(year,  fam.getMealID()) : null;
			
			ONCWebsiteFamilyExtended webFam = new ONCWebsiteFamilyExtended(fam,
													ServerRegionDB.getRegion(fam.getRegion()),
													ServerGroupDB.getGroup(fam.getGroupID()).getName(),
													childList, adultList, famAgent, famMeal);
			
			response = gson.toJson(webFam, ONCWebsiteFamilyExtended.class);
		}
		else
			response = "";
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
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
			return fAL.get(index).getReferenceNum();
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
		while(index < fAL.size() && !fAL.get(index).getReferenceNum().equals(targetID))
			index++;
		
		if(index < fAL.size())
			return fAL.get(index);
		else
			return null;
	}
	
	void checkFamilyGiftStatusAndGiftCardOnlyOnWishAdded(int year, int childid)
	{
		int famID = childDB.getChildsFamilyID(year, childid);
		
		ONCFamily fam = getFamily(year, famID);
		
	    //determine the proper family gift status for the family after adding the wish. If the
		//family gifts have already been packaged, then don't perform the test
	    FamilyGiftStatus newGiftStatus;
	    if(fam.getGiftStatus().compareTo(FamilyGiftStatus.Packaged) < 0)
	    	newGiftStatus = getLowestGiftStatus(year, famID);
	    else
	    	newGiftStatus = fam.getGiftStatus();
	    
	    //determine if the families gift card only status after adding the wish
	    boolean bNewGiftCardOnlyFamily = isGiftCardOnlyFamily(year, famID);
	   
	    //if gift status has changed, update the data base and notify clients
	    if(newGiftStatus != fam.getGiftStatus() || bNewGiftCardOnlyFamily != fam.isGiftCardOnly())
	    {
	    		if(newGiftStatus != fam.getGiftStatus())
	    		{
	    			//create a family history change
	    			fam.setGiftStatus(newGiftStatus);
	    			ONCFamilyHistory addedHistItem = addHistoryItem(year, fam.getID(), fam.getFamilyStatus(), newGiftStatus, 
						"", "Gift Status Change", fam.getChangedBy(), true);
	    			fam.setDeliveryID(addedHistItem.getID());
	    		}
	    	
	    		if(bNewGiftCardOnlyFamily != fam.isGiftCardOnly())
	    			fam.setGiftCardOnly(bNewGiftCardOnlyFamily);
	    	
	    		familyDB.get(year - BASE_YEAR).setChanged(true);
	    	
	    		Gson gson = new Gson();
	    		String change = "UPDATED_FAMILY" + gson.toJson(fam, ONCFamily.class);
	    		clientMgr.notifyAllInYearClients(year, change);	//null to notify all clients
	    }
	}
	
	ONCFamilyHistory addHistoryItem(int year, int famID, FamilyStatus fs, FamilyGiftStatus fgs, String driverID,
						String reason, String changedBy, boolean bNotify)
	{
		ONCFamilyHistory reqFamHistObj = new ONCFamilyHistory(-1, famID, fs, fgs, driverID, reason,
				 changedBy, Calendar.getInstance(TimeZone.getTimeZone("UTC")));
		
		ONCFamilyHistory addedFamHistory = familyHistoryDB.addFamilyHistoryObject(year, reqFamHistObj, bNotify);
		
		return addedFamHistory;
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
		if(fam != null)
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
//		int giftCardID = ServerWishCatalog.findWishIDByName(year, GIFT_CARD_WISH_NAME);
		int giftCardID = globalDB.getGiftCardID(year);
		if(giftCardID == -1)
			bGiftCardOnlyFamily = false;
		else
		{	
			List<ONCChild> childList = childDB.getChildList(year, famid);	//get the children in the family
		
			//examine each child to see if their assigned gifts are gift cards. If gift is not
			//assigned or not a gift card, then it's not a gift card only family
			int childindex=0;
			while(childindex < childList.size() && bGiftCardOnlyFamily)
			{
				ONCChild c = childList.get(childindex++);	//get the child
				
				//if all gifts aren't assigned, then not a gift card only family
				int wn = 0;
				while(wn < NUMBER_OF_WISHES_PER_CHILD && bGiftCardOnlyFamily)
					if(c.getChildGiftID(wn++) == -1)
						bGiftCardOnlyFamily = false;
				
				//if all are assigned, examine each gift to see if it's a gift card
				int giftindex = 0;
				while(giftindex < NUMBER_OF_WISHES_PER_CHILD && bGiftCardOnlyFamily)
				{
					ONCChildWish cw = ServerChildWishDB.getWish(year, c.getChildGiftID(giftindex++));
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
	FamilyGiftStatus getLowestGiftStatus(int year, int famid)
	{
		//This matrix correlates a child wish status to the family status.
		FamilyGiftStatus[] wishstatusmatrix = {FamilyGiftStatus.Requested,	//WishStatus Index = 0;
								FamilyGiftStatus.Requested,	//WishStatus Index = 1;
								FamilyGiftStatus.Selected,	//WishStatus Index = 2;
								FamilyGiftStatus.Selected,	//WishStatus Index = 3;
								FamilyGiftStatus.Selected,	//WishStatus Index = 4;
								FamilyGiftStatus.Selected,	//WishStatus Index = 5;
								FamilyGiftStatus.Selected,	//WishStatus Index = 6;
								FamilyGiftStatus.Received,	//WishStatus Index = 7;
								FamilyGiftStatus.Received,	//WishStatus Index = 8;
								FamilyGiftStatus.Selected,	//WishStatus Index = 9;
								FamilyGiftStatus.Verified};	//WishStatus Index = 10;
			
		//Check for all gifts selected
		FamilyGiftStatus lowestfamstatus = FamilyGiftStatus.Verified;
		for(ONCChild c : childDB.getChildList(year, famid))
		{
			for(int wn=0; wn< NUMBER_OF_WISHES_PER_CHILD; wn++)
			{
				ONCChildWish cw = ServerChildWishDB.getWish(year, c.getChildGiftID(wn));
				
				//if cw is null, it means that the wish doesn't exist yet. If that's the case, 
				//set the status to the lowest status possible as if the wish existed
				WishStatus childwishstatus = WishStatus.Not_Selected;	//Lowest possible child wish status
				if(cw != null)
					childwishstatus = ServerChildWishDB.getWish(year, c.getChildGiftID(wn)).getChildWishStatus();
					
				if(wishstatusmatrix[childwishstatus.statusIndex()].compareTo(lowestfamstatus) < 0)
					lowestfamstatus = wishstatusmatrix[childwishstatus.statusIndex()];
			}
		}
			
		return lowestfamstatus;
	}
	
	void updateFamilyHistory(int year, ONCFamilyHistory addedHistObj)
	{
		//find the family
		FamilyDBYear famDBYear = familyDB.get(year - BASE_YEAR);
		ONCFamily fam = getFamily(year, addedHistObj.getFamID());
		
		//update the delivery ID and delivery status
		fam.setDeliveryID(addedHistObj.getID());
		fam.setGiftStatus(addedHistObj.getGiftStatus());
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

//		Calendar date_changed = Calendar.getInstance();	//No date_changed in ONCFamily yet
//		if(!nextLine[6].isEmpty())
//			date_changed.setTimeInMillis(Long.parseLong(nextLine[6]));
			
		famDBYear.add(new ONCFamily(nextLine));
	}
	
	void save(int year)
	{
		String[] header = {"ONC ID", "ONCNum", "Region", "School Code", "ODB Family #", "Batch #", 
				"DNS Code", "Family Status", "Delivery Status",
				"Speak English?","Language if No", "Caller", "Notes", "Delivery Instructions",
				"Client Family", "First Name", "Last Name", "House #", "Street", "Unit #", "City", "Zip Code",
				"Substitute Delivery Address", "All Phone #'s", "Home Phone", "Other Phone", "Family Email", 
				"ODB Details", "Children Names", "Schools", "ODB WishList", "Adopted For",
				"Agent ID", "GroupID", "Delivery ID", "Meal ID", "Meal Status", "# of Bags", "# of Large Items", 
				"Stoplight Pos", "Stoplight Mssg", "Stoplight C/B", "Transportation", "Gift Card Only"};
		
		FamilyDBYear fDBYear = familyDB.get(year - BASE_YEAR);
		if(fDBYear.isUnsaved())
			
		{
			String path = String.format("%s/%dDB/NewFamilyDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(fDBYear.getList(), header, path);
			fDBYear.setChanged(false);
		}
	}
	
	static boolean didAgentReferInYear(int year, int agentID)
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
				bAddressHadUnit = !famList.get(index).getUnit().trim().isEmpty();
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
		while(index < famList.size() && !famList.get(index).getReferenceNum().equals(targetID))
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
     * number already assigned. The method uses the school code string passed (after checking
     * to see if it is in the valid range) and indexes into the oncnumRegionRanges array to get
     * the starting ONC number for the region. It then queries the family array to see if that
     * number is already in use. If it's in use, it goes to the next number to check again. It 
     * continues until it finds an unused number or reaches the end of the range for that region.
     * The first unused ONC number in the range is returned. If the end of the range is reached 
     * and all numbers have been assigned, it will display an error dialog and after the user
     * acknowledges the error, it will return string "OOR" for out of range.
     * If the region isn't valid, it will complain and then return the string "RNV" for region
     * not valid 
     * @param schoolCode - String
     * @return
     ********************************************************************************************/
    String generateONCNumber(int year, String schoolCode)
    {
    		String oncNum = null;
    	
		if(oncnumRangeMap.containsKey(schoolCode))
		{
			int start = oncnumRangeMap.get(schoolCode).getStart();
			int	end = oncnumRangeMap.get(schoolCode).getEnd();
			
			String searchstring = Integer.toString(start);
			while(start < end && searchForONCNumber(year, searchstring) != -1)
				searchstring = Integer.toString(++start);
			
			if(start==end)
			{
				oncNum = "OOR";		//Not enough size in range
			}
			else
				oncNum = Integer.toString(start);
		}
		else
			oncNum = "NNA";
		
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
    		
    		List<ONCChild> dupChildList = childDB.getChildList(year, dupFamily.getID());
    		
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
    			List<ONCChild> pyChildList = childDB.getChildList(yearIndex, pyFamily.getID());
    			
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
    	
    	return checkFamily.getFirstName().equalsIgnoreCase(addedFamily.getFirstName()) &&
    			checkFamily.getLastName().equalsIgnoreCase(addedFamily.getLastName()) &&
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
    			if(f.getReferenceNum().startsWith("C"))
    			{
    				int refNum = Integer.parseInt(f.getReferenceNum().substring(1));
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
    			ServerFamilyHistoryDB deliveryDB = null;
    			try {
    				deliveryDB = ServerFamilyHistoryDB.getInstance();
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
    		if(f.getDeliveryID() > -1 && f.getGiftStatus().compareTo(FamilyGiftStatus.Assigned) >= 0)
    		{
    			//get delivery for family
    			ONCFamilyHistory del = deliveryDB.getHistoryObject(year, f.getDeliveryID());
    			if(del != null && del.getdDelBy().equals(drvNum))
    				delCount++;
    		}
    	}
    	
    	return delCount;
    }
/*  
    void convertFamilyDBForStatusChanges(int year)
    {
    	String[] header, nextLine;
    	List<String[]> outputList = new ArrayList<String[]>();
    	
    	//open the current year file
    	String path = String.format("%s/%dDB/FamilyDB.csv", System.getProperty("user.dir"), year);
    	CSVReader reader;
		try 
		{
			reader = new CSVReader(new FileReader(path));
			if((header = reader.readNext()) != null)	//Does file have records? 
	    	{
	    		//Read the User File
	    		if(header.length == FAMILYDB_HEADER_LENGTH)	//Does the record have the right # of fields? 
	    		{
	    			while ((nextLine = reader.readNext()) != null)	// nextLine[] is an array of fields from the record
	    			{
	    				NewFamStatus nfs = getNewFamStatus(nextLine[6], nextLine[7]);
	    				nextLine[6] = nfs.getNewFamStatus();
	    				nextLine[7] = nfs.getNewGiftStatus();
	    				outputList.add(nextLine);
	    			}
	    		}
	    		else
	    		{
	    			String error = String.format("%s file corrupted, header length = %d", path, header.length);
	    	       	JOptionPane.showMessageDialog(null, error,  path + "Corrupted", JOptionPane.ERROR_MESSAGE);
	    		}		   			
	    	}
	    	else
	    	{
	    		String error = String.format("%s file is empty", path);
	    		JOptionPane.showMessageDialog(null, error,  path + " Empty", JOptionPane.ERROR_MESSAGE);
	    	}
	    	
	    	reader.close();
	    	
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
		
		//now that we should have an output list of converted String[] for each family, write it
		
		String[] outHeader = {"ONC ID", "ONCNum", "Region", "ODB Family #", "Batch #", "DNS Code", "Family Status", "Delivery Status",
				"Speak English?","Language if No", "Caller", "Notes", "Delivery Instructions",
				"Client Family", "First Name", "Last Name", "House #", "Street", "Unit #", "City", "Zip Code",
				"Substitute Delivery Address", "All Phone #'s", "Home Phone", "Other Phone", "Family Email", 
				"ODB Details", "Children Names", "Schools", "ODB WishList",
				"Adopted For", "Agent ID", "Delivery ID", "Meal ID", "Meal Status", "# of Bags", "# of Large Items", 
				"Stoplight Pos", "Stoplight Mssg", "Stoplight C/B", "Transportation", "Gift Card Only"};
		
	//	System.out.println(String.format("FamilyDB saveDB - Saving %d New Family DB", year));
		String outPath = String.format("%s/%dDB/NewFamilyDB.csv", System.getProperty("user.dir"), year);
		
		 try 
		    {
		    	CSVWriter writer = new CSVWriter(new FileWriter(outPath));
		    	writer.writeNext(outHeader);
		    	 
		    	for(int index=0; index < outputList.size(); index++)
		    		writer.writeNext(outputList.get(index));
		    	
		    	writer.close();
		    	       	    
		    } 
		    catch (IOException x)
		    {
		    	System.err.format("IO Exception: %s%n", x);
		    }	
    }
*/    
    /***
     * Used to convert ServerFamilyDB from old family status & gift status to new
     * @param ofs
     * @param ogs
     * @return
     */
/*    
    NewFamStatus getNewFamStatus(String ofs, String ogs)
    {
    	int oldFamStatus = Integer.parseInt(ofs);
    	int oldGiftStatus = Integer.parseInt(ogs);
    	
    	if(oldFamStatus == 0 && oldGiftStatus == 0)
    		return new NewFamStatus(0,1);
    	else if(oldFamStatus == 0 && oldGiftStatus == 1)
    		return new NewFamStatus(0,1);
    	else if(oldFamStatus == 0 && oldGiftStatus == 2)
    		return new NewFamStatus(0,1);
    	else if(oldFamStatus == 0 && oldGiftStatus == 3)
    		return new NewFamStatus(0,1);
    	else if(oldFamStatus == 0 && oldGiftStatus == 4)
    		return new NewFamStatus(0,1);
    	else if(oldFamStatus == 0 && oldGiftStatus == 5)
    		return new NewFamStatus(0,1);
    	else if(oldFamStatus == 0 && oldGiftStatus == 6)
    		return new NewFamStatus(0,1);
    	else if(oldFamStatus == 0 && oldGiftStatus == 7)
    		return new NewFamStatus(0,1);
    	else if(oldFamStatus == 0 && oldGiftStatus == 8)
    		return new NewFamStatus(0,0);
    	else if(oldFamStatus == 1 && oldGiftStatus == 0)
    		return new NewFamStatus(1,1);
    	else if(oldFamStatus == 1 && oldGiftStatus == 1)
    		return new NewFamStatus(1,1);
    	else if(oldFamStatus == 1 && oldGiftStatus == 2)
    		return new NewFamStatus(1,1);
    	else if(oldFamStatus == 1 && oldGiftStatus == 3)
    		return new NewFamStatus(1,1);
    	else if(oldFamStatus == 1 && oldGiftStatus == 4)
    		return new NewFamStatus(1,1);
    	else if(oldFamStatus == 1 && oldGiftStatus == 5)
    		return new NewFamStatus(1,1);
    	else if(oldFamStatus == 1 && oldGiftStatus == 6)
    		return new NewFamStatus(1,1);
    	else if(oldFamStatus == 1 && oldGiftStatus == 7)
    		return new NewFamStatus(1,1);
    	else if(oldFamStatus == 1 && oldGiftStatus == 8)
    		return new NewFamStatus(1,0);
    	else if(oldFamStatus == 2 && oldGiftStatus == 0)
    		return new NewFamStatus(1,2);
    	else if(oldFamStatus == 2 && oldGiftStatus == 1)
    		return new NewFamStatus(2,2);
    	else if(oldFamStatus == 2 && oldGiftStatus == 2)
    		return new NewFamStatus(3,2);
    	else if(oldFamStatus == 2 && oldGiftStatus == 3)
    		return new NewFamStatus(3,6);
    	else if(oldFamStatus == 2 && oldGiftStatus == 4)
    		return new NewFamStatus(3,8);
    	else if(oldFamStatus == 2 && oldGiftStatus == 5)
    		return new NewFamStatus(3,9);
    	else if(oldFamStatus == 2 && oldGiftStatus == 6)
    		return new NewFamStatus(3,7);
    	else if(oldFamStatus == 2 && oldGiftStatus == 7)
    		return new NewFamStatus(3,10);
    	else if(oldFamStatus == 2 && oldGiftStatus == 8)
    		return new NewFamStatus(1,0);
    	else if(oldFamStatus == 3 && oldGiftStatus == 0)
    		return new NewFamStatus(1,3);
    	else if(oldFamStatus == 3 && oldGiftStatus == 1)
    		return new NewFamStatus(2,3);
    	else if(oldFamStatus == 3 && oldGiftStatus == 2)
    		return new NewFamStatus(3,3);
    	else if(oldFamStatus == 3 && oldGiftStatus == 3)
    		return new NewFamStatus(3,6);
    	else if(oldFamStatus == 3 && oldGiftStatus == 4)
    		return new NewFamStatus(3,8);
    	else if(oldFamStatus == 3 && oldGiftStatus == 5)
    		return new NewFamStatus(3,0);
    	else if(oldFamStatus == 3 && oldGiftStatus == 6)
    		return new NewFamStatus(3,7);
    	else if(oldFamStatus == 3 && oldGiftStatus == 7)
    		return new NewFamStatus(3,10);
    	else if(oldFamStatus == 3 && oldGiftStatus == 8)
    		return new NewFamStatus(1,0);
    	else if(oldFamStatus == 4 && oldGiftStatus == 0)
    		return new NewFamStatus(1,4);
    	else if(oldFamStatus == 4 && oldGiftStatus == 1)
    		return new NewFamStatus(2,4);
    	else if(oldFamStatus == 4 && oldGiftStatus == 2)
    		return new NewFamStatus(3,4);
    	else if(oldFamStatus == 4 && oldGiftStatus == 3)
    		return new NewFamStatus(3,6);
    	else if(oldFamStatus == 4 && oldGiftStatus == 4)
    		return new NewFamStatus(3,8);
    	else if(oldFamStatus == 4 && oldGiftStatus == 5)
    		return new NewFamStatus(3,9);
    	else if(oldFamStatus == 4 && oldGiftStatus == 6)
    		return new NewFamStatus(3,7);
    	else if(oldFamStatus == 4 && oldGiftStatus == 7)
    		return new NewFamStatus(3,10);
    	else if(oldFamStatus == 4 && oldGiftStatus == 8)
    		return new NewFamStatus(1,0);
    	else if(oldFamStatus == 5 && oldGiftStatus == 0)
    		return new NewFamStatus(1,5);
    	else if(oldFamStatus == 5 && oldGiftStatus == 1)
    		return new NewFamStatus(2,5);
    	else if(oldFamStatus == 5 && oldGiftStatus == 2)
    		return new NewFamStatus(3,5);
    	else if(oldFamStatus == 5 && oldGiftStatus == 3)
    		return new NewFamStatus(3,6);
    	else if(oldFamStatus == 5 && oldGiftStatus == 4)
    		return new NewFamStatus(3,8);
    	else if(oldFamStatus == 5 && oldGiftStatus == 5)
    		return new NewFamStatus(3,9);
    	else if(oldFamStatus == 5 && oldGiftStatus == 6)
    		return new NewFamStatus(3,7);
    	else if(oldFamStatus == 5 && oldGiftStatus == 7)
    		return new NewFamStatus(3,10);
    	else if(oldFamStatus == 5 && oldGiftStatus == 8)
    		return new NewFamStatus(1,0); 
    	else
    		return null;
    }
*/
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
/*    
    private class NewFamStatus
    {
    		private int famStatus;
    		private int giftStatus;
    	
    		NewFamStatus(int famStatus, int giftStatus)
    		{
    			this.famStatus = famStatus;
    			this.giftStatus = giftStatus;
    		}
    	
    		String getNewFamStatus() { return Integer.toString(famStatus); }
    		String getNewGiftStatus() { return Integer.toString(giftStatus); }
    }
*/    
    private class ONCNumRange
    {
    		int start;
    		int end;
    		
    		ONCNumRange(int start, int end)
    		{
    			this.start = start;
    			this.end = end;
    		}
    		
    		//getters
    		int getStart() { return start; }
    		int getEnd() { return end; }
    }
}