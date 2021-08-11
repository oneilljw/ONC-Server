package oncserver;

import java.util.List;

import ourneighborschild.ONCGroup;
import ourneighborschild.ONCServerUser;

public class WebUser
{
	@SuppressWarnings("unused")
	private String firstname;
	@SuppressWarnings("unused")
	private String lastname;
	@SuppressWarnings("unused")
	private String title;
	@SuppressWarnings("unused")
	private String org;
	@SuppressWarnings("unused")
	private String email;
	@SuppressWarnings("unused")
	private String workphone;
	@SuppressWarnings("unused")
	private String cellphone;
	@SuppressWarnings("unused")
	private String permission;
	@SuppressWarnings("unused")
	private List<ONCGroup> groups;
	
	WebUser(ONCServerUser u, List<ONCGroup> groupList)
	{
		this.firstname = u.getFirstName(); 
		this.lastname = u.getLastName();
		this.title = u.getTitle();
		this.org = u.getOrganization();
		this.email = u.getEmail();
		this.workphone = u.getHomePhone();
		this.cellphone = u.getCellPhone();
		this.permission = u.getPermission().toString();
		this.groups = groupList;
	}	
}
