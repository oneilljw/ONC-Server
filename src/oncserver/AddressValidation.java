package oncserver;

public class AddressValidation 
{
	private boolean bAddressValid;
	private String errorMssg;
	
	AddressValidation(boolean bAddressValid, String errorMssg)
	{
		this.bAddressValid = bAddressValid;
		this.errorMssg = errorMssg;
	}
	
	//getters
	boolean isAddressValid() { return bAddressValid; }
	
	//setters
	void setAddressValid(boolean tf) { bAddressValid = tf; }
}
