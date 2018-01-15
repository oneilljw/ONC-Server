package oncserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import ourneighborschild.WebsiteStatus;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;

public class ONCWebServer
{
	private static final int WEB_SERVER_PORT = 8902;
	
	private static ONCWebServer instance = null;
	private static WebsiteStatus websiteStatus;
	
	private static List<ONCHttpHandler> oncHandlerList;
	
	private ONCWebServer() throws IOException
	{
		ServerUI serverUI = ServerUI.getInstance();
		oncHandlerList = new ArrayList<ONCHttpHandler>();
		
		//create the web server
		HttpServer server = HttpServer.create(new InetSocketAddress(WEB_SERVER_PORT), 0);
		HttpContext context;
		
		//set up the oncHttpHandler
		String[] contexts = {"/dbStatus", "/agents", "/getagent", "/getmeal", "/children",
							"/adults", "/wishes", "/reqchangepw","/activities", "/activitydays",
							"/address", "/changepw", "/startpage", "/getuser", "/getstatus", "/getpartner",
							"/profileunchanged", "/updateuser", "/driversignin", "/signindriver",
							"/volunteersignin", "/signinvolunteer", "/partnerupdate", "/updatepartner",
							"/contactinfo", "/groups", "/partners", "/partnertable",
							"/regiontable", "/regions", "/zipcodes", "/regionupdate", "/updateregion", "/getregion",
							"/volunteerregistration", "/registervolunteer", "/dashboard",
							};
		
		ONCWebHttpHandler oncHttpHandler = new ONCWebHttpHandler();
		oncHandlerList.add(oncHttpHandler);
		
		for(String contextname:contexts)
		{
			context = server.createContext(contextname, oncHttpHandler);
			context.getFilters().add(new ParameterFilter());
//			context.getFilters().add(paramFilter);
		}

		//set up the login handler
		String[] loginContexts = {"/welcome", "/logout", "/login", "/onchomepage", "/metrics",
								  "/timeout"};
		
		LoginHandler loginHandler = new LoginHandler();
		oncHandlerList.add(loginHandler);
		
		for(String contextname: loginContexts)
		{
			context = server.createContext(contextname, loginHandler);
			context.getFilters().add(new ParameterFilter());
//			context.getFilters().add(paramFilter);
		}
		
		//set up the common handler
		String[] commonContexts = {"/jquery.js", "/favicon.ico", "/oncsplash", "/clearx", "/onclogo", 
								   "/oncstylesheet", "/oncdialogstylesheet", "/vanilla"};
		
		CommonHandler commonHandler = new CommonHandler();
		
		for(String contextname: commonContexts)
		{
			context = server.createContext(contextname, commonHandler);
			context.getFilters().add(new ParameterFilter());
//			context.getFilters().add(paramFilter);
		}
		
		//set up the family handler
		String[] familyContexts = {"/referral","/referfamily","/familyupdate","/updatefamily","/familyview",
				 					"/families","/familystatus","/commonfamily.js","/familysearch",
				 					"/getfamily","/references","/newfamily"};
		
		FamilyHandler familyHandler = new FamilyHandler();
		oncHandlerList.add(familyHandler);
		
		for(String contextname: familyContexts)
		{
			context = server.createContext(contextname, familyHandler);
			context.getFilters().add(new ParameterFilter());
			//context.getFilters().add(paramFilter);
		}
		
//		Filter paramFilter = new ParameterFilter();
		
		//start the web server
		server.setExecutor(null); // creates a default executor
		server.start();
		
		websiteStatus = new WebsiteStatus(true, true, "Online");
		
		serverUI.addLogMessage(String.format("Web Server started: %d contexts", contexts.length));
	}
	
	public static ONCWebServer getInstance() throws IOException
	{
		if(instance == null)
			instance = new ONCWebServer();
		
		return instance;
	}
	
	static String getWebsiteStatusJson()
	{
		//build websiteStatusJson
		Gson gson = new Gson();
		String websiteJson = gson.toJson(websiteStatus, WebsiteStatus.class);
		return "WEBSITE_STATUS" + websiteJson;
	}
	
	static String setWebsiteStatus(String websiteStatusJson)
	{ 
		Gson gson = new Gson();
		ONCWebServer.websiteStatus = gson.fromJson(websiteStatusJson, WebsiteStatus.class);
		
		return "UPDATED_WEBSITE_STATUS" + websiteStatusJson;
	}
	
	static boolean isWebsiteOnline() { return websiteStatus.isWebsiteOnline(); }
	static boolean isWebsiteLoggingEnabled() { return websiteStatus.isWebsiteLoggingEnabled(); }
	static String getWebsiteTimeBackOnline() { return websiteStatus.getTimeBackUp(); }
	
	static String reloadWebpages()
	{ 
		for(ONCHttpHandler handler : oncHandlerList)
			handler.loadWebpages();
		
		return "UPDATED_WEBPAGES";
	}
}
