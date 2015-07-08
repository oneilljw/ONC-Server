package oncserver;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class ONCHttpHandler implements HttpHandler
{
	public void handle(HttpExchange t) throws IOException 
    {
//    	@SuppressWarnings("unchecked")
//		Map<String, Object> params = (Map<String, Object>)t.getAttribute("parameters");
		
		ServerUI serverUI = ServerUI.getInstance();
		serverUI.addLogMessage("Web request, context =  " + t.getRequestURI().toASCIIString());
//    	System.out.println(t.getRequestURI().toASCIIString());
    	
    	if(t.getRequestURI().toString().contains("login"))
    	{
/*    		
    		String response = "<html>"
	      		+"<body>"
    			+"<style>"
	      		+"IMG.displayed {display: block; margin-left: auto; margin-right: auto }"
    			+"</style>"
	      		+"<img src= \"oncsplash.gif\" alt=\"HTML5 Icon\" class= \"displayed\">"
	      		+"</body>"
	      		+ "</html>";
*/	      		
    		String response = "<!DOCTYPE html><html>"
    		+"<body>"
    		+"<style>"
      		+"IMG.displayed {display: block; margin-left: auto; margin-right: auto }"
			+"</style>"
      		+"<img src= \"oncsplash.gif\" alt=\"HTML5 Icon\" class= \"displayed\">"
    		+"<p><b><i>Welcome to Our Neighbors Child</i></b></p>"
    		+"<p>Please Login</p>"
    		+"<form action=\"action_page.php\">"
    		+"User Name: "
    		+"<input type=\"text\" name=\"firstname\">"
    		+"<br>"
    		+"Password:   "
    		+"<input type=\"text\" name=\"lastname\">"
    		+"<br><br>"
    		+"<input type=\"submit\">"
    		+"</form>"
    		+"</body>"
    		+"</html>";

    		t.sendResponseHeaders(200, response.length());
    		OutputStream os = t.getResponseBody();
    		os.write(response.getBytes());
    		os.close();
    	}
    	else if(t.getRequestURI().toString().contains("oncsplash"))
    	{
    		// add the required response header for a PDF file
  	      	Headers h = t.getResponseHeaders();
  	      	h.add("Content-Type", "image/gif");

  	      	// onc splash screen
  	      	String path = String.format("%s/oncsplash.gif", System.getProperty("user.dir"));
  	      	File file = new File (path);
  	      	byte [] bytearray  = new byte [(int)file.length()];
  	      
  	      	FileInputStream fis = new FileInputStream(file);
  	      
  	      	BufferedInputStream bis = new BufferedInputStream(fis);
  	      	bis.read(bytearray, 0, bytearray.length);
  	      	bis.close();

  	      	//send the response.
  	      	t.sendResponseHeaders(200, file.length());
  	      	OutputStream os = t.getResponseBody();
  	      	os.write(bytearray,0,bytearray.length);
  	      	os.close();
    	}
    	else if(t.getRequestURI().toString().contains("test"))
    	{
    		 String response = "<html>"
    		      		+"<body style=background-image:url('http://www.ourneighborschild.org/uploads/7/7/7/5/7775392/9738099.jpg?163')>"
    		      		+"<form><input type=\"button\" id=\"btn01\" value=\"OK\"></form>"
    		      		+"<p>Click the \"Disable\" button to disable the \"OK\" button:</p>"
    		      		+"<button onclick=\"disableElement()\">Disable</button>"
    		      		+"<script>"
    		      		+"function disableElement() {"
    		      		+"document.getElementById(\"btn01\").disabled = true;}"
    		      		+"</script>"
    		      		+"</body>"
    		      		+ "</html>";
    		  t.sendResponseHeaders(200, response.length());
    		  OutputStream os = t.getResponseBody();
    		  os.write(response.getBytes());
    		  os.close();
    	}
    }
}
