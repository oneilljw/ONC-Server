package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ourneighborschild.GroupType;
import ourneighborschild.ONCGroup;
import ourneighborschild.ONCObject;
import ourneighborschild.ONCServerUser;
import ourneighborschild.UserPermission;

public class ServerGroupDB extends ServerPermanentDB 
{
	private static final String GROUP_DB_FILENAME = "GroupDB.csv";
	private static final int GROUP_RECORD_LENGTH = 11;
	
	private static ServerGroupDB instance = null;
	
	private static List<ONCGroup> groupList;
	
	private ServerGroupDB() throws FileNotFoundException, IOException
	{
		groupList = new ArrayList<ONCGroup>();
		importDB(String.format("%s/PermanentDB/%s", System.getProperty("user.dir"), GROUP_DB_FILENAME), "Group DB", GROUP_RECORD_LENGTH);
		nextID = getNextID(groupList);
		bSaveRequired = false;
	}
	
	public static ServerGroupDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerGroupDB();
		
		return instance;
	}

	//return the group list as a json
	String getGroupList()
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCGroup>>(){}.getType();
			
		String response = gson.toJson(groupList, listtype);
		return response;	
	}
	
	/********
	 * returns a json list of non-Volunteer groups based on the users permission
	 * @param loggedInUser
	 * @param agentID
	 * @param callbackFunction
	 * @return
	 */
	static HtmlResponse getGroupListJSONP(ONCServerUser loggedInUser, int agentID, boolean bDefault, String callbackFunction)
	{		
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCGroup>>(){}.getType();

		List<ONCGroup> returnList = new LinkedList<ONCGroup>();
		
		if(loggedInUser.getPermission().compareTo(UserPermission.Agent) > 0)
		{
			//admin or sys admin users return all non-volunteer groups
			for(ONCGroup g : groupList)
				if(g.getType().compareTo(GroupType.Volunteer) < 0)
					returnList.add(g);
			
			Collections.sort(returnList, new ONCGroupNameComparator());
			
			//add an all group to the top of the list with id = -1 if bAddAll is true.
			if(bDefault)
			{
				ONCGroup allGroup = new ONCGroup(-1, new Date(), loggedInUser.getLNFI(), 3, "", 
									loggedInUser.getLNFI(), "Any", GroupType.Community, 1, false, false);
				returnList.add(0, allGroup);
			}
		}
		else //user with permission AGENT logged in
		{	
//			if(agentID < 0 || loggedInUser.getID() == agentID)
//			{
				for(Integer groupID : loggedInUser.getGroupList())
				{
					int index = 0;
					while(index < groupList.size() && groupList.get(index).getID() != groupID)
						index++;
				
					if(index < groupList.size())
						returnList.add(groupList.get(index));
				}
				
				Collections.sort(returnList, new ONCGroupNameComparator());
				
				if(returnList.isEmpty())
					returnList.add(0, new ONCGroup(-2, new Date(), loggedInUser.getLNFI(), 3, "", 
									loggedInUser.getLNFI(), "None", GroupType.Community, 1, false, false));
				else if(bDefault && returnList.size() > 1)
					returnList.add(0, new ONCGroup(-1, new Date(), loggedInUser.getLNFI(), 3, "", 
									loggedInUser.getLNFI(), "Any", GroupType.Community, 1, false, false));
				
				Collections.sort(returnList, new ONCGroupNameComparator());
//			}
//			else
//			{
//				//return a group list that is the intersection of the logged in agents groups and the
//				//selected agents groups
//				ONCServerUser selectedAgent = ServerUserDB.getServerUser(agentID);
//				for(Integer groupID : loggedInUser.getGroupList())
//					for(Integer selAgentGroupID :selectedAgent.getGroupList())
//						if(selAgentGroupID == groupID)
//							returnList.add(groupList.get(groupID));
//							
//				Collections.sort(returnList, new ONCGroupNameComparator());
//			}
		}
		
		String response = gson.toJson(returnList, listtype);
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	
	/********
	 * returns a json list of Volunteer groups based on inclusion on webpage dropdown
	 * @param loggedInUser
	 * @param agentID
	 * @param callbackFunction
	 * @return
	 */
	static HtmlResponse getVolunteerGroupListJSONP(String callbackFunction)
	{		
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCGroup>>(){}.getType();

		List<ONCGroup> returnList = new LinkedList<ONCGroup>();
		
		for(ONCGroup g : groupList)
			if(g.getType() == GroupType.Volunteer && g.includeOnWebpage())
				returnList.add(g);
		
		Collections.sort(returnList, new ONCGroupNameComparator());
		
		//add an artificial "Self" group to the top of the list
		returnList.add(0, new ONCGroup(-2, new Date(), "", 3, "", 
				"", "Self", GroupType.Volunteer, 1, true, true));
		
		//add an artificial "Other" group to the bottom of the list. ID = -3 tells the web page
		//to display a text field requiring the user to specify the actual group their with
		returnList.add(new ONCGroup(-3, new Date(), "", 3, "", 
				"", "Other", GroupType.Volunteer, 1, true, true));
		
		String response = gson.toJson(returnList, listtype);
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	
	static String getVolunteerGroupHTMLSelectOptions()
	{
		//create the list of groups that are used to pre-process the web page
		List<ONCGroup> preprocessGroupList = new LinkedList<ONCGroup>();
		
		for(ONCGroup g : groupList)
			if(g.getType() == GroupType.Volunteer && g.includeOnWebpage())
				preprocessGroupList.add(g);
		
		Collections.sort(preprocessGroupList, new ONCGroupNameComparator());
		
		//add an artificial "Self" group to the top of the list
		preprocessGroupList.add(0, new ONCGroup(-2, new Date(), "", 3, "", 
				"", "Self", GroupType.Volunteer, 1, true, true));
		
		//add an artificial "Other" group to the bottom of the list. ID = -3 tells the web page
		//to display a text field requiring the user to specify the actual group their with
		preprocessGroupList.add(new ONCGroup(-3, new Date(), "", 3, "", 
				"", "Other", GroupType.Volunteer, 1, true, true));
		
		StringBuffer buff = new StringBuffer();
		
		//convert list to an html option string
		for(ONCGroup g : preprocessGroupList)
			if(g.getType() == GroupType.Volunteer && g.includeOnWebpage())
				buff.append(String.format("<option value=\"%d\" data-visible=\"%s\">%s</option>", 
						g.getID(), Boolean.toString(g.contactInfoRqrd()), g.getName()));

		return buff.toString();
	}
	
	static ONCGroup getGroup(int groupID)
	{
		int index = 0;
		while(index < groupList.size() && groupList.get(index).getID() != groupID)
			index++;
		
		return index < groupList.size() ? groupList.get(index) : null;
	}
	
	@Override
	String add(String json)
	{
		//Create a group object to add to the catalog from the json
		Gson gson = new Gson();
		ONCGroup addGroupReq = gson.fromJson(json, ONCGroup.class);
		
		addGroupReq.setID(nextID++);
		groupList.add(addGroupReq);
		bSaveRequired = true;
		
		return "ADDED_GROUP" + gson.toJson(addGroupReq, ONCGroup.class);
	}
	
	String update(String json)
	{
		//Extract the  group object to update from the json
		Gson gson = new Gson();
		ONCGroup reqGroup = gson.fromJson(json, ONCGroup.class);
		
		//Find the position for the current group object being replaced
		int index = 0;
		while(index < groupList.size() && groupList.get(index).getID() != reqGroup.getID())
			index++;
		
		//If group is located, replace the wish with the update.
		if(index == groupList.size()) 
		{
			return "UPDATE_FAILED";
		}
		else
		{
			reqGroup.setDateChanged(new Date());
			groupList.set(index, reqGroup);
			bSaveRequired = true;
			return "UPDATED_GROUP" + gson.toJson(reqGroup, ONCGroup.class);
		}
	}
	
	String delete(String json)
	{
		//Create a group object for the deleted group request
		Gson gson = new Gson();
		ONCGroup reqDelGroup = gson.fromJson(json, ONCGroup.class);
	
		//find the group object in the list
		int index = 0;
		while(index < groupList.size() && groupList.get(index).getID() != reqDelGroup.getID())
			index++;
		
		//group object must be present in group list to be deleted
		if(index < groupList.size())
		{
			groupList.remove(index);
			bSaveRequired = true;
			return "DELETED_GROUP" + json;
		}
		else
			return "DELETE_GROUP_FAILED" + json;
	}

	@Override
	void addObject(String type, String[] nextLine) 
	{
		groupList.add(new ONCGroup(nextLine));		
	}

	@Override
	String[] getExportHeader()
	{
		return new String[] {"ID", "Date Changed", "Changed By", "SL Position", "SL Message", 
							 "SL Changed By","Name", "Type", "Permission", "Website?", "Contact Info?"};
	}

	@Override
	String getFileName() { return GROUP_DB_FILENAME; }

	@Override
	List<? extends ONCObject> getONCObjectList() { return groupList; }
	
	private static class ONCGroupNameComparator implements Comparator<ONCGroup>
	{
		@Override
		public int compare(ONCGroup o1, ONCGroup o2)
		{
			return o1.getName().compareTo(o2.getName());
		}
	}	
}
