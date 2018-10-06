package cj.studio.ecm.chip.netsite;

import java.util.Map;

import cj.studio.ecm.CJSystem;
import cj.studio.ecm.EcmException;
import cj.studio.ecm.IAssembly;
import cj.studio.ecm.IServiceAfter;
import cj.studio.ecm.IServiceSite;
import cj.studio.ecm.ServiceCollection;
import cj.studio.ecm.annotation.CjService;
import cj.studio.ecm.annotation.CjServiceRef;
import cj.studio.ecm.graph.IGraph;
import cj.studio.ecm.graph.IGraphHandler;
import cj.studio.ecm.logging.ILogging;
import cj.studio.ecm.net.IClient;
import cj.studio.ecm.net.INetboard;
import cj.studio.ecm.net.INetboardEvent;
import cj.studio.ecm.net.IServer;
import cj.studio.ecm.net.graph.INetGraph;

@CjService(name = "netSite")
public class NetSite implements INetSite, IServiceAfter {
	@CjServiceRef(refByName = "graphContainer")
	private IGraphContainer container;
	private INetboard netboard;
	ILogging log;
	public NetSite() {
		log=CJSystem.current().environment().logging();
	}

	@Override
	public INetboard getNetboard() {
		return netboard;
	}

	@Override
	public void chipsLoadFinished(Map<String, IAssembly> lsall) {
		netboard.doEvent(INetboardEvent.EVENT_CHIPS_LOADED, lsall);
	}

	@Override
	public void finished() {
		container.runNetsiteFinished();
		netboard.doEvent(INetboardEvent.EVENT_NETSITE_LOADED);
	}

	@Override
	public void setNetSiteRoot(String d) {
		container.setNetSiteRoot(d);
	}

	@Override
	public void onAfter(IServiceSite site) {
		netboard = (INetboard) site.getService(INetboard.KEY_NETBOARD);
	}

	@Override
	public void dispose() {
		container.dispose();
		netboard.dispose();
	}

	public void init(String home) {
		container.init(home);
		// INetSiteSessionCenter sessionCenter = new NetSiteSessionCenter();
		// sessionCenter.setStrategy(new TokenDistributionStrategy());
		// container.setSessionCenter(sessionCenter);
	}

	@Override
	public void notifyKillProc(String pid, IAssembly a) {
		try {
			ServiceCollection<IGraph> col = a.workbin().part(IGraph.class);
			StringBuffer sb = new StringBuffer();
			for (IGraph g : col) {
				IGraphHandler gh = (IGraphHandler) g.site().getService(
						IGraphHandler.KEY_GRAPHHANDLER);
				if (gh != null) {
					Object ret = gh.event(IGraphHandler.EVENT_NETBOARD_EVENT);
					if (ret != null && (ret instanceof INetboardEvent)) {
						if (!netboard.containsEvent(ret))
							netboard.addEvent((INetboardEvent) ret);
					}
				}
				String key = String.format("%s#%s", pid, g.name());
				container.removeChipGraph(
				/*pid + "#"
				+ g.protocolFactory().getProtocol()*/key, sb);
			}
			System.out.println(sb.toString());
			try {
				netboard.doEvent(INetboardEvent.EVENT_KILLPROCE, pid, a, col);
			} catch (Exception e) {
				log.error(e);
			}
		} catch (Exception e) {
			throw e;
		}
	}

	@Override
	public void notifyRunProc(String pid, IAssembly a) {
		try {
			ServiceCollection<IGraph> col = a.workbin().part(IGraph.class);
			if (col.isEmpty()) {
				throw new EcmException(
						String.format("程序集%s-%s中没有定义graph，或没声明为开放类型.程序集名：%s", a
								.info().getName(), a.info().getVersion(), a
								.fileName()));
			}
			StringBuffer sb = new StringBuffer();
			for (IGraph g : col) {
				if (!g.isInit())
					continue;
				String key = String.format("%s#%s", pid, g.name());
				container.addChipGraph(key, g, sb);

				IGraphHandler gh = (IGraphHandler) g.site().getService(
						IGraphHandler.KEY_GRAPHHANDLER);
				if (gh != null) {
					Object ret = gh.event(IGraphHandler.EVENT_NETBOARD_EVENT);
					if (ret != null ) {
						if(! (ret instanceof INetboardEvent)){
							log.error(getClass(),String.format("graph:%s中未实现INetboardEvent事件", key));
							continue;
						}
						if (!netboard.containsEvent(ret))
							netboard.addEvent((INetboardEvent) ret);
					}
				}
			}
			System.out.println(sb);
			try {
				netboard.doEvent(INetboardEvent.EVENT_RUNPROCE, pid, a, col,
						container.getChipGraphis());
			} catch (Exception e) {
				log.error(getClass(),e);
			}
		} catch (Exception e) {
			throw e;
		}
	}

	@Override
	public void notifyClientBuildGraph(INetGraph ng) {
		container.retrieveClientNetConnectors(ng);
	}

	@Override
	public void notifyServerBuildGraph(INetGraph ng) {
		container.retrieveServerNetConnectors(ng);
	}

	@Override
	public void notifyServerStart(IServer s) {
		INetGraph g = s.buildNetGraph();
		if (!g.isInit())
			g.initGraph();
		StringBuffer sb = new StringBuffer();
		container.startedNet(g, sb);
		System.out.println(sb);
	}

	@Override
	public void notifyServerStop(IServer s) {
		INetGraph g = s.buildNetGraph();
		StringBuffer sb = new StringBuffer();
		container.removeNetGraph(g.name(), sb);
		System.out.println(sb);
	}
	@Override
	public void removeNetGraphByName(String netname) {
		StringBuffer sb = new StringBuffer();
		container.removeNetGraph(netname, sb);
	}
	@Override
	public void notifyClientClose(IClient c) {
		INetGraph g = c.buildNetGraph();
		StringBuffer sb = new StringBuffer();
		container.removeNetGraph(g.name(), sb);
		System.out.println(sb);
	}

	@Override
	public void notifyClientConnect(IClient c) {
		INetGraph g = c.buildNetGraph();
		if (!g.isInit())
			return;
		StringBuffer sb = new StringBuffer();
		container.startedNet(g, sb);
		System.out.println(sb);
	}

	// class NetSiteSessionCenter implements INetSiteSessionCenter {
	// private ITokenDistributionStrategy strategy;
	// private Map<String, ISessionManager> map;// key#sessionManagerName
	//
	// public NetSiteSessionCenter() {
	// map = new HashMap<String, ISessionManager>();
	// }
	//
	// @Override
	// public void setStrategy(ITokenDistributionStrategy strategy) {
	// this.strategy = strategy;
	// }
	//
	// @Override
	// public void unregister(String id) {
	// if (!map.containsKey(id)) {
	// return;
	// }
	// ISessionManager sm = map.get(id);
	// if (sm != null) {
	// sm.dispose();
	// }
	// map.remove(id);
	// }
	//
	// @Override
	// public void register(ISessionManager sm) {
	// sm.setCjTokenDistributionStrategy(strategy);
	// if (map.containsKey(sm.id())) {
	// throw new EcmException(String.format(
	// "会话管理器名称冲突:%s，一个graph只能有一个会话管理器，原因可能出在这。", sm.id()));
	// }
	// map.put(sm.id(), sm);
	// }
	//
	// @Override
	// public void dispose() {
	// String[] keys = map.keySet().toArray(new String[0]);
	// for (String key : keys) {
	// ISessionManager sm = map.get(key);
	// sm.dispose();
	// }
	// map.clear();
	// }
	// }

}
