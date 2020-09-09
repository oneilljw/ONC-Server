/* 
 * Common js for web pages in ONC web app that contain one or more main body
 * data tables or the dashboard web page.
 */
var filters = [];
var resets = [];
var bResettingFilters = false;

function initializeFilters()
{
	//set the filters to last stored setting
	for(let filter of filters)
	{
		if(sessionStorage.getItem(filter.id) != null)	
			filter.value = sessionStorage.getItem(filter.id);
	}
	
	//for each reset button determine if a filter is set. If one or more are, enable the button
	for(let resetbtn of resets)
	{	
		let bFilterSet = false;
		for(let filter of filters)
		{	
			if(filter.dataset.reset == resetbtn.id)
			{
				bFilterSet = bFilterSet || filter.selectedIndex > 0;
			}
		}
	
		resetbtn.disabled = !bFilterSet;		
	}
}
function initializeRowSelection()
{
	let table = $('#oncdatatable').DataTable();
	$('#oncdatatable tbody').on( 'click', 'tr', function () 
	{
		if( $(this).hasClass('selected') )
		{
			$(this).removeClass('selected');
			table.button( 0 ).enable( false );
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
		let selectedRowData = table.row( this ).data();
		rowAction(selectedRowData.id);
	});
}
function initializeMultipleRowSelection()
{
	let table = $('#oncdatatable').DataTable();
	$('#oncdatatable tbody').on( 'click', 'tr', function () 
	{
		if( $(this).hasClass('selected') )
		{
			$(this).removeClass('selected');
//			table.button( 0 ).enable( table.rows( '.selected' ).count() > 0 );
		}
		else
		{
//			table.$('tr.selected').removeClass('selected');
			$(this).addClass('selected');
//			table.button( 0 ).enable( true );
		}
		
		table.button( 0 ).enable( table.rows( '.selected' ).count() == 1 );
//		table.button( 1 ).enable(changeselects['dnschangeselect'] != 'No Change' && table.rows( '.selected' ).count() > 0);
		table.button( 2 ).enable( table.rows( '.selected' ).count() > 0 );
		table.button( 3 ).enable( table.rows( '.selected' ).count() > 0 );
		table.button( 4 ).enable( table.rows( '.selected' ).count() > 0 );
		
		checkApplyChangesEnabled(table);
	});
    
	$('#oncdatatable tbody').on('dblclick', 'tr', function () 
	{
		let selectedRowData = table.row( this ).data();
		rowAction(selectedRowData);
	});
}
function resetTableButtons(table, count)
{
	for(i=0; i< count; i++)
		table.button( i ).enable(false);
	
	//reset the stored change select values
	changeselects['dnschangeselect'] = 'No Change';
}
function getTableDataFromServer(tableElement)
{
	$.getJSON(getTableData.resource, getTableDataParams(), function(data)
	{
		if(data.hasOwnProperty('error'))
			window.location=document.getElementById('timeoutanchor').href;
		else
		{
			var table = $('#' + tableElement.id).DataTable();
    		table.clear();
			table.rows.add(data);
			
			//apply the search for each filter associated with this table
			for(let filter of filters)
				if(filter.dataset.table== tableElement.id && filter.dataset.type === 'search')
					table.column(filter.dataset.column)
					 	.search(filter.value);

			table.draw();
		}
	});
}
function executeFilter(filter)
{
	if(!bResettingFilters)
	{
		let val = filter.value;
		
		sessionStorage.setItem(filter.id, filter.value);
		
	    if(filter.dataset.type === 'tabledata')
	    	getTableDataFromServer(document.getElementById(filter.dataset.table));
	    else if(filter.dataset.type === 'search')
	    {	
	    	let table = $('#'+ filter.dataset.table).DataTable();
	    	table.column(filter.dataset.column).search(val ? '^'+val+'$' : '', true, false).draw();
	    }
	    else
	    	getExternalData(); //dashboard doesn't have data-table attribute
	    
	    const resetbtn =  document.getElementById(filter.dataset.reset);
	    resetbtn.disabled = !isFilterSet(resetbtn);
	}	
}
function isFilterSet(resetbtn)
{
	let bFilterSet = false;
	for(let filter of filters)
	{
		if(filter.dataset.reset == resetbtn.id && filter.selectedIndex > 0)
		{
			bFilterSet = true;
			break;
		}	
	}
	
	return bFilterSet;
}  
function onResetFilters(reset)
{	
	//set flag to ignore listener events
	bResettingFilters = true;
		
	//reset the filters associated with this reset button and determine
	//whether we have to fetch refreshed table data from the server
	let fetchData = 0;	//3-fetch both, 2-fetch external data, 1-fetch table data
	for(let filter of filters)
	{
		if(filter.dataset.reset == reset.id)
		{
			if(filter.dataset.type ==='externaldata' && filter.selectedIndex > 0)
				fetchData = fetchData | 2;
				
			if(filter.dataset.type ==='tabledata' && filter.selectedIndex > 0)
				fetchData = fetchData | 1;
			
			filter.selectedIndex = 0;
			sessionStorage.setItem(filter.id, filter.value);
		}
	}
	
	//disable the reset button
	reset.disabled = true;
	
	//fetch data if necessary (filters/draws the table), otherwise filter the table
	//currently, can't have both external and table data on same web page
	if(fetchData > 1)	
		getExternalData();
	else if(fetchData == 1)	
		getTableDataFromServer(document.getElementById(reset.dataset.table));
	else
	{
		var table = $('#' + reset.dataset.table).DataTable();
		table.search( '' ).columns().search( '' ).draw();
	}
	
	//clear the ignore listener event flag
	bResettingFilters = false;
}