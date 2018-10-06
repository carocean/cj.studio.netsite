package cj.studio.ecm.chip.netsite;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import cj.studio.ecm.CJSystem;
import cj.studio.ecm.EcmException;
import cj.studio.ecm.IServiceAfter;
import cj.studio.ecm.IServiceProvider;
import cj.studio.ecm.IServiceSite;
import cj.studio.ecm.ServiceCollection;
import cj.studio.ecm.annotation.CjService;
import cj.studio.ecm.annotation.CjServiceRef;
import cj.studio.ecm.annotation.CjServiceSite;
import cj.studio.ecm.chip.netsite.http.DefaultProcessor;
import cj.studio.ecm.graph.IBranchKey;
import cj.studio.ecm.graph.IGraph;
import cj.studio.ecm.graph.IGraphHandler;
import cj.studio.ecm.graph.IGraphPlugNetboardEvent;
import cj.studio.ecm.graph.IPin;
import cj.studio.ecm.graph.IPlug;
import cj.studio.ecm.graph.ISink;
import cj.studio.ecm.graph.Pin;
import cj.studio.ecm.logging.ILogging;
import cj.studio.ecm.net.INetboard;
import cj.studio.ecm.net.graph.INetGraph;
import cj.studio.ecm.net.layer.INetLayerGraphCreator;
import cj.studio.ecm.net.layer.ITokenDistributionStrategy;
import cj.studio.ecm.net.layer.NetLayerGraphCreator;
import cj.studio.ecm.net.layer.SessionInfo;
import cj.ultimate.gson2.com.google.gson.Gson;
import cj.ultimate.gson2.com.google.gson.GsonBuilder;
import cj.ultimate.gson2.com.google.gson.reflect.TypeToken;
import cj.ultimate.util.IdGeneratorUtil;
import cj.ultimate.util.StringUtil;

@CjService(name = "graphContainer")
public class GraphContainer implements IGraphContainer, IServiceAfter {
	private Map<String, IGraph> netGraphis;// key(netgraph.name)
	private Map<String, IGraph> chipGraphis;// key(pid#graphName)
	private Map<String, Junction> junctions;// key(pid#graphName)
	@CjServiceRef(refByName = "customGraphConnectRequest")
	private GraphConnectCommand connectCommand;
	@CjServiceSite
	private IServiceSite site;
	private String connectconf;
	private String[] rootSiteProcess;// 默认的根站点进程see:DefaultProcessor.class
	ILogging log;
	private boolean netsiteFinished;
	private List<String> processIds;

	// private INetSiteSessionCenter sessionCenter;
	// private static Logger logger = Logger.getLogger(GraphContainer.class
	// .getName());

	public GraphContainer() {
		netGraphis = new HashMap<String, IGraph>();
		chipGraphis = new HashMap<String, IGraph>();
		junctions = new HashMap<String, Junction>();
		 processIds = new ArrayList<String>();
		rootSiteProcess = new String[1];
		log = CJSystem.current().environment().logging();
	}

	// public void setSessionCenter(INetSiteSessionCenter sessionCenter) {
	// this.sessionCenter = sessionCenter;
	// }

	@Override
	public void setNetSiteRoot(String d) {
		rootSiteProcess[0] = d;
	}

	public void init(String home) {
		netsiteFinished = false;
		this.connectconf = home + File.separator + "connectTable.json";
		load();
	}

	// public Set<String> getProcessIds() {
	// return processIds;
	// }

	public void load() {
		FileReader w = null;
		try {
			File f = new File(connectconf);
			if (!f.exists()) {
				f.createNewFile();
			}
			w = new FileReader(f);
			// Gson gson = new Gson();
			GsonBuilder gb = new GsonBuilder();
			gb.enableComplexMapKeySerialization();
			gb.setPrettyPrinting();
			Gson gson = gb.create();
			Map<String, Junction> map = gson.fromJson(w,
					new TypeToken<HashMap<String, Junction>>() {
					}.getType());
			if (map != null && !map.isEmpty())
				this.junctions.putAll(map);
		} catch (IOException e) {
			throw new EcmException(e);
		} finally {
			try {
				if (w != null)
					w.close();
			} catch (IOException e) {

			}
		}
	}

	public void save() {

		String fn = connectconf;
		Gson gson = new Gson();
		String json = gson.toJson(junctions);
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
		// System.out.println("网络图保存成功");
	}

	@Override
	public void onAfter(IServiceSite site) {
		connectCommand.setContainer(this);
	}

	protected ISink genDispatchSink() {
		return (ISink) site.getService("dispatchSink");
	}

	@Override
	public boolean lsConnectAll() {
		String[] keys = chipGraphis.keySet().toArray(new String[0]);
		for (String key : keys) {
			String[] arr = key.split("#");
			System.out.println(String.format("-----chip graph: %s", arr[0]));
			lsConnectInfo(arr[0], arr[1]);
			System.out.println("-------end-----");
		}
		return false;
	}

