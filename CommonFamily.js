/*!
 * ONC Common Family JavaScript library v1.2
 * Common functions for family referral and family info editing web pages
 * Date: 2018-07-23
 */
var cities = {"Centreville":{"zipcodes":["20120", "20121"]},
  				  "Chantilly":{"zipcodes":["20151"]},
  				  "Clifton":{"zipcodes":['20124']},
  				  "Fairfax":{"zipcodes":['20151','22030','22031','22032','22033','22034','22035','22036','22037','22038']},
  				  "Fairfax Station":{"zipcodes":['22039']},
  				  "Sully Station":{"zipcodes":['20120']},
  				  "Alexandria":{"zipcodes":["22302","22303","22304","22305","22306","22307","22308","22309","22310","22311","22312","22315"]},
  				  "Annandale":{"zipcodes":['22003']},
  				  "Arlington":{"zipcodes":['22203','22204','22206','22207','22213']},
  				  "Baileys Crossroads":{"zipcodes":['22041']},
 				  "Belleview":{"zipcodes":['22307']},
 				  "Burke":{"zipcodes":['22015']},
 				  "Cameron Station":{"zipcodes":['22304']},
 				  "Dunn Loring":{"zipcodes":['22027']},
				  "Engleside":{"zipcodes":['22309']},
				  "Falls Church":{"zipcodes":['22041','22042','22043','22044','22046']},
  				  "Fort Belvoir":{"zipcodes":['22060']},
  				  "Franconia":{"zipcodes":['22310']},
 				  "Great Falls":{"zipcodes":['22066']},
 				  "Greenway":{"zipcodes":['22067']},
 				  "Herndon":{"zipcodes":['22170','22171','22190','22191','22194']},
 				  "Jefferson Manor":{"zipcodes":['22303']},
				  "Kingstowne":{"zipcodes":['22315']},
 				  "Lorton":{"zipcodes":['22079','22199']},
 				  "Mason Neck":{"zipcodes":['22079']},
				  "McLean":{"zipcodes":['22101','22102','22103','22106']},
				  "Merrifield":{"zipcodes":['22081','22116']},
 				  "Mosby":{"zipcodes":['22042']},
				  "Mount Vernon":{"zipcodes":['22121']},
 				  "Newington":{"zipcodes":['22122']},
 				  "North Springfield":{"zipcodes":['22151']},
				  "Oak Hill":{"zipcodes":['22171']},
				  "Oakton":{"zipcodes":['22124']},
				  "Pimmit":{"zipcodes":['22043']},
 				  "Reston":{"zipcodes":['22190','22191','22194']},
 				  "Seven Corners":{"zipcodes":['22044']},
				  "Springfield":{"zipcodes":['22015','22150','22151','22152','22153']},
				  "Tysons Corner":{"zipcodes":['22108','22182']},
 				  "Vienna":{"zipcodes":['22027','22124','22180','22181','22182','22183']},
 				  "West McLean":{"zipcodes":['22102','22103']},
				  "West Springfield":{"zipcodes":['22152']}};

var schools = ["Brookfield ES","Bull Run ES","Centre Ridge ES","Centreville ES","Centreville HS",
  					"Chantilly Acadamy","Chantilly HS","Cub Run ES","Deer Park ES","Floris ES","Franklin MS",
  					"Greenbriar East ES","Greenbriar West ES","Homeschooled", "Lees Corner ES","Lutie Lewis Coates ES",
  					"Liberty MS","London Towne ES","McNair ES","Mountain View HS","None","Oak Hill ES","Poplar Tree ES",
  					"Powell ES", "Pre-K","Rocky Run MS","Stone MS", "Union Mill ES", "Virginia Run ES", 
  					"Westfield HS","Willow Springs ES"];

var phonecodes = [1,3,4,12,16,48];
var phonecodesetmask = [3,12,48];
var phonecodeclearmask = [60,51,15];

