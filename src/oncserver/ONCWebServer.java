package oncserver;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;

public class ONCWebServer
{
	public ONCWebServer() throws IOException
	{
		ServerUI serverUI = ServerUI.getInstance();
		
		HttpServer server = HttpServer.create(new InetSocketAddress(8902), 0);
		ONCHttpHandler oncHttpHandler = new ONCHttpHandler();
			
		HttpContext context = server.createContext("/familystatus", oncHttpHandler);
		context.getFilters().add(new ParameterFilter());
		
		context = server.createContext("/jstest", oncHttpHandler);
		context.getFilters().add(new ParameterFilter());
			
		context = server.createContext("/oncsplash", oncHttpHandler);
		context.getFilters().add(new ParameterFilter());
		    
		context = server.createContext("/", oncHttpHandler);
		context.getFilters().add(new ParameterFilter());
		
		context = server.createContext("/login", oncHttpHandler);
		context.getFilters().add(new ParameterFilter());
		
		context = server.createContext("/logout", oncHttpHandler);
		context.getFilters().add(new ParameterFilter());
		    
		server.setExecutor(null); // creates a default executor
		server.start();
		
		serverUI.addLogMessage("Web Server started");
	}
}
