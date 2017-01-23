package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ourneighborschild.ONCGroup;
import ourneighborschild.ONCObject;

public class ServerGroupDB extends ServerPermanentDB 
{
	private static final String GROUP_DB_FILENAME = "/GroupDB.csv";
	private static final int GROUP_RECORD_LENGTH = 9;
	
	private static ServerGroupDB instance = null;
	
	private List<ONCGroup> groupList;
	
	private ServerGroupDB() throws FileNotFoundException, IOException
	{
		groupList = new ArrayList<ONCGroup>();
		importDB(String.format("%s/PermanentDB%s", System.getProperty("user.dir"), GROUP_DB_FILENAME), "Group DB", GROUP_RECORD_LENGTH);
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
		
		//If catalog wish is located, replace the wish with the update.
		if(index == groupList.size()) 
		{
			ONCGroup currGroup = groupList.get(index);
			return "UPDATE_FAILED" + gson.toJson(currGroup , ONCGroup.class);
		}
		else
		{
			groupList.set(index, reqGroup);
			bSaveRequired = true;
			return "UPDATED_GROUP" + json;
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
	void addObject(String[] nextLine) 
	{
		groupList.add(new ONCGroup(nextLine));		
	}

	@Override
	String[] getExportHeader()
	{
		return new String[] {"ID", "Date Changed", "Changed By", "SL Position", "SL Message", 
							 "SL Changed By","Name", "Type", "Permission"};
	}

	@Override
	String getFileName() { return GROUP_DB_FILENAME; }

	@Override
	List<? extends ONCObject> getONCObjectList() { return groupList; }
}
