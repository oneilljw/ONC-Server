package oncserver;

/*
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import ourneighborschild.Agent;
import ourneighborschild.ONCObject;
import ourneighborschild.ONCServerUser;
import ourneighborschild.ONCUser;

import com.google.gson.Gson;
*/

public class ServerAgentDB
{
/*	
	private static final String AGENT_DB_FILENAME = "AgentDB.csv";	
	private static final int AGENT_DB_HEADER_LENGTH = 6;
	
	private static List<Agent> agentDB;
	private static ServerAgentDB instance = null;
	
	private ServerAgentDB() throws FileNotFoundException, IOException
	{
		agentDB = new ArrayList<Agent>();
		importDB(String.format("%s/PermanentDB/%s", System.getProperty("user.dir"), AGENT_DB_FILENAME), "Agent DB", AGENT_DB_HEADER_LENGTH);
		nextID = getNextID(agentDB);
		bSaveRequired = false;
/*
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
	
	public static ServerAgentDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerAgentDB();
		
		return instance;
	}
/*	
	//Return a json of Agents in the data base. 
	String getAgents()
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<Agent>>(){}.getType();
			
		String response = gson.toJson(agentDB, listtype);
		return response;	
	}
*/	
	/***
	 * Returns an <Agent> json that contains agents that referred families in the parameter
	 * year. Uses the JSONP construct.
	 * @param year
	 * @param callbackFunction
	 * @return
	 */
/*	
	static HtmlResponse getAgentsJSONP(int year, ONCUser user, String callbackFunction)
	{	
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<Agent>>(){}.getType();
		
		List<Agent> agentReferredInYearList = new ArrayList<Agent>();
		
		//if user permission is AGENT, only return a list of that agent, else return all agents
		//that referred
		if(user.getPermission().compareTo(UserPermission.Agent) == 0)
		{
			int index=0;
			while(index < agentDB.size() && agentDB.get(index).getID() != user.getAgentID())
				index++;
					
			agentReferredInYearList.add(agentDB.get(index));
		}
		else
		{
			for(Agent agent : agentDB)
				if(ServerFamilyDB.didAgentReferInYear(year, agent.getID()))
					agentReferredInYearList.add(agent);
			
			//sort the list by name
			Collections.sort(agentReferredInYearList, new ONCAgentNameComparator());
		}
			
		String response = gson.toJson(agentReferredInYearList, listtype);
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HTTPCode.Ok);		
	}
	
	static HtmlResponse getAgentJSONP(int agentID, String callbackFunction)
	{		
		Gson gson = new Gson();
		String response;
	
//		List<Agent> agtAL = agentDB.get(year-BASE_YEAR).getList();
		
		int index=0;
		while(index<agentDB.size() && agentDB.get(index).getID() != agentID)
			index++;
		
		if(index < agentDB.size())
		{
			Agent agent = new Agent(agentDB.get(index));
			response = gson.toJson(agent, Agent.class);
		}
		else
			response = "";
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HTTPCode.Ok);		
	}
	
	String update(String json)
	{
		//Create an object for the request agent update
		Gson gson = new Gson();
		Agent reqObj = gson.fromJson(json, Agent.class);
		
		//Find the position for the requested object being replaced
//		AgentDBYear agentDBYear = agentDB.get(year - BASE_YEAR);
		
//		List<Agent> objAL = agentDBYear.getList();
		int index = 0;
		while(index < agentDB.size() && agentDB.get(index).getID() != reqObj.getID())
			index++;
		
		//If object is located, replace the current obj with the update.
		if(index ==agentDB.size()) 
		{
			Agent currObj = agentDB.get(index);
			return "UPDATE_FAILED" + gson.toJson(currObj , Agent.class);
		}
		else
		{
			agentDB.set(index, reqObj);
			bSaveRequired = true;
//			agentDBYear.setChanged(true);
			
			//notify the userDB so agent profile and user profile can stay in sync
			//get a reference to the ServerUser data base
			try {
				ServerUserDB.getInstance().processAgentUpdate(reqObj);
			} catch (NumberFormatException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			return "UPDATED_AGENT" + json;
		}
	}
	
	ImportONCObjectResponse processImportedReferringAgent(Agent reqAgt)
	{		
		//get the agent list for the requested year
//		AgentDBYear agentDBYear = agentDB.get(year - BASE_YEAR);
//		List<Agent> agtAL = agentDBYear.getList();
				
		//check to see if the agent already exists by name. If so, don't create a new
		//agent and add to the db, it they do exist return the existing agent
		int index = 0;
		while(index < agentDB.size() && !agentDB.get(index).doesAgentNameMatch(reqAgt.getAgentLastName()))
			index++;
				
		if(index < agentDB.size())
		{
			//found a name match. Determine if other fields have changed. If so, update
			boolean bAgentUpdated = false;
			Agent existingAgent = agentDB.get(index);
			
			if(!reqAgt.getAgentOrg().trim().isEmpty() && !reqAgt.getAgentOrg().equals(existingAgent.getAgentOrg())) 
			{
				existingAgent.setAgentOrg(reqAgt.getAgentOrg().trim());
				bAgentUpdated = true;
			}
			
			if(!reqAgt.getAgentTitle().trim().isEmpty() && !reqAgt.getAgentTitle().equals(existingAgent.getAgentTitle())) 
			{
				existingAgent.setAgentTitle(reqAgt.getAgentTitle().trim()); 
				bAgentUpdated = true;
			}
					
			if(!reqAgt.getAgentEmail().trim().isEmpty() && !reqAgt.getAgentEmail().equals(existingAgent.getAgentEmail()))
			{
				existingAgent.setAgentEmail(reqAgt.getAgentEmail().trim());
				bAgentUpdated = true;
			}
					
			if(!reqAgt.getAgentPhone().trim().isEmpty() && !reqAgt.getAgentPhone().equals(existingAgent.getAgentPhone())) 
			{
				existingAgent.setAgentPhone(reqAgt.getAgentPhone().trim());
				bAgentUpdated = true;
			}
			
			if(bAgentUpdated)
			{
				bSaveRequired = true;
//				agentDBYear.setChanged(true);
				
				//keep the userDB in sync from profile perspective
				try 
				{
					ServerUserDB.getInstance().processAgentUpdate(existingAgent);
				}
				catch (NumberFormatException e) 
				{
					e.printStackTrace();
				}
				catch (IOException e) 
				{
					e.printStackTrace();
				}
				
				Gson gson = new Gson();
				return new ImportONCObjectResponse(AGENT_UPDATED, existingAgent, 
						"UPDATED_AGENT" + gson.toJson(existingAgent, Agent.class));
			}
			else
				return new ImportONCObjectResponse(AGENT_UNCHANGED, existingAgent, "UNCHANGED");
		}
		else
		{
			//agent name match not found, add the id to the requested agent, save and return
			//set the new ID for the catalog wish
			reqAgt.setID(nextID++);
			agentDB.add(reqAgt);
			bSaveRequired = true;
//			agentDBYear.setChanged(true);
			Gson gson = new Gson();
			return new ImportONCObjectResponse(AGENT_ADDED, reqAgt, "ADDED_AGENT" + gson.toJson(reqAgt, Agent.class));	
		}
	}
/*	
	String add(String json)
	{
		//Create an object to add to the data base
		Gson gson = new Gson();
		Agent reqAddAgt = gson.fromJson(json, Agent.class);
		
		//get the agent list for the requested year
//		AgentDBYear agentDBYear = agentDB.get(year - BASE_YEAR);
//		List<Agent> agtAL = agentDBYear.getList();
		
		//check to see if the agent already exists by name. If so, don't create a new
		//agent and add to the db, it they do exist return the existing agent
		int index = 0;
		while(index < agentDB.size() && !agentDB.get(index).doesAgentNameMatch(reqAddAgt.getAgentLastName()))
				index++;
		
		if(index < agentDB.size())
		{
			//found a name match. Update the remaining agent into and return. Check to see that
			//the new data field isn't blank
			Agent existingAgent = agentDB.get(index);
			if(!reqAddAgt.getAgentOrg().trim().isEmpty()) {existingAgent.setAgentOrg(reqAddAgt.getAgentOrg().trim()); }
			if(!reqAddAgt.getAgentTitle().trim().isEmpty()) {existingAgent.setAgentTitle(reqAddAgt.getAgentTitle().trim()); }
			if(!reqAddAgt.getAgentEmail().trim().isEmpty()) {existingAgent.setAgentEmail(reqAddAgt.getAgentEmail().trim()); }
			if(!reqAddAgt.getAgentPhone().trim().isEmpty()) {existingAgent.setAgentPhone(reqAddAgt.getAgentPhone().trim()); }
			bSaveRequired = true;
//			agentDBYear.setChanged(true);
			
			//keep the userDB in sync from profile perspective
			try {
				ServerUserDB.getInstance().processAgentUpdate(existingAgent);
			} catch (NumberFormatException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			return "UPDATED_AGENT" + gson.toJson(existingAgent, Agent.class);
		}
		else
		{
			//agent name match not found, add the id to the requested agent, save and return
			//set the new ID for the catalog wish
			reqAddAgt.setID(nextID++);
			bSaveRequired = true;
			return "ADDED_AGENT" + gson.toJson(reqAddAgt, Agent.class);	
		}
	}
	
	String delete(String json)
	{
		//Create an object for the delete request
		Gson gson = new Gson();
		Agent reqDelObj = gson.fromJson(json, Agent.class);
	
		//find the wish in the catalog
//		AgentDBYear agentDBYear = agentDB.get(year - BASE_YEAR);
//		List<Agent> objAL = agentDBYear.getList();
		int index = 0;
		while(index < agentDB.size() && agentDB.get(index).getID() != reqDelObj.getID())
			index++;
		
		//wish must be present in catalog to be deleted
		if(index <  agentDB.size())
		{
			agentDB.remove(index);
			bSaveRequired = true;
//			agentDBYear.setChanged(true);
			return "DELETED_AGENT" + json;
		}
		else
			return "DELETE_AGENT_FAILED" + json;
	}
	
	Agent getAgent(int agentID)
	{
//		AgentDBYear agentDBYear = agentDB.get(year - BASE_YEAR);
//		List<Agent> objAL = agentDBYear.getList();
		int index = 0;
		while(index < agentDB.size() && agentDB.get(index).getID() != agentID)
			index++;
		
		//wish must be present in catalog to be deleted
		if(index < agentDB.size())
			return agentDB.get(index);
		else
			return null;
	}

	static Agent getAgent(ONCUser user)
	{
//		AgentDBYear agentDBYear = agentDB.get(year - BASE_YEAR);
//		List<Agent> objAL = agentDBYear.getList();
		
		//check each agent for agent id = user.agentID
		int index = 0;
		while(index < agentDB.size() && agentDB.get(index).getID() != user.getAgentID())
			index++;
		
		if(index < agentDB.size())
			return agentDB.get(index);
		else
			return null;
	}
*/	
	/*******
	 * Called to check if a new user is already an agent. If they are, simply return the agent ID.
	 * If they aren't, the method adds them as a new agent, notifies the in-year clients and
	 * returns the new agents ID.
	 * @param year
	 * @param su
	 * @return
	 ****************************************/
/*	
	int checkForAgent(ONCServerUser su)
	{	
		//get access to Client Manager instance
		ClientManager clientMgr = ClientManager.getInstance();
		
		//get the agent list for the requested year
//		AgentDBYear agentDBYear = agentDB.get(year - BASE_YEAR);
//		List<Agent> agtAL = agentDBYear.getList();
		
		//check each agent by agent name == user name, since new users don't have agent ID set
		int index = 0;
		while(index < agentDB.size() && !
				agentDB.get(index).doesAgentNameMatch(su.getFirstname() + " " +  su.getLastname())) 
			index++;
		
		if(index < agentDB.size())	//have a match by name?
		{
			//match found, check for updated agent contact info, make updates as 
			//necessary and return agentID
			Agent agent = agentDB.get(index);
			if(!agent.getAgentOrg().equals(su.getOrg()) || !agent.getAgentTitle().equals(su.getTitle()) ||
					!agent.getAgentEmail().equals(su.getEmail()) || !agent.getAgentPhone().equals(su.getPhone()))
			{	
				agent.setAgentOrg(su.getOrg());
				agent.setAgentTitle(su.getTitle());
				agent.setAgentEmail(su.getEmail());
				agent.setAgentPhone(su.getPhone());
				
				bSaveRequired = true;
//				agentDBYear.setChanged(true);	//mark the database for save
				
				//notify clients of updated agent
				Gson gson = new Gson();
				String updateMssg = "UPDATED_AGENT" + gson.toJson(agent, Agent.class);
				clientMgr.notifyAllClients(updateMssg);
			}
			
			return agent.getID();
		}
		else
		{
			//add new user as an agent, notify in-year clients and return agent ID
			Agent newAgent = new Agent(nextID++, su.getFirstname(), su.getLastname(), su.getOrg(),
					su.getTitle(), su.getEmail(), su.getPhone());
			
			bSaveRequired = true;
			
			//notify clients of added agent
			Gson gson = new Gson();
			String updateMssg = "ADDED_AGENT" + gson.toJson(newAgent, Agent.class);
			clientMgr.notifyAllClients(updateMssg);
			
			return newAgent.getID();
		}
	}
*/	
	/*********
	 * When a user is updated, need to keep the user profile and the agent profile in sync. This
	 * method updates the agent profile
	 * @param agentID
	 */
/*	
	void processUserUpdate(ONCServerUser updatedUser)
	{
		//get access to Client Manager instance
		ClientManager clientMgr = ClientManager.getInstance();
				
		//get the agent list for the requested year
//		AgentDBYear agentDBYear = agentDB.get(year - BASE_YEAR);
//		List<Agent> agtAL = agentDBYear.getList();
				
		//check each agent by agent name == user name, since new users don't have agent ID set
		int index = 0;
		while(index < agentDB.size() && agentDB.get(index).getID() != updatedUser.getAgentID())
			index++;
				
		if(index < agentDB.size())	//agent found? If so, update the profile
		{
			Agent updatedAgent = agentDB.get(index);
			updatedAgent.setAgentLastName(updatedUser.getFirstname() + " " + updatedUser.getLastname());
			updatedAgent.setAgentOrg(updatedUser.getOrg());
			updatedAgent.setAgentTitle(updatedUser.getTitle());
			updatedAgent.setAgentEmail(updatedUser.getEmail());
			updatedAgent.setAgentPhone(updatedUser.getPhone());
			
			//mark the db for save and notify all in year clients of update
			bSaveRequired = true;
//			agentDBYear.setChanged(true);	//mark the database for save
			
			//notify clients of updated agent
			Gson gson = new Gson();
			String updateMssg = "UPDATED_AGENT" + gson.toJson(updatedAgent, Agent.class);
			clientMgr.notifyAllClients(updateMssg);
		}
	}
	
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
	void addObject(String[] nextLine)
	{
//		for(int i=0; i<nextLine.length; i++)
//			System.out.println(String.format("ServerAgentDB.addObject: nextLine[%d] = %s", i, nextLine[i]));
		
		Agent addedAgent = new Agent(Integer.parseInt(nextLine[0]), "", nextLine[1], nextLine[2],
				nextLine[3], nextLine[4], nextLine[5]);
		
		agentDB.add(addedAgent);	
	}

	@Override
	void save()
	{
		String[] header = {"Agent ID", "Name", "Organization", "Title", "Email", "Phone"};
		
//		AgentDBYear agentDBYear = agentDB.get(year - BASE_YEAR);
		if(agentDBYear.isUnsaved())
		{
//			System.out.println(String.format("AgentDB save() - Saving Agent DB"));
			String path = String.format("%s/AgentDB.csv", System.getProperty("user.dir"));
			exportDBToCSV(agentDB,  header, path);
			bSaveRequired = false;
//			agentDBYear.setChanged(false);
		}	
	}
	
	private static class ONCAgentNameComparator implements Comparator<Agent>
	{
		@Override
		public int compare(Agent o1, Agent o2)
		{
			return o1.getAgentLastName().compareTo(o2.getAgentLastName());
		}
	}

	@Override
	String[] getExportHeader() 
	{
		return new String[] {"Agent ID", "Name", "Organization", "Title", "Email", "Phone"};
	}

	@Override
	String getFileName() 
	{
		return AGENT_DB_FILENAME;
	}

	@Override
	List<? extends ONCObject> getONCObjectList() { return agentDB; }
*/	
}
