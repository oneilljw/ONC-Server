package oncserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;

import au.com.bytecode.opencsv.CSVWriter;
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
		private static final String SIGNUP_REPORT_URL = "https://api.signupgenius.com/v2/k/signups/report/%s/%d/?user_key=%s";
		
		SignUp signup;
		SignUpType signUpType;
		SignUpReport signUpReport;
		SignUpReportType reportType;
		List<SignUpActivity> signUpClothingList;
		List<ONCPartner> newPartnerFoundList;
		List<ONCPartner> updatedPartnerFoundList;
		List<ONCPartner> unmodifiedPartnerFoundList;
		
		//singleton constructor for importing volunteers and activities from a specific sign-up in ONC account
		SignUpGeniusClothingImportWorker(SignUp signup, SignUpType signUpType, SignUpReportType reportType)
		{
			this.signup = signup;
			this.signUpType = signUpType;
			this.reportType = reportType;
		
			//initialize the lists
			newPartnerFoundList = new ArrayList<ONCPartner>();
			updatedPartnerFoundList = new ArrayList<ONCPartner>();
			unmodifiedPartnerFoundList = new ArrayList<ONCPartner>();
		}
		
		@Override
		protected Void doInBackground() throws Exception
		{
			String response = getSignUpJson(String.format(SIGNUP_REPORT_URL, reportType.toString(), signup.getSignupid(), API_KEY));
//			String response = simulateGeniusReportFetch("clothingtestfile.txt");
//			System.out.println(String.format("SUGClothImport.DIB: response= %s", response));
			if(response != null && !response.isEmpty())
			{
				//get the report that was imported
				Gson gson = new Gson();
				signUpReport = gson.fromJson(response.toString(), SignUpReport.class); //create the report from the json
				signUpClothingList = signUpReport.getContent().getSignUpActivities(); //get the list of signUpActivities
				
				//not all sign up activities have an end date. If they don't, set the end date to Christmas Day
				//for the current season. Note: SignUpActivity times are in seconds not milliseconds
				for(SignUpActivity sua : signUpClothingList)
					if(sua.getEnddate() == 0)
						sua.setEnddate(getChristmasDayTimeInSeconds());	//milliseconds to seconds
				
				//create the unique partner list
				List<ONCPartner> uniquePartnerList = createUniquePartnerList(signUpClothingList);
				
				//DEBUG PRINT THE IMPORTED PARTNER INFO
//				for(ONCPartner p : uniquePartnerList)
//					System.out.println(String.format("SUGClothImportWrkr.DIB: unique part name= %s, email= %s",
//							p.getContact(), p.getContact_email()));
				
				//create the new and modified volunteer lists
				if(!uniquePartnerList.isEmpty())
					createNewAndModifiedPartnerLists(uniquePartnerList);			
			}
				
			return null;
		}
		
		protected void done()
	    {
	    		//thread complete, update sign up import time and notify clients of the import
			signup.setLastImportTimeInMillis(System.currentTimeMillis());
        		signUpContentReceived(SignUpEventType.REPORT, signup);
			
			//if clothing partners were imported, notify clients. For 2018 we're not updating partners
        		//for Coat imports
			if(signUpType == SignUpType.Clothing)
			{	
				if(!updatedPartnerFoundList.isEmpty())
					signUpContentReceived(SignUpEventType.UPDATED_PARTNERS, updatedPartnerFoundList);
			
				if(!newPartnerFoundList.isEmpty())
					signUpContentReceived(SignUpEventType.NEW_PARTNERS, newPartnerFoundList);
			}
        				
        		//save the imported file
			saveClothingSignUp();
			
//			System.out.println(String.format("SUGClothImptr.done: %d updated, %d new, %d unmodified partners imported",
//  				updatedPartnerFoundList.size(), newPartnerFoundList.size(), unmodifiedPartnerFoundList.size()));
        	}
		
		void saveClothingSignUp()
		{
			String path = String.format("%s/2018DB/%s", System.getProperty("user.dir"), "SignUpGeniusClothingImport.csv");
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
		 * reads a previously stored json from SignUp Genius from a file and returns it as a String
		 */

		@SuppressWarnings("unused")
		String simulateGeniusReportFetch(String filename)
        	{
        		InputStream is = null;
        		BufferedReader buf = null;
        		String line = null;
        		try
        		{
        			is = new FileInputStream(String.format("%s/%s", System.getProperty("user.dir"), filename));
        			buf = new BufferedReader(new InputStreamReader(is));
        			line = buf.readLine();
        			
        			StringBuilder sb = new StringBuilder(); 
            		while(line != null)
            		{ 
            			sb.append(line).append("\n"); 
            			line = buf.readLine();
            		}
            		
            		buf.close();
            		
            		return sb.toString();
        		}
        		catch (FileNotFoundException e)
        		{
        			return null;
        		}
        		catch (IOException e)
        		{
        			return null;
        		}
        	}
       		
        	/***
        	* Parses the list of imported sign-up activities and produces a list of unique partners
        	* in the list. A partner may adopt multiple clothing/coat gifts, this method produces a list
        	* where the partner is listed once, regardless of how many items they adopt.
        	* @param signUpActivityList
        	* @return
        	*/
        	private List<ONCPartner> createUniquePartnerList(List<SignUpActivity>  signUpActivityList)
        	{	
        		//create the unique partner in signUp list
        		List<ONCPartner>  uniqueSignUpPartnerList = new ArrayList<ONCPartner>();
        		for(SignUpActivity sua : signUpActivityList)
        		{	
        			if(!sua.getFirstname().isEmpty() && !sua.getLastname().isEmpty() && !sua.getEmail().isEmpty())
        			{
        				ONCPartner importedPartner = new ONCPartner(sua, signUpType);
        				if(!isPartnerInList(uniqueSignUpPartnerList, importedPartner))
        					uniqueSignUpPartnerList.add(importedPartner);
        			}
        		}
        			
        		return uniqueSignUpPartnerList;
        	}
        		
        	private boolean isPartnerInList(List<ONCPartner> list, ONCPartner p)
        	{
        		int index = 0;	
        		while(index < list.size() && !arePartnersAMatch(list.get(index), p))
        			index++;
        			
        		return index < list.size();
        	}
        	
        	/***
        	 * partners are a match if their name and email fields match or their email and home phone fields match
        	 * @param p1
        	 * @param p2
        	 * @return
        	 */
        	boolean arePartnersAMatch(ONCPartner p1, ONCPartner p2)
        	{
        		boolean partnersNameAndEmailMatches = p1.getContact().equalsIgnoreCase(p2.getContact()) &&
        				p1.getContact_email().equalsIgnoreCase(p2.getContact_email());
        		
        		boolean partnersEmailAndPhoneMatches = p1.getHomePhone().equals(p2.getHomePhone()) &&
        				p1.getContact_email().equalsIgnoreCase(p2.getContact_email());
        		
        		
        		return partnersNameAndEmailMatches || partnersEmailAndPhoneMatches;
        	}
        		
        	private void createNewAndModifiedPartnerLists(List<ONCPartner> uniquePartnerList)
        	{
        		//clone a list of the current partners from the partner database
        		ServerPartnerDB partnerDB;
        		try
        		{
        			partnerDB = ServerPartnerDB.getInstance();
        			List<ONCPartner> clonePartnerList = partnerDB.clone(DBManager.getCurrentYear());
        				
        			//compare the unique partner list to the cloned list. If an imported unique partner is not in 
        			//the clone list or if the imported unique partner name or contact info has been modified, add
        			//the partner to the newAndModified partner list
        			for(ONCPartner importedPartner : uniquePartnerList)
        			{
        				//check for email match first. If email matches, update the address and phone number
        				int index = 0;
        				while(index < clonePartnerList.size() && (clonePartnerList.get(index).getContact_email().isEmpty() ||
        						!clonePartnerList.get(index).getContact_email().equalsIgnoreCase(importedPartner.getContact_email())))
        					index++;
        
        				if(index < clonePartnerList.size())
        				{	
        					if(!clonePartnerList.get(index).matches(importedPartner))
       					{
        						//there is an email match, but not an overall match. Update the contact info
        						ONCPartner clonedPartner = clonePartnerList.get(index);
        						clonedPartner.setContact_phone(importedPartner.getContact_phone());
        						clonedPartner.setHouseNum(importedPartner.getHouseNum());
        						clonedPartner.setStreet(importedPartner.getStreet());
        						clonedPartner.setUnit(importedPartner.getUnit());
        						clonedPartner.setCity(importedPartner.getCity());
        						clonedPartner.setZipCode(importedPartner.getZipCode());
        						updatedPartnerFoundList.add(clonedPartner);
        					}
        					else
        						unmodifiedPartnerFoundList.add(importedPartner);
        					
        				}
        				else  //check to see if there is a name match
        				{
        					int x = 0;
        					while(x < clonePartnerList.size() &&
        							!clonePartnerList.get(x).getLastName().equalsIgnoreCase(importedPartner.getLastName()))
        						x++;
        					
        					if(x < clonePartnerList.size())
        					{
        						//found cloned partner by name but not by email. Update contact info and add to list
        						ONCPartner clonedPartner = clonePartnerList.get(x);
        						clonedPartner.setContact(importedPartner.getContact());
        						clonedPartner.setContact_email(importedPartner.getContact_email());
        						clonedPartner.setContact_phone(importedPartner.getContact_phone());
        						clonedPartner.setHouseNum(importedPartner.getHouseNum());
        						clonedPartner.setStreet(importedPartner.getStreet());
        						clonedPartner.setUnit(importedPartner.getUnit());
        						clonedPartner.setCity(importedPartner.getCity());
        						clonedPartner.setZipCode(importedPartner.getZipCode());
        						updatedPartnerFoundList.add(clonedPartner);
        					}
        					else
        						newPartnerFoundList.add(importedPartner);	//add new parter to list
        				}
        			}
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

        	/***
         * Component of the json object returned when importing a specific SignUp from SignUp Genius
         * @author johnoneil
         *
         */
        	private class SignUpReport extends SignUpCommon
        	{
        		private SignUpContent data;
        		
        		//getters
        		SignUpContent getContent() { return data; }
        		
        	}
        	
        	/***
        	 * Component of the object returned from SignUp Genius when importing a specific Volunteer SignUp
        	 * from the Our Neighbor's Child SignUp Genius Volunteer SignUps. An array of SignUpActivity objects
        	 *  as part of the json.
        	 * @author johnoneil
        	 *
        	 */
        	private class SignUpContent
        	{
        		private SignUpActivity[] signup;
        		
        		//getters
        		List<SignUpActivity> getSignUpActivities()
        		{
        			List<SignUpActivity> list = new ArrayList<SignUpActivity>();
        			for(SignUpActivity su:signup)
        				list.add(su);
        			
        			return list;	
        		}
        	}	
	}
}
