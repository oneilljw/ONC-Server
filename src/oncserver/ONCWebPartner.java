package oncserver;

import ourneighborschild.ONCPartner;

public class ONCWebPartner
{
	private int id;
	private String name;
	
	public ONCWebPartner(ONCPartner p)
	{
		this.id=p.getID();
		this.name = p.getLastName();
	}
	
	//getters
	int getID() { return id; }
	String getName() { return name; }
}
