package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

import ourneighborschild.EmailAddress;
import ourneighborschild.ONCEmail;
import ourneighborschild.ONCEmailAttachment;
import ourneighborschild.ONCVolunteer;
import ourneighborschild.ServerCredentials;
import ourneighborschild.ServerGVs;
import ourneighborschild.SignUpActivity;
import ourneighborschild.VolAct;
import ourneighborschild.Activity;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerVolunteerDB extends ServerSeasonalDB implements SignUpListener
{
	private static final int VOLUNTEER_DB_HEADER_LENGTH = 21;
	private static final String VOLUNTEER_EMAIL_ADDRESS = "volunteer@ourneighborschild.org";
	private static final String VOLUNTEER_EMAIL_PASSWORD = "crazyelf";
	
	private static List<VolunteerDBYear> volDB;
	private static ServerVolunteerDB instance = null;
	
	private static ClientManager clientMgr;
	private static ServerWarehouseDB warehouseDB;
	private static ServerActivityDB activityDB;
	private static ServerVolunteerActivityDB volActDB;
	private static ServerGlobalVariableDB sGVDB;
//	private static SignUpGeniusIF geniusIF;

	private ServerVolunteerDB() throws FileNotFoundException, IOException
	{
		//create the driver data bases for TOTAL_YEARS number of years
		volDB = new ArrayList<VolunteerDBYear>();
		
//		geniusIF = SignUpGeniusIF.getInstance();
//		geniusIF.addSignUpListener(this);
		
		clientMgr = ClientManager.getInstance();
		warehouseDB = ServerWarehouseDB.getInstance();
		activityDB = ServerActivityDB.getInstance();
		volActDB = ServerVolunteerActivityDB.getInstance();
		sGVDB = ServerGlobalVariableDB.getInstance();

		//populate the data base for the last TOTAL_YEARS from persistent store
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the child list for each year
			VolunteerDBYear volunteerDBYear = new VolunteerDBYear(year);
									
			//add the list of children for the year to the db
			volDB.add(volunteerDBYear);
									
			//import the volunteers from persistent store
			importDB(year, String.format("%s/%dDB/VolunteerDB.csv",
					System.getProperty("user.dir"),
						year), "Volunteer DB", VOLUNTEER_DB_HEADER_LENGTH);
		
			//set the next id
			volunteerDBYear.setNextID(getNextID(volunteerDBYear.getList()));
		}
		
		//connect to sign up genius thru the interface
//		geniusIF = SignUpGeniusIF.getInstance();
//		geniusIF.requestSignUpContent(13398811, SignUpReportType.filled);
	}
	
	public static ServerVolunteerDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerVolunteerDB();
		
		return instance;
	}
	
	List<ONCVolunteer> clone(int year)
	{
		List<ONCVolunteer> volList = volDB.get(year - BASE_YEAR).getList();
		List<ONCVolunteer> cloneList = new ArrayList<ONCVolunteer>();
		
		for(ONCVolunteer v : volList)
			cloneList.add(new ONCVolunteer(v));
		
		return cloneList;		
	}
	
	String getDrivers(int year)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCVolunteer>>(){}.getType();
			
		String response = gson.toJson(volDB.get(year - BASE_YEAR).getList(), listtype);
		return response;	
	}
	
	static HtmlResponse getVolunteerJSONP(int year, String fn, String ln, String cell, String callbackFunction)
	{		
		Gson gson = new Gson();
		List<ONCVolunteer> searchList = volDB.get(year - BASE_YEAR).getList();
		
		String response;
		int index=0;
		
		//determine whether to find the volunteer by first and last name or by cell phone
		if(fn.isEmpty() || ln.isEmpty())
		{
			//either first or last name is empty, search for cell phone match
			while(index < searchList.size() && !searchList.get(index).doesCellPhoneMatch(cell))
			{
				index++;
			}
		}
		else
		{
			//search for first and last name match
			while(index < searchList.size() && !(searchList.get(index).getFirstName().equalsIgnoreCase(fn) && 
					 searchList.get(index).getLastName().equalsIgnoreCase(ln)))
			{
				index++;
			}
		}
		
		if(index< searchList.size())
			response = gson.toJson(searchList.get(index), ONCVolunteer.class);
		else
			response = "{\"id\":-1}";	//send back id = -1, meaning not found
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}

	@Override
	String add(int year, String json) 
	{
		//Need to change this to add a check to see if the driver already exists, 
		//similar to what we do for agents.
		
		//Create a driver object for the new driver
		Gson gson = new Gson();
		ONCVolunteer addedDriver = gson.fromJson(json, ONCVolunteer.class);
				
		//set the new ID for the new driver
		VolunteerDBYear volunteerDBYear = volDB.get(year - BASE_YEAR);
		addedDriver.setID(volunteerDBYear.getNextID());
		volunteerDBYear.add(addedDriver);
		volunteerDBYear.setChanged(true);
				
		return "ADDED_DRIVER" + gson.toJson(addedDriver, ONCVolunteer.class);
	}
	
	ONCVolunteer add(int year, ONCVolunteer addedVol) 
	{		
		//set the new ID for the new driver
		VolunteerDBYear volunteerDBYear = volDB.get(year - BASE_YEAR);
		addedVol.setID(volunteerDBYear.getNextID());
		volunteerDBYear.add(addedVol);
		volunteerDBYear.setChanged(true);
		
		return addedVol;
	}
	
	String addVolunteerGroup(int year, String volunteerGroupJson, DesktopClient currClient)
	{
		//get the current year volunteer list for the proper year
		List<ONCVolunteer> cyVolList = volDB.get(year - BASE_YEAR).getList();
		
		//create the response list of jsons
		List<String> jsonResponseList = new ArrayList<String>();
		
		//un-bundle the input list of ONCVolunteer objects
		Gson gson = new Gson();
		Type listOfVolunteers = new TypeToken<ArrayList<ONCVolunteer>>(){}.getType();		
		List<ONCVolunteer> inputVolList = gson.fromJson(volunteerGroupJson, listOfVolunteers);
		
		//for each volunteer in the list, check to see if it's a duplicate of an existing 
		//volunteer. If not add it.
		for(ONCVolunteer inputVol : inputVolList)
		{
			int index = 0;
			while(index < cyVolList.size() && !inputVol.equals(cyVolList.get(index)))
				index++;
							
			if(index == cyVolList.size())
			{
				//no match found, add the input volunteer to the current year list
				ONCVolunteer addedVol = add(year, inputVol);
				if(addedVol != null)
					jsonResponseList.add("ADDED_DRIVER" + gson.toJson(addedVol, ONCVolunteer.class));
			}
		}
		
		//notify all other clients of the imported volunteer objects
		if(!jsonResponseList.isEmpty())
			clientMgr.notifyAllOtherInYearClients(currClient, jsonResponseList);
		
		Type listOfChanges = new TypeToken<ArrayList<String>>(){}.getType();
		return "ADDED_VOLUNTEER_GROUP" + gson.toJson(jsonResponseList, listOfChanges);
	}
	
	String updateVolunteerGroup(int year, String volunteerGroupJson, DesktopClient currClient)
	{
		//create the response list of jsons
		List<String> jsonResponseList = new ArrayList<String>();
		
		//un-bundle the input list of updated ONCVolunteer objects
		Gson gson = new Gson();
		Type listOfVolunteers = new TypeToken<ArrayList<ONCVolunteer>>(){}.getType();		
		List<ONCVolunteer> inputVolList = gson.fromJson(volunteerGroupJson, listOfVolunteers);
		
		//for each volunteer in the list, find them in the current year and replace them
		for(ONCVolunteer inputVol : inputVolList)
		{
			//volunteer found, add the input volunteer to the current year list
			ONCVolunteer updatedVol = update(year, inputVol);
			if(updatedVol != null)
				jsonResponseList.add("UPDATED_DRIVER" + gson.toJson(updatedVol, ONCVolunteer.class));
		}
		
		//notify all other clients of the imported volunteer objects
		clientMgr.notifyAllOtherInYearClients(currClient, jsonResponseList);
		
		Type listOfChanges = new TypeToken<ArrayList<String>>(){}.getType();
		return "UPDATED_VOLUNTEER_GROUP" + gson.toJson(jsonResponseList, listOfChanges);
	}
	
	/************
	 * Signs in volunteers from the web site. If the volunteer is already in the database,
	 * simply notes the have check into the warehouse. If they're not, adds them as a new
	 * volunteer. Asks the activity database for the closest activity match to create an 
	 * activity for the volunteer.
	 * Activity database. 
	 * @param year
	 * @param volParams
	 * @param callbackFunction
	 * @return
	 */
	static HtmlResponse signInVolunteerJSONP(int year, Map<String, String> volParams,
											boolean bWarehouseSignIn, String website,
											String callbackFunction)
	{	
		List<String> responseList = new ArrayList<String>();
		
		String fn = volParams.get("delFN");
		String ln = volParams.get("delLN");
		
		VolunteerDBYear volDBYear = volDB.get(year - BASE_YEAR);
		List<ONCVolunteer>volList = volDBYear.getList();
		
		int index=0;
		while(index < volList.size() && !(volList.get(index).getFirstName().equalsIgnoreCase(fn) && 
				 volList.get(index).getLastName().equalsIgnoreCase(ln)))
			index++;
		
		if(index < volList.size())
		{
			//found the volunteer, update their contact info and increment their sign-ins.
			//if the volunteer has already registered, don't worry about activities	
			ONCVolunteer updatedVol = volList.get(index);
			
			if(bWarehouseSignIn)
			{
				updatedVol.setSignIns(updatedVol.getSignIns() + 1);
				updatedVol.setDateChanged(new Date());
			}
			
			if(volParams.get("group").equals("Other"))
				updatedVol.setOrganization(volParams.get("groupother"));
			else
				updatedVol.setOrganization(volParams.get("group"));
			
			if(volParams.get("group").equals("Self") || volParams.get("group").equals("Other"))
			{
				if(!volParams.get("delhousenum").isEmpty())
					updatedVol.setHouseNum(volParams.get("delhousenum"));
				if(!volParams.get("delstreet").isEmpty())
					updatedVol.setStreet(volParams.get("delstreet"));
				if(!volParams.get("delunit").isEmpty())
					updatedVol.setUnit(volParams.get("delunit"));
				if(!volParams.get("delcity").isEmpty())
					updatedVol.setCity(volParams.get("delcity"));
				if(!volParams.get("delzipcode").isEmpty())
					updatedVol.setZipCode(volParams.get("delzipcode")); 
				if(!volParams.get("delemail").isEmpty())
					updatedVol.setEmail(volParams.get("delemail"));
				if(!volParams.get("comment").isEmpty())
					updatedVol.setComment(volParams.get("comment"));
				
				//check if the single phone provided in the sign-in matches either of the current
				//phones. If it doesn't, assume it's a cell phone and update it.
				String webphone = volParams.get("primaryphone");
				if(!webphone.isEmpty() && !webphone.equals(updatedVol.getHomePhone()) && 
					!webphone.equals(updatedVol.getCellPhone()))
					updatedVol.setCellPhone(volParams.get("primaryphone"));			
			}
			
			volDBYear.setChanged(true);
			
			//notify in year clients that the volunteer has signed in
			Gson gson = new Gson();
			responseList.add("UPDATED_DRIVER" + gson.toJson(updatedVol, ONCVolunteer.class));
			clientMgr.notifyAllInYearClients(year, responseList);
			
			//notify the the warehouseDB of the sign-in
			warehouseDB.add(year, updatedVol);
		}
		else	
		{
			//Didn't find the volunteer, create and add a new one, including the activity that
			//matches from the Activity DB.
			ONCVolunteer addedVol = new ONCVolunteer(-1, "", fn, ln, volParams.get("delemail"), 
					volParams.get("delhousenum"), volParams.get("delstreet"), volParams.get("delunit"),
					volParams.get("delcity"), volParams.get("delzipcode"),
					volParams.get("primaryphone"), volParams.get("primaryphone"),
					volParams.get("group").equals("Other") ? volParams.get("groupother") : volParams.get("group"),
					volParams.get("comment"), new Date(), website);
			
			addedVol.setID(volDBYear.getNextID());
			if(bWarehouseSignIn)	
				addedVol.setSignIns(addedVol.getSignIns() + 1);
			
			volDBYear.add(addedVol);
			volDBYear.setChanged(true);
			
			//ask the activity DB for the activity that matches the sign-in within +/- 59 minutes
			//of the activity start and end dates/times
//			Activity activity = activityDB.matchActivity(year, 1513532756000L);	//test for dday 2017 1745 UTC
			Activity activity = activityDB.matchActivity(year, System.currentTimeMillis());
			if(activity != null)
			{
				//DEBUG
//				System.out.println(String.format("ServVolDB.signInVol: actID = %d, name= %s",
//						activity.getID(), activity.getName()));
				
				//create the volAct and ask the VolActDB to add it. Set geniusID to -1 to indicate
				//the volunteer activity was not imported from SignUp Genius
				VolAct addVAReq = new VolAct(-1, addedVol.getID(), activity.getID(), -1, 1, "");
				responseList.add(volActDB.add(year, addVAReq));
			}
			
			//notify in year clients
			Gson gson = new Gson();
			responseList.add("ADDED_DRIVER" + gson.toJson(addedVol, ONCVolunteer.class));
			clientMgr.notifyAllInYearClients(year, responseList);
			
			//notify the warehouseDB of a sign-in
			warehouseDB.add(year, addedVol);
		}
		
		String responseJson = String.format("{\"message\":\"%s, thank you for volunteering "
				+ "with Our Neighbor's Child!\"}", volParams.get("delFN"));
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + responseJson +")", HttpCode.Ok);		
	}
	
	/************
	 * Registers volunteers from the web site. If the volunteer is already in the database,
	 * check to see if the activity is in the data base. If it's not, add the activity to the Volunteer
	 * Activity database. 
	 * @param year
	 * @param volParams
	 * @param callbackFunction
	 * @return
	 */
	static HtmlResponse addVolunteerJSONP(int year, Map<String, String> volParams,
											Map<String, String> activityParams,
											boolean bWarehouseSignIn,
											String website, String callbackFunction)
	{	
		List<String> responseList = new ArrayList<String>();
		
		String fn = volParams.get("delFN");
		String ln = volParams.get("delLN");
		
		VolunteerDBYear volDBYear = volDB.get(year - BASE_YEAR);
		List<ONCVolunteer>volList = volDBYear.getList();
		
		int index=0;
		while(index < volList.size() && !(volList.get(index).getFirstName().equalsIgnoreCase(fn) && 
				 volList.get(index).getLastName().equalsIgnoreCase(ln)))
			index++;
		
		if(index < volList.size())
		{
			//found the volunteer, update their contact info and increment their sign-ins
			ONCVolunteer updatedVol = volList.get(index);
			
			if(bWarehouseSignIn)
			{
				updatedVol.setSignIns(updatedVol.getSignIns() + 1);
				updatedVol.setDateChanged(new Date());
			}
			
			//A DESIGN QUESTION IS SHOULD WE CHANGE THE GROUP FIELD FROM A STRING TO AN INTEGER AND
			//FORCE ALL GROUPS TO BE IN THE GROUP DATABASE? THIS WILL CHANGE THE GROUP BEHAVIOR.
			if(volParams.get("group").equals("Other"))
				updatedVol.setOrganization(volParams.get("groupother"));
			else
				updatedVol.setOrganization(volParams.get("group"));
			
			if(volParams.get("group").equals("Self") || volParams.get("group").equals("Other"))
			{
				if(!volParams.get("delhousenum").isEmpty())
					updatedVol.setHouseNum(volParams.get("delhousenum"));
				if(!volParams.get("delstreet").isEmpty())
					updatedVol.setStreet(volParams.get("delstreet"));
				if(!volParams.get("delunit").isEmpty())
					updatedVol.setUnit(volParams.get("delunit"));
				if(!volParams.get("delcity").isEmpty())
					updatedVol.setCity(volParams.get("delcity"));
				if(!volParams.get("delzipcode").isEmpty())
					updatedVol.setZipCode(volParams.get("delzipcode")); 
				if(!volParams.get("delemail").isEmpty())
					updatedVol.setEmail(volParams.get("delemail"));
				if(!volParams.get("comment").isEmpty())
					updatedVol.setComment(volParams.get("comment"));
				
				//check if the single phone provided in the sign-in matches either of the current
				//phones. If it doesn't, assume it's a cell phone and update it.
				String webphone = volParams.get("primaryphone");
				if(!webphone.isEmpty() && !webphone.equals(updatedVol.getHomePhone()) && 
					!webphone.equals(updatedVol.getCellPhone()))
					updatedVol.setCellPhone(volParams.get("primaryphone"));
				
				//if its an updated registration, create a new activity list and replace the 
				//the prior list. If its a sign-in from the warehouse, add the one activity
				//to the volunteers current activity list. The activity won't be added if it already
				//exists per the VolunteerActivity API
				responseList = volActDB.processActivityList(year, activityDB.createActivityList(year, activityParams, updatedVol));			
			}
			
			volDBYear.setChanged(true);
			
			//notify in year clients
			Gson gson = new Gson();
			responseList.add("UPDATED_DRIVER" + gson.toJson(updatedVol, ONCVolunteer.class));
			clientMgr.notifyAllInYearClients(year, responseList);
		}
		else	
		{
			//Didn't find the volunteer, create and add a new one, including their activity list
			ONCVolunteer addedVol = new ONCVolunteer(-1, "N/A", fn, ln, volParams.get("delemail"), 
					volParams.get("delhousenum"), volParams.get("delstreet"), volParams.get("delunit"),
					volParams.get("delcity"), volParams.get("delzipcode"),
					volParams.get("primaryphone"), volParams.get("primaryphone"),
					volParams.get("group").equals("Other") ? volParams.get("groupother") : volParams.get("group"),
					volParams.get("comment"), new Date(), website);
			
			addedVol.setID(volDBYear.getNextID());
			if(bWarehouseSignIn)	
				addedVol.setSignIns(addedVol.getSignIns() + 1);
			
			volDBYear.add(addedVol);
			volDBYear.setChanged(true);
			
			responseList = volActDB.processActivityList(year, activityDB.createActivityList(year, activityParams, addedVol));
			
			//notify in year clients
			Gson gson = new Gson();
			responseList.add("ADDED_DRIVER" + gson.toJson(addedVol, ONCVolunteer.class));
			clientMgr.notifyAllInYearClients(year, responseList);
			
			//DISABLE AUTO EMAIL FOR NOW
//				List<ONCVolunteer> emailList = new ArrayList<ONCVolunteer>();
//				emailList.add(addedVol);
//				createAndSendVolunteerEmail(0, emailList);
		}
		
		String responseJson = String.format("{\"message\":\"%s, thank you for volunteering "
				+ "with Our Neighbor's Child!\"}", volParams.get("delFN"));
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + responseJson +")", HttpCode.Ok);		
	}
	
	String update(int year, String json)
	{
		//Create a driver object for the updated driver
		Gson gson = new Gson();
		ONCVolunteer updatedDriver = gson.fromJson(json, ONCVolunteer.class);
		
		//Find the position for the current driver being updated
		VolunteerDBYear volunteerDBYear = volDB.get(year - BASE_YEAR);
		List<ONCVolunteer> dAL = volunteerDBYear.getList();
		int index = 0;
		while(index < dAL.size() && dAL.get(index).getID() != updatedDriver.getID())
			index++;
		
		//Replace the current object with the update
		if(index < dAL.size())
		{
			dAL.set(index, updatedDriver);
			volunteerDBYear.setChanged(true);
			
			//if a driver number is present, check to see if the driver volunteer has
			//the activity for delivery. If they don't add it. First step is to find the
			//delivery activity, it it exists. If it does, proceed to check to see if
			//if the volunteer has a VolunterActivity for delivery. If they don't, add it.
			if(!updatedDriver.getDrvNum().isEmpty() && isNumeric(updatedDriver.getDrvNum()))
			{
				int deliveryActivityID = sGVDB.getDeliveryActivityID(year);
				if(deliveryActivityID > -1)
				{
					Activity deliveryActivity = activityDB.findActivity(year, deliveryActivityID);
					if(deliveryActivity != null)
					{
						String addedVolActResult = checkForActivityAndAddIfMissing(year, updatedDriver, deliveryActivity);
						if(addedVolActResult.startsWith("ADDED_VOLUNTEER_ACTIVITY"))
							clientMgr.notifyAllInYearClients(year, addedVolActResult);
					}
				}
			}
			
			return "UPDATED_DRIVER" + gson.toJson(updatedDriver, ONCVolunteer.class);
		}
		else
			return "UPDATE_FAILED";
	}
	
	String checkForActivityAndAddIfMissing(int year, ONCVolunteer v, Activity a)
	{
		String result = null;
		List<VolAct> volActList = volActDB.getVolunteerActivities(year, v);
		int index = 0;
		while(index < volActList.size() && volActList.get(index).getActID() != a.getID())
			index++;
		
		if(index == volActList.size())	//activity wasn't found, add it.
			result = volActDB.add(year,new VolAct(-1, v.getID(), a.getID(), a.getGeniusID(), 1, ""));
		
		return result;
	}
	
	ONCVolunteer update(int year, ONCVolunteer updatedVolunteer)
	{
//		Gson gson = new Gson();
		
		//Find the position for the current volunteer being updated
		VolunteerDBYear volunteerDBYear = volDB.get(year - BASE_YEAR);
		List<ONCVolunteer> dAL = volunteerDBYear.getList();
		int index = 0;
		while(index < dAL.size() && dAL.get(index).getID() != updatedVolunteer.getID())
			index++;
		
		//Replace the current object with the update
		if(index < dAL.size())
		{
			dAL.set(index, updatedVolunteer);
			volunteerDBYear.setChanged(true);
//			return "UPDATED_DRIVER" + gson.toJson(updatedVolunteer, ONCVolunteer.class);
			return updatedVolunteer;
		}
		else
//			return "UPDATE_FAILED";
			return null;
	}
	
	String delete(int year, String json)
	{
		//Create an object for the delete request
		Gson gson = new Gson();
		ONCVolunteer deletedDriver = gson.fromJson(json, ONCVolunteer.class);
		
		//find and remove the deleted child from the data base
		VolunteerDBYear volunteerDBYear = volDB.get(year - BASE_YEAR);
		List<ONCVolunteer> dAL = volunteerDBYear.getList();
		
		int index = 0;
		while(index < dAL.size() && dAL.get(index).getID() != deletedDriver.getID())
			index++;
		
		if(index < dAL.size())
		{
			dAL.remove(index);
			volunteerDBYear.setChanged(true);
			return "DELETED_DRIVER" + json;
		}
		else
			return "DELETE_FAILED";	
	}
	
	ONCVolunteer getDriverByDriverNumber(int year, String drvNum)
	{
		List<ONCVolunteer> dAL = volDB.get(year-BASE_YEAR).getList();
		
		//find the driver
		int index = 0;	
		while(index < dAL.size() && !dAL.get(index).getDrvNum().equals(drvNum))
			index++;
			
		if(index < dAL.size())
			return dAL.get(index);
		else
			return null;	
	}
	
	List<ONCVolunteer> processUpdatedSignUpGeniusVolunteers(List<ONCVolunteer> sugVolList)
	{
		Gson gson = new Gson();
		List<ONCVolunteer> updatedVolList = new ArrayList<ONCVolunteer>();
		List<String> clientJsonMssgList = new ArrayList<String>();
		
		//add the updated volunteers to the database
		for(ONCVolunteer v : sugVolList)
		{
			ONCVolunteer updatedVol = update(DBManager.getCurrentYear(), v);
			if(updatedVol != null)
			{
				updatedVolList.add(updatedVol);
				clientJsonMssgList.add("UPDATED_DRIVER" + gson.toJson(updatedVol, ONCVolunteer.class));
			}
		}
		
		if(!clientJsonMssgList.isEmpty())
		{
			//there were updated volunteers, send list of new json's to clients
			ClientManager clientMgr = ClientManager.getInstance();
			clientMgr.notifyAllInYearClients(DBManager.getCurrentYear(), clientJsonMssgList);
		}
		
		return updatedVolList;
	}
	
	List<ONCVolunteer> processNewSignUpGeniusVolunteers(List<ONCVolunteer> sugVolList)
	{
		Gson gson = new Gson();
		List<ONCVolunteer> newVolList = new ArrayList<ONCVolunteer>();
		List<String> clientJsonMssgList = new ArrayList<String>();
		
		//add the new volunteers to the database
		for(ONCVolunteer v : sugVolList)
		{
			ONCVolunteer addedVol = add(DBManager.getCurrentYear(), v);
			if(addedVol != null)
			{
				newVolList.add(addedVol);
				clientJsonMssgList.add("ADDED_DRIVER" + gson.toJson(addedVol, ONCVolunteer.class));
			}
			
//			System.out.println(String.format("ServVolDB.newAndMod: newVol: %s %s, id= %d",
//					v.getFirstName(), v.getLastName(), v.getID()));
		}
		
		if(!clientJsonMssgList.isEmpty())
		{
			//there were new activities, send list of new json's to clients
			ClientManager clientMgr = ClientManager.getInstance();
			clientMgr.notifyAllInYearClients(DBManager.getCurrentYear(), clientJsonMssgList);
		}
		
		return newVolList;
	}
	
	void updateVolunteerDriverDeliveryCounts(int year, String drvNum1, String drvNum2)
	{
		Gson gson = new Gson();
		List<ONCVolunteer> volList = volDB.get(year-BASE_YEAR).getList();
		
		List<String> volChangeList = new ArrayList<String>();
		
		if(drvNum1 != null)
		{
			//find drvNum1 and decrement their assigned delivery count
			int index = 0;	
			while(index < volList.size() && !volList.get(index).getDrvNum().equals(drvNum1))
				index++;
				
			if(index < volList.size())
			{
				volList.get(index).incrementDeliveryCount(-1);
				volChangeList.add("UPDATED_DRIVER" + gson.toJson(volList.get(index), ONCVolunteer.class));
			}
		}
		
		if(drvNum2 != null)
		{
			//find drvNum1 and increment their assigned delivery count
			int index = 0;	
			while(index < volList.size() && !volList.get(index).getDrvNum().equals(drvNum1))
				index++;
			
			if(index < volList.size())
			{
				volList.get(index).incrementDeliveryCount(1);
				volChangeList.add("UPDATED_DRIVER" + gson.toJson(volList.get(index), ONCVolunteer.class));
			}
		}
		
		if(!volChangeList.isEmpty())
		{
			//at least one driver delivery count was updated. Mark the db for saving and 
			//notify all in year clients
			volDB.get(year-BASE_YEAR).setChanged(true);
			clientMgr.notifyAllInYearClients(year, volChangeList);
		}
	}
	
	static void createAndSendVolunteerEmail(int emailType, List<ONCVolunteer> volList)
	{
		//build the email
		ArrayList<ONCEmail> emailAL = new ArrayList<ONCEmail>();
		ArrayList<ONCEmailAttachment> attachmentAL = new ArrayList<ONCEmailAttachment>();
		String subject = null;
		
		//Create the subject and attachment array list
		if(emailType == 0) //Confirmation Email
		{
			subject = "Volunteer Confirmation from Our Neighbor's Child";
		}
		
		//For each volunteer, create the email body and recipient information in an
		//ONCEmail object and add it to the email array list
		//Create the email body if the volunteer exists
		for(ONCVolunteer v : volList)
		{
			String emailBody = create2017VolunteerConfirmationEmail(v);
			
			//Create recipient list for email.
			ArrayList<EmailAddress> recipientAdressList = createRecipientList(v);
	        
	        //If the volunteer email isn't valid, the message will not be sent.
	        if(emailBody != null && !recipientAdressList.isEmpty())
	        	emailAL.add(new ONCEmail(subject, emailBody, recipientAdressList));     	
		}
		
		//Create the from address string array
		EmailAddress fromAddress = new EmailAddress(VOLUNTEER_EMAIL_ADDRESS, "Our Neighbor's Child");
//		EmailAddress fromAddress = new EmailAddress(TEST_AGENT_EMAIL_SENDER_ADDRESS, "Our Neighbor's Child");
		
		//Create the blind carbon copy list 
		ArrayList<EmailAddress> bccList = new ArrayList<EmailAddress>();
		bccList.add(new EmailAddress(VOLUNTEER_EMAIL_ADDRESS, "Volunteer Coordinator"));
//		bccList.add(new EmailAddress("kellylavin1@gmail.com", "Kelly Lavin"));
//		bccList.add(new EmailAddress("mnrogers123@msn.com", "Nicole Rogers"));
//		bccList.add(new EmailAddress("johnwoneill@cox.net", "John O'Neill"));
		
		//Create mail server accreditation, then the mailer background task and execute it
		//Go Daddy Mail
//		ServerCredentials creds = new ServerCredentials("smtpout.secureserver.net", "director@act4others.org", "crazyelf1");
		//Google Mail
		ServerCredentials creds = new ServerCredentials("smtp.gmail.com", VOLUNTEER_EMAIL_ADDRESS, VOLUNTEER_EMAIL_PASSWORD);
		
	    ServerEmailer oncEmailer = new ServerEmailer(fromAddress, bccList, emailAL, attachmentAL, creds);
	    oncEmailer.execute();		
	}
	
	/**************************************************************************************************
	 *Creates a new list of recipients for each volunteer email. For volunteers, there is only one
	 * recipient per email.
	 *If the volunteer does not have a valid email or name, an empty list is returned
	 **************************************************************************************************/
	static ArrayList<EmailAddress> createRecipientList(ONCVolunteer v)
	{
		ArrayList<EmailAddress> recipientAddressList = new ArrayList<EmailAddress>();
		
		//verify the agent has a valid email address and name. If not, return an empty list
		if(v != null && v.getEmail() != null && v.getEmail().length() > 2 &&
				v.getLastName() != null && v.getLastName().trim().length() > 2)
        {
			//LIVE EMAIL ADDRESS
			EmailAddress toAddress = new EmailAddress(v.getEmail(), v.getLastName());	//live
			recipientAddressList.add(toAddress);

			//TEST EMAIL ADDRESS
//			EmailAddress toAddress1 = new EmailAddress("johnwoneill1@gmail.com", "John O'Neill");	//test
//        	recipientAddressList.add(toAddress1);      	
        }
		
		return recipientAddressList;
	}
	
	static String create2017VolunteerConfirmationEmail(ONCVolunteer v)
	{
        //Create the text part of the email using html
		String msg = "<html><body><div>" +
			"<p>Dear " + v.getFirstName() + ",</p>"
			+ "<p>Thank you for volunteering to help Our Neighbor's Child's in 2017!!"
			+ " It's a rare and wonderful thing when an ALL volunteer orgainzation comes together"
			+ " to consistently serve our community. Of course, that's only possible because of"
			+ " people like YOU! We are tremendously grateful for your generous donation of time"
			+ " and talent!</p>"
			+ "<p>We received your registration via our website."
			+ " This table summarizes the activities for which you volunteered:</p>. "
			+ createVolunteerActivityTableHTML(v)
			+"<p>You will receive a reminder email the day before each activity."
			+" In the unlikely event  the schedule of one of your selected activites changes,"
			+" such as due to a major snow event, we will notify as soon as possible via email.</p>"
		    +"<p>As always, thank you so much for your support and I look forward to seeing you soon!</p>"
		    +"<p>Kelly</p>"
		    +"<p>Kelly Murray Lavin<br>"
		    +"Executive Director/Volunteer<br>"
		    +"Our Neighbor's Child<br>"
		    +"P.O. Box 276<br>"
		    +"Centreville, VA 20120<br>"
		    +"<a href=\"http://www.ourneighborschild.org\">www.ourneighborschild.org</a></p></div>";
		    
        return msg;
	}
	
	static String createVolunteerActivityTableHTML(ONCVolunteer v )
	{
		StringBuilder actTableHTML = new StringBuilder("<table style=\"width:100%\">");
		actTableHTML.append("<th align=\"left\"><u>Activity</u></th>");
		actTableHTML.append("<th align=\"left\"><u>Start Date</u></th>");
		actTableHTML.append("<th align=\"left\"><u>Start Time</u></th>");
		actTableHTML.append("<th align=\"left\"><u>Location</u></th>");

		try
		{
			ServerVolunteerActivityDB volActDB = ServerVolunteerActivityDB.getInstance();
			ServerActivityDB servActDB = ServerActivityDB.getInstance();
			
			List<VolAct> vaList = volActDB.getVolunteerActivities(DBManager.getCurrentYear(), v);
			List<Activity> activities = new ArrayList<Activity>();
			
			for(VolAct va : vaList)
				activities.add(servActDB.findActivity(DBManager.getCurrentYear(), va.getActID()));
			
			//sort the search list by Start date
			Collections.sort(activities, new VolunteerActivityDateComparator());
			for(Activity va : activities)
				if(v != null)
				{
					actTableHTML.append("<tr><td>" + va.getName() + "</td>");
					actTableHTML.append("<td>" + va.getStartDate() + "</td>");
//					actTableHTML.append("<td>" + va.getStartTime() + "</td>");
					actTableHTML.append("<td>" + va.getLocation() + "</td></tr>");
				}
				
			actTableHTML.append("</table>");
					
			return actTableHTML.toString();
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

		return actTableHTML.toString();
	}
	
	@Override
	void addObject(int year, String[] nextLine)
	{
		VolunteerDBYear volunteerDBYear = volDB.get(year - BASE_YEAR);
		volunteerDBYear.add(new ONCVolunteer(nextLine));	
	}

	@Override
	void createNewYear(int newYear)
	{
		//create a new ONCVolunteer data base year for the year provided in the newYear parameter
		//The ONCVolunteer db year list is initially empty prior to the import of volunteers, so all we
		//do here is create a new VolunteerDBYear for the newYear and save it.
		VolunteerDBYear volunteerDBYear = new VolunteerDBYear(newYear);
		volDB.add(volunteerDBYear);
		volunteerDBYear.setChanged(true);	//mark this db for persistent saving on the next save event
	}
	
	@Override
	void save(int year)
	{
		 VolunteerDBYear volunteerDBYear = volDB.get(year - BASE_YEAR);
		 
		 if(volunteerDBYear.isUnsaved())
		 {
			 String[] driverHeader = {"Driver ID", "Driver Num" ,"First Name", "Last Name", "House Number", "Street",
			 			"Unit", "City", "Zip", "Email", "Home Phone", "Cell Phone", "Comment",
			 			"Group", "# Del. Assigned", "#Sign-Ins", "Time Stamp", "Changed By",
			 			"Stoplight Pos", "Stoplight Mssg", "Changed By"};
			 
			String path = String.format("%s/%dDB/VolunteerDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(volunteerDBYear.getList(),  driverHeader, path);
			volunteerDBYear.setChanged(false);
		}
	}
	
	boolean isActivityInList(List<SignUpActivity> list, long slotitemid)
	{
		int index = 0;
		while(index < list.size() && list.get(index).getSlotitemid() != slotitemid)
			index++;
		
		return index < list.size();
	}
	
	@Override
	public void signUpDataReceived(SignUpEvent event)
	{
		// TODO Auto-generated method stub
		
	}
	private class VolunteerDBYear extends ServerDBYear
	{
		private List<ONCVolunteer> volList;
	    	
	    VolunteerDBYear(int year)
	    {
	    		super();
	    		volList = new ArrayList<ONCVolunteer>();
	    }
	    
	    //getters
	    List<ONCVolunteer> getList() { return volList; }
	    
	    void add(ONCVolunteer addedDriver) { volList.add(addedDriver); }
	}

	private static class VolunteerActivityDateComparator implements Comparator<Activity>
	{
		@Override
		public int compare(Activity o1, Activity o2)
		{
			if(o1.getStartDate() < o2.getStartDate())
				return -1;
			else if(o1.getStartDate() > o2.getStartDate())
				return 1;
			else
				return 0;
		}
/*		
		@Override
		public int compare(VolunteerActivity o1, VolunteerActivity o2)
		{
			
			SimpleDateFormat sdf = new SimpleDateFormat("M/d/yy");
			Date o1StartDate, o2StartDate;
			try 
			{
				o1StartDate = sdf.parse(o1.getStartDate());
				o2StartDate = sdf.parse(o2.getStartDate());
			} 
			catch (ParseException e) 
			{
				return 0;
			}
				
			return o1StartDate.compareTo(o2StartDate);
		}
*/		
	}
}
