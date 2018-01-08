package oncserver;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;

import javax.mail.AuthenticationFailedException;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.event.TransportEvent;
import javax.mail.event.TransportListener;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.swing.SwingWorker;

import ourneighborschild.EmailAddress;
import ourneighborschild.ONCEmail;
import ourneighborschild.ONCEmailAttachment;
import ourneighborschild.ServerCredentials;

import com.sun.mail.smtp.SMTPTransport;

/***************************************************************************************************
* This class implements an email sender as a background task
* **************************************************************************************************/
public class ServerEmailer extends SwingWorker<Void, Void> implements TransportListener
{
	private static final int MIN_EMAIL_ADDRESS_LENGTH = 2;
//	private static final int EMAIL_SUCCESSFUL_RETURN_CODE = 250;
   
	private EmailAddress fromAddress;	//from address line for the email
	private ServerCredentials credentials; //host URL, account id, password
	private ArrayList<EmailAddress> bccList;	//bcc addresses for the email
	private ArrayList<ONCEmail> emailAL;	//list of email messages, to addressees
	private ArrayList<ONCEmailAttachment> attachmentAL; //list of file names for email attachments
	private int count;
	
	ServerEmailer(EmailAddress fromAdd, ArrayList<EmailAddress> bccList, ArrayList<ONCEmail> emailAL,
			ArrayList<ONCEmailAttachment> attFileAL, ServerCredentials creds)
	{		
		fromAddress = fromAdd;
		this.bccList = bccList;
		this.emailAL = emailAL;
		attachmentAL = attFileAL;
		credentials = creds;
		count = 0;
	}
	
	/**********************************************************************************************
	 * This method is called to send email's. The bodies and addresses are contained in ONCEmail objects
	 * held in the email array list. The email is sent from the address held in the fromAdd string 
	 * array. Element[0] contains the email address and element[1] contains the senders name.
	 * The email subject is held in subject, the blind carbon copy addresses are held in string arrays
	 * in the bccAddress array list. Attachments are defined by ONCEmailAttachment objects and consist
	 * of an attachment filename, content ID, and disposition. Attachments, if any, are stored in the 
	 * attachmentAL array list. Attachment files must be located in the application launch directory. 
	 * The class establishes a JavaMail Session and establishes a transport. It then loops on the partners
	 * selected in the table, creating a MIMEMessage for each one selected using the partner's organization
	 * data and sends the message. The createMimeMessage method does the creation of the MIMEMessage.
	 * @throws MessagingException
	 **********************************************************************************************/
	void createAndSendEmail() throws MessagingException
	{
		//Set system properties to create session
		Properties props = System.getProperties();
        props.put("mail.smtps.host", credentials.getServerName());
        props.put("mail.smtps.auth","true");
        
        //Create the session and transport
        Session session = Session.getInstance(props, null);
        SMTPTransport t = (SMTPTransport)session.getTransport("smtps");
        t.addTransportListener(this);

        try 
        {
        		t.connect(credentials.getServerName(), credentials.getUserID(), credentials.getPassword());
        }
        catch (AuthenticationFailedException aex) 
        {
//        	String errMsg = String.format("Mail Server Login Failed: server = %s, usderID = %s, pw = %s", 
//        			credentials.getServerName(), credentials.getUserID(), credentials.getPassword());
        }
		
        //for each email in the email list, create a MimeMessage and send the email
        MimeMessage msg = null;
        this.setProgress(0);	//Initialize progress bound, incremented by transport listeners
		for(ONCEmail email:emailAL)
		{
			try {	//Create the message and send it
				msg = createMimeMessage(session, email);
			} catch (MessagingException mex) {
//				System.out.println("send failed, message exception: " + mex);
				mex.printStackTrace();
				msg = null;
			} catch (IOException ioex) {
				// TODO Auto-generated catch block
//				System.out.println("send failed, io exception: " + ioex);
				ioex.printStackTrace();
				msg = null;
			}
			
			if(msg != null)
			{
				t.sendMessage(msg, msg.getAllRecipients());
//				System.out.println("Response: " + t.getLastServerResponse());
			}
		}
	}
	
	/***************************************************************************************************
	 * This method creates and returns a MIMEMessage. The ONCEmail object parameter holds the body of the 
	 * email and a list of EmailAdress objects for each recipients. Each EmailAddress object contains the
	 * recipients email address and the recipients name. 
	 ***************************************************************************************************/
	MimeMessage createMimeMessage(Session session, ONCEmail email) throws MessagingException, IOException
	{
		MimeMessage msg = new MimeMessage(session);
        
        MimeMultipart content = new MimeMultipart("related");
        
        //If there are attachments, create the attachments, 
        //attachments must be in the application launch folder
        ArrayList<MimeBodyPart> imagePart = new ArrayList<MimeBodyPart>();
    
        for(int i=0; i<attachmentAL.size(); i++)
        {
        	imagePart.add(new MimeBodyPart()); 
        	imagePart.get(i).attachFile(System.getProperty("user.dir") + "/" + attachmentAL.get(i).getFilename());   
        	imagePart.get(i).setContentID("<" + attachmentAL.get(i).getCID() + ">");
        	imagePart.get(i).setDisposition(attachmentAL.get(i).getDisposition());
        }
        
        //Create the email body
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(email.getEmailBody(), "US-ASCII", "html");
       
        //add the parts
        content.addBodyPart(textPart);
        for(MimeBodyPart ip:imagePart)
        	content.addBodyPart(ip);
        
        //Combine the parts into a whole
        msg.setContent(content);
        
        //Set the Message From field
        msg.setFrom(new InternetAddress(fromAddress.getEmailAddress(), fromAddress.getName()));
         
        //Create To: recipients
        if(email.getToAddressees().size() > 0)
        {
        	msg.setRecipient(Message.RecipientType.TO, new InternetAddress(email.getToAddressees().get(0).getEmailAddress(),
																			email.getToAddressees().get(0).getName()));
        	
        	for(int i=1; i<email.getToAddressees().size(); i++)
        		msg.addRecipient(Message.RecipientType.TO, new InternetAddress(email.getToAddressees().get(i).getEmailAddress(), 
        																		email.getToAddressees().get(i).getName()));
        }
        	
        //Add blind carbon copy.
        for(EmailAddress bcc: bccList)
        	msg.addRecipient(Message.RecipientType.BCC, new InternetAddress(bcc.getEmailAddress(), bcc.getName()));
          
        //Set the message subject  
        msg.setSubject(email.getEmailSubject());
        
        //Set the message header and sent date
        msg.setHeader("X-Mailer", "Our Neighbor's Child");
        msg.setSentDate(new Date());
        
      //Ensure first email address is a valid address, else return null
        if(email.getToAddressees().get(0).getEmailAddress().length() > MIN_EMAIL_ADDRESS_LENGTH)	
        	return msg;
        else 
        	return null;
	}
	
    @Override
    public Void doInBackground()
    {
        try
        {
			createAndSendEmail();
		} 
        catch (MessagingException e) 
        {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        return null;
    }
   
    @Override
	public void messageDelivered(TransportEvent te) {
		this.setProgress((count++*100)/emailAL.size());
	}

	@Override
	public void messageNotDelivered(TransportEvent te) {
		this.setProgress((count++*100)/emailAL.size());	
	}

	@Override
	public void messagePartiallyDelivered(TransportEvent te) {
		this.setProgress((count++*100)/emailAL.size());
	}
	
	 /*
     * Executed in event dispatching thread
     */
    @Override
    public void done()
    {
    	
    }	
}
