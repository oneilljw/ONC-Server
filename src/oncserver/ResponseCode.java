package oncserver;

public class ResponseCode
{
	private String message;
	private String famRef;
		
	ResponseCode(String mssg, String famRef)
	{
		this.message = mssg;
		this.famRef = famRef;
	}
		
	ResponseCode(String mssg)
	{
		this.message = mssg;
		this.famRef = "NNA";
	}
	
	String getMessage() { return message; }
	String getFamRef() { return famRef; }
}
