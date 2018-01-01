package oncserver;

import ourneighborschild.ONCPartner;

public class ONCWebPartnerExtended extends ONCWebPartner
{
	private String lastName;
	private String firstName;
	private String houseNum;
	private String street;
	private String unit;
	private String city;
	private String zipCode;
	private String homePhone;
	private String other;
	private String deliverTo;
	private String specialNotes;
	private String contact;
	private String contact_email;
	private String contact_phone;
	private String contact2;
	private String contact2_email;
	private String contact2_phone;
	private String pyRequested;
	private String pyAssigned;
	private String pyDelivered;
	private String pyReceivedBeforeDeadline;
	private String pyReceivedAfterDeadline;

	public ONCWebPartnerExtended(ONCPartner p) 
	{
		super(p);
		parseName(p.getLastName());
		this.houseNum = p.getHouseNum();
		this.street = p.getStreet();
		this.unit = p.getUnit();
		this.city = p.getCity();
		this.zipCode = p.getZipCode();
		this.homePhone = p.getHomePhone();
		this.other = p.getOther();
		this.deliverTo= p.getDeliverTo();
		this.specialNotes = p.getSpecialNotes();
		this.contact = p.getContact();
		this.contact_email = p.getContact_email();
		this.contact_phone = p.getContact_phone();
		this.contact2 =  p.getContact2();
		this.contact2_email = p.getContact2_email();
		this.contact2_phone = p.getContact2_phone();
		this.pyRequested = Integer.toString(p.getPriorYearRequested());
		this.pyAssigned = Integer.toString(p.getPriorYearAssigned());
		this.pyDelivered = Integer.toString(p.getPriorYearDelivered());
		this.pyReceivedBeforeDeadline = Integer.toString(p.getPriorYearReceivedBeforeDeadline());
		this.pyReceivedAfterDeadline = Integer.toString(p.getPriorYearReceivedAfterDeadline());
	}
	
	//getters
	String getLastName()	{ return lastName; }
	String getFirstName()	{ return firstName; }
	String getHouseNum()	{ return houseNum; }
	String getStreet()	{return street; }
	String getUnit() { return unit; }
	String getCity()	{return city; }
	String getZipCode()	{ return zipCode; }
	String getHomePhone()	{ return homePhone; }
	String getOther()	{ return other; }
	String getDeliverTo() { return deliverTo; }
	String getSpecialNotes()	{ return specialNotes; }
	String getContact()	{ return contact; }
	String getContact_email()	{ return contact_email; }
	String getContact_phone()	{ return contact_phone; }
	String getContact2()	{ return contact2; }
	String getContact2_email()	{ return contact2_email; }
	String getContact2_phone()	{ return contact2_phone; }
	String getPriorYearRequested() { return pyRequested; }
	String getPriorYearAssigned() { return pyAssigned; }
	String getPriorYearDelivered() { return pyDelivered; }
	String getPriorYearReceivedBeforeDeadline() { return pyReceivedBeforeDeadline; }
	String getPriorYearReceivedAfterDeadline() { return pyReceivedAfterDeadline; }
	
	void parseName(String name)
	{
		String[] parts = name.split(",", 2);
		if(parts.length == 0)
		{
			this.firstName = "";
			this.lastName = "";
		}
		else if(parts.length == 1)
		{
			this.firstName = "";
			this.lastName = name.trim();
		}
		else
		{
			this.firstName = parts[1].trim();
			this.lastName = parts[0].trim();
		}
	}
}
