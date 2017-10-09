package oncserver;

import ourneighborschild.GiftCollection;
import ourneighborschild.ONCPartner;

public class ONCWebPartner 
{
	private int id;
	private String status;
	private String type;
	private GiftCollection collection;
	private String lastName;
	
	private String[] stati = {"No Action Yet", "1st Email Sent", "Responded", "2nd Email Sent", "Called, Left Mssg",
			   "Confirmed", "Not Participating"};

	private String[] types = {"Any","Business","Church","School", "Clothing", "Coat", "ONC Shopper"};
	
	public ONCWebPartner(ONCPartner p)
	{
		this.id=p.getID();
		this.status = stati[p.getStatus()];
		this.type = types[p.getType()];
		this.collection = p.getGiftCollectionType();
		this.lastName = p.getLastName();
	}
	
	//getters
	int getID() { return id; }
	String getStatus() { return status; }
	String getType() { return type; }
	GiftCollection getCollectionType() { return collection; }
	String getLastName() { return lastName; }
}
