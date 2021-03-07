package oncserver;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

public class ServerMenuBar extends JMenuBar
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public JMenuItem showLogMI, countsMI, convertStatusMI, createHistMI, updateUserNameMI, createDelCardsMI, convertChildGiftsMI;

	public ServerMenuBar()
	{
		JMenu menuTools;	    
        
	    //Build the first menu.
	    menuTools = new JMenu("Server Tools");
	    this.add(menuTools);
	 
	   //a group of JMenuItems for the File Menu
	    showLogMI = new JMenuItem("Show Log");
	    menuTools.add(showLogMI);
	    
	    countsMI = new JMenuItem("Update PY Partner Performance");
	    countsMI.setEnabled(false);
	    menuTools.add(countsMI);
	    
	    convertStatusMI = new JMenuItem("Convert Partner DB");
	    convertStatusMI.setEnabled(true);
	    menuTools.add(convertStatusMI);
	    
	    createHistMI = new JMenuItem("Create Family History");
	    createHistMI.setEnabled(true);
	    menuTools.add(createHistMI);
	    
	    updateUserNameMI = new JMenuItem("Update User Name");
	    updateUserNameMI.setEnabled(true);
	    menuTools.add(updateUserNameMI);
	    
	    createDelCardsMI = new JMenuItem("Deliviery Cards");
	    createDelCardsMI.setEnabled(true);
	    menuTools.add(createDelCardsMI);
	    
	    convertChildGiftsMI = new JMenuItem("Convert Child Gifts");
	    convertChildGiftsMI.setEnabled(true);
	    menuTools.add(convertChildGiftsMI);
	}
}
