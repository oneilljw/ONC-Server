package ONCServer;

import java.util.Calendar;
import java.util.Date;

public class PriorYearPartnerPerformance
{
	private int childID;
	private int wishNumber;
	private int wishAssigneeID;
//	private Calendar wishAssigneeTimeStamp;
	private int wishReceivedID;
//	private Calendar wishReceivedTimeStamp;
	
	PriorYearPartnerPerformance(int id, int wn, int assignedID, int receivedID)
	{
		this.childID = id;
		this.wishNumber = wn;
		this.wishAssigneeID = assignedID;
		
//		this.wishAssigneeTimeStamp = Calendar.getInstance();
//		this.wishAssigneeTimeStamp.setTime(assigneeTS);
		
		this.wishReceivedID = receivedID;
		
//		this.wishReceivedTimeStamp = Calendar.getInstance();
//		this.wishReceivedTimeStamp.setTime(receivedTS);
		
	}
	
	//getters
	int getPYChildID() { return childID; }
	int getPYPWishNumber() { return wishNumber;}
	
	int getPYPartnerWishAssigneeID() { return wishAssigneeID;}
	int getPYPartnerWishReceivedID() { return wishReceivedID;}
//	Calendar getPYPartnerAssigneeTimeStamp() { return wishAssigneeTimeStamp; }
//	Calendar getPYPartnerReceivedTimeStamp() { return wishReceivedTimeStamp; }
}
