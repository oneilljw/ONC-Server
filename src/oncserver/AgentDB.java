package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import OurNeighborsChild.Agent;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class AgentDB extends ONCServerDB
{
	private static final int AGENT_DB_HEADER_LENGTH = 6;
	private static List<AgentDBYear> agentDB;
	private static AgentDB instance = null;
	
	private AgentDB() throws FileNotFoundException, IOException
	{
		//create the agent data base
		agentDB = new ArrayList<AgentDBYear>();
						
		//populate the agent data base for the last TOTAL_YEARS from persistent store
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the child list for each year
			AgentDBYear agentDBYear = new AgentDBYear(year);
							
			//add the list of children for the year to the db
			agentDB.add(agentDBYear);
							
			//import the children from persistent store
			importDB(year, String.format("%s/%dDB/AgentDB.csv",
					System.getProperty("user.dir"),
						year), "Child DB", AGENT_DB_HEADER_LENGTH);
							
			//set the next id
			agentDBYear.setNextID(getNextID(agentDBYear.getList()));
		}
	}
	
	public static AgentDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new AgentDB();
		
		return instance;
	}
	
	//Search the database for the family. Return a json if the family is found. 
	String getAgents(int year)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<Agent>>(){}.getType();
			
		String response = gson.toJson(agentDB.get(year - BASE_YEAR).getList(), listtype);
		return response;	
	}
	
	String update(int year, String json)
	{
		//Create an object for the request agent update
		Gson gson = new Gson();
		Agent reqObj = gson.fromJson(json, Agent.class);
		
		//Find the position for the requested object being replaced
		AgentDBYear agentDBYear = agentDB.get(year - BASE_YEAR);
		
		List<Agent> objAL = agentDBYear.getList();
		int index = 0;
		while(index < objAL.size() && objAL.get(index).getID() != reqObj.getID())
			index++;
		
		//If object is located, replace the current obj with the update.
		if(index == objAL.size()) 
		{
			Agent currObj = objAL.get(index);
			return "UPDATE_FAILED" + gson.toJson(currObj , Agent.class);
		}
		else
		{
			objAL.set(index, reqObj);
			agentDBYear.setChanged(true);
			return "UPDATED_AGENT" + json;
		}
	}
	
	String add(int year, String json)
	{
		//Create an object to add to the data base
		Gson gson = new Gson();
		Agent reqAddAgt = gson.fromJson(json, Agent.class);
		
		//get the agent list for the requested year
		AgentDBYear agentDBYear = agentDB.get(year - BASE_YEAR);
		List<Agent> agtAL = agentDBYear.getList();
		
		//check to see if the agent already exists by name. If so, don't create a new
		//agent and add to the db, it they do exist return the existing agent
		//NEED TO CHANGE HOW THE SEARCH IS DONE Nancy McMillen didn't match Nancy  McMillen
		int index = 0;
//		while(index < agtAL.size() && 
//				!reqAddAgt.getAgentName().equalsIgnoreCase(agtAL.get(index).getAgentName()))		
//			index++;
		while(index < agtAL.size() && !agtAL.get(index).doesAgentNameMatch(reqAddAgt.getAgentName()))
				index++;
		
		if(index < agtAL.size())
		{
			//found a name match. Update the remaining agent into and return. Check to see that
			//the new data field isn't blank
			Agent existingAgent = agtAL.get(index);
			if(!reqAddAgt.getAgentOrg().trim().isEmpty()) {existingAgent.setAgentOrg(reqAddAgt.getAgentOrg().trim()); }
			if(!reqAddAgt.getAgentTitle().trim().isEmpty()) {existingAgent.setAgentTitle(reqAddAgt.getAgentTitle().trim()); }
			if(!reqAddAgt.getAgentEmail().trim().isEmpty()) {existingAgent.setAgentEmail(reqAddAgt.getAgentEmail().trim()); }
			if(!reqAddAgt.getAgentPhone().trim().isEmpty()) {existingAgent.setAgentPhone(reqAddAgt.getAgentPhone().trim()); }
			agentDBYear.setChanged(true);
			return "UPDATED_AGENT" + gson.toJson(existingAgent, Agent.class);
		}
		else
		{
			//agent name match not found, add the id to the requested agent, save and return
			//set the new ID for the catalog wish
			reqAddAgt.setID(agentDBYear.getNextID());
			agentDBYear.add(reqAddAgt);
			agentDBYear.setChanged(true);
			return "ADDED_AGENT" + gson.toJson(reqAddAgt, Agent.class);	
		}
	}
	
	String delete(int year, String json)
	{
		//Create an object for the delete request
		Gson gson = new Gson();
		Agent reqDelObj = gson.fromJson(json, Agent.class);
	
		//find the wish in the catalog
		AgentDBYear agentDBYear = agentDB.get(year - BASE_YEAR);
		List<Agent> objAL = agentDBYear.getList();
		int index = 0;
		while(index < objAL.size() && objAL.get(index).getID() != reqDelObj.getID())
			index++;
		
		//wish must be present in catalog to be deleted
		if(index < objAL.size())
		{
			objAL.remove(index);
			agentDBYear.setChanged(true);
			return "DELETED_AGENT" + json;
		}
		else
			return "DELETE_AGENT_FAILED" + json;
	}
