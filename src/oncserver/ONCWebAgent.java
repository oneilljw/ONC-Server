package oncserver;

import ourneighborschild.ONCServerUser;

public class ONCWebAgent 
{
	/**
	 * This class provides the blueprint for web agent objects in the ONC web site. 
	 */
	private int id;
	private String firstname;
	private String lastname;
	private String name;
	private String org;
	private String title;
	private String email;
	private String phone;

	//Agent from ONCServerUser
	public ONCWebAgent(ONCServerUser su)
	{
		id = su.getID(); 
		firstname = su.getFirstName();
		lastname = su.getLastName();
		name = firstname + " " + lastname;
		org = su.getOrganization();
		title = su.getTitle();
		email = su.getEmail();
		phone = su.getCellPhone();
	}
	
	//getters
	int getID() { return id; }
	String getLastname() { return lastname; }
	String getFirstname() { return firstname; }
	String getName() { return name; }
	String getOrg() { return org; }
	String getTitle() { return title; }
	String getEmail() { return email; }
	String getPhone() { return phone; }
}
