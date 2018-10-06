package cj.studio.ecm.chip.netsite;

import java.util.List;
import java.util.Map;

import cj.studio.ecm.net.IClient;
import cj.ultimate.IDisposable;

public interface IClientManager extends IDisposable{
	public boolean connected(String clientName);
	void init(String home);

	IClient connect(String ip, String port,String protocol, Map<String, String> props)throws InterruptedException;

	void close(String clientName);

	void save();

	void restore();

	void clear();

	List<IClient> getAll();

	void connect(String name)throws InterruptedException;

}
