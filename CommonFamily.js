/*!
 * ONC Common Family JavaScript library v1.2
 * Common functions for family referral and family info editing web pages
 * Date: 2018-07-23
 */
var schools = ["Brookfield ES","Bull Run ES","Centre Ridge ES","Centreville ES","Centreville HS",
  					"Chantilly Acadamy","Chantilly HS","Cub Run ES","Deer Park ES","Floris ES","Franklin MS",
  					"Greenbriar East ES","Greenbriar West ES","Homeschooled", "Lees Corner ES","Lutie Lewis Coates ES",
  					"Liberty MS","London Towne ES","McNair ES","Mountain View HS","None","Oak Hill ES","Poplar Tree ES",
  					"Powell ES", "Pre-K","Rocky Run MS","Stone MS", "Union Mill ES", "Virginia Run ES", 
  					"Westfield HS","Willow Springs ES"];

function updateChildTable(bAction)
{
    $("#tchildbody").empty();
    	
    for(var i=0; i<childrenJson.length; i++)
	{
    		addChildTableRow(i, childrenJson[i], bAction);	//add row to table
	}
   
    $( function() {
    		$( ".schoolname" ).autocomplete({
    			source: schools
    		});
    	});
    
    $('ul.ui-autocomplete.ui-menu').css({fontSize: '12px', width: '300px'});
}
function updateAdultTable(bAction)
{
	$("#tadultbody").empty();
	
	for(var i=0; i<adultsJson.length; i++)
	{
		addAdultTableRow(i, adultsJson[i], bAction);	//add row to table
	}
}

function addChildTableRow(cnum, child, bAction)
{
    var childinfo = [child.firstname, child.lastname, child.sDOB, child.gender, child.school];
    var fieldname = ["childfn", "childln", "childdob", "childgender", "childschool"];
    var fieldsize = [15, 16, 11, 11, 20];
    
    var tabBody = document.getElementById("childtable").getElementsByTagName('tbody').item(0);
    row=document.createElement("tr");
	
    for(index=0; index < childinfo.length; index++)	//create the child info cells
    {
    		cell= document.createElement("td");
	    content= document.createElement("input");
	    	content.type="text";
	    	content.id=fieldname[index] + cnum;
	    	content.name=fieldname[index] + cnum;
	    	content.readOnly=!bAction;
	    	content.value = childinfo[index];
	    	content.setAttribute("size", fieldsize[index]);
	    	
	    	if(fieldname[index] === 'childschool')
	    		content.className = 'schoolname';
    		
	    	cell.appendChild(content);
	    	row.appendChild(cell);
    }
    
    if(bAction === true)
    {
    		btn = document.createElement("button");
    		btn.value=cnum;
    		btn.type="button";
    		btn.innerHTML = "Remove";
    		btn.onclick=function() {removeChild(cnum);};
    		row.appendChild(btn);
    }
    
    tabBody.appendChild(row);
}
function addAdultTableRow(anum, adult, bAction)
{
    var tabBody = document.getElementById("adulttable").getElementsByTagName('tbody').item(0);
    row=document.createElement("tr");
    
    cell= document.createElement("td");
    adultname= document.createElement("input");
    adultname.type="text";
    adultname.id="adultname" + anum;
    adultname.name="adultname" + anum;
    adultname.value = adult.name;
    adultname.readOnly = !bAction;
    adultname.setAttribute("size", 27);
    cell.appendChild(adultname);
    row.appendChild(cell);
    
    cell= document.createElement("td");
    var adultgender= document.createElement("input");
    adultgender.type="text";
    adultgender.id="adultgender" + anum;
    adultgender.name="adultgender" + anum;
    adultgender.value = adult.gender;
    adultgender.setAttribute("size", 11);
    cell.appendChild(adultgender);
    row.appendChild(cell);
    
    if(bAction === true)
    {	
    		var btn = document.createElement("button");
    		btn.value= anum;
    		btn.type="button";
    		btn.innerHTML = "Remove";
    		btn.onclick=function() {removeAdult(anum);};
    		row.appendChild(btn);
	}
    
    tabBody.appendChild(row);
}
function sameAddressClicked(cb)
{	
	if(cb.checked == true)	//copy the address input elements to delivery address elements and verify
	{	
		copyAddressToDeliveryAddress();
		$( ".address" ).tooltip( "option", "disabled", false);
	}
	else	//clear the delivery address elements
	{
		clearDeliveryAddress();
		$( ".address" ).tooltip( "option", "disabled", true);
	}
}
  	
