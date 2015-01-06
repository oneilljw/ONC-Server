package ONCServer;

public class PriorYearPartnerPerformance
{
	private int childID;
	private int wishNumber;
	private int nOrnamentsAssigned;
	private int nOrnamentsReceived;
	
	PriorYearPartnerPerformance(int id, int wn, int nAssigned, int nReceived)
	{
		this.childID = id;
		this.wishNumber = wn;
		this.nOrnamentsAssigned = nAssigned;
		this.nOrnamentsReceived = nReceived;
	}
	
	//getters
	int getPYChildID() { return childID; }
	int getPYPWishNumber() { return wishNumber;}
	int getPYPartnerOrnAssigned() { return nOrnamentsAssigned;}
	int getPYPartnerOrnReceived() { return nOrnamentsReceived;}
	
}
