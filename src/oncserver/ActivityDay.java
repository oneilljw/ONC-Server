package oncserver;

import java.util.List;

import ourneighborschild.VolunteerActivity;

public class ActivityDay 
{
	private int id;
	private String day;
	private List<VolunteerActivity> actList;
	
	public ActivityDay(int id, String day, List<VolunteerActivity> actList)
	{
		this.id = id;
		this.day = day;
		this.actList = actList;
	}
}
