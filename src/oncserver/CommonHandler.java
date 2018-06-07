package oncserver;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsExchange;

public class CommonHandler extends ONCWebpageHandler
{
	@Override
	public void handle(HttpExchange te) throws IOException 
	{
		HttpsExchange t = (HttpsExchange) te;
		String requestURI = t.getRequestURI().toASCIIString();
    	
		String mssg = String.format("HTTP request %s: %s:%s", t.getRemoteAddress().toString(), t.getRequestMethod(), requestURI);
		ServerUI.getInstance().addLogMessage(mssg);

		if(requestURI.contains("/oncsplash"))
			sendFile(t, "image/gif", "oncsplash");
		else if(requestURI.contains("/clearx"))
			sendCachedFile(t, "image/gif", "clearx");
		else if(requestURI.contains("/onclogo"))
			sendCachedFile(t, "image/gif", "onclogo");
		else if(requestURI.contains("/favicon.ico"))
			sendCachedFile(t, "image/x-icon ", "oncicon");
		else if(requestURI.contains("/vanilla.ttf"))
			sendCachedFile(t, "application/octet-stream", "vanilla");
		else if(requestURI.contains("/oncstylesheet"))
			sendCachedFile(t, "text/css", "oncstylesheet");
		else if(requestURI.contains("/oncdialogstylesheet"))
			sendCachedFile(t, "text/css", "oncdialogstylesheet");
		else if(requestURI.contains("/oncloginstylesheet"))
			sendCachedFile(t, "text/css", "oncloginstylesheet");
		else if(requestURI.contains("/jquery.js"))
			sendCachedFile(t, "text/javascript", "jquery");
	}
}
