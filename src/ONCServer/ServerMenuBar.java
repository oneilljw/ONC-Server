package ONCServer;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

public class ServerMenuBar extends JMenuBar
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public JMenuItem showLogMI, convertPYChildMI;

	public ServerMenuBar()
	{
		JMenu menuLog;	    
        
	    //Build the first menu.
	    menuLog = new JMenu("Server Tools");
	    this.add(menuLog);
	 
	   //a group of JMenuItems for the File Menu
	    showLogMI = new JMenuItem("Show Log");
	    menuLog.add(showLogMI);
	    
	    convertPYChildMI = new JMenuItem("Convert PY Child");
	    convertPYChildMI.setEnabled(false);
	    menuLog.add(convertPYChildMI);
	}
}
