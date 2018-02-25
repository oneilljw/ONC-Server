package oncserver;

import java.net.HttpCookie;

public class HtmlResponse 
{
	private String htmlResponse;
	private HttpCode httpCode;
	private HttpCookie httpCookie;
	
	//used to provide a cookie to the user agent
	HtmlResponse(String response, HttpCode code, HttpCookie cookie)
	{
		this.htmlResponse = response;
		this.httpCode = code;
		this.httpCookie = cookie;
	}
	
	//used when a new cookie isn't required
	HtmlResponse(String response, HttpCode code)
	{
		this.htmlResponse = response;
		this.httpCode = code;
		this.httpCookie = null;
	}
	
	String getResponse() { return htmlResponse; }
	HttpCode getResponseCode() { return httpCode; }
	int getCode() { return httpCode.code(); }
	HttpCookie getCookie() { return httpCookie; }
	
	void append(String html_add) { htmlResponse += html_add; }
}