function copyAddressToDeliveryAddress()
{
  	var addrInputElements = [document.getElementById('housenum'),
			                    document.getElementById('street'),
			                    document.getElementById('unit')];
	
	var addrSelectElements = [document.getElementById('city'),
	                          document.getElementById('zipcode')];
	                          
	var delInputElements = [document.getElementById('delhousenum'),
			                 document.getElementById('delstreet'),
			                 document.getElementById('delunit')];
	
	var delSelectElements = [document.getElementById('delcity'),
	                          document.getElementById('delzipcode')];
	
	//copy the address input elements to delivery address elements
	for(var i=0; i<addrInputElements.length; i++)
		delInputElements[i].value = addrInputElements[i].value;
	
	delSelectElements[0].value = addrSelectElements[0].value;
	cityChanged('delcity');
	delSelectElements[1].value = addrSelectElements[1].value;
		
	//set read only
	for(var i=0; i<addrInputElements.length; i++)
	{
		delInputElements[i].readOnly = true;
		addrInputElements[i].readOnly = true;
	}
}
  	
function clearDeliveryAddress()
{
  	var addrInputElements = [document.getElementById('housenum'),
			                 document.getElementById('street'),
				             document.getElementById('unit')];
		                          
	var delInputElements = [document.getElementById('delhousenum'),
				            document.getElementById('delstreet'),
				            document.getElementById('delunit')];
		
	var delSelectElements = [document.getElementById('delcity'),
		                     document.getElementById('delzipcode')];
		
	//clear the delivery address elements
	for(var i=0; i<delInputElements.length; i++)
		delInputElements[i].value = "";
		
		delSelectElements[0].selectedIndex = 0;
		cityChanged('delcity');
		delSelectElements[1].selectedIndex = 0;
	
		//clear read only for delivery input elements
		for(var i=0; i<addrInputElements.length; i++)
		{
			delInputElements[i].readOnly = false;
			addrInputElements[i].readOnly = false;
		}
		
		//clear background color
		changeAddressBackground(delInputElements, '#FFFFFF');
		changeAddressBackground(delSelectElements, '#FFFFFF');
  	}
	
