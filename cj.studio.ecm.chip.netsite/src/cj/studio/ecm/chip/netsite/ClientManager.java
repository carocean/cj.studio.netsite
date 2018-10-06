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
import cj.studio.ecm.net.IClient;
import cj.studio.ecm.net.INetboard;
import cj.studio.ecm.net.INetboardEvent;
import cj.studio.ecm.net.graph.INetGraph;
import cj.studio.ecm.net.nio.netty.WebsocketxNettyClient;
import cj.studio.ecm.net.nio.netty.http.HttpNettyClient;
import cj.studio.ecm.net.nio.netty.local.LocalClient;
import cj.studio.ecm.net.nio.netty.tcp.TcpNettyClient;
import cj.studio.ecm.net.nio.netty.udt.UdtNettyClient;
import cj.studio.ecm.net.rio.http.JdkHttpClient;
import cj.studio.ecm.net.rio.tcp.TcpCjNetClient;
import cj.studio.ecm.net.rio.udt.UdtCjNetClient;
import cj.ultimate.gson2.com.google.gson.Gson;

@CjService(name = "clientManager")
public class ClientManager implements IClientManager, IServiceAfter {
	private Map<String, IClient> works;// 动态服务,key<clientName>
	private String clientconf;
	@CjServiceRef
	private INetSite netSite;
	ILogging log;

	public ClientManager() {
		works = new HashMap<String, IClient>();
		this.log = CJSystem.current().environment().logging();
	}

	@Override
	public void dispose() {
		for (String k : works.keySet()) {
			IClient c = works.get(k);
			netSite.notifyClientClose(c);
			c.close();
		}
		works.clear();
	}

	@Override
	public void onAfter(IServiceSite site) {
	}

	public void init(String home) {
		this.clientconf = home + File.separator + "client.json";
		restore();
		INetboard board = netSite.getNetboard();
		if (board != null) {
			String names[] = works.keySet().toArray(new String[0]);
			List<IClient> list = new ArrayList<IClient>();
			for (String key : names) {
				list.add(works.get(key));
			}
			board.doEvent(INetboardEvent.EVENT_CLIENTS_LOADED, names, list);
		}
	}

	@Override
	public void connect(String name) throws InterruptedException {
		IClient c = works.get(name);
		if (c == null) {
			throw new EcmException("要启动的服务不存在." + name);
		}
		if ("closed".equals(c.status())) {
			c.connect(c.getHost(), c.getPort());
			System.out.println("已重连。" + c.buildNetGraph().name());
			return;
		}
		System.out.println("客户端状态为可用，本次不做任何操作.");
	}

	public boolean connected(String clientName) {
		return works.containsKey(clientName);
	}

	@Override
	public IClient connect(String host, String port, String protocol,
			Map<String, String> map) throws InterruptedException {
		IClient c = null;
		switch (protocol) {
		case "udt":
			c = new UdtNettyClient();
			break;
		case "rio-udt":
			c = new UdtCjNetClient();
			break;
		case "rio-http":
			c = new JdkHttpClient();
			break;
		case "rio-tcp":
			c = new TcpCjNetClient();
			break;
		case "tcp":
			c = new TcpNettyClient();
			break;
		case "http":
			c = new HttpNettyClient();
			break;
		case "websocket":
			c = new WebsocketxNettyClient();
			break;
		case "local":
			c = new LocalClient();
			break;
		default:
			throw new EcmException("不支持的客户端协议" + protocol);
		}
		for (String k : map.keySet()) {
			c.setProperty(k, map.get(k));
		}
		if (!works.containsKey(c.netName())) {
			try {
				INetGraph ng = c.buildNetGraph();
				if (ng == null) {
					log.error(getClass(),
							String.format("net%s没有graph定义", c.netName()));
					return null;
				}
				netSite.notifyClientBuildGraph(ng);
				c.connect(host, port);
				works.put(c.netName(), c);
				netSite.notifyClientConnect(c);
				System.out.println("客户端已启动。" + c.netName() + ":" + port);
			} catch (Exception e) {
				log.error(getClass(), e);
				if (works.containsKey(c.netName()))
					works.remove(c.netName());
			}
			return c;
		}
		System.out.println("已存在客户端。故未启动" + c.netName());
		return works.get(c.netName());

	}

	@Override
	public void close(String clientName) {
		IClient s = works.get(clientName);
		if (s == null) {
			System.out.println("要停止的客户端不在工作队列." + clientName);
			netSite.removeNetGraphByName(clientName);
			return;
		}
		netSite.notifyClientClose(s);
		s.close();
		works.remove(clientName);
		System.out.println("已停止." + clientName);
	}

	@Override
	public void save() {
		Map<String, Map<String, String>> smap = new HashMap<String, Map<String, String>>();
		for (String k : works.keySet()) {
			IClient s = works.get(k);
			Map<String, String> props = new HashMap<String, String>();
			smap.put(k, props);
			props.put("port", s.getPort());
			props.put("protocol", s.simple());
			for (String name : s.enumProp()) {
				props.put(name, s.getProperty(name));
			}
		}

		String fn = clientconf;
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

	@SuppressWarnings("unchecked")
	@Override
	public void restore() {
		String fn = clientconf;
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
				close(sname);
				Map<String, String> props = map.get(sname);
				if (props == null)
					props = new HashMap<String, String>();
				props.put("status", "init");
				try {
					connect(props.get("host"), props.get("port"),
							props.get("protocol"), props);
				} catch (Exception e) {
					CJSystem.current()
							.environment()
							.logging()
							.error(String.format("连接远程：%s:%s:%s 原因：%s",
									props.get("host"), props.get("port"),
									props.get("protocol"), e.getMessage()));

				}
			}
			System.out.println("恢复成功");
		} catch (Exception e) {
			throw new EcmException(e);
		}
	}

	@Override
	public void clear() {
		File f = new File(clientconf);
		if (f.exists())
			f.delete();
	}

	@Override
	public List<IClient> getAll() {
		List<IClient> clients = new ArrayList<IClient>();
		for (String n : works.keySet()) {
			clients.add(works.get(n));
		}
		return clients;
	}

}
