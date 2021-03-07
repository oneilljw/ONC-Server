package oncserver;

import ourneighborschild.GiftDistribution;
import ourneighborschild.ServerGVs;

public class SeasonParameters
{
	private long thanksgivingMealDeadline;
	private long decemberMealDeadline;
	private long waitlistDeadline;
	private long decemberGiftDeadline;
	private long editDeadline;
	
	@SuppressWarnings("unused")
	private int nGifts;
	
	@SuppressWarnings("unused")
	private int wishIntakeConfiguration;
	
	@SuppressWarnings("unused")
	private GiftDistribution giftDistribution;
	
	@SuppressWarnings("unused")
	private int mealIntake;
	
	
	SeasonParameters(ServerGVs serverGVs, int offset)
	{
		this.thanksgivingMealDeadline = serverGVs.getThanksgivingMealDeadlineMillis() + offset;
		this.decemberMealDeadline = serverGVs.getDecemberMealDeadlineMillis() + offset;
		this.waitlistDeadline = serverGVs.getWaitListGiftDeadlineMillis() + offset;
		this.decemberGiftDeadline = serverGVs.getDecemberGiftDeadlineMillis() + offset;
		this.editDeadline = serverGVs.getFamilyEditDeadlineMillis() + offset;
		this.nGifts = serverGVs.getNumberOfGiftsPerChild();
		this.wishIntakeConfiguration = serverGVs.getChildWishIntakeConfiguraiton();
		this.giftDistribution = serverGVs.getGiftDistribution();
		this.mealIntake = serverGVs.getMealIntake();
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