function cityChanged(elementName)
{
	if(elementName === 'city')
		var zipElement = document.getElementById('zipcode')
	else
		var zipElement = document.getElementById('delzipcode')	
			
	removeOptions(zipElement);
	
	var cityElement = document.getElementById(elementName);
	
	if(cityElement.value === 'Centreville')
	{
		zipElement.options[0] = new Option('20120', '20120');
		zipElement.options[1] = new Option('20121', '20121');
	}
	else if(cityElement.value === 'Chantilly')
	{
		zipElement.options[0] = new Option('20151', '20151');
	}
	else if(cityElement.value === 'Clifton')
	{
		zipElement.options[0] = new Option('20124', '20124');
	}
	else if(cityElement.value === 'Fairfax')
	{
		zipElement.options[0] = new Option('20151', '20151');
		zipElement.options[1] = new Option('22030', '22030');
		zipElement.options[2] = new Option('22031', '22031');
		zipElement.options[3] = new Option('22032', '22032');
		zipElement.options[4] = new Option('22033', '22033');
		zipElement.options[5] = new Option('22034', '22034');
		zipElement.options[6] = new Option('22035', '22035');
		zipElement.options[7] = new Option('22036', '22036');
		zipElement.options[8] = new Option('22037', '22037');
		zipElement.options[9] = new Option('22038', '22038');
	}
	else if(cityElement.value === 'Fairfax Station')
	{
		zipElement.options[0] = new Option('22039', '22039');
	}
	else if(cityElement.value === 'Alexandria')
	{
		zipElement.options[0] = new Option('22302', '22302');
		zipElement.options[1] = new Option('22303', '22303');
		zipElement.options[2] = new Option('22304', '22304');
		zipElement.options[3] = new Option('22306', '22306');
		zipElement.options[4] = new Option('22307', '22307');
		zipElement.options[5] = new Option('22308', '22308');
		zipElement.options[6] = new Option('22309', '22309');
		zipElement.options[7] = new Option('22310', '22310');
		zipElement.options[8] = new Option('22311', '22311');
		zipElement.options[9] = new Option('22312', '22312');
		zipElement.options[10] = new Option('22315', '22315');
	}
	else if(cityElement.value === 'Annandale')
	{
		zipElement.options[0] = new Option('22003', '22003');
	}
	else if(cityElement.value === 'Arlington')
	{
		zipElement.options[0] = new Option('22203', '22203');
		zipElement.options[1] = new Option('22204', '22204');
		zipElement.options[2] = new Option('22206', '22206');
		zipElement.options[3] = new Option('22207', '22207');
		zipElement.options[4] = new Option('22213', '22213');
	}
	else if(cityElement.value === 'Baileys Crossroads')
	{
		zipElement.options[0] = new Option('22041', '22041');
	}
	else if(cityElement.value === 'Belleview')
	{
		zipElement.options[0] = new Option('22307', '22307');
	}
	else if(cityElement.value === 'Burke')
	{
		zipElement.options[0] = new Option('22015', '22015');
	}
	else if(cityElement.value === 'Cameron Station')
	{
		zipElement.options[0] = new Option('22304', '22304');
	}
	else if(cityElement.value === 'Dunn Loring')
	{
		zipElement.options[0] = new Option('22027', '22027');
	}
	else if(cityElement.value === 'Engleside')
	{
		zipElement.options[0] = new Option('22309', '22309');
	}
	else if(cityElement.value === 'Falls Church')
	{
		zipElement.options[0] = new Option('22041', '22041');
		zipElement.options[1] = new Option('22042', '22042');
		zipElement.options[2] = new Option('22043', '22043');
		zipElement.options[3] = new Option('22044', '22044');
		zipElement.options[4] = new Option('22046', '22046');
	}
	else if(cityElement.value === 'Fort Belvoir')
	{
		zipElement.options[0] = new Option('22060', '22060');
	}
	else if(cityElement.value === 'Franconia')
	{
		zipElement.options[0] = new Option('22310', '22310');
	}
	else if(cityElement.value === 'Great Falls')
	{
		zipElement.options[0] = new Option('22066', '22066');
	}
	else if(cityElement.value === 'Greenway')
	{
		zipElement.options[0] = new Option('22067', '22067');
	}
	else if(cityElement.value === 'Herndon')
	{
		zipElement.options[0] = new Option('20170', '20170');
		zipElement.options[1] = new Option('20171', '20171');
		zipElement.options[2] = new Option('20190', '20190');
		zipElement.options[3] = new Option('20191', '20191');
		zipElement.options[4] = new Option('20194', '20194');
	}
	else if(cityElement.value === 'Jefferson Manor')
	{
		zipElement.options[0] = new Option('22303', '22303');
	}
	else if(cityElement.value === 'Kingstowne')
	{
		zipElement.options[0] = new Option('22315', '22315');
	}
	else if(cityElement.value === 'Lorton')
	{
		zipElement.options[0] = new Option('22079', '22079');
		zipElement.options[1] = new Option('22199', '22199');
	}
	else if(cityElement.value === 'Mason Neck')
	{
		zipElement.options[0] = new Option('22079', '22079');
	}
	else if(cityElement.value === 'McLean')
	{
		zipElement.options[0] = new Option('22101', '22101');
		zipElement.options[1] = new Option('22102', '22102');
		zipElement.options[2] = new Option('22103', '22103');
		zipElement.options[3] = new Option('22106', '22106');
	}
	else if(cityElement.value === 'Merrifield')
	{
		zipElement.options[0] = new Option('22081', '22081');
		zipElement.options[1] = new Option('22116', '22116');
	}
	else if(cityElement.value === 'Mosby')
	{
		zipElement.options[0] = new Option('22042', '22042');
	}
	else if(cityElement.value === 'Mount Vernon')
	{
		zipElement.options[0] = new Option('22121', '22121');
	}
	else if(cityElement.value === 'Newington')
	{
		zipElement.options[0] = new Option('22122', '22122');
	}
	else if(cityElement.value === 'North Springfield')
	{
		zipElement.options[0] = new Option('22151', '22151');
	}
	else if(cityElement.value === 'Oak Hill')
	{
		zipElement.options[0] = new Option('22171', '22171');
	}
	else if(cityElement.value === 'Oakton')
	{
		zipElement.options[0] = new Option('22124', '22124');
	}
	else if(cityElement.value === 'Pimmit')
	{
		zipElement.options[0] = new Option('22043', '22043');
	}
	else if(cityElement.value === 'Reston')
	{
		zipElement.options[0] = new Option('20190', '20190');
		zipElement.options[1] = new Option('20191', '20191');
		zipElement.options[2] = new Option('20194', '20194');
	}
	else if(cityElement.value === 'Seven Corners')
	{
		zipElement.options[0] = new Option('22044', '22044');
	}
	else if(cityElement.value === 'Springfield')
	{
		zipElement.options[0] = new Option('22015', '22015');
		zipElement.options[1] = new Option('22150', '22150');
		zipElement.options[2] = new Option('22151', '22151');
		zipElement.options[3] = new Option('22152', '22152');
		zipElement.options[4] = new Option('22153', '22153');
	}
	else if(cityElement.value === 'Sully Station')
	{
		zipElement.options[0] = new Option('20120', '20120');
	}
	else if(cityElement.value === 'Tysons Corner')
	{
		zipElement.options[0] = new Option('22102', '22102');
		zipElement.options[1] = new Option('22182', '22182');
	}
	else if(cityElement.value === 'Vienna')
	{
		zipElement.options[0] = new Option('22027', '22027');
		zipElement.options[1] = new Option('22124', '22124');
		zipElement.options[2] = new Option('22180', '22180');
		zipElement.options[3] = new Option('22181', '22181');
		zipElement.options[4] = new Option('22182', '22182');
		zipElement.options[5] = new Option('22183', '22183');
	}
	else if(cityElement.value === 'West McLean')
	{
		zipElement.options[0] = new Option('22102', '22102');
		zipElement.options[1] = new Option('22103', '22103');
	}
	else if(cityElement.value === 'West Springfield')
	{
		zipElement.options[0] = new Option('22152', '22152');
	}
}
	
