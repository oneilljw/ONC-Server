package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ourneighborschild.ONCNote;
import ourneighborschild.ONCUser;


public class ServerNoteDB extends ServerSeasonalDB
{
	private static final int NOTE_DB_HEADER_LENGTH = 14;

	private static List<NoteDBYear> noteDB;
	private static ServerNoteDB instance = null;
	
	private static Map<String, DNSCode> dnsCodeMap;
	private static ClientManager clientMgr;
	
	private ServerNoteDB() throws FileNotFoundException, IOException
	{
		//create the note data bases for TOTAL_YEARS number of years
		noteDB = new ArrayList<NoteDBYear>();
					
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
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
		
		dnsCodeMap = new HashMap<String, DNSCode>();
		loadDNSCodeMap();
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
		
		String response = gson.toJson(noteDB.get(year-BASE_YEAR).getList(), listOfNotes);
		return response;	
	}

	@Override
	String add(int year, String json, ONCUser client)
	{
		//Create an ONCAdult object for the added adult
		Gson gson = new Gson();
		ONCNote addedNote = gson.fromJson(json, ONCNote.class);
						
		//retrieve the note data base for the year
		NoteDBYear noteDBYear =noteDB.get(year - BASE_YEAR);
								
		//set the new ID and time stamps for the added ONCNote
		addedNote.setID(noteDBYear.getNextID());
		addedNote.setDateChanged(new Date());
		addedNote.setChangedBy(client.getLNFI());
		addedNote.setStoplightChangedBy(client.getLNFI());
		
		//add the new note to the data base
		noteDBYear.add(addedNote);
		noteDBYear.setChanged(true);
							
		return "ADDED_NOTE" + gson.toJson(addedNote, ONCNote.class);
	}
	
	String update(int year, String json)
	{
		//Create a ONCNote object for the updated note
		Gson gson = new Gson();
		ONCNote updatedNote = gson.fromJson(json, ONCNote.class);
		
		//Find the position for the current note being updated
		NoteDBYear noteDBYear = noteDB.get(year-BASE_YEAR);
		List<ONCNote> noteList = noteDBYear.getList();
		int index = 0;
		while(index < noteList.size() && noteList.get(index).getID() != updatedNote.getID())
			index++;
		
		//Replace the current note object with the update. Check for a state change and
		//update the status accordingly
		if(index < noteList.size())
		{
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
		NoteDBYear noteDBYear = noteDB.get(year-BASE_YEAR);
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
	void createNewYear(int year)
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
		NoteDBYear noteDBYear = noteDB.get(year - BASE_YEAR);
		noteDBYear.add(new ONCNote(nextLine));
	}

	@Override
	void save(int year)
	{
		String[] header = {"Note ID", "Owner ID", "Status", "Title", "Note", "Changed By",
				"Response", "Response By", "Time Created", "Time Viewed",
				"Time Responded", "Stoplight Pos", "Stoplight Mssg", "Stoplight C/B"};
		
		NoteDBYear noteDBYear = noteDB.get(year - BASE_YEAR);
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
		for(ONCNote n : noteDB.get(year-BASE_YEAR).getList())
			if(n.getOwnerID() == ownerID)
				famNoteList.add(n);
				
		return famNoteList;		 
	}
	
	static int lastNoteStatus(int year, int ownerID)
	{
		List<ONCNote> noteList = noteDB.get(year-BASE_YEAR).getList();
		int index = noteList.size() -1;
		while(index >= 0 && noteList.get(index).getOwnerID() != ownerID)
			index--;
		
		return index >= 0 ? noteList.get(index).getStatus() : -1;
	}
	
	//take advantage of the fact the list of notes if saved in time order. Search 
	//from the bottom for the first note for the family. If no note, return a dummy note
	static ONCNote getLastNoteForFamily(int year, int ownerID, WebClient wc)
	{
		NoteDBYear noteDBYear = noteDB.get(year-BASE_YEAR);
		List<ONCNote> noteList = noteDBYear.getList();
		int index = noteList.size()-1;
		while(index >= 0 && noteList.get(index).getOwnerID() != ownerID)
			index--;
		
		if(index >= 0)
		{
			ONCNote updatedNote = noteList.get(index);
			
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
	
	static HtmlResponse getDNSCodeJSONP(String code, String callbackFunction)
	{		
		Gson gson = new Gson();
		String response = gson.toJson(dnsCodeMap.get(code), DNSCode.class);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	
	static HtmlResponse processNoteResponseJSONP(int year, int noteID, WebClient wc, String submittedResponse, 
														String callbackFunction)
	{
		String response = "";
		//find and update the note with the agents response
		NoteDBYear noteDBYear = noteDB.get(year-BASE_YEAR);
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
	void loadDNSCodeMap()
	{
	    	dnsCodeMap.put("DUP", new DNSCode("Duplicate Family", "Duplicate referral: Two or more referrals were received for this family. "
	    				+ "ONC will serve the family through the first referral and this referral will not be "
	    				+ "processed"));
	    		
	    	dnsCodeMap.put("WL", new DNSCode("Waitlist", "Waitlist referral: indicates the family is on ONC's wait list."));
	    		
	    	dnsCodeMap.put("FO", new DNSCode("Food Only", "Referrals marked FO are only requesting holiday food assistance and not requesting "
			      	+ "gift assistance. ONC forwards food assistance requests to Western Fairfax Christian "
			      	+ "Ministries (WFCM). Contact jbush@wfcmva.org with food assistance questions."));
	    		
	    	dnsCodeMap.put("NISA", new DNSCode("Not In Serving Area", "Indicates this family is not located in ONC's serving area."));
	    		
	    	dnsCodeMap.put("OPT-OUT", new DNSCode("Opt-Out", "Indicates the family requested holiday gift "
	    				+ "assistance, however, when ONC contacted the family to confirm delivery, the family "
	    				+ "withdrew it's gift assistance request. This may not apply to food assistance. "
	    				+ "Contact jbush@wfcmva.org for food assistance information."));
	    		
	    	dnsCodeMap.put("SA", new DNSCode("Salvation Army", "Indicates that the family is being served by the Salvation Army and "
	    				+ "will not be receiving an ONC gift delivery."));
	    		
	    	dnsCodeMap.put("SBO", new DNSCode("Served By Others", "Indicates the family is being served by "
	    				+ "another organization in our area. See Gift Status - Referred."));
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
	private class DNSCode
    {
    		String title;
    		String definition;
    		
    		DNSCode(String title, String definition)
    		{
    			this.title = title;
    			this.definition = definition;
    		}
    		
    		//getters
    		@SuppressWarnings("unused")
			String getTitle() { return title; }
    		@SuppressWarnings("unused")
			String getDefinition() { return definition; }
    }	
}
