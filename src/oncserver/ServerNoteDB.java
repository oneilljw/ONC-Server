package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ourneighborschild.ONCNote;
import ourneighborschild.ONCUser;


public class ServerNoteDB extends ServerSeasonalDB
{
	private static final int NOTE_DB_HEADER_LENGTH = 16;

	private static List<NoteDBYear> noteDB;
	private static ServerNoteDB instance = null;
	
	
	private static ClientManager clientMgr;
	
	private ServerNoteDB() throws FileNotFoundException, IOException
	{
		//create the note data bases for TOTAL_YEARS number of years
		noteDB = new ArrayList<NoteDBYear>();
					
		for(int year = DBManager.getBaseSeason(); year < DBManager.getBaseSeason() + DBManager.getNumberOfYears(); year++)
		{
			//create the note list for year
			NoteDBYear noteDBYear = new NoteDBYear(year);
				
			//add the list for the year to the db
			noteDB.add(noteDBYear);
						
			importDB(year, String.format("%s/%dDB/NoteDB.csv", System.getProperty("user.dir"),
								year), "Note DB", NOTE_DB_HEADER_LENGTH);

			//set the next id
			noteDBYear.setNextID(getNextID(noteDBYear.getList()));	
		}
		
		clientMgr = ClientManager.getInstance();
	}
	
	public static ServerNoteDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerNoteDB();
		
