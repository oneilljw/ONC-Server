var userJson = {};
var profileCBGroups = [];
var baselineGroupCheckSum = 0;
var errorColor = '#FF9999';
function setInput(elementID, value)
{
	if(value !== null && value !== 'null')
		document.getElementById(elementID).value = value;
	else
		document.getElementById(elementID).value = '';
}   
function addComboBoxOption(combobox, text, value)
{
	var option = document.createElement("option");
	option.text = text;
    option.value = value;
    try 
    {
        combobox.add(option, null); //Standard 
    }
    catch(error)
    {
        combobox.add(option); // IE only
    }
}
function setBannerVisible(bDisplay)
{
	if(bDisplay)
	{	
		if(sessionStorage.getItem('banner') == null)
		{
			//first time the page is accessed in the session
			sessionStorage.setItem('banner', 'block');
			$('#welcome-div').css('display', 'block');
		}
		else if(sessionStorage.getItem('banner') == 'block')
		{	
			sessionStorage.setItem('banner', 'none');
			$('#welcome-div').css('display', 'none');
		}
		else
			$('#welcome-div').css('display', 'none');
	}
	else
	{
		sessionStorage.setItem('banner', 'none');
		$('#welcome-div').css('display', 'none');
	}
}
function createGroupTable()
{
	var table = $('#profiletable').DataTable( 
	{
	    dom: 'Brt',
	    orderClasses: false,
	    stripeClasses:['stripe1','stripe2'],
	    buttons: 
	    {
	    	buttons: 
	    	[
	    		{ 
	    			text: 'Remove School/Org.', 
	    			action: function ( e, dt, node, config )
	    			{
	    			    var selectedRow = table.row('.selected').data();
	    			    if(typeof selectedRow !== 'undefined')		    						
	    			    	removeGroup(selectedRow);
	    			},
	    			enabled: false
	    		},
	    		{ 
	    			text: 'Add School/Org.', 
	    			action: function ( e, dt, node, config ) { addGroup(); },
	    			enabled: false
	    		},
	    	],
	    	dom: 
	    	{
	    		button: 
	    		{
	    			className: ''
	    		}
	    	}
	    },
	    columns: 
	    [
	    	{ data: "id", visible: false},
	    	{ data: "name", width: '360px'}
	    ],
	    scrollY: "100px",
	    scrollCollapse: true,
	    paging: false
	});
	
	$('#profiletable tbody').on( 'click', 'tr', function () 
	{
	    if( $(this).hasClass('selected') )
	    {
	    	$(this).removeClass('selected');
	    }
	    else
	    {
	    	table.$('tr.selected').removeClass('selected');
	    	$(this).addClass('selected');
	    	table.button( 0 ).enable( true );
	    }
	});
}
function showEditProfileDialog() 
{
    var params = "callback=?";		
    $.getJSON('getuser', params, function(user)
    {
    	userJson = user;
    			
    	if(user.hasOwnProperty('error'))
    	{
    		window.location=document.getElementById('timeoutanchor').href;
    	}
    	else if(user.hasOwnProperty('lastname'))
    	{
    		setInput('userfirstname', user.firstname);
    		setInput('userlastname', user.lastname);
    		setInput('userorg', user.org);
	    	setInput('usertitle', user.title);
	    	setInput('useremail', user.email);
	    	setInput('workphone', user.workphone);
	    	setInput('usercellphone', user.cellphone);
    	    			
    	    //clear & populate the profileTable array with groups from user profile, if
    	    //user has Agent, Admin or Sys_Admin permission	
    	    if(user.permission === 'Agent' || user.permission === 'Admin' || user.permission === 'Sys_Admin')
    	    {
    	    	var table = $('#profiletable').DataTable();
        		table.clear();
    			table.rows.add(user.groups);
    			baselineGroupCheckSum = table.column(0).data().sum();
    			table.draw();
    	    			
    	    	//get profile eligible groups from server and place into the group combobox
    	    	var profileGroupCB = document.getElementById('groupselect');
    	    	var groupparams = "agentid=-1&profile=yes&callback=?";
    	    	$.getJSON('groups', groupparams, function(data)
    	    	{
    	    		profileCBGroups = data;
    	    		
    	    		//clear the group combo boxes
    	    	    profileGroupCB.options.length = 0;
    	    	    			
    	    	    //add a dummy option to the top of the group select
    	    	    addComboBoxOption(profileGroupCB, "-- Select a School/Org To Add --", -1);
    	    			
    	    	    //add the groups for the agent
    	    		for (var i=0; i < data.length; i++)
    	    			addComboBoxOption(profileGroupCB, data[i].name, data[i].id);
    	    	});
    	    }
    	}
    });
}
function onGroupSelected(selElem)
{
	//called when group select is changed
	var table = $('#profiletable').DataTable();
	table.button( 1 ).enable( selElem.selectedIndex > 0 );
}  
function addGroup()
{
	//can add group as long as it isn't already in the table
	var groupsel = document.getElementById("groupselect");
	var addGroup = groupsel.options[groupsel.selectedIndex];

	var table = $('#profiletable').DataTable();
	var data = table.data().toArray();

	//test to see if group is already in the table
	for(i=0; i<data.length; i++)
		if(data[i].id == addGroup.value)
			break;
			
	if(i < data.length)
		console.log("Can't add group, it's already in user profile");
	else
	{
		var addedGroup = profileCBGroups[groupsel.selectedIndex-1];

		table.row.add(addedGroup);
		table.draw();
		table.button(1).enable( false );

		checkForProfileChange();
    		
		groupsel.selectedIndex = 0;
	}
}    
function removeGroup(selectedRow)
{
	//remove the selected row from the table and disable the remove button
	var table = $('#profiletable').DataTable();
	table.row('.selected').remove().draw();
	table.button(0).enable( false );
	checkForProfileChange();   
}
function checkForProfileChange()
{
	var table = $('#profiletable').DataTable();
    var updateButton = document.getElementById('update');

	updateButton.disabled = table.column(0).data().sum() == baselineGroupCheckSum &&
    	document.getElementById('userfirstname').value == userJson.firstname &&
    	 document.getElementById('userlastname').value == userJson.lastname &&
    	  document.getElementById('userorg').value == userJson.org &&
    	   document.getElementById('usertitle').value == userJson.title &&
    	    document.getElementById('useremail').value == userJson.email &&
    	     document.getElementById('workphone').value == userJson.workphone &&
    	      document.getElementById('usercellphone').value == userJson.cellphone;
} 
function verifyCellPhone()
{
	let number = document.getElementById('usercellphone').value;
	
	if(number.length===12 && number.charAt(3)==='-' && number.charAt(7)==='-' && isNumericPhoneNumber(number, '-'))
		return true;
	else if(number.length===12 && number.charAt(3)==='.' && number.charAt(7)==='.' && isNumericPhoneNumber(number, '.'))
		return true;
	else if(number.length===12 && number.charAt(0)==='(' && number.charAt(4)===')')
		return true;
	else if(number.length===13 && number.charAt(0)==='(' && number.charAt(4)===')' && number.charAt(5)===' ')
		return true;
	else if(number.length===10 && !isNaN(number))
		return true;
	else
		return false;
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
function onUpdateProfile()
{
	if(verifyCellPhone())
	{	
	    var params = "firstname=" + document.getElementById('userfirstname').value
			+ "&" + "lastname=" + document.getElementById('userlastname').value
			+ "&" + "org=" + document.getElementById('userorg').value
			+ "&" + "title=" + document.getElementById('usertitle').value
			+ "&" + "email=" + document.getElementById('useremail').value
			+ "&" + "workphone=" + document.getElementById('workphone').value
	    	+ "&" + "cellphone=" + document.getElementById('usercellphone').value;
	
	    var table = $('#profiletable').DataTable();
		var data = table.data().toArray();
		for(var index=0; index<data.length; index++)
	    	params= params.concat("&group",  index, "=", data[index].id);
	    				
		params= params.concat("&", "callback=?");	
	
		$.getJSON('updateuser', params, function(responseJson)
		{
			document.getElementById('cancel').disabled = false;
			document.getElementById('update').disabled = true;
	    	
	    	if(responseJson.hasOwnProperty('message'))
	    	{
				document.getElementById('banner-message').textContent= responseJson.message;
	    	}
	    	else
	    	{
	    	    userJson = responseJson;
	    	    document.getElementById('banner-message').textContent= "User profile successfully updated!";
	    	}
	    	document.getElementById('welcome-div').style.display = "block";
	    	
	    	window.location = '#close'; 
		});
	}
	else
	{
		//disable update and the no change button
		document.getElementById('cancel').disabled = false;
		document.getElementById('update').disabled = true;
		
		//show pop-up prompting for cell phone
		document.getElementById('cellwarningmssg').textContent = "Each user MUST have a valid cellphone #. ONC relies on cell phones " +
		"for account recovery and two-factor authentication purposes.";
		
		window.location=document.getElementById('cellwarninganchor').href;
	}
}
function onProfileNotChanged()
{
	if(verifyCellPhone())
	{
		var params = {}	
		$.post('profileunchanged', params, function(response)
		{
			document.getElementById('banner-message').textContent= response.message;
		}, "jsonp");
	
		window.location = '#close';
	}
	else
	{
		//disable update and the no change button
		document.getElementById('cancel').disabled = false;
		document.getElementById('update').disabled = true;
		
		//change the background of the cell phone field to the error color
		document.getElementById('usercellphone').style.backgroundColor = errorColor;
		
		//show pop-up prompting for cell phone
		document.getElementById('cellwarningmssg').textContent = "Each user MUST have a valid cellphone #. ONC relies on cell phones " +
		"for account recovery and two-factor authentication purposes.";
		
		window.location=document.getElementById('cellwarninganchor').href;
	}
}
function checkChangePWEnable()
{
    if(document.getElementById("currpw").value === '' || 
    	document.getElementById("newpw").value === '' ||
    	 document.getElementById("verifypw").value === '')
    {
    	document.getElementById('btnchangepw').disabled = true;
    }
    else
    	document.getElementById('btnchangepw').disabled = false; 	
}
function onChangePW() 
{
	if(validatePassword())
	{
		var params = {}	
		params["field1"] = document.getElementById('currpw').value;
		params["field2"] = document.getElementById('newpw').value;
		$.post('reqchangepw', params, function(response)
		{
			document.getElementById('banner-message').textContent= response;
			document.getElementById('welcome-div').style.display = "block";
		});
		
		document.getElementById('currpw').value = "";
    	document.getElementById('newpw').value = "";
    	document.getElementById('verifypw').value = "";
    	window.location='#close';
	}
}
function validatePassword()
{
	var mssgEl = document.getElementById("pw_mssg");
    var pass1El = document.getElementById("newpw");
    var pass2El = document.getElementById("verifypw");
            
    if(pass1El.value !== pass2El.value)
    {
        //New and Verify Passwords do not match
        pass1El.style.backgroundColor = errorColor;
        pass2El.style.backgroundColor = errorColor;
        mssgEl.textContent = "New and Verify passwords don't match, please try again!";
        mssgEl.style.display = 'block';
        return false;
    }
    else
    {
    	//All tests pass
    	pass1El.style.backgroundColor = "#FFFFFF";
        pass2El.style.backgroundColor = "#FFFFFF";
        mssgEl.style.display = 'none';
        return true;
    }
}
function onCancel()
{
	window.location='#close';
}
function onLogoutLink()
{
	sessionStorage.clear();	//clear filter settings, id's, etc.
	
	var params = {};
	post('logout', params);
}
function onSessionTimeout()
{
	sessionStorage.clear();	//clear filter settings, id's, etc.
	
	window.location.assign('timeout');
}
function onSubmit()
{
	document.getElementById(updateObject.resource).submit();
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