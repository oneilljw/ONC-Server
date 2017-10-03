package oncserver;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.text.DefaultCaret;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import ourneighborschild.ONCUser;

public class ServerUI extends JPanel implements ClientListener
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final int NUM_ROWS_TO_DISPLAY = 8;
	private static final int LOG_TEXT_FONT_SIZE = 13;
	
	private static final int CLIENT_ID_COL = 0;
	private static final int CLIENT_FN_COL = 1;
	private static final int CLIENT_LN_COL = 2;
	private static final int CLIENT_PERM_COL = 3;
	private static final int CLIENT_STATE_COL = 4;
	private static final int CLIENT_HB_COL = 5;
	private static final int CLIENT_YEAR_COL = 6;
	private static final int CLIENT_VER_COL = 7;
	private static final int CLIENT_TIMESTAMP_COL = 8;
	
	private static ServerUI instance = null;	//Only one UI
	private transient ImageIcon imageIcons[];
	public JButton btnStartServer, btnStopServer;
	private JTextArea logTA;
	private JRadioButton rbStoplight;
	private JTable desktopClientTable, websiteClientTable;
	private DesktopClientTableModel desktopClientTM;
	private WebClientTableModel webClientTM;
	
	private ClientManager clientMgr;

	public static ServerUI getInstance()
	{
		if(instance == null)
			instance = new ServerUI();
		
		return instance;
	}
	
	private ServerUI()
	{
		clientMgr = ClientManager.getInstance();
		if(clientMgr != null)
			clientMgr.addClientListener(this);
		
		//Layout User I/F
		initIcons();

		JPanel statusPanel = new JPanel();
		statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.X_AXIS));
		JPanel statusPanelLeft = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JPanel statusPanelRight = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		
		JLabel lblONCicon = new JLabel(imageIcons[0]);
		statusPanelLeft.add(lblONCicon);
	    
		rbStoplight = new JRadioButton(imageIcons[4]);
    	rbStoplight.setToolTipText("");
    	statusPanelRight.add(rbStoplight);
    	
    	statusPanel.add(statusPanelLeft);
    	statusPanel.add(statusPanelRight);
    	
    	//Set up the desktop client table panel and website client table panel
    	JPanel desktoptablepanel = new JPanel();
    	desktoptablepanel.setLayout(new BorderLayout());
    	
    	desktopClientTM = new DesktopClientTableModel();
    	desktopClientTable = new JTable();

    	//Set the table model, select ability to select multiple rows and add a listener to 
    	//check if the user has selected a row. 
    	desktopClientTable.setModel(desktopClientTM);
    	desktopClientTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    	//Set table column widths
    	int tablewidth = 0;
    	int[] colWidths = {40, 80, 80, 80, 80, 28, 40, 52, 140};
    	for(int i=0; i < colWidths.length; i++)
    	{
    		desktopClientTable.getColumnModel().getColumn(i).setPreferredWidth(colWidths[i]);
    		tablewidth += colWidths[i];
    	}
   		tablewidth += 24; 	//Account for vertical scroll bar

    	//Set up the table header
    	JTableHeader anHeader = desktopClientTable.getTableHeader();
    	anHeader.setForeground( Color.black);
    	anHeader.setBackground( new Color(161,202,241));

    	//Center cell entries for specified cells
    	DefaultTableCellRenderer dtcr = new DefaultTableCellRenderer();    
    	dtcr.setHorizontalAlignment(SwingConstants.CENTER);
    	int [] center_cols = {0, 3, 5};
    	for(int i=0; i<center_cols.length; i++)
    		desktopClientTable.getColumnModel().getColumn(center_cols[i]).setCellRenderer(dtcr);

    	desktopClientTable.setFillsViewportHeight(true);
    	desktoptablepanel.add(anHeader, BorderLayout.NORTH);
    	desktoptablepanel.add(desktopClientTable, BorderLayout.CENTER);
    	desktoptablepanel.setPreferredSize(new Dimension(tablewidth, desktopClientTable.getRowHeight()*NUM_ROWS_TO_DISPLAY));

    	JPanel websitetablepanel = new JPanel();
    	websitetablepanel.setLayout(new BorderLayout());
    	
    	webClientTM = new WebClientTableModel();
    	websiteClientTable = new JTable();

    	//Set the table model, select ability to select multiple rows and add a listener to 
    	//check if the user has selected a row. 
    	websiteClientTable.setModel(webClientTM);
    	
    	websiteClientTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    	for(int i=0; i < colWidths.length; i++)
    	{
    		websiteClientTable.getColumnModel().getColumn(i).setPreferredWidth(colWidths[i]);
    	}

    	//Set up the table header
    	anHeader = websiteClientTable.getTableHeader();
    	anHeader.setForeground( Color.black);
    	anHeader.setBackground( new Color(161,202,241));

    	//Center cell entries for specified cells
    	dtcr = new DefaultTableCellRenderer();    
    	dtcr.setHorizontalAlignment(SwingConstants.CENTER);
    	for(int i=0; i<center_cols.length; i++)
    		websiteClientTable.getColumnModel().getColumn(center_cols[i]).setCellRenderer(dtcr);

    	websiteClientTable.setFillsViewportHeight(true);
    	websitetablepanel.add(anHeader, BorderLayout.NORTH);
    	websitetablepanel.add(websiteClientTable, BorderLayout.CENTER);
    	websitetablepanel.setPreferredSize(new Dimension(tablewidth, websiteClientTable.getRowHeight()*NUM_ROWS_TO_DISPLAY));			
    	
    	//Set up the client log pane
    	logTA = new JTextArea();
  	   	logTA.setEditable(false);
  	   
        //create the paragraph attributes
        SimpleAttributeSet paragraphAttribs = new SimpleAttributeSet();  
        StyleConstants.setAlignment(paragraphAttribs , StyleConstants.ALIGN_LEFT);
        StyleConstants.setFontSize(paragraphAttribs, LOG_TEXT_FONT_SIZE);
        StyleConstants.setSpaceBelow(paragraphAttribs, 3);

        DefaultCaret caret = (DefaultCaret) logTA.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
  	   	
	    //Create the ODB Wish List scroll pane and add the Wish List text pane to it.
        JScrollPane logScrollPane = new JScrollPane(logTA);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("Server Log"));
        logScrollPane.setPreferredSize(new Dimension(tablewidth, 180));
        
        //Set up the control panel
        JPanel cntlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        btnStartServer = new JButton("Start Server");
        btnStartServer.setVisible(false);
        
        btnStopServer = new JButton("Stop Server");
        
        cntlPanel.add(btnStopServer);
        cntlPanel.add(btnStartServer);
        
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); 
        this.add(statusPanel);
        this.add(desktoptablepanel);
        this.add(websitetablepanel);
        this.add(logScrollPane);
        this.add(cntlPanel);
        
        this.setMinimumSize(new Dimension(tablewidth, 600));
	}
	
	/** Initialize icons */
	void initIcons()
	{
		imageIcons = new ImageIcon[5];
		
		//ONC Server logo
		imageIcons[0] = createImageIcon("oncserverlogosmall.png", "ONC Logo");
		
		//Icons used for stop light status
		imageIcons[1] = createImageIcon("traffic-lights-green-icon.gif", "Green Light Icon");
		imageIcons[2] = createImageIcon("traffic-lights-yellow-icon.gif", "Yellow Light Icon");
		imageIcons[3] = createImageIcon("traffic-lights-red-icon.gif", "Red Light Icon");
		imageIcons[4] = createImageIcon("traffic-lights-off-icon.gif", "Off Light Icon");	
	}

	void addLogMessage(String mssg)
	{
		Calendar timestamp = Calendar.getInstance();
		
		String line = new SimpleDateFormat("MM/dd/yy H:mm:ss.S").format(timestamp.getTime());
		
		logTA.append(line + ": " + mssg + "\n");
		logTA.setCaretPosition(logTA.getDocument().getLength());
	}
	
	void setStoplight(int pos)	//0-green, 1-yellow, 2-red, 3-off
	{
		if(pos >= 0 && pos < 4)
			rbStoplight.setIcon(imageIcons[pos+1]);
	}
	
	ImageIcon getIcon(int index) { return imageIcons[index]; }
	
	 /** Returns an ImageIcon, or null if the path was invalid. */
	ImageIcon createImageIcon(String path, String description)
	{
		java.net.URL imgURL = getClass().getResource(path);
		if (imgURL != null) { return new ImageIcon(imgURL, description); } 
		else { System.err.println("Couldn't find file: " + path); return null; }
	}
	
	@Override
	public void clientChanged(ClientEvent ce) 
	{
		if(ce.getEventType() == ClientEventType.MESSAGE)
		{
			String mssg = (String) ce.getObject1();
			addLogMessage(mssg);
		}
		else if(ce.getClientType() == ClientType.DESKTOP)
		{
			desktopClientTM.fireTableDataChanged();
		}
		else if(ce.getClientType() == ClientType.WEB)
		{
			webClientTM.fireTableDataChanged();
		}
	}
	
	class DesktopClientTableModel extends AbstractTableModel
	{
        /**
		 * Implements the table model for the Online UserDialog
		 */
		private SimpleDateFormat sdf;
		private ClientManager clientMgr;
		
		public DesktopClientTableModel()
		{
			sdf = new SimpleDateFormat("MM/dd H:mm:ss");
			clientMgr = ClientManager.getInstance();
		}
		
		private static final long serialVersionUID = 1L;
		
		private String[] columnNames = {"ID", "First Name", "Last Name", 
										"Perm", "State", "HB", "Year", "Ver", "Time Stamp" };
 
        public int getColumnCount() { return columnNames.length; }
 
        public int getRowCount() { return clientMgr.getDesktopClientList().size(); }
 
        public String getColumnName(int col) { return columnNames[col]; }
 
        public Object getValueAt(int row, int col)
        {
        	DesktopClient dc = clientMgr.getDesktopClientList().get(row);
        	ONCUser u = dc.getClientUser();
        	
        	if(col == CLIENT_ID_COL)  
        		return dc.getClientID();
        	else if(col == CLIENT_FN_COL)
        		return u != null ? u.getFirstName() : "Anonymous";
        	else if(col == CLIENT_LN_COL)
        		return u != null ? u.getLastName() : "Anonymous";
        	else if(col == CLIENT_PERM_COL)
        		return u != null ? u.getPermission().toString() : "U";
        	else if(col == CLIENT_STATE_COL)
        		return dc.getClientState();
        	else if (col == CLIENT_HB_COL)
        		return dc.getClientHeartbeat().toString().substring(0,1);
        	else if (col == CLIENT_YEAR_COL)
        		return dc.getClientState() == ClientState.DB_Selected ? Integer.toString(dc.getYear()) : "None";
        	else if (col == CLIENT_VER_COL)
        		return dc.getClientVersion();
        	else if (col == CLIENT_TIMESTAMP_COL)
        		return sdf.format(dc.getClientTimestamp());
        	else
        		return "Error";
        }
        
        //JTable uses this method to determine the default renderer/editor for each cell.
        @Override
        public Class<?> getColumnClass(int column)
        {
        	return String.class;
        }
 
        public boolean isCellEditable(int row, int col)
        {
        	return false;
        }
    }
	
	class WebClientTableModel extends AbstractTableModel
	{
        /**
		 * Implements the table model for the Online UserDialog
		 */
		private SimpleDateFormat sdf;
		private ClientManager clientMgr;
		
		public WebClientTableModel()
		{
			sdf = new SimpleDateFormat("MM/dd H:mm:ss");
			clientMgr = ClientManager.getInstance();
		}
		
		private static final long serialVersionUID = 1L;
		
		private String[] columnNames = {"ID", "First Name", "Last Name", 
										"Perm", "State", "HB", "Year", "Ver", "Time Stamp" };
 
        public int getColumnCount() { return columnNames.length; }
 
        public int getRowCount() { return clientMgr.getWebClientList().size(); }
 
        public String getColumnName(int col) { return columnNames[col]; }
 
        public Object getValueAt(int row, int col)
        {
        	WebClient wc = clientMgr.getWebClientList().get(row);
        	ONCUser u = wc.getWebUser();
        	Calendar timestamp = Calendar.getInstance();
        	
        	if(col == CLIENT_ID_COL)  
        		return "0";
        	else if(col == CLIENT_FN_COL)
        		return u != null ? u.getFirstName() : "Anonymous";
        	else if(col == CLIENT_LN_COL)
        		return u != null ? u.getLastName() : "Anonymous";
        	else if(col == CLIENT_PERM_COL)
        		return u != null ? u.getPermission().toString() : "U";
        	else if(col == CLIENT_STATE_COL)
        		return wc.getClientState();
        	else if (col == CLIENT_HB_COL)
        		return "O";
        	else if (col == CLIENT_YEAR_COL)
            	return Integer.toString(timestamp.get(Calendar.YEAR));
        	else if (col == CLIENT_VER_COL)
        		return "Web";
        	else if (col == CLIENT_TIMESTAMP_COL)
        	{
        		timestamp.setTimeInMillis(wc.getloginTimeStamp());
            	return sdf.format(timestamp.getTime());
        	}
        	else
        		return "Error";
        }
        
        //JTable uses this method to determine the default renderer/editor for each cell.
        @Override
        public Class<?> getColumnClass(int column)
        {
        	return String.class;
        }
 
        public boolean isCellEditable(int row, int col)
        {
        	return false;
        }
    }
}
