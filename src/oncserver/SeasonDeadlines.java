package oncserver;

import ourneighborschild.ServerGVs;

public class SeasonDeadlines
{
	private long thanksgivingMealDeadline;
	private long decemberMealDeadline;
	private long waitlistDeadline;
	private long editDeadline;
	
	SeasonDeadlines(ServerGVs serverGVs)
	{
		this.thanksgivingMealDeadline = serverGVs.getThanksgivingMealDeadlineMillis();
		this.decemberMealDeadline = serverGVs.getDecemberMealDeadlineMillis();
		this.waitlistDeadline = serverGVs.getWaitListGiftDeadlineMillis();
		this.editDeadline = serverGVs.getFamilyEditDeadlineMillis();
	}
	
	SeasonDeadlines(long thanksgivingMealDeadline, long decemberMealDeadline, long waitlistDeadline,
			long editDeadline)
	{
		this.thanksgivingMealDeadline = thanksgivingMealDeadline;
		this.decemberMealDeadline = decemberMealDeadline;
		this.waitlistDeadline = waitlistDeadline;
		this.editDeadline = editDeadline;
	}
	
	//getters
	long getThanskgivingMeal() { return thanksgivingMealDeadline; }
	long getDecemberMeal() { return decemberMealDeadline; }
	long getWaitlist() { return waitlistDeadline; }
	long getEdit() { return editDeadline; }
	
	//setters
	void setThanksgivingMeal(long tmd) { thanksgivingMealDeadline = tmd; }
	void setDecemberMeal(long dmd) { decemberMealDeadline =dmd; }
	void setWaitlis(long wd) { waitlistDeadline = wd ; }
	void setEdit(long ed) { editDeadline =ed; }
}
