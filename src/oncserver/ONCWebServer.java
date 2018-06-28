package oncserver;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class ONCWebServer
{
//	private static final int CONCURRENT_THREADS = 2;
	private static final int REDIRECT_PORT = 8903;
	
	private static ONCWebServer instance = null;
	
	private ONCWebServer() throws IOException
	{
		ServerUI serverUI = ServerUI.getInstance();
			
		//create the web server
		HttpServer server = HttpServer.create(new InetSocketAddress(REDIRECT_PORT), 0);
		HttpContext context;
		
		//set up the oncWebHttpHandler
		String[] contexts = {"/"};
		
		PublicWebsiteHandler handler = new PublicWebsiteHandler();
		for(String contextname:contexts)
		{
			context = server.createContext(contextname, handler);
			context.getFilters().add(new ParameterFilter());
		}

		//start the web server
//		ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_THREADS);
		server.setExecutor(null); // creates a default executor
		server.start();
		
		
		serverUI.addLogMessage(String.format("Public Web Server started: %d contexts", contexts.length));
	}
	
	public static ONCWebServer getInstance() throws IOException
	{
		if(instance == null)
			instance = new ONCWebServer();
		
		return instance;
	}
	
	class PublicWebsiteHandler implements HttpHandler
	{
        @Override
        public void handle(HttpExchange t) throws IOException 
        {
        		String requestURI = t.getRequestURI().toASCIIString();

         	if(requestURI.equals("/"))
         	{
         		Headers header = t.getResponseHeaders();
         		ArrayList<String> headerList = new ArrayList<String>();
         		headerList.add("https://oncdms.org:8902/welcome");
         		header.put("Location", headerList);

         		String response = "Redirecting to secure website";
         		t.sendResponseHeaders(HttpCode.Redirect.code(), response.length());
         		OutputStream os = t.getResponseBody();
         		os.write(response.getBytes());
         		os.close();
         		t.close();
         	}
        }
    }	
}
