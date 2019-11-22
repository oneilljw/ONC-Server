package oncserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.google.gson.Gson;

import au.com.bytecode.opencsv.CSVWriter;
import ourneighborschild.GiftStatus;
import ourneighborschild.ONCChild;
import ourneighborschild.ONCChildGift;
import ourneighborschild.ONCFamily;
import ourneighborschild.ONCPartner;
import ourneighborschild.SignUp;
import ourneighborschild.SignUpActivity;
import ourneighborschild.SignUpType;

public class SignUpGeniusClothingImporter extends SignUpGeniusImporter
{
	private static SignUpGeniusClothingImporter instance;

	public static SignUpGeniusClothingImporter getInstance()
	{
		if(instance == null)
			instance = new SignUpGeniusClothingImporter();
		
		return instance;
	}

	void requestSignUpContent(SignUp signup, SignUpType signUpType, SignUpReportType reportType)
	{
		SignUpGeniusClothingImportWorker importer = new SignUpGeniusClothingImportWorker(signup, signUpType, reportType);
		importer.execute();
	}
	
	private void signUpContentReceived(SignUpEventType type, Object object)
	{
		fireSignUpDataChanged(this, type, object);
	}
	
	private class SignUpGeniusClothingImportWorker extends SignUpGeniusImportWorker
	{
		SignUp signup;
		SignUpType signUpType;
		SignUpReport signUpReport;
		SignUpReportType reportType;
		List<SignUpActivity> signUpClothingList;
		List<ClothingPartner> clothingPartners; 
		
		//component databases we'll pull info from
		ServerFamilyDB familyDB;
		ServerChildDB childDB;
		ServerChildGiftDB childGiftDB;
		ServerPartnerDB partnerDB;
		
