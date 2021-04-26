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
		ServerUI.getInstance().addUIAndLogMessage(mssg);

		if(requestURI.contains("/oncsplash"))
			sendCachedFile(t, "image/gif", "oncsplash", false);
		else if(requestURI.contains("/trustlogo"))
			sendCachedFile(t, "image/png", "trustlogo", false);
		else if(requestURI.contains("/erroricon"))
			sendCachedFile(t, "image/png", "erroricon", false);
		else if(requestURI.contains("/giftcardicon"))
			sendCachedFile(t, "image/png", "giftcardicon", false);
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
		else if(requestURI.contains("/oncnewstylesheet"))
			sendCachedFile(t, "text/css", "oncstylesheet2", false);
		else if(requestURI.contains("/oncdialogstylesheet"))
			sendCachedFile(t, "text/css", "oncdialogstylesheet", false);
		else if(requestURI.contains("/oncloginstylesheet"))
			sendCachedFile(t, "text/css", "oncloginstylesheet", false);
		else if(requestURI.contains("/jquery.js"))
			sendCachedFile(t, "text/javascript", "jquery", false);
		else if(requestURI.contains("/staticcharts.js"))
			sendCachedFile(t, "text/javascript", "staticcharts", false);
		else if(requestURI.contains("/webcam-easy.js"))
			sendCachedFile(t, "text/javascript", "webcam-easy", false);
		else if(requestURI.contains("/snap.wav"))
			sendCachedFile(t, "text/javascript", "snap", false);
		else if(requestURI.contains("/editprofile.js"))
			sendCachedFile(t, "text/javascript", "editprofile", false);
		else if(requestURI.contains("/onccommon.js"))
			sendCachedFile(t, "text/javascript", "onccommon", false);
		else if(requestURI.contains("/onctable.js"))
			sendCachedFile(t, "text/javascript", "onctable", false);
		else if(requestURI.contains("/login.js"))
			sendCachedFile(t, "text/javascript", "login", false);
		else if(requestURI.contains("/stoplighticon-any"))
			sendCachedFile(t, "image/png", "stoplighticon-any", false);
		else if(requestURI.contains("/stoplighticon-gray"))
			sendCachedFile(t, "image/png", "stoplighticon-gray", false);
		else if(requestURI.contains("/stoplighticon-green"))
			sendCachedFile(t, "image/png", "stoplighticon-green", false);
		else if(requestURI.contains("/stoplighticon-yellow"))
			sendCachedFile(t, "image/png", "stoplighticon-yellow", false);
		else if(requestURI.contains("/stoplighticon-red"))
			sendCachedFile(t, "image/png", "stoplighticon-red", false);
		else if(requestURI.contains("/deliverycards"))
			sendFile(t, "application/pdf", "DeliveryCard.pdf");
	}
}
