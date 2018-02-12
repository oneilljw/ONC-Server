package oncserver;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import ourneighborschild.WebsiteStatus;

public class ONCWebServer
{
//	private static final int WEB_SERVER_PORT = 443;
	private static final int WEB_SERVER_PORT = 8902;
	private static final int CONCURRENT_THREADS = 5;
	
	private static ONCWebServer instance = null;
	private static WebsiteStatus websiteStatus;
	
	private static List<ONCHttpHandler> oncHandlerList;
	
	private ONCWebServer() throws IOException
	{
		ServerUI serverUI = ServerUI.getInstance();
		oncHandlerList = new ArrayList<ONCHttpHandler>();
		
//		String keystoreFilename = System.getProperty("user.dir") + "/A4O/a4o.keystore";
		String keystoreFilename = System.getProperty("user.dir") + "/oncdms.jks";
		char[] storepass = "oncpassword".toCharArray();
		char[] keypass = "oncpassword".toCharArray();		
		
		HttpsServer server = null;
		try 
		{	
			// load certificate
			FileInputStream fIn = new FileInputStream(keystoreFilename);
			KeyStore keystore = KeyStore.getInstance("JKS");
			keystore.load(fIn, storepass);
			
			// display certificate
//			String alias = "mykey";
//			Certificate certificate = keystore.getCertificate(alias);
//			System.out.println(certificate);
			
			// setup the key manager factory
			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(keystore, keypass);
			
			// setup the trust manager factory
			TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
			tmf.init(keystore);
			
			// create https server
			server = HttpsServer.create(new InetSocketAddress(WEB_SERVER_PORT), 0);
			
			// create ssl context
			SSLContext sslContext = SSLContext.getInstance("TLSv1");
			sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
			
			// setup the HTTPS context and parameters
			server.setHttpsConfigurator(new HttpsConfigurator(sslContext) 
			{
				public void configure(HttpsParameters params) 
				{
					try 
					{
						//Initialize the SSL context
						SSLContext c = SSLContext.getDefault();
						SSLEngine engine = c.createSSLEngine();
						
						// get the default parameters
//						SSLParameters sslParameters = c.getDefaultSSLParameters();
//						sslParameters.setNeedClientAuth(true);
//						params.setSSLParameters(sslParameters);
						
						params.setNeedClientAuth(true);             
						params.setCipherSuites(engine.getEnabledCipherSuites());           
						params.setProtocols(engine.getEnabledProtocols());
					} 
					catch (Exception ex) 
					{
						ex.printStackTrace();
						System.out.println("Failed to create HTTPS server");
					}
				}
			});
		} 
		catch (KeyStoreException kse) 
		{
			System.out.println(String.format("KeyStoreException : %s", kse.getMessage()));
		} 
		catch (NoSuchAlgorithmException nsae)
		{
			System.out.println(String.format("NoSuchAlgorithmException : %s", nsae.getMessage()));
		} 
		catch (CertificateException ce)
		{
			System.out.println(String.format("CertificateException : %s", ce.getMessage()));
		} 
		catch (IOException ioe)
		{
			System.out.println(String.format("IOException : %s", ioe.getMessage()));
			System.out.println(ioe.getLocalizedMessage());
		}
		catch (UnrecoverableKeyException uke)
		{
			System.out.println(String.format("UnrevocerableKeyException : %s", uke.getMessage()));
		} 
		catch (KeyManagementException kme)
		{
			System.out.println(String.format("KeyManagementException : %s", kme.getMessage()));
		}
		
		//create the handler and the contexts
		HttpContext context;
		
		//set up the oncWebHttpHandler
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
		int contextCount = 0;
		
		for(String contextname:contexts)
		{
			context = server.createContext(contextname, oncHttpHandler);
			context.getFilters().add(new ParameterFilter());
			contextCount++;
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
			contextCount++;
		}
		
		//set up the common handler
		String[] commonContexts = {"/jquery.js", "/favicon.ico", "/oncsplash", "/clearx", "/onclogo", 
								   "/oncstylesheet", "/oncdialogstylesheet", "/vanilla"};
		
		CommonHandler commonHandler = new CommonHandler();
		
		for(String contextname: commonContexts)
		{
			context = server.createContext(contextname, commonHandler);
			context.getFilters().add(new ParameterFilter());
			contextCount++;
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
			contextCount++;
		}

		//start the web server
		ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_THREADS);
		server.setExecutor(pool); // creates a default executor
		server.start();
				
