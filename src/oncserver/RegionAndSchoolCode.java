package oncserver;

public class RegionAndSchoolCode
{
	private int region;
	private String schoolCode;
	private int errorCode;
		
	RegionAndSchoolCode(int reg, String sc, int errCode)
	{
		this.region = reg;
		this.schoolCode = sc;
		this.errorCode = errCode;
	}
	
	//getters
	int getRegion() { return region; }
	String getSchoolCode() { return schoolCode; }
	int getErrorCode() { return errorCode; }
}
