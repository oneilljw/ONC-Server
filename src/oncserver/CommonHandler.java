package oncserver;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class CommonHandler extends ONCHandlerServices implements HttpHandler 
{

	@Override
	public void handle(HttpExchange t) throws IOException 
	{
//		Set<String> keyset = params.keySet();
//		for(String key:keyset)
//			System.out.println(String.format("uri=%s, key=%s, value=%s", t.getRequestURI().toASCIIString(), key, params.get(key)));
    	
    	String requestURI = t.getRequestURI().toASCIIString();
    	
    	String mssg = String.format("HTTP request %s: %s:%s", t.getRemoteAddress().toString(), t.getRequestMethod(), requestURI);
		ServerUI.getInstance().addLogMessage(mssg);
		
		if(requestURI.contains("/oncsplash"))
    	{
    		sendFile(t, "image/gif", "oncsplash.gif");
    	}
    	else if(requestURI.contains("/clearx"))
    	{
    		sendFile(t, "image/gif", "clear_x.gif");
    	}
    	else if(requestURI.contains("/onclogo"))
    	{
    		sendFile(t, "image/gif", "oncsplash.gif");    	
    	}
    	else if(requestURI.contains("/favicon.ico"))
    	{
    		sendFile(t, "image/x-icon ", "ONC.ico");    	
    	}   	
    	else if(requestURI.contains("/vanilla.ttf"))
    	{
    		sendFile(t, "application/octet-stream", "vanilla.ttf");  	
    	}
    	else if(requestURI.contains("/oncstylesheet"))
    	{
    		sendFile(t, "text/css", "ONCStyleSheet.css");
    	}
    	else if(requestURI.contains("/oncdialogstylesheet"))
    	{
    		sendFile(t, "text/css", "ONCDialogStyleSheet.css");
    	}
    	else if(requestURI.contains("/jquery.js"))
    	{
    		sendFile(t, "text/javascript", "jquery-1.11.3.js");
    	}
	}
}
