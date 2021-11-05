package oncserver;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;

import ourneighborschild.Address;
import ourneighborschild.AdultGender;
import ourneighborschild.BritepathFamily;
import ourneighborschild.DNSCode;
import ourneighborschild.FamilyGiftStatus;
import ourneighborschild.FamilyStatus;
import ourneighborschild.MealStatus;
import ourneighborschild.ONCAdult;
import ourneighborschild.ONCChild;
import ourneighborschild.ONCChildGift;
import ourneighborschild.FamilyHistory;
import ourneighborschild.FamilyPhoneInfo;
import ourneighborschild.ONCGroup;
import ourneighborschild.ONCFamily;
import ourneighborschild.ONCMeal;
import ourneighborschild.ONCServerUser;
import ourneighborschild.ONCUser;
import ourneighborschild.ONCWebChild;
import ourneighborschild.ONCWebsiteFamily;
import ourneighborschild.PhoneInfo;
import ourneighborschild.UserPermission;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.itextpdf.barcodes.BarcodeEAN;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.property.AreaBreakType;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.property.VerticalAlignment;

public class ServerFamilyDB extends ServerSeasonalDB
{
	private static final int FAMILYDB_HEADER_LENGTH = 41;
	
//	private static final int FAMILY_STOPLIGHT_RED = 2;
//	private static final int NUMBER_OF_WISHES_PER_CHILD = 2;	//COVID 19 - Two gifts/child not three
	private static final String ODB_FAMILY_MEMBER_COLUMN_SEPARATOR = " - ";
	private static final int DEFAULT_GROUP_ID = 62;
	private static final int DNS_WAITLIST_CODE = 1;
	private static final int[] PHONECODES = {1,3,4,12,16,48};
	private static final int[] PHONECODE_CLEAR_MASK = {60,51,15};
	
	private static List<FamilyDBYear> familyDB;
	private static ServerFamilyDB instance = null;
	private static int highestRefNum;
	private static Map<String, ONCNumRange> oncnumRangeMap;
	
	private static ServerFamilyHistoryDB familyHistoryDB;
	private static ServerUserDB userDB;
	private static ServerChildDB childDB;
	private static ServerGiftCatalog cat;
	private static ServerAdultDB adultDB;
	private static ServerMealDB mealDB;
	private static ServerGlobalVariableDB globalDB;
	private static ServerDNSCodeDB dnsCodeDB;
	private static ServerRegionDB regionDB;
	
	private static ClientManager clientMgr;
	
	private ServerFamilyDB() throws FileNotFoundException, IOException
	{
		//create the ONC number range map. The map key is the school code for each school in ONC's
		//3 school pyramids. The start and end range values are based on an analysis of number 
		//of families referred from 2015 - 2018
		//In 2021, Franklin MS is in both the Chantilly and Oakton Pyramids. The ES in in
		//ONC zip codes (22033) is Waples Mill, which now has school code T and feeds both Chantilly
		//and Oakton.
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
		oncnumRangeMap.put("T", new ONCNumRange(1300, 1324));	//Added in 2021 for Franklin MS issue
		oncnumRangeMap.put("Y", new ONCNumRange(1325, 1399));
		oncnumRangeMap.put("Z", new ONCNumRange(1400, 1499));
	
		familyDB = new ArrayList<FamilyDBYear>();
		
		//populate the family data base for the last TOTAL_YEARS from persistent store
		for(int year = DBManager.getBaseSeason(); year < DBManager.getBaseSeason() + DBManager.getNumberOfYears(); year++)
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
		cat = ServerGiftCatalog.getInstance();
		adultDB = ServerAdultDB.getInstance();
		userDB = ServerUserDB.getInstance();
		mealDB = ServerMealDB.getInstance();
		globalDB = ServerGlobalVariableDB.getInstance();
		dnsCodeDB = ServerDNSCodeDB.getInstance();
		regionDB = ServerRegionDB.getInstance();

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
		
		String response = gson.toJson(familyDB.get(DBManager.offset(year)).getList(), listOfFamilies);
		return response;	
	}

