package oncserver;

public enum HttpCode 
{
	Ok (200),
	Redirect (301),
	Forbidden (403);
	
	private final int code;
	
	HttpCode(int code)
	{
		this.code = code;
	}
	
	public int code() { return code; }
}
