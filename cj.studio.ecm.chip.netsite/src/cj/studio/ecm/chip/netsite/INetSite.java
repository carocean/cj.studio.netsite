package cj.studio.ecm.chip.netsite;

import java.util.Map;

import cj.studio.ecm.IAssembly;
import cj.studio.ecm.net.IClient;
import cj.studio.ecm.net.INetboard;
import cj.studio.ecm.net.IServer;
import cj.studio.ecm.net.graph.INetGraph;
import cj.ultimate.IDisposable;

public interface INetSite extends IDisposable{
	INetboard getNetboard();
	void notifyKillProc(String pid, IAssembly a);

	void notifyRunProc(String pid, IAssembly a);

	void notifyServerStart(IServer s);

	void notifyServerStop(IServer s);

	void notifyClientClose(IClient c);

	void notifyClientConnect(IClient c);

	void init(String home);

	void setNetSiteRoot(String d);

	void chipsLoadFinished(Map<String, IAssembly> lsall);
	void notifyServerBuildGraph(INetGraph ng);
	void notifyClientBuildGraph(INetGraph ng);
	void finished();
	void removeNetGraphByName(String netname);
}
