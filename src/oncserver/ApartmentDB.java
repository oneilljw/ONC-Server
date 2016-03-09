package oncserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.JOptionPane;

import ourneighborschild.ONCFamily;
import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

public class ApartmentDB 
{
	private static final int APARTMENTDB_HEADER_LENGTH = 6;
	
	private static ApartmentDB instance = null;
	private static List<Apartment> aptList;
	private static List<Integer> hashIndex;
	private static char[] streetLetter = {'?','A','B','C','D','E','F','G','H','I','J','K','L','M',
										 'N','O','P','Q','R','S','T','U','V','W','X','Y','Z'};

	public ApartmentDB() throws FileNotFoundException, IOException
	{
		aptList = new ArrayList<Apartment>();
		if(aptList.size() == 0)
		{
			readApartmentDBFromFile(System.getProperty("user.dir") +"/ApartmentDB.csv");
			Collections.sort(aptList, new ApartmentComparator());	//sort region list by street name
			createHashTable();
		}
	}
	
	public static ApartmentDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ApartmentDB();
		
		return instance;
	}
	
	static void createHashTable()
	{
		//build hash index array. Used to quickly search the regions list. Hash is based on first character
		//in street name. Prior to building, sort the region list alphabetically by street name , with street
		//names that start with a digit at the top of the list			
		hashIndex = new ArrayList<Integer>();
		int letterIndex = 1;	//used to iterate the regions array
						
		hashIndex.add(0);	//add the first row to the hash, it's the streets that begin with a number
		while(letterIndex < streetLetter.length)		
		{
			//find street that starts with the current street letter
			int index = 0;
			while(index < aptList.size() && aptList.get(index).getStreetname().charAt(0) != streetLetter[letterIndex])
				index++;
			
			if(index < aptList.size())	//first street found
			hashIndex.add(index++);
			else	//no street found that starts with that letter, set the hashIndex = to it's size
				hashIndex.add(aptList.size());
			
			letterIndex++;
		}
		hashIndex.add(aptList.size());	//add the last index into the hash table
	}
	
	static boolean isAddressAnApartment(String streetNum, String streetName)
	{
		//break streetName into name and type
		String[] streetNameAndType = getStreetNameAndType(streetName);
		
		//determine which part of the apartment list to search based on street name. Street names that start
		//with a digit are first in the apartment list.
		int searchIndex, endIndex;
		System.out.println(String.format("ApartmentDB.isAddressAnApartment: Checking %s %s %s", 
								streetNum, streetNameAndType[0], streetNameAndType[1]));
		if(Character.isDigit(streetName.charAt(0)))
		{	
			searchIndex = 0;
			endIndex = hashIndex.get(1);
		}
		else
		{
			int index = streetName.toUpperCase().charAt(0) - 'A' + 1;	//'A'
			searchIndex = hashIndex.get(index);
			endIndex = hashIndex.get(index+1);
		}

		while(searchIndex < endIndex && !(aptList.get(searchIndex).getStreetname().equalsIgnoreCase(streetNameAndType[0]) &&
										  aptList.get(searchIndex).getHousenum().equals(streetNum)))
			searchIndex++;
				
		//If match not found return false, else return true
		return searchIndex != endIndex;	
	}
	
	void readApartmentDBFromFile(String path) throws FileNotFoundException, IOException
	{		
    	CSVReader reader = new CSVReader(new FileReader(path));
    	String[] nextLine, header;
    		
    	if((header = reader.readNext()) != null)
    	{
    		//Read the ONC CSV File
    		if(header.length == APARTMENTDB_HEADER_LENGTH)
    		{
    			while ((nextLine = reader.readNext()) != null)	// nextLine[] is an array of values from the line
    				aptList.add(new Apartment(nextLine));
    		}
    		else
    			JOptionPane.showMessageDialog(null, "ApartmentDB file corrupted, header length = " + Integer.toString(header.length), 
    						"Invalid ApartmentDB File", JOptionPane.ERROR_MESSAGE);   			
    	}
    	else
			JOptionPane.showMessageDialog(null, "Couldn't read ApartmentDB file, is it empty?: " + path, 
					"Invalid ApartmentDB", JOptionPane.ERROR_MESSAGE);
    	
    	reader.close();
	}
	
	static void addPriorYearApartments(List<ONCFamily> famList)
	{
		//add the prior year addresses that had units that weren't already in the db
		for(ONCFamily f:famList)
		{
			//see if the address is has a unit and  already in the data base
			//if they aren't in the database add them.
			if(f.getUnitNum().trim().length() > 0)
			{
				//it had a unit number in prior year, so need to check to see if it's in the
				//apartment list already.		
				if(!isAddressAnApartment(f.getHouseNum(), f.getStreet()))
				{
					//ots not in the list, we need to add it
					String[] nameAndType = getStreetNameAndType(f.getStreet());
					Apartment newApt = new Apartment(f.getHouseNum(), nameAndType[0], nameAndType[1], 
						f.getUnitNum(), f.getCity(), f.getZipCode());
				
					aptList.add(newApt);
			
					Collections.sort(aptList, new ApartmentComparator());	//sort region list by street name
					createHashTable();
				}
			}
		}
		
		save();
	}
	
	static void save()
	{
		String[] header = {"House #", "Street Name", "Street Type", "Unit #", "City", "Zip Code"};
		
		String path = System.getProperty("user.dir") + "/ApartmentDB.csv";
		File oncwritefile = new File(path);
			
		try 
	    {
	    	CSVWriter writer = new CSVWriter(new FileWriter(oncwritefile.getAbsoluteFile()));
	    	writer.writeNext(header);
	    	
	    	for(Apartment a: aptList)
	    		writer.writeNext(a.getExportRow());	//Write server Apartment row
	    	
	    	writer.close();
	    } 
	    catch (IOException x)
	    {
	    	System.err.format("IO Exception: %s%n", x);
	    }
	}
	
	static String[] getStreetNameAndType(String streetName)
	{
		String[] result = new String[2];
		result[0] = streetName;
		result[1] = "";
		
		//separate the street name and street type. Street type is always at the end
		String[] streetParts = streetName.split(" ");
		if(streetParts.length > 1)	//must have at least two parts - name and type, else return the input
		{
			int partsIndex = 1;
			StringBuilder buff = new StringBuilder(streetParts[0].trim());
			while(partsIndex < streetParts.length-1)
				buff.append(" " + streetParts[partsIndex++].trim());
			
			result[0] = buff.toString();
			result[1] = streetParts[partsIndex];
		}
			
		return result;
	}
	
	private static class ApartmentComparator implements Comparator<Apartment>
	{
		@Override
		public int compare(Apartment apt1, Apartment apt2)
		{
			Integer si1 = Character.getNumericValue(apt1.getStreetname().charAt(0));	//non digit returns -1
			Integer si2 = Character.getNumericValue(apt2.getStreetname().charAt(0));
			
			if(si1 != -1  && si2 != -1)
				return si1.compareTo(si2);
			else if(si1 != -1 && si2 == -1)
				return -1;
			else if(si1 == -1 && si2 != -1)
				return 1;
			else
				return apt1.getStreetname().compareTo(apt2.getStreetname());
		}
	}

}
