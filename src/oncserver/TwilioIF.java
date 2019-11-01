package oncserver;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;

public class TwilioIF
{
	// Find your Account Sid and Token at twilio.com/console
    // DANGER! This is insecure. See http://twil.io/secure
    public static final String ACCOUNT_SID = "AC146e471a06cd920fd91862d2965bebf8";
    public static final String AUTH_TOKEN = "9ad1158e7d190c0a55146f6cd6162b89";
    private static final String ONC_TWILIO_NUMBER = "+15716654028";

    private static TwilioIF instance;
    
    private TwilioIF()
    {
    		Twilio.init(ACCOUNT_SID,AUTH_TOKEN);
    }
    
    public static TwilioIF getInstance()
	{
		if(instance == null)
			instance = new TwilioIF();
		
		return instance;
	}
    
    public void sendSMS(String number, String mssg) 
    {
        Message message = Message.creator(
                new com.twilio.type.PhoneNumber(number),
                new com.twilio.type.PhoneNumber(ONC_TWILIO_NUMBER),
                mssg)
            .create();

        ServerUI.addDebugMessage(String.format("Mssg Sent %s %s", 
        		message.getSid(), message.getBody()));
    }
}
