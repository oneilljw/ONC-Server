package oncserver;

import java.util.ArrayList;
import java.util.List;

import ourneighborschild.VolunteerActivity;

public class ActivityDay 
{
	private int id;
	private String day;
	private List<VolunteerActivity> actList;
	
	public ActivityDay(int id, String day)
	{
		this.id = id;
		this.day = day;
		this.actList = new ArrayList<VolunteerActivity>();
	}
	
	void addActivity(VolunteerActivity va)
	{
		actList.add(va);
	}
}
