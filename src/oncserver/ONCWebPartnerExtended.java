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
	private String orn_req;
	private String orn_assigned;
	private String orn_delivered;
	private String orn_rec_before;
	private String orn_rec_after;
	private String other;
	private String confirmed;
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
		this.orn_req = Integer.toString(p.getNumberOfOrnamentsRequested());
		this.orn_assigned = Integer.toString(p.getNumberOfOrnamentsAssigned());
		this.orn_delivered = Integer.toString(p.getNumberOfOrnamentsDelivered());;
		this.orn_rec_before = Integer.toString(p.getNumberOfOrnamentsReceivedBeforeDeadline());;
		this.orn_rec_after = Integer.toString(p.getNumberOfOrnamentsReceivedAfterDeadline());;
		this.other = p.getOther();
		this.confirmed = p.getConfirmed();
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
