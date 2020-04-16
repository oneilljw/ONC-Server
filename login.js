function onInputChange()
{
	loginButton = document.getElementById('submit');
		
	if(document.getElementById('field1').value !== "" && document.getElementById('field2').value !== '')
	{
		//set the offset so server knows time zone
		document.getElementById('offset').value = new Date().getTimezoneOffset();
		loginButton.disabled = false;
	}
	else
		loginButton.disabled = true;
}