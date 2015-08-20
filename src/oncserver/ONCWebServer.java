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
		
		HttpContext context;

		context = server.createContext("/families", oncHttpHandler);
		context.getFilters().add(new ParameterFilter());
		
		context = server.createContext("/children", oncHttpHandler);
		context.getFilters().add(new ParameterFilter());
		
		context = server.createContext("/adults", oncHttpHandler);
		context.getFilters().add(new ParameterFilter());
		
		context = server.createContext("/dbStatus", oncHttpHandler);
		context.getFilters().add(new ParameterFilter());
		
		context = server.createContext("/agents", oncHttpHandler);
		context.getFilters().add(new ParameterFilter());
			
		context = server.createContext("/oncsplash", oncHttpHandler);
		context.getFilters().add(new ParameterFilter());
		
		context = server.createContext("/onclogo", oncHttpHandler);
		context.getFilters().add(new ParameterFilter());
		    
		context = server.createContext("/", oncHttpHandler);
		context.getFilters().add(new ParameterFilter());
		
		context = server.createContext("/login", oncHttpHandler);
		context.getFilters().add(new ParameterFilter());
		
		context = server.createContext("/logout", oncHttpHandler);
		context.getFilters().add(new ParameterFilter());
		
		context = server.createContext("/newfamily", oncHttpHandler);
		context.getFilters().add(new ParameterFilter());
		
		context = server.createContext("/referral", oncHttpHandler);
		context.getFilters().add(new ParameterFilter());
		
		context = server.createContext("/changepw", oncHttpHandler);
		context.getFilters().add(new ParameterFilter());
		
		context = server.createContext("/address", oncHttpHandler);
		context.getFilters().add(new ParameterFilter());
		
		context = server.createContext("/getfamily", oncHttpHandler);
		context.getFilters().add(new ParameterFilter());
		
		context = server.createContext("/referfamily", oncHttpHandler);
		context.getFilters().add(new ParameterFilter());
		
		context = server.createContext("/updatefamily", oncHttpHandler);
		context.getFilters().add(new ParameterFilter());
		    
		server.setExecutor(null); // creates a default executor
		server.start();
		
		serverUI.addLogMessage("Web Server started");
	}
}