function removeOptions(select)
{
    for(var i=select.options.length-1;i>=0;i--)
        select.remove(i);   
}

function onSubmit(bReferral)
{
	var errorElement = document.getElementById('errormessage');
	
	//check
	var phoneMssg= verifyPhoneNumbers();
	var schoolsMssg = verifySchools();
	var giftsmealsMssg = '';
	if(bReferral === true)	//only check if it's a referral submission
		giftsmealsMssg = verifyGiftsAndMeals();		
	
	if(!verifyEmail())
	{
		errorElement.textContent="Error: Improperly formatted email address";
		document.getElementById('badfammssg').textContent = "Error: Improperly formatted email address";
		window.location=document.getElementById('badfamanchor').href;		
	}
	else if(phoneMssg != '')
	{
		//one or more phone #'s are bad
		errorElement.textContent=phoneMssg;
		document.getElementById('badfammssg').textContent = phoneMssg;
		window.location=document.getElementById('badfamanchor').href;
	}
	else if(schoolsMssg != '')
	{
		//one or more schools are missing
		errorElement.textContent = schoolsMssg;
		document.getElementById('badfammssg').textContent = schoolsMssg;
		window.location=document.getElementById('badfamanchor').href;
	}
	else if(giftsmealsMssg != '')
	{
		//neither gifts nor meals was requested
		errorElement.textContent = giftsmealsMssg;
		document.getElementById('badfammssg').textContent = giftsmealsMssg;
		window.location=document.getElementById('badfamanchor').href;
	}
	else	
	{
		//HOH name elements
		var hohNameElement = [document.getElementById('hohfn'), 
	   						  document.getElementById('hohln')];
		//HOH address elements
		var hohAddrElement = [document.getElementById('housenum'), 
	    	               		document.getElementById('street'),
	    	               		document.getElementById('unit'),
	    	               		document.getElementById('city'), 
	    	               		document.getElementById('zipcode')];
		
		var hohUnitElement = [document.getElementById('unit')];
		
		//delivery address elements
		var delAddrElement = [document.getElementById('delhousenum'),
							document.getElementById('delstreet'),
							document.getElementById('delunit'),
							document.getElementById('delcity'), 
							document.getElementById('delzipcode')];

		var delUnitElement = [document.getElementById('delunit')];
		
		//phone numbers, schools are good and at least gift or meal assistance was requested.
		//Check to see that we have first and last names and
		//HoH and Delivery addresses are good. First check the delivery address
		if(hohNameElement[0].value !== "" && hohNameElement[1].value !== "")
		{
			if(delAddrElement[0].value !== "" && delAddrElement[1].value !== ""  &&
				hohAddrElement[0].value !== "" && hohAddrElement[1].value != "")
			{
				//form the address check url
	       		$.getJSON('checkaddresses', createAllAddressParams(hohAddrElement, delAddrElement), function(addresponse)
	      		{
	      			if(addresponse.hasOwnProperty('error'))
	      			{
						window.location=document.getElementById('timeoutanchor').href;
	      			}
	      			else if(addresponse.returnCode === 0)
	      			{
//	      				document.getElementById("familyform").submit();
	      				//determine if its a referral or an update
	      				var urlmode = window.location.href;
	      				if(urlmode.indexOf('referral') >= 0 || urlmode.indexOf('newfamily') >= 0)
	      					post('referfamily', createReferralParams(true));
	      				else if(urlmode.indexOf('familyupdate') >= 0)
	      					post('updatefamily', createReferralParams(false));
	      			}
	      			else
	      				processAddressError(addresponse, delAddrElement, delUnitElement, 
	      									hohAddrElement, hohUnitElement, errorElement);
	      		});
			}
			else
			{
				changeAddressBackground(delAddrElement, errorColor);
				changeAddressBackground(hohAddrElement, errorColor);
				errorElement.textContent = "Submission Error: Delivery or HoH address is incomplete";
				document.getElementById('badfammssg').textContent = "Submission Error: Delivery or HOH address incomplete";
				window.location=document.getElementById('badfamanchor').href;
			}
		}
		else
		{
			changeAddressBackground(hohAddrElement, errorColor);
			changeAddressBackground(delAddrElement, '#FFFFFF');
			changeAddressBackground(hohAddrElement, '#FFFFFF');
			errorElement.textContent = "Submission Error: HOH First and/or Last Name is missing";
			document.getElementById('badfammssg').textContent = "Submission Error: HOH First or Last Names missing";
			window.location=document.getElementById('badfamanchor').href;
		}	
	}
}

