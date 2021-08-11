package oncserver;

import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.desktop.QuitEvent;
import java.awt.desktop.QuitHandler;
import java.awt.desktop.QuitResponse;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.apache.log4j.BasicConfigurator;

import ourneighborschild.OSXAdapter;

public class ONCServer
{
	/**
	 * @param args
	 * @throws IOException 
	 */
	private static final String APPNAME = "Our Neighbor's Child Server v8.02";
	private static final String ONC_COPYRIGHT = "\u00A92018 John W. O'Neill";
	private static final int JAVA_VERSION_NINE = 9;
	private ServerUI serverUI;	//User IF
	private ServerLoop serverIF; 	//Server loop
	private ClientManager clientMgr; //Manages all connected clients
	private DBManager dbManager;

	private boolean bServerRunning;
//	private Timer dbSaveTimer;
//	private List<ONCServerDB> dbList;
	
	//GUI Objects
	private JFrame oncFrame;
	private ServerMenuBar serverMenuBar;
	
	ONCServer() throws IOException
	{
		//If running under MAC OSX, use the system menu bar and set the application title appropriately and
    	//set up our application to respond to the Mac OS X application menu
        if(System.getProperty("os.name").toLowerCase().startsWith("mac os x")) 
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
            
            //determine if java version is Java 9 or after. If so, use Desktop to set About, Preferences and Quit
            //with lambds's. Otherwise use OSX Adapter
            String javaVersion = System.getProperty("java.version");
            String majorVersion = javaVersion.substring(0, javaVersion.indexOf('.'));
            
    		if(majorVersion.matches("-?\\d+(\\.\\d+)?") && Integer.parseInt(majorVersion) >= JAVA_VERSION_NINE)
    		{
    			Desktop desktop = Desktop.getDesktop();
                
                desktop.setAboutHandler(e ->
                	JOptionPane.showMessageDialog(oncFrame, APPNAME + "\n" + ONC_COPYRIGHT, "About the ONC Server",
            			JOptionPane.INFORMATION_MESSAGE, clientMgr.getAppIcon()));
                
                desktop.setQuitHandler(new ServerQuit());
    		}
    		else
    		{	
    			// Generic registration with the Mac OS X application, attempts to register with the Apple EAWT
    			// See OSXAdapter.java to see how this is done without directly referencing any Apple APIs
    			try
    			{
    				// Generate and register the OSXAdapter, passing it a hash of all the methods we wish to
    				// use as delegates for various com.apple.eawt.ApplicationListener methods
    				OSXAdapter.setQuitHandler(this, getClass().getDeclaredMethod("quit", (Class[])null));
    				OSXAdapter.setAboutHandler(this,getClass().getDeclaredMethod("about", (Class[])null));
 //             	OSXAdapter.setPreferencesHandler(this, getClass().getDeclaredMethod("preferences", (Class[])null));
 //             	OSXAdapter.setFileHandler(this, getClass().getDeclaredMethod("loadImageFile", new Class[] { String.class }));
    			} 
    			catch (Exception e)
    			{
    				System.err.println("Error while loading the OSXAdapter:");
    				e.printStackTrace();
    			}
    		}    
        }
        
        //Set up client manager
      	clientMgr = ClientManager.getInstance();
      	
      	//initialize the port manager
      	ServerPortManager.getInstance();
        
        //create mainframe window for the application and add button listeners to start/stop the sever
        createandshowGUI();
        UIButtonListener btnListener = new UIButtonListener();
        serverUI.btnStartServer.addActionListener(btnListener);
        serverUI.btnStopServer.addActionListener(btnListener);

		//set up the database manager and load the data base from persistent store
		dbManager = DBManager.getInstance(serverUI.getIcon(0));
		
