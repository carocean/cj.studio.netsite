package cj.studio.ecm.chip.netsite;

import java.util.List;
import java.util.Map;

import cj.studio.ecm.net.IServer;
import cj.ultimate.IDisposable;

public interface IServerManager extends IDisposable{
	IServer startServer(String port, String protocol, Map<String, String> props);

	void stopServer(String serverName);
	List<IServer> getAll();

	void stopAll();
	void save();
	void restore();
	void init(String home);
	void clearmemo();
	void update(String serverName,Map<String,String> props);
	void setRootSiteProcess(String d);
}