function processAddressError(addresponse, delAddrElement, delAddrUnitElement, hohAddrElement, hohAddrUnitElement, errorElement)
{
	const DEL_ADDRESS_NOT_VALID = 1;
	const DEL_ADDRESS_MISSING_UNIT = 2;
	const DEL_ADDRESS_NOT_IN_SERVED_ZIPCODE = 4;
	const DEL_ADDRESS_NOT_IN_SERVED_PYRAMID = 8;
	const HOH_ADDRESS_NOT_VALID = 16;
	const HOH_ADDRESS_MISSING_UNIT = 32;
	
	var hohMissing = addresponse.returnCode & HOH_ADDRESS_MISSING_UNIT;
	var hohInvalid = addresponse.returnCode & HOH_ADDRESS_NOT_VALID;
	var delMissing = addresponse.returnCode & DEL_ADDRESS_MISSING_UNIT;
	var delInvalid = addresponse.returnCode & DEL_ADDRESS_NOT_VALID;
	var delOutOfArea = addresponse.returnCode & DEL_ADDRESS_NOT_IN_SERVED_ZIPCODE;
	var delOutOfPyramid = addresponse.returnCode & DEL_ADDRESS_NOT_IN_SERVED_PYRAMID;
	
	//clear delivery and hoh backgrounds
	changeAddressBackground(hohAddrElement,'#FFFFFF');
	changeAddressBackground(delAddrElement,'#FFFFFF');
	
	if(hohMissing > 0)	
	{
		changeAddressBackground(hohAddrUnitElement, errorColor);
		errorElement.textContent = "Error: Delivery and/or HoH address missing Apt#";
	}
	else if(hohInvalid > 0)	
	{
		changeAddressBackground(hohAddrElement, errorColor);
		errorElement.textContent = "Error: Delivery and/or HOH address incomplete or does not exist";
	}
	
	if(delMissing > 0)	//address found, missing unit
	{
		changeAddressBackground(delAddrUnitElement, errorColor);
		errorElement.textContent = "Error: Delivery and/or HoH missing Apt#";
	}
	else if(delInvalid > 0)	//del address was not found
	{
		changeAddressBackground(delAddrElement, errorColor);
		errorElement.textContent = "Error: Delivery and/or HoH address incomplete or does not exist";
	}
	else if(delOutOfArea > 0)
	{
		errorElement.textContent = "Error: Valid delivery address but outside of ONC serving area";
	}
	else if(delOutOfPyramid > 0)
	{
		errorElement.textContent = "Error: Valid delivery address but not in a school pyramid ONC serves";
	}
		
	document.getElementById('badfammssg').textContent = addresponse.errMssg;
	window.location=document.getElementById('badfamanchor').href;
}

