package oncserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.JOptionPane;

import ourneighborschild.Address;
import ourneighborschild.Region;
import ourneighborschild.School;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import au.com.bytecode.opencsv.CSVReader;
import ourneighborschild.GoogleGeocode;
import ourneighborschild.ONCUser;

public class ServerRegionDB extends ServerPermanentDB
{
	/**
	 * 
	 */
	private static final int REGION_HEADER_LENGTH = 13;
	private static final String REGION_FILENAME = "Regions_New.csv";
	private static final String REGION_DB_NAME = "RegionDB";
	
	private static final int SCHOOL_DB_HEADER_LENGTH = 15;
	private static final String SCHOOL_FILENAME = "ServedSchoolsDB.csv";
	private static final String SCHOOL_DB_NAME = "ServedSchoolsDB";
	
	private static final int ZIPCODE_HEADER_LENGTH = 1;
	private static final String ZIPCODE_FILENAME = "ServedZipcodesDB.csv";
	private static final String ZIPCODE_DB_NAME = "ServedZipcodesDB";
	
	private static final String GEOCODE_URL = "https://maps.googleapis.com/maps/api/geocode/json?address=";
	private static final String MNR_URL = "http://www.fairfaxcounty.gov/FFXGISAPI/v1/report?location=%s&format=json";

	private static ServerRegionDB instance = null;
	private static List<Region> regionAL;
	private static List<String> zipcodeList;	//list of unique zip codes in region db
	private static String[] regions = {"?", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", 
										"N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"};
	private static List<String> servedZipCodesList;
	private static List<School> schoolList;
	
	private static List<Integer> hashIndex;
	
	private ServerRegionDB() throws FileNotFoundException, IOException
	{
		//Create the list of served zip codes
		servedZipCodesList = new ArrayList<String>();
		importDB(String.format("%s/PermanentDB/%s", System.getProperty("user.dir"), ZIPCODE_FILENAME), ZIPCODE_DB_NAME, ZIPCODE_HEADER_LENGTH);
//		servedZipCodesList.add("20120");
//		servedZipCodesList.add("20121");
//		servedZipCodesList.add("20124");
//		servedZipCodesList.add("20151");
//		servedZipCodesList.add("22033");
//		servedZipCodesList.add("22039");
//		saveZipcodeListToFile();
				
		//Create the list of elementary schools in ONC's serving area
		schoolList = new ArrayList<School>();
		importDB(String.format("%s/PermanentDB/%s", System.getProperty("user.dir"), SCHOOL_FILENAME), SCHOOL_DB_NAME, SCHOOL_DB_HEADER_LENGTH);
//		schoolList.add(new School("A", new Address("15301", "", "", "Lee", "Highway", "", "", "Centreville", "20120"), "Bull Run", "38.828835,-77.475749"));
//		schoolList.add(new School("B", new Address("14400", "", "", "New Braddock", "Road", "", "", "Centreville", "20121"), "Centre Ridge", "38.826088,-77.445725"));
//		schoolList.add(new School("C", new Address("14330", "", "", "Green Trais", "Blvd", "", "", "Centreville", "20121"), "Centreville", "38.820056,-77.440809"));
//		schoolList.add(new School("D", new Address("13340", "", "", "Leland", "Road", "", "", "Centreville", "20120"), "Powell", "38.846304,-77.407887"));
//		schoolList.add(new School("E", new Address("13611", "", "", "Springstone", "Drive", "", "", "Clifton", "20124"), "Union Mill", "38.820727,-77.417579"));
//		schoolList.add(new School("F", new Address("5301", "", "", "Sully Station", "Drive", "", "", "Centreville", "20120"), "Cub Run", "38.865949,-77.458535"));
//		schoolList.add(new School("G", new Address("15450", "", "", "Martins Hundred", "Drive", "", "", "Centreville", "20120"), "Virginia Run", "38.852333,-77.485570"));
//		schoolList.add(new School("H", new Address("15109", "", "", "Carlbern", "Drive", "", "", "Centreville", "20120"), "Deer Park", "38.855992,-77.470855"));
//		schoolList.add(new School("I", new Address("6100", "", "", "Stone", "Road", "", "", "Centreville", "20120"), "London Towne", "38.839880,-77.456534"));
//		schoolList.add(new School("J", new Address("4200", "", "", "Lees Corner", "Road", "", "", "Chantilly", "20151"), "Brookfield", "38.882490,-77.419328"));
//		schoolList.add(new School("K", new Address("13006", "", "", "Point Pleasant", "Drive", "", "", "Fairfax", "22033"), "Greenbriar East", "38.872170,-77.394355"));
//		schoolList.add(new School("L", new Address("13300", "", "", "Poplar Tree", "Road", "", "", "Fairfax", "22033"), "Greenbriar West", "38.876560,-77.405355"));
//		schoolList.add(new School("M", new Address("13500", "", "", "Hollinger", "Avenue", "", "", "Fairfax", "22033"), "Lees Corner", "38.890636,-77.411140"));
//		schoolList.add(new School("N", new Address("13440", "", "", "Melville", "Lane", "", "", "Chantilly", "20151"), "Poplar Tree", "38.863387,-77.414740"));
//		schoolList.add(new School("O", new Address("5400", "", "", "Willow Springs School", "Road", "", "", "Fairfax", "22030"), "Willow Springs", "38.832115,-77.379740"));
//		schoolList.add(new School("P", new Address("3210", "", "", "Kincross", "Circle", "", "", "Herndon", "20171"), "Oak Hill", "38.913202,-77.408478"));
//		schoolList.add(new School("Q", new Address("2708", "", "", "Centreville", "Road", "", "", "Herndon", "20171"), "Floris", "38.936122,-77.414987"));
//		schoolList.add(new School("R", new Address("2480", "", "", "River Birch", "Road", "", "", "Herndon", "20171"), "Lutie Lewis Coates", "38.952190,-77.419300"));
//		schoolList.add(new School("S", new Address("2499", "", "", "Thomas Jefferson", "Drive", "", "", "Herndon", "20171"), "McNair", "38.946508,-77.404669"));
//		saveSchoolListToFile();

		regionAL = new ArrayList<Region>();
		importDB(String.format("%s/PermanentDB/%s", System.getProperty("user.dir"), REGION_FILENAME), REGION_DB_NAME, REGION_HEADER_LENGTH);
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
	
	public static ServerRegionDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerRegionDB();
		
		return instance;
	}
	