	@Override
	public boolean lsConnectInfo(String chipProcessId, String graphName) {
		String theGraphKey = String.format("%s#%s", chipProcessId, graphName);
		if (!chipGraphis.containsKey(theGraphKey)) {
			String ret = String.format("容器中%s不存在该Graph", theGraphKey);
			System.out.println(ret);
			return false;
		}
		IGraph chipG = chipGraphis.get(theGraphKey);
		String[] inArr = chipG.enumInputPin();
		for (String key : inArr) {
			lsConnectInfo(chipProcessId, graphName, key, "input");
		}
		String[] outArr = chipG.enumOutputPin();
		for (String key : outArr) {
			lsConnectInfo(chipProcessId, graphName, key, "output");
		}
		return true;
	}

	@Override
	public boolean isconnect(String chipGraphPID, String graphName,
			String pinName, String method, String netName) {
		String theGraphKey = String.format("%s#%s", chipGraphPID, graphName);

		if (!chipGraphis.containsKey(theGraphKey)) {
			throw new EcmException(String.format("chipGraphis中不存在ChipGraph:%s",
					chipGraphPID));
		}
		IBranchKey key = new RuleBranchKey(netName, chipGraphPID);
		if (!netGraphis.containsKey(netName)) {
			throw new EcmException(String.format("netsite中不存在NetGraph:%s",
					netName));
		}
		IGraph chipG = chipGraphis.get(theGraphKey);
		IGraph netG = netGraphis.get(netName);
		if (netG == null)
			return false;
		switch (method) {
		case "output":
			IPin chipPin = chipG.out(pinName);
			if (chipPin == null) {
				return false;
			}
			IPlug plug = chipPin.getPlug("dispatcher");
			if (plug == null) {
				return false;
			}
			if (plug.containsBranch(key)) {
				// Pin p = plug.branch(netName);
				String ret = String.format("true:%s.%s.%s -> %s", chipGraphPID,
						chipG.name(), pinName, netName);
				System.out.println(ret);
				return true;
			}

			break;
		case "input":

			IPin netOutput = ((INetGraph) netG).netOutput();
			plug = netOutput.getPlug("dispatcher");
			if (plug == null) {
				return false;
			}
			// String branchKey = String.format("%s#%s", chipGraphPID,
			// chipG.acceptProtocol());
			if (plug.containsBranch(key)) {
				IPin p = plug.branch(key);
				if (!pinName.equals(p.name())) {
					return false;
				}
				String ret = String.format("true:%s.%s.%s <- %s", chipGraphPID,
						chipG.name(), p.name(), netName);
				System.out.println(ret);
				return true;
			}

			break;
		}
		System.out.println(String.format("false:%s.%s与%s之间没有建立%s连接。",
				chipGraphPID, chipG.name(), netName,
				"input".equals(method) ? "输入" : "输出"));
		return false;
	}

	public boolean watch(String pid, String n, String g, boolean off) {
		if (!StringUtil.isEmpty(pid)) {
			String theGraphKey = String.format("%s#%s", pid, g);
			if (!chipGraphis.containsKey(theGraphKey)) {
				System.out.println(String.format("容器中%s不存在该进程", theGraphKey));
				return false;
			}
			IGraph chipG = chipGraphis.get(theGraphKey);
			String[] names = chipG.enumOutputPin();
			for (String name : names) {
				IPin out = chipG.out(name);
				if (!out.contains("dispatcher")) {
					continue;
				}
				IPlug plug = out.getPlug("dispatcher");
				if (!off)
					plug.option("iswatch", "true");
				else
					plug.option("iswatch", "false");
			}
			return true;
		}
		if (!StringUtil.isEmpty(n)) {
			if (!netGraphis.containsKey(n))
				throw new EcmException(String.format("要监视的图%s不存在", n));
			;
			INetGraph netG = (INetGraph) netGraphis.get(n);
			IPin out = netG.netOutput();
			if (!out.contains("dispatcher")) {
				return false;
			}
			IPlug plug = out.getPlug("dispatcher");
			if (!off)
				plug.option("iswatch", "true");
			else
				plug.option("iswatch", "false");
		}
		return false;
	}

