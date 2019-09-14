var userJson = {};
var profileCBGroups = [];
var profileTableGroups = []; 
function showEditProfileDialog() 
{
    	var params = "callback=?";
			
    	$.getJSON('getuser', params, function(user)
    	{
    		userJson = user;
    			
    		if(userJson.hasOwnProperty('error'))
      	{
			window.location=document.getElementById('timeoutanchor').href;
      	}
    		else if(userJson.hasOwnProperty('lastname'))
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
    	    			profileTableGroups.length= 0;
    	    			for(var i=0; i<userJson.groups.length; i++)
    	    				profileTableGroups.push(userJson.groups[i]);
    	    			
    	    			updateGroupTable();
    	    			
    	    			//get profile eligible groups from server and place into the group combobox
    	    			var profileGroupCB = document.getElementById('groupselect');
    	    			var groupparams = "agentid=-1&default=off&profile=yes&callback=?";
    	    			$.getJSON('groups', groupparams, function(data)
    	    			{
    	    				profileCBGroups = data;
    	    				//clear the group combo boxes
    	    	    			profileGroupCB.options.length = 0;
    	    	    			
    	    	    			//add a dummy option to the top of the group select
    	    	    			addComboBoxOption(profileGroupCB, "--Select a School/Org To Add--", -1);
    	    			
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
    
function updateGroupTable()
{
    	$("#grouptbody").empty();
	for(var i=0; i<profileTableGroups.length; i++)
		addGroupTableRow(i, profileTableGroups[i]);
}
    
function addGroupTableRow(index, group)
{
    var tabBody = document.getElementById("grouptable").getElementsByTagName('tbody').item(0);
    row=document.createElement("tr");
        
    var cell= document.createElement("td");
    cell.appendChild(document.createTextNode(group.name));
    cell.style.fontSize='12px';
    cell.style.width = '320px';
    row.appendChild(cell);
        
    var btn = document.createElement("button");
    btn.value= group.id;
    btn.type="button";
   	btn.innerHTML = "Remove";
    	btn.onclick=function() {removeGroup(group.id);};
    row.appendChild(btn);
        
    tabBody.appendChild(row);
}

function onGroupSelected(selElem)
{
	var addButton = document.getElementById('addgroup');
	
	//if a value of -1 is selected, disable the add button
	if(selElem.selectedIndex > 0)
	{	
		addButton.value = 'changed';
		addButton.style.backgroundColor='#336699';
		addButton.disabled = false;
	}
	else
	{
		addButton.value = 'unchanged';
		addButton.style.backgroundColor='Gray';
		addButton.disabled = true;
	}	
}
   
function addGroup()
{
	//check to see if change is allowed
	var addButton = document.getElementById('addgroup');
	if(addButton.value === 'changed')
	{
		var groupsel = document.getElementById("groupselect");
    		var addedGroup = profileCBGroups[groupsel.selectedIndex-1];
    		console.log(addedGroup);

    		profileTableGroups.push(addedGroup);
    		updateGroupTable();
    		checkForProfileChange();
    		
    		groupsel.selectedIndex = 0;
    		
    		addButton.value = 'unchanged';
    		addButton.style.backgroundColor='Gray';
    		addButton.disabled = true;
	}
}
    
function removeGroup(groupid)
{
	var index = 0;
    	while(index < profileTableGroups.length && profileTableGroups[index].id !== groupid)
    		index++;
    		
    	if(index <= profileTableGroups.length)
    	{
    		//found the group, remove it from groups array
    		profileTableGroups.splice(index, 1);
		updateGroupTable();
		checkForProfileChange();
    	}
}
    
function checkForProfileChange()
{
    	var updateButton = document.getElementById('update');
    	updateButton.value = 'unchanged';
    	updateButton.style.backgroundColor='Gray';
	updateButton.disabled = true;
    		
    	if(document.getElementById('userfirstname').value !== userJson.firstname ||
    		document.getElementById('userlastname').value !== userJson.lastname ||
    		document.getElementById('userorg').value !== userJson.org ||
    		document.getElementById('usertitle').value !== userJson.title ||
    		document.getElementById('useremail').value !== userJson.email ||
    		document.getElementById('userphone').value !== userJson.phone)
   	{
    		updateButton.value = 'changed';
    		updateButton.style.backgroundColor='#336699';
    	    updateButton.disabled = false
   	}
    	else if(userJson.groups.length != profileTableGroups.length)
    	{
    		updateButton.value = 'changed';
    		updateButton.style.backgroundColor='#336699';
    	    updateButton.disabled = false;
    	}
    	else
    	{
        	var index = 0;
        	for(var index = 0; index < profileTableGroups.length; index++)
        	{
        		if(profileTableGroups[index].id !== userJson.groups[index].id)
        		{
        			updateButton.value= 'changed';
        			updateButton.style.backgroundColor='#336699';
            	    updateButton.disabled = false
        		}		
        	}
    	}
}
    
function onUpdateProfile(button)
{
	if(button.value == 'changed')
   	{
    		var params = "firstname=" + document.getElementById('userfirstname').value
		+ "&" + "lastname=" + document.getElementById('userlastname').value
		+ "&" + "org=" + document.getElementById('userorg').value
		+ "&" + "title=" + document.getElementById('usertitle').value
		+ "&" + "email=" + document.getElementById('useremail').value
		+ "&" + "phone=" + document.getElementById('userphone').value;
    			
    		for(var index=0; index<profileTableGroups.length; index++)
    			params= params.concat("&group",  index, "=", profileTableGroups[index].id);
    				
		params= params.concat("&", "callback=?");
			
		$.getJSON('updateuser', params, function(responseJson)
		{
			button.value = 'unchanged';
    			button.style.backgroundColor='Gray';
    	        	button.disabled = true;
    	        		
    	        	if(responseJson.hasOwnProperty('message'))
    	        	{
				document.getElementById('message').textContent= responseJson.message;
    	        	}
    	        	else
    	        	{
    	        		userJson = responseJson;
    	        		document.getElementById('message').textContent= "User profile update successful";
    	        	}	
		});
		    		
		window.location=document.getElementById('closepopup').href;
	}
	else
	{
		onProfileNotChanged();
	}
}
    
function onProfileNotChanged()
{
	var params = {}	
	$.post('profileunchanged', params, function(response)
	{
		document.getElementById('message').textContent= response.message;
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