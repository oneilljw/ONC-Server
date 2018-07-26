/*!
 * ONC Common Family JavaScript library v1.2
 * Common functions for family referral and family info editing web pages
 * Date: 2018-07-23
 */
function updateChildTable(bAction)
{
    $("#tchildbody").empty();
    	
    for(var i=0; i<childrenJson.length; i++)
	{
    	addChildTableRow(i, childrenJson[i], bAction);	//add row to table
	}
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
	    	content.name=fieldname[index] + cnum;
    	content.value = childinfo[index];
    	content.setAttribute("size", fieldsize[index]);
    		
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
    adultname.name="adultname" + anum;
    adultname.value = adult.name;
    adultname.setAttribute("size", 27);
    cell.appendChild(adultname);
    row.appendChild(cell);
    
    cell= document.createElement("td");
    var adultgender= document.createElement("input");
    adultgender.type="text";
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
		copyAddressToDeliveryAddress();	
	else	//clear the delivery address elements
		clearDeliveryAddress();
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
	
//	verifyDeliveryAddress();
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
		zipElement.options[5] = new Option('22037', '22037');
		zipElement.options[6] = new Option('22039', '22039');
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

function verifyHOHAddress()
{
	//called when HOH address inputs change
	var addrElement = [document.getElementById('housenum'), 
    	               	   document.getElementById('street'),
    	               	   document.getElementById('unit'),
    	               	   document.getElementById('city'), 
	                   document.getElementById('zipcode')];
	
	var unitElement = [document.getElementById('unit')];
	
	//check to see that house number and street are not blank
	if(addrElement[0].value !== "" && addrElement[1].value !== "" )
	{
		//form the address url
       	var addressparams = createAddressParams(addrElement);
        var addrresponse;
       	$.getJSON('address', addressparams, function(data)
      	{
      		addresponse = data;
      		
      		if(addresponse.hasOwnProperty('error'))
      		{
				window.location=document.getElementById('timeoutanchor').href;
      		}
      		else if(addresponse.errorCode === 0)
      		{
      			changeAddressBackground(addrElement, '#FFFFFF');
      		}	
      		else if(addresponse.errorCode === 1 || addresponse.errorCode === 3)
      		{
      			changeAddressBackground(addrElement, errorColor);
      		}
      		else if(addresponse.errorCode === 2)
      		{
      			changeAddressBackground(addrElement, '#FFFFFF');
      			changeAddressBackground(unitElement, errorColor);
      		}
      	});
	}
}

function verifyDeliveryAddress()
{
	//called when delivery address inputs change
	var addrElement = [document.getElementById('delhousenum'),
	    	           document.getElementById('delstreet'),
	        	       document.getElementById('delunit'),
	            	   document.getElementById('delcity'), 
	            	   document.getElementById('delzipcode')];
	
	var unitElement = [document.getElementById('delunit')];
	
	//check to see that housenum and street are provided
	if(addrElement[0].value !== "" && addrElement[1].value !== "" )
	{
		//form the address url
        var addressparams = createAddressParams(addrElement);
        var addrresponse;
        $.getJSON('address',  addressparams, function(data)
      	{
      		addresponse = data;
      		
      		if(addresponse.hasOwnProperty('error'))
      		{
				window.location=document.getElementById('timeoutanchor').href;
      		}
      		else if(addresponse.errorCode === 0)
      		{
      			changeAddressBackground(addrElement, '#FFFFFF');
      		}	
      		else if(addresponse.errorCode === 1 || addresponse.errorCode === 3)
      		{
      			changeAddressBackground(addrElement, errorColor);
      		}
      		else if(addresponse.errorCode === 2)
      		{
      			changeAddressBackground(addrElement, '#FFFFFF');
      			changeAddressBackground(unitElement, errorColor);
      		}
      	});
	}
}

function onSubmit()
{
	var errorElement = document.getElementById('errormessage');
	
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
	
	//check phone numbers
	var phoneMssg= verifyPhoneNumbers();
	if(phoneMssg != '')
	{
		//one or more phone #'s are bad
		errorElement.textContent=phoneMssg;
	}
	else	
	{
		//phone numbers are good, check to see that we have first and last names and
		//HoH and Delivery addresses are good. First check the delivery address
		if(hohNameElement[0].value !== "" && hohNameElement[1].value !== "")
		{
			if(delAddrElement[0].value !== "" && delAddrElement[1].value !== "" )
			{
				//form the delivery address url
	       		$.getJSON('address', createAddressParams(delAddrElement), function(addresponse)
	      		{
	       			console.log(addresponse);
	      			if(addresponse.hasOwnProperty('error'))
	      			{
						window.location=document.getElementById('timeoutanchor').href;
	      			}
	      			else if(addresponse.errorCode === 0)
	      			{
	      				changeAddressBackground(delAddrElement, '#FFFFFF');

	      				//delivery address is good, now check the HoH address. If same as 
	      				//delivery address no need to check, else, check to see that HoH
	      				//house # and street are provided
	      				if(document.getElementById('sameaddress').checked === true)
	      				{
	      					changeAddressBackground(hohAddrElement, '#FFFFFF');		      					
	      					
							//if phone #'s are ok, delivery address is valid, and HoH address is identical
							document.getElementById("familyform").submit();
	      				}
	      				else
	      				{
	      					if(hohAddrElement[0].value !== "" && hohAddrElement[1].value !== "" )
	      					{
	      						$.getJSON('address',  createAddressParams(hohAddrElement), function(addresponse)
	      						{
	      							if(addresponse.hasOwnProperty('error'))
	      							{
	      								window.location=document.getElementById('timeoutanchor').href;
	      							}
	      							else if(addresponse.errorCode === 0)
	      							{
	      								changeAddressBackground(hohAddrElement, '#FFFFFF');		      					
		      					
	      								//if phone #'s are ok and both addresses are valid, it's ok to submit
	      								document.getElementById("familyform").submit();
	      							}	
	      							else if(addresponse.errorCode === 1 || addresponse.errorCode === 3)
	      							{
	      								changeAddressBackground(hohAddrElement, errorColor);
	      								errorElement.textContent = "Submission Error: HoH address incomplete or does not exist";
	      								document.getElementById('badfammssg').textContent = addresponse.errMssg;
	      								window.location=document.getElementById('badfamanchor').href;
	      							}
	      							else if(addresponse.errorCode === 2)
	      							{
	      								changeAddressBackground(hohAddrElement, '#FFFFFF');
	      								changeAddressBackground(hohUnitElement, errorColor);
	      								errorElement.textContent = "Submission Error: HoH address unit error";
	      							}
	      						});
	      					}
	      					else
	      					{
	      						changeAddressBackground(hohAddrElement, errorColor);
	      						errorElement.textContent = "Submission Error: HoH address incomplete";
	      						document.getElementById('badfammssg').textContent = "Submission Error: HoH address incomplete";
								window.location=document.getElementById('badfamanchor').href;
	      					}
	      				}	
	      			}	
	      			else if(addresponse.errorCode === 1 || addresponse.errorCode === 3)
	      			{
	      				changeAddressBackground(delAddrElement, errorColor);
	      				errorElement.textContent = "Submission Error: Delivery address incomplete or does not exist";
	      				document.getElementById('badfammssg').textContent = addresponse.errMssg;
	      				window.location=document.getElementById('badfamanchor').href;
	      			}
	      			else if(addresponse.errorCode === 2)
	      			{
	      				changeAddressBackground(delAddrElement, '#FFFFFF');
	      				changeAddressBackground(delUnitElement, errorColor);
	      				errorElement.textContent = "Submission Error: Delivery address unit error";
	      			}
	      		});
			}
			else
			{
				changeAddressBackground(delAddrElement, errorColor);
				errorElement.textContent = "Submission Error: Delivery address incomplete";
				document.getElementById('badfammssg').textContent = "Submission Error: Delivery address incomplete";
				window.location=document.getElementById('badfamanchor').href;
			}
		}
		else
		{
			changeAddressBackground(hElement, errorColor);
			errorElement.textContent = "Submission Error: HOH First and/or Last Names missing";
			document.getElementById('badfammssg').textContent = "Submission Error: HOH First or Last Names missing";
			window.location=document.getElementById('badfamanchor').href;
		}	
	}
}

function onZipChanged(zipelement)
{
//	if(zipelement === 'zipcode')
//		verifyHOHAddress();
//	else
//		verifyDeliveryAddress();
}

function changeAddressBackground(elements, color)
{
	for(var i=0; i< elements.length; i++)
		elements[i].style.backgroundColor = color;
}

function createAddressParams(addrElement)
{	
	var addressparams = "housenum=" + encodeURIComponent(addrElement[0].value) + "&" +
						"street=" + encodeURIComponent(addrElement[1].value) + "&" +
						"unit=" + encodeURIComponent(addrElement[2].value) + "&" +
						"city=" + addrElement[3].value + "&" +
						"zipcode=" + addrElement[4].value + "&" +
						"callback=?";
	
	return addressparams;
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
		errorMssg= "Error: At least one valid phone number is required for referral";
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
	else if(number.length===12 && number.charAt(3)==='-' && number.charAt(7)==='-' &&
			isNumericPhoneNumber(number, '-'))
		phoneElement[elementNum].style.backgroundColor = '#FFFFFF';
	else if(number.length===12 && number.charAt(3)==='.' && number.charAt(7)==='.' && 
			isNumericPhoneNumber(number, '.'))
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

function isNumericPhoneNumber(phonenumber, separator)
{
	var testNumber = phonenumber;
	if(separator === '-')
		testNumber = phonenumber.replace(/-/g, '');
	else if(separator === '.')
		testNumber = phonenumber.replace(/./g, '');

	return !isNaN(testNumber);	
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

    // The rest of this code assumes you are not using a library.
    // It can be made less wordy if you use one.
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