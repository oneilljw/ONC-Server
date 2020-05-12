package oncserver;

public class ResponseCode
{
	private String message;	//header message
	private String famRef;
	private String successMssg;	//message for pop-up
	private String title;	//title for pop-up
	private boolean bSuccess;
	
	ResponseCode(String mssg)
	{
		this.message = mssg;
		this.famRef = "NNA";
		this.successMssg = "";
		this.title = "";
		this.bSuccess = false;
	}
	
	ResponseCode(String mssg, boolean bSuccess)
	{
		this.message = mssg;
		this.famRef = "NNA";
		this.successMssg = "";
		this.title = "";
		this.bSuccess = bSuccess;
	}
	
	ResponseCode(String mssg, String famRef)
	{
		this.message = mssg;
		this.famRef = famRef;
		this.successMssg = "";
		this.title = "";
		this.bSuccess = false;
	}
	
	ResponseCode(String successMssg, String title, boolean bSuccess)
	{
		this.message = "";
		this.famRef = "";
		this.successMssg = successMssg;
		this.title = title;
		this.bSuccess = bSuccess;
	}
	
	ResponseCode(String mssg, String successMssg, String famRef)
	{
		this.message = mssg;
		this.famRef = famRef;
		this.successMssg = successMssg;
		this.title = "";
		this.bSuccess = false;
	}
		
	ResponseCode(String mssg, String successMssg, String title, String famRef)
	{
		this.message = mssg;
		this.famRef = famRef;
		this.successMssg = successMssg;
		this.title = title;
		this.bSuccess = false;
	}
	
	String getMessage() { return message; }
	String getFamRef() { return famRef; }
	String getSuccessMessage() { return successMssg; }
	String getTitle() { return title; }
	boolean transactionSuccessful() { return bSuccess; }
}
