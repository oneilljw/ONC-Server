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
	private static final String ONC_LOGO_FILE = "oncsplash.gif";
	private static final String TRUST_LOGO_FILE = "comodo_secure_seal_76x26_transp.png";
	
	private static ONCWebServer instance = null;
	
	protected static Map<String, byte[]> webfileMap;
	
	private ONCWebServer() throws IOException
	{
		ServerUI serverUI = ServerUI.getInstance();
		
		//load the cache
		if(webfileMap == null)
			webfileMap = new HashMap<String, byte[]>();
			
		webfileMap.put("onclogo", readFileToByteArray(ONC_LOGO_FILE));
		webfileMap.put("trustlogo", readFileToByteArray(TRUST_LOGO_FILE));	
			
		//create the web server
		HttpServer server = HttpServer.create(new InetSocketAddress(REDIRECT_PORT), 0);
		HttpContext context;
		
		//set up the oncWebHttpHandler
		String[] contexts = {"/", "/trustlogo"};
		
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
	
	void sendCachedFile(HttpExchange t, String mimetype, String sheetname, boolean debugMode) throws IOException
	{
		if(debugMode)
			ServerUI.addDebugMessage(String.format("WebpageHldr.sendCachedFile: sheetname= %s", sheetname));
		// add the required response header
	    Headers h = t.getResponseHeaders();
	    h.add("Content-Type", mimetype);
	    
	    if(debugMode)
	    	ServerUI.addDebugMessage(String.format("WebpageHldr.sendCachedFile: header content added, type= %s", mimetype));

	    //get file
	    byte[] bytearray = webfileMap.get(sheetname);
	    
	    if(debugMode)
	    		ServerUI.addDebugMessage(String.format("WebpageHldr.sendCachedFile: bytearray size= %d", bytearray.length));
	    	    
//		String path = String.format("%s/%s", System.getProperty("user.dir"), sheetname);
//		File file = new File(path);
//		byte [] bytearray  = new byte [(int)file.length()];
//	      
//	    FileInputStream fis = new FileInputStream(file);
//	      
//	    BufferedInputStream bis = new BufferedInputStream(fis);
//	    bis.read(bytearray, 0, bytearray.length);
//	    bis.close();
	      	
	    //send the response.
//	    t.sendResponseHeaders(HTTP_OK, file.length());
	    t.sendResponseHeaders(200, bytearray.length);
	    
	    if(debugMode)
	    		ServerUI.addDebugMessage(String.format("WebpageHldr.sendCachedFile: sent response headers, code= %d, length= %d", 200, bytearray.length)); 
	    
	    OutputStream os = t.getResponseBody();
	    
	    if(debugMode)
	    		ServerUI.addDebugMessage(String.format("WebpageHldr.sendCachedFile: got response body"));
	    
	    os.write(bytearray,0,bytearray.length);
	    
	    if(debugMode)
	    		ServerUI.addDebugMessage(String.format("WebpageHldr.sendCachedFile: wrote response, length= %d", bytearray.length));
	    
	    os.close();
	    
	    if(debugMode)
	    		ServerUI.addDebugMessage(String.format("WebpageHldr.sendCachedFile: closed output stream"));
	    
	    t.close();
	    
	    if(debugMode)
	    		ServerUI.addDebugMessage(String.format("WebpageHldr.sendCachedFile: closed HttpExchange"));
	}
	
	static byte[] readFileToByteArray(String filename) throws IOException
	{
		//get file
	    String path = String.format("%s/%s", System.getProperty("user.dir"), filename);
	    File file = new File(path);
	    byte [] bytearray  = new byte [(int)file.length()];
	      
	    FileInputStream fis = new FileInputStream(file);
	      
	    BufferedInputStream bis = new BufferedInputStream(fis);
	    bis.read(bytearray, 0, bytearray.length);
	    bis.close();
	    
		return bytearray;
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
         	else if(requestURI.equals("/onclogo"))
         	{
        			sendCachedFile(t, "image/gif", "onclogo", false);
         	}
         	else if(requestURI.equals("/trustlogo"))
         	{
        			sendCachedFile(t, "image/gif", "trustlogo", false);
         	}
        }
    }	
}