	@Override
	public boolean lsConnectInfo(String chipGraphPID, String graphName,
			String chipGraphPin, String method) {
		String theGraphKey = String.format("%s#%s", chipGraphPID, graphName);
		if (!chipGraphis.containsKey(theGraphKey)) {
			System.out.println(String.format("容器中%s不存在该进程", theGraphKey));
			return false;
		}
		IGraph chipG = chipGraphis.get(theGraphKey);
		boolean found = false;
		switch (method) {
		case "output":
			if (junctions.containsKey(theGraphKey)) {
				Junction junction = junctions.get(theGraphKey);
				if (junction.containsOutput(chipGraphPin)) {
					List<RuleBranchKey> list = junction
							.getOutputList(chipGraphPin);
					for (RuleBranchKey rbkey : list) {
						if (netGraphis.containsKey(rbkey.keyNetName)) {
							String str = String
									.format("output端子插入net：%s.%s(%s accept:%s).%s flow %s rewrite %s to %s -> net(%s) ",
											chipGraphPID,
											chipG.name(),
											chipG.protocolFactory() == null ? ""
													: chipG.protocolFactory()
															.getProtocol(),
											chipG.acceptProtocol(),
											chipGraphPin, rbkey.flow,
											rbkey.rewrite, rbkey.to,
											rbkey.keyNetName);
							System.out.println(str);
							found = true;
						}
					}
				}
			}
			break;
		// Pin chipPin = chipG.out(chipGraphPin);
		// if (chipPin == null) {
		// System.out.println(String.format("图%s中不存在端子%s", theGraphKey,
		// chipGraphPin));
		// return false;
		// }
		// IPlug plug = chipPin.getPlug("dispatcher");
		// if (plug == null) {
		// ISink sink = genDispatchSink();
		// plug = chipPin.plugLast("dispatcher", sink);
		// }
		// IBranchKey[] bracheKeys = plug.enumBranchKey();
		// for (IBranchKey key : bracheKeys) {
		// RuleBranchKey rbkey = (RuleBranchKey) key;
		// String str = String
		// .format("output端子插入net：%s.%s(%s accept:%s).%s flow %s rewrite %s to %s -> net(%s) ",
		// chipGraphPID, chipG.name(), chipG
		// .protocolFactory() == null ? "" : chipG
		// .protocolFactory().getProtocol(), chipG
		// .acceptProtocol(), chipGraphPin,
		// rbkey.flow, rbkey.rewrite, rbkey.to, key.key());
		// System.out.println(str);
		// }
		// return true;
		case "input":
			if (junctions.containsKey(theGraphKey)) {
				Junction junction = junctions.get(theGraphKey);
				if (junction.containsInput(chipGraphPin)) {
					List<RuleBranchKey> list = junction
							.getInputList(chipGraphPin);
					for (RuleBranchKey rbkey : list) {
						if (netGraphis.containsKey(rbkey.keyNetName)) {
							String str = String
									.format("input端子插入net：%s.%s(%s accept:%s).%s flow %s rewrite %s to %s <- net(%s) ",
											chipGraphPID,
											chipG.name(),
											chipG.protocolFactory() == null ? ""
													: chipG.protocolFactory()
															.getProtocol(),
											chipG.acceptProtocol(),
											chipGraphPin, rbkey.flow,
											rbkey.rewrite, rbkey.to,
											rbkey.keyNetName);
							System.out.println(str);
							found = true;
						}
					}
				}
			}
			break;
		// for (String key : netGraphis.keySet()) {
		// INetGraph ng = (INetGraph) netGraphis.get(key);
		// Pin out = ng.netOutput();
		// plug = out.getPlug("dispatcher");
		// if (plug == null) {
		// continue;
		// }
		// // key = String.format("%s#%s", chipG.processId(),
		// // chipG.acceptProtocol());
		// RuleBranchKey rbkey = new RuleBranchKey(key, chipGraphPID);
		// if (plug.containsBranch(rbkey)) {
		// IBranchKey keys[] = plug.enumBranchKey();
		// IBranchKey found = null;
		// for (IBranchKey k : keys) {
		// if (rbkey.equals(k)) {
		// found = k;
		// break;
		// }
		// }
		// rbkey = (RuleBranchKey) found;
		// String str = String
		// .format("input端子插入net：%s.%s(%s accept:%s).%s flow %s rewrite %s to %s <- net(%s) ",
		// chipGraphPID, chipG.name(), chipG
		// .protocolFactory() == null ? ""
		// : chipG.protocolFactory()
		// .getProtocol(), chipG
		// .acceptProtocol(), chipGraphPin,
		// rbkey.flow, rbkey.rewrite, rbkey.to,
		// rbkey.keyNetName);
		// System.out.println(str);
		// // System.out.println(String.format("%s.%s <- %s",
		// // theGraphKey, chipGraphPin, ng.name()));
		// }
		// }

		// return found;
		}
		return found;
	}

	@Override
	public boolean connect(String chipGraphPID, String graphName,
			String chipGraphPin, String method, String netName, String flow,
			String rewirte, String to) {
		return connect(chipGraphPID, graphName, chipGraphPin, method, netName,
				flow, rewirte, to, true);
	}

	public IGraph[] getChipGraphis() {
		return chipGraphis.values().toArray(new IGraph[0]);
	}