function changeAddressBackground(elements, color)
{
	for(var i=0; i< elements.length; i++)
		elements[i].style.backgroundColor = color;
}

function createAllAddressParams(hohAddrElement, delAddrElement)
{	
	var addressparams = "housenum=" + encodeURIComponent(hohAddrElement[0].value) + "&" +
						"street=" + encodeURIComponent(hohAddrElement[1].value) + "&" +
						"unit=" + encodeURIComponent(hohAddrElement[2].value) + "&" +
						"city=" + hohAddrElement[3].value + "&" +
						"zipcode=" + hohAddrElement[4].value + "&" +
						"sameaddress=" + document.getElementById('sameaddress').checked + "&" +
						"delhousenum=" + encodeURIComponent(delAddrElement[0].value) + "&" +
						"delstreet=" + encodeURIComponent(delAddrElement[1].value) + "&" +
						"delunit=" + encodeURIComponent(delAddrElement[2].value) + "&" +
						"delcity=" + delAddrElement[3].value + "&" +
						"delzipcode=" + delAddrElement[4].value + "&" +
						"callback=?";
	return addressparams;
}

function createAddressParams(addrElement, type)
{	
	var addressparams = "housenum=" + encodeURIComponent(addrElement[0].value) + "&" +
						"street=" + encodeURIComponent(addrElement[1].value) + "&" +
						"unit=" + encodeURIComponent(addrElement[2].value) + "&" +
						"city=" + addrElement[3].value + "&" +
						"zipcode=" + addrElement[4].value + "&" +
						"type=" + type + "&" +
						"callback=?";
	return addressparams;
}

function verifySchools()
{
	var errorMssg = "";
	for(var i=0; i< document.getElementById("childtable").rows.length -1; i++)
	{
		var elementID = 'childschool' + i;
		var schoolElement = document.getElementById(elementID);
		if(schoolElement.value ==='')
		{
			var cnum = i+1;
			schoolElement.style.backgroundColor = errorColor;
			errorMssg = "School Missing for Child";
		}	
	}	
	return errorMssg;
}

function verifyGiftsAndMeals()
{
	var errorMssg = '';
	if(document.getElementById('giftreq').checked === false && 
		document.getElementById('mealreq').checked === false)
	{
		errorMssg = "Neither gift nor meal assistance requested";
	}	
	return errorMssg;
}

