package oncserver;

import java.util.ArrayList;
import java.util.Map;

import ourneighborschild.ONCObject;

public class TwilioSMSReceive extends ONCObject
{
	private String AccountSid;
	private String MessageSid;
	private String Body;
	private String ToZip;
	private String ToCity;
	private String FromState;
	private String SmsSid;
	private String To;
	private String ToCountry;
	private String FromCountry;
	private String SmsMessageSid;
	private String ApiVersion;
	private String FromCity;
	private String SmsStatus;
	private String NumSegments;
	private String NumMedia;
	private String From;
	private String FromZip;
	private long timestamp;
	
	TwilioSMSReceive(Map<String,String> map)
	{
		super(-1);
		this.AccountSid = map.containsKey("AccountSid") ? map.get("AccountSid") : "";
		this.MessageSid = map.containsKey("MessageSid") ? map.get("MessageSid") : "";
		this.Body = map.containsKey("Body") ? map.get("Body") : "";
		this.ToZip = map.containsKey("ToZip") ? map.get("ToZip") : "";
		this.ToCity = map.containsKey("ToCity") ? map.get("ToCity") : "";
		this.FromState = map.containsKey("FromState") ? map.get("FromState") : "";
		this.SmsSid = map.containsKey("SmsSid") ? map.get("SmsSid") : "";
		this.To = map.containsKey("To") ? map.get("To") : "";
		this.ToCountry = map.containsKey("ToCountry") ? map.get("ToCountry") : "";
		this.FromCountry = map.containsKey("FromCountry") ? map.get("FromCountry") : "";
		this.SmsMessageSid = map.containsKey("SmsMessageSid") ? map.get("SmsMessageSid") : "";
		this.ApiVersion = map.containsKey("ApiVersion") ? map.get("ApiVersion") : "";
		this.FromCity = map.containsKey("FromCity") ? map.get("FromCity") : "";
		this.SmsStatus = map.containsKey("SmsStatus") ? map.get("SmsStatus") : "";
		this.NumSegments = map.containsKey("NumSegments") ? map.get("NumSegments") : "";
		this.NumMedia = map.containsKey("NumMedia") ? map.get("NumMedia") : "";
		this.From = map.containsKey("From") ? map.get("From") : "";
		this.FromZip = map.containsKey("FromZip") ? map.get("FromZip") : "";
		this.timestamp = System.currentTimeMillis();
	}
	
	//Constructor used when importing data base from CSV by the server
	public TwilioSMSReceive(String[] nextLine)
	{
		super(Integer.parseInt(nextLine[0]));
		this.AccountSid = nextLine[1];
		this.MessageSid = nextLine[2];
		this.Body = nextLine[3];
		this.ToZip = nextLine[4];
		this.ToCity = nextLine[5];
		this.FromState = nextLine[6];
		this.SmsSid = nextLine[7];
		this.To = nextLine[8];
		this.ToCountry = nextLine[9];
		this.FromCountry = nextLine[10];
		this.SmsMessageSid = nextLine[11];
		this.ApiVersion = nextLine[12];
		this.FromCity = nextLine[13];
		this.SmsStatus = nextLine[14];
		this.NumSegments = nextLine[15];
		this.NumMedia = nextLine[16];
		this.From = nextLine[17];
		this.FromZip = nextLine[18];
	}
	
	//getters
	String getAccountSid() { return AccountSid; }
	String getMessageSid() { return MessageSid; }
	String getBody() { return Body; }
	String getToZip() { return ToZip; }
	String getToCity() { return ToCity; }
	String getFromState() { return FromState; }
	String getSmsSid() { return SmsSid; }
	String getTo() {return To; }
	String getToCountry() { return ToCountry; }
	String getFromCountry() { return FromCountry; }
	String getSmsMessageSid() { return SmsMessageSid; }
	String getApiVersion() { return ApiVersion; }
	String getFromCity() { return FromCity; }
	String getSmsStatus() { return SmsStatus; }
	String getNumSegments() { return NumSegments; }
	String getNumMedia() { return NumMedia; }
	String getFrom() { return From; }
	String getFromZip() { return FromZip; }
	long getTimestamp() { return timestamp; }
	
	//setters
	void setAccountSid(String accountSid) { AccountSid = accountSid; }
	void setMessageSid(String messageSid) { MessageSid = messageSid; }
	void setBody(String body) { Body = body; }
	void setToZip(String toZip) { ToZip = toZip; }
	void setFromState(String fromState) { FromState = fromState; }
	void setSmsSid(String smsSid) { SmsSid = smsSid; }
	void setTo(String to) { To = to; }
	void setToCountry(String toCountry) { ToCountry = toCountry; }
	void setFromCountry(String fromCountry) { FromCountry = fromCountry; }
	void setSmsMessageSid(String smsMessageSid) { SmsMessageSid = smsMessageSid; }
	void setApiVersion(String apiVersion) { ApiVersion = apiVersion; }
	void setFromCity(String fromCity) { FromCity = fromCity; }
	void setSmsStatus(String smsStatus) { SmsStatus = smsStatus; }
	void setNumSegments(String numSegments) { NumSegments = numSegments; }
	void setNumMedia(String numMedia) { NumMedia = numMedia; }
	void setFrom(String from) { From = from; }
	void setFromZip(String fromZip) { FromZip = fromZip; }

	@Override
	public String[] getExportRow()
	{
		ArrayList<String> row = new ArrayList<String>();
		row.add(Integer.toString(id));
		row.add(AccountSid);
		row.add(MessageSid);
		row.add(Body);
		row.add(ToZip);
		row.add(ToCity);
		row.add(FromState);
		row.add(SmsSid);
		row.add(To);
		row.add(ToCountry);
		row.add(FromCountry);
		row.add(SmsMessageSid);
		row.add(ApiVersion);
		row.add(FromCity);
		row.add(SmsStatus);
		row.add(NumSegments);
		row.add(NumMedia);
		row.add(From);
		row.add(FromZip);
		row.add(Long.toString(timestamp));
		
		return row.toArray(new String[row.size()]);
	}
}
