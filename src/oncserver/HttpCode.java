package oncserver;

public enum HttpCode 
{
	Ok (200),
	No_Body (204),
	Partial_Content (206),
	Redirect (301),
	Invalid (400),
	Forbidden (403),
	Method_Not_Allowed (405);
	
	private final int code;
	
	HttpCode(int code)
	{
		this.code = code;
	}
	
	public int code() { return code; }
}
