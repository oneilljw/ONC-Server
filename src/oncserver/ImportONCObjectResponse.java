package oncserver;

import ourneighborschild.ONCObject;

/*******
 * Class is used when importing families from Britepaths. Provides for determining whether
 * an agent already exists, exists but info is updated, or the referral was from a new Agent
 * @author johnoneil
 *
 */
public class ImportONCObjectResponse 
{
	private ONCObject obj;
	private String jsonResponse;
	
	ImportONCObjectResponse(ONCObject obj, String jsonResponse)
	{
		this.obj = obj;
		this.jsonResponse = jsonResponse;
	}
	
	//getters
	int getONCObjectID(){ return obj == null ? -1 : obj.getID(); }
	String getJsonResponse() { return jsonResponse; }	
}
