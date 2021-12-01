package oncserver;

public class WebGift
{
	private boolean bValid;
	private int giftNumber;
	private String[] line;
	private String message;
	
	WebGift(boolean bValid, int giftNumber, String line0, String line1, String line2, String line3, String message)
	{
		this.bValid = bValid;
		this.giftNumber = giftNumber;
		this.line = new String[4];
		this.line[0] = line0;
		this.line[1] = line1;
		this.line[2] = line2;
		this.line[3] = line3;
		this.message = message;
	}
	
	WebGift(boolean bValid, String message)
	{
		this.bValid = bValid;
		this.giftNumber = 99;
		this.line = new String[4];
		this.line[0] = "";
		this.line[1] = "";
		this.line[2] = "";
		this.line[3] = "";
		this.message = message;
	}
	
	//getters
	boolean isWebGiftValid() { return bValid; }
	int getGiftNumber() { return giftNumber; }
	String getLine(int ln){ return ln >= 0 && ln < 4 ? line[ln] : "Error: invalid line #"; }
	String getMessage() { return message; }	
	
	//setters
	void setMessage(String message) { this.message = message; }
}
