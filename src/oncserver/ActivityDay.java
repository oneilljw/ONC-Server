package oncserver;

import java.text.ParseException;
import java.text.SimpleDateFormat;
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
		
		SimpleDateFormat inputSDF = new SimpleDateFormat("M/d/yy");
		SimpleDateFormat outputSDF = new SimpleDateFormat("EEEE, MMMM d, yyyy");
		
		try 
		{
			this.day = outputSDF.format(inputSDF.parse(day));
		} 
		catch (ParseException e) 
		{
			this.day = "Error: Unable to determine date";
		}
		
		this.actList = new ArrayList<VolunteerActivity>();
	}
	
	//getters
	int getID() { return id; }
	String getDay() { return day; }
	
	void addActivity(VolunteerActivity va)
	{
		actList.add(va);
	}
}
