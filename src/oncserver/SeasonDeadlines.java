package oncserver;

import ourneighborschild.ServerGVs;

public class SeasonDeadlines
{
	private long thanksgivingMealDeadline;
	private long decemberMealDeadline;
	private long waitlistDeadline;
	private long decemberGiftDeadline;
	private long editDeadline;
	
	SeasonDeadlines(ServerGVs serverGVs, int offset)
	{
		this.thanksgivingMealDeadline = serverGVs.getThanksgivingMealDeadlineMillis() + offset;
		this.decemberMealDeadline = serverGVs.getDecemberMealDeadlineMillis() + offset;
		this.waitlistDeadline = serverGVs.getWaitListGiftDeadlineMillis() + offset;
		this.decemberGiftDeadline = serverGVs.getDecemberGiftDeadlineMillis() + offset;
		this.editDeadline = serverGVs.getFamilyEditDeadlineMillis() + offset;
	}
	
	SeasonDeadlines(long thanksgivingMealDeadline, long decemberMealDeadline, long waitlistDeadline,
			long decemberGiftDeadline, long editDeadline, int offset)
	{
		this.thanksgivingMealDeadline = thanksgivingMealDeadline + offset;
		this.decemberMealDeadline = decemberMealDeadline + offset;
		this.waitlistDeadline = waitlistDeadline + offset;
		this.decemberGiftDeadline = decemberGiftDeadline + offset;
		this.editDeadline = editDeadline + offset;
	}
	
	//getters
	long getThanskgivingMeal() { return thanksgivingMealDeadline; }
	long getDecemberMeal() { return decemberMealDeadline; }
	long getWaitlist() { return waitlistDeadline; }
	long getDecemberGift() { return decemberGiftDeadline; }
	long getEdit() { return editDeadline; }
	
	//setters
	void setThanksgivingMeal(long tmd) { thanksgivingMealDeadline = tmd; }
	void setDecemberMeal(long dmd) { decemberMealDeadline = dmd; }
	void setWaitlist(long wd) { waitlistDeadline = wd ; }
	void setDecemberGift(long dgd) { decemberMealDeadline = dgd; }
	void setEdit(long ed) { editDeadline = ed; }
}