/*	
	void exportFamilyDBToCSV(ArrayList<Agent>eAL, String path)
    {	
	    try 
	    {
	    	CSVWriter writer = new CSVWriter(new FileWriter(path));
	    		
	    	String[] header = {"Agent ID", "Name", "Organization", "Title", "Email", "Phone"};
	    	writer.writeNext(header);
	    	    
	    	for(Agent a:eAL)
	    		writer.writeNext(a.getDBExportRow());	//Get family object row
	    	 
	    	writer.close();
	    	       	    
	    } 
	    catch (IOException x)
	    {
	    		System.err.format("IO Exception: %s%n", x);
	    }
    }
*/	
	private class AgentDBYear extends ServerDBYear
	{
		private List<Agent> aList;
	    	
	    AgentDBYear(int year)
	    {
	    	super();
	    	aList = new ArrayList<Agent>();
	    }
	    
	    //getters
	    List<Agent> getList() { return aList; }
	    
	    void add(Agent addedAgent) { aList.add(addedAgent); }
	}

	@Override
	void createNewYear(int newYear)
	{
		//create a new agent data base year for the year provided in the newYear parameter
		//Then copy the prior years agents to the newly created agent db year list.
		//Mark the newly created AgentDBYear for saving during the next save event
				
		//get a reference to the prior years agent list
		List<Agent> lyAgentList = agentDB.get(agentDB.size()-1).getList();
				
		//create the new WishCatalogDBYear
		AgentDBYear agentDBYear = new AgentDBYear(newYear);
		agentDB.add(agentDBYear);
				
		//add last years catalog wishes to the new years catalog
		for(Agent lyAgent : lyAgentList)
			agentDBYear.add(new Agent(lyAgent));
		
		//set the nextID for the newly created AgentDBYear
		agentDBYear.setNextID(getNextID(agentDBYear.getList()));
				
		//Mark the newly created WishCatlogDBYear for saving during the next save event
		agentDBYear.setChanged(true);
	}

	@Override
	void addObject(int year, String[] nextLine)
	{
		AgentDBYear agentDBYear =  agentDB.get(year - BASE_YEAR);
		agentDBYear.add(new Agent(Integer.parseInt(nextLine[0]), nextLine[1], nextLine[2],
				nextLine[3], nextLine[4], nextLine[5]));	
	}

	@Override
	void save(int year)
	{
		String[] header = {"Agent ID", "Name", "Organization", "Title", "Email", "Phone"};
		
		AgentDBYear agentDBYear = agentDB.get(year - BASE_YEAR);
		if(agentDBYear.isUnsaved())
		{
//			System.out.println(String.format("AgentDB save() - Saving Agent DB"));
			String path = String.format("%s/%dDB/AgentDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(agentDBYear.getList(),  header, path);
			agentDBYear.setChanged(false);
		}	
	}	
}
