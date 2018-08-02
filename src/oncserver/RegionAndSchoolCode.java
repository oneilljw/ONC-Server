package oncserver;

public class RegionAndSchoolCode
{
	private int region;
	private String schoolCode;
		
	RegionAndSchoolCode(int reg, String sc)
	{
		this.region = reg;
		this.schoolCode = sc;
	}
		
	int getRegion() { return region; }
	String getSchoolCode() { return schoolCode; }
}
