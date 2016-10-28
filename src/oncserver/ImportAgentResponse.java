package oncserver;

/*******
 * Class is uses when importing families from Britepaths. Provides for determining whether
 * an agent already exists, exists but info is updated, or the referral was from a new Agent
 * @author johnoneil
 *
 */
public class ImportAgentResponse 
{
	private int returnCode;
	private int agentID;
	private String jsonResponse;
	
	ImportAgentResponse(int returnCode, int agentID, String jsonResponse)
	{
		this.returnCode = returnCode;
		this.agentID = agentID;
		this.jsonResponse = jsonResponse;
	}
	
	//getters
	int getReturnCode() { return returnCode; }
	int getAgentID() { return agentID; }
	String getJsonResponse() { return jsonResponse; }	
}
