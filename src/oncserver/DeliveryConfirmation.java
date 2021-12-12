package oncserver;

import ourneighborschild.ONCFamily;

public class DeliveryConfirmation
{
	private boolean bValid;
	private String oncnum;
	private String hohFN;
	private String hohLN;
	private String housenum;
	private String street;
	private String unit;
	private String city;
	private String zipcode;
	private String elemSchool;
	private String region;
	private String primphone;
	private String altphone;
	private String language;
	private String delinstructions;
	private String nBags;
	private String nBikes;
	private String nLargeItems;
	private String message;
	
	DeliveryConfirmation(boolean bValid, ONCFamily f, String elemSchool, String region, String message)
	{
		this.bValid = bValid;
		this.oncnum = f.getONCNum();
		this.hohFN =f.getFirstName();
		this.hohLN = f.getLastName();
		this.housenum = f.getHouseNum();
		this.street = f.getStreet();
		this.unit = f.getUnit();
		this.city = f.getCity();
		this.zipcode = f.getZipCode();
		this.elemSchool = elemSchool;
		this.region = region;
		this.primphone = f.getHomePhone();
		this.altphone = f.getCellPhone();
		this.language = f.getLanguage();
		this.delinstructions = f.getDeliveryInstructions();
		this.nBags = Integer.toString(f.getNumOfBags());
		this.nBikes = Integer.toString(f.getNumOfLargeItems());
		this.nLargeItems = Integer.toString(f.getNumOfLargeItems());
		this.message= message;
	}
	
	DeliveryConfirmation(boolean bValid, String message)
	{
		this.bValid = bValid;
		this.hohFN = "";
		this.hohLN = "";
		this.housenum = "";
		this.street = "";
		this.unit = "";
		this.city = "";
		this.zipcode = "";
		this.elemSchool= "";
		this.region = "";
		this.primphone = "";
		this.altphone = "";
		this.language = "";
		this.delinstructions = "";
		this.nBags = "";
		this.nBikes = "";
		this.nLargeItems = "";
		this.message = message;
	}
	
	//getters
	boolean isValid() { return bValid; }
	String getONCNum() { return this.oncnum; }
	String getHOHFN() { return this.hohFN; }
	String getHOHLN() { return this.hohLN; }
	String getHouseNum() { return housenum; }
	String getStreet() { return street; }
	String getUnit() { return unit; }
	String getCity() { return city; }
	String getZipcode() { return zipcode; }
	String getElemSchool() { return elemSchool; }
	String getRegion() { return region; }
	String getPrimphone() { return primphone; }
	String getAltphone() { return altphone; }
	String getLanguage() { return language; }
	String getDelinstructions() { return delinstructions; }
	String getNumBags() { return nBags; }
	String getNumBikes() { return nBikes; }
	String getNumLargeItems() { return nLargeItems; }
	String getMessage() { return message; }
}