	String getRegions()
	{
		if(regions != null)
			return "REGIONS" + "?,A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z";
		else
			return "NO_REGIONS";
	}
	
	String getServedSchools()
	{
		Gson gson = new Gson();
		Type listOfSchools = new TypeToken<ArrayList<School>>(){}.getType();
		
		String response = gson.toJson(schoolList, listOfSchools);
		return response;	
	}
	
	/********************************************************************************************
	 * An address for region match requires four parts: Street Number, Street Direction, Street Name 
	 * and Street Suffix. This method takes two parameters, street number and name and determines
	 * the four parts. It is possible the street number is embedded in the street name parameter and
	 * or is present in the street number parameter
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
		{
			RegionAndSchoolCode rSC = searchForRegionMatch(searchAddress);
			return "MATCH" + Integer.toString(rSC.getRegion());
		}
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
	
	static HtmlResponse getSchoolsJSONP(String callbackFunction)
	{		
		Gson gson = new Gson();
		Type listOfSchools = new TypeToken<ArrayList<School>>(){}.getType();
		
		List<School> elementarySchoolList = new ArrayList<School>();
		
		for(School school : schoolList)
			if(!school.getCode().isEmpty())
				elementarySchoolList.add(school);

		String response = gson.toJson(elementarySchoolList, listOfSchools);
		
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
	
	String getSchoolRegion(String schoolName, String zipcode)
	{
		int index = 0;
		while(index < schoolList.size() && 
			  !(schoolList.get(index).getName().equalsIgnoreCase(schoolName) && 
				 schoolList.get(index).getAddress().getZipCode().equals(zipcode)))
			index++;
		
		if(index < schoolList.size())
			return schoolList.get(index).getCode();
		else if(isZipCodeServed(zipcode))
			return "Y";
		else
			return "Z";
	}
	
	School getSchoolByCode(String code)
	{
		int index=0;
		while(index < schoolList.size() && !schoolList.get(index).getCode().contentEquals(code))
			index++;
		
		return index < schoolList.size() ? schoolList.get(index) : null;
	}
	
	boolean isZipCodeServed(String zipcode)
	{
		int index = 0;
		while(index < servedZipCodesList.size() && !servedZipCodesList.get(index).equals(zipcode))
			index++;
		
		return index < servedZipCodesList.size();
	}
	
	Region add(Region addedRegion)
	{
		//set the new ID for the region
		addedRegion.setID(nextID++);	//Set id for new region
		
		//need to determine lat/long and Elementary School for region.
		String streetname = addedRegion.getStreetName().trim().replaceAll(" ", "+");
		String address = String.format("%d+%s+%s", addedRegion.getAddressNumLow(), streetname, 
													addedRegion.getZipCode());
		
		//Need to add error processing here. Can't add a street without it's lat/long, and school.
		GoogleGeocode geocode = getGoogleGeocode(address);
		FCSchool fcSchool = null;
		if(geocode != null)
		{
			String latlong = geocode.getGeocode();
			if((fcSchool = getSchool(latlong)) != null)
			{
				//add the location, school and school region to the added Region object
				addedRegion.setLocation(latlong);
				
				String schoolName = toTitleCase(fcSchool.getName());
				addedRegion.setSchool(schoolName);
				addedRegion.setSchoolRegion(getSchoolRegion(schoolName, addedRegion.getZipCode()));
				
				//add the region to the  database and rebuild the zip code list. Mark for save
				regionAL.add(addedRegion);
				buildHashIndex();
				buildZipCodeList();
				bSaveRequired = true;
				
//				System.out.println(String.format("ServRegDB.add: Added %s", addedRegion.getPrintalbeRegion()));
			}
		}
		
		return geocode == null ? null : fcSchool == null ? null : addedRegion;
	}
	
	Region update(Region updateRegion)
	{	
		//Find the position for the current region being replaced
		int index = 0;
		while(index < regionAL.size() && regionAL.get(index).getID() != updateRegion.getID())
			index++;
		
		//If region is located and the updated region has been changed, replace the current 
		//region with the update
		if(index < regionAL.size() && !updateRegion.isRegionMatch(regionAL.get(index)))
		{
			
			//lat/long and elementary school may have changed, need to update them as well
			//Need to add error processing here. Can't add a street without it's lat/long, and school.
			String streetname = updateRegion.getStreetName().trim().replaceAll(" ", "+");
			String address = String.format("%d+%s+%s", updateRegion.getAddressNumLow(), streetname, 
														updateRegion.getZipCode());
			GoogleGeocode geocode = getGoogleGeocode(address);
			FCSchool fcSchool = null;
			if(geocode != null)
			{
				String latlong = geocode.getGeocode();
				if((fcSchool = getSchool(latlong)) != null)
				{
					//add the location, school and school region to the added Region object
					updateRegion.setLocation(latlong);
					
					String schoolName = toTitleCase(fcSchool.getName());
					updateRegion.setSchool(schoolName);
					updateRegion.setSchoolRegion(getSchoolRegion(schoolName, updateRegion.getZipCode()));
					
					//add the region to the  database and rebuild the zip code list. Mark for save
					regionAL.set(index, updateRegion);
					buildHashIndex();
					buildZipCodeList();
					bSaveRequired = true;
					
					System.out.println(String.format("ServRegDB.add: Added %s", updateRegion.getPrintalbeRegion()));
					
					return updateRegion;
				}
				else
					return null;
			}
			else
				return null;
		}
		else
			return null;
	}	

	/***
	 * Searches the region DB for a match to the search address. Possible outcomes are:
	 * If the address is found, therefore it's a valid Fairfax County address for ONC. In that case 
	 * the region number and school code are returned. The region still may be 26 ("Z") and
	 * the school code might be that as well.
	 * If the address is not found. In that case region 0 and school code Z are returned.
	 * @param searchAddress
	 * @return
	 */
	static RegionAndSchoolCode searchForRegionMatch(Address searchAddress)
	{	
		//determine which part of the region list to search based on street name. Street names that start
		//with a digit are first in the region list.
		int searchIndex, endIndex;
		
		if(searchAddress.getStreetName().isEmpty())
			return new RegionAndSchoolCode(0, "Z", 1);	//if no street name, can't find a region or school code
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
		
			//If match not found return region = 0, else return region and school code
			if(searchIndex == endIndex)
				return new RegionAndSchoolCode(0, "Z", 1);
			else
				return new RegionAndSchoolCode(getRegionNumber(regionAL.get(searchIndex).getRegion()),
												regionAL.get(searchIndex).getSchoolRegion(), 1);
		}
	}
	
	static boolean isAddressValid(Address chkAddress)
	{
		return searchForRegionMatch(chkAddress).getRegion() > 0;		
	}
	
	static boolean isAddressServedSchool(Address ckAddress)
	{
		int index = 0;
		while(index < schoolList.size() && !schoolList.get(index).getAddress().matches(ckAddress))
			index++;
		
		return index < schoolList.size();
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
    		if(header.length == REGION_HEADER_LENGTH)
    		{
    			while ((nextLine = reader.readNext()) != null)	// nextLine[] is an array of values from the line
    				regionAL.add(new Region(nextLine));
    		}
    		else
    			JOptionPane.showMessageDialog(null, "Regions file corrupted, header length = " + Integer.toString(header.length), 
    						"Invalid Region File", JOptionPane.ERROR_MESSAGE);   			
    	}
    	else
			JOptionPane.showMessageDialog(null, "Couldn't read file, is it empty?: " + path, 
					"Invalid Region File", JOptionPane.ERROR_MESSAGE);
    	
    	reader.close();
	}
	
	int getNumberOfRegions() { return regions.length; }
	boolean isRegionValid(int region) { return region >=0 && region < regions.length; }
	
	//Get a geo code location from Google Maps 
	GoogleGeocode getGoogleGeocode(String address)
	{
		//Turn the string into a valid URL
		URL dirurl= null;
		try 
		{
			dirurl = new URL(GEOCODE_URL + address + "&key=" + ServerEncryptionManager.getKey("key1"));
		} 
		catch (MalformedURLException e2) 
		{
			e2.printStackTrace();
		}
					
		//Attempt to open the URL via a network connection to the Internet
		HttpURLConnection httpconn= null;
		try {httpconn = (HttpURLConnection)dirurl.openConnection();} 
		catch (IOException e1) {e1.printStackTrace();}
					
		//It opened successfully, get the data
		StringBuffer response = new StringBuffer();
		try 
		{
			if(httpconn.getResponseCode() == HttpURLConnection.HTTP_OK)
			{
				BufferedReader input = new BufferedReader(new InputStreamReader(httpconn.getInputStream()),8192);
				String strLine = null;
				while ((strLine = input.readLine()) != null)
					response.append(strLine);					
				input.close();
			}
		}
		catch (IOException e1)
		{
			ServerUI.addDebugMessage(String.format("Can't get Google Geocode Location for %s", address));
			response = null;
		}
		
		Gson gson = new Gson();
		return response != null ? gson.fromJson(response.toString(), GoogleGeocode.class) : null;
	}
	
	//location is geocode (lat/long) from Google Maps 
	FCSchool getSchool(String location)
	{
		//remove any white space from location
		String urllocation = location.replaceAll(" ", "");
		//Turn the string into a valid URL
		URL mnrurl = null;
		try 
		{
			mnrurl = new URL(String.format(MNR_URL, urllocation));
//			System.out.println("MNR URL: " + mnrurl.toString());
		} 
		catch (MalformedURLException e2) 
		{
			e2.printStackTrace();
		}
							
		//Attempt to open the URL via a network connection to the Internet
		HttpURLConnection httpconn= null;
		try 
		{
			httpconn = (HttpURLConnection) mnrurl.openConnection();
		} 
		catch (IOException e1)
		{
			e1.printStackTrace();
		}
							
		//It opened successfully, get the data
		StringBuffer response = new StringBuffer();
		try 
		{
			if(httpconn.getResponseCode() == HttpURLConnection.HTTP_OK)
			{
				BufferedReader input = new BufferedReader(new InputStreamReader(httpconn.getInputStream()), 16384);
				String strLine = null;
				while ((strLine = input.readLine()) != null)
					response.append(strLine);					
				input.close();
			}
			else
				System.out.println("Response code not 200, it was: " + httpconn.getResponseCode());
		}
		catch (IOException e1)
		{
			return null;
		}
				
		//find the elementary school json in the response
		int esIndex = response.indexOf("elementaryschool");
		if(esIndex > -1)
		{	
			int esJsonStart = response.indexOf("{", esIndex);
			int esJsonEnd = response.indexOf("}", esJsonStart);
//			System.out.println(String.format("esStart= %d, jsonStart= %d, jsonEnd= %d",  esIndex, esJsonStart, esJsonEnd));
			String esJson = response.substring(esJsonStart, esJsonEnd+1);
//			System.out.println("ES Json: " + esJson);
			Gson gson = new Gson();
			FCSchool elemSchool = gson.fromJson(esJson, FCSchool.class);
//			System.out.println("ES in Json: " + elemSchool.getName());;
				
			return elemSchool;
		}
		else
			return null;
	}	
	
	@Override
	void addObject(String type, String[] nextLine) 
	{
		if(type.equals(REGION_DB_NAME))
			regionAL.add(new Region(nextLine));
		else if(type.equals(SCHOOL_DB_NAME))
			schoolList.add(new School(nextLine));
		else if(type.equals(ZIPCODE_DB_NAME))
			servedZipCodesList.add(nextLine[0]);
	}
	
	@Override
	String[] getExportHeader()
	{
		return new String[] {"ID","Street Name","Street Type","Region","School Region",
							"School","Location","Street Dir","Street Post Dir",
							"Address Low","Address High","Odd Even","Zip Code"};
	}
	
	@Override
	String getFileName() { return REGION_FILENAME; }
	
	@Override
	List<Region> getONCObjectList() { return regionAL; }
		
	public static void WriteStringToFile(String string)
	{	
		String pathname = String.format("%s/PermanentDB/%s", System.getProperty("user.dir"), "FCPSBoundaryLocatorHTML.txt");
		try 
		{
			File file = new File(pathname);
			FileWriter fileWriter = new FileWriter(file);
			fileWriter.write(string);
			fileWriter.flush();
			fileWriter.close();
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}
	}
	
	static String readFile() throws IOException 
	{
		String pathname = String.format("%s/PermanentDB/%s", System.getProperty("user.dir"), "FCPSBoundaryLocatorHTML.txt");
		byte[] encoded = Files.readAllBytes(Paths.get(pathname));
		return new String(encoded, Charset.defaultCharset());
	}
	
	private static class RegionStreetNameComparator implements Comparator<Region>
	{
		@Override
		public int compare(Region region1, Region region2)
		{
			String sn1 = region1.getStreetName();
			String sn2 = region2.getStreetName();
			
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
	String add(String userjson, ONCUser client) {
		// TODO Auto-generated method stub
		return null;
	}
/*	
	void saveSchoolListToFile()
	{
		String path = String.format("%s/PermanentDB/%s",System.getProperty("user.dir"), "Served Schools.csv");
		File oncwritefile = new File(path);
		String[] header = {"School Code", "Street #", "# Suffix", "Dir", "Street Name", "Type", "Post Dir", 
				"Unit", "City", "Zipcode", "School Name", "Lat/Long"};
		
		try 
		{
			CSVWriter writer = new CSVWriter(new FileWriter(oncwritefile.getAbsoluteFile()));
			writer.writeNext(header);
	    	
			for(School sch : schoolList)
				writer.writeNext(sch.getExportRow());
	    	
			writer.close();
		} 
		catch (IOException x)
		{
			System.err.format("IO Exception: %s%n", x.getMessage());
		}
	}
	
	void saveZipcodeListToFile()
	{
		String path = String.format("%s/PermanentDB/%s",System.getProperty("user.dir"), "Served Zipcodes.csv");
		File oncwritefile = new File(path);
		String[] header = {"Zipcode"};
		
		try 
		{
			CSVWriter writer = new CSVWriter(new FileWriter(oncwritefile.getAbsoluteFile()));
			writer.writeNext(header);
			
			for(String zc : servedZipCodesList)
			{
				String[] line = new String[1];
				line[0] = zc;
				writer.writeNext(line);
			}
	    	
			writer.close();
		} 
		catch (IOException x)
		{
			System.err.format("IO Exception: %s%n", x.getMessage());
		}
	}
*/	
	private class FCSchool
	{		
		private String label;
		private String name;
		@SuppressWarnings("unused")
		private String retrieveURL;
		@SuppressWarnings("unused")
		private String url;
		@SuppressWarnings("unused")
		private String point;
		@SuppressWarnings("unused")
		private int distance;
		@SuppressWarnings("unused")
		private String address;
		@SuppressWarnings("unused")
		private String city;
		@SuppressWarnings("unused")
		private String zip;
		@SuppressWarnings("unused")
		private int schoolnumber;
		
		String getName() { return name.isEmpty() ? label : name; }
	}
}
