package oncserver;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

import ourneighborschild.Address;
import ourneighborschild.Region;
import ourneighborschild.School;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import au.com.bytecode.opencsv.CSVReader;

public class ServerRegionDB extends ServerPermanentDB
{
	/**
	 * 
	 */
	private static final int ONC_REGION_HEADER_LENGTH = 13;
	private static final String FILENAME = "Regions_2018.csv";
	
	private static ServerRegionDB instance = null;
	private static List<Region> regionAL;
	private static List<String> zipcodeList;	//list of unique zip codes in region db
	private static String[] regions = {"?", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", 
										"N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"};
	private static List<School> schoolList;
	private ImageIcon oncIcon;
	
	private static List<Integer> hashIndex;
	
	private ServerRegionDB(ImageIcon appicon) throws FileNotFoundException, IOException
	{
		oncIcon = appicon;
		
		//Create the list of elementary schools in ONC's serving area
		schoolList = new ArrayList<School>();
		schoolList.add(new School("A", new Address("15301", "", "", "Lee", "Highway", "", "", "Centreville", "20120"), "Bull Run"));
		schoolList.add(new School("B", new Address("14400", "", "", "New Braddock", "Road", "", "", "Centreville", "20121"), "Centre Ridge"));
		schoolList.add(new School("C", new Address("14330", "", "", "Green Trais", "Blvd", "", "", "Centreville", "20121"), "Centreville"));
		schoolList.add(new School("D", new Address("13340", "", "", "Leland", "Road", "", "", "Centreville", "20120"), "Powell"));
		schoolList.add(new School("E", new Address("13611", "", "", "Springstone", "Drive", "", "", "Clifton", "20124"), "Union Mill"));
		schoolList.add(new School("F", new Address("5301", "", "", "Sully Station", "Drive", "", "", "Centreville", "20120"), "Cub Run"));
		schoolList.add(new School("G", new Address("15450", "", "", "Martins Hundred", "Drive", "", "", "Centreville", "20120"), "Virginia Run"));
		schoolList.add(new School("H", new Address("15109", "", "", "Carlbern", "Drive", "", "", "Centreville", "20120"), "Deer Park"));
		schoolList.add(new School("I", new Address("6100", "", "", "Stone", "Road", "", "", "Centreville", "20120"), "London Towne"));
		schoolList.add(new School("J", new Address("4200", "", "", "Lees Corner", "Road", "", "", "Chantilly", "20151"), "Brookfield"));
		schoolList.add(new School("K", new Address("13006", "", "", "Point Pleasant", "Drive", "", "", "Fairfax", "22033"), "Greenbriar East"));
		schoolList.add(new School("L", new Address("13300", "", "", "Poplar Tree", "Road", "", "", "Fairfax", "22033"), "Greenbriar West"));
		schoolList.add(new School("M", new Address("13500", "", "", "Hollinger", "Avenue", "", "", "Fairfax", "22033"), "Lees Corner"));
		schoolList.add(new School("N", new Address("13440", "", "", "Melville", "Lane", "", "", "Chantilly", "20151"), "Poplar Tree"));
		schoolList.add(new School("O", new Address("5400", "", "", "Willow Springs School", "Road", "", "", "Fairfax", "22030"), "Willow Springs"));
		schoolList.add(new School("P", new Address("3210", "", "", "Kincross", "Circle", "", "", "Herndon", "20171"), "Oak Hill"));
		schoolList.add(new School("Q", new Address("2708", "", "", "Centreville", "Road", "", "", "Herndon", "20171"), "Floris"));
		schoolList.add(new School("R", new Address("2480", "", "", "River Birch", "Drive", "", "", "Herndon", "20171"), "Lutie Lewis Coates"));
		schoolList.add(new School("S", new Address("2499", "", "", "Thomas Jefferson", "Drive", "", "", "Herndon", "20171"), "McNair"));
		
		regionAL = new ArrayList<Region>();

		importDB(String.format("%s/PermanentDB/%s", System.getProperty("user.dir"), FILENAME), "Region DB", ONC_REGION_HEADER_LENGTH);
		nextID = getNextID(regionAL);
		bSaveRequired = false;

		//build zip code list
		zipcodeList = new ArrayList<String>();
		buildZipCodeList();
		
		//build hash index array. Used to quickly search the regions list. Hash is based on first character
		//in street name. Prior to building, sort the region list alphabetically by street name , with street
		//names that start with a digit at the top of the list
		hashIndex = new ArrayList<Integer>();
		buildHashIndex();
	}
	
