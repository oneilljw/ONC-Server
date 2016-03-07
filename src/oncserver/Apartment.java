package oncserver;

import java.util.ArrayList;
import java.util.List;

import ourneighborschild.ONCObject;

public class Apartment
{
	private String housenum;
	private String streetname;
	private String streettype;
	private String unit;
	private String city;
	private String zip;
	
	public Apartment(String housenum, String streetname, String streettype, String unit, String city, String zip) 
	{
		this.housenum = housenum;
		this.streetname = streetname;
		this.streettype = streettype;
		this.unit = unit;
		this.city = city;
		this.zip = zip;
	}
	
	public Apartment(String[] file_line)
	{
		this.housenum = file_line[0];
		this.streetname = file_line[1];
		this.streettype = file_line[2];
		this.unit = file_line[3];
		this.city = file_line[4];
		this.zip = file_line[5];
	}
	
	//getters
	String getHousenum() { return housenum; }
	String getStreetname() { return streetname; }
	String getStreettype() { return streettype; }
	String getUnit() { return unit; }
	String getCity() { return city; }
	String getZip() { return zip; }
	
	//setters
	void setHousenum(String s) { this.housenum = s; }
	void setStreetname(String s) { this.streetname = s; }
	void setStreettype(String s) { this.streettype = s; }
	void setUnit(String s) { this.unit = s; }
	void setCity(String s) { this.city = s; }
	void setZip(String s) { this.zip = s; }
	
	public String[] getExportRow() 
	{
		List<String> apartmentList = new ArrayList<String>();
		apartmentList.add(housenum);
		apartmentList.add(streetname);
		apartmentList.add(streettype);
		apartmentList.add(unit);
		apartmentList.add(city);
		apartmentList.add(zip);
		
		return apartmentList.toArray(new String[apartmentList.size()]);
	}
}
