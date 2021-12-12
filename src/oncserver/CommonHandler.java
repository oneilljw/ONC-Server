package oncserver;

import java.io.IOException;
import java.io.OutputStream;

import com.sun.net.httpserver.Headers;
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
		else if(requestURI.contains("/mistletoeicon"))
			sendCachedFile(t, "image/gif", "mistletoeicon", false);
		else if(requestURI.contains("/cornerhat"))
			sendCachedFile(t, "image/png", "cornerhat", false);
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
		{
			Headers reqHeaders = t.getRequestHeaders();
			Headers respHeaders = t.getResponseHeaders();
		   
			Integer[] range = getRange(reqHeaders);
			if(range != null)
			{
				//Get the actual byte range from the range header string, and set the starting byte.
				byte[] bytearray = webfileMap.get("snap.wav");
				int fSize = bytearray.length;
				HttpCode retCode = HttpCode.Ok;
				
				int startbyte = range[0];
				int endbyte = bytearray.length-1;	//
				
				//if only one range parameter, i.e. "[bytes:0-]", send the entire file
	            if (range[1] != -1)
	            	endbyte = range[1];
	             
	            //If the start byte is not equal to zero, that means the user is requesting partial content.
	            if (startbyte != 0 || endbyte != fSize - 1)
	            	retCode = HttpCode.Partial_Content;
	            	
	            int desSize = endbyte - startbyte + 1;
	            
	            respHeaders.add("Content-Type", "audio/wav");
	            respHeaders.add("Accpet-Ranges", "bytes");
	            respHeaders.add("Content-Length", Integer.toString(desSize)); 
	            respHeaders.add("Content-Range", String.format("bytes %d-%d/%d", startbyte, endbyte , fSize));
	            t.sendResponseHeaders(retCode.code(), desSize);
	            
	            OutputStream os = t.getResponseBody();
	    	    os.write(bytearray, startbyte, desSize);
	    	    os.close();
	    	    
	    	    t.close();
			}	
			else
				sendBadRequestResponseHeader(t);	//couldn't parse the Range request header	
		}
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
//			sendFile(t, "application/pdf", "DeliveryCard.pdf");
			sendFile(t, "application/pdf", String.format("%s/%s", System.getProperty("user.dir"), "DeliveryCard.pdf"));
		else if(requestURI.contains("/barcode.js"))
			sendCachedFile(t, "text/javascript", "barcode", false);
		
	}
}