		ServerGlobalVariableDB gvDB = ServerGlobalVariableDB.getInstance();
		Calendar now = Calendar.getInstance();
		Calendar yearEndCal = Calendar.getInstance();
		yearEndCal.set(Calendar.YEAR, DBManager.getCurrentYear());
		yearEndCal.set(Calendar.MONTH, Calendar.DECEMBER);
		yearEndCal.set(Calendar.DAY_OF_MONTH, 31);
		yearEndCal.set(Calendar.HOUR, 11);
		yearEndCal.set(Calendar.MINUTE, 59);
		yearEndCal.set(Calendar.SECOND, 59);
		yearEndCal.set(Calendar.MILLISECOND, 999);
				
		if(now.after(gvDB.getSeasonStartCal(DBManager.getCurrentYear())) && now.before(yearEndCal))
			websiteStatus = new WebsiteStatus(true, true, "Online");
		else
		{
			websiteStatus = new WebsiteStatus(true, false, "Online");
			serverUI.setStoplight(1, "Logging disabled");
		}
				
		serverUI.addLogMessage(String.format("Web Server started: %d contexts, logging %s", 
					contextCount, websiteStatus.isWebsiteLoggingEnabled() ? "enabled" : "disabled"));
	}
		
	public static ONCWebServer getInstance() throws IOException
	{
		if(instance == null)
			instance = new ONCWebServer();
		
		return instance;
	}

/*	
	private ONCWebServer() throws IOException
	{
		ServerUI serverUI = ServerUI.getInstance();
		oncHandlerList = new ArrayList<ONCHttpHandler>();
		
		//create the web server
		HttpServer server = HttpServer.create(new InetSocketAddress(WEB_SERVER_PORT), 0);
		HttpContext context;
		
		//set up the oncWebHttpHandler
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
		int contextCount = 0;
		
		for(String contextname:contexts)
		{
			context = server.createContext(contextname, oncHttpHandler);
			context.getFilters().add(new ParameterFilter());
//			context.getFilters().add(paramFilter);
			contextCount++;
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
			contextCount++;
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
			contextCount++;
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
//			context.getFilters().add(paramFilter);
			contextCount++;
		}
		
//		Filter paramFilter = new ParameterFilter();
		
		//start the web server
		ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_THREADS);
		server.setExecutor(pool); // creates a default executor
		server.start();
		
		ServerGlobalVariableDB gvDB = ServerGlobalVariableDB.getInstance();
		Calendar now = Calendar.getInstance();
		Calendar yearEndCal = Calendar.getInstance();
		yearEndCal.set(Calendar.YEAR, DBManager.getCurrentYear());
		yearEndCal.set(Calendar.MONTH, Calendar.DECEMBER);
		yearEndCal.set(Calendar.DAY_OF_MONTH, 31);
		yearEndCal.set(Calendar.HOUR, 11);
		yearEndCal.set(Calendar.MINUTE, 59);
		yearEndCal.set(Calendar.SECOND, 59);
		yearEndCal.set(Calendar.MILLISECOND, 999);
		
		if(now.after(gvDB.getSeasonStartCal(DBManager.getCurrentYear())) && now.before(yearEndCal))
			websiteStatus = new WebsiteStatus(true, true, "Online");
		else
		{
			websiteStatus = new WebsiteStatus(true, false, "Online");
			serverUI.setStoplight(1, "Logging disabled");
		}
		
		serverUI.addLogMessage(String.format("Web Server started: %d contexts, logging %s", 
				contextCount, websiteStatus.isWebsiteLoggingEnabled() ? "enabled" : "disabled"));
	}
	
	public static ONCWebServer getInstance() throws IOException
	{
		if(instance == null)
			instance = new ONCWebServer();
		
		return instance;
	}
*/	
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
		
		//if a change to logging, change the UI stoplight color and add a message
		ServerUI serverUI = ServerUI.getInstance();
		if(serverUI.getStoplight() == 0 && !websiteStatus.isWebsiteLoggingEnabled())
			serverUI.setStoplight(1, "Logging Disabled");
		else if(serverUI.getStoplight() == 1 && websiteStatus.isWebsiteLoggingEnabled())
			serverUI.setStoplight(0, "Logging Enabled");
			 	
		return "UPDATED_WEBSITE_STATUS" + websiteStatusJson;
	}
	
	static boolean isWebsiteOnline() { return websiteStatus.isWebsiteOnline(); }
	static boolean isServerLoggingEnabled() { return websiteStatus.isWebsiteLoggingEnabled(); }
	static String getWebsiteTimeBackOnline() { return websiteStatus.getTimeBackUp(); }
	
	static String reloadWebpages()
	{ 
		for(ONCHttpHandler handler : oncHandlerList)
			handler.loadWebpages();
		
		return "UPDATED_WEBPAGES";
	}
}