	static HtmlResponse getFamiliesJSONP(int year, ONCServerUser loggedInUser, String callbackFunction)
	{	
		Gson gson = new Gson();
		Type listOfWebsiteFamilies = new TypeToken<ArrayList<ONCWebsiteFamily>>(){}.getType();
		
		List<ONCFamily> currentSeasonList = familyDB.get(DBManager.offset(DBManager.getCurrentSeason())).getList();
		List<ONCFamily> searchList = familyDB.get(DBManager.offset(year)).getList();
		List<ONCGroup> agentsGroupList = ServerGroupDB.getGroupList(loggedInUser);
		
		ArrayList<ONCWebsiteFamily> responseList = new ArrayList<ONCWebsiteFamily>();
		
		if(loggedInUser.getPermission().compareTo(UserPermission.Agent) > 0)
		{
			//case: admin or higher login, requested agent = ANY, request group = ANY
			//can only happen if loggedInUser permission > AGENT. If the requested agent is
			//the logged-in user and their permissions are higher then Agent return all families			
			for(ONCFamily f : searchList)
			{
				FamilyHistory fh = ServerFamilyHistoryDB.getCurrentFamilyHistory(year, f.getID());
				ONCMeal meal = mealDB.getFamiliesCurrentMeal(year, f.getID());
				boolean bAlreadyReferred = year == DBManager.getCurrentSeason() ? true : alreadyReferredInCurrentSeason(year, f, currentSeasonList);
				
				responseList.add(new ONCWebsiteFamily(f, fh, bAlreadyReferred, dnsCodeDB.getDNSCode(fh.getDNSCode()), meal,
									ServerNoteDB.lastNoteStatus(year, f.getID())));
			}
		}
		else if(loggedInUser.getPermission().compareTo(UserPermission.Agent) == 0)
		{
			//case: agent is the logged in user, return all referrals from the agent and any referrals
			//from shared groups the agent is in.
			for(ONCFamily f : searchList)
			{
				FamilyHistory fh = ServerFamilyHistoryDB. getCurrentFamilyHistory(year, f.getID());
				ONCMeal meal = mealDB.getFamiliesCurrentMeal(year, f.getID());
				boolean bAlreadyReferred = year == DBManager.getCurrentSeason() ? true : alreadyReferredInCurrentSeason(year, f, currentSeasonList);
				
				if(f.getAgentID() == loggedInUser.getID())
					responseList.add(new ONCWebsiteFamily(f,fh,bAlreadyReferred, dnsCodeDB.getDNSCode(fh.getDNSCode()), meal, ServerNoteDB.lastNoteStatus(year, f.getID())));
				else
					for(ONCGroup agentGroup : agentsGroupList)
						if(agentGroup.groupSharesInfo() && f.getGroupID() == agentGroup.getID())
							responseList.add(new ONCWebsiteFamily(f, fh,bAlreadyReferred, dnsCodeDB.getDNSCode(fh.getDNSCode()), meal,
									ServerNoteDB.lastNoteStatus(year, f.getID())));
			}
		}

//		//sort the list by HoH last name
//		Collections.sort(responseList, new ONCWebsiteFamilyLNComparator());
		
		String response = gson.toJson(responseList, listOfWebsiteFamilies);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	static HtmlResponse getFullFamiliesJSONP(int year, ONCServerUser loggedInUser, String callbackFunction)
	{	
		Gson gson = new Gson();
		Type listOfWebsiteFamilies = new TypeToken<ArrayList<ONCWebsiteFamily>>(){}.getType();
		
		List<ONCFamily> searchList = familyDB.get(DBManager.offset(year)).getList();
		
		ArrayList<ONCWebsiteFamilyFull> responseList = new ArrayList<ONCWebsiteFamilyFull>();
		
		if(loggedInUser.getPermission().compareTo(UserPermission.Agent) > 0)
		{
			//case: admin or higher login			
			for(ONCFamily f : searchList)
			{
				FamilyHistory fh = ServerFamilyHistoryDB. getCurrentFamilyHistory(year, f.getID());
				ONCMeal meal = mealDB.getFamiliesCurrentMeal(year, f.getID());
				responseList.add(new ONCWebsiteFamilyFull(f, fh, dnsCodeDB.getDNSCode(fh.getDNSCode()), meal));
			}
		}
		
		String response = gson.toJson(responseList, listOfWebsiteFamilies);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	static boolean alreadyReferredInCurrentSeason(int year, ONCFamily f, List<ONCFamily> currentSeasonList)
	{	
		//check to see if the parameter family has already been referred in the current season
		int index = 0;
		while(index < currentSeasonList.size() && !currentSeasonList.get(index).getReferenceNum().contentEquals(f.getReferenceNum()))
			index++;
			
		return index <currentSeasonList.size();
	}
	static HtmlResponse getFamilyReferencesJSONP(int year, String callbackFunction)
	{		
		Gson gson = new Gson();
		Type listOfFamilyReferences = new TypeToken<ArrayList<FamilyReference>>(){}.getType();
		
		List<ONCFamily> searchList = familyDB.get(DBManager.offset(year)).getList();
		ArrayList<FamilyReference> responseList = new ArrayList<FamilyReference>();
		
		//sort the search list by ONC Number
		Collections.sort(searchList, new ONCFamilyONCNumComparator());
		
		for(int i=0; i<searchList.size(); i++)
			responseList.add(new FamilyReference(searchList.get(i).getReferenceNum()));
		
		String response = gson.toJson(responseList, listOfFamilyReferences);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	
	static HtmlResponse getAgentsWhoReferredJSONP(int year, ONCServerUser loggedInAgent, String callbackFunction)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCWebAgent>>(){}.getType();
		List<ONCWebAgent> agentReferredInYearList = new LinkedList<ONCWebAgent>();
		
		List<ONCFamily> searchList = familyDB.get(DBManager.offset(year)).getList();
		
		if(loggedInAgent.getPermission().compareTo(UserPermission.Admin) >= 0)
		{
			//Admin or higher user - get all agents who referred in year
			for(ONCFamily f : searchList)
				if(!isInList(f.getAgentID(), agentReferredInYearList))
					agentReferredInYearList.add(new ONCWebAgent(ServerUserDB.getServerUser(f.getAgentID())));
		}
		else if(loggedInAgent.getPermission() == UserPermission.Agent)
		{
			//add the agent
			agentReferredInYearList.add(new ONCWebAgent(loggedInAgent));
			
			//get a list of groups the agent is in
			for(ONCGroup g : ServerGroupDB.getGroupList(loggedInAgent))
			{
				if(g.groupSharesInfo())
				{
					//get all other agents in the group who referred families in the year
					List<ONCServerUser> otherAgentsInGroup = userDB.getOtherUsersInGroup(g.getID(), loggedInAgent);
					for(ONCServerUser otherAgent : otherAgentsInGroup)
					{
						//if agent referred in year, add them to the agent list
						if(didAgentReferInYear(year, otherAgent.getID()) && !isInList(otherAgent.getID(), agentReferredInYearList))
							agentReferredInYearList.add(new ONCWebAgent(otherAgent));
					}
				}
			}	
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
		
    	List<ONCFamily> oncFamAL = familyDB.get(DBManager.offset(year)).getList();
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
	
	static List<String> confirmGiftDelivery(int year, int famID, String dataURI)
	{
		//create the response list of strings
		List<String> responseList = new ArrayList<String>();
		
		//find the family
		FamilyDBYear fDBYear = familyDB.get(DBManager.offset(year));
		List<ONCFamily> fAL = fDBYear.getList();
		
		int index=0;
		while(index<fAL.size() && fAL.get(index).getID() != famID)
			index++;
					
		if(index<fAL.size())
		{	
			ONCFamily updatedFamily = fAL.get(index);
		
			//found the family, extract the image from the dataURI
			byte[] imagedata = Base64.getDecoder().decode(dataURI.substring(dataURI.indexOf(",")+1));
			
			BufferedImage bufferedImage;
			try 
			{
				bufferedImage = ImageIO.read(new ByteArrayInputStream(imagedata));
				ImageIO.write(bufferedImage, "png", new File(String.format("%s/%dDB/Confirmations/confirmed%d.png",
														System.getProperty("user.dir"),year,famID)));
				
				if(!updatedFamily.hasDeliveryImage())
				{
					//if a confirmation signature image doesn't already exist, modify the family record and notify clients
					updatedFamily.setDeliveryConfirmation(true);
					fDBYear.setChanged(true);
					
					//notify the in-year clients
					Gson gson = new Gson();
					String json = "UPDATED_FAMILY" + gson.toJson(updatedFamily, ONCFamily.class);
					clientMgr.notifyAllInYearClients(year, json);
					
				}
				
				//notify the history database of delivery confirmation
				familyHistoryDB.checkFamilyGiftStatusOnDeliveryConfirmation(year, famID);
				
				responseList.add(String.format("ONC #%s, %s %s", updatedFamily.getONCNum(), updatedFamily.getFirstName(), updatedFamily.getLastName()));
				responseList.add("Gift Delivery Confirmed");
			}
			catch (IOException e) 
			{
				responseList.add("Delivery Confirmation Unsucessful");
				responseList.add("Error Code: 1, Please contact ONC director");
			}	
		}
		else
		{
			responseList.add("Delivery Confirmation Unsucessful");
			responseList.add("Error Code: 2, Please contact ONC director");
		}
		
		return responseList;
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
			int dns = 0, unverified = 0, dnswaitlist = 0, servwaitlist = 0, verified = 0, 
					contacted = 0, confirmed = 0;
			
			for(ONCFamily f : familyDB.get(DBManager.offset(year)).getList())
			{
				FamilyHistory lastHistoryObj = familyHistoryDB.getLastFamilyHistory(year, f.getID());
				//unserved families
				if(lastHistoryObj.getDNSCode() > -1 || !isNumeric(f.getONCNum()))
				{
					if(isNumeric(f.getONCNum()) && lastHistoryObj.getDNSCode() == DNS_WAITLIST_CODE)
						dnswaitlist++;
					dns++;
				}
				else
				{
					if(lastHistoryObj.getFamilyStatus() == FamilyStatus.Unverified) {unverified++; }
					else if(lastHistoryObj.getFamilyStatus() == FamilyStatus.Waitlist) { servwaitlist++; }
					else if(lastHistoryObj.getFamilyStatus() == FamilyStatus.Verified) { verified++; }
					else if(lastHistoryObj.getFamilyStatus() == FamilyStatus.Contacted) { contacted++; }
					else if(lastHistoryObj.getFamilyStatus() == FamilyStatus.Confirmed) { confirmed++; }
					served++;
				}
			}
			
			metricList.add(new Metric("DNS", dns));
			metricList.add(new Metric("DNS WL", dnswaitlist));
			metricList.add(new Metric("Served", served));
			metricList.add(new Metric("Unverified", unverified));
			metricList.add(new Metric("Served WL", servwaitlist));
			metricList.add(new Metric("Verfied", verified));
			metricList.add(new Metric("Contacted", contacted));
			metricList.add(new Metric("Confirmed", confirmed));
		}
		else if(maptype.equals("gift"))
		{
			int notreq = 0, req = 0, sel = 0, rec = 0, pck = 0, ref = 0; 
			
			for(ONCFamily f : familyDB.get(DBManager.offset(year)).getList())
			{
				FamilyHistory lastHistoryObj = familyHistoryDB.getLastFamilyHistory(year, f.getID());
				if(lastHistoryObj.getDNSCode() == -1 && isNumeric(f.getONCNum()))
				{
					//served families only
					if(lastHistoryObj.getGiftStatus() == FamilyGiftStatus.NotRequested) { notreq++; }
					else if(lastHistoryObj.getGiftStatus() == FamilyGiftStatus.Requested) { req++; }
					else if(lastHistoryObj.getGiftStatus() == FamilyGiftStatus.Selected) { sel++; }
					else if(lastHistoryObj.getGiftStatus() == FamilyGiftStatus.Received) { rec++; }
					
					//if gift status is PACKAGED or higher, but not REFERRED, count it as PACKAGED
					else if(lastHistoryObj.getGiftStatus().compareTo(FamilyGiftStatus.Packaged) >= 0 &&
							lastHistoryObj.getGiftStatus().compareTo(FamilyGiftStatus.Referred) < 0)
					{
						pck++;
					}
					else if(lastHistoryObj.getGiftStatus() == FamilyGiftStatus.Referred) { ref++; }
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
			
			for(ONCFamily f : familyDB.get(DBManager.offset(year)).getList())
			{
				FamilyHistory lastHistoryObj = familyHistoryDB.getLastFamilyHistory(year, f.getID());
				if(lastHistoryObj.getDNSCode() == -1 && isNumeric(f.getONCNum()))
				{
					//served families only. Get current meal for family
					ONCMeal meal =  mealDB.getFamiliesCurrentMeal(year, f.getID());
					if(meal == null)  { notreq++; }
					else if(meal.getStatus() == MealStatus.None) { notreq++; }
					else if(meal.getStatus() == MealStatus.Requested) { req++; }
					else if(meal.getStatus() == MealStatus.Assigned) { assg++; }
					else if(meal.getStatus() == MealStatus.Referred) {ref++; }
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
			
			for(ONCFamily f : familyDB.get(DBManager.offset(year)).getList())
			{
				FamilyHistory lastHistoryObj = familyHistoryDB.getLastFamilyHistory(year, f.getID());
				if(lastHistoryObj.getDNSCode() == -1 && isNumeric(f.getONCNum()))
				{
					//served families only
					served++;
					if(lastHistoryObj.getGiftStatus() == FamilyGiftStatus.Assigned) { assg++; }
					else if(lastHistoryObj.getGiftStatus() == FamilyGiftStatus.Delivered) { del++; }
					else if(lastHistoryObj.getGiftStatus() == FamilyGiftStatus.Attempted) {att++; }
					else if(lastHistoryObj.getGiftStatus() == FamilyGiftStatus.Returned) { ret++; }
					else if(lastHistoryObj.getGiftStatus() == FamilyGiftStatus.CounselorPickUp) { cpu++; }
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
	
	String update(int year, String familyjson, ONCUser clientUser, boolean bAutoAssign)
	{
		//Create a family object for the updated family
		Gson gson = new Gson();
		ONCFamily updatedFamily = gson.fromJson(familyjson, ONCFamily.class);
		
		//Find the position for the current family being replaced
		FamilyDBYear fDBYear = familyDB.get(DBManager.offset(year));
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
			
			updateRegionAndSchoolCode(updatedFamily);	
			
			//check if the update is requesting automatic assignment of an ONC family number
			//can only auto assign if ONC Number is not number and is not "DEL" and the
			//region is valid
			if(bAutoAssign && !updatedFamily.getONCNum().equals("DEL") && updatedFamily.getRegion() != 0 &&
						  !Character.isDigit(updatedFamily.getONCNum().charAt(0)))
			{
				updatedFamily.setONCNum(generateONCNumber(year, updatedFamily.getSchoolCode()));
			}
			
			//check to see if any of the three phone numbers changed, if so, update the phone code
			int updatedCode = currFam.getPhoneCode();
			if(!updatedFamily.getHomePhone().equals(currFam.getHomePhone()))
			{
				updatedCode = updatedCode & PHONECODE_CLEAR_MASK[0];
				if(updatedFamily.getHomePhone().length() > 9)
				{
					PhoneInfo homePhoneInfo = getPhoneInfo(updatedFamily.getHomePhone());
					updatedCode = updatedCode | (homePhoneInfo.getType().equals("mobile") ? PHONECODES[0] : PHONECODES[1]);
				}	
			}
			if(!updatedFamily.getCellPhone().equals(currFam.getCellPhone()))
			{
				updatedCode = updatedCode & PHONECODE_CLEAR_MASK[1];
				if(updatedFamily.getCellPhone().length() > 9)
				{
					PhoneInfo cellPhoneInfo = getPhoneInfo(updatedFamily.getCellPhone());
					updatedCode =  updatedCode | (cellPhoneInfo.getType().equals("mobile") ? PHONECODES[2] : PHONECODES[3]);
				}	
			}			
			if(!updatedFamily.getAlt2Phone().equals(currFam.getAlt2Phone()))
			{
				updatedCode = updatedCode & PHONECODE_CLEAR_MASK[2];
				if(updatedFamily.getAlt2Phone().length() > 9)
				{
					PhoneInfo alt2PhoneInfo = getPhoneInfo(updatedFamily.getAlt2Phone());
					updatedCode = updatedCode | (alt2PhoneInfo.getType().equals("mobile") ? PHONECODES[4] : PHONECODES[5]);
				}	
			}
			updatedFamily.setPhoneCode(updatedCode);

			updatedFamily.setChangedBy(clientUser.getLNFI());
			updatedFamily.setDateChanged(System.currentTimeMillis());
			
			fAL.set(index, updatedFamily);
			fDBYear.setChanged(true);
			return "UPDATED_FAMILY" + gson.toJson(updatedFamily, ONCFamily.class);
		}
		else
			return "UPDATE_FAILED";
	}
	
	String updateListOfFamilies(int year, String giftListJson, ONCUser client)
	{
		//Create an update family list 
		Gson gson = new Gson();
		Type listOfFamilies = new TypeToken<ArrayList<ONCFamily>>(){}.getType();		
		List<ONCFamily> updatedFamilyList = gson.fromJson(giftListJson, listOfFamilies);
		List<String> responseJsonList = new ArrayList<String>();

		//retrieve the family data base for the year
		FamilyDBYear familyDBYear = familyDB.get(DBManager.offset(year));
		List<ONCFamily> fAL = familyDBYear.getList();
		
		for(ONCFamily updatedFamily : updatedFamilyList)
		{
			//Find the position for the current family being replaced
			int index = 0;
			while(index < fAL.size() && fAL.get(index).getID() != updatedFamily.getID())
				index++;
			
			//replace the current family object with the update. First, check for address change.
			//if address has changed, update the region. 
			if(index < fAL.size())
			{
				//update time stamp and changed by info
				updatedFamily.setDateChanged(System.currentTimeMillis());
				updatedFamily.setChangedBy(client.getLNFI());
				
//				//check to see if either status or DNS code is changing, if so, add a history item
//				ONCFamily currFam = fAL.get(index);
//				if(currFam != null && 
//					(currFam.getFamilyStatus() != updatedFamily.getFamilyStatus() || 
//					  currFam.getGiftStatus() != updatedFamily.getGiftStatus() ||
//					   currFam.getDNSCode() != updatedFamily.getDNSCode()))
//				{
//					addHistoryItem(year, updatedFamily.getID(), updatedFamily.getFamilyStatus(), 
//																updatedFamily.getGiftStatus(), null, updatedFamily.getDNSCode(),
//																"Status Changed", updatedFamily.getChangedBy(), true);
//						
//					updatedFamily.setDeliveryID(histItem.getID());
//				}
					
				fAL.set(index, updatedFamily);
				familyDBYear.setChanged(true);

				responseJsonList.add("UPDATED_FAMILY" + gson.toJson(updatedFamily, ONCFamily.class));
			}
		}
		
		Type responseListType = new TypeToken<ArrayList<String>>(){}.getType();
		return "UPDATED_LIST_FAMILIES" + gson.toJson(responseJsonList, responseListType);
	}
	
	ONCFamily update(int year, ONCFamily updatedFamily, WebClient wc, boolean bAutoAssign, String updateNote)
	{
		//Find the position for the current family being replaced
		FamilyDBYear fDBYear = familyDB.get(DBManager.offset(year));
		List<ONCFamily> fAL = fDBYear.getList();
		int index = 0;
		while(index < fAL.size() && fAL.get(index).getID() != updatedFamily.getID())
			index++;
		
		//replace the current family object with the update. First, check for address change.
		//if address has changed, update the region. 
		if(index < fAL.size())
		{
			updateRegionAndSchoolCode(updatedFamily);	
			
			//check if the update is requesting automatic assignment of an ONC family number
			//can only auto assign if ONC Number is not number and is not "DEL" and the
			//region is valid
			if(bAutoAssign && !updatedFamily.getONCNum().equals("DEL") && updatedFamily.getRegion() != 0 &&
						  !Character.isDigit(updatedFamily.getONCNum().charAt(0)))
			{
				updatedFamily.setONCNum(generateONCNumber(year, updatedFamily.getSchoolCode()));
			}
			
//			//add a history item so change can be tracked. Changed by is the web user who made the change
//			DNSCode famDNSCode = dnsCodeDB.getDNSCode(updatedFamily.getDNSCode());
//			addHistoryItem(year, updatedFamily.getID(), updatedFamily.getFamilyStatus(), 
//								updatedFamily.getGiftStatus(), null, famDNSCode.getID(), updateNote, wc.getWebUser().getLNFI(), true);
//			updatedFamily.setDeliveryID(histItem.getID());
			
			fAL.set(index, updatedFamily);
			fDBYear.setChanged(true);
			return updatedFamily;
		}
		else
			return null;
	}
	
	RegionAndSchoolCode updateRegionAndSchoolCode(ONCFamily updatedFamily)
	{
		//if family has a delivery address, use it, else use home address.
		Address address;
		if(updatedFamily.getSubstituteDeliveryAddress().isEmpty())
			address = new Address(updatedFamily.getHouseNum(),
					updatedFamily.getStreet(), updatedFamily.getUnit(),
					  updatedFamily.getCity(), updatedFamily.getZipCode());
		else
			address = new Address(updatedFamily.getSubstituteDeliveryAddress());
		
		//address is new or has changed, set or update the region and school code
		RegionAndSchoolCode rSC = ServerRegionDB.searchForRegionMatch(address);

		updatedFamily.setRegion(rSC.getRegion());
		updatedFamily.setSchoolCode(rSC.getSchoolCode());
			
		return rSC;
	}		
	
	@Override
	String add(int year, String json, ONCUser client)
	{
		//Create a family object for the add family request
		Gson gson = new Gson();
		ONCFamily addedFam = gson.fromJson(json, ONCFamily.class);
		
		if(addedFam != null)
		{
			//get the family data base for the correct year
			FamilyDBYear fDBYear = familyDB.get(DBManager.offset(year));
			
			//check to see if the reference number is provided, if not, generate one
			if(addedFam.getReferenceNum().equals("NNA"))
				addedFam.setReferenceNum(generateReferenceNumber());
			
			//set region and school code for family
			updateRegionAndSchoolCode(addedFam);
			
			//get each phones info and add it to a Family Phone Info Object
			int phoneCode = 0;
			PhoneInfo[] famPhoneInfo = new PhoneInfo[3];
			
			//check home phone
			if(!addedFam.getHomePhone().isEmpty())
			{
				famPhoneInfo[0] = getPhoneInfo(addedFam.getHomePhone());
				if(famPhoneInfo[0].isPhoneValid())
					phoneCode = phoneCode | (famPhoneInfo[0].getType().equals("mobile") ? PHONECODES[0] : PHONECODES[1]);
			}
			else
				famPhoneInfo[0] = new PhoneInfo();
			
			//check cell phone
			if(!addedFam.getCellPhone().isEmpty())
			{
				famPhoneInfo[1] = getPhoneInfo(addedFam.getCellPhone());
				if(famPhoneInfo[1].isPhoneValid())
					phoneCode = phoneCode | (famPhoneInfo[1].getType().equals("mobile") ? PHONECODES[2] : PHONECODES[3]);
			}
			else
				famPhoneInfo[1] = new PhoneInfo();
			
			//check alt2 phone
			if(!addedFam.getAlt2Phone().isEmpty())
			{
				famPhoneInfo[2] = getPhoneInfo(addedFam.getAlt2Phone());
				if(famPhoneInfo[2].isPhoneValid())
					phoneCode = phoneCode | (famPhoneInfo[2].getType().equals("mobile") ? PHONECODES[4] : PHONECODES[5]);
			}
			else
				famPhoneInfo[2] = new PhoneInfo();
			
			
			addedFam.setPhoneCode(phoneCode);
		
			//create the ONC number
			addedFam.setONCNum(generateONCNumber(year, addedFam.getSchoolCode()));
			
			//set the new ID for the added family
			addedFam.setID(fDBYear.getNextID());
			addedFam.setDateChanged(System.currentTimeMillis());
			addedFam.setChangedBy(client.getLNFI());
			addedFam.setStoplightChangedBy(client.getLNFI());
			
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
//				FamilyHistory famHist = familyHistoryDB.getLastFamilyHistory(year, addedFam.getID());
				FamilyHistory famHistory = new FamilyHistory(-1, addedFam.getID(), 
																FamilyStatus.Unverified,
																FamilyGiftStatus.Requested,
																"", "Family Referred thru Britepaths", 
																addedFam.getChangedBy(), 
																System.currentTimeMillis(),
																-1);
			
				FamilyHistory addedFamHistory = familyHistoryDB.addFamilyHistoryObject(year, famHistory, currClient.getClientUser().getLNFI(), false);
//				if(addedFamHistory != null)
//					addedFam.setDeliveryID(addedFamHistory.getID());
				
				//if the family was added successfully, add the history item, adults and children
				jsonResponseList.add("ADDED_FAMILY" + gson.toJson(addedFam, ONCFamily.class));
				
				if(addedFamHistory != null)
					jsonResponseList.add("ADDED_DELIVERY" + gson.toJson(addedFamHistory, FamilyHistory.class));
		
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
			FamilyDBYear fDBYear = familyDB.get(DBManager.offset(year));
			
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
	
/*	
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
				famToCheck.setDNSCode(dnsCodeDB.getDNSCode("DUP").getID());
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
*/	
	String getFamily(int year, String zFamID)
	{
		int oncID = Integer.parseInt(zFamID);
		List<ONCFamily> fAL = familyDB.get(DBManager.offset(year)).getList();
		
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
	
	String getConfirmationPNG(int year, String zFamID)
	{
		try
		{
			int oncID = Integer.parseInt(zFamID);
			List<ONCFamily> fAL = familyDB.get(DBManager.offset(year)).getList();
			
			int index = 0;	
			while(index < fAL.size() && fAL.get(index).getID() != oncID)
				index++;
			
			if(index < fAL.size())
			{
				//family found, is there a confirmation?
				if(fAL.get(index).hasDeliveryImage())
				{
					String path = String.format("%s/%dDB/Confirmations/confirmed%d.png", System.getProperty("user.dir"),year,oncID);
					byte[] fileContent;
					try 
					{
						fileContent = FileUtils.readFileToByteArray(new File(path));
						String encodedString = Base64.getEncoder().encodeToString(fileContent);
						return encodedString;
					} 
					catch (IOException e) 
					{
						return "ERROR: INVALID_IMAGE_FORMAT";
					}
				}
				else
					return "ERROR: CONFIRMATION_NOT_FOUND";
			}
			else
				return "ERROR: FAMILY_NOT_FOUND";
		}
		catch (NumberFormatException nfe)
		{
			return "ERROR: INVALID_FAMILY_IDENTIFER";
		}
	}
	
	static HtmlResponse getFamilyJSONP(int year, boolean bByReference, String targetID, boolean bIncludeSchools, String callbackFunction)
	{		
		Gson gson = new Gson();
		String response;
	
		List<ONCFamily> fAL = familyDB.get(DBManager.offset(year)).getList();
		
		int index=0;
		
		if(bByReference)
		{	
			//search by reference number
			while(index<fAL.size() && !fAL.get(index).getReferenceNum().equals(targetID))
				index++;
		}
		else
		{
			//search by season family id number
			int oncID = isNumeric(targetID) ? Integer.parseInt(targetID) : -1;
			while(index<fAL.size() && fAL.get(index).getID() != oncID)
				index++;
		}
					
		if(index<fAL.size())
		{
			ONCFamily fam = fAL.get(index);
			
			//get a list of the children, list of adults, agent and meal, if there is a meal
			List<ONCWebChild> childList = childDB.getWebChildList(year, fam.getID(), bIncludeSchools);
			List<ONCAdult> adultList = adultDB.getAdultsInFamily(year, fam.getID());
			List<ONCWebChild> adjustedAgeChildList = new ArrayList<ONCWebChild>();
			
			//Check dob year for each child in the family. If the year of birth is more than 20 years from the 
			//year of the current season, move the child into the adult list
			for(ONCWebChild wc : childList)
			{
				String[] dobParts = wc.getDOB().split("/");	//string dob in form mm/dd/yyyy
				if(dobParts.length == 3 && dobParts[2].length() == 4)
				{
					try
					{
						int dobyear = Integer.parseInt(dobParts[2]);
						if(DBManager.getCurrentSeason() - dobyear > 20)
							adultList.add(new ONCAdult(wc));	//child is over age, add to adult list
						else
							adjustedAgeChildList.add(wc);	//child is still age eligible, add to checked child list
					}
					catch (NumberFormatException nfe)
					{
						adjustedAgeChildList.add(wc);	//something is wrong with dob format, add child anyway
					}
				}
			}	
			
			FamilyHistory fh = ServerFamilyHistoryDB.getCurrentFamilyHistory(year, fam.getID());
			ONCWebAgent famAgent = userDB.getWebAgent(fam.getAgentID());
			ONCMeal famMeal =  mealDB.getFamiliesCurrentMeal(year, fam.getID());
			DNSCode famDNSCode = dnsCodeDB.getDNSCode(fh.getDNSCode());
			
			ONCWebsiteFamilyExtended webFam = new ONCWebsiteFamilyExtended(fam, fh,
													ServerRegionDB.getRegion(fam.getRegion()),
													ServerGroupDB.getGroup(fam.getGroupID()).getName(),
													adjustedAgeChildList, adultList, famAgent, famMeal, famDNSCode);
			
			response = gson.toJson(webFam, ONCWebsiteFamilyExtended.class);
		}
		else
			response = "";
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	
	ONCFamily getFamily(int year, int id)	//id number set each year
	{
		List<ONCFamily> fAL = familyDB.get(DBManager.offset(year)).getList();
		int index = 0;	
		while(index < fAL.size() && fAL.get(index).getID() != id)
			index++;
		
		if(index < fAL.size())
			return fAL.get(index);
		else
			return null;
	}
	
	static ONCFamily getFamilyByReference(int year, int id, String referenceNumber)
	{
		List<ONCFamily> fAL = familyDB.get(DBManager.offset(year)).getList();
		
		ONCFamily family = null;
		for(int i=0; i<fAL.size(); i++)
		{
			if(fAL.get(i).getID() == id && fAL.get(i).getReferenceNum().contentEquals(referenceNumber))
				family = fAL.get(i);	
		}
		
		return family;
	}
	ONCFamily getFamilyBySMS(int year, TwilioSMSReceive receivedSMS)
	{
		String formatedPhoneNum = removeNonDigitsFromPhoneNumber(receivedSMS.getFrom().substring(2));
		
		List<ONCFamily> fAL = familyDB.get(DBManager.offset(year)).getList();
		int i;
		for(i=0; i<fAL.size(); i++)
			if(removeNonDigitsFromPhoneNumber(fAL.get(i).getHomePhone()).equals(formatedPhoneNum) ||
				removeNonDigitsFromPhoneNumber(fAL.get(i).getCellPhone()).equals(formatedPhoneNum))
				break;
		
		return i < fAL.size() ? fAL.get(i) : null;	
	}
	
	static String getFamilyRefNum(int year, int id)	//id number set each year
	{
		List<ONCFamily> fAL = familyDB.get(DBManager.offset(year)).getList();
		int index = 0;	
		while(index < fAL.size() && fAL.get(index).getID() != id)
			index++;
		
		if(index < fAL.size())
			return fAL.get(index).getReferenceNum();
		else
			return null;
	}
	
//	ONCFamily getFamilyByMealID(int year, int mealID)
//	{
//		List<ONCFamily> fAL = familyDB.get(DBManager.offset(year)).getList();
//		int index = 0;	
//		while(index < fAL.size() && fAL.get(index).getMealID() != mealID)
//			index++;
//		
//		if(index < fAL.size())
//			return fAL.get(index);
//		else
//			return null;
//	}
/*	
	void checkFamilyStatusOnSmsReceived(int year, ONCFamily fam, boolean bDeliveryTimeframe, boolean bConfirmingBody,
										boolean bDecliningBody)
	{
		if(fam != null);
		{
			FamilyHistory lastHistoryObj = familyHistoryDB.getLastFamilyHistory(year, fam.getID());
			if(!(bDeliveryTimeframe && lastHistoryObj.getFamilyStatus() == FamilyStatus.Confirmed))
			{
				//it's not around delivery day or the family wasn't already confirmed, 
				//so we potentially want to change family status.
				if(bConfirmingBody && (lastHistoryObj.getFamilyStatus() == FamilyStatus.Contacted ||
										lastHistoryObj.getFamilyStatus() == FamilyStatus.Verified ||
										 lastHistoryObj.getFamilyStatus() == FamilyStatus.Waitlist))
				{
					fam.setFamilyStatus(FamilyStatus.Confirmed);
					familyDB.get(DBManager.offset(year)).setChanged(true);
				
					//notify all in-year clients of the status change
					Gson gson = new Gson();
	    				String change = "UPDATED_FAMILY" + gson.toJson(fam, ONCFamily.class);
	    				clientMgr.notifyAllInYearClients(year, change);	//null to notify all clients
				}
				else if(bDecliningBody && (fam.getFamilyStatus() == FamilyStatus.Confirmed ||
											fam.getFamilyStatus() == FamilyStatus.Verified ||
											 fam.getFamilyStatus() == FamilyStatus.Waitlist))
				{
					fam.setFamilyStatus(FamilyStatus.Contacted);
					familyDB.get(DBManager.offset(year)).setChanged(true);
				
					//notify all in-year clients of the status change
					Gson gson = new Gson();
	    				String change = "UPDATED_FAMILY" + gson.toJson(fam, ONCFamily.class);
	    				clientMgr.notifyAllInYearClients(year, change);	//null to notify all clients
				}
			}
		}
	}

*/	
	ONCFamily getFamilyByTargetID(int year, String targetID)	//Persistent onc id number string
	{
		//Verify targetID is numeric and convert to string
		try
		{
			int famID = Integer.parseInt(targetID);
			
			List<ONCFamily> fAL = familyDB.get(DBManager.offset(year)).getList();
			int index = 0;	
			while(index < fAL.size() && fAL.get(index).getID()!= famID)
				index++;
			
			if(index < fAL.size())
				return fAL.get(index);
			else
				return null;
		}
		catch (NumberFormatException nfe)
		{
			ServerUI.addLogMessage(String.format("WebServer Family Update: Unable to parse target id parameter: %s", targetID));
			return null;
		}
	}
	
	ONCFamily getFamilyByONCNum(int year, String oncNum)	//Persistent odb, wfcm or onc id number string
	{
		List<ONCFamily> fAL = familyDB.get(DBManager.offset(year)).getList();
		int index = 0;	
		while(index < fAL.size() && !fAL.get(index).getONCNum().equals(oncNum))
			index++;
		
		if(index < fAL.size())
			return fAL.get(index);
		else
			return null;
	}
/*	
	void checkFamilyStatusOnSMSStatusCallback(int year, ONCSMS receivedSMS)
	{
		ONCFamily fam = getFamily(year, receivedSMS.getEntityID());
		
		//we only consider changing family status to Contacted based on outgoing message status if the family 
		//has not yet confirmed delivery
		if(fam != null && fam.getFamilyStatus() != FamilyStatus.Confirmed)
		{	
			if((receivedSMS.getStatus() == SMSStatus.SENT ||  receivedSMS.getStatus() == SMSStatus.DELIVERED) && 
				(fam.getFamilyStatus() == FamilyStatus.Waitlist || fam.getFamilyStatus() == FamilyStatus.Verified))
			{
				//sometimes we don't get a DELIVERD status from the super carrier, so we'll accept a SENT status.
				fam.setFamilyStatus(FamilyStatus.Contacted);
				familyDB.get(DBManager.offset(year)).setChanged(true);
			
				//notify all in-year clients of the status change
				Gson gson = new Gson();
    				String change = "UPDATED_FAMILY" + gson.toJson(fam, ONCFamily.class);
    				clientMgr.notifyAllInYearClients(year, change);	//null to notify all clients
			}
			else if((receivedSMS.getStatus() == SMSStatus.UNDELIVERED || receivedSMS.getStatus() == SMSStatus.FAILED) && 
					fam.getFamilyStatus() == FamilyStatus.Contacted )
			{
				//message status updated to UNDELIVERED. If we already changed the family status to Contacted, change
				//it back to either Waitlist or Verified.
				FamilyStatus newStatus = fam.getDNSCode() == DNSCode.WAITLIST ? FamilyStatus.Waitlist : FamilyStatus.Verified;
				fam.setFamilyStatus(newStatus);
				familyDB.get(DBManager.offset(year)).setChanged(true);
			
				//notify all in-year clients of the status change
				Gson gson = new Gson();
    				String change = "UPDATED_FAMILY" + gson.toJson(fam, ONCFamily.class);
    				clientMgr.notifyAllInYearClients(year, change);	//null to notify all clients
			}
		}
	}
*/
	/****
	 * Using the SMS service provider, looks up a phone number to determine validity and type of phone
	 * and returns a single six bit integer phone code that indicates whether the primary, alternate and
	 * 2nd alternate phone numbers for each family are valid. If they are valid, the code 
	 * indicates if the number is a mobile number or a land line or other (VOIP) number.
	 * Bits 0-1 are used for the primary number, 2-3 for the alternate and 4-5 for the 2nd alternate.
	 * Individual codes are 0 - Invalid or Empty, 1- Valid mobile, 2 - UNUSED, 3 - Valid land line or other
	 * @param phonenumber - phone number to check
	 * @param currCode - current six bit phone code for the family
	 * @param phoneid - 0-primary phone number, 1-alternate phone number, 2-2nd alternate phone number
	 * @return six bit phone code for the family
	 */
	PhoneInfo getPhoneInfo(String phonenumber)
	{
		//lookup the phone number from the SMS service provider
		Map<String,Object> resultMap = ServerSMSDB.lookupPhoneNumber(phonenumber);
		
		//if the number is a valid phone number, determine the type. If mobile, set the code
		//corresponding to the phone id to 1. Else, set it to 3. If it's not a valid number, 
		//clear the code for the associated id (set it to 0).
		if((Integer) resultMap.get("returncode") == 0)
		{	
			String type = (String) resultMap.get("type");
			String name = (String) resultMap.get("name");
			int code = type.equals("mobile") ? 1 : 3;
			
			return new PhoneInfo(phonenumber, code, type, name);	
		}
		else
			return new PhoneInfo(phonenumber, 0, "", "");	
	}
	
	String checkFamilyPhoneNumbers(int year, String zFamID)
	{
		try
		{
			int oncID = Integer.parseInt(zFamID);
			FamilyDBYear fDBYear = familyDB.get(DBManager.offset(year));
			List<ONCFamily> fAL = fDBYear.getList();
			
			int index = 0;	
			while(index < fAL.size() && fAL.get(index).getID() != oncID)
				index++;
			
			if(index < fAL.size())
			{
				ONCFamily family = fAL.get(index);
				
				Gson gson = new Gson();
				FamilyPhoneInfo fpi = checkFamilyPhoneNumbers(year, family);
				
				//check to see if the phone code has changed
				if(family.getPhoneCode() != fpi.getFamilyPhoneCode())
				{
					//family code has changed, update the object, mark the db for saving and notify all
					//in year clients
					family.setPhoneCode(fpi.getFamilyPhoneCode());
					fDBYear.setChanged(true);
					
					String message = "UPDATED_FAMILY" + gson.toJson(family, ONCFamily.class);
					clientMgr.notifyAllInYearClients(year, message);	
				}
				return "FAMILY_PHONE_INFO" + gson.toJson(fpi, FamilyPhoneInfo.class);
			}
			else
				return "FAMILY_PHONE_REQUEST_FAILED: Famiy not found";
		}
		catch(NumberFormatException nfe)
		{
			return "FAMILY_PHONE_REQUEST_FAILED: Family ID Number Format Exception";
		}
	}
	
	FamilyPhoneInfo checkFamilyPhoneNumbers(int year, ONCFamily fam)
	{
		FamilyPhoneInfo fpi = new FamilyPhoneInfo();
		
		//check to see if any of the three phone numbers changed, if so, update the phone code
		int updatedCode = 0;
		if(fam.getHomePhone().length() > 9)
		{
			PhoneInfo homePhoneInfo = getPhoneInfo(fam.getHomePhone());
			updatedCode = homePhoneInfo.getType().equals("mobile") ? PHONECODES[0] : PHONECODES[1] ;
			fpi.setPhoneInfo(0,  homePhoneInfo);
		}	
		if(fam.getCellPhone().length() > 9)
		{
			PhoneInfo cellPhoneInfo = getPhoneInfo(fam.getCellPhone());
			updatedCode = cellPhoneInfo.getType().equals("mobile") ? PHONECODES[2] : PHONECODES[3] ;
			fpi.setPhoneInfo(1,  cellPhoneInfo);
		}	
		if(fam.getAlt2Phone().length() > 9)
		{
			PhoneInfo alt2PhoneInfo = getPhoneInfo(fam.getAlt2Phone());
			updatedCode = alt2PhoneInfo.getType().equals("mobile") ? PHONECODES[4] : PHONECODES[5] ;
			fpi.setPhoneInfo(2,  alt2PhoneInfo);
		}
		
		//now that we have looked up info for all three phone numbers, set the family phone code
		//and return the data
		fpi.setFamilyPhoneCode(updatedCode);
		return fpi;
	}
	
	void checkFamilyGiftCardOnlyOnGiftAdded(int year, ONCChildGift priorGift, ONCChildGift addedGift)
	{
		int famID = childDB.getChildsFamilyID(year, addedGift.getChildID());
		
		ONCFamily fam = getFamily(year, famID);

	    //determine if the families gift card only status could change after adding the wish.
	    int giftCardGiftID = globalDB.getGiftCardID(year);
	    boolean bGCStatusCouldChange = giftCardGiftID > -1 && 
	    									((priorGift != null && priorGift.getCatalogGiftID() == giftCardGiftID) || 
	    									(addedGift.getCatalogGiftID() == giftCardGiftID));
	    
    	if(bGCStatusCouldChange)
    	{
    		boolean bGiftCardOnlyFamilyNow = isGiftCardOnlyFamily(year, famID);
    		if(fam.isGiftCardOnly() != bGiftCardOnlyFamilyNow)
    		{
    			fam.setGiftCardOnly(bGiftCardOnlyFamilyNow);
    			familyDB.get(DBManager.offset(year)).setChanged(true);
    			Gson gson = new Gson();
        		String change = "UPDATED_FAMILY" + gson.toJson(fam, ONCFamily.class);
        		clientMgr.notifyAllInYearClients(year, change);	//null to notify all clients	
    		}
	    }
	}
/*	
	FamilyHistory addHistoryItem(int year, int famID, FamilyStatus fs, FamilyGiftStatus fgs, String driverID,
						int dnsCode, String reason, String changedBy, boolean bNotify)
	{
		FamilyHistory reqFamHistObj = new FamilyHistory(-1, famID, fs, fgs, driverID, reason,
				 changedBy, System.currentTimeMillis(), dnsCode);
		
		FamilyHistory addedFamHistory = familyHistoryDB.addFamilyHistoryObject(year, reqFamHistObj, bNotify);
		
		return addedFamHistory;
	}
*/	
	/********
	 * Whenever meal is added, the meal database notifies the family database to update
	 * the id for the families meal and the meal status field.
	 * The meal status field is redundant in the ONC Family object for performance considerations. 
	 * @param year
	 * @param addedMeal
	 */
//	void familyMealAdded(int year, ONCMeal addedMeal)
//	{
//		ONCFamily fam = getFamily(year, addedMeal.getFamilyID());
//		if(fam != null)
//		{
//			fam.setMealID(addedMeal.getID());
//			fam.setMealStatus(addedMeal.getStatus());
//			familyDB.get(DBManager.offset(year)).setChanged(true);
//			
//			Gson gson = new Gson();
//	    		String changeJson = "UPDATED_FAMILY" + gson.toJson(fam, ONCFamily.class);
//	    		clientMgr.notifyAllInYearClients(year, changeJson);	//null to notify all clients
//		}
//	}
	
	boolean isGiftCardOnlyFamily(int year, int famid)
	{
		ServerChildGiftDB childGiftDB = null;
		try 
		{
			childGiftDB = ServerChildGiftDB.getInstance();
	
			//set a return variable to true. If we find one instance, we'll set it to false
			boolean bGiftCardOnlyFamily = true;
			
			//first, determine if gift cards are even in the catalog. If they aren't, return false as
			//it can't be a gift card only family
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
	
					//if all are assigned, examine each gift to see if it's a gift card
					int numGiftsPerChild = globalDB.getServerGlobalVariables(year).getNumberOfGiftsPerChild();
					int giftindex = 0;
//					while(giftindex < NUMBER_OF_WISHES_PER_CHILD && bGiftCardOnlyFamily)
					while(giftindex < numGiftsPerChild && bGiftCardOnlyFamily)
					{
						ONCChildGift cg = childGiftDB.getCurrentChildGift(year, c.getID(), giftindex++);
						if(cg == null || cg != null && cg.getCatalogGiftID() != giftCardID)	//gift card?
							bGiftCardOnlyFamily = false;
					}	
				}
			}
		
			return bGiftCardOnlyFamily;
		} 
		catch (FileNotFoundException e) 
		{
			return false;
		} 
		catch (IOException e)
		{
			return false;
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
/*	
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
		
		ServerChildGiftDB childGiftDB = null;
		try 
		{
			childGiftDB = ServerChildGiftDB.getInstance();
			int numGiftsPerChild = globalDB.getServerGlobalVariables(year).getNumberOfGiftsPerChild();
			
			for(ONCChild c : childDB.getChildList(year, famid))
			{
//				for(int wn=0; wn< NUMBER_OF_WISHES_PER_CHILD; wn++)
				for(int wn=0; wn < numGiftsPerChild; wn++)	
				{
					ONCChildGift cw = childGiftDB.getCurrentChildGift(year, c.getID(), wn);
					
					//if cw is null, it means that the wish doesn't exist yet. If that's the case, 
					//set the status to the lowest status possible as if the wish existed
					GiftStatus childwishstatus = GiftStatus.Not_Selected;	//Lowest possible child wish status
					if(cw != null)
						childwishstatus = cw.getGiftStatus();
						
					if(wishstatusmatrix[childwishstatus.statusIndex()].compareTo(lowestfamstatus) < 0)
						lowestfamstatus = wishstatusmatrix[childwishstatus.statusIndex()];
				}
			}
				
			return lowestfamstatus;
		} 
		catch (FileNotFoundException e) 
		{
			return FamilyGiftStatus.Requested;
		} 
		catch (IOException e) 
		{
			return FamilyGiftStatus.Requested;
		}
	}
*/	
/*	
	void updateFamilyHistory(int year, FamilyHistory addedHistObj)
	{
		//find the family
		FamilyDBYear famDBYear = familyDB.get(DBManager.offset(year));
		ONCFamily fam = getFamily(year, addedHistObj.getFamID());
		
		//update the delivery ID and delivery status
//		fam.setDeliveryID(addedHistObj.getID());
		fam.setFamilyGiftStatus(addedHistObj.getGiftStatus());
		famDBYear.setChanged(true);
		
		//notify in year clients of change
		Gson gson = new Gson();
    	String change = "UPDATED_FAMILY" + gson.toJson(fam, ONCFamily.class);
    	clientMgr.notifyAllInYearClients(year, change);	//null to notify all clients	
	}
	
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
		FamilyDBYear famDBYear = familyDB.get(DBManager.offset(year));

//		Calendar date_changed = Calendar.getInstance();	//No date_changed in ONCFamily yet
//		if(!nextLine[6].isEmpty())
//			date_changed.setTimeInMillis(Long.parseLong(nextLine[6]));
			
		famDBYear.add(new ONCFamily(nextLine));
	}
	
	void save(int year)
	{
		String[] header = {"ONC ID", "ONCNum", "Region", "School Code", "ODB Family #", "Batch #", 
//				"DNS Code", "Family Status", "Delivery Status",
				"Speak English?","Language if No", "Caller", "Notes", "Delivery Instructions",
				"Client Family", "First Name", "Last Name", "House #", "Street", "Unit #", "City", "Zip Code",
				"Substitute Delivery Address", "All Phone #'s", "Home Phone", "Other Phone", "Family Email", 
				"ODB Details", "Children Names", "Schools", "ODB WishList", "Adopted For",
				"Agent ID", "GroupID",
//				"Delivery ID",
				"Phone Code", "# of Bags", "# of Large Items", 
				"Stoplight Pos", "Stoplight Mssg", "Stoplight C/B", "Transportation", "Gift Card Only",
				"Gift Distribution","Del Confirmation"};
		
		FamilyDBYear fDBYear = familyDB.get(DBManager.offset(year));
		if(fDBYear.isUnsaved())	
		{
			String path = String.format("%s/%dDB/NewFamilyDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(fDBYear.getList(), header, path);
			fDBYear.setChanged(false);
		}
	}
	
	static boolean didAgentReferInYear(int year, int agentID)
	{
		List<ONCFamily> famList = familyDB.get(DBManager.offset(year)).getList();
		
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
		if(priorYear > DBManager.getBaseSeason())
		{
			List<ONCFamily> famList = familyDB.get(DBManager.offset(priorYear)).getList();
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
		List<ONCFamily> famList = familyDB.get(DBManager.offset(year)).getList();
		
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
		return familyDB.get(DBManager.offset(year)).getList();
	}
	
	String getTwilioFormattedPhoneNumber(int year, int famID, int phoneChoice)
	{
		List<ONCFamily> searchList = familyDB.get(DBManager.offset(year)).getList();
		
		String twilioFormattedPhoneNum = null;	//initialize returned number
		
		if(phoneChoice >= 0 && phoneChoice <= 1)	//validate the phone choice range
		{	
			int index = 0;
			while(index < searchList.size() && searchList.get(index).getID() != famID)
				index++;
		
			if(index < searchList.size())
			{
				ONCFamily f = searchList.get(index);
				//we've found the family, now check to see if we have a phone number to use
				//if the request is to use the primary phone, use it if it's valid
				//if not add the alternate phone.
			
				if(phoneChoice == 0)	//home phone
				{
					if(!f.getHomePhone().isEmpty() && f.getHomePhone().trim().length() == 12)
						twilioFormattedPhoneNum = String.format("+1%s", removeNonDigitsFromPhoneNumber(f.getHomePhone()));
					else if(!f.getCellPhone().isEmpty() && f.getCellPhone().trim().length() == 12)
						twilioFormattedPhoneNum = String.format("+1%s", removeNonDigitsFromPhoneNumber(f.getCellPhone()));
				}
			
				//we've found the family, now check to see if we have a phone number to use
				//if the request is to use the alternate phone, use it if it's valid
				//if not add the primary phone.
				if(phoneChoice == 1)	//cell phone
				{
					if(!f.getCellPhone().isEmpty() && f.getCellPhone().trim().length() == 12)
						twilioFormattedPhoneNum = String.format("+1%s", removeNonDigitsFromPhoneNumber(f.getCellPhone()));
					else if(!f.getHomePhone().isEmpty() && f.getHomePhone().trim().length() == 12)
						twilioFormattedPhoneNum = String.format("+1%s", removeNonDigitsFromPhoneNumber(f.getHomePhone()));
				}
			}
		}
		
		return twilioFormattedPhoneNum;
	}
	
	@Override
	void createNewSeason(int newYear)
	{
		//test to see if prior year existed. If it did, need to add to the ApartmentDB the families who's
		//addresses had units.
		if(familyDB.size() > 1)
			ApartmentDB.addPriorYearApartments(newYear-1);
		
		//create a new family data base year for the year provided in the newYear parameter
		//The family db year is initially empty prior to the import of families, so all we
		//do here is create a new FamilyDBYear for the newYear and save it.
		FamilyDBYear famDBYear = new FamilyDBYear(newYear);
		familyDB.add(famDBYear);
		famDBYear.setChanged(true);	//mark this db for persistent saving on the next save event
		
		//add a directory named Confirmations that will hold images of delivery receipt signatures
		File confirmationDir = new File(String.format("%s/%dDB/Confirmations", System.getProperty("user.dir"), newYear));
		if (!confirmationDir.exists())
		    confirmationDir.mkdirs();
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
    		List<ONCFamily> famList = familyDB.get(DBManager.offset(year)).getList();
    	
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
		while(yearIndex >= DBManager.getBaseSeason() && !bFamilyIsInPriorYear)
		{
			List<ONCFamily> pyFamilyList = familyDB.get(DBManager.offset(yearIndex)).getList();
		
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
    		List<ONCFamily> oncFamAL = familyDB.get(DBManager.offset(year)).getList();
    	
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
    		int delCount = 0;
    		for(ONCFamily f : familyDB.get(DBManager.offset(year)).getList())
    		{
    			FamilyHistory lastFamHistObj = familyHistoryDB.getLastFamilyHistory(year, f.getID());
    			if(lastFamHistObj != null && lastFamHistObj.getGiftStatus().compareTo(FamilyGiftStatus.Assigned) >= 0 &&
    					lastFamHistObj.getdDelBy().equals(drvNum))
    			{
    					delCount++;
    			}
    		}
    
    		return delCount;
    }
    
    //calculate the number of families referred by the agent from a specific group
    //for a particular year
    static int getNumReferralsByUserAndGroup(int year, ONCUser u, ONCGroup g)
    {
		if(DBManager.isYearAvailable(year))
		{
			int count = 0;
			for(ONCFamily f : familyDB.get(DBManager.offset(year)).getList())
				if(f.getAgentID() == u.getID() && f.getGroupID() == g.getID())
					count++;
			
			return count;
		}
		else
			return 0;
    }
    
    int getMinONCNum() { return oncnumRangeMap.get("A") != null ? oncnumRangeMap.get("A").getStart() : 10000; }
    int getMaxONCNum() { return oncnumRangeMap.get("Z") != null ? oncnumRangeMap.get("Z").getEnd() : -1; }
    
    //create a delivery card file
    HtmlResponse createDelCardFileJSONP(Map<String, Object> params)
    {
    	String message;
    	boolean bResult = false;
    	
    	//process the request
		int year = Integer.parseInt((String) params.get("year"));
		
		//get the list of family id's the web client requested to update
		List<Integer> famIDList = new ArrayList<Integer>();
		int index = 0;
		while(params.containsKey("famid" + Integer.toString(index)))
		{
			String zID = (String) params.get("famid" + Integer.toString(index++));
			famIDList.add(Integer.parseInt(zID));
		}
    	
    	//convert family id list to family list
    	List<ONCFamily> familyList = new ArrayList<ONCFamily>();
    	for(Integer famid : famIDList)
    	{
    		ONCFamily f = getFamily(year, famid);
    		if(f != null)
    			familyList.add(f);
    		
    	}
    	
    	String dest = String.format("%s/DeliveryCard.pdf", System.getProperty("user.dir")); 
    	PdfWriter writer;
		try
		{
			writer = new PdfWriter(dest);
			PdfDocument pdfDoc = new PdfDocument(writer);
			Document document = new Document(pdfDoc);
			
			for(index=0; index < familyList.size(); index++)
			{
				if(index % 2 == 0)
					createDelCardPDF(year, familyList.get(index), pdfDoc, document, CardType.TOP);
				else if(index == familyList.size()-1)
					createDelCardPDF(year, familyList.get(index), pdfDoc, document, CardType.LAST);
				else
					createDelCardPDF(year, familyList.get(index), pdfDoc, document, CardType.BOTTOM);				
			}
			
			document.close();
			
			bResult = true;
			message = String.format("%d delivery cards created", index);
		} 
		catch (FileNotFoundException e) 
		{
			message = "Failed: IOException occurred";
		}
		catch (IOException e) 
		{
			message = "Failed: IOException occurred";
		}
		
		Gson gson = new Gson();
		CreationResult result = new CreationResult(bResult, message);
		String response = gson.toJson(result, CreationResult.class);
			
		//wrap the json in the callback function per the JSONP protocol
		String callbackFunction = (String) params.get("callback");
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);
    }
    //create a delivery card pdf
    void createDelCardPDF(int year, ONCFamily f, PdfDocument pdfDoc, Document document, CardType type) throws IOException
    {
		document.setMargins(16,40,0,20);
		
		//set up fonts for various components
		PdfFont datafont = PdfFontFactory.createFont(StandardFonts.TIMES_ROMAN, true);
		PdfFont boldfont = PdfFontFactory.createFont(StandardFonts.TIMES_BOLD, true);
		
		//create a newline paragraph
		Paragraph newline = new Paragraph("\n");
		
		//create the top line table
		Paragraph oncNumP = new Paragraph(f.getONCNum()).setFont(boldfont).setFontSize(26);
		Paragraph driverP = new Paragraph("Driver #: ______ ").setFont(boldfont).setFontSize(20);
		Paragraph nameP = new Paragraph("Name: ________________").setFont(boldfont).setFontSize(20);
		
		float[] topColWidths = {164, 160, 236};
		Table table = new Table(topColWidths);
	    Cell cell = new Cell().add(oncNumP).setBorder(Border.NO_BORDER);
	    table.addCell(cell);
	    Cell cellTwo = new Cell().add(driverP)
	    		.setTextAlignment(TextAlignment.RIGHT)
	    	    .setVerticalAlignment(VerticalAlignment.MIDDLE).setBorder(Border.NO_BORDER);
	    table.addCell(cellTwo);
	    Cell cellThree = new Cell().add(nameP)
	    		.setVerticalAlignment(VerticalAlignment.MIDDLE).setBorder(Border.NO_BORDER);
	    table.addCell(cellThree);
	    document.add(table);
								
	    //create the address table
		float[] addrColWidths = {200, 280};
		table = new Table(addrColWidths);
		
		Paragraph p = new Paragraph();
		p.setFont(datafont).setFontSize(12).setMultipliedLeading(1.0f);
		List<Text> address = new ArrayList<Text>();
		address.add(new Text("\n"));
		address.add(new Text(String.format("%s %s\n", f.getFirstName(), f.getLastName())));
		address.add(new Text(String.format("%s %s\n", f.getHouseNum(), f.getStreet())));
		address.add(new Text(String.format("%s, VA %s\n", f.getCity(), f.getZipCode())));	   
		for(Text t : address)
			p.add(t);
		
		table.addCell(new Cell().add(p).setBorder(Border.NO_BORDER));
		table.addCell(new Cell().setBorder(Border.NO_BORDER));
		table.addCell(new Cell().setBorder(Border.NO_BORDER));
		table.addCell(new Cell().add(new Paragraph(String.format("ONC %d", year))
												.setFont(boldfont)
											    .setFontSize(12)
											    .setTextAlignment(TextAlignment.RIGHT))
												.setBorder(Border.NO_BORDER));
	    document.add(table);
	    
	    //create the middle table
		CellList cellList = new CellList();
		cellList.addCell("Elementary School:  ",regionDB.getSchoolByCode(f.getSchoolCode()).getName(), boldfont, datafont);
		cellList.addCell("Region:  ", ServerRegionDB.getRegion(f.getRegion()), boldfont, datafont);
		cellList.addCell("Primary Phone:  ", f.getHomePhone(), boldfont, datafont);
		cellList.addCell("Alternate Phone:  ", f.getCellPhone(), boldfont, datafont);
		cellList.addCell("Language:  ", f.getLanguage(), boldfont, datafont, 1, 2);
		cellList.addCell("Special Delivery Comments:  ", f.getDeliveryInstructions(), boldfont, datafont, 1,2);
	
		float[] midColWidths = {220, 180};
		table = new Table(midColWidths);
		for(Cell c : cellList.getCells())
			table.addCell(c);
	    document.add(table);
	    
	    document.add(newline);
	    
	    //create the bottom table
	    float[] botColWidths = {50, 130, 100, 180};
		table = new Table(botColWidths);
	    Cell cell5 = new Cell().add(new Paragraph("TOY BAGS").setFont(boldfont).setFontSize(11).setTextAlignment(TextAlignment.CENTER)).setBorder(Border.NO_BORDER);
	    Cell cell6 = new Cell().add(new Paragraph("BIKE(S)").setFont(boldfont).setFontSize(11).setTextAlignment(TextAlignment.CENTER)).setBorder(Border.NO_BORDER);
	    Cell cell7 = new Cell().add(new Paragraph("OTHER LARGE ITEMS").setFont(boldfont).setFontSize(11).setTextAlignment(TextAlignment.CENTER)).setBorder(Border.NO_BORDER);
	    
	    String zBikes = Integer.toString(getNumberOfBikesSelectedForFamily(year, f));
	    Cell cellBikeCount = new Cell().add(new Paragraph(zBikes).setFont(boldfont).setFontSize(28).setTextAlignment(TextAlignment.CENTER)).setBorder(Border.NO_BORDER);
	   
	    table.addCell(new Cell().setBorder(Border.NO_BORDER));
	    table.addCell(cell5);
	    table.addCell(cell6);
	    table.addCell(cell7);
	    table.addCell(new Cell().setBorder(Border.NO_BORDER));
	    table.addCell(new Cell().setBorder(Border.NO_BORDER));
	    table.addCell(cellBikeCount);
	    table.addCell(new Cell().setBorder(Border.NO_BORDER));
	    document.add(table);
	    
	    // Creating the season icon as an ImageData object
	    String[] imageFiles = {"Gift-icon.png","Christmas-Mistletoe-icon.gif",
	    		"Snowman-icon.gif","Santa-icon.gif","Stocking-icon.gif"};
	    
	    int imageFileIndex = year % 5;
	    String imageFileName = imageFiles[imageFileIndex];
	    String imageFile = String.format("%s/Icons/%s", System.getProperty("user.dir"), imageFileName);
	    
	    ImageData data = ImageDataFactory.create(imageFile);
	    Image img = new Image(data);
	    img.scaleAbsolute(44,44);
	    img.setFixedPosition(500, type == CardType.TOP ? 700 : 270);
	    document.add(img);
	   

		//UPC-E Barcode   
	    BarcodeEAN barcode = new BarcodeEAN(pdfDoc);
	    barcode.setCodeType(BarcodeEAN.UPCE);
	    String formattedONCNum = ("0000000" + f.getONCNum()).substring(f.getONCNum().length());
	    int zParity = BarcodeEAN.calculateEANParity(formattedONCNum);
	    barcode.setCode(formattedONCNum + Integer.toString(zParity));
	    PdfFormXObject barcodeObject = barcode.createFormXObject(ColorConstants.BLACK,ColorConstants.BLACK, pdfDoc);
	    Image barcodeImage = new Image(barcodeObject).scale(1.2f, 1.2f);
	    Paragraph barcodeP = new Paragraph().setTextAlignment(TextAlignment.RIGHT);
	    barcodeP.add(barcodeImage);
	    document.add(barcodeP);
	    
	    if(type == CardType.TOP)	//add space at the end to align the top of the bottom card
	    {	
	    	Paragraph fillerP = new Paragraph().setFixedLeading(28).add(new Text("\n\n"));
	    	document.add(fillerP);
	    }
	    else if(type == CardType.BOTTOM)
	    	document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
    }
    
    int getNumberOfBikesSelectedForFamily(int year, ONCFamily f)
	{
    	ServerChildGiftDB childGiftDB = null;
    	try
    	{
			childGiftDB = ServerChildGiftDB.getInstance();
			int numGiftsPerChild = globalDB.getServerGlobalVariables(year).getNumberOfGiftsPerChild();
			
			int nBikes = 0;
			ONCChildGift cg;
			for(ONCChild c: childDB.getChildList(year, f.getID()))
//				for(int wn=0; wn < NUMBER_OF_WISHES_PER_CHILD; wn++)
				for(int wn=0; wn < numGiftsPerChild; wn++)
					if((cg = childGiftDB.getCurrentChildGift(year, c.getID(), wn)) != null && cg.getCatalogGiftID() == cat.getGiftID(year, "Bike"))
						nBikes++;
					
			return nBikes;
		} 
    	catch (FileNotFoundException e) 
    	{
    		return 0;
		}
    	catch (IOException e) 
    	{
			return 0;
		}	
	}
/*    
    void familiesWithoutHistories()
    {
    	Integer years[] = {2012,2013,2014,2015,2016,2017,2018,2019,2020};
    	
    	for(Integer year : years)
    	{
    		List<ONCFamily> searchList = familyDB.get(DBManager.offset(year)).getList();
    		for(ONCFamily f: searchList)
    		{
    			FamilyHistory fh = familyHistoryDB.getLastFamilyHistory(year, f.getID());
    			if(fh == null)	//no history
    			{
    				DBManager.setDBYearLock(year, false); //temporarily unlock the year if it was locked
    				//create a new history
    				FamilyHistory newHistoryObj = new FamilyHistory(-1, f.getID(), f.getFamilyStatus(), f.getGiftStatus(), "",
    						"Added Missing History", "O'Neill, J", System.currentTimeMillis(), f.getDNSCode());
    				
    				familyHistoryDB.addFamilyHistoryObject(year, newHistoryObj);
			
    				System.out.println(String.format("ServFamDB.famWithoutHistAdded: year=%d, id=%d, ONC#=%s", year, f.getID(), f.getONCNum()));
    			}
    		}
    	}
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
/* 
    private static class ONCWebsiteFamilyLNComparator implements Comparator<ONCWebsiteFamily>
	{
		@Override
		public int compare(ONCWebsiteFamily o1, ONCWebsiteFamily o2)
		{
			return o1.getHOHLastName().toLowerCase().compareTo(o2.getHOHLastName().toLowerCase());
		}
	}

    private void convertDNSCodes()
    {
    		//for each year in the datbaase, convert the DNS code from a string to an int
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
    
    private class CellList
    {  	
    	private List<Cell> cellList;
    	
    	CellList()
    	{
    		cellList = new ArrayList<Cell>();
    	}
    	void addCell(String leader, String data, PdfFont leaderFont, PdfFont dataFont)
    	{
    		cellList.add(new Cell().add(new Paragraph().add(new Text(leader).setFont(leaderFont))
    											.add(new Text(data).setFont(dataFont))
    											.setFontSize(12))
    											.setHeight(23)
    											.setBorder(Border.NO_BORDER));
    	}
    	void addCell(String leader, String data, PdfFont leaderFont, PdfFont dataFont, int rowspan, int colspan)
    	{
    		Cell newCell = new Cell(rowspan, colspan);
    		newCell.add(new Paragraph().add(new Text(leader).setFont(leaderFont))
									  .add(new Text(data).setFont(dataFont))
									  .setFontSize(12));
    		newCell.setHeight(23).setBorder(Border.NO_BORDER);
    		
    		cellList.add(newCell);
    											
    	}
//    	void addCell()
//    	{
//    		cellList.add(new Cell().setBorder(Border.NO_BORDER));
//   	}
    	
    	List<Cell> getCells() { return cellList; }
    }
    
    private enum CardType { TOP, BOTTOM, LAST };
    
    private class CreationResult
    {
    	@SuppressWarnings("unused")
		private boolean bResult;
    	@SuppressWarnings("unused")
		private String message;
    		
    	CreationResult(boolean bResult, String message)
    	{
    		this.bResult = bResult;
    		this.message = message;
    	}
    }
}