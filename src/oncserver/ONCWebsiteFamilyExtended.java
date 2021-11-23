package oncserver;

import ourneighborschild.ONCWebsiteFamily;
import ourneighborschild.ONCAdult;
import ourneighborschild.ONCFamily;
import ourneighborschild.ONCMeal;
import ourneighborschild.ONCWebChild;
import ourneighborschild.Transportation;
import ourneighborschild.DNSCode;
import ourneighborschild.DistributionCenter;
import ourneighborschild.FamilyHistory;
import ourneighborschild.GiftDistribution;

import java.util.List;

	public class ONCWebsiteFamilyExtended extends ONCWebsiteFamily 
	{
		private String	BatchNum;
		private String	Language;
		private String	HouseNum;
		private String	Street;
		private String	UnitNum;
		private String	City;
		private String	ZipCode;
		private String 	Region;
		private String	substituteDeliveryAddress;	//in Google Map Address format		
		private String	HomePhone;
		private String	OtherPhone;
		private String  altPhone2;
		private int		phonecode;
		private String	FamilyEmail;
		private String	details;
		private String  transportation;
//		private int		groupID;
		private String	groupName;
//		private int		mealID;
		private String 	notes;
		private String  delInstr;
		private boolean bGiftCardOnly;
		private List<ONCWebChild> childList;
		private List<ONCAdult> adultList;
		private ONCWebAgent agent;
		private ONCMeal meal;
		private GiftDistribution distribution;
		
		public ONCWebsiteFamilyExtended(ONCFamily f, FamilyHistory fh, String region, String groupname, List<ONCWebChild> childList, 
										List<ONCAdult> adultList, ONCWebAgent agent, ONCMeal meal, DNSCode dnsCode, DistributionCenter center)
		{
			super(f, fh, dnsCode, meal, center);
			this.BatchNum = f.getBatchNum();
			this.Language = f.getLanguage();
			this.HouseNum = f.getHouseNum();
			this.Street = f.getStreet();
			this.UnitNum = f.getUnit();
			this.City = f.getCity();
			this.ZipCode = f.getZipCode();
			this.Region = region;
			this.substituteDeliveryAddress = f.getSubstituteDeliveryAddress();	//in Google Map Address format		
			this.HomePhone = f.getHomePhone();
			this.OtherPhone = f.getCellPhone();
			this.altPhone2 = f.getAlt2Phone();
			this.phonecode = f.getPhoneCode();
			this.FamilyEmail = f.getEmail();
			this.details = f.getDetails();
			this.transportation = f.getTransportation().toString();
//			this.groupID = f.getGroupID();
			this.groupName = groupname;
//			this.mealID = f.getMealID();
			this.notes = f.getNotes();
			this.delInstr = f.getDeliveryInstructions();
			this.bGiftCardOnly = f.isGiftCardOnly();
			this.childList = childList;
			this.adultList = adultList;
			this.agent = agent;
			this.meal = meal;
			this.distribution = f.getGiftDistribution();
		}
		
		//getters
		String getBatchNum() {return BatchNum; }
		String getLanguage() {return Language;}
		String getHouseNum() {return HouseNum;}
		String getStreet() {return Street;}
		String getUnitNum() {return UnitNum;}
		String getCity() {return City;}
		String getZipCode() {return ZipCode;}
		String getRegion() { return Region; }
		String getSubstituteDeliveryAddress() {return substituteDeliveryAddress;}
		String getHomePhone() {return HomePhone;}
		String getOtherPhone() {return OtherPhone;}
		String getAlt2Phone() { return altPhone2; }
		int getPhoneCode() { return phonecode; }
		String getFamilyEmail() {return FamilyEmail;}
		String getDetails() {return details;}
		String getTransportation() { return transportation; }
//		int getGroupID() {return groupID;}
		String getGroupName() { return groupName; }
//		int getMealID() {return mealID;}
		String getNotes() { return notes; }
		String getDeliveryInstructions() { return delInstr; }
		boolean isGiftCardOnly() { return bGiftCardOnly; }
		List<ONCWebChild> getChildList() { return childList; }
		List<ONCAdult> getAdultList() { return adultList; }
		ONCWebAgent getAgent() { return agent; }
		ONCMeal getMeal() { return meal; }
		GiftDistribution getGiftDistribution() { return distribution; }

		//setters
		void setBatchNum(String bn) {BatchNum = bn;}
		void setLanguage(String language) {Language = language;}
		void setHouseNum(String houseNum) {HouseNum = houseNum;}
		void setStreet(String street) {Street = street;}
		void setUnitNum(String unitNum) {UnitNum = unitNum;}
		void setCity(String city) {City = city;}
		void setZipCode(String zipCode) {ZipCode = zipCode;}
		public void setRegion(String reg) { Region = reg; }
		void setSubstituteDeliveryAddress(String substituteDeliveryAddress) {this.substituteDeliveryAddress = substituteDeliveryAddress;}
		void setHomePhone(String homePhone) {HomePhone = homePhone;}
		void setOtherPhone(String otherPhone) {OtherPhone = otherPhone;}
		void setAltPhone2(String number) { this.altPhone2 = number; } 
		void setPhoneCode(int code) { this.phonecode = code; }
		void setFamilyEmail(String familyEmail) {FamilyEmail = familyEmail;}
		void setDetails(String details) {this.details = details;}
		void setTransportation(Transportation t) {this.transportation = t.toString(); }
//		void setGroupID(int groupID) {this.groupID = groupID;}
		void setGroupName( String name) { this.groupName = name; }
//		void setMealID(int mealID) {this.mealID = mealID;}
		void setNotes( String notes) { this.notes = notes; }
		void setDeliveryInstructions( String di) { this.delInstr = di; }
		void setGiftCardOnly( boolean gco) { this.bGiftCardOnly = gco; }
		void setGiftDistribution( GiftDistribution dist) { this.distribution = dist; }
}
