package oncserver;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.swing.SwingWorker;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.lookups.v1.PhoneNumber;

import ourneighborschild.EntityType;
import ourneighborschild.ONCSMS;
import ourneighborschild.SMSDirection;

import ourneighborschild.SMSStatus;

public class TwilioIF
{
	// Find your Account Sid and Token at twilio.com/console
    // DANGER! This is insecure. See http://twil.io/secure
    private static final String ONC_TWILIO_NUMBER = "+15716654028";

    private static TwilioIF instance;
    
    private TwilioIF()
    {		
    		ONCSMS testSMS = new ONCSMS(-1, EntityType.FAMILY, -1, "+15713440902", SMSDirection.UNKNOWN, 
    				getSMSMessage(1), SMSStatus.REQUESTED);
//    	sendSMS(testSMS);
    		
 //   	lookupPhoneNumber("+15713440902");
    }
    
    public static TwilioIF getInstance()
	{
		if(instance == null)
			instance = new TwilioIF();
		
		return instance;
	}
    
    /***
     * Encapsulates background SMS worker
     * @param smsList
     */
    private void sendSMSList(List<ONCSMS> smsList)
    {
    		TwilioSMSMessageSender sender = new TwilioSMSMessageSender(smsList);
		sender.execute();
    }
/*    
    public void sendSMS(ONCSMS requestedSMS) 
    {
    		Twilio.init(ServerEncryptionManager.getKey("key3"),ServerEncryptionManager.getKey("key4"));
        Message message = Message.creator(
                new com.twilio.type.PhoneNumber(requestedSMS.getPhoneNum()),
                new com.twilio.type.PhoneNumber(ONC_TWILIO_NUMBER),
                requestedSMS.getBody())
            .create();
        
        if(message.getErrorCode() == null)
        {
        		if(message.getDirection().toString().equals("outbound-api"))
        			requestedSMS.setDirection(SMSDirection.OUTBOUND_API);
        		
        		requestedSMS.setStatus(SMSStatus.valueOf(message.getStatus().toString().toUpperCase()));
        }
        
        System.out.println(message);
        
        System.out.println(String.format("TwilioIF: sendSMS: ONCSMS Status = %s, ONCSMS Direction = %s",
        		requestedSMS.getStatus().toString(), requestedSMS.getDirection().toString()));

        ServerUI.addDebugMessage(String.format("Mssg Sent %s %s", message.getSid(), message.getBody()));
    }
*/    
    
   String getSMSMessage(int mssgID)
   {
	   if(mssgID == 1)
		   return "This is another test of ONC Text Messaging";
	   else 
		   return null;
   }

   /***************************************************************************************************
    * This class communicates with Twilio thru it's api to send SMS. This executes as a background task
    * since we have to throttle sending of requested messages.
    * ***************************************************************************************************/
    public class TwilioSMSMessageSender extends SwingWorker<Void, Void>
    {
    		private static final int SMS_MESSAGE_RATE = 1000 * 5;		//one message every 5 seconds
    		
    		private List<ONCSMS> smsRequestList;
    		
    		TwilioSMSMessageSender(List<ONCSMS> requestList)
    		{
    			Twilio.init(ServerEncryptionManager.getKey("key3"),ServerEncryptionManager.getKey("key4"));
    			this.smsRequestList = requestList;
    		}
    		
    		@Override
    		protected Void doInBackground() throws Exception
    		{
    			//verify phone number accessibility -- entity DB is responsible for Twilio formatting
    			for(ONCSMS smsRequest : smsRequestList)
    			{
    				Map<String,String> lookupMap = lookupPhoneNumber(smsRequest.getPhoneNum());
    				if(lookupMap.containsKey("type") && lookupMap.get("type").equals("mobile"))
    					smsRequest.setStatus(SMSStatus.VALIDATED);
    				else
    					smsRequest.setStatus(SMSStatus.ERR_NOT_MOBILE);	   
    			}
    			
    			return null;
    		}
    		
    		Map<String, String> lookupPhoneNumber(String lookupNumber) 
    		{
    		    PhoneNumber phoneNumber = PhoneNumber.fetcher(new com.twilio.type.PhoneNumber(lookupNumber))
    		           .setType(Arrays.asList("carrier")).fetch();
    		       
    		    Map<String,String> returnedMap = phoneNumber.getCarrier();
    		       
    		    for(String key : returnedMap.keySet())
    		    	   	System.out.println(String.format("Twilio Lookup: key=%s, value=%s", key, returnedMap.get(key)));
    		       
    		    return phoneNumber.getCarrier();
    		}
    		
    		@Override
    		protected void done()
    		{
    		}
    }  
}
