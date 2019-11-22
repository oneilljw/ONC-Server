package oncserver;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import com.google.gson.Gson;

import ourneighborschild.Frequency;
import ourneighborschild.GeniusSignUps;
import ourneighborschild.SignUp;
import ourneighborschild.SignUpType;

/***
 * Singleton class that imports list of SignUps in the Our Neighbor's Child account on SignUp Genius.
 * Users request the list via the requestSignUpList method. Retrieval of the list of SignUps is
 * performed in a separate thread using a SwingWorker. Once a list of SignUps has been imported,
 * a SignUp Event is fired, notifying listeners that a new list of SignUps has been imported.
 * @author johnoneil
 */
public class SignUpGeniusSignUpListImporter extends SignUpGeniusImporter
{
	private static SignUpGeniusSignUpListImporter instance;

	public static SignUpGeniusSignUpListImporter getInstance()
	{
		if(instance == null)
			instance = new SignUpGeniusSignUpListImporter();
		
		return instance;
	}

	/***
	 * Used to request import of a list of active SignUps from ONC's SignUp Genius Account
	 * Only visible method in the class.
	 */
	void requestSignUpList()
	{
		SignUpGeniusSignUpListImportWorker importer = new SignUpGeniusSignUpListImportWorker();
		importer.execute();
	}
	
	/***
	 * Called by the list import worker when the background thread performing the import completes.
	 * Notifies listeners that a list of SignUps has been imported.
	 * @param geniusSignUps
	 */
	private void signUpSummaryReceived(GeniusSignUps geniusSignUps)
	{	
		fireSignUpDataChanged(this, SignUpEventType.SIGNUP_LIST_IMPORT, geniusSignUps);
	}
	
	/***
	 * Worker that imports a list of active SignUps from SignUp Genius in a separate thread. Inherits 
	 * from SignUpGenius. 
	 * @author johnoneil
	 *
	 */
	private class SignUpGeniusSignUpListImportWorker extends SignUpGeniusImportWorker
	{
		private static final String SIGNUPS_URL = "https://api.signupgenius.com/v2/k/signups/created/active/?user_key=%s";
		
		GeniusSignUps importedSignUps;

		@Override
		protected Void doInBackground() throws Exception
		{
			//gets the list of sign-ups from SignUp Genius
			String response = getSignUpJson(String.format(SIGNUPS_URL, apiKey()));
			if(response != null && !response.isEmpty())
				processSignUpsJson(response.toString());
			
			return null;
		}
		
		 /*
	     * Executed in event dispatching thread
	     */
	    @Override
	    protected void done()
	    {
	    		signUpSummaryReceived(importedSignUps);	//notify the event dispatching thread import is complete
	    }
	   
	    /***
	     * Processes the json returned from SignUp Genius when importing a list of active SignUps.
	     * Resets the last import time to the start of time and the import Frequency to NEVER.
	     * SignUps is an object class returned when importing a list of SignUps. SignUps provides an array of
	     * SignUp class objects. The SignUp class contains the details of each SignUp.
	     * @param json
	     */
		private void processSignUpsJson(String json)
		{
			Gson gson = new Gson();
			SignUps signUps =  gson.fromJson(json, SignUps.class);
			
			//Create a GeniusSignUps object. For each of the imported sign ups, initialize and adjust the time
			importedSignUps = new GeniusSignUps(signUps.getSignUps());

			for(SignUp su : importedSignUps.getSignUpList())
			{
				//check to see if an imported sign up is new. If it is, initialize the member variables
				//that don't come from the genius import for each sign up. They will be null otherwise.
				SignUp existingSignUp = null;
				try
				{
					ServerActivityDB activityDB = ServerActivityDB.getInstance();
					existingSignUp = activityDB.findSignUp(su.getSignupid());
				}
				catch (IOException e)
				{
					su.setLastImportTimeInMillis(0);
					su.setFrequency(Frequency.NEVER);
					su.setSignUpType(SignUpType.Unknown);
					if(su.getEndtimeInMillis() == 0)
						su.setEndtime(getChristmasDayTimeInSeconds() * 1000);
					else
						su.setEndtime(su.getEndtimeInMillis() * 1000);
				}
				
				if(existingSignUp != null)
				{	
					su.setLastImportTimeInMillis(existingSignUp.getLastImportTimeInMillis());
					su.setFrequency(existingSignUp.getFrequency());
					su.setSignUpType(existingSignUp.getSignUpType());
				}
				else
				{
					su.setLastImportTimeInMillis(0);
					su.setFrequency(Frequency.NEVER);
					su.setSignUpType(SignUpType.Unknown);	
				}
				
				//adjust the SignUp Genius end time to milliseconds from seconds. Add Christmas day
				//as an end time if one was not provided
				if(su.getEndtimeInMillis() == 0)
					su.setEndtime(getChristmasDayTimeInSeconds() * 1000);
				else
					su.setEndtime(su.getEndtimeInMillis() * 1000);
			}	
		}
		
		/** SignUps is an object class returned when importing a list of SignUps. SignUps provides an array of
	     * SignUp class objects. The SignUp class contains the details of each SignUp. SignUpCommon contains
	     * the common elements of the json returned from SignUpGenius
	     */ 
		private class SignUps extends SignUpCommon
		{
			private SignUp[] data;
			
			//getters
			public List<SignUp> getSignUps()
			{
				List<SignUp> list = new LinkedList<SignUp>();
				for(SignUp su : data)
					list.add(su);
				
				return list;	
			}
		}	
	}
}