		//Create the client listener socket and start the loop		
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
    		JOptionPane.showMessageDialog(oncFrame, APPNAME + "\n" + ONC_COPYRIGHT, "About the ONC Server",
    			JOptionPane.INFORMATION_MESSAGE, clientMgr.getAppIcon());
    }
    
    private void createandshowGUI()
	{
    		MenuBarListener mbl = new MenuBarListener();
    		serverMenuBar = new ServerMenuBar();
    		serverMenuBar.countsMI.addActionListener(mbl);
    		serverMenuBar.convertStatusMI.addActionListener(mbl);
    		serverMenuBar.createHistMI.addActionListener(mbl);
    		serverMenuBar.updateUserNameMI.addActionListener(mbl);
    		serverMenuBar.createDelCardsMI.addActionListener(mbl);
    		serverMenuBar.missingHistoriesMI.addActionListener(mbl);
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
        oncFrame.setSize(524, 520);
        oncFrame.setVisible(true);
	}
    
    void startServer()
    {
    	//Create and start the server loop
    	serverIF = new ServerLoop(clientMgr);
    	
    	serverIF.start();
		serverUI.setStoplight(0, "Server started");	//Set server status to green - running
		
		serverUI.addUIAndLogMessage("App Server Interface Loop started");
		serverUI.addUIAndLogMessage(String.format("System current time in millis: %d", System.currentTimeMillis()));
		ServerUI.addDebugMessage(String.format("User Directory: %s", System.getProperty("user.dir")));
		
		serverUI.btnStartServer.setVisible(false);
		serverUI.btnStopServer.setVisible(true);
		
		bServerRunning = true;
    }
    
    void stopServer()
    {
    	dbManager.saveDBToPermanentStore(); //force a save of unsaved data to permanent store
    	
    	serverUI.writeServerLogFile(); //write the log before terminating
    	
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
		
		serverUI.setStoplight(2, "Server Stopped");	//Set server status to red - stopped
		
		serverUI.addUIAndLogMessage("Server Interface Loop stopped");
		
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

    public static void main(String args[])
	{ 
		 SwingUtilities.invokeLater(new Runnable()
		 {
			 public void run()
			 { 
				try
				{
					new ONCServer();
					ONCSecureWebServer.getInstance();
					ONCWebServer.getInstance();
//					BasicConfigurator.configure();
					
				} 
				catch (IOException e)
				{
					e.printStackTrace();
				}
		}});	 
	}
   
    private class MenuBarListener implements ActionListener
    {
		@Override
		public void actionPerformed(ActionEvent e) 
		{
			if(e.getSource() == serverMenuBar.createDelCardsMI)
			{
/*				
				//create delivery card pdf
				try {
					ServerFamilyDB serverFamilyDB = ServerFamilyDB.getInstance();
					List<Integer> famIDList = new ArrayList<Integer>();
					famIDList.add(254);
					famIDList.add(255);
					famIDList.add(256);
					famIDList.add(270);
					serverFamilyDB.createDelCardFile(2019, famIDList);
					ServerUI.addDebugMessage("Delivery Card PDF created");
				} catch (FileNotFoundException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
*/				
			}
			else if(e.getSource() == serverMenuBar.countsMI)
			{
//				//update py performance
//				try {
//					ServerPartnerDB serverPartnerDB = ServerPartnerDB.getInstance();
//					serverPartnerDB.determinePriorYearPerformance(2016);
//					clientMgr.addLogMessage("Partner PY Performance Updated");
//				} catch (FileNotFoundException e1) {
//					// TODO Auto-generated catch block
//					e1.printStackTrace();
//				} catch (IOException e1) {
//					// TODO Auto-generated catch block
//					e1.printStackTrace();
//				}
			}
			else if(e.getSource() == serverMenuBar.convertStatusMI)
			{
/*				
				//convert org db to partner db. Add Individual type
				try 
				{
					ServerPartnerDB serverPartnerDB = ServerPartnerDB.getInstance();
					serverPartnerDB.convertOldPartnerDBToNewPartnerDB();
					
				} catch (FileNotFoundException e1)
				{
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (IOException e1) 
				{
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
*/				
			}			
			else if(e.getSource() == serverMenuBar.createHistMI)
			{
				//update py performance
//				try {
//					ServerFamilyHistoryDB serverFamilyHistoryDB = ServerFamilyHistoryDB.getInstance();
//					serverFamilyHistoryDB.convertDeliveryDBForStatusChanges(2016);
//					clientMgr.addLogMessage(String.format("%d Family History DB Created", 2016));
//					serverFamilyHistoryDB.convertDeliveryDBForStatusChanges(2015);
//					clientMgr.addLogMessage(String.format("%d Family History DB Created", 2015));
//					serverFamilyHistoryDB.convertDeliveryDBForStatusChanges(2014);
//					clientMgr.addLogMessage(String.format("%d Family History DB Created", 2014));
//					serverFamilyHistoryDB.convertDeliveryDBForStatusChanges(2013);
//					clientMgr.addLogMessage(String.format("%d Family History DB Created", 2013));
//					serverFamilyHistoryDB.convertDeliveryDBForStatusChanges(2012);
//					clientMgr.addLogMessage(String.format("%d Family History DB Created", 2012));
//				} catch (FileNotFoundException e1) {
//					// TODO Auto-generated catch block
//					e1.printStackTrace();
//				} catch (IOException e1) {
//					// TODO Auto-generated catch block
//					e1.printStackTrace();
//				}
			}	
			else if(e.getSource() == serverMenuBar.updateUserNameMI)
			{
//				//update py performance
//				try 
//				{
//					ServerUserDB userDB = ServerUserDB.getInstance();
//					clientMgr.addLogMessage(String.format("%d User Names updated", userDB.updateUserNames()));
//				} catch (FileNotFoundException e1) {
//					// TODO Auto-generated catch block
//					e1.printStackTrace();
//				} catch (IOException e1) {
//					// TODO Auto-generated catch block
//					e1.printStackTrace();
//				}
			}
			else if(e.getSource() == serverMenuBar.missingHistoriesMI)
			{
//				//find families without histories
//				ServerFamilyDB familyDB;
//				try
//				{
//					familyDB = ServerFamilyDB.getInstance();
//					familyDB.familiesWithoutHistories();
//				}
//				catch (IOException e1)
//				{
//					// TODO Auto-generated catch block
//					e1.printStackTrace();
//				}	
			}
		}
    }
    
    private class ServerQuit implements QuitHandler
    {

		@Override
		public void handleQuitRequestWith(QuitEvent arg0, QuitResponse arg1)
		{
			if(bServerRunning)	//Did user forget to stop the server prior to quitting? 
	    		serverIF.terminateServer();
	    	System.exit(0);
		}
    }
}
