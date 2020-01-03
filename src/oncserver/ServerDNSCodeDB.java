package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ourneighborschild.DNSCode;
import ourneighborschild.ONCObject;
import ourneighborschild.ONCUser;

public class ServerDNSCodeDB extends ServerPermanentDB
{
	private static final String DNSCODE_DB_FILENAME = "DNSCodeDB.csv";
	private static final int DNSCODE_RECORD_LENGTH = 9;
	
	private static ServerDNSCodeDB instance = null;
	
	private static List<DNSCode> dnsCodeList;
	
	private ServerDNSCodeDB() throws FileNotFoundException, IOException
	{
		dnsCodeList = new ArrayList<DNSCode>();
		importDB(String.format("%s/PermanentDB/%s", System.getProperty("user.dir"), DNSCODE_DB_FILENAME), "Group DB", DNSCODE_RECORD_LENGTH);
		nextID = getNextID(dnsCodeList);
		bSaveRequired = false;
	}
	
	public static ServerDNSCodeDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerDNSCodeDB();
		
		return instance;
	}
	
	//return the group list as a json
	String getDNSCodes()
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<DNSCode>>(){}.getType();
				
		String response = gson.toJson(dnsCodeList, listtype);
		return response;	
	}
	
	DNSCode getDNSCode(int id)
	{
		int index = 0;
		while(index < dnsCodeList.size() && dnsCodeList.get(index).getID() != id)
			index++;
		
		return index < dnsCodeList.size() ? dnsCodeList.get(index) : new DNSCode();
	}
	
	DNSCode getDNSCode(String acronym)
	{
		int index = 0;
		while(index < dnsCodeList.size() && !dnsCodeList.get(index).getAcronym().equals(acronym))
			index++;
		
		return index < dnsCodeList.size() ? dnsCodeList.get(index) : new DNSCode();
	}
	
	static HtmlResponse getDNSCodeJSONP(String code, String callbackFunction)
	{		
		Gson gson = new Gson();
		
		String response;
		if(isNumeric(code))
		{
			int codeID = Integer.parseInt(code);
			int index = 0;
			while(index < dnsCodeList.size() && dnsCodeList.get(index).getID() != codeID)
				index++;
		
			if(index < dnsCodeList.size())
				response = gson.toJson(dnsCodeList.get(index), DNSCode.class);
			else
				response = gson.toJson(new DNSCode(), DNSCode.class);
		}
		else
		{
			DNSCode badCodeReq = new DNSCode(-1, "BAD", "Invalid DNS Code Request", "Non Numeric DNS Code Request cannnot be processed");
			response = gson.toJson(badCodeReq, DNSCode.class);
		}
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HttpCode.Ok);		
	}
	
	@Override
	String add(String json, ONCUser client)
	{
		//Create a dnsCode object to add to the catalog from the json
		Gson gson = new Gson();
		DNSCode addDNSCodeReq = gson.fromJson(json, DNSCode.class);
		
		addDNSCodeReq.setID(nextID++);
		addDNSCodeReq.setDateChanged(System.currentTimeMillis());
		addDNSCodeReq.setChangedBy(client.getLNFI());
		dnsCodeList.add(addDNSCodeReq);
		bSaveRequired = true;
		
		return "ADDED_DNSCODE" + gson.toJson(addDNSCodeReq, DNSCode.class);
	}
	
	String update(String json, ONCUser client)
	{
		//Extract the dnsCode object to update from the json
		Gson gson = new Gson();
		DNSCode reqDNSCode = gson.fromJson(json, DNSCode.class);
		
		//Find the position for the current group object being replaced
		int index = 0;
		while(index < dnsCodeList.size() && dnsCodeList.get(index).getID() != reqDNSCode.getID())
			index++;
		
		//If group is located, replace the wish with the update.
		if(index == dnsCodeList.size()) 
		{
			return "UPDATE_FAILED";
		}
		else
		{
			reqDNSCode.setDateChanged(System.currentTimeMillis());
			reqDNSCode.setChangedBy(client.getLNFI());
			dnsCodeList.set(index, reqDNSCode);
			bSaveRequired = true;
			return "UPDATED_DNSCODE" + gson.toJson(reqDNSCode, DNSCode.class);
		}
	}

	@Override
	void addObject(String type, String[] nextLine) 
	{
		dnsCodeList.add(new DNSCode(nextLine));		
	}

	@Override
	String[] getExportHeader()
	{
		return new String[] {"ID", "Acronym", "Title", "Definition", "Date Changed", "Changed By",
								"SL Position", "SL Message", "SL Changed By"};
	}

	@Override
	String getFileName() { return DNSCODE_DB_FILENAME; }

	@Override
	List<? extends ONCObject> getONCObjectList() { return dnsCodeList; }
	
	static String getDNSCodeHTML()
	{
		StringBuffer buff = new StringBuffer("<dl>");
		for(DNSCode code : dnsCodeList)
			buff.append(String.format("<dt>%s - %s</dt><dd>%s</dd>", code.getAcronym(),
					code.getName(), code.getDefinition()));
		
		buff.append("</dl>");
		return buff.toString();
	}
}
