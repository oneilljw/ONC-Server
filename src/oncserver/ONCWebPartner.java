package oncserver;

import ourneighborschild.GiftCollection;
import ourneighborschild.ONCPartner;

public class ONCWebPartner 
{
	private int id;
	private String status;
	private String type;
	private GiftCollection collection;
	private String name;
	
	public ONCWebPartner(ONCPartner p)
	{
		this.id=p.getID();
		this.status = getStatusString(p.getStatus());
		this.type = getTypeString(p.getType());
		this.collection = p.getGiftCollectionType();
		this.name = p.getLastName();
	}
	
	//getters
	int getID() { return id; }
	String getStatus() { return status; }
	String getType() { return type; }
	GiftCollection getCollectionType() { return collection; }
	String getName() { return name; }
	
	String getStatusString(int status) 
	{ 
		String[] stati = {"No Action Yet", "1st Email Sent", "Responded", "2nd Email Sent", 
							"Called, Left Mssg", "Confirmed", "Not Participating"};
		
		return stati[status];
	}
	
	String getTypeString(int type)
	{
		String[] types = {"Any","Business","Church","School", "Clothing", "Coat", "ONC Shopper"};
		return types[type];
	}
}
