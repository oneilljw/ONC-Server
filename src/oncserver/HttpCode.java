package oncserver;

public enum HttpCode 
{
	Ok (200),
	No_Body (204),
	Redirect (301),
	Forbidden (403),
	Method_Not_Allowed (405);
	
	private final int code;
	
	HttpCode(int code)
	{
		this.code = code;
	}
	
	public int code() { return code; }
}