		//singleton constructor for importing volunteers and activities from a specific sign-up in ONC account
		SignUpGeniusClothingImportWorker(SignUp signup, SignUpType signUpType, SignUpReportType reportType)
		{
			this.signup = signup;
			this.signUpType = signUpType;
			this.reportType = reportType;
		
			//initialize the list of ClothingPartners
			clothingPartners = new ArrayList<ClothingPartner>();
			
			//get references to component data bases
			try
			{
				familyDB = ServerFamilyDB.getInstance();
				childDB = ServerChildDB.getInstance();
				childGiftDB = ServerChildGiftDB.getInstance();
				partnerDB = ServerPartnerDB.getInstance();
			}
			catch (FileNotFoundException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		@Override
		protected Void doInBackground() throws Exception
		{			
			String response = getSignUpJson(String.format(SIGNUP_REPORT_URL, reportType.toString(), signup.getSignupid(), apiKey()));
//			String response = simulateGeniusReportFetch("SignUpGeniusClothingJsonGunnTest.txt");
			if(response != null && !response.isEmpty())
			{
				//save the imported json
				saveJsonToFile(response, "SignUpGeniusClothingJsonImport.txt");
				
				//get the report that was imported
				Gson gson = new Gson();
				signUpReport = gson.fromJson(response.toString(), SignUpReport.class); //create the report from the json
				signUpClothingList = signUpReport.getContent().getSignUpActivities(); //get the list of signUpActivities
				
				//not all sign up clothing items have been adopted. Create a list of just the signUps that
				//have been filled. Make sure they have an end date.
				List<SignUpActivity> signUpFilledItemsList = new ArrayList<SignUpActivity>();
				for(SignUpActivity sua : signUpClothingList)
				{
					if(!sua.getFirstname().isEmpty() && !sua.getLastname().isEmpty() && !sua.getEmail().isEmpty())
        				{
						if(sua.getEnddate() == 0)
							sua.setEnddate(getChristmasDayTimeInSeconds());	//milliseconds to seconds
						
						signUpFilledItemsList.add(sua);
        				}
				}
				
				ServerUI.addDebugMessage(String.format("Clothing SignUp Imported: %d Gifts Adopted, %d Gifts Unclaimed", 
						signUpFilledItemsList.size(), signUpClothingList.size()-signUpFilledItemsList.size()));
				
				//order the filled SignUp Activity list by email. We use this ordering to iterate thru the
				//list to identity unique partners. Many partners adopt more then one clothing or coat gift
				Collections.sort(signUpFilledItemsList, new SUAEmailComparator());
				
				//Iterate thru the list of filled signUps. Start with the first signUp, create a
				//ClothingPartner object with either a new ONC Partner or an existing ONCPartner found in
				//the partner database. Add the first signUps gift to the ClothingPartner object.
				//Continue to iterate. Check to see if the next signUp has the same partner as the last.
				//If so, just add the signUp's gift to the existing ClothingPartner's gift list, otherwise,
				//create a new ClothingPartner and add the signUp's gift to the new ClothingPartner's list.
				//Rinse and repeat until we've iterated thru the list of signUp's.
				int year = DBManager.getCurrentSeason();
				String lastPartnerEmail = "";
				ClothingPartner cp = null;
				
				for(SignUpActivity sua : signUpFilledItemsList)
				{
					ONCPartner suaPartner = new ONCPartner(sua, signUpType);
					
					if(lastPartnerEmail.isEmpty() || !lastPartnerEmail.equals(suaPartner.getContact_email()))
					{
						//we found another partner in the SUA list, we need to create a new clothing partner
						//and add the gift to the new clothing partners list.
						ONCPartner existingPartner = partnerDB.getPartnerFromSignUpActivity(year, suaPartner);						
    				
    						if(existingPartner != null)
    							clothingPartners.add((cp = new ClothingPartner(existingPartner)));
    						else
    							clothingPartners.add((cp = new ClothingPartner(suaPartner)));
    							
    						lastPartnerEmail = cp.getPartner().getContact_email(); 
					}
					
					if(cp != null)
					{
						ONCChildGift childGift = getChildGift(sua.getItem());
						if(childGift != null)
							cp.addChildGift(childGift);
					}
				}			
			}
				
			return null;
		}
		
		//Background clothing/coat importer has completed, process the Clothing Partners the background importer
		//found in the import. Multiple imports of the same signUp will contain overlapped SignUpActivities, since
		//SignUp Genius dumps the full SignUp report each time the report is requested.
		protected void done()
	    {
			ServerUI.addDebugMessage("Clothing SignUp Background Importer Complete");
        		Gson gson = new Gson();
        		List<String> clientResponseList = new ArrayList<String>();
			int year = DBManager.getCurrentSeason();	//assume it's for the current season
				
			//iterate thru the clothing partners found in the signUp. Separate into new or existing partners.
			//For new partners, add the partner to the data base. Then iterate thru the gift adoptions for 
			//each partner, making the new gift assignments, and updating the partner ornament counts. Set
			//the partner status to CONFIRMED and set GiftStatus for all gift adoptions to DELIVERED.
			for(ClothingPartner cp : clothingPartners)
			{
				if(cp.getPartner().getID() == -1)	//an existing partner will have a seven digit ID
				{
					//this is a new partner. ONCPartner constructor set status to CONFIRMED
					cp.getPartner().setNumberOfOrnamentsRequested(cp.getGiftList().size());
					cp.getPartner().setNumberOfOrnamentsAssigned(cp.getGiftList().size());
					cp.getPartner().setNumberOfOrnamentsDelivered(cp.getGiftList().size());
					ONCPartner addedPartner = partnerDB.add(year, cp.getPartner());
						
					clientResponseList.add("ADDED_PARTNER"+gson.toJson(addedPartner, ONCPartner.class));
						
					//now that we added the new partner, add the gifts they chose to adopt. However, we
					//have to deal with the decrementing the old partner, if any, gift assignment
					for(ONCChildGift cg : cp.getGiftList())
					{
						//decrement the current partner's assigned gift count and add the response to the clientResponseJson
						if(cg.getPartnerID() > -1)				
							clientResponseList.add(partnerDB.decrementGiftsAssignedCount(year, cg.getPartnerID(), false));
							
						//set the added gift partner id and set the status to DELIVERED
						cg.setChildWishAssigneeID(cp.getPartner().getID());
						cg.setChildWishStatus(GiftStatus.Delivered);	
					}
						
					//add the gift to the child gift data base, no client notification
					List<String> addedGiftsResponseList = childGiftDB.addListOfSignUpGeniusImportedGifts(year, cp.getGiftList());
					for(String responseJson : addedGiftsResponseList)
						clientResponseList.add(responseJson);	
				}
				else
				{
					//this is an existing partner who is adopting gifts thru signUP genius. This may be
					//a subsequent retrieval of the signUp so handle accordingly.
					List<ONCChildGift> newGeniusClothingAdoptionsList = new ArrayList<ONCChildGift>();
					for(ONCChildGift cg : cp.getGiftList())
					{
						//determine if the gift has already been assigned to the partner. If not, 
						//decrement the old partner assigned count, increment the new partner and add the child gift
						if(cg.getPartnerID() != cp.getPartner().getID())
						{
							//decrement the current partner's assigned gift count and add the response to the clientResponseJson
							if(cg.getPartnerID() > -1)				
								clientResponseList.add(partnerDB.decrementGiftsAssignedCount(year, cg.getPartnerID(), false));
								
							//set the added gift partner id and set the status to DELIVERED. Add the gift
							//to the list we'll send to the ChildGift DB
							cg.setChildWishAssigneeID(cp.getPartner().getID());
							cg.setChildWishStatus(GiftStatus.Delivered);
							newGeniusClothingAdoptionsList.add(cg);
								
							//update the new partner's requested, assigned and delivered counts
							cp.getPartner().incrementOrnRequested();
							cp.getPartner().incrementOrnAssigned();
							cp.getPartner().incrementOrnDelivered();
						}
					}
						
					if(!newGeniusClothingAdoptionsList.isEmpty())
					{
						//add the gift to the child gift data base, no client notification
						List<String> addedGiftsResponseList = childGiftDB.addListOfSignUpGeniusImportedGifts(year, newGeniusClothingAdoptionsList);
						for(String responseJson : addedGiftsResponseList)
							clientResponseList.add(responseJson);
						
						//change partner status CONFIRMED and update the existing partner in the Partner DB
						cp.getPartner().setStatus(ONCPartner.PARTNER_STATUS_CONFIRMED);
						ONCPartner updatedPartner = partnerDB.update(year, cp.getPartner());
						
						clientResponseList.add("UPDATED_PARTNER"+gson.toJson(updatedPartner, ONCPartner.class));
					}
				}
			}
			
			//save the imported file
			saveClothingSignUp("SignUpGeniusClothingImport.csv");
				
			//thread complete, update sign up import time and notify clients of the import
			signup.setLastImportTimeInMillis(System.currentTimeMillis());
	        	signUpContentReceived(SignUpEventType.REPORT, signup);
	        	
			//if the clientResponseJson isn't empty, notify all in-year clients
			if(!clientResponseList.isEmpty())
			{
				ClientManager clientMgr = ClientManager.getInstance();
				clientMgr.notifyAllInYearClients(year, clientResponseList);
			}
			
			ServerUI.addDebugMessage("Clothing SignUp Importer EDT Procssing Complete");
        	}
		
		void saveClothingSignUp(String filename)
		{
			String path = String.format("%s/%dDB/%s", System.getProperty("user.dir"),DBManager.getCurrentSeason(), filename);
			File oncwritefile = new File(path);
			
			String[] header = {"First Name", "Last Name" ,"Email", "Phone", "Phone Type", 
					"Address1", "Address2", "ZipCode", "Qty", "Clothing Adopted", "Comment"};
			
			 if(signUpType == SignUpType.Coat)
				 header[9] = "Coat Adopted";
			
			try 
			{
				CSVWriter writer = new CSVWriter(new FileWriter(oncwritefile.getAbsoluteFile()));
				writer.writeNext(header);
		    	
				for(SignUpActivity sua : signUpClothingList)
					writer.writeNext(sua.getExportRow());	//SignUps
					
				writer.close();	
			} 
			catch (IOException x)
			{
				System.err.format("IO Exception: %s%n", x.getMessage());
			}
		}
		
		/****
		 * Method takes a string for the item provided in a sign-up with a specific format and returns the
		 * ONCChildGift object from the ChildGift data base. 
		 * Item format must be "ONC#: dddd ** child.getChildAge() + " " + child.getChildGender() ** gift detail
		 * @param item: String of json item value. See format above
		 * @return ONCChildGift corresponding to item or null if ONCChildGift can't be located in data base
		 */
    		ONCChildGift getChildGift(String item)
    		{
    			//break the imported item into three strings at the astrisk
    			String[] itemParts = item.split(" \\*\\* ");
    			
    			ONCChildGift childGift = null;
			if(itemParts.length == 3)
			{	
				//find the family by ONC Num
				String oncNum = itemParts[0].substring(6).trim();
				int year = DBManager.getCurrentSeason();
				ONCFamily f = familyDB.getFamilyByONCNum(year, oncNum);
				if(f != null)
				{	
					//get list of children in family
					List<ONCChild> childrenInFamilyList = childDB.getChildList(year, f.getID());
					
					//find the child that the item was selected for
					int cn;
					for(cn=0; cn < childrenInFamilyList.size(); cn++)
					{
						ONCChild child = childrenInFamilyList.get(cn);
						String childAgeGenderString = child.getChildAge() + " " + child.getChildGender();
						
						if(childAgeGenderString.equals(itemParts[1].trim()))
							break;
					}
						
					if(cn < childrenInFamilyList.size())
					{
						//found the child the item was selected for, now find the ONCChildWish id
						ONCChild child = childrenInFamilyList.get(cn);
						int gn;
						for(gn=0; gn < ServerChildDB.NUMBER_GIFTS_PER_CHILD; gn++)
						{
							ONCChildGift childCurrGift = ServerChildGiftDB.getGift(year, child.getChildGiftID(gn));	
							if(childCurrGift.getDetail().equals(itemParts[2].trim()))	 //there is a "\r" at the end of the imported item string
								break;
						}
			
						if(gn < ServerChildDB.NUMBER_GIFTS_PER_CHILD)
							childGift = ServerChildGiftDB.getGift(year, child.getChildGiftID(gn));
					}
				}
			}
			
			return childGift;
    		}
	
        	private class ClothingPartner
        	{
        		private ONCPartner partner;
        		private List<ONCChildGift> partnerGiftList;
        		
        		ClothingPartner(ONCPartner partner)
        		{
        			this.partner = partner;
        			partnerGiftList = new ArrayList<ONCChildGift>();
        		}
        
        		//getters
        		ONCPartner getPartner() { return partner; }
        		List<ONCChildGift> getGiftList() { return partnerGiftList; }

        		//helpers
        		void addChildGift(ONCChildGift cg) { partnerGiftList.add(cg); }
        	}
        	
        	private class SUAEmailComparator implements Comparator<SignUpActivity>
        	{
        		@Override
        		public int compare(SignUpActivity o1, SignUpActivity o2)
        		{			
        			return o1.getEmail().compareTo(o2.getEmail());
        		}
        	}
	}
}
