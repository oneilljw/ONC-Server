package oncserver;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
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
		SimpleDateFormat outputSDF = new SimpleDateFormat("EEE MMM d, yyyy");
		
		try 
		{
			this.day = outputSDF.format(inputSDF.parse(day));
		} 
		catch (ParseException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		this.actList = new ArrayList<VolunteerActivity>();
	}
	
	void addActivity(VolunteerActivity va)
	{
		actList.add(va);
	}
}
