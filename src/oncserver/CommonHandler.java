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
			sendCachedFile(t, "image/gif", "oncsplash", false);
		else if(requestURI.contains("/trustlogo"))
			sendCachedFile(t, "image/png", "trustlogo", false);
		else if(requestURI.contains("/erroricon"))
			sendCachedFile(t, "image/png", "erroricon", false);
		else if(requestURI.contains("/checkmarkicon"))
			sendCachedFile(t, "image/png", "checkmarkicon", false);
		else if(requestURI.contains("/clearx"))
			sendCachedFile(t, "image/gif", "clearx", false);
		else if(requestURI.contains("/onclogo"))
			sendCachedFile(t, "image/gif", "onclogo", false);
		else if(requestURI.contains("/favicon.ico"))
			sendCachedFile(t, "image/x-icon ", "oncicon", false);
		else if(requestURI.contains("/vanilla.ttf"))
			sendCachedFile(t, "application/octet-stream", "vanilla", false);
		else if(requestURI.contains("/oncstylesheet"))
			sendCachedFile(t, "text/css", "oncstylesheet", false);
		else if(requestURI.contains("/oncdialogstylesheet"))
			sendCachedFile(t, "text/css", "oncdialogstylesheet", false);
		else if(requestURI.contains("/oncloginstylesheet"))
			sendCachedFile(t, "text/css", "oncloginstylesheet", false);
		else if(requestURI.contains("/jquery.js"))
			sendCachedFile(t, "text/javascript", "jquery", false);
	}
}