	void buildHashIndex()
	{
		//build hash index array. Used to quickly search the regions list. Hash is based on first character
		//in street name. Prior to building, sort the region list alphabetically by street name , with street
		//names that start with a digit at the top of the list
		Collections.sort(regionAL, new RegionStreetNameComparator());	//sort region list by street name
		
//		for(Region r : regionAL)
//			if(Character.isDigit(r.getStreetName().charAt(0)) || r.getStreetName().charAt(0) == 'A')
//				System.out.println(String.format("Steetname %s, region %s, zip= %s", 
//						r.getStreetName(), r.getRegion(), r.getZipCode()));
		
		hashIndex.clear();
		int index = 0;	//used to iterate the region list
		int regionIndex = 1;	//used to iterate the regions array
		
		hashIndex.add(0);	//add the first row to the hash: it's the streets that start with a digit
		while(index < regionAL.size() && regionIndex < regions.length)		
		{
			if(regionAL.get(index).getStreetName().charAt(0) == regions[regionIndex].charAt(0))
			{
				hashIndex.add(index++);
				regionIndex++;
			}
			else
				index++;
		}
		
		hashIndex.add(regionAL.size());	//add the last index into the hash table
	}
	
	public static ServerRegionDB getInstance(ImageIcon appicon) throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerRegionDB(appicon);
		
