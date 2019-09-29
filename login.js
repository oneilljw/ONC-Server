function onInputChange()
{
	loginButton = document.getElementById('submit');
		
	if(document.getElementById('field1').value !== "" && document.getElementById('field2').value !== '')
	{	
		loginButton.style.backgroundColor='#336699';
		loginButton.disabled = false;
	}
	else
	{
		loginButton.style.backgroundColor='Gray';
		loginButton.disabled = true;
	}
}