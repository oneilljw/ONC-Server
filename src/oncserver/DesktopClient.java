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
import java.util.TimeZone;

import ourneighborschild.Login;
import ourneighborschild.ONCServerUser;
import ourneighborschild.ONCUser;
import ourneighborschild.UserAccess;
import ourneighborschild.UserStatus;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class DesktopClient extends Thread 
{
	private static final int BASE_YEAR = 2012;
	private static final float MINIMUM_CLIENT_VERSION = 9.12f;
	
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
    private ServerRegionDB serverRegionDB;
    private ServerGlobalVariableDB globalvariableDB;
    private ServerFamilyDB serverFamilyDB;
    private ServerDNSCodeDB serverDNSCodeDB;
    private ServerGroupDB serverGroupDB;
    private ServerChildDB childDB;
    private ServerChildGiftDB childwishDB;
    private ServerClonedGiftDB clonedGiftDB;
    private ServerPartnerDB serverPartnerDB;
    private ServerActivityDB activityDB;
    private ServerVolunteerDB volunteerDB;
    private ServerVolunteerActivityDB volunteerActivityDB;
    private ServerWarehouseDB warehouseDB;
    private ServerFamilyHistoryDB famHistoryDB;
    private ServerGiftCatalog wishCatalog;
    private ServerGiftDetailDB wishDetailDB;
    private PriorYearDB prioryearDB;
    private ClientManager clientMgr;
    private ServerMealDB mealDB;
    private ServerAdultDB adultDB;
    private ServerNoteDB noteDB;
    private ServerBatteryDB batteryDB;
    private ServerInventoryDB inventoryDB;
    private ServerSMSDB smsDB;
    private ServerDistributionCenterDB distributionCenterDB;
    private ServerChatManager chatMgr;
    private ONCUser clientUser;
    private Calendar timestamp;
    private long timeLastActive;
    private String lastcommand;

    /**
     * Constructs a handler thread for a given socket and mark initializes the stream fields,
     * displays the first two welcoming messages.
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
			serverRegionDB = ServerRegionDB.getInstance();
	        globalvariableDB = ServerGlobalVariableDB.getInstance();
	        serverDNSCodeDB = ServerDNSCodeDB.getInstance();
	        serverFamilyDB = ServerFamilyDB.getInstance();
	        serverGroupDB = ServerGroupDB.getInstance();
	        childDB = ServerChildDB.getInstance();
	        childwishDB = ServerChildGiftDB.getInstance();
	        clonedGiftDB = ServerClonedGiftDB.getInstance();
	        serverPartnerDB = ServerPartnerDB.getInstance();
	        activityDB = ServerActivityDB.getInstance();
	        volunteerDB = ServerVolunteerDB.getInstance();
	        volunteerActivityDB = ServerVolunteerActivityDB.getInstance();
	        warehouseDB = ServerWarehouseDB.getInstance();
	        famHistoryDB = ServerFamilyHistoryDB.getInstance();
	        wishCatalog = ServerGiftCatalog.getInstance();
	        wishDetailDB = ServerGiftDetailDB.getInstance();
	        prioryearDB = PriorYearDB.getInstance();
	        mealDB = ServerMealDB.getInstance();
	        adultDB = ServerAdultDB.getInstance();
	        noteDB = ServerNoteDB.getInstance();
	        batteryDB = ServerBatteryDB.getInstance();
	        inventoryDB = ServerInventoryDB.getInstance();
	        smsDB = ServerSMSDB.getInstance();
	        distributionCenterDB = ServerDistributionCenterDB.getInstance();
		  
	        clientUser = null;
	        timestamp = Calendar.getInstance();
	        timeLastActive = System.currentTimeMillis();
        
	        changeQ = new LinkedList<String>();
        
	        input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(socket.getOutputStream(), true);
            
            //tell the client that they have successfully connected to the server
            String encryptedResponse = ServerEncryptionManager.encrypt("LOGINConnected to the ONC Server, Please Login");
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
    String getClientRemoteSocketAddress() { return socket.getRemoteSocketAddress().toString(); }
    ClientState getClientState() { return state; }
    Heartbeat getClientHeartbeat() { return heartbeat; }
    String getClientVersion() { return version; }
    Date getClientTimestamp() { return timestamp.getTime(); }
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
    		clientMgr.clientStateChanged(ClientType.DESKTOP, ClientEventType.ACTIVE, this); 
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
            	
                if(command.startsWith("GET<changes>"))	//first because it's the most frequent server request
                {   
            		if(changeQ.isEmpty())
            			output.println("NO_CHANGES");
            		else
            		{
            			//bundle the change q into a list of strings and send to the client
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
                else if (command.startsWith("LOGIN_REQUEST"))
                { 
                    String response = loginRequest(command.substring(13));
                    output.println(response);
                }
                else if(command.startsWith("GET<users>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = userDB.getUsers();
                	output.println(response);
//                	clientMgr.addLogMessage(response);               	
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
                	String response = serverRegionDB.getRegions();
                	output.println(response);
                }
                else if(command.startsWith("GET<dnscodes>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = serverDNSCodeDB.getDNSCodes();
                	output.println(response);
                }
                else if(command.startsWith("GET<served_schools>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = serverRegionDB.getServedSchools();
                	output.println(response);
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
                else if(command.startsWith("GET<keys>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = ServerEncryptionManager.getKeyMapJson();
                	output.println(response);
                }
                else if(command.startsWith("GET<regionmatch>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = serverRegionDB.getRegionMatch(command.substring(16));
                	output.println(response);
                }
                else if(command.startsWith("GET<familys>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = serverFamilyDB.getFamilies(year);
                	output.println(response);
                }
//				else if(command.startsWith("GET<agents>"))
//              {
//                	clientMgr.addLogMessage(command);
//                	String response = serverAgentDB.getAgents();
//                	String response = userDB.getAgents();
//                	output.println(response);
//              }
                else if(command.startsWith("GET<groups>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = serverGroupDB.getGroupList();
                	output.println(response);
                }
                else if(command.startsWith("GET<family>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = serverFamilyDB.getFamily(year, command.substring(11));
                	output.println(response);		
                }
                else if(command.startsWith("GET<family_phone_info>"))
                {
                	clientMgr.addLogMessage(command);               	
                	String response = serverFamilyDB.checkFamilyPhoneNumbers(year, command.substring(22));
                	output.println(response);		
                }
                else if(command.startsWith("GET<confirmation>"))
                {
                	clientMgr.addLogMessage(command);               	
                	String response = serverFamilyDB.getConfirmationPNG(year, command.substring(17));
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
                	String response = childwishDB.getCurrentChildGiftList(year);
                	output.println(response);
                }
                else if(command.startsWith("GET<clonedgifts>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = clonedGiftDB.getCurrentCloneGiftList(year);
                	output.println(response);
                }
                else if(command.startsWith("GET<partners>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = serverPartnerDB.getPartners(year);
                	output.println(response);
                }
                else if(command.startsWith("GET<activities>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = activityDB.getActivities(year);
                	output.println(response);
                }
                else if(command.startsWith("GET<drivers>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = volunteerDB.getDrivers(year);
                	output.println(response);
                }
                else if(command.startsWith("GET<volunteer_activities>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = volunteerActivityDB.getVolunteerActivities(year);
                	output.println(response);
                }
                else if(command.startsWith("GET<family_histories>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = famHistoryDB.getFamilyHistories(year);
                	output.println(response);
                }
                else if(command.startsWith("GET<catalog>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = wishCatalog.getGiftCatalog(year);
                	output.println(response);
                }
                else if(command.startsWith("GET<wishdetail>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = wishDetailDB.getWishDetail(year);
                	output.println(response);
                }
                else if(command.startsWith("GET<adults>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = adultDB.getAdults(year);
                	output.println(response);		
                }
                else if(command.startsWith("GET<notes>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = noteDB.getNotes(year);
                	output.println(response);		
                }
                else if(command.startsWith("GET<meals>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = mealDB.getMeals(year);
                	output.println(response);		
                }
                else if(command.startsWith("GET<batteries>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = batteryDB.getBatteries(year);
                	output.println(response);
                	state = ClientState.DB_Selected;
                    clientMgr.clientStateChanged(ClientType.DESKTOP, ClientEventType.ACTIVE, this); 
                }
                else if(command.startsWith("GET<pychild>"))
                {
                	clientMgr.addLogMessage(command);
                	output.println(prioryearDB.getPriorYearChild(year, command.substring(12)));
                }
                else if(command.startsWith("GET<inventory>"))
                {
                	clientMgr.addLogMessage(command);
                	output.println(inventoryDB.getInventory());
                }
                else if(command.startsWith("GET<search_pychild>"))
                {
                	clientMgr.addLogMessage(command);
                	String jsonResponse = prioryearDB.searchForPriorYearChild(year, command.substring(19));
                	output.println(jsonResponse);
                	clientMgr.addLogMessage(jsonResponse);
                }
                else if(command.startsWith("GET<wishhistory>"))
                {
                	clientMgr.addLogMessage(command);
                	output.println(childwishDB.getChildGiftHistory(year, command.substring(16)));
                }
                else if(command.startsWith("GET<clonedgifthistory>"))
                {
                	clientMgr.addLogMessage(command);
                	output.println(clonedGiftDB.getGiftHistory(year, command.substring(22)));
                }
                else if(command.startsWith("GET<warehousehistory>"))
                {
                	clientMgr.addLogMessage(command);
                	output.println(warehouseDB.getWarehouseSignInHistory(year, command.substring(21)));
                }
                else if(command.startsWith("GET<deliveryhistory>"))
                {
                	clientMgr.addLogMessage(command);
                	output.println(famHistoryDB.getFamilyHistory(year, command.substring(20)));
                }
                else if(command.startsWith("GET<website_status>"))
                {
                	clientMgr.addLogMessage(command);
                	output.println(ONCSecureWebServer.getWebsiteStatusJson());
                }
                else if(command.startsWith("GET<genius_signups>"))
                {
                	clientMgr.addLogMessage(command);
                	output.println(activityDB.getSignUps());
                }
                else if(command.startsWith("GET<sms_messages>"))
                {
                	clientMgr.addLogMessage(command);
                	output.println(smsDB.getSMSMessages(year));
                }
                else if(command.startsWith("GET<distribution_centers>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = distributionCenterDB.getDistributionCenters(year);
                	output.println(response);
                }
                else if(command.equals("GET<request_signups>"))
                {
                	clientMgr.addLogMessage(command);
//                	SignUpGeniusIF geniusIF = SignUpGeniusIF.getInstance();
//                	geniusIF.requestSignUpList();
                	SignUpGeniusSignUpListImporter suListImporter = SignUpGeniusSignUpListImporter.getInstance();
                	suListImporter.requestSignUpList();
                	output.println("REQUESTED_SIGNUPS");
                }
                else if(command.startsWith("POST<update_signup>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = activityDB.updateSignUp(command.substring(19));
                	clientMgr.addLogMessage(response);
                	output.println(response);
                	clientMgr.notifyAllOtherClients(this, response);
                }
                else if(command.startsWith("POST<update_genius_signups>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = activityDB.updateGeniusSignUps(command.substring(27));
                	clientMgr.addLogMessage(response);
                	output.println(response);
                	clientMgr.notifyAllOtherClients(this, response);
                }
                else if(command.startsWith("GET<changes>"))
                {   
//                	if(changeQ.peek() == null)
                		if(changeQ.isEmpty())
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
                	String response = userDB.add(command.substring(14), clientUser);
                	output.println(response);
                	clientMgr.addLogMessage(response);
 //               	clientMgr.dataChanged(this, response);
                	clientMgr.notifyAllOtherClients(this, response);
                }
                else if(command.startsWith("POST<update_user>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = userDB.update(year, command.substring(17));
                	output.println(response);
                	clientMgr.addLogMessage(response);
 //               	clientMgr.dataChanged(this, response);
                	clientMgr.notifyAllOtherClients(this, response);
                }
                else if(command.startsWith("POST<setyear>"))
                {
                		String response = setYear(command.substring(13));
                		output.println(response);
                		clientMgr.addLogMessage(response);
                	
                		if(response.startsWith("YEAR"))
                     {
                     	String mssg = "GLOBAL_MESSAGE" + clientUser.getFirstName() + " " + clientUser.getLastName() + 
                     				  " is using " +Integer.toString(year) + " season data";
                 		clientMgr.notifyAllOtherClients(this, mssg);
                     }
                }
                else if(command.startsWith("POST<update_family"))
                {
            		clientMgr.addLogMessage(command);
            		String response;
            		if(command.contains("_oncnum>"))
            			response = serverFamilyDB.update(year, command.substring(26), clientUser, true);
            		else
            			response = serverFamilyDB.update(year, command.substring(19), clientUser, false);
            		output.println(response);
            		clientMgr.addLogMessage(response);
            		clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<update_list_of_families>"))
                {                	
            		//update the list of family update requests in the family data base
            		clientMgr.addLogMessage(command);
            		String response = serverFamilyDB.updateListOfFamilies(year, command.substring(29), clientUser);
            		output.println(response);
            		clientMgr.addLogMessage(response);
            		clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<add_family>"))
                {
            		clientMgr.addLogMessage(command);
            		String response = serverFamilyDB.add(year, command.substring(16), clientUser);
            		output.println(response);
            		clientMgr.addLogMessage(response);
            		clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<family_group>"))
                {
            		clientMgr.addLogMessage(command);
            		String response = serverFamilyDB.addFamilyGroup(year, command.substring(18), this);
            		output.println(response);
            		clientMgr.addLogMessage(response);
                }
                else if(command.startsWith("POST<new_volunteer_group>"))
                {
            		clientMgr.addLogMessage(command);
            		String response = volunteerDB.addVolunteerGroup(year, command.substring(25), this);
            		output.println(response);
            		clientMgr.addLogMessage(response);
                }
                else if(command.startsWith("POST<update_volunteer_group>"))
                {
            		clientMgr.addLogMessage(command);
            		String response = volunteerDB.updateVolunteerGroup(year, command.substring(28), this);
            		output.println(response);
            		clientMgr.addLogMessage(response);
                }
//              else if(command.startsWith("POST<check_duplicatefamily>"))
//              {
//            		clientMgr.addLogMessage(command);
//            		String response = serverFamilyDB.checkForDuplicateFamily(year, command.substring(27), clientUser);
//            		output.println(response);
//            		clientMgr.addLogMessage(response);
//              }
                else if(command.startsWith("POST<update_child>"))
                {
            		clientMgr.addLogMessage(command);
            		String response = childDB.update(year, command.substring(18));
            		output.println(response);
            		clientMgr.addLogMessage(response);
            		clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<delete_child>"))
                {
            		clientMgr.addLogMessage(command);
            		String response = childDB.deleteChild(year, command.substring(18));
            		output.println(response);
            		clientMgr.addLogMessage(response);
            		clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<add_child>"))
                {
            		clientMgr.addLogMessage(command);
            		String response = childDB.add(year, command.substring(15), clientUser);
            		output.println(response);
            		clientMgr.addLogMessage(response);
            		clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<update_group>"))
                {
            		clientMgr.addLogMessage(command);
            		String response = serverGroupDB.update(command.substring(18));
            		output.println(response);
            		clientMgr.addLogMessage(response);
            		clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<delete_group>"))
                {
            		clientMgr.addLogMessage(command);
            		String response = serverGroupDB.delete(command.substring(18));
            		output.println(response);
            		clientMgr.addLogMessage(response);
            		clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<add_group>"))
                {
            		clientMgr.addLogMessage(command);
            		String response = serverGroupDB.add(command.substring(15), clientUser);
            		output.println(response);
            		clientMgr.addLogMessage(response);
            		clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<add_barcode>"))
                {
            		clientMgr.addLogMessage(command);
            		String response = inventoryDB.addFromBarcodeScan(command.substring(17));
            		output.println(response);
            		clientMgr.addLogMessage(response);
 //               	clientMgr.dataChanged(this, response);
                	clientMgr.notifyAllOtherClients(this, response);
                }
                else if(command.startsWith("POST<add_inventory>"))
                {
            		clientMgr.addLogMessage(command);
            		String response = inventoryDB.add(command.substring(19), clientUser);
            		output.println(response);
            		clientMgr.addLogMessage(response);
 //               	clientMgr.dataChanged(this, response);
                	clientMgr.notifyAllOtherClients(this, response);
                }
                else if(command.startsWith("POST<update_inventory>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = inventoryDB.update(command.substring(22));
                	output.println(response);
                	clientMgr.addLogMessage(response);
//                	clientMgr.dataChanged(this, response);
                	clientMgr.notifyAllOtherClients(this, response);
                }
                else if(command.startsWith("POST<delete_inventory>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = inventoryDB.delete(command.substring(22));
                	output.println(response);
                	clientMgr.addLogMessage(response);
//                	clientMgr.dataChanged(this, response);
                	clientMgr.notifyAllOtherClients(this, response);
                }
                else if(command.startsWith("POST<update_partner>"))
                {
            		clientMgr.addLogMessage(command);
            		String response = serverPartnerDB.update(year, command.substring(20));
            		output.println(response);
            		clientMgr.addLogMessage(response);
            		clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<add_partner>"))
                {
            		clientMgr.addLogMessage(command);
            		String response = serverPartnerDB.add(year, command.substring(17), clientUser);
            		output.println(response);
            		clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<delete_partner>"))
                {
            		clientMgr.addLogMessage(command);
            		String response = serverPartnerDB.delete(year, command.substring(20));
            		output.println(response);
            		clientMgr.notifyAllOtherInYearClients(this, response);
                }
/*            
                else if(command.startsWith("POST<update_agent>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = serverAgentDB.update(command.substring(18));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<add_agent>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = serverAgentDB.add(command.substring(15));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<delete_agent>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = serverAgentDB.delete(command.substring(18));
                	output.println(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
*/                
                else if(command.startsWith("POST<update_catwish>"))
                {
            		clientMgr.addLogMessage(command);
            		String response = wishCatalog.update(year, command.substring(20));
            		output.println(response);
//                	clientMgr.dataChanged(this, response);
                	clientMgr.notifyAllOtherClients(this, response);
                }
                else if(command.startsWith("POST<add_catwish>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = wishCatalog.add(year, command.substring(17), clientUser);
                	output.println(response);
//                	clientMgr.dataChanged(this, response);
                	clientMgr.notifyAllOtherClients(this, response);
                }
                else if(command.startsWith("POST<delete_catwish>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = wishCatalog.delete(year, command.substring(20));
                	output.println(response);
//                	clientMgr.dataChanged(this, response);
                	clientMgr.notifyAllOtherClients(this, response);
                }
                else if(command.startsWith("POST<update_wishdetail>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = wishDetailDB.update(year, command.substring(23));
                	output.println(response);
//                	clientMgr.dataChanged(this, response);
                	clientMgr.notifyAllOtherClients(this, response);
                }
                else if(command.startsWith("POST<add_wishdetail>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = wishDetailDB.add(year, command.substring(20), clientUser);
                	output.println(response);
//                	clientMgr.dataChanged(this, response);
                	clientMgr.notifyAllOtherClients(this, response);
                }
                else if(command.startsWith("POST<delete_wishdetail>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = wishDetailDB.delete(year, command.substring(23));
                	output.println(response);
//                	clientMgr.dataChanged(this, response);
                	clientMgr.notifyAllOtherClients(this, response);
                }
                else if(command.startsWith("POST<childwish>"))
                {                	
                	//Add the wish to the child wish data base and update the 
                	//child wish ID in the child data base for the added wish
                	clientMgr.addLogMessage(command);
                	String response = childwishDB.add(year, command.substring(15), clientUser);
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<add_giftlist>"))
                {                	
                	//Add the list of gifts to the child gift data base
                	clientMgr.addLogMessage(command);
                	String response = childwishDB.addListOfGifts(year, command.substring(18), clientUser);
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<add_newclonedgiftlist>"))
                {                	
                	//Add the list of gifts to the cloned gift data base
                	clientMgr.addLogMessage(command);
                	String response = clonedGiftDB.addListOfGifts(year, command.substring(27), clientUser, true);
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<add_clonedgiftlist>"))
                {                	
                	//Add the list of gifts to the cloned gift data base
                	clientMgr.addLogMessage(command);
                	String response = clonedGiftDB.addListOfGifts(year, command.substring(24), clientUser, false);
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<update_delivery>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = famHistoryDB.update(year, command.substring(21));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<add_delivery>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = famHistoryDB.add(year, command.substring(18), clientUser);
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<family_history_group>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = famHistoryDB.addFamilyHistoryList(year, command.substring(26), clientUser);
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<update_activity>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = activityDB.update(year, command.substring(21));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<delete_activity>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = activityDB.delete(year, command.substring(21));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<add_activity>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = activityDB.add(year, command.substring(18), clientUser);
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<update_driver>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = volunteerDB.update(year, command.substring(19));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<delete_driver>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = volunteerDB.delete(year, command.substring(19));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<add_driver>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = volunteerDB.add(year, command.substring(16), clientUser);
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<update_center>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = distributionCenterDB.update(year, command.substring(19), clientUser);
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<delete_center>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = distributionCenterDB.delete(year, command.substring(19));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<add_center>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = distributionCenterDB.add(year, command.substring(16), clientUser);
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<update_volunteer_activity>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = volunteerActivityDB.update(year, command.substring(31));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<delete_volunteer_activity>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = volunteerActivityDB.delete(year, command.substring(31));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<add_volunteer_activity>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = volunteerActivityDB.add(year, command.substring(28), clientUser);
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<volunteer_activity_group>"))
                {
                	clientMgr.addLogMessage(command);
                	List<String> responseList = volunteerActivityDB.addVolunteerActivityList(year, command.substring(30));
                	output.println(responseList);
                	clientMgr.notifyAllInYearClients(year, responseList);
                }
                
/* MEALS ARE NOT UPDATED OR DELETED BY DESKTOP CLIENTS - NEW MEALS ARE ADDED TO THE END OF THE LINKED LIST
                else if(command.startsWith("POST<update_meal>"))
                {
                		clientMgr.addLogMessage(command);
                		String response = mealDB.update(year, command.substring(17));
                		output.println(response);
                		clientMgr.addLogMessage(response);
                		clientMgr.dataChanged(this, response);
                }                
                else if(command.startsWith("POST<delete_meal>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = mealDB.delete(year, command.substring(17));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
*/                
                else if(command.startsWith("POST<add_adult>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = adultDB.add(year, command.substring(15), clientUser);
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<update_adult>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = adultDB.update(year, command.substring(18));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<delete_adult>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = adultDB.delete(year, command.substring(18));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<add_note>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = noteDB.add(year, command.substring(14), clientUser);
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<update_note>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = noteDB.update(year, command.substring(17));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<add_dnscode>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = serverDNSCodeDB.add(command.substring(17), clientUser);
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<update_dnscode>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = serverDNSCodeDB.update(command.substring(20), clientUser);
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<delete_notet>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = noteDB.delete(year, command.substring(17));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<add_meal>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = mealDB.add(year, command.substring(14), clientUser);
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<add_list_of_meals>"))
                {                	
                	//update the list of meals in the meal data base
                	clientMgr.addLogMessage(command);
                	String response = mealDB.addListOfMealChanges(year, command.substring(23), clientUser);
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<add_battery>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = batteryDB.add(year, command.substring(17), clientUser);
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<update_battery>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = batteryDB.update(year, command.substring(20));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<delete_battery>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = batteryDB.delete(year, command.substring(20));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherInYearClients(this, response);
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
                	clientMgr.notifyAllOtherInYearClients(this, response);
                }
                else if(command.startsWith("POST<update_dbyear>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = dbManager.updateDBYear(year, command.substring(19));
                	output.println(response);
                	clientMgr.addLogMessage(response);
//                	clientMgr.dataChanged(this, response);
                	clientMgr.notifyAllOtherClients(this, response);
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
                else if(command.startsWith("POST<sms_request>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = smsDB.processSMSRequest(command.substring(17));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                }
                else if(command.startsWith("POST<update_website_status>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = ONCSecureWebServer.setWebsiteStatus(command.substring(27));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                	clientMgr.notifyAllOtherClients(this, response);
                }
                else if(command.startsWith("POST<update_webpages>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = ONCWebpageHandler.reloadWebpagesAndWebfiles();
                	output.println(response);
                	clientMgr.addLogMessage(response);
                }
                else if(command.startsWith("POST<send_user_email>"))
                {
                	clientMgr.addLogMessage(command);
                	String response = userDB.createAndSendSeasonWelcomeEmail(year, command.substring(21));
                	output.println(response);
                	clientMgr.addLogMessage(response);
                }
                else if (command.startsWith("LOGOUT")) 
                {
                	String response = "GOODBYE";
                	output.println(response);
                	
                	if(clientUser != null)
                		clientUser.setClientID(-1);	//note that client has gone off-line
                	
                	String mssg = "GLOBAL_MESSAGE" + clientUser.getFirstName() + " " + clientUser.getLastName() + 
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
/*    	
    		try 
    		{
    			socket.close();
        } 
    		catch (IOException e) 
    		{
    			String logMssg = String.format("Client %d: Close Socket IOException: %s", id, e.getMessage());
    			clientMgr.addLogMessage(logMssg);
    		}
*/    		
    		try 
    		{
    			clientMgr.addLogMessage(String.format("Client %d: Closing Socket", id));
    		}
    		finally 
    		{
    		    try
    		    {
    		    		output.close();
    		    }
    		    finally 
    		    {
    		    		try 
    		    		{
    		    			try
					{
						input.close();
					}
					catch (IOException e)
					{
						String logMssg = String.format("Client %d: Close Input IOException: %s", id, e.getMessage());
		    				clientMgr.addLogMessage(logMssg);
					}
    		    		}
    		    		finally
    		    		{
    		    			try
					{
						socket.close();
					}
					catch (IOException e)
					{
						String logMssg = String.format("Client %d: Close Socket IOException: %s", id, e.getMessage());
		    				clientMgr.addLogMessage(logMssg);
					}
    		    		}
    		    }
    		}
    	}
    
    String loginRequest(String loginjson)
    {
    		Gson gson = new Gson();
    		Login lo = gson.fromJson(loginjson, Login.class);
 
    		String userID = ServerEncryptionManager.decrypt(lo.getUserID());
    		String password = ServerEncryptionManager.decrypt(lo.getPassword());
    	
    		float lo_version = Float.parseFloat(lo.getVersion());
  	
    		String value = "INVALID";
    		
//    		System.out.println(String.format("DeskClient.loginReq: submitted password= %s", password));
    	
    		//don't want a reference here, want a new object. A user can be logged in more than once.
    		//However, never can use this object to update a user's info
    		ONCServerUser serverUser = (ONCServerUser) userDB.find(userID);
    		
//    		if(serverUser != null)
//    			System.out.println(String.format("DeskClient.loginReq: serverUser Name= %s, id= %d, userPW= %s",
//    				serverUser.getUserID(), serverUser.getID(), serverUser.getUserPW()));
//    		else
//    			System.out.println("ServerUser is null");
  
    		if(lo_version < MINIMUM_CLIENT_VERSION)	//Is client connecting with current software?
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
    		else if(serverUser != null && serverUser.getStatus().equals(UserStatus.Inactive))	//can't find the user in the data base
    		{
    			clientMgr.clientLoginAttempt(false, String.format("Client %d login request failed with v%s:"
    				+ " User account is inactive", id, lo.getVersion()));
    			value += "Inactive account, contact Exec. Dir.";
    		}
    		else if(serverUser != null && !(serverUser.getAccess().equals(UserAccess.App) ||
    			serverUser.getAccess().equals(UserAccess.AppAndWebsite)))	//user doesn't have app access
    		{
    			clientMgr.clientLoginAttempt(false, String.format("Client %d login request failed with v%s:"
    				+ " Website only user account", id, lo.getVersion()));
    			value += "Website acct: use www.ourneighborschild.org";
    		}
    		else if(serverUser != null && !serverUser.pwMatch(password))	//found the user but pw is incorrect
    		{
    			clientMgr.clientLoginAttempt(false, String.format("Client %d login request failed with v%s:"
    				+ " Incorrect password", id, lo.getVersion()));
    			value += "Incorrect password, please try again";
    		}
    		else if(serverUser != null && serverUser.pwMatch(password))	//user found, password matches
    		{
    			//Create user json and attach to VALID response
    			state = ClientState.Logged_In;	//Client logged in
    			serverUser.incrementSessions();
    		
    			Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    			serverUser.setLastLogin(calendar.getTimeInMillis());
    		
    			userDB.requestSave();
    		
    			clientUser = serverUser.getUserFromServerUser();
    			clientUser.setClientID(id);	//set the user object client ID
    			version = lo.getVersion();
    		
    			String loginJson = gson.toJson(clientUser, ONCUser.class);
    			value = "VALID" + loginJson;
    		
    			String mssg = "UPDATED_USER" + loginJson;
    			clientMgr.notifyAllOtherClients(this, mssg);
    		
    			clientMgr.clientLoginAttempt(true, String.format("Client %d, %s %s login request sucessful",
    															id, clientUser.getFirstName(), clientUser.getLastName()));
    		}
    	
    		return value;
    }
    
    String[] getClientTableRow()
    {
    		String[] row = new String[9];
    		row[0] = Long.toString(id);
    	
    		if(clientUser != null)	//if server user is known, user their name
    		{
    			row[1] = clientUser.getFirstName();
    			row[2] = clientUser.getLastName();
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
    		if(clientUser.getFirstName().isEmpty())
    			return clientUser.getLastName();
    		else
    			return clientUser.getFirstName() + " " + clientUser.getLastName();
    }
    
    String setYear(String dbYear)
    {
    		int yr = Integer.parseInt(dbYear);
    		year = (yr >  BASE_YEAR) ? yr : BASE_YEAR;
    	
    		getClientUser().setClientYear(year);
    	
    		return "YEAR" + Integer.toString(year);
    }
    
    int getYear() { return year; }
}
