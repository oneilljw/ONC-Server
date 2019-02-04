package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ourneighborschild.ONCChild;
import ourneighborschild.ONCChildGift;
import ourneighborschild.ONCFamily;
import ourneighborschild.ONCPriorYearChild;
import ourneighborschild.ONCUser;

import com.google.gson.Gson;

public class PriorYearDB extends ServerSeasonalDB
{
	private static final int PY_CHILD_DB_HEADER_LENGTH = 10;
//	private static final int ONC_MAX_CHILD_AGE = 24; //Used for sorting children into array lists
	
	private static PriorYearDB instance = null;
	private List<PriorYearChildDBYear> pycDB;
	
	private PriorYearDB() throws FileNotFoundException, IOException
	{
		//create the prior year child data base
		pycDB = new ArrayList<PriorYearChildDBYear>();
								
		//populate the data base for the last TOTAL_YEARS from persistent store
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the prior year child list for each year
			PriorYearChildDBYear pycDBYear = new PriorYearChildDBYear(year);
									
			//add the list for the year to the db
			pycDB.add(pycDBYear);
									
			//import from persistent store		
			importDB(year, String.format("%s/%dDB/PriorYearChildDB.csv",
						System.getProperty("user.dir"),
							year), "Prior Year Child DB", PY_CHILD_DB_HEADER_LENGTH);
					
			//set the next id
			pycDBYear.setNextID(getNextID(pycDBYear.getList()));
		}
	}
	
	public static PriorYearDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new PriorYearDB();
		
		return instance;
	}
	
	String getPriorYearChild(int year, String zID)
	{
		List<ONCPriorYearChild> pycAL = pycDB.get(year - BASE_YEAR).getList();
		int id = Integer.parseInt(zID);
		int index = 0;
		
		while(index < pycAL.size() && pycAL.get(index).getID() != id)
			index++;
		
		if(index < pycAL.size())
		{
			Gson gson = new Gson();
			String pycjson = gson.toJson(pycAL.get(index), ONCPriorYearChild.class);
			
			return "PYC" + pycjson;
		}
		else
			return "PYC_NOT_FOUND";
	}
	
	String searchForPriorYearChild(int year, String pycJson)
	{
		Gson gson = new Gson();
		ONCPriorYearChild targetPYC = gson.fromJson(pycJson, ONCPriorYearChild.class);
	
		
		List<ONCPriorYearChild> pycAL = pycDB.get(year - BASE_YEAR).getList();
		
		int index = 0;
		while(index < pycAL.size())
		{
			ONCPriorYearChild pyc = pycAL.get(index);
			if(pyc.getLastName().equalsIgnoreCase(targetPYC.getLastName()) &&
				pyc.getDOB() == targetPYC.getDOB() && 
				 pyc.getGender().equalsIgnoreCase(targetPYC.getGender()))
				break;
			else
				index++;
		}
		
		if(index < pycAL.size())
		{
			String pycjson = gson.toJson(pycAL.get(index), ONCPriorYearChild.class);
			
			return "PYC" + pycjson;
		}
		else
			return "PYC_NOT_FOUND";
	}
	
	ONCPriorYearChild getPriorYearChild(int year, int id)
	{
		List<ONCPriorYearChild> pycAL = pycDB.get(year - BASE_YEAR).getList();
		
		int index = 0;
		while(index < pycAL.size() && pycAL.get(index).getID() != id)
			index++;
		
		if(index < pycAL.size())
			return pycAL.get(index);
		else
			return null;
	}
	
	int searchForMatch(int year, ONCChild c)
	{
		List<ONCPriorYearChild> pycList = pycDB.get(year - BASE_YEAR).getList();
		int index = 0;
		
		while(index < pycList.size() && !pycList.get(index).isMatch(c.getChildFirstName(),
																	c.getChildLastName(),
																	c.getChildGender(),
																	c.getChildDateOfBirth()))													 
			index++;
		
		if(index == pycList.size())
			return -1;
		else
			return pycList.get(index).getID();
	}

	@Override
	String add(int year, String userjson, ONCUser client) 
	{
		//This method is unused. Prior year children are only created by the
		//server. 
		return null;
	}
	
	//overloaded add method used when creating a new prior year child
	void add(int year, ONCChild lyc, String lyw0, String lyw1, String lyw2)
	{
		PriorYearChildDBYear pycDBYear = pycDB.get(year - BASE_YEAR);
				
		//set the new ID for the new prior year child and add the new prior year child
		ONCPriorYearChild newpyChild = new ONCPriorYearChild(pycDBYear.getNextID(), lyc, lyw0, lyw1, lyw2);
		
		pycDBYear.add(newpyChild);
	}
	
	//overloaded add method used when retaining a prior year child
	void add(int year, ONCPriorYearChild pyc)
	{
		PriorYearChildDBYear pycDBYear = pycDB.get(year - BASE_YEAR);
				
		//set the new ID for the new prior year child and add the new prior year child
		ONCPriorYearChild newpyChild = new ONCPriorYearChild(pycDBYear.getNextID(), pyc);
		
		pycDBYear.add(newpyChild);
	}

	@Override
	void addObject(int year, String[] nextLine) 
	{
		PriorYearChildDBYear pycDBYear = pycDB.get(year - BASE_YEAR);
		pycDBYear.add(new ONCPriorYearChild(nextLine));
	}
	
	@Override
	void createNewYear(int newYear) 
	{
		//before creating and adding a new PriorYearChildDBYear to the data base, get last years
		//prior year child data base list. It will be the last in the list. It is used later in
		//the method to begin to populate the new prior year children list for the new year
		List<ONCPriorYearChild> lypycList = pycDB.get(pycDB.size()-1).getList();
		
		//create a new PriorYearChildDBYear object and add it to the data base. It will have an empty
		//ONCPriorYearChild list. Subsequent add calls will add ONCPriorYearChild objects to the list
		PriorYearChildDBYear newPYCDBYear = new PriorYearChildDBYear(newYear);
		pycDB.add(newPYCDBYear);
		
		//retain all prior year children from last year who only had last year wishes.
		//do not retain those with only prior year wishes
		for(ONCPriorYearChild lypyc : lypycList)
		{
			if(lypyc.hasLastYearWishes())
		    {
				//add last years prior year child to the this years PriorYearChildDBYear list.
				//This overloaded add method uses a PriorYearChild constructor that converts 
				//last year wishes to prior year wishes and leaves last year wishes blank
				add(newYear, lypyc);
		    }
		}
		
		//get references to last years family, child, child wish and wish catalog data bases
		ServerFamilyDB serverFamilyDB = null;
		ServerChildDB serverChildDB = null;
//		ServerChildWishDB childwishDB = null;
		ServerWishCatalog cat = null;
		try 
		{
			serverFamilyDB = ServerFamilyDB.getInstance();
			serverChildDB = ServerChildDB.getInstance();
//			childwishDB = ServerChildWishDB.getInstance();
			cat = ServerWishCatalog.getInstance();
		} 
		catch (FileNotFoundException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//get last years list of ONCChild objects
		List<ONCChild> lycList = serverChildDB.getList(newYear-1);
		
		//for each child from last year, if they were in an eligible family determine if the 
		//child's prior year history was retained already. If it was, add last years wishes. 
		//If not, add a new prior year child.
		int minONCNum = serverFamilyDB.getMinONCNum();
		int maxONCNum = serverFamilyDB.getMaxONCNum();
		
		for(ONCChild lyc:lycList)
		{
			ONCFamily lyfam = serverFamilyDB.getFamily(newYear-1, lyc.getFamID());
			int lyONCFamONCNum;
			
/* DEBUG CODE *****************			
			int bCD = 0;
			if(lyfam == null)
				bCD = bCD | 1;
			else
			{
				if(!isNumeric(lyfam.getONCNum()))
						bCD = bCD | 2;
				else
				{
					lyONCFamONCNum = Integer.parseInt(lyfam.getONCNum());
					if(lyONCFamONCNum < minONCNum) { bCD = bCD | 4; }
					if(lyONCFamONCNum > maxONCNum) { bCD = bCD | 8; }
					if(!lyfam.getDNSCode().isEmpty()) { bCD = bCD | 16; }
				}
			}
			
			if((bCD & 1) > 0)
				System.out.println(String.format("PriorYearDB.createNewYear: Family for childID %d is null", lyc.getID(), bCD));
			if((bCD & 2) > 0)
				System.out.println(String.format("PriorYearDB.createNewYear: Family for childID %d ONC# %s not numeric", lyc.getID(), lyfam.getONCNum()));
			if((bCD & 4) > 0)
				System.out.println(String.format("PriorYearDB.createNewYear: Family for childID %d ONC# %s < min", lyc.getID(), lyfam.getONCNum()));
			if((bCD & 8) > 0)
				System.out.println(String.format("PriorYearDB.createNewYear: Family for childID %d ONC# %s > max", lyc.getID(), lyfam.getONCNum()));
			if((bCD & 16) > 0)
				System.out.println(String.format("PriorYearDB.createNewYear: Family for childID %d ONC# %s has DNS Code %s", lyc.getID(), lyfam.getONCNum(), lyfam.getDNSCode()));				
****************/
			
			if(lyfam != null && isNumeric(lyfam.getONCNum()) && 
				(lyONCFamONCNum = Integer.parseInt(lyfam.getONCNum())) >= minONCNum &&
				 lyONCFamONCNum <= maxONCNum && lyfam.getDNSCode().isEmpty())	
			{
				ONCChildGift lyChildWish1 = ServerChildWishDB.getWish(newYear-1, lyc.getChildGiftID(0));
				ONCChildGift lyChildWish2 = ServerChildWishDB.getWish(newYear-1, lyc.getChildGiftID(1));
				ONCChildGift lyChildWish3 = ServerChildWishDB.getWish(newYear-1, lyc.getChildGiftID(2));
				
				//determine the wishes from last year. Check to ensure the child wish existed. If it didn't, 
				//set the wish blank
				String lyWish1 = "", lyWish2 = "", lyWish3 = "";
				if(lyChildWish1 != null)
					 lyWish1 = cat.findWishNameByID(newYear-1, lyChildWish1.getGiftID()) + "- " + lyChildWish1.getDetail();
				
				if(lyChildWish2 != null)
					lyWish2 = cat.findWishNameByID(newYear-1, lyChildWish2.getGiftID()) + "- " + lyChildWish2.getDetail();
				
				if(lyChildWish3 != null)
					lyWish3 = cat.findWishNameByID(newYear-1, lyChildWish3.getGiftID()) + "- " + lyChildWish3.getDetail();
	    		
				//last year child was in a served family, have they already been added to the
	    			//new years prior year child list?
	    			int id;
				if((id = searchForMatch(newYear, lyc)) > -1)
				{
					//last years child is already in this years prior year child list, simply
					//add their last year wishes to the prior year child object
					ONCPriorYearChild pyc = getPriorYearChild(newYear, id);
					pyc.addChildWishes(lyWish1, lyWish2, lyWish3);
				}
				else //if id == -1 or search returned null, the child wasn't retained,
				{
					//they don't have a history, so create a new history entry for them and add it
					add(newYear, lyc, lyWish1, lyWish2, lyWish3);
				}
			}
		}
		
		newPYCDBYear.setChanged(true);	//mark this db for persistent saving on the next save event
	}
		
    private class PriorYearChildDBYear extends ServerDBYear
    {
    	private List<ONCPriorYearChild> pycList;
    	
    	PriorYearChildDBYear(int year)
    	{
    		super();
    		pycList = new ArrayList<ONCPriorYearChild>();
    	}
    	
    	//getters
    	List<ONCPriorYearChild> getList() { return pycList; }
    	
    	void add(ONCPriorYearChild addedPYC) { pycList.add(addedPYC); }
    }

	@Override
	void save(int year)
	{
		String[] header = {"PY Child ID", "Last Name", "Gender", "DoB",
							"Last Year Wish 1", "Last Year Wish 2", "Last Year Wish 3",
							"Previous Year Wish 1", "Previous Year Wish 2", "Previous Year Wish 3"};
		
		PriorYearChildDBYear pycDBYear = pycDB.get(year - BASE_YEAR);
		
		if(pycDBYear.isUnsaved())
		{
			//	System.out.println(String.format("FamilyDB saveDB - Saving Family DB"));
			String path = String.format("%s/%dDB/PriorYearChildDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(pycDBYear.getList(), header, path);
			pycDBYear.setChanged(false);
		}
	}
/*	
	void updateDOBs()
	{
		//get time zone at GMT
		TimeZone localTZ = TimeZone.getDefault();
		
		//for each year in the data base, update the dob's
		for(int year=2012; year < 2014; year++)
		{
			PriorYearChildDBYear pycDBYear = pycDB.get(year - BASE_YEAR);
			System.out.println(String.format("Processing %d Prior Year Child DB", year));
			//for each prior year child, update their dob by calculating their dob time zone
			//offset to GMT and adding it to their local time zone DOB that was previously saved
			for(ONCPriorYearChild pyc : pycDBYear.getList())
			{
				long currDOB = pyc.getDOB();
				int gmtOffset = localTZ.getOffset(currDOB);
				pyc.setDOB(currDOB + gmtOffset);
			}
		
			//mark the db for saving to persistent store
			pycDBYear.setChanged(true);
		}
	}
*/	
}
