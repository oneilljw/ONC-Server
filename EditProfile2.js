var userJson = {};
var profileCBGroups = [];
var baselineGroupCheckSum = 0;
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
	    			className: 'edit-button',
	    			enabled: false
	    		},
	    		{ 
	    			text: 'Add School/Org.', 
	    			action: function ( e, dt, node, config ) { addGroup(); },
	    			className: 'edit-button',
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
    
function onProfileNotChanged()
{
	var params = {}	
	$.post('profileunchanged', params, function(response)
	{
		document.getElementById('banner-message').textContent= response.message;
		document.getElementById('welcome-div').style.display = "block";
	}, "jsonp");
		
	window.location=document.getElementById('closepopup').href;
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