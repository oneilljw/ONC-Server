package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ourneighborschild.ONCNote;


public class ServerNoteDB extends ServerSeasonalDB
{
	private static final int NOTE_DB_HEADER_LENGTH = 13;

	private static List<NoteDBYear> noteDB;
	private static ServerNoteDB instance = null;
	
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
	String add(int year, String json)
	{
		//Create an ONCAdult object for the added adult
		Gson gson = new Gson();
		ONCNote addedNote = gson.fromJson(json, ONCNote.class);
						
		//retrieve the note data base for the year
		NoteDBYear noteDBYear =noteDB.get(year - BASE_YEAR);
								
		//set the new ID for the added ONCNote
		addedNote.setID(noteDBYear.getNextID());
		
		//add the new noteto the data base
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
		
		//Replace the current adult object with the update
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
		String[] header = {"Note ID", "Owner ID", "Status", "Note", "Changed By",
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