$( function()
{
    $( ".schoolname" ).autocomplete(
    {
    	source: schools,
  	    appendTo: "#editChild"
    });
});
function popluateCitySelects()
{
	for(let key of Object.keys(cities))
	{
		addComboBoxOption(document.getElementById('city'), key, key);
		addComboBoxOption(document.getElementById('delcity'), key, key);		
	}
	cityChanged(document.getElementById('city'));
	cityChanged(document.getElementById('delcity'));
}
function updateNewChildTable(childList)
{
	console.log(childList);
	let table = $('#childtable').DataTable();
	table.clear();
	table.rows.add(childList);
	table.draw();
}
function updateNewAdultTable(adultList)
{
	let table = $('#adulttable').DataTable();
	table.clear();
	table.rows.add(adultList);
//	baselineGroupCheckSum = table.column(0).data().sum();
	table.draw();
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
	cityChanged(document.getElementById('delcity'));
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
	cityChanged(document.getElementById('delcity'));
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
function cityChanged(citySelect)
{
	var zipSelect;
	if(citySelect.id === 'city')
		zipSelect = document.getElementById('zipcode');
	else
		zipSelect = document.getElementById('delzipcode');
	
	removeOptions(zipSelect);	//clear zip codes
	
	//add new zipcodes
	let zipcodes=cities[citySelect.value].zipcodes
	for(index=0; index < zipcodes.length; index++)
		addComboBoxOption(zipSelect, zipcodes[index], zipcodes[index]);	
}	
function removeOptions(select)
{
    for(var i=select.options.length-1;i>=0;i--)
        select.remove(i);   
}
function onSubmitFamilyForm(bReferral)
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
		//Check to see that we have first and last names and then check either or both the 
		//HoH and Delivery addresses are good based on whether we're delivering gifts to family.
		//The default for the distribution preference select is 'Pickup'.
		if(hohNameElement[0].value != "" && hohNameElement[1].value != "")
		{	
			if(sessionStorage.getItem('distpref') == 'Delivery')
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
		      				var urlmode = window.location.href;
		      				if(urlmode.indexOf('referral') >= 0 || urlmode.indexOf('newfamily') >= 0)
		      				{
		      					post('referfamily', createReferralParams(true));
		      				}
		      				else if(urlmode.indexOf('familyupdate') >= 0)
		      				{
		      					console.log(document.getElementById('phonecode'));
		      					post('updatefamily', createReferralParams(false));	
		      				}
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
				//Not delivering to this family so only check HOH address
				if(hohAddrElement[0].value !== "" && hohAddrElement[1].value != "")
				{
					//form the address check url
		       		$.getJSON('address', createAddressParams(hohAddrElement, ''), function(addresponse)
		      		{
		      			if(addresponse.hasOwnProperty('error'))
		      			{
							window.location=document.getElementById('timeoutanchor').href;
		      			}
		      			else if(addresponse.returnCode === 0)
		      			{
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
					changeAddressBackground(hohAddrElement, errorColor);			
					errorElement.textContent = "Submission Error: HoH address is incomplete";
					document.getElementById('badfammssg').textContent = "Submission Error: HOH address incomplete";
					window.location=document.getElementById('badfamanchor').href;
				}
			}
		}
		else
		{
			changeAddressBackground(hohNameElement, errorColor);
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
		if(sessionStorage.getItem('homeDelvery') == 'true')
			errorElement.textContent = "Error: Delivery and/or HoH address missing Apt#";
		else
			errorElement.textContent = "Error: HoH address is missing Apt#";
	}
	else if(hohInvalid > 0)	
	{
		changeAddressBackground(hohAddrElement, errorColor);
		if(sessionStorage.getItem('homeDelvery') == 'true')
			errorElement.textContent = "Error: Delivery and/or HOH address incomplete or does not exist";
		else
			errorElement.textContent = "Error: HOH address incomplete, does not exist or is an invalid HoH address";
	}
	
	if(delMissing > 0)	//address found, missing unit
	{
		changeAddressBackground(delAddrUnitElement, errorColor);
		if(sessionStorage.getItem('homeDelvery') == 'true')
			errorElement.textContent = "Error: Delivery and/or HoH missing Apt#";
		else
			errorElement.textContent = "Error: HoH missing Apt#";
	}
	else if(delInvalid > 0)	//del address was not found
	{
		changeAddressBackground(delAddrElement, errorColor);
		if(sessionStorage.getItem('homeDelvery') == 'true')
			errorElement.textContent = "Error: Delivery and/or HoH address incomplete or does not exist";
		else
			errorElement.textContent = "Error: HoH address incomplete, does not exist or is an invalid HoH address";
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
	let childtable = $('#childtable').DataTable();
	let children = childtable.rows().data();
	
	childtable.rows().every( function ( rowIdx, tableLoop, rowLoop )
	{
		var child = this.data();
		if(child.school === '')
		{	
			$(childtable.cells(this.index(), 5).nodes()).addClass( 'cellerror' );
			errorMssg = "School Missing for " + child.firstname + " " + child.lastname;
		}
		else
			$(childtable.cells(this.index(), 5).nodes()).removeClass( 'cellerror' );	
	});	

	return errorMssg;
}
function verifyGiftsAndMeals()
{
	var errorMssg = '';
	
	let table = $('#childtable').DataTable();
	let nChildren = table.data().count();
	
	if(document.getElementById('giftreq').checked === false && 
		document.getElementById('mealreq').checked === false)
	{
		errorMssg = "Neither gift nor meal assistance requested";
	}
	else if(document.getElementById('giftreq').checked === true && nChildren === 0)
	{
		errorMssg = "No children. Must have at least one child in family if gift assistance is requested";
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
	else if(number.length===10 && !isNaN(number))
		phoneElement[elementNum].style.backgroundColor = '#FFFFFF';
	else
	{
		numberGood= false;
		phoneElement[elementNum].style.backgroundColor = errorColor;
	}
	
	return numberGood;
}
//function isIndividualPhoneCodeValid(code)
//{
//	return code == 0 || code==1 || code==3 || code==4 || code==12 || code==16 || code==48;
//}
function verifyEmail()
{
	var emailElement= document.getElementById('email');
	
	if(emailElement.value.length == 0)
	{
		emailElement.style.backgroundColor = '#FFFFFF';
		return true;
	}
	else if(isEmailValid(emailElement.value))
	{
		emailElement.style.backgroundColor = '#FFFFFF';
		return true;
	}
	else
	{	
		emailElement.style.backgroundColor = errorColor;
		return false;
	}
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
       				changeAddressBackground(unitElement, '#FFFFFF');
       				document.getElementById("verifimg").src="checkmarkicon";
       			}
       			else if(addresponse.returnCode === 2)	//missing unit
       			{
       				changeAddressBackground(addrElement, '#FFFFFF');
       				changeAddressBackground(unitElement, errorColor);
       				document.getElementById("verifimg").src="erroricon";
       			}	
       			else	//not in county or not served
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
function verifyPhone(phoneid)
{
	let element;
	if(phoneid == 0)
		element = document.getElementById('homephone');
	else if(phoneid == 1)
		element = document.getElementById('cellphone');
	else if(phoneid == 2)
		element = document.getElementById('altphone');
	
	let number= element.value;
	
	if(number == '')
	{
		let phoneCodeElement = document.getElementById('phonecode');
		element.title = 'Enter phone number';
		element.style.backgroundColor = '#FFFFFF';
		phoneCodeElement.value = phoneCodeElement.value & phonecodeclearmask[phoneid];
	}
	else if((number.length===12 && number.charAt(3)==='-' && number.charAt(7)==='-' && isNumericPhoneNumber(number, '-')) ||
			(number.length===12 && number.charAt(3)==='.' && number.charAt(7)==='.' && isNumericPhoneNumber(number, '.')) ||
			(number.length===10 && !isNaN(number)))
	{
		//format is good, ask server to check for valid number
		let phoneparams = "phonenumber=" + encodeURIComponent(element.value) + "&callback=?";

       	$.getJSON('lookup', phoneparams, function(phoneresponse)
      	{
       		let phonecode = document.getElementById('phonecode').value;
       		
       		if(phoneresponse.hasOwnProperty('error'))
      		{
				window.location=document.getElementById('timeoutanchor').href;
      		}
       		else if(phoneresponse.returncode == 0)	//valid phone number
       		{
       			phonecode = phonecode & phonecodeclearmask[phoneid];
       			element.title = phoneresponse.carrier + ' ' + phoneresponse.type + ' phone number';
       			
   				//determine phone code (0 or 2 are invalid, 1 is a valid mobile, 3 is a valid land line or other phone
   				//the result is left shifted based on which phoneid was checked (0-2)
   				if(phoneresponse.type =='mobile')
   				{
   					phonecode = phonecode | phonecodes[phoneid * 2];
   					element.style.backgroundColor = '#65D02F';
   				}
   				else
   				{
   					phonecode = phonecode | phonecodes[(phoneid * 2) + 1];
   					element.style.backgroundColor = '#FFE561'
   				}
   				
   				console.log('phoneid= ' + phoneid + ', phonecode= ' + phonecode);
   				document.getElementById('phonecode').value = phonecode;
       		}
       		else
       		{
       			let phoneCodeElement = document.getElementById('phonecode');
       			element.title = 'Invalid phone number';
       			element.style.backgroundColor = errorColor;
       			phoneCodeElement.value = phoneCodeElement.value & phonecodeclearmask[phoneid];
       		}
      	});
	}
	else
	{
		let phoneCodeElement = document.getElementById('phonecode');
		element.title = 'Invalid phone number';
		element.style.backgroundColor = errorColor;
		phoneCodeElement.value = phoneCodeElement.value & phonecodeclearmask[phoneid];
	}
}
function createReferralParams(bReferral)
{	
	var params = {};
	
	//create the year, reference and target id parameters
	params["year"] = sessionStorage.getItem("currseason");
	params["targetid"] = sessionStorage.getItem("targetid");
	params["referencenum"] = sessionStorage.getItem("referencenum");
	
	//create the common parameters. If not doing home delivery, substitute the HoH address elements
	//for the delivery address elements
	var paramname = ['language','hohfn','hohln','housenum','street','unit','city',
		 'zipcode','delcity','delzipcode', 'delhousenum','delstreet','delunit',
		 'email','homephone','cellphone', 'altphone', 'phonecode'];

	var noDeliveryElementNames = ['language','hohfn','hohln','housenum','street','unit','city',
		 'zipcode','city','zipcode', 'housenum','street','unit',
		 'email','homephone','cellphone', 'altphone', 'phonecode'];
	
	for(cen=0; cen < paramname.length; cen++)
	{
		//if pickup, not delivery, make the referral address the same as HoH
		if(document.getElementById('distpref').value == 'Delivery')
			params[paramname[cen]] = document.getElementById(paramname[cen]).value;
		else
			params[paramname[cen]] = document.getElementById(noDeliveryElementNames[cen]).value;
	}	
		
	if(bReferral)	//is this a referral or just an update? Create the unique parameters
	{	
		//create transportation and gift required parameters
		if(document.getElementById('distpref').value == 'Pickup' || document.getElementById('transYes').checked)
			params['transportation'] = 'Yes';
		else
			params['transportation'] = 'No';
		
		if(document.getElementById('giftreq').checked)
			params['giftreq'] = 'on';
		else
			params['giftreq'] = 'off';
		
		var uniqueementname = ['mealtype','dietres','groupcb','uuid', 'detail', 'distpref'];
		for(uen=0; uen < uniqueementname.length; uen++)
			params[uniqueementname[uen]] = document.getElementById(uniqueementname[uen]).value;
		
		//create the child and wish parameters
		let childTable = $('#childtable').DataTable();
		let children = childTable.rows().data();
		let numWishes = sessionStorage.getItem('numberGiftsPerChild');
		
		for(cn=0; cn<childTable.rows().count(); cn++)
		{
			params["childfn"+cn] = children[cn].firstname;
			params["childln"+cn] = children[cn].lastname;
			params["childdob"+cn] = children[cn].sDOB;
			params["childgender"+cn] = children[cn].gender;
			params["childschool"+cn] = children[cn].school;
			
			if(numWishes > 0)
			{	
				params["wish"+cn+"0"] = children[cn].wish0;
				params["wish"+cn+"3"] = children[cn].wish3;	//alternate wish
			}
			if(numWishes > 1)
				params["wish"+cn+"1"] = children[cn].wish1;
			if(numWishes > 2)
				params["wish"+cn+"2"] = children[cn].wish2;
		}
	
		//create the adult parameters
		let adultTable = $('#adulttable').DataTable();
		let adults = adultTable.rows().data();	
		for(an=0; an < adultTable.rows().count(); an++)
		{
			params["adultname"+an] = adults[an].name;
			params["adultgender"+an] = adults[an].gender;	
		}
	}	
	return params;
}