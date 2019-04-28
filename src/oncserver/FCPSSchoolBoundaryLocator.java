package oncserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/***
 * Interface to FCPS Boundary Information System to retrieve school information. 
 * @author johnoneill
 */
public class FCPSSchoolBoundaryLocator
{
	private static final String FCPS_SCHOOL_LOCATOR_URL = "http://boundary.fcps.edu/boundary/";
	
	private static final String ADDR_VAL_VIEW_STATE = "/wEPDwUKMTIzMTMxMzM5OA9kFgICAw9kFgQCBQ8PFgIeB1Zpc2libGVoZBYCAgUPEGRkFgBkAgcPDxYCHwBoZBYEAgEPPCsAEQIBEBYAFgAWAAwUKwAAZAIFDzwrABECARAWABYAFgAMFCsAAGQYAgUWQWR2YW5jZWRTY2hvb2xHcmlkVmlldw9nZAUOU2Nob29sR3JpZFZpZXcPZ2QMF7si9WwtRRszrGdT8AqH0gMpves0jNNQewqyhmdS7A==";
	private static final String ADDR_VAL_EVENT_VALIDATION = "/wEdAAQt3GWdlwtCR3Tv5WPcHfJSJ4s8hImnUtb2BymF3SjDXYXKxO2sTKajWgcCSGt2n5LvLFxDxzB8xcQIIDgkwq/j3IbXPC+151k+4k4Pdl9/inGUuJbny1sDXY52/dbUNmc=";
	private static final String COMMON_VIEW_STATE_GENERATOR = "1C4E2778";
	
	private static final String ELEMENTARY_SCHOOL_SPAN_SELECTOR = "#SchoolGridView_Label4_0";
	private static final String MIDDLE_SCHOOL_SPAN_SELECTOR = "#SchoolGridView_Label4_1";
	private static final String HIGH_SCHOOL_SPAN_SELECTOR = "#SchoolGridView_Label4_2";
	
	private static final String VIEW_STATE_INPUT_NAME = "__VIEWSTATE";
	private static final String EVENT_VALIDATION_INPUT_NAME = "__EVENTVALIDATION";
	private static final String ADDRESS_INPUT_NAME = "AddressMatchesList";
	
	/****
	 * Fetches school information for a given address in Fairfax County using the School Boundary
	 * Locator web page served by the county. Retrieving school information is a two step process.
	 * The first step is a REST POST call to the web site to validate an address. If validated,
	 * the second step is a call to retrieve school information associated with the the address
	 * @param streetNumber - Numeric string. Non numeric strings are rejected
	 * @param streetName - Street name without a suffix such as RD, CT, etc.
	 * @return Map containing elementary, middle & high schools for address or error message
	 */
	Map<String,String> lookupSchoolInfo(String streetNumber, String streetName)
	{
		//check to see if street number is valid
		if(isNumeric(streetNumber))
		{
			try
			{
				//first step is to validate the address using the web site. Create the parameter map
				//to pass to the fist step method
				Map<String,String> params = new HashMap<String,String>();
				params.put("ViewState", ADDR_VAL_VIEW_STATE);
				params.put("EventValidation", ADDR_VAL_EVENT_VALIDATION);
				params.put("StreetNumber", streetNumber.toUpperCase());
				params.put("StreetName" , streetName.toUpperCase());
			
				Map<String,String> valAddrMap = getValidatedAddressInfo(params);
			
				//second step is to retrieve school info for the validated address from the web site
				if(valAddrMap.containsKey("Error"))
					return valAddrMap;
				else
					return getSchoolInfo(valAddrMap);
			}
			catch (ClientProtocolException e)
			{
				Map<String,String> errMap = new HashMap<String, String>();
				errMap.put("Error", e.getMessage());
				return errMap;
			}
			catch (IOException e)
			{
				Map<String, String> errMap = new HashMap<String, String>();
				errMap.put("Error", e.getMessage());
				return errMap;
			}
		}
		else
		{
			Map<String, String> errMap = new HashMap<String, String>();
			errMap.put("Error", String.format("Street Number %s is not numeric", streetNumber));
			return errMap;
		}
	}
	
