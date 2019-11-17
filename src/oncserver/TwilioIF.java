package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.swing.SwingWorker;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.lookups.v1.PhoneNumber;

import ourneighborschild.ONCSMS;
import ourneighborschild.SMSDirection;
import ourneighborschild.SMSRequest;
import ourneighborschild.SMSStatus;

public class TwilioIF
{
	// Find your Account Sid and Token at twilio.com/console
    // DANGER! This is insecure. See http://twil.io/secure
    private static final String ONC_TWILIO_NUMBER = "+15716654028";
    private static final String SMS_STATUS_CALLBACK = "https://34.224.169.163:8902/sms-update";

    private static TwilioIF instance;
    private ServerSMSDB smsDB;
    
    private TwilioIF() throws FileNotFoundException, IOException
    {	
    		smsDB = ServerSMSDB.getInstance();
    }
    
    public static TwilioIF getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new TwilioIF();
		
		return instance;
	}
    
    /***
     * Encapsulates background SMS worker that validates SMS phone numbers
     * @param smsList
     */
    String validateSMSList(SMSRequest request, List<ONCSMS> smsList)
    {
    		//initialize the background task
    		TwilioSMSValidator validator = new TwilioSMSValidator(request, smsList);
		validator.execute();
		
		return "SMS_REQUEST_INITIATED";
    }
    
    /***
     * Encapsulates background SMS worker
     * @param smsList
     */
    String sendSMSList(SMSRequest request, List<ONCSMS> smsList)
    {
    		//create a deep copy of the request list
    		List<ONCSMS> twilioSMSRequestList = new ArrayList<ONCSMS>();
    		for(ONCSMS smsReq : smsList)
    			twilioSMSRequestList.add(smsReq);
    		
    		//initialize the background task
    		TwilioSMSMessageSender sender = new TwilioSMSMessageSender(request, twilioSMSRequestList);
		sender.execute();
		
		return "SMS_REQUEST_PROCESSED";
    }
    
    void twilioValidationComplete(SMSRequest request, List<ONCSMS> resultList)
    {
    		//notify the SMS DB that the validation request has been processed
    		smsDB.twilioValidationRequestComplete(request, resultList);
    }
    
    void twilioSendRequestComplete(SMSRequest request, List<ONCSMS> resultList)
    {
    		//notify the SMS DB that the request has been processed
//    	smsDB.twilioRequestComplete(request, resultList);
    }
 
    /***************************************************************************************************
     * This class communicates with Twilio thru it's api to send SMS. This executes as a background task
     * since we have to throttle sending of requested messages.
     * ***************************************************************************************************/
     public class TwilioSMSMessageSender extends SwingWorker<Void, Void>
     {
     	private static final int SMS_MESSAGE_RATE = 1000 * 10;		//one message every 10 seconds
     		
     	private SMSRequest request;
     	private List<ONCSMS> smsSendRequestList;
     		
     	TwilioSMSMessageSender(SMSRequest request, List<ONCSMS> requestList)
     	{
     		this.request = request; 
     			
     		Twilio.init(ServerEncryptionManager.getKey("key3"),ServerEncryptionManager.getKey("key4"));
     		this.smsSendRequestList = requestList;
     	}
     		
     	@Override
     	protected Void doInBackground() throws Exception
     	{
     		//verify phone number accessibility -- entity DB is responsible for Twilio formatting
     		int numRequestsProcessed = 0;
     		for(ONCSMS smsRequest : smsSendRequestList)
     		{
     			//send sms
     			sendSMS(smsRequest);
     					
     			if(numRequestsProcessed++ < smsSendRequestList.size()-1)
     				Thread.sleep(SMS_MESSAGE_RATE);
     		}
     						
     		return null;
     	}
     		
     	void sendSMS(ONCSMS requestedSMS) 
     	{	  
     		Message message = Message.creator(
     	                new com.twilio.type.PhoneNumber(requestedSMS.getPhoneNum()),
     	                new com.twilio.type.PhoneNumber(ONC_TWILIO_NUMBER),
     	                requestedSMS.getBody())
     	            .setStatusCallback(URI.create(SMS_STATUS_CALLBACK))
     	            .create();
     	        
     	        if(message.getErrorCode() == null)
     	        {
     	        		if(message.getDirection().toString().equals("outbound-api"))
     	        			requestedSMS.setDirection(SMSDirection.OUTBOUND_API);
     	        		
     	        		requestedSMS.setMessageSID(message.getSid());
     	        		requestedSMS.setStatus(SMSStatus.valueOf(message.getStatus().toString().toUpperCase()));
     	        }
     	        
     	        ServerUI.addDebugMessage(String.format("SMS Mssg Sent %s %s", message.getSid(), message.getTo()));
     	    }
/*     	    
     	   SMSStatus fetchMessage(String messageSID)
     	    {
     	    		Message message = Message.fetcher( messageSID)
     	        .fetch();
     	    		
     	    		com.twilio.rest.api.v2010.account.Message.Status status = message.getStatus();
     	    		
     	    		SMSStatus smsStatus = SMSStatus.valueOf(status.toString());
     	    		return smsStatus;
     		} 
*/     		
     		@Override
     		protected void done()
     		{
     			twilioSendRequestComplete(request, smsSendRequestList);
     		}
     }
     
     /***************************************************************************************************
      * This class communicates with Twilio thru it's api to send SMS. This executes as a background task
      * since we have to throttle sending of requested messages.
      * ***************************************************************************************************/
      public class TwilioSMSValidator extends SwingWorker<Void, Void>
      {
    	  	private SMSRequest request;
    	  	private List<ONCSMS> smsValidationRequestList;
      		
      	TwilioSMSValidator(SMSRequest request, List<ONCSMS> requestList)
      	{
      		Twilio.init(ServerEncryptionManager.getKey("key3"),ServerEncryptionManager.getKey("key4"));
      		this.request = request;
      		this.smsValidationRequestList = requestList;
      	}
      		
      	@Override
      	protected Void doInBackground() throws Exception
      	{
      		//verify phone number accessibility -- entity DB is responsible for Twilio formatting
      		for(ONCSMS smsRequest : smsValidationRequestList)
      		{
      			//verify the phone type. If a mobile phone, set status to VALIDATED, else
      			//set status to ERR_NOT_MOBILE
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
      		return phoneNumber.getCarrier();
      	}
      	
      	@Override
      	protected void done()
      	{
      		twilioValidationComplete(request, smsValidationRequestList);
      	}
    }     
}