	// chip 连接到net
	private boolean connect(String chipGraphPID, String graphName,
			String chipGraphPin, String method, String netName, String flow,
			String rewrite, String to, boolean issave) {
		String theGraphKey = String.format("%s#%s", chipGraphPID, graphName);
		if (!chipGraphis.containsKey(theGraphKey)) {
			throw new EcmException(String.format("netsite中不存在ChipGraph:%s",
					theGraphKey));
		}
		if (!netGraphis.containsKey(netName)) {
			throw new EcmException(String.format("netsite中不存在NetGraph:%s",
					netName));
		}
		IGraph chipG = chipGraphis.get(theGraphKey);
		IGraph netG = netGraphis.get(netName);

		IGraphHandler handler = (IGraphHandler) chipG.site().getService(
				"$.graph.handler");
		IGraphPlugNetboardEvent gevent = null;
		if (handler != null) {
			Object ret = handler
					.event(IGraphHandler.EVENT_GRAPH_PLUG_NETBOARD_EVENT);
			if (ret != null) {
				gevent = (IGraphPlugNetboardEvent) ret;
			}
		}
		switch (method) {
		case "output":// 向net输出,对接net的输入端子
			IPin chipPin = chipG.out(chipGraphPin);
			if (chipPin == null) {
				if (handler != null) {
					Object raw = handler.event(IGraphHandler.EVENT_DEMAND_PIN,
							chipGraphPin, chipGraphPID, method, netName);
					if (raw != null) {
						chipPin = (Pin) raw;
					}
				}
				if (chipPin == null)
					throw new EcmException(String.format(
							"chipGraph：%s的端子：%s不存在。", chipGraphPID,
							chipGraphPin));
			}
			RuleBranchKey key = new RuleBranchKey(netG.name(), chipGraphPID,
					flow, rewrite, to, netG.acceptProtocol());
			if (gevent == null) {
				// 非编程模式下开始插入
				IPlug plug = chipPin.getPlug("dispatcher");
				if (plug == null) {
					ISink sink = genDispatchSink();
					plug = chipPin.plugLast("dispatcher", sink);
				}
				if (plug.containsBranch(key)) {
					IPin p = plug.branch(key);
					throw new EcmException(String.format(
							"chip(%s) input(%s) 已插到 net(%s)上", chipGraphPID,
							p.name(), netName));
				}
				IPin netInput = ((INetGraph) netG).netInput();
				plug.plugBranch(key, netInput);
				// 插入结束
			} else {
				// 编程模式下
				IPin netinput = ((INetGraph) netG).netInput();
				gevent.plugNetboard(netName, netinput, "input", flow, rewrite,
						to);
			}
			String name = String.format("%s#%s", chipGraphPID, chipG.name());
			Junction item = junctions.get(name);
			if (item == null) {
				item = new Junction();
				junctions.put(name, item);
			}
			item.setGraphProtocol(chipG.protocolFactory() == null ? "" : chipG
					.protocolFactory().getProtocol());
			item.setChipGraphname(chipG.name());
			item.appendOutput(chipGraphPin, key);
			item.setChipGraphName(chipG.name());
			if (issave) {
				save();
			}
			String str = String
					.format("output端子插入net：%s.%s(%s accept:%s).%s flow %s rewrite %s to %s -> net(%s) ",
							chipGraphPID, item.getChipGraphname(),
							item.getGraphProtocol(), chipG.acceptProtocol(),
							chipGraphPin, key.flow, key.rewrite, key.to,
							netName);
			System.out.println(str);
			if (handler != null) {
				try {
					IGraph[] col = chipGraphis.values().toArray(new IGraph[0]);
					IGraph[] col2 = netGraphis.values().toArray(new IGraph[0]);
					handler.event(IGraphHandler.EVENT_PLUG_OUTPUT_NET,
							chipGraphPin, chipGraphPID, chipG, netG, col, col2);
				} catch (Exception e) {
					log.error(getClass(), e);
				}
			}
			return true;
		case "input":// 对接net的输出端子

			IPin netOutput = ((INetGraph) netG).netOutput();
			key = new RuleBranchKey(netName, chipGraphPID, flow, rewrite, to,
					chipG.acceptProtocol());
			if (gevent == null) {
				// 开始插入
				IPlug plug = netOutput.getPlug("dispatcher");
				if (plug == null) {
					ISink sink = genDispatchSink();
					plug = netOutput.plugLast("dispatcher", sink);
				}

				if (plug.containsBranch(key)) {
					IPin p = plug.branch(key);
					throw new EcmException(String.format(
							"chip(%s) input(%s) 已插到到 net(%s)的output端子上，分支是：%s",
							chipGraphPID, p.name(), netName, chipGraphPID));
				}
				chipPin = chipG.in(chipGraphPin);
				if (chipPin == null) {
					if (handler != null) {
						Object raw = handler.event(
								IGraphHandler.EVENT_DEMAND_PIN, chipGraphPin,
								chipGraphPID, method, netName);
						if (raw != null) {
							chipPin = (Pin) raw;
						}
					}
					if (chipPin == null)
						throw new EcmException(String.format(
								"chipGraph：%s的端子：%s不存在。", chipGraphPID,
								chipGraphPin));
				}

				plug.plugBranch(key, chipPin);
				// 结束插入
			} else {
				// 编程模式下
				gevent.plugNetboard(netName, netOutput, "output", flow,
						rewrite, to);
			}
			name = String.format("%s#%s", chipGraphPID, chipG.name());
			item = junctions.get(name);
			if (item == null) {
				item = new Junction();
				junctions.put(name, item);
			}
			item.setGraphProtocol(chipG.protocolFactory() == null ? "" : chipG
					.protocolFactory().getProtocol());
			item.setChipGraphname(chipG.name());
			item.appendInput(chipGraphPin, key);
			item.setChipGraphName(chipG.name());
			if (issave) {
				save();
			}
			str = String
					.format("input端子插入net：%s.%s(%s accept:%s).%s flow %s rewrite %s to %s <- net(%s) ",
							chipGraphPID, item.getChipGraphname(),
							item.getGraphProtocol(), chipG.acceptProtocol(),
							chipGraphPin, key.flow, key.rewrite, key.to,
							netName);
			System.out.println(str);
			if (handler != null) {
				try {
					IGraph[] col = chipGraphis.values().toArray(new IGraph[0]);
					IGraph[] col2 = netGraphis.values().toArray(new IGraph[0]);
					handler.event(IGraphHandler.EVENT_PLUG_INPUT_NET,
							chipGraphPin, chipGraphPID, chipG, netG, col, col2);
				} catch (Exception e) {
					log.error(getClass(), e);
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean disconnect(String chipGraphPID, String protocol,
			String chipGraphPin, String method, String netName) {
		return disconnect(chipGraphPID, protocol, chipGraphPin, method,
				netName, true);
	}

	private boolean disconnect(String chipGraphPID, String graphName,
			String chipGraphPin, String method, String netName, boolean issave) {
		String theGraphKey = String.format("%s#%s", chipGraphPID, graphName);
		RuleBranchKey key = new RuleBranchKey(netName, chipGraphPID);
		switch (method) {
		case "output":
			Junction item = junctions.get(theGraphKey);
			item.removeOutputConnect(chipGraphPin, key);
			System.out.println(String.format(
					"已清理注册表中连接点：output：%s.%s(%s).%s <- net(%s)", chipGraphPID,
					graphName, item.getGraphProtocol(), chipGraphPin, netName));
			break;
		case "input":
			item = junctions.get(theGraphKey);
			item.removeInputConnect(chipGraphPin, key);
			System.out.println(String.format(
					"已清理注册表中连接点：input：%s.%s(%s).%s <- net(%s)", chipGraphPID,
					graphName, item.getGraphProtocol(), chipGraphPin, netName));
			break;
		}
		if (issave)
			save();
		// 以下解构
		if (!chipGraphis.containsKey(theGraphKey)) {
			return false;
		}
		if (!netGraphis.containsKey(netName)) {
			return false;
		}
		IGraph chipG = chipGraphis.get(theGraphKey);
		IGraph netG = netGraphis.get(netName);

		IGraphHandler handler = (IGraphHandler) chipG.site().getService(
				"$.graph.handler");

		switch (method) {
		case "output":
			IPin chipPin = chipG.out(chipGraphPin);
			if (chipPin == null)
				throw new EcmException(String.format("chipGraph：%s的端子：%s不存在。",
						chipGraphPID, chipGraphPin));
			IPlug plug = chipPin.getPlug("dispatcher");
			if (plug == null)
				return false;
			if (plug.branchCount() < 1) {
				chipPin.unPlug("dispatcher");
				return false;
			} else {

				plug.removeBranch(key);
			}
			System.out.println(String.format(
					"output端子已从net拔出：%s.%s(%s).%s <- net(%s)", chipGraphPID,
					graphName, chipG.protocolFactory().getProtocol(),
					chipGraphPin, netName));
			if (handler != null) {
				try {
					IGraph[] col = chipGraphis.values().toArray(new IGraph[0]);
					handler.event(IGraphHandler.EVENT_UNPLUG_OUTPUT_NET,
							chipGraphPin, chipGraphPID, chipG, netG, col);
				} catch (Exception e) {
					log.error(getClass(), e);
				}
			}
			return true;
		case "input":
			IPin netOutput = ((INetGraph) netG).netOutput();
			plug = netOutput.getPlug("dispatcher");
			if (plug == null) {
				return false;
			}
			// String branchKey = String.format("%s#%s", chipGraphPID,
			// chipG.acceptProtocol());
			if (plug.containsBranch(key)) {
				plug.removeBranch(key);
				System.out.println(String.format(
						"input端子已从net拔出：%s.%s(%s).%s <- net(%s)", chipGraphPID,
						graphName, chipG.protocolFactory().getProtocol(),
						chipGraphPin, netName));
				if (handler != null) {
					try {
						IGraph[] col = chipGraphis.values().toArray(
								new IGraph[0]);
						handler.event(IGraphHandler.EVENT_UNPLUG_OUTPUT_NET,
								chipGraphPin, chipGraphPID, chipG, netG, col);
					} catch (Exception e) {
						log.error(getClass(), e);
					}
				}
				return true;
			}
			if (plug.branchCount() < 1) {
				netOutput.unPlug("dispatcher");
			}
			break;
		}
		return false;
	}

	@Override
	public void dispose() {
		String[] keys = netGraphis.keySet().toArray(new String[0]);
		StringBuffer sb = new StringBuffer();
		for (String key : keys) {
			removeNetGraph(key, sb);
		}
		keys = chipGraphis.keySet().toArray(new String[0]);
		for (String key : keys) {
			removeChipGraph(key, sb);
		}
		netGraphis.clear();
		chipGraphis.clear();
		 processIds.clear();
		// sessionCenter.dispose();
	}

	@Override
	public void startedNet(INetGraph ng, StringBuffer sb) {
		String netName = ng.name();
		IGraphHandler handler = null;
		String[] keys = junctions.keySet().toArray(new String[0]);
		for (String key : keys) {
			Junction item = junctions.get(key);
			String inputs[] = item.enumInput();
			for (String in : inputs) {
				List<RuleBranchKey> list = item.getInputList(in);
				if (list == null)
					continue;
				for (RuleBranchKey rk : list) {
					if (netName.equals(rk.keyNetName)) {
						IGraph g = chipGraphis.get(key);
						if(g==null){
							chipGraphis.remove(key);
							continue;
						}
						handler = (IGraphHandler) g.site().getService(
								"$.graph.handler");
						if (handler != null)
							break;

					}
				}
				if(handler!=null){
					break;
				}

			}
			if(handler!=null){
				break;
			}
			String outputs[] = item.enumOutput();
			for (String out : outputs) {
				List<RuleBranchKey> list = item.getOutputList(out);
				if (list == null)
					continue;
				for (RuleBranchKey rk : list) {
					if (netName.equals(rk.keyNetName)&&chipGraphis.containsKey(key)) {
						IGraph g = chipGraphis.get(key);
						 handler = (IGraphHandler) g.site()
								.getService("$.graph.handler");
						if (handler != null)
							break;
					}
				}
				if(handler!=null){
					break;
				}
			}
			if(handler!=null){
				break;
			}
		}
		if (handler != null) {
			try {
				handler.event(IGraphHandler.EVENT_NET_STARTED, netName);
			} catch (Exception e) {
				log.error(getClass(), e);
			}
		}
	}

	// 处理服务器站的默认流
	private void attachDefaultProcessorSink(INetGraph ng) {
		IGraph defaultNetsiteGraph = (IGraph) site
				.getService("netSiteDefaultHttpGraph");
		if (!defaultNetsiteGraph.isInit()) {
			defaultNetsiteGraph.initGraph();
			NetLayerGraphCreator nc = (NetLayerGraphCreator) defaultNetsiteGraph
					.creator();
			nc.getSessionManager().setCjTokenDistributionStrategy(
					new TokenDistributionStrategy());
		}
		IPin out = ng.netOutput();
		if (out == null)
			return;
		DefaultProcessor processor = (DefaultProcessor) site
				.getService("defaultProcessor");
		processor.setProcessIds(processIds);
		processor.setNetSiteRoot(rootSiteProcess);
		out.plugFirst("defaultProcessor", processor);
	}

	private void checkChip(String key, IGraph g) {
		if (chipGraphis.containsKey(key)) {
			throw new EcmException(String.format("进程存在相同Graph,(%s)。", key));
		}
		// 检查容器中是否存在了相同acceptprotocol的graph
		// String[] arr = chipGraphis.keySet().toArray(new String[0]);
		// for (String k : arr) {
		// IGraph e = chipGraphis.get(k);
		// String ep = e.protocolFactory() == null ? "" : e.protocolFactory()
		// .getProtocol();
		// String gp = g.protocolFactory() == null ? "" : g.protocolFactory()
		// .getProtocol();
		// if (ep.equals(gp) || e.acceptProtocol().equals(g.acceptProtocol())) {
		// throw new EcmException(String.format(
		// "要添加的graph(%s %s %s)与现有容器中的graph(%s %s %s)协议或可接受协议相同。",
		// g.name(), gp, g.acceptProtocol(), e.name(), ep,
		// e.acceptProtocol()));
		// }
		// }
	}

	@Override
	public void runNetsiteFinished() {
		netsiteFinished = true;
	}

	@Override
	public void addChipGraph(String key, IGraph g, StringBuffer sb) {
		checkChip(key, g);
		IServiceProvider sp = new NetsiteServiceProvider();
		g.parent(sp);

		IGraphHandler handler = (IGraphHandler) g.site().getService(
				"$.graph.handler");
		chipGraphis.put(key, g);
		String pid = key.substring(0, key.indexOf("#"));
		 processIds.add(pid);

//		g.options("processid-in-netsite", pid);// 当前进程号，已放到ChipManager中

		sb.append(String.format("chipGraph(%s)入站，线路:%s.\r\n", g.name(), pid));

		if (g.creator() instanceof INetLayerGraphCreator) {
			// attachNetLayer(pid, g);
			INetLayerGraphCreator c = (INetLayerGraphCreator) g.creator();
			c.getSessionManager().setCjTokenDistributionStrategy(
					new TokenDistributionStrategy());
		}
		if (netsiteFinished) {
			retrieveChipConnect(pid, g);
		}
		if (handler != null) {
			IGraph[] arr = this.chipGraphis.values().toArray(new IGraph[0]);
			handler.event(IGraphHandler.EVENT_ENTERING_NETSITE, pid, g, arr);
		}
	}

	@Override
	public void retrieveClientNetConnectors(INetGraph ng) {
		if(!ng.isInit()){
			ng.initGraph();
		}
		attachDefaultProcessorSink(ng);
		String netName = ng.name();
		if (netGraphis.containsKey(netName)) {
			throw new EcmException("存在同名net。" + netName);
		}
		netGraphis.put(netName, ng);
		// sb.append(String.format("添加net graph(%s).\r\n", netName));
		System.out
				.println(String
						.format("\r\n**********************开始加载net:%s的连接***********************",
								netName));
		retrieveNetConnectors(netName);
		System.out
				.println("**************************连接加载完成***************************\r\n");
	}

	@Override
	public void retrieveServerNetConnectors(INetGraph ng) {
		if(!ng.isInit()){
			ng.initGraph();
		}
		attachDefaultProcessorSink(ng);
		String netName = ng.name();
		if (netGraphis.containsKey(netName)) {
			throw new EcmException("存在同名net。" + netName);
		}
		netGraphis.put(netName, ng);
		// sb.append(String.format("添加net graph(%s).\r\n", netName));
		System.out
				.println(String
						.format("\r\n**********************开始加载net:%s的连接***********************",
								netName));
		retrieveNetConnectors(netName);
		System.out
				.println("**************************连接加载完成***************************\r\n");

	}

	private void retrieveNetConnectors(String netName) {
		String[] keys = junctions.keySet().toArray(new String[0]);
		for (String key : keys) {
			Junction item = junctions.get(key);
			String inputs[] = item.enumInput();
			for (String in : inputs) {
				List<RuleBranchKey> list = item.getInputList(in);
				if (list == null)
					continue;
				for (RuleBranchKey rk : list) {
					if (netName.equals(rk.keyNetName)) {
						String[] kv = key.split("#");
						try {
							connect(kv[0], kv[1], in, "input", netName,
									rk.flow, rk.rewrite, rk.to, false);
						} catch (Exception e) {
							// CJSystem.current().environment().logging().error(e.getMessage());
							log.error(getClass(), e.getMessage());
						}
					}
				}
			}
			String outputs[] = item.enumOutput();
			for (String out : outputs) {
				List<RuleBranchKey> list = item.getOutputList(out);
				if (list == null)
					continue;
				for (RuleBranchKey rk : list) {
					if (netName.equals(rk.keyNetName)) {
						String[] kv = key.split("#");
						try {
							connect(kv[0], kv[1], out, "output", netName,
									rk.flow, rk.rewrite, rk.to, false);
						} catch (Exception e) {
							// CJSystem.current().environment().logging().error(e.getMessage());
							log.error(getClass(), e.getMessage());
						}
					}
				}
			}
		}
	}

	// 找回芯片图的连接
	private void retrieveChipConnect(String pid, IGraph g) {
		System.out
				.println(String
						.format("\r\n**********************开始加载芯片%s与net的连接***********************",
								pid));
		String name = String.format("%s#%s", pid, g.name());
		Junction item = this.junctions.get(name);
		if (item == null) {
			System.out
					.println("**************************未发现有可加载的连接***************************");
			return;
		}
		Iterator<String> itinput = item.itInput();
		while (itinput.hasNext()) {
			String inpinName = itinput.next();
			List<RuleBranchKey> branchKeys = item.getInputList(inpinName);
			if (branchKeys == null || branchKeys.isEmpty())
				continue;
			for (IBranchKey key : branchKeys) {
				RuleBranchKey r = (RuleBranchKey) key;
				try {
					connect(pid, item.getChipGraphname(), inpinName, "input",
							r.keyNetName, r.flow, r.rewrite, r.to, false);
				} catch (Exception e) {
					// CJSystem.current().environment().logging().error(e.getMessage());
					log.error(getClass(), e.getMessage());
				}
			}
		}
		Iterator<String> itoutput = item.itoutput();
		while (itoutput.hasNext()) {
			String outpinName = itoutput.next();
			List<RuleBranchKey> netNames = item.getOutputList(outpinName);
			if (netNames == null || netNames.isEmpty())
				continue;
			for (IBranchKey key : netNames) {
				RuleBranchKey r = (RuleBranchKey) key;
				try {
					connect(pid, item.getChipGraphname(), outpinName, "output",
							r.keyNetName, r.flow, r.rewrite, r.to, false);
				} catch (Exception e) {
					log.error(getClass(), e.getMessage());
				}
			}
		}
		System.out
				.println("**************************连接加载完成***************************\r\n");
	}

	@Override
	public void removeNetGraph(String name, StringBuffer sb) {
		INetGraph g = (INetGraph) netGraphis.get(name);
		if (g == null)
			return;
		sb.append(String.format("以下开始解除Chip Graph与net(%s)的所有连接", name));
		IPin out = g.netOutput();
		if (out != null) {
			IPlug plug = out.getPlug("dispatchSink");
			if (plug != null) {
				for (String key : plug.enumBranch()) {
					removeChipGraph(key, sb);
				}
			}
			out.dispose();
		}

		netGraphis.remove(name);
	}

	@Override
	public void removeChipGraph(String key, StringBuffer sb) {
		IGraph g = chipGraphis.get(key);
		if (g == null)
			return;
		String[] keyarr = key.split("#");

		String pid = keyarr[0];
		 processIds.remove(pid);

		IGraphHandler handler = (IGraphHandler) g.site().getService(
				"$.graph.handler");
		if (handler != null) {
			try {
				handler.event(IGraphHandler.EVENT_LEAVED_NETSITE, pid, g);
			} catch (Exception e) {
				CJSystem.current().environment().logging()
						.error(e.getMessage());
			}
		}
		for (String outName : g.enumOutputPin()) {
			IPin out = g.out(outName);
			if (out != null) {
				try {
					for (String nk : netGraphis.keySet()) {
						disconnect(keyarr[0], keyarr[1], out.name(), "output",
								nk, false);
					}
					out.dispose();
				} catch (Exception e) {
					sb.append(String.format("\t%s", e.getMessage()));
				}
			}
		}
		for (String inName : g.enumInputPin()) {
			IPin in = g.in(inName);
			if (in != null) {
				try {
					for (String bkey : netGraphis.keySet()) {
						disconnect(keyarr[0], keyarr[1], in.name(), "input",
								bkey, false);
					}
				} catch (Exception e) {
					sb.append(String.format("\t%s", e.getMessage()));
				}
			}
		}
		// if (g.creator() instanceof INetLayerGraphCreator) {
		// unattachNetLayer(key, g);
		// }
		chipGraphis.remove(key);

		g.dispose();
		sb.append(String.format("已解除chip graph与net的所有连接"));
	}

	class TokenDistributionStrategy implements ITokenDistributionStrategy {
		Map<String, SessionInfo> map;// key#sessionId

		public TokenDistributionStrategy() {
			map = new HashMap<String, SessionInfo>();
		}

		protected String genCjToken(SessionInfo info) {
//			String cjtoken = IdGenerator.newInstance().asLongText();
			String cjtoken=IdGeneratorUtil.generateSessionId();
			while (map.containsKey(cjtoken)) {
				return genCjToken(info);
			}
			map.put(cjtoken, info);
			return cjtoken;
		}

		@Override
		public String genToken(SessionInfo info) {
			// TODO Auto-generated method stub
			return genCjToken(info);
		}
	}

	class NetsiteServiceProvider implements IServiceProvider {

		@Override
		public <T> ServiceCollection<T> getServices(Class<T> serviceClazz) {
			return connectCommand.getServices(serviceClazz);
		}

		@Override
		public Object getService(String serviceId) {
			if (serviceId.startsWith("$.graph.atom.netboardAtom")) {
				return new NetboardAtom();
			}
			if (serviceId.startsWith(INetboard.KEY_NETBOARD)) {
				return site.getService(INetboard.KEY_NETBOARD);
			}
			return connectCommand.getService(serviceId);
		}

	}

}