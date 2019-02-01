package oncserver;

import ourneighborschild.GiftCollectionType;
import ourneighborschild.ONCPartner;

public class ONCWebPartnerExtended extends ONCWebPartner
{
	private String status;
	private String type;
	private GiftCollectionType collection;
	private String orn_req;
	private String orn_assigned;
	private String orn_delivered;
	private String orn_received;
	private String orn_rec_before;
	private String orn_rec_after;
	
	public ONCWebPartnerExtended(ONCPartner p)
	{
		super(p);
		this.status = getStatusString(p.getStatus());
		this.type = getTypeString(p.getType());
		this.collection = p.getGiftCollectionType();
		this.orn_req = Integer.toString(p.getNumberOfOrnamentsRequested());
		this.orn_assigned = Integer.toString(p.getNumberOfOrnamentsAssigned());
		this.orn_delivered = Integer.toString(p.getNumberOfOrnamentsDelivered());
		this.orn_received = Integer.toString(p.getNumberOfOrnamentsReceivedBeforeDeadline() + p.getNumberOfOrnamentsReceivedAfterDeadline());
		this.orn_rec_before = Integer.toString(p.getNumberOfOrnamentsReceivedBeforeDeadline());
		this.orn_rec_after = Integer.toString(p.getNumberOfOrnamentsReceivedAfterDeadline());
	}
	
	//getters
	String getStatus() { return status; }
	String getType() { return type; }
	GiftCollectionType getCollectionType() { return collection; }
	String getNumberOfOrnamentsRequested()	{ return orn_req; }
	String getNumberOfOrnamentsAssigned() { return orn_assigned; }
	String getNumberOfOrnamentsDelivered() { return orn_delivered; }
	String getNumberOfOrnamentsReceived() { return orn_received; }
	String getNumberOfOrnamentsReceivedBeforeDeadline() { return orn_rec_before; }
	String getNumberOfOrnamentsReceivedAfterDeadline() { return orn_rec_after; }
	
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
	
	static int getStatus(String zStatus)
	{
		String[] stati = {"No Action Yet", "1st Email Sent", "Responded", "2nd Email Sent", 
				"Called, Left Mssg", "Confirmed", "Not Participating"};
		
		int index = 0;
		while(index < stati.length && !stati[index].equals(zStatus))
			index++;
			
		if(index < stati.length)
			return index;
		else
			return 0;	
	}
	
	static int getType(String zType)
	{
		String[] types = {"Any","Business","Church","School", "Clothing", "Coat", "ONC Shopper"};
		
		int index = 0;
		while(index < types.length && !types[index].equals(zType))
			index++;
			
		if(index < types.length)
			return index;
		else
			return 0;
	}
}