	/***
	 * Fetches validated address, view state and event validation info from the FCPS Boundary Locator
	 * web site. This is the first step in looking up a school for an address. The web site first validates
	 * the address, then, after the user validates that's the address they're looking for, it returns
	 * school information for the address.
	 * @param map containing four parameters, view state, event validation, street number and street name.
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	private Map<String,String> getValidatedAddressInfo(Map<String,String> params) throws ClientProtocolException, IOException 
	{
		// Create unique form parameters
		List<NameValuePair> uniqueParams = new ArrayList<NameValuePair>();
		uniqueParams.add(new BasicNameValuePair("__LASTFOCUS", ""));
		uniqueParams.add(new BasicNameValuePair("__VIEWSTATE", params.get("ViewState")));
		uniqueParams.add(new BasicNameValuePair("__EVENTVALIDATION", params.get("EventValidation")));
		uniqueParams.add(new BasicNameValuePair("StreetNumber", params.get("StreetNumber")));
		uniqueParams.add(new BasicNameValuePair("StreetName", params.get("StreetName")));
		uniqueParams.add(new BasicNameValuePair("EnterButton", "Enter"));
		
		//get the HTML from the FCPS boundary locator web site
		String responseHtml = getFCPSBoundaryLocatorHTML(uniqueParams);
		
		if(responseHtml == null || responseHtml.isEmpty() || responseHtml.startsWith("Error"))
		{
			Map<String, String> errMap = new HashMap<String,String>();
			errMap.put("Error", "Failed Post");
			return errMap;
		}
		else	 //scrape the HTML to obtain the validated address map
			return scrapeValidatedAddressHTML(responseHtml.toString());
	}
	
	private Map<String,String> getSchoolInfo(Map<String,String> valAddrMap) throws ClientProtocolException, IOException 
	{
		// Create unique parameters for school boundary lookup
		List<NameValuePair> uniqueParams = new ArrayList<NameValuePair>();
		uniqueParams.add(new BasicNameValuePair("__VIEWSTATE", valAddrMap.get("ViewState")));
		uniqueParams.add(new BasicNameValuePair("__EVENTVALIDATION", valAddrMap.get("EventValidation")));
		uniqueParams.add(new BasicNameValuePair("AddressMatchesList", valAddrMap.get("ValidatedAddress")));
		uniqueParams.add(new BasicNameValuePair("SubmitButton", "Submit"));
		
		//get the HTML from the FCPS bounary locator web site
		String responseHtml = getFCPSBoundaryLocatorHTML(uniqueParams);

		//If proper response, scrape the HTML into a map and return it
		if(responseHtml == null || responseHtml.isEmpty() || responseHtml.startsWith("Error"))
		{
			Map<String, String> errMap = new HashMap<String,String>();
			errMap.put("Error", "Failed Post");
			return errMap;
		}
		else
			return scrapeSchoolBoundaryHTML(responseHtml.toString(), valAddrMap.get("ValidatedAddress"));
	}
	
	/**
	 *  Uses the JSoup library and takes HTML return from the first call to validate the address
	 *  and parses it to return three strings needed to make the next call to the boundary locator 
	 *  populated into a map. Three map keys are "ViewState", "EventValidation" and "ValidatedAddress". 
	 *  If an error is detected, a single entry map is returned with an "Error" key.
	*/
	private Map<String,String> scrapeValidatedAddressHTML(String html) 
	{
		// Read HTML into Jsoup document
		Document doc = Jsoup.parse(html);
			
		//parse document for the three inputs we need to request school boundary info
		Element viewStateInput = doc.select("input[name=" + VIEW_STATE_INPUT_NAME + "]").first();
		Element eventValidationInput = doc.select("input[name=" + EVENT_VALIDATION_INPUT_NAME + "]").first();
		Element addressInput = doc.select("input[name=" + ADDRESS_INPUT_NAME + "]").first();
		
		if(viewStateInput != null && eventValidationInput != null && addressInput != null)
		{
			Map<String,String> valAddrMap = new HashMap<String,String>();
			valAddrMap.put("ViewState", viewStateInput.attr("value"));
			valAddrMap.put("EventValidation", eventValidationInput.attr("value"));
			valAddrMap.put("ValidatedAddress", addressInput.attr("value"));
			return valAddrMap;
		}
		else 
		{
			Map<String, String> errMap = new HashMap<String,String>();
			errMap.put("Error", "Address validation returned invalid format");
			return errMap;
		}		
	}
		
