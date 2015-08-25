package oncserver;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;

public class ONCWebServer
{
	private static final int WEB_SERVER_PORT = 8902;
	
	public ONCWebServer() throws IOException
	{
		ServerUI serverUI = ServerUI.getInstance();
		
		HttpServer server = HttpServer.create(new InetSocketAddress(WEB_SERVER_PORT), 0);
		ONCHttpHandler oncHttpHandler = new ONCHttpHandler();
		
		String[] contexts = {"/welcome", "/logout", "/login", "/dbStatus", "/agents",
							"/families", "/getfamily", "/getmeal", "/children", "/adults",
							"/oncsplash", "/onclogo", "/newfamily", "/address", "/referral",
							"/referfamily", "/familyupdate", "/updatefamily", "/changepw"};
		
		HttpContext context;
		
		for(String contextname:contexts)
		{
			context = server.createContext(contextname, oncHttpHandler);
			context.getFilters().add(new ParameterFilter());
		}

		server.setExecutor(null); // creates a default executor
		server.start();
		
		serverUI.addLogMessage(String.format("Web Server started: %d contexts", contexts.length));
	}
}