function verifyPhoneNumbers()
{
	var errorMssg= "";
	var phoneName= ["Primary", "Alternate", "2nd Alternate"];
	
	//check that at least one phone nubmer element isn't empty
	if(document.getElementById('homephone').value.length === 0 && 
		document.getElementById('cellphone').value.length === 0 &&
		 document.getElementById('altphone').value.length === 0)
	{
		errorMssg= "At least one valid phone number is required for referral";
		document.getElementById('homephone').style.backgroundColor = errorColor;
		document.getElementById('cellphone').style.backgroundColor = errorColor;
		document.getElementById('altphone').style.backgroundColor = errorColor;
	}
	else
	{
		//check that phone numbers that have been entered are properly formatted
		var index=0;
		while(index<phoneName.length && verifyPhoneNumber(index))
			index++;
		
		if(index < phoneName.length)	
			errorMssg= "Error: " + phoneName[index] + " Phone # is invalid";
	}
	
	return errorMssg;	
}

function verifyPhoneNumber(elementNum)
{
	var phoneElement= [document.getElementById('homephone'), 
						document.getElementById('cellphone'),
						document.getElementById('altphone')];
	
	var numberGood = true;
	var number= phoneElement[elementNum].value;
	
	if(number == '')	
		phoneElement[elementNum].style.backgroundColor = '#FFFFFF';
	else if(number.length===12 && number.charAt(3)==='-' && number.charAt(7)==='-' && isNumericPhoneNumber(number, '-'))
		phoneElement[elementNum].style.backgroundColor = '#FFFFFF';
	else if(number.length===12 && number.charAt(3)==='.' && number.charAt(7)==='.' && isNumericPhoneNumber(number, '.'))
		phoneElement[elementNum].style.backgroundColor = '#FFFFFF';
//	else if(number.length===12 && number.charAt(3)===' ' && number.charAt(7)==='.' && isNumericPhoneNumber(number, '.'))
//		phoneElement[elementNum].style.backgroundColor = '#FFFFFF';
	else if(number.length===10 && !isNaN(number))
		phoneElement[elementNum].style.backgroundColor = '#FFFFFF';
	else
	{
		numberGood= false;
		phoneElement[elementNum].style.backgroundColor = errorColor;
	}
	
	return numberGood;
}

function isNumericPhoneNumber(phonenumber, separator)
{
//	var testNumberA = phonenumber.replace(/-/g, '');
//	var testNumberB = testNumberA.replace(/./g, '');
//	var testNumberC = testNumberB.replace(/\s/g, '');
//	console.log(testNumberC);
	var testNumber = phonenumber;
	if(separator === '-')
		testNumber = phonenumber.replace(/-/g, '');
	else if(separator === '.')
		testNumber = phonenumber.replace(/./g, '');

//	console.log('phonenumber= ' + phonenumber + ', testnumberC= ' + testNumberC);
	return !isNaN(testNumber);	
}

function verifyEmail()
{
	var emailElement= document.getElementById('email');
	var emailAddr = emailElement.value;
	
	var emailGood = isEmailValid(emailAddr);
	
	
	if(emailAddr == '')	
		emailElement.style.backgroundColor = '#FFFFFF';
	else if(emailGood)
		emailElement.style.backgroundColor = '#FFFFFF';
	else
		emailElement.style.backgroundColor = errorColor;
	
	return emailGood;
}

