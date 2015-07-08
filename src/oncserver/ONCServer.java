package oncserver;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.InetSocketAddress;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;

import ourneighborschild.OSXAdapter;

public class ONCServer
{
	/**
	 * @param args
	 * @throws IOException 
	 */
	
	private static final String APPNAME = "Our Neighbor's Child Server";
	private static final String ONC_SERVER_VERSION = "Our Neighbor's Child Server Version 1.23\n";
	private static final String ONC_COPYRIGHT = "\u00A92015 John W. O'Neill";
	private ServerUI serverUI;	//User IF
	private ServerLoop serverIF; 	//Server loop
	private ClientManager clientMgr; //Manages all connected clients
	
	private boolean bServerRunning;
//	private Timer dbSaveTimer;
//	private List<ONCServerDB> dbList;
	
	//GUI Objects
	private JFrame oncFrame;
	private ServerMenuBar serverMenuBar;
	
	//Check if we are on Mac OS X.  This is crucial to loading and using the OSXAdapter class.
    private static boolean MAC_OS_X = (System.getProperty("os.name").toLowerCase().startsWith("mac os x"));

	ONCServer() throws IOException
	{
		//If running under MAC OSX, use the system menu bar and set the application title appropriately and
    	//set up our application to respond to the Mac OS X application menu
        if (MAC_OS_X) 
        {          	
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("com.apple.mrj.application.apple.menu.about.name", APPNAME);
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();}
			catch (InstantiationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace(); }
			catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace(); }
			catch (UnsupportedLookAndFeelException e) {
				// TODO Auto-generated catch block
				e.printStackTrace(); }
			
            // Generic registration with the Mac OS X application, attempts to register with the Apple EAWT
            // See OSXAdapter.java to see how this is done without directly referencing any Apple APIs
            try
            {
                // Generate and register the OSXAdapter, passing it a hash of all the methods we wish to
                // use as delegates for various com.apple.eawt.ApplicationListener methods
                OSXAdapter.setQuitHandler(this, getClass().getDeclaredMethod("quit", (Class[])null));
                OSXAdapter.setAboutHandler(this,getClass().getDeclaredMethod("about", (Class[])null));
 //             OSXAdapter.setPreferencesHandler(this, getClass().getDeclaredMethod("preferences", (Class[])null));
 //             OSXAdapter.setFileHandler(this, getClass().getDeclaredMethod("loadImageFile", new Class[] { String.class }));
            } 
            catch (Exception e)
            {
                System.err.println("Error while loading the OSXAdapter:");
                e.printStackTrace();
            }
        }
        
        //create mainframe window for the application and add button listeners to start/stop the sever
        createandshowGUI();
        serverUI.btnStartServer.addActionListener(new UIButtonListener());
        serverUI.btnStopServer.addActionListener(new UIButtonListener());
        
		//Set up client manager
		clientMgr = ClientManager.getInstance();
		
		//set up the database manager and load the data base from persistent store
		DBManager.getInstance(clientMgr.getAppIcon());

		//Create the client listener socket and start the loop		
//		serverUI.setStoplight(0);	//Set server status to green - started
		startServer();	//Start the server on app start up
    }
	
	 // General quit handler; fed to the OSXAdapter as the method to call when a system quit event occurs
    // A quit event is triggered by Cmd-Q, selecting Quit from the application or Dock menu, or logging out
    public boolean quit()
    {
    	if(bServerRunning)	//Did user forget to stop the server prior to quitting? 
    		serverIF.terminateServer();
    	return true;
    }
    
    // General info dialog; fed to the OSXAdapter as the method to call when 
    // "About OSXAdapter" is selected from the application menu   
    public void about()
    {
    	JOptionPane.showMessageDialog(oncFrame, ONC_SERVER_VERSION + ONC_COPYRIGHT, "About the ONC Server",
    			JOptionPane.INFORMATION_MESSAGE, clientMgr.getAppIcon());
    }
    
    private void createandshowGUI()
	{
    	serverMenuBar = new ServerMenuBar();
    	serverMenuBar.countsMI.addActionListener(new MenuBarListener());
    	serverUI = ServerUI.getInstance();
    	
    	oncFrame =  new JFrame(APPNAME);
    	oncFrame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent we)
			 {
				if(bServerRunning)	//Did user forget to stop the server prior to quitting? 
		    		serverIF.terminateServer();

				System.exit(0);	  
			 }});
        oncFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);	//On close, user is prompted to confirm
        oncFrame.setMinimumSize(new Dimension(200, 200));
        oncFrame.setLocationByPlatform(true);
        
        oncFrame.setJMenuBar(serverMenuBar);
        oncFrame.setContentPane(serverUI); 
        oncFrame.setSize(500, 450);
        oncFrame.setVisible(true);
	}
    
    void startServer()
    {
    	//Create and start the server loop
    	serverIF = new ServerLoop(clientMgr);
    	
    	serverIF.start();
		serverUI.setStoplight(0);	//Set server status to green - running
		
		serverUI.addLogMessage("Server Interface Loop started");
		
		serverUI.btnStartServer.setVisible(false);
		serverUI.btnStopServer.setVisible(true);
		
		bServerRunning = true;
    }
    
    void stopServer()
    {
		serverIF.stopServer();
		try 
		{
			serverIF.join();
			bServerRunning = serverIF.terminateServer();
		} 
		catch (InterruptedException e1)
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		serverUI.setStoplight(2);	//Set server status to green - running
		
		serverUI.addLogMessage("Server Interface Loop stopped");
		
		serverUI.btnStartServer.setVisible(true);
		serverUI.btnStopServer.setVisible(false);
    }
    
    private class UIButtonListener implements ActionListener
    {
    	public void actionPerformed(ActionEvent e)
    	{
    		if(e.getSource() == serverUI.btnStartServer)
    			startServer();
    		else if(e.getSource() == serverUI.btnStopServer)
    			stopServer();
    	}	
    }
    
    public static void main(String[] args)
	{
		HttpServer server;
		try {
			server = HttpServer.create(new InetSocketAddress(8902), 0);
			ONCHttpHandler oncHttpHandler = new ONCHttpHandler();
			
			HttpContext context = server.createContext("/test", oncHttpHandler);
			context.getFilters().add(new ParameterFilter());
			
		    context = server.createContext("/oncsplash", oncHttpHandler);
		    context.getFilters().add(new ParameterFilter());
		    
		    context = server.createContext("/login", oncHttpHandler);
		    context.getFilters().add(new ParameterFilter());
		    
		    server.setExecutor(null); // creates a default executor
		    server.start();
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}
		
	}
/*
    public static void main(String args[])
	{    	
		 SwingUtilities.invokeLater(new Runnable() {
			 public void run()
			 {
				 
				try
				{
					new ONCServer();
				} 
				catch (IOException e)
				{
						e.printStackTrace();
				}
				
		}});	 
	}
*/    
    private class MenuBarListener implements ActionListener
    {
		@Override
		public void actionPerformed(ActionEvent e) 
		{
			if(e.getSource() == serverMenuBar.countsMI)
			{
				
			}
		}
    }
}