	private Map<String,String> scrapeSchoolBoundaryHTML(String html, String valAddress) 
	{
		Map<String,String> resultMap = new HashMap<String,String>();
		
		// Read HTML into Jsoup document
		Document doc = Jsoup.parse(html);
		
		//parse result
		Element elemSchoolElem = doc.select(ELEMENTARY_SCHOOL_SPAN_SELECTOR).first();
		Element middleSchoolElem = doc.select(MIDDLE_SCHOOL_SPAN_SELECTOR).first();
		Element highSchoolElem = doc.select(HIGH_SCHOOL_SPAN_SELECTOR).first();
	
		if(elemSchoolElem == null)
			resultMap.put("Error", "Elementary school not found for " + valAddress);
		else if(middleSchoolElem == null)
			resultMap.put("Error", "Middle school not found for " + valAddress);
		else if(highSchoolElem == null)
			resultMap.put("Error", "High school not found for " + valAddress);
		else
		{
			resultMap.put("ElementarySchool", elemSchoolElem.text());
			resultMap.put("MiddleSchool", middleSchoolElem.text());
			resultMap.put("HighSchool", highSchoolElem.text());
		}
		
		return resultMap;
	}
	
	/***
	 * Singular interface to fetch HTML from FCPS Boundary Locator web page.
	 * Retrieving school information from the web page is a two step process. The first step
	 * validates the address, the second step retrieves school information for the address.
	 * This common method supports both steps.  
	 * @param params - unique parameters for each step
	 * @return - HTML String or a String that starts with "Error";
	 * @throws IOException 
	 * @throws ClientProtocolException 
	 */
	private String getFCPSBoundaryLocatorHTML(List<NameValuePair> params) throws ClientProtocolException, IOException
	{
		// Create the HTTP Client
		HttpClient httpClient = HttpClients.createDefault();
				
		// Create an HTTP Post object to send
		HttpPost httpPost = new HttpPost(FCPS_SCHOOL_LOCATOR_URL);
						
		//add the common four parameters
		params.add(new BasicNameValuePair("ToolkitScriptManager1_HiddenField", ""));
		params.add(new BasicNameValuePair("__EVENTTARGET", ""));
		params.add(new BasicNameValuePair("__EVENTARGUMENT", ""));
		params.add(new BasicNameValuePair("__VIEWSTATEGENERATOR", COMMON_VIEW_STATE_GENERATOR));
						
		// Set Form in POST
		httpPost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
						
		// Send POST
		HttpResponse response = httpClient.execute(httpPost);
						
		// Get POST status
		StatusLine statusLine = response.getStatusLine();
						
		// Get POST Response
		HttpEntity entity = response.getEntity();
						
		// Ensure we received a valid response
		if(entity == null)
			return String.format("Error: FCPS Boundary Website %s did not respond", FCPS_SCHOOL_LOCATOR_URL);
		else if(statusLine.getStatusCode() != 200)
			return String.format("Error: FCPS Boundary Website %s returned error: response code= d)",
					FCPS_SCHOOL_LOCATOR_URL, statusLine.getStatusCode());
		else
		{
			// read resulting HTML
			BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
							
			String inputLine;
			StringBuffer responseHtml = new StringBuffer();
			while ((inputLine = in.readLine()) != null) 
				responseHtml.append(inputLine);
							
			in.close();
							
			// Scrap the HTML String into a map and return it
			return responseHtml.toString();
		}		
	}
	
	static boolean isNumeric(String str)
	{
		if(str == null || str.isEmpty())
			return false;
		else
			return str.matches("-?\\d+(\\.\\d+)?");  //match a number with optional '-' and decimal.
	}
}
