package cj.studio.ecm.chip.netsite;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cj.studio.ecm.CJSystem;
import cj.studio.ecm.EcmException;
import cj.studio.ecm.IServiceAfter;
import cj.studio.ecm.IServiceSite;
import cj.studio.ecm.annotation.CjService;
import cj.studio.ecm.annotation.CjServiceRef;
import cj.studio.ecm.logging.ILogging;
import cj.studio.ecm.net.INetGraphInitedEvent;
import cj.studio.ecm.net.INetboard;
import cj.studio.ecm.net.INetboardEvent;
import cj.studio.ecm.net.IServer;
import cj.studio.ecm.net.graph.INetGraph;
import cj.studio.ecm.net.nio.netty.WebsocketxNettyServer;
import cj.studio.ecm.net.nio.netty.http.HttpNettyServer;
import cj.studio.ecm.net.nio.netty.local.LocalNettyServer;
import cj.studio.ecm.net.nio.netty.tcp.TcpNettyServer;
import cj.studio.ecm.net.nio.netty.udt.UdtNettyServer;
import cj.studio.ecm.net.rio.tcp.TcpCjNetServer;
import cj.studio.ecm.net.rio.udt.UdtCjNetServer;
import cj.studio.ecm.net.switch0.Switch;
import cj.ultimate.gson2.com.google.gson.Gson;
import cj.ultimate.util.StringUtil;

@CjService(name = "serverManager")
public class ServerManager implements IServerManager, IServiceAfter {
	private Map<String, IServer> works;// 动态服务,key<serverName>
	private Map<String, IServer> fixed;// 预配服务,key<serverName>
	private String serverconf;
	@CjServiceRef
	private INetSite netSite;
	@CjServiceRef
	private IChipManager chipManager;
	private String rootSiteProcess;
	ILogging log;

	public ServerManager() {
		works = new HashMap<String, IServer>();
		fixed = new HashMap<String, IServer>();
		this.log = CJSystem.current().environment().logging();
	}

	@Override
	public void setRootSiteProcess(String d) {
		netSite.setNetSiteRoot(d);
		rootSiteProcess = d;
	}

	@Override
	public void onAfter(IServiceSite site) {
		List<IServer> fixed = chipManager.getMainChipServers();
		for (IServer s : fixed) {
			this.fixed.put(s.buildNetGraph().name(), s);
			s.setProperty("role", "fixed-server");
			works.put(s.buildNetGraph().name(), s);
			s.eventNetGraphInited(new INetGraphInitedEvent() {
				public void graphInited(Object sender) {
					netSite.notifyServerStart((IServer) sender);
				}
			});
		}
	}

	@Override
	public void update(String serverName, Map<String, String> props) {
		IServer s = null;
		s = works.get(serverName);
		if (s == null) {
			System.out.println("不存在指定的服务." + serverName);
			return;
		}
		s.stop();
		for (String k : props.keySet()) {
			s.setProperty(k, props.get(k));
		}
		s.eventNetGraphInited(new INetGraphInitedEvent() {
			public void graphInited(Object sender) {
				netSite.notifyServerStart((IServer) sender);
			}
		});
		s.start(s.getPort());
		System.out.println("刷新成功。");
	}

	@Override
	public IServer startServer(String port, String protocol,
			Map<String, String> map) {
		IServer s = finedFixedServer(port, protocol, map.get("serverName"));
		if (s != null) {// 以下是预配服务
			System.out.println("发现预配服务重启中。" + s.buildNetGraph().name());
			String useport = s.getPort();
			s.stop();
			for (String k : map.keySet()) {
				String v = map.get(k);
				s.setProperty(k, v);
			}
			s.eventNetGraphInited(new INetGraphInitedEvent() {
				public void graphInited(Object sender) {
					netSite.notifyServerStop((IServer) sender);
					netSite.notifyServerStart((IServer) sender);
				}
			});
			if (!StringUtil.isEmpty(port))
				useport = port;
			if (!StringUtil.isEmpty(protocol)) {
				if (!StringUtil.isEmpty(map.get("serverName")))
					throw new RuntimeException("预配置服务不能更改其协议。"
							+ map.get("serverName"));
			}
			s.start(useport);
			works.put(s.buildNetGraph().name(), s);
			System.out.println("已重启。" + s.buildNetGraph().name());

		} else {// 以下是动态服务

			switch (protocol) {
			case "udt":
				s = new UdtNettyServer();
				break;
			case "rio-udt":
				s = new UdtCjNetServer();
				break;
			case "tcp":
				s = new TcpNettyServer();
				break;
			case "rio-tcp":
				s = new TcpCjNetServer();
				break;
			case "http":
				s = new HttpNettyServer();
				break;
			case "websocket":
				s = new WebsocketxNettyServer();
				break;
			case "local":
				s = new LocalNettyServer();
				break;
			case "switch":
				s = new Switch();
				break;
			default:
				throw new EcmException("不支持服务器" + protocol);
			}
			for (String k : map.keySet()) {
				s.setProperty(k, map.get(k));
			}
			if (!map.containsKey("bossThreadCount")) {
				s.setProperty("bossThreadCount", "1");
			}
			if (!map.containsKey("workThreadCount")) {
				s.setProperty("workThreadCount", "0");// 0表示默认线程数，由netty自行分配，策略是：每个cpu一个线程。
			}

			if ((!works.containsKey(s.netName()))) {
				INetGraph ng = s.buildNetGraph();
				if (ng == null) {
					log.error(getClass(),
							String.format("net%s没有graph定义", s.netName()));
					return null;
				}
				netSite.notifyServerBuildGraph(ng);
				s.start(port);
				s.setProperty("role", "work-server");
				works.put(s.buildNetGraph().name(), s);
				netSite.notifyServerStart(s);
				log.info(getClass(), String.format("工作服务已启动。%s:%s", s
						.buildNetGraph().name(), port));

				return s;
			}
			log.info(getClass(),
					String.format("已存在服务器%s。故未启动", s.buildNetGraph().name()));
			return works.get(s.buildNetGraph().name());
		}

		return s;

	}

