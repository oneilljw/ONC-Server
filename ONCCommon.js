var filters = null;
var bResettingFilters = false;
var userJson = {};
var profileCBGroups = [];
var baselineGroupCheckSum = 0;
function initializeFiltersAndRowSelection()
{
	//set the filters to last stored setting
	var bFilterSet = false;
	for(i=0; i<filters.length; i++)
	{
		if(sessionStorage.getItem(filters[i].id) != null)
		{	
			filters[i].value = sessionStorage.getItem(filters[i].id);
			bFilterSet = bFilterSet || filters[i].selectedIndex > 0;
		}
	}
	
	document.getElementById('resetbtn').disabled = !bFilterSet;
	
	//enable row selection
	var table = $('#oncdatatable').DataTable();
	$('#oncdatatable tbody').on( 'click', 'tr', function () 
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
	    				
	$('#oncdatatable tbody').on('dblclick', 'tr', function () 
	{
	    var selectedRowData = table.row( this ).data();
	    rowAction(selectedRowData.id);
	});
}
function getTableDataFromServer()
{
	$.getJSON(getTableData.resource, getTableDataParams(), function(data)
	{
		if(data.hasOwnProperty('error'))
			window.location=document.getElementById('timeoutanchor').href;
		else
		{
			var table = $('#oncdatatable').DataTable();
    		table.clear();
			table.rows.add(data);
			
			for(i=0; i < filters.length; i++)
				if(filters[i].dataset.type === 'search')
					table.column(filters[i].dataset.column)
					 	.search(filters[i].value);

			table.draw();
		}
	});
}
function rowAction(objectID)
{
	sessionStorage.setItem(updateObject.storageItem, objectID);
	var params = {}
	params[updateObject.storageItem] = objectID;
	
	post(updateObject.resource, params);
}
function newAction()
{
	sessionStorage.setItem(updateObject.storageItem, "New");
	
	var params = {}
	post(updateObject.resource, params);
}
function executeFilter(filter)
{
	if(!bResettingFilters)
	{
		sessionStorage.setItem(filter.id, filter.value);
	    
	    if(filter.dataset.type === 'tabledata')
	    	getTableDataFromServer();
	    else if(filter.dataset.type === 'search')
	    {	
	    	var table = $('#oncdatatable').DataTable();
	    	table.column(filter.dataset.column).search(filter.value).draw();
	    }
	    else
	    	getExternalData();
	    	
	    document.getElementById('resetbtn').disabled = !isFilterSet();
	}	
}
function isFilterSet()
{
	var filterSet = false;
	for(i=0; i<filters.length; i++)
	{
		if(filters[i].selectedIndex > 0)
		{
			filterSet = true;
			break;
		}	
	}
	
	return filterSet;
}  
function onResetFilters()
{	
	//set flag to ingnoe listener events
	bResettingFilters = true;
		
	//reset the filters
	var fetchData = 0;	//3-fetch both, 2-fetch external data, 1-fetch table data
	for(i=0; i < filters.length; i++)
	{
		if(filters[i].dataset.type ==='externaldata' && filters[i].selectedIndex > 0)
			fetchData = fetchData | 2;
			
		if(filters[i].dataset.type ==='tabledata' && filters[i].selectedIndex > 0)
			fetchData = fetchData | 1;
		
		filters[i].selectedIndex = 0;
		sessionStorage.setItem(filters[i].id, filters[i].value);
	}
	
	//disable the reset button
	document.getElementById('resetbtn').disabled = true;
	
	//fetch data if necessary (filters/draws the table), otherwise filter the table
	//currently, can't have both external and table data on same web page
	if(fetchData > 1)
		getExternalData();
	else if(fetchData == 1)
		getTableDataFromServer();
	else
	{
		var table = $('#oncdatatable').DataTable();
		table.search( '' ).columns().search( '' ).draw();
	}
	
	//clear the ignore listener event flag
	bResettingFilters = false;
}
function onChangePW() 
{
	if(validate())
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
		window.location=document.getElementById('closepopup').href;
	}
}
function onProfileNotChanged()
{
	var params = {}	
	$.post('profileunchanged', params, function(response)
	{
		document.getElementById('banner-message').textContent= response.message;
	}, "jsonp");
	
	window.location=document.getElementById('closepopup').href;
}
function onCancel()
{
	window.location=document.getElementById('closepopup').href;
}
function validate()
{
	var mssgEl = document.getElementById("pw_mssg");
	var currpwEl = document.getElementById("currpw");
    var pass1El = document.getElementById("newpw");
    var pass2El = document.getElementById("verifypw");
    var ok = true;
    
    if(currpwEl.value == "")
    {
    	//alert("current password field empty");
        currpwEl.style.borderColor = "#E34234";
        
        mssgEl.style.color = "Red";
        mssgEl.textContent = "Current password not provided, please try again!";
        ok = false;
    }
    else if(pass1El.value == '' || pass2El.value == '')
	{
		//alert("Either New or Verify Passwords are empty");
		pass1El.style.borderColor = "#E34234";
		pass2El.style.borderColor = "#E34234";

		mssgEl.style.color = "Red";
		mssgEl.textContent = "Please fill out both New and Confirm, try again!";
		ok = false;
	}        
    else if(pass1El.value !== pass2El.value)
    {
        //alert("New and Verify Passwords do not match");
        pass1El.style.borderColor = "#E34234";
        pass2El.style.borderColor = "#E34234";
        
        mssgEl.style.color = "Red";
        mssgEl.textContent = "New and Verify passwords don't match, please try again!";
        ok = false;
    }

    return ok;
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
	    	setInput('userphone', user.phone);
    	    			
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
    	    	var groupparams = "agentid=-1&default=off&profile=yes&callback=?";
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
function setInput(elementID, value)
{
	if(value !== null && value !== 'null')
		document.getElementById(elementID).value = value;
	else
		document.getElementById(elementID).value = '';
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
    	     document.getElementById('userphone').value == userJson.phone;
}   
function onUpdateProfile()
{
    var params = "firstname=" + document.getElementById('userfirstname').value
		+ "&" + "lastname=" + document.getElementById('userlastname').value
		+ "&" + "org=" + document.getElementById('userorg').value
		+ "&" + "title=" + document.getElementById('usertitle').value
		+ "&" + "email=" + document.getElementById('useremail').value
		+ "&" + "phone=" + document.getElementById('userphone').value;

    var table = $('#profiletable').DataTable();
	var data = table.data().toArray();
	for(var index=0; index<data.length; index++)
    	params= params.concat("&group",  index, "=", data[index].id);
    				
	params= params.concat("&", "callback=?");	

	$.getJSON('updateuser', params, function(responseJson)
	{
		var updateButton = document.getElementById('update');
		updateButton.disabled = true;
    	
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
    	
    	window.location=document.getElementById('closepopup').href; 
	});		
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