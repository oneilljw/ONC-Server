package oncserver;

import com.sun.net.httpserver.HttpHandler;

public interface ONCHttpHandler extends HttpHandler 
{
	void loadWebpages();
}
