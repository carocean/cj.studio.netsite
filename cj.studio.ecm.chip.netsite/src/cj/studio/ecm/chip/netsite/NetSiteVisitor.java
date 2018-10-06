package cj.studio.ecm.chip.netsite;

import java.util.HashMap;
import java.util.Map;

import cj.studio.ecm.annotation.CjService;
import cj.studio.ecm.annotation.CjServiceRef;
import cj.studio.ecm.frame.Circuit;
import cj.studio.ecm.frame.Frame;
import cj.studio.ecm.graph.CircuitException;
import cj.studio.ecm.graph.IPlug;
import cj.studio.ecm.graph.ISink;
import cj.ultimate.util.StringUtil;

/**
 * 为dispatchSink实现访问netsite的能力
 * 
 * <pre>
 * 主要功能是：
 * 1.为graph的流提供自动管理client net的能力
 * 2.为graph的流提供连接管理的功能
 * </pre>
 * 
 * @author carocean
 *
 */
@CjService(name = "netSiteVisitor")
public class NetSiteVisitor implements ISink {
	@CjServiceRef(refByName = "graphContainer")
	private IGraphContainer container;
	@CjServiceRef(refByName = "clientManager")
	private IClientManager clientManager;

	@Override
	public void flow(Frame frame,Circuit circuit, IPlug plug) throws CircuitException {
		if (!frame.command().contains(".")) {
			throw new CircuitException("503",
					"命令错误，主指令与子指令应以.号分开，如：cc.connect /myClientUdt/ netsite/1.0");
		}
		String[] cmd = frame.command().split(".");
		switch (cmd[0]) {
		case "cc":
			doCc(cmd[1], frame, circuit, plug);
			break;
		case "gc":
			doGc(cmd[1], frame, circuit, plug);
			break;
		default:
			throw new CircuitException("503",
					String.format("不支持的指令:%s", cmd[0]));
		}

	}

	private void doGc(String cmd, Frame frame, Circuit circuit, IPlug plug)
			throws CircuitException {
		switch (cmd) {
		case "plug":
			doGc_plug(frame, circuit, plug);
			break;
		case "unplug":
			doGc_unplug(frame, circuit, plug);
			break;
		case "is":
			doGc_is(frame, circuit, plug);
			break;
		default:
			throw new CircuitException("503", String.format("不支持的指令:%s", cmd));
		}
	}

	private void doGc_is(Frame frame, Circuit circuit, IPlug plug)
			throws CircuitException {
		String chipProcessId = plug.site().getProperty("$.graph.processId");
		String graphName = plug.site().getProperty("$.graph.name");
		String method = "output";
		String chipGraphPin = frame.head("output");
		if (StringUtil.isEmpty(chipGraphPin)) {
			throw new CircuitException("404", "未指定Head：output");
		}
		String pinName = frame.head("pin-name");
		if (StringUtil.isEmpty(pinName)) {
			throw new CircuitException("404", "未指定Head：pin-name");
		}
		String netName = frame.rootName();
		if (StringUtil.isEmpty(netName)) {
			throw new CircuitException("404", "请求地址url的root为空，它用于表示要插入的netName");
		}
		try {
			container.isconnect(chipProcessId, graphName,pinName, method, netName);
		} catch (Exception e) {
			throw new CircuitException("503", e);
		}
	}

	private void doGc_unplug(Frame frame, Circuit circuit, IPlug plug)
			throws CircuitException {
		String chipGraphPID = plug.site().getProperty("$.graph.processId");
		String graphName = plug.site().getProperty("$.graph.name");
		String method = "output";
		String chipGraphPin = frame.head("output");
		if (StringUtil.isEmpty(chipGraphPin)) {
			throw new CircuitException("404", "未指定Head：output");
		}
		String netName = frame.rootName();
		if (StringUtil.isEmpty(netName)) {
			throw new CircuitException("404", "请求地址url的root为空，它用于表示要插入的netName");
		}
		try {
			container.disconnect(chipGraphPID, graphName, chipGraphPin, method,
					netName);
		} catch (Exception e) {
			throw new CircuitException("503", e);
		}
	}

	private void doGc_plug(Frame frame, Circuit circuit, IPlug plug)
			throws CircuitException {
		String chipGraphPID = plug.site().getProperty("$.graph.processId");
		String graphName = plug.site().getProperty("$.graph.name");
		String method = "output";
		String chipGraphPin = frame.head("output");
		if (StringUtil.isEmpty(chipGraphPin)) {
			throw new CircuitException("404", "未指定Head：output");
		}
		String netName = frame.rootName();
		if (StringUtil.isEmpty(netName)) {
			throw new CircuitException("404", "请求地址url的root为空，它用于表示要插入的netName");
		}
		String flow = frame.parameter("flow");
		String rewrite = frame.parameter("rewirte");
		String to = frame.parameter("to");
		try {
			container.connect(chipGraphPID, graphName, chipGraphPin, method,
					netName, flow, rewrite, to);
		} catch (Exception e) {
			throw new CircuitException("503", e);
		}
	}

	private void doCc(String cmd, Frame frame, Circuit circuit, IPlug plug)
			throws CircuitException {
		switch (cmd) {
		case "connect":
			doCc_connect(frame, circuit, plug);
			break;
		case "close":
			doCc_close(frame, circuit, plug);
			break;
		case "is":
			doCc_is(frame, circuit, plug);
			break;
		default:
			throw new CircuitException("503", String.format("不支持的指令:%s", cmd));
		}
	}

	private void doCc_is(Frame frame, Circuit circuit, IPlug plug) throws CircuitException {
		String clientName = frame.rootName();
		if (StringUtil.isEmpty(clientName)) {
			throw new CircuitException("404", "未指定参数：clientName");
		}
		boolean connected=clientManager.connected(clientName);
		circuit.status(connected?"200":"404");
		circuit.message(connected?"ok":"不存在.");
	}

	private void doCc_close(Frame frame, Circuit circuit, IPlug plug)
			throws CircuitException {
		String clientName = frame.rootName();
		if (StringUtil.isEmpty(clientName)) {
			throw new CircuitException("404", "未指定参数：clientName");
		}
		try {
			clientManager.close(clientName);
		} catch (Exception e) {
			throw new CircuitException("503", e);
		}
	}

	private void doCc_connect(Frame frame, Circuit circuit, IPlug plug)
			throws CircuitException {
		String ip = frame.head("ip");
		if (StringUtil.isEmpty(ip)) {
			throw new CircuitException("404", "未指定头：ip");
		}
		String port = frame.head("port");
		if (StringUtil.isEmpty(port)) {
			throw new CircuitException("404", "未指定头：port");
		}
		String protocol = frame.head("t");
		if (StringUtil.isEmpty(protocol)) {
			throw new CircuitException("404", "未指定头：t");
		}
		String clientName = frame.rootName();
		if (StringUtil.isEmpty(clientName)) {
			throw new CircuitException("404", "未指定参数：clientName");
		}
		Map<String, String> props = new HashMap<String, String>();
		props.put("clientName", clientName);
		String[] params = frame.enumParameterName();
		for (String k : params) {
			props.put(k, frame.parameter(k));
		}

		try {
			clientManager.connect(ip, port, protocol, props);
		} catch (Exception e) {
			throw new CircuitException("503", e);
		}
	}

}