		return instance;
	}
	
	String getNotes(int year)
	{
		Gson gson = new Gson();
		Type listOfNotes = new TypeToken<ArrayList<ONCNote>>(){}.getType();
		
		String response = gson.toJson(noteDB.get(DBManager.offset(year)).getList(), listOfNotes);
		return response;	
	}

	@Override
	String add(int year, String json, ONCUser client)
	{
		//Create an ONCAdult object for the added adult
		Gson gson = new Gson();
		ONCNote addedNote = gson.fromJson(json, ONCNote.class);
						
		//retrieve the note data base for the year
		NoteDBYear noteDBYear = noteDB.get(DBManager.offset(year));
								
		//set the new ID and time stamps for the added ONCNote
		addedNote.setID(noteDBYear.getNextID());
		addedNote.setDateChanged(System.currentTimeMillis());
		addedNote.setChangedBy(client.getLNFI());
		
		//if the showNextSeason flag is set, clear the showNextSeason flag for all other family notes
		if(addedNote.showNextSeason())
			for(ONCNote n : noteDBYear.getList())
				if(n.getOwnerID() == addedNote.getOwnerID())
					n.setShowNextSeason(false);
		
		//add the new note to the data base.
		noteDBYear.add(addedNote);
		noteDBYear.setChanged(true);
		
		//if the note includes a request to sent the agent an email, do so
//		if(addedNote.sendEmail())
//			userDB.createAndSendNoteNotificationEmail(year, addedNote);
							
		return "ADDED_NOTE" + gson.toJson(addedNote, ONCNote.class);
	}
	
	String update(int year, String json)
	{
		//Create a ONCNote object for the updated note
		Gson gson = new Gson();
		ONCNote updatedNote = gson.fromJson(json, ONCNote.class);
		
		//Find the position for the current note being updated
		NoteDBYear noteDBYear = noteDB.get(DBManager.offset(year));
		List<ONCNote> noteList = noteDBYear.getList();
		int index = 0;
		while(index < noteList.size() && noteList.get(index).getID() != updatedNote.getID())
			index++;
		
		//Replace the current note object with the update. Check for a state change and
		//update the status accordingly
		if(index < noteList.size())
		{
			//if the change is setting the showNextSeason flags, clear all the showNextSeason flags
			//in for the same family in the season
			if(updatedNote.showNextSeason())
				for(ONCNote n : noteDBYear.getList())
					if(n.getOwnerID() == updatedNote.getOwnerID())
						n.setShowNextSeason(false);
			
			noteList.set(index, updatedNote);
			noteDBYear.setChanged(true);
			return "UPDATED_NOTE" + gson.toJson(updatedNote, ONCNote.class);
		}
		else
			return "UPDATE_FAILED";
	}
	
	String delete(int year, String json)
	{
		//Create a ONCNote object for the deleted note
		Gson gson = new Gson();
		ONCNote deletedNote = gson.fromJson(json, ONCNote.class);
		
		//find and remove the deleted note from the data base
		NoteDBYear noteDBYear = noteDB.get(DBManager.offset(year));
		List<ONCNote> noteList = noteDBYear.getList();
		
		int index = 0;
		while(index < noteList.size() && noteList.get(index).getID() != deletedNote.getID())
			index++;
		
		if(index < noteList.size())
		{
			noteList.remove(index);
			noteDBYear.setChanged(true);
			return "DELETED_NOTE" + json;
		}
		else
			return "DELETE_FAILED";	
	}

	@Override
	void createNewSeason(int year)
	{
		//create a new ONCNote data base year for the year provided in the year parameter
		//The note db year list is initially empty, all we do here is create a new Note
		//DBYear for the new year and save it.
		NoteDBYear noteDBYear = new NoteDBYear(year);
		noteDB.add(noteDBYear);
		noteDBYear.setChanged(true);	//mark this db for persistent saving on the next save event
	}

	@Override
	void addObject(int year, String[] nextLine)
	{
		//Get the note list for the year and add the note
		NoteDBYear noteDBYear = noteDB.get(DBManager.offset(year));
		noteDBYear.add(new ONCNote(nextLine));
	}

	@Override
	void save(int year)
	{
		String[] header = {"Note ID", "Owner ID", "Status", "Title", "Note", "Changed By",
				"Response", "Response By", "Time Created", "Time Viewed","Time Responded",
				"Email","Next Season", "Stoplight Pos", "Stoplight Mssg", "Stoplight C/B"};
		
		NoteDBYear noteDBYear = noteDB.get(DBManager.offset(year));
		if(noteDBYear.isUnsaved())
		{
			String path = String.format("%s/%dDB/NoteDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(noteDBYear.getList(),  header, path);
			noteDBYear.setChanged(false);
		}
	}
	
	static List<ONCNote> getNotesForFamily(int year, int ownerID)
	{
		List<ONCNote> famNoteList = new ArrayList<ONCNote>();
		for(ONCNote n : noteDB.get(DBManager.offset(year)).getList())
			if(n.getOwnerID() == ownerID)
				famNoteList.add(n);
				
		return famNoteList;		 
	}
	/******
	 * Retrieves the status of the last note for a family if the year is the current season. 
	 * Otherwise, it looks for the last note in a prior year and if the prior year last note 
	 * has the showNextSeason flag set, it returns that status. If no note is found or if a prior 
	 * year last note is found without the showNextSeason flag set, -1 is returned.  
	 * @param year
	 * @param ownerID
	 * @return
	 */
	static int lastNoteStatus(int year, int ownerID)
	{	
		List<ONCNote> noteList = noteDB.get(DBManager.offset(year)).getList();
		
		ONCNote note = null;
		int index;
		if(DBManager.getCurrentSeason() != year)
		{
			for(index = noteList.size() -1; index >=0; index--)
			{
				note = noteList.get(index);
				if(note.getOwnerID() == ownerID && note.showNextSeason())
					break;
			}
		}
		else
		{
			for(index = noteList.size() -1; index >=0; index--)
			{
				note = noteList.get(index);
				if(note.getOwnerID() == ownerID)
					break;
			}
		}
		
		if(index >= 0 && note != null)
			return note.getStatus();
		else
			return -1; 
	}
	
	//For current season families, we only let the referring agent view the most recent note. For prior season 
	//families we provide the note, if one exists, that has been marked to show the next season.
	//Take advantage of the fact the list of notes if saved in time order. Search 
	//from the bottom for the first note for the family. If no note, return a dummy note
	static ONCNote getLastNoteForFamily(int year, int ownerID, WebClient wc)
	{
		NoteDBYear noteDBYear = noteDB.get(DBManager.offset(year));
		List<ONCNote> noteList = noteDBYear.getList();
		ONCNote updatedNote = null;
		int index;
		
		if(DBManager.getCurrentSeason() != year)
		{
			for(index = noteList.size() -1; index >=0; index--)
			{
				updatedNote = noteList.get(index);
				if(updatedNote.getOwnerID() == ownerID && updatedNote.showNextSeason())
					break;
			}
		}
		else
		{
			for(index = noteList.size() -1; index >=0; index--)
			{
				updatedNote = noteList.get(index);
				if(updatedNote.getOwnerID() == ownerID)
					break;
			}
		}		
		
		if(index >= 0 && updatedNote != null)
		{
			//update user who viewed if not already responded
			if(updatedNote.getStatus() < ONCNote.RESPONDED)
			{
				updatedNote.noteViewed(wc.getWebUser().getLNFI());
				noteDBYear.setChanged(true);
			
				//notify in year clients of the updated note
				Gson gson = new Gson();
				clientMgr.notifyAllInYearClients(year, "UPDATED_NOTE" + gson.toJson(updatedNote, ONCNote.class));
			}
			return updatedNote; 
		}
		else
			return new ONCNote();
	}
	
	static HtmlResponse getLastNoteForFamilyJSONP(int year, int famID, WebClient wc, String callbackFunction)
	{		
		Gson gson = new Gson();
		String response = gson.toJson(getLastNoteForFamily(year, famID, wc), ONCNote.class);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	
	static HtmlResponse processNoteResponseJSONP(int year, int noteID, WebClient wc, String submittedResponse, 
														String callbackFunction)
	{
		String response = "";
		//find and update the note with the agents response
		NoteDBYear noteDBYear = noteDB.get(DBManager.offset(year));
		List<ONCNote> noteList = noteDBYear.getList();
		int index=0;
		while(index < noteList.size() && noteList.get(index).getID() != noteID)
			index++;
		
		if(index < noteList.size())
		{
			ONCNote updatedNote = noteList.get(index);
			updatedNote.setResponse(submittedResponse, wc.getWebUser().getLNFI());
			noteDBYear.setChanged(true);
			
			//notify in year clients of the updated note
			Gson gson = new Gson();
			clientMgr.notifyAllInYearClients(year, "UPDATED_NOTE" + gson.toJson(updatedNote, ONCNote.class));
			response = gson.toJson(updatedNote, ONCNote.class);
		}
		
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);
	}
	
	private class NoteDBYear extends ServerDBYear
	{
		private List<ONCNote> list;
	    	
	    NoteDBYear(int year)
	    {
	    		super();
	    		list = new ArrayList<ONCNote>();
	    }
	    	
	    	//getters
	    	List<ONCNote> getList() { return list; }
	    	
	    	void add(ONCNote addedNote) { list.add(addedNote); }
	}
}
