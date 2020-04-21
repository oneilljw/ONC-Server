package oncserver;

import ourneighborschild.DNSCode;
import ourneighborschild.ONCFamily;
import ourneighborschild.ONCWebsiteFamily;

public class ONCWebsiteFamilyFull extends ONCWebsiteFamily
{
	private String zipcode;
	private String  schoolcode;
	private String	changedBy;
	private boolean bGiftCardOnly;
	private int		slPos;
	private String	HomePhone;
	private String	OtherPhone;
	private String	email;	
	
	public ONCWebsiteFamilyFull(ONCFamily f, DNSCode dnsCode)
	{
		super(f, dnsCode);
		this.zipcode = f.getZipCode();
		this.schoolcode = f.getSchoolCode();
		this.changedBy = f.getChangedBy();		
		this.bGiftCardOnly = f.isGiftCardOnly();
		this.slPos = f.getStoplightPos();
		this.HomePhone = f.getHomePhone();
		this.OtherPhone = f.getCellPhone();
		this.email = f.getEmail();
	}
	
	//getters
	String getZipCode() {return zipcode;}
	String getSchoolCode() {return schoolcode; }
	String getChangedBy() { return changedBy; }
	boolean isGiftCardOnly() { return bGiftCardOnly; }
	int getStoplightPos() { return slPos; }
	String getHomePhone() {return HomePhone;}
	String getOtherPhone() {return OtherPhone;}
	String getFamilyEmail() {return email;}
	
	//setters
	void setZipCode(String zipCode) {this.zipcode = zipCode;}
	void setGiftCardOnly( boolean gco) { this.bGiftCardOnly = gco; }
	void setHomePhone(String homePhone) {HomePhone = homePhone;}
	void setOtherPhone(String otherPhone) {OtherPhone = otherPhone;}
	void setFamilyEmail(String email) {this.email = email; }	
}
