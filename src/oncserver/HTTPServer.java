package oncserver;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;


public class HTTPServer 
{
	private HttpServer server;
	
	public HTTPServer()
	{
		try {
			server = HttpServer.create(new InetSocketAddress(8902), 0);
			server.createContext("/test", new MyHandler());
		    server.createContext("/oncsplash", new GetHandler());
		    server.createContext("/login", new LoginHandler());
		    server.setExecutor(null); // creates a default executor
		    server.start();
		    
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	static class MyHandler implements HttpHandler
	{
	    public void handle(HttpExchange t) throws IOException 
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
	
	static class GetHandler implements HttpHandler 
	{
	    public void handle(HttpExchange t) throws IOException 
	    {
	      // add the required response header for a PDF file
	      Headers h = t.getResponseHeaders();
	      h.add("Content-Type", "image/gif");

	      // a PDF (you provide your own!)
	      String path = String.format("%s/oncsplash.gif",
										System.getProperty("user.dir"));
	      File file = new File (path);
	      byte [] bytearray  = new byte [(int)file.length()];
	      FileInputStream fis = new FileInputStream(file);
	      BufferedInputStream bis = new BufferedInputStream(fis);
	      bis.read(bytearray, 0, bytearray.length);

	      // ok, we are ready to send the response.
	      t.sendResponseHeaders(200, file.length());
	      OutputStream os = t.getResponseBody();
	      os.write(bytearray,0,bytearray.length);
	      os.close();
	    }
	}
	
	static class LoginHandler implements HttpHandler 
	{
	    public void handle(HttpExchange t) throws IOException 
	    {
	    	String response = "<html>"
		      		+"<body>"
	    			+"<style>"
		      		+"IMG.displayed {display: block; margin-left: auto; margin-right: auto }"
	    			+"</style>"
		      		+"<img src= \"oncsplash.gif\" alt=\"HTML5 Icon\" class= \"displayed\">"
		      		+"</body>"
		      		+ "</html>";
		      t.sendResponseHeaders(200, response.length());
		      OutputStream os = t.getResponseBody();
		      os.write(response.getBytes());
		      os.close();
	    }
	}
}