		return instance;
	}
	
	String getRegions()
	{
		if(regions != null)
			return "REGIONS" + "?,A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z";
		else
			return "NO_REGIONS";
	}
	
	/********************************************************************************************
	 * An address for region match requires four parts: Street Number, Street Direction, Street Name 
	 * and Street Suffix. This method takes two parameters, street number and name and determines
	 * the four parts. It is possible the street number is embedded in the streetname parameter and
	 * or is present in the streetnum parameter
	 *********************************************************************************************/
	String getRegionMatch(String addressjson)
	{
		Gson gson = new Gson();
		Address searchAddress = gson.fromJson(addressjson, Address.class);
		
//		String[] searchAddress = createSearchAddress(matchAddress.getStreetNum(),
//													  matchAddress.getStreetName(),
//													   matchAddress.getZipCode());
		
		//Must have a valid street number and street name to search
		if(searchAddress.getStreetNum().isEmpty() || searchAddress.getStreetName().isEmpty())
			return "NO_MATCH" + Integer.toString(0);
		else	
			return "MATCH" + Integer.toString(searchForRegionMatch(searchAddress));		
	}
	
	static HtmlResponse getAddressesJSONP(String zipCode, String callbackFunction)
	{		
		Gson gson = new Gson();
		Type listOfRegions = new TypeToken<ArrayList<Region>>(){}.getType();
		
		List<Region> addressList = new ArrayList<Region>();
		for(Region r : regionAL)
			if(r.getZipCode().equals(zipCode))
				addressList.add(r);

		String response = gson.toJson(addressList, listOfRegions);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	
	static HtmlResponse getZipCodeJSONP(String callbackFunction)
	{		
		Gson gson = new Gson();
		Type listOfZipCodes = new TypeToken<ArrayList<String>>(){}.getType();

		String response = gson.toJson(zipcodeList, listOfZipCodes);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	
	static HtmlResponse getRegionJSONP(String regionID, String callbackFunction)
	{		
		Gson gson = new Gson();
		String response;
		
		int index=0;
		while(index < regionAL.size() && regionAL.get(index).getID() != (Integer.parseInt(regionID)))
			index++;
		
		if(index<regionAL.size())
			response = gson.toJson(regionAL.get(index), Region.class);
		else
			response = "";
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	
	void buildZipCodeList()
	{
		zipcodeList.clear();
		
		for(Region r : regionAL)
			if(!isZipCodeInList(r.getZipCode()))
				zipcodeList.add(r.getZipCode());
		
		Collections.sort(zipcodeList, new ZipCodeComparator());
	}
	
	boolean isZipCodeInList(String zipcode)
	{
		int index = 0;
		while(index < zipcodeList.size() && !zipcodeList.get(index).equals(zipcode))
			index++;
		
		return index < zipcodeList.size();
	}
	
	boolean isSchoolAddress(Address address)
	{
		int index = 0;
		while(index < schoolList.size() && !schoolList.get(index).getAddress().matches(address))
			index++;
		
		return index < schoolList.size();
	}
	
	Region add(Region addedRegion)
	{
		//set the new ID for the region
		addedRegion.setID(nextID++);	//Set id for new region
		
		//add the region to the  database and rebuild the zip code list. Mark for save
		regionAL.add(addedRegion);
		buildHashIndex();
		buildZipCodeList();
		bSaveRequired = true;
		
		return addedRegion;
	}
	
	Region update(Region updateRegion)
	{	
		//Find the position for the current family being replaced
		int index = 0;
		while(index < regionAL.size() && regionAL.get(index).getID() != updateRegion.getID())
			index++;
		
		//If region is located and the updated region has been changed, replace the current 
		//region with the update
		if(index < regionAL.size() && !updateRegion.isRegionMatch(regionAL.get(index)))
		{
			regionAL.set(index, updateRegion);
			bSaveRequired = true;
			return updateRegion;
		}
		else
		{
			return null;
		}
	}	
/*	
	int getRegionMatch(Address matchAddress)
	{
		return searchForRegionMatch(matchAddress);
	}
	
	static String[] createSearchAddress(String streetnum, String streetname, String zipcode)
	{
		String[] searchAddress = new String[5];
		String[] step1 = new String[2];
		
		//Break the street name into its five parts using a three step process. If only the street name
		//or street number are empty, take different paths. 
		if(streetname.length() < 2)
			step1 = separateLeadingDigits(streetnum);
		else
			step1 = separateLeadingDigits(streetname);
		
		String[] step2 = separateStreetDirection(step1[1]);
		String[] step3 = separateStreetSuffix(step2[1]);
		
		searchAddress[0] = step1[0];	//Street Number
		searchAddress[1] = step2[0];	//Street Direction
		searchAddress[2] = step3[0];	//Street Name
		searchAddress[3] = step3[1];	//Street Type
		searchAddress[4] = zipcode;		//zip code
		
		if(searchAddress[0].isEmpty())	//Street number may not be contained in street name
		{
			String[] stnum = separateLeadingDigits(streetnum);
			searchAddress[0] = stnum[0];
		}
		
		return searchAddress;
	}
	
	/*********************************************************************************
	 * This method takes a string and separates the leading digits. If there are characters
	 * after the leading digits without a blank space, it will throw those away. It will
	 * return a two element string array with the leading digits as element 1 and the remainder
	 * of the string past the blank space as element 2. If there are no leading digits, element 1
	 ***********************************************************************************/
/*	
	static String[] separateLeadingDigits(String src)
	{
		String[] output = {"",""};
		
		if(!src.isEmpty())
		{	
			StringBuffer buff = new StringBuffer("");
		
			//IF there are leading digits, separate them and if the first character is a digit
			//and a non digit character is found before a black space, throw the non digit characters away.
			int ci = 0;
			if(Character.isDigit(src.charAt(ci)))
			{	
				while(ci < src.length() && Character.isDigit(src.charAt(ci)))	//Get leading digits
					buff.append(src.charAt(ci++));
		
				while(ci < src.length() && src.charAt(ci++) != ' '); //Throw away end of digit string if necessary	
				
			}
	
			output[0] = buff.toString();
			output[1] = src.substring(ci).trim();
		}

		return output;		
	}
*/	
	/*********************************************************************************
	 * This method takes a string and separates the street direction. If the first character
	 * is a N or a S and and the second character is a period or a blank space, a direction
	 * is present. The method will return a two element string array with the direction
	 * character as element 0 and the remainder of the string as element 1. If a street
	 * direction is not present, element 0 will be empty and element 1 will contain the 
	 * original street parameter

	 ***********************************************************************************/
/*	
	static String[] separateStreetDirection(String street)
	{
		String[] output = new String[2];
		
		if(!street.isEmpty() && (street.charAt(0) == 'N' || street.charAt(0) == 'S') &&
			 (street.charAt(1) == '.' || street.charAt(1) == ' '))
		{
			output[0] = Character.toString(street.charAt(0));
			output[1] = street.substring(2);
		}
		else
		{
			output[0] ="";
			output[1] = street;
		}
	
		return output;
	}
*/	
/*	
	static String[] separateStreetSuffix(String streetname)
	{
		StringBuffer buf = new StringBuffer("");
		
		String[] stnameparts = streetname.split(" ");
		
		int index = 0;
		while(index < stnameparts.length-1)
			buf.append(stnameparts[index++] + " ");
		
		String[] output = {buf.toString().trim(), stnameparts[index].trim()};
		return output;
	}
*/	
	static int searchForRegionMatch(Address searchAddress)
	{	
		//determine which part of the region list to search based on street name. Street names that start
		//with a digit are first in the region list. searchAddres[2] is the street name
		int searchIndex, endIndex;
		
		if(searchAddress.getStreetName().isEmpty())
			return 0;	//if no street name, can't find a region.
		else
		{
			if(Character.isDigit(searchAddress.getStreetName().charAt(0)))
			{	
				searchIndex = 0;
				endIndex = hashIndex.get(1);
			}
			else
			{
				int index = Character.toUpperCase(searchAddress.getStreetName().charAt(0)) - 'A' + 1;	//'A'
				searchIndex = hashIndex.get(index);
				endIndex = hashIndex.get(index+1);
			}

			while(searchIndex < endIndex && !regionAL.get(searchIndex).isRegionMatch(searchAddress))
				searchIndex++;
		
			//If match not found return region = 0, else return region
			return searchIndex == endIndex ? 0 : getRegionNumber(regionAL.get(searchIndex).getRegion());
		}
	}
	
	static boolean isAddressValid(Address chkAddress)
	{
		return searchForRegionMatch(chkAddress) > 0;		
	}
	
	static int getRegionNumber(String r) //Returns 0 if r is null or empty, number corresponding to letter otherwise
	{
		int index = 0;
		if(r != null && !r.isEmpty())
			while (index < regions.length && !r.equals(regions[index]))
				index++;
		
		return index;
	}
	
	static String getRegion(int regnum)
	{
		String region = "?";
		
		if(regnum > 0 && regnum < regions.length)
			region = regions[regnum];
		
		return region;
	}
	
	void getONCRegions(String path) throws FileNotFoundException, IOException
	{		
    	CSVReader reader = new CSVReader(new FileReader(path));
    	String[] nextLine, header;
    		
    	if((header = reader.readNext()) != null)
    	{
    		//Read the ONC CSV File
    		if(header.length == ONC_REGION_HEADER_LENGTH)
    		{
    			while ((nextLine = reader.readNext()) != null)	// nextLine[] is an array of values from the line
    				regionAL.add(new Region(nextLine));
    		}
    		else
    			JOptionPane.showMessageDialog(null, "Regions file corrupted, header length = " + Integer.toString(header.length), 
    						"Invalid Region File", JOptionPane.ERROR_MESSAGE, oncIcon);   			
    	}
    	else
			JOptionPane.showMessageDialog(null, "Couldn't read file, is it empty?: " + path, 
					"Invalid Region File", JOptionPane.ERROR_MESSAGE, oncIcon);
    	
    	reader.close();
	}
	
	int getNumberOfRegions() { return regions.length; }
	boolean isRegionValid(int region) { return region >=0 && region < regions.length; }
	
	@Override
	void addObject(String[] nextLine) 
	{
		regionAL.add(new Region(nextLine));
	}
	
	@Override
	String[] getExportHeader()
	{
		return new String[] {"ID","Street Name","Street Type","Region","School Region",
							"School","SchoolAddress?","Street Dir","Street Post Dir",
							"Address Low","Address High","Odd Even","Zip Code"};
	}
	
	@Override
	String getFileName() { return FILENAME; }
	
	@Override
	List<Region> getONCObjectList() { return regionAL; }
	
	private static class RegionStreetNameComparator implements Comparator<Region>
	{
		@Override
		public int compare(Region region1, Region region2)
		{
			String sn1 = region1.getStreetName();
			String sn2 = region2.getStreetName();
			
//			Integer si1 = Character.getNumericValue(streetname1.charAt(0));	//non digit returns -1
//			Integer si2 = Character.getNumericValue(streetname2.charAt(0));
			
			if(Character.isLetter(sn1.charAt(0)) && Character.isLetter(sn2.charAt(0)))
				return sn1.compareTo(sn2);
			else if(Character.isDigit(sn1.charAt(0)) && Character.isLetter(sn2.charAt(0)))
				return -1;
			else if(Character.isLetter(sn1.charAt(0)) && Character.isDigit(sn2.charAt(0)))
				return 1;
			else if(Character.isDigit(sn1.charAt(0)) && Character.isDigit(sn2.charAt(0)))
				return getValue(sn1).compareTo(getValue(sn2));	
			else
				return 0;
				
//			Integer si1 = Character.getNumericValue(sn1.charAt(0));	//non digit returns -1
//			Integer si2 = Character.getNumericValue(sn2.charAt(0));
//			
//			if(si1 != -1  && si2 != -1)
//				return si1.compareTo(si2);
//			else if(si1 != -1 && si2 == -1)
//				return -1;
//			else if(si1 == -1 && si2 != -1)
//				return 1;
//			else
//				return sn1.compareTo(sn2);
		}
		
		Integer getValue(String s)
		{
			int index = 0;
			while(index < s.length() && Character.isDigit(s.charAt(index)))
				index++;
			
			if(index < s.length())
				return Integer.parseInt(s.substring(0, index));
			else
				return 0;
		}
	}
	
	private static class ZipCodeComparator implements Comparator<String>
	{
		@Override
		public int compare(String z1, String z2)
		{
			return z1.compareTo(z2);
		}
	}

	@Override
	String add(String userjson) {
		// TODO Auto-generated method stub
		return null;
	}
}
