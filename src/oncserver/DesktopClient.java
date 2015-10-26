package oncserver;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import ourneighborschild.Login;
import ourneighborschild.ONCChild;
import ourneighborschild.ONCChildWish;
import ourneighborschild.ONCEncryptor;
import ourneighborschild.ONCServerUser;
import ourneighborschild.ONCUser;
import ourneighborschild.UserPermission;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class DesktopClient extends Thread 
{
	private static final int BASE_YEAR = 2012;
	private static final int NUMBER_OF_WISHES_PER_CHILD = 3;
	private static final float MINIMUM_CLIENT_VERSION = 3.08f;
	
	private int id;
	private String version;
	private ClientState state; //connected, started, logged in, dbSelected
	private Heartbeat heartbeat;
	private int year; 	//What year data is the client connected to
    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    
    private Queue<String> changeQ; //Keeps list of change JSONs that client polls
    
    private DBManager dbManager;
    private ServerUserDB userDB;
    private RegionDB regionDB;
    private GlobalVariableDB globalvariableDB;
    private FamilyDB familyDB;
    private AgentDB agentDB;
    private ServerChildDB childDB;
    private ServerChildWishDB childwishDB;
    private PartnerDB partnerDB;
    private ServerDriverDB driverDB;
    private ServerDeliveryDB deliveryDB;
    private ServerWishCatalog wishCatalog;
    private ServerWishDetailDB wishDetailDB;
    private PriorYearDB prioryearDB;
    private ClientManager clientMgr;
    private ServerMealDB mealDB;
    private ServerAdultDB adultDB;
    private ServerChatManager chatMgr;
    private ONCUser clientUser;
    private Calendar timestamp;
    private long timeLastActive;
    private String lastcommand;

    /**
     * Constructs a handler thread for a given socket and mark
     * initializes the stream fields, displays the first two
     * welcoming messages.
     */
    public DesktopClient(Socket socket, int id)
    {
    	this.id = id;
    	version = "N/A";
    	state = ClientState.Connected;
    	heartbeat = Heartbeat.Not_Started;
    	year = -1;
        this.socket = socket;
        
        //Initialize the client manager interface
        clientMgr = ClientManager.getInstance();
        
        //Initialize the chat manager interface
        chatMgr = ServerChatManager.getInstance();

        //Initialize the web server and data base interface
        try
        {
        	dbManager = DBManager.getInstance(clientMgr.getAppIcon());
			userDB = ServerUserDB.getInstance();
			regionDB = RegionDB.getInstance(clientMgr.getAppIcon());
	        globalvariableDB = GlobalVariableDB.getInstance();
	        familyDB = FamilyDB.getInstance();
	        agentDB = AgentDB.getInstance();
	        childDB = ServerChildDB.getInstance();
	        childwishDB = ServerChildWishDB.getInstance();
	        partnerDB = PartnerDB.getInstance();
	        driverDB = ServerDriverDB.getInstance();
	        deliveryDB = ServerDeliveryDB.getInstance();
	        wishCatalog = ServerWishCatalog.getInstance();
	        wishDetailDB = ServerWishDetailDB.getInstance();
	        prioryearDB = PriorYearDB.getInstance();
	        mealDB = ServerMealDB.getInstance();
	        adultDB = ServerAdultDB.getInstance();
		  
	        clientUser = null;
	        timestamp = Calendar.getInstance();
	        timeLastActive = System.currentTimeMillis();
        
	        changeQ = new LinkedList<String>();
        
	        input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(socket.getOutputStream(), true);
            
            //tell the client that they have successfully connected to the server
            String encryptedResponse = ONCEncryptor.encrypt("LOGINConnected to the ONC Server, Please Login");
            output.println(encryptedResponse);
//          output.println("LOGINConnected to the ONC Server, Please Login");
        }         
        catch (FileNotFoundException e1) 
        {
        	clientMgr.addLogMessage(String.format("Client %d constructor FileNotFoundException %s", id, e1.getMessage()));
    	} 
        catch (IOException e1)
        {
        	clientMgr.addLogMessage(String.format("Client %d constructor I/O exception %s", id, e1.getMessage()));
        	clientMgr.clientDied(this); 
    	}
    }
    
    int getClientID() { return id; }
    ClientState getClientState() { return state; }
    Heartbeat getClientHeartbeat() { return heartbeat; }
    long getTimeLastActiveInMillis() { return timeLastActive; }
    int getClientUserID() { return clientUser == null ? -1 : clientUser.getID(); }
    ONCUser getClientUser() { return clientUser == null ? null : clientUser; }
   
    
    void setClientState (ClientState cs) { state = cs; }
    void setClientHeartbeat(Heartbeat hb) { heartbeat = hb; }

    /**
     * The run method of this thread.
     */
    public void run()
    {
    	state = ClientState.Running;	//Client has started
    	heartbeat = Heartbeat.Active;
    	clientMgr.clientStateChanged();	//tell client 
    	String command = "";
    	lastcommand = "";
    	
        try 
        {
            // Repeatedly get commands from the client and process them.
            while (state != ClientState.Ended)
            {	
            	command = input.readLine();	//Blocks until the client sends a message to the socket
            	lastcommand = command;
            	
            	//note the time the last command was received from the client.This is the 
                //clients heart beat. When connected, the client should be asking for changes
                //no less than once a second. However the heart beat rate is determined by the client
                timeLastActive = System.currentTimeMillis();
            	
                if (command.startsWith("LOGIN_REQUEST"))
                { 
                    String response = loginRequest(command.substring(13));
                    output.println(response);
//                  if(response.startsWith("VALID"))
//                  {
//                    	String mssg = "GLOBAL_MESSAGE" + clientUser.getFirstname() + " " + clientUser.getLastname() + 
//                   				  " is now online";
//                		clientMgr.notifyAllOtherClients(this, mssg);
//                  }
                }
                else if(command.startsWith("GET<users>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = userDB.getUsers();
                	output.println(response);
                	clientMgr.addLogMessage(response);               	
                }
                else if(command.startsWith("GET<online_users>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = clientMgr.getOnlineUsers();
                	output.println(response);
                	clientMgr.addLogMessage(response);               	
                }
                else if(command.startsWith("GET<regions>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = regionDB.getRegions();
                	output.println(response);
                	clientMgr.addLogMessage(response);
                }
                else if(command.startsWith("GET<globals>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = globalvariableDB.getGlobalVariables(year);
                	output.println(response);
                }
                else if(command.startsWith("GET<dbstatus>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = dbManager.getDatabaseStatusList();
                	output.println(response);
                }
                else if(command.startsWith("GET<regionmatch>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = regionDB.getRegionMatch(command.substring(16));
                	output.println(response);
                }
                else if(command.startsWith("GET<familys>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = familyDB.getFamilies(year);
                	output.println(response);
                }
                else if(command.startsWith("GET<agents>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = agentDB.getAgents(year);
                	output.println(response);
                }
                else if(command.startsWith("GET<family>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = familyDB.getFamily(year, command.substring(11));
                	output.println(response);		
                }
                else if(command.startsWith("GET<children>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = childDB.getChildren(year);
                	output.println(response);
                }
                else if(command.startsWith("GET<childwishes>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = getChildWishes(year);
                	output.println(response);
                }
                else if(command.startsWith("GET<partners>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = partnerDB.getPartners(year);
                	output.println(response);
                }
                else if(command.startsWith("GET<drivers>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = driverDB.getDrivers(year);
                	output.println(response);
                }
                else if(command.startsWith("GET<deliveries>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = deliveryDB.getDeliveries(year);
                	output.println(response);
                }
                else if(command.startsWith("GET<catalog>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = wishCatalog.getWishCatalog(year);
                	output.println(response);
                }
                else if(command.startsWith("GET<wishdetail>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = wishDetailDB.getWishDetail(year);
                	output.println(response);
                	state = ClientState.DB_Selected;
                	clientMgr.clientStateChanged();
                }
                else if(command.startsWith("GET<adults>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = adultDB.getAdults(year);
                	output.println(response);		
                }
                else if(command.startsWith("GET<meals>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = mealDB.getMeals(year);
                	output.println(response);		
                }
                else if(command.startsWith("GET<pychild>"))
                {
                	clientMgr.addLogMessage(command);
                	output.println(prioryearDB.getPriorYearChild(year, command.substring(12)));
                }
                else if(command.startsWith("GET<wishhistory>"))
                {
                	clientMgr.addLogMessage(command);
                	output.println(childwishDB.getChildWishHistory(year, command.substring(16)));
                }
                else if(command.startsWith("GET<deliveryhistory>"))
                {
                	clientMgr.addLogMessage(command);
                	output.println(deliveryDB.getDeliveryHistory(year, command.substring(20)));
                }
                else if(command.startsWith("GET<website_status>"))
                {
                	clientMgr.addLogMessage(command);
                	output.println(ONCWebServer.getWebsiteStatusJson());
                }
                else if(command.startsWith("GET<changes>"))
                {   
                	if(changeQ.peek() == null)
                		output.println("NO_CHANGES");
                	else
                	{
                		//bundle the change q into a list of strings and send it
                		//to the client
                		Gson gson = new Gson();
                		List<String> qContents = new ArrayList<String>();
                		Type listOfChanges = new TypeToken<ArrayList<String>>(){}.getType();
                		
                		while(!changeQ.isEmpty())
	                		qContents.add(changeQ.remove());
                		
                		String response = gson.toJson(qContents, listOfChanges);
                		output.println(response);
                		clientMgr.addLogMessage("GET<changes> Response: " + response);
                	}
                }
                else if(command.startsWith("POST<add_user>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = userDB.add(year, command.substring(14));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<update_user>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = userDB.update(year, command.substring(17));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<setyear>"))
                {
                	String response = setYear(command.substring(13));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	
                	 if(response.startsWith("YEAR"))
                     {
                     	String mssg = "GLOBAL_MESSAGE" + clientUser.getFirstname() + " " + clientUser.getLastname() + 
                     				  " is using " +Integer.toString(year) + " season data";
                 		clientMgr.notifyAllOtherClients(this, mssg);
                     }
                }
                else if(command.startsWith("POST<update_family"))
                {
                	clientMgr.addLogMessage(command);
                	String response;
                	if(command.contains("_oncnum>"))
                		response = familyDB.update(year, command.substring(26), true);
                	else
                		response = familyDB.update(year, command.substring(19), false);
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<add_family>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = familyDB.add(year, command.substring(16));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<check_duplicatefamily>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = familyDB.checkForDuplicateFamily(year, command.substring(27), clientUser);
                	output.println(response);
                	clientMgr.addLogMessage(response);
                }
                else if(command.startsWith("POST<update_child>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = childDB.update(year, command.substring(18));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<delete_child>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = childDB.deleteChild(year, command.substring(18));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<add_child>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = childDB.add(year, command.substring(15));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<update_partner>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = partnerDB.update(year, command.substring(20));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<add_partner>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = partnerDB.add(year, command.substring(17));
                	output.println(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<delete_partner>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = partnerDB.delete(year, command.substring(20));
                	output.println(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<update_agent>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = agentDB.update(year, command.substring(18));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<add_agent>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = agentDB.add(year, command.substring(15));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<delete_agent>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = agentDB.delete(year, command.substring(18));
                	output.println(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<update_catwish>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = wishCatalog.update(year, command.substring(20));
                	output.println(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<add_catwish>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = wishCatalog.add(year, command.substring(17));
                	output.println(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<delete_catwish>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = wishCatalog.delete(year, command.substring(20));
                	output.println(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<update_wishdetail>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = wishDetailDB.update(year, command.substring(23));
                	output.println(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<add_wishdetail>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = wishDetailDB.add(year, command.substring(20));
                	output.println(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<delete_wishdetail>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = wishDetailDB.delete(year, command.substring(23));
                	output.println(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<childwish>"))
                {                	
                	//Add the wish to the child wish data base and update the 
                	//child wish ID in the child data base for the added wish
                	clientMgr.addLogMessage(command);
                	String response = childwishDB.add(year, command.substring(15));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<update_delivery>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = deliveryDB.update(year, command.substring(21));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<add_delivery>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = deliveryDB.add(year, command.substring(18));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<update_driver>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = driverDB.update(year, command.substring(19));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<delete_driver>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = driverDB.delete(year, command.substring(19));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<add_driver>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = driverDB.add(year, command.substring(16));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.dataChanged(this, response);
                }
/*MEALS ARE NOT UPDATED BY DESKTOP CLIENTS - NEW MEALS ARE ADDED
                else if(command.startsWith("POST<update_meal>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = mealDB.update(year, command.substring(17));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.dataChanged(this, response);
                }
*/                
                else if(command.startsWith("POST<delete_meal>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = mealDB.delete(year, command.substring(17));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<add_adult>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = adultDB.add(year, command.substring(15));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<update_adult>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = adultDB.update(year, command.substring(18));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<delete_adult>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = adultDB.delete(year, command.substring(18));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<add_meal>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = mealDB.add(year, command.substring(14));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<add_newseason>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = dbManager.createNewYear();
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherClients(this, response);
                }
                else if(command.startsWith("POST<update_globals>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = globalvariableDB.update(year, command.substring(20));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<update_dbyear>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = dbManager.updateDBYear(year, command.substring(19));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.dataChanged(this, response);
                }
                else if(command.startsWith("POST<change_password>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = userDB.changePassword(year, command.substring(21), this);
                	output.println(response);
                	clientMgr.addLogMessage(response);
                }
                else if(command.startsWith("POST<chat_request>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = chatMgr.requestChat(command.substring(18));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                }
                else if(command.startsWith("POST<chat_accepted>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = chatMgr.acceptChat(command.substring(19));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                }
                else if(command.startsWith("POST<chat_message>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = chatMgr.forwardChatMessage(command.substring(18));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                }
                else if(command.startsWith("POST<chat_ended>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = chatMgr.endChat(command.substring(16));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                }
                else if(command.startsWith("POST<update_website_status>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = ONCWebServer.setWebsiteStatus(command.substring(27));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherClients(this, response);
                }
                else if (command.startsWith("LOGOUT")) 
                {
                	String response = "GOODBYE";
                	output.println(response);
                	
                	if(clientUser != null)
                		clientUser.setClientID(-1);	//note that client has gone off-line
                	
                	String mssg = "GLOBAL_MESSAGE" + clientUser.getFirstname() + " " + clientUser.getLastname() + 
             				  " is offline";
                	clientMgr.notifyAllOtherClients(this, mssg);
                	 state = ClientState.Ended;
                	
                	clientMgr.clientLoggedOut(this);  
                }
                else
                	output.println("UNRECOGNIZED_COMMAND" + command);
            } 
        } 
       	catch (IOException e) 
        {
       		if(clientUser != null)
       			clientUser.setClientID(-1);	//note that user is offline
       		String logMssg = String.format("Client %d died, I/O exception %s, last command: %s",
       				id, e.getMessage(), lastcommand);
       		clientMgr.addLogMessage(logMssg);
       		clientMgr.clientDied(this); 
        }
        catch (NullPointerException npe)
        {
        	if(clientUser != null)
        		clientUser.setClientID(-1);	//note that user is off line
        	String logMssg = String.format("Client %d died, NullPointerException %s, last command: %s",
        			id, npe.getMessage(), lastcommand);
        	clientMgr.addLogMessage(logMssg);
        	clientMgr.clientDied(this);
        }
    }
    
    void closeClientSocket()
    {
    	try {
        	socket.close();
        } 
    	catch (IOException e) {
    		String logMssg = String.format("Client %d: Close Socket IOException: %s", id, e.getMessage());
        	clientMgr.addLogMessage(logMssg);
        }
    }
    
    String loginRequest(String loginjson)
    {
    	Gson gson = new Gson();
    	Login lo = gson.fromJson(loginjson, Login.class);
    	
//    	String userID = lo.getUserID();
//    	String password = lo.getPassword();
    	
    	String userID = ONCEncryptor.decrypt(lo.getUserID());
    	String password = ONCEncryptor.decrypt(lo.getPassword());
    	
    	float lo_version = Float.parseFloat(lo.getVersion());
  	
    	String value = "INVALID";
    	
    	//don't want a reference here, want a new object. A user can be logged in more than once.
    	//However, never can use this object to update a user's info
    	ONCServerUser serverUser = (ONCServerUser) userDB.find(userID);
  
    	if(lo_version < MINIMUM_CLIENT_VERSION)
    	{
    		clientMgr.clientLoginAttempt(false, String.format("Client %d login request failed: "
    				+ "Downlevel Client, v%s",  id, lo.getVersion()));
    		value += "Downlevel ONC Client: v" + lo.getVersion() + ", please upgrade";
    	}
    	else if(serverUser == null)	//can't find the user in the data base
    	{
    		clientMgr.clientLoginAttempt(false, String.format("Client %d login request failed with v%s:"
    				+ " User name not found", id, lo.getVersion()));
    		value += "User Name not found";
    	}
    	else if(serverUser != null && serverUser.getPermission() == UserPermission.INACTIVE)	//can't find the user in the data base
    	{
    		clientMgr.clientLoginAttempt(false, String.format("Client %d login request failed with v%s:"
    				+ " User account is inactive", id, lo.getVersion()));
    		value += "Inactive user account";
    	}
    	else if(serverUser != null && !serverUser.pwMatch(password))	//found the user but pw is incorrect
    	{
    		clientMgr.clientLoginAttempt(false, String.format("Client %d login request failed with v%s:"
    				+ " Incorrect password", id, lo.getVersion()));
    		value += "Incorrect password";
    	}
    	else if(serverUser != null && serverUser.pwMatch(password))	//user found, password matches
    	{
    		//Create user json and attach to VALID response
    		state = ClientState.Logged_In;	//Client logged in
    		serverUser.incrementSessions();
    		serverUser.setLastLogin(new Date());
    		userDB.save(year);	//year will equal -1 at this point, but ignored. Only one user.csv
    		
    		clientUser = serverUser.getUserFromServerUser();
    		clientUser.setClientID(id);	//set the user object client ID
    		version = lo.getVersion();
    		
    		String loginJson = gson.toJson(clientUser, ONCUser.class);
    		value = "VALID" + loginJson;
    		
    		String mssg = "UPDATED_USER" + loginJson;
    		clientMgr.notifyAllOtherClients(this, mssg);

    		clientMgr.clientLoginAttempt(true, String.format("Client %d, %s %s login request sucessful",
    															id, clientUser.getFirstname(), clientUser.getLastname()));	
    	}
    	
    	return value;
    }
    
    String[] getClientTableRow()
    {
    	String[] row = new String[9];
    	row[0] = Long.toString(id);
    	
    	if(clientUser != null)	//if server user is known, user their name
    	{
    		row[1] = clientUser.getFirstname();
    		row[2] = clientUser.getLastname();
    		row[3] = clientUser.getPermission().toString();	
    	}
    	else
    	{
    		row[1] = "Anonymous";
    		row[2] = "Anonymous";
    		row[3] = "U";
    	}
    	
    	row[4] = state.toString();
    	row[5] = heartbeat.toString().substring(0,1);	//get string of 1st character
    	row[6] = state == ClientState.DB_Selected ? Integer.toString(year) : "None";
    	row[7] = version;
    	row[8] = new SimpleDateFormat("MM/dd H:mm:ss").format(timestamp.getTime());
    		
    	return row;
    }
    
    void addChange(String change)
    {
    	changeQ.add(change);
    }
    
    String getClientName()
    { 
    	if(clientUser.getFirstname().isEmpty())
    		return clientUser.getLastname();
    	else
    		return clientUser.getFirstname() + " " + clientUser.getLastname();
    }
    
    String setYear(String dbYear)
    {
    	int yr = Integer.parseInt(dbYear);
    	year = (yr >  BASE_YEAR) ? yr : BASE_YEAR;
    	
    	getClientUser().setClientYear(year);
    	
    	return "YEAR" + Integer.toString(year);

    }
    
    /******************
     * This method creates a json containing an array list of ONCChildWish's. The list 
     * contains the current wishes for each child in the child data base. The method is
     * in this class since access to both the child database and the child wish database
     * is necessary to build the array list. 
     * @param year
     * @return
     */
    String getChildWishes(int year)
    {
    	//Build the array list of current ONCChildWish's for each child
    	ArrayList<ONCChildWish> childwishAL = new ArrayList<ONCChildWish>();
  
    	for(ONCChild c:childDB.getList(year))
    		for(int wn=0; wn < NUMBER_OF_WISHES_PER_CHILD; wn++)		
    			if(c.getChildWishID(wn) > -1) //Wish must have a valid ID
    				childwishAL.add(childwishDB.getWish(year, c.getChildWishID(wn)));

    	//Convert the array list to a json and return it
    	Gson gson = new Gson();
    	Type listtype = new TypeToken<ArrayList<ONCChildWish>>(){}.getType();
   
    	return gson.toJson(childwishAL, listtype);
    }
    
    int getYear() { return year; } 
}