	private IServer finedFixedServer(String port, String protocol,
			String serverName) {
		if (fixed.containsKey(serverName)) {
			IServer s = fixed.get(serverName);
			return s;
		}
		for (String k : fixed.keySet()) {
			IServer s = fixed.get(k);
			if (s.getPort().equals(port) && s.simple().equals(protocol))
				return s;
		}
		return null;
	}

	@Override
	public void init(String home) {
		this.serverconf = home + File.separator + "servers.json";
		restore();
		INetboard board = netSite.getNetboard();
		if (board != null) {
			String names[] = works.keySet().toArray(new String[0]);
			List<IServer> list = new ArrayList<IServer>();
			for (String key : names) {
				list.add(works.get(key));
			}
			board.doEvent(INetboardEvent.EVENT_SERVERS_LOADED, names, list);
		}
	}

	@Override
	public void stopServer(String serverName) {
		IServer s = works.get(serverName);
		if (s == null) {
			System.out.println("要停止的服务不在工作队列." + serverName);
			return;
		}
		netSite.notifyServerStop(s);
		s.stop();
		works.remove(serverName);
		System.out.println("已停止." + serverName);
	}

	@Override
	public void stopAll() {
		String[] keys = works.keySet().toArray(new String[0]);
		for (String k : keys) {
			stopServer(k);
		}
	}

	@Override
	public void save() {
		Map<String, Map<String, String>> smap = new HashMap<String, Map<String, String>>();
		for (String k : works.keySet()) {
			IServer s = works.get(k);
			Map<String, String> props = new HashMap<String, String>();
			smap.put(k, props);
			props.put("port", s.getPort());
			props.put("protocol", s.simple());
			if (StringUtil.isEmpty(s.getProperty("role")))
				props.put("role", "work-server");
			for (String name : s.enumProp()) {
				props.put(name, s.getProperty(name));
			}
		}
		Map<String, String> others = new HashMap<String, String>();
		smap.put("others", others);
		others.put("DefaultNetSiteProcess", rootSiteProcess);

		String fn = serverconf;
		Gson gson = new Gson();
		String json = gson.toJson(smap);
		FileWriter writer = null;
		try {
			writer = new FileWriter(fn);
			writer.write(json);
		} catch (IOException e) {
			throw new EcmException(e);
		} finally {
			if (writer != null)
				try {
					writer.close();
				} catch (IOException e) {
				}
		}
		System.out.println("成功持久在：" + fn);
	}

	@Override
	public void dispose() {
		for (String k : works.keySet()) {
			IServer s = works.get(k);
			netSite.notifyServerStop(s);
			s.stop();
		}
		works.clear();
		this.fixed.clear();
	}

	@SuppressWarnings("unchecked")
	@Override
	public void restore() {
		String fn = serverconf;
		File f = new File(fn);
		if (!f.exists()) {
			System.out.println("没有可供恢复的快照");
			return;
		}
		FileReader reader = null;
		try {
			reader = new FileReader(f);
			Gson gson = new Gson();
			Map<String, Map<String, String>> map = gson.fromJson(reader,
					HashMap.class);
			for (String sname : map.keySet()) {
				if ("others".equals(sname)) {
					Map<String, String> others = map.get(sname);
					rootSiteProcess = others.get("DefaultNetSiteProcess");
					netSite.setNetSiteRoot(rootSiteProcess);
					continue;
				}
				stopServer(sname);
				Map<String, String> props = map.get(sname);
				if (props == null)
					props = new HashMap<String, String>();
				startServer(props.get("port"), props.get("protocol"), props);
			}

			System.out.println("恢复成功");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void clearmemo() {
		File f = new File(serverconf);
		if (f.exists())
			f.delete();
	}

	public List<IServer> getAll() {
		List<IServer> servers = new ArrayList<IServer>();
		for (String n : works.keySet()) {
			servers.add(works.get(n));
		}
		return servers;
	}

}
