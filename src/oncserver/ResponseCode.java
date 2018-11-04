package oncserver;

public class ResponseCode
{
	private String message;
	private String famRef;
	private String successMssg;
		
	ResponseCode(String mssg, String famRef)
	{
		this.message = mssg;
		this.famRef = famRef;
		this.successMssg = "";
	}
		
	ResponseCode(String mssg)
	{
		this.message = mssg;
		this.famRef = "NNA";
		this.successMssg = "";
	}
	
	ResponseCode(String mssg, String successMssg, String famRef)
	{
		this.message = mssg;
		this.famRef = famRef;
		this.successMssg = successMssg;
	}
	
	String getMessage() { return message; }
	String getFamRef() { return famRef; }
	String getSuccessMessage() { return successMssg; }
}