function isEmailValid (email)
{
	return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function verifyAddress(element)
{
	var prefix = '';
	if(element.id==='verifydeladdress')
		prefix = 'del';
	
	var addrFields = ["housenum", "street", "unit", "city", "zipcode"];
	
	var addrElement = [];
	for(var index=0; index<addrFields.length; index++)
		addrElement.push(document.getElementById(prefix + addrFields[index]));
	
	var unitElement = [document.getElementById(prefix + 'unit')];
	
	if(addrElement[0].value !== "" && addrElement[1].value !== "")
	{
		//form the address check url
       	$.getJSON('address', createAddressParams(addrElement, prefix), function(addresponse)
      	{
       		if(addresponse.hasOwnProperty('error'))
      		{
				window.location=document.getElementById('timeoutanchor').href;
      		}
       		else
       		{
       			if(addresponse.returnCode == 0)	//valid
       			{
       				changeAddressBackground(addrElement, '#FFFFFF');
       				document.getElementById("verifimg").src="checkmarkicon";
       			}
       			else if(addresponse.returnCode === 2)	//missing unit
       			{
       				changeAddressBackground(addrElement, '#FFFFFF');
       				changeAddressBackground(unitElement, errorColor);
       				document.getElementById("verifimg").src="erroricon";
       			}	
       			else		//not in county or not served
       			{	
       				changeAddressBackground(addrElement, errorColor);
       				document.getElementById("verifimg").src="erroricon";
       			}
       			
       			document.getElementById('verifmssg').innerHTML = addresponse.errMssg;
      			window.location=document.getElementById('verifanchor').href;
       		}	
      	});
	}
	else
	{
		changeAddressBackground(addrElement, errorColor);
		document.getElementById("verifimg").src="erroricon";
		document.getElementById('verifmssg').innerHTML = "Address is incomplete. Please enter both a House # and a Street Name";
		window.location=document.getElementById('verifanchor').href;
	}
}

function createReferralParams(bReferral)
{	
	var params = {};
	
	//create the year parameter
	params["year"] = sessionStorage.getItem("curryear");
	
	//create the common parameters
	var commonelementname = ['targetid','language','hohfn','hohln','housenum','street','unit','city',
							 'zipcode','delcity','delzipcode', 'delhousenum','delstreet','delunit',
							 'email','homephone','cellphone', 'altphone','detail'];
	for(cen=0; cen < commonelementname.length; cen++)
		params[commonelementname[cen]] = document.getElementById(commonelementname[cen]).value;
	
	
	if(bReferral)	//is this a referral or just an update? Create the unique parameters
	{	
		//create transportation and gift required parameters
		if(document.getElementById('transYes').checked)
			params['transportation'] = 'Yes';
		else
			params['transportation'] = 'No';
	
		if(document.getElementById('giftreq').checked)
			params['giftreq'] = 'on';
		else
			params['giftreq'] = 'off';
		
		var uniqueementname = ['mealtype','dietres','groupcb','uuid'];
		for(uen=0; uen < uniqueementname.length; uen++)
			params[uniqueementname[uen]] = document.getElementById(uniqueementname[uen]).value;
		
		//create the child and wish parameters
		var childelementname = ['childfn', 'childln', 'childdob', 'childgender', 'childschool'];
		for(cn=0; cn < childrenJson.length; cn++)	
		{
			for(cfn=0; cfn<childelementname.length; cfn++)
				params[childelementname[cfn]+cn] = document.getElementById(childelementname[cfn]+cn).value;
			for(wn=0; wn < 4; wn++) 
				params['wish'+cn+wn] = document.getElementById('wish'+cn+wn).value;
		}
	
		//create the adult parameters
		var adultelementname = ['adultname','adultgender'];
		for(an=0; an < adultsJson.length; an++)
			for(afn=0; afn<adultelementname.length; afn++)
				params[adultelementname[afn]+an] = document.getElementById(adultelementname[afn]+an).value;	    			
	}	
	return params;
}

function onLogoutLink()
{
	var params = {};
	post('logout', params);
}

function onSessionTimeout()
{
	 window.location.assign('timeout');
}
function post(path, params, method) 
{
    method = method || "post"; // Set method to post by default if not specified.

    var form = document.createElement("form");
    form.setAttribute("method", method);
    form.setAttribute("action", path);

    for(var key in params) 
    {
    		if(params.hasOwnProperty(key))
        {
            var hiddenField = document.createElement("input");
            hiddenField.setAttribute("type", "hidden");
            hiddenField.setAttribute("name", key);
            hiddenField.setAttribute("value", params[key]);

        		form.appendChild(hiddenField);
        }
    }

    document.body.appendChild(form);
    form.submit();
}