package cj.studio.ecm.chip.netsite;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cj.studio.ecm.frame.Circuit;
import cj.studio.ecm.frame.Frame;
import cj.studio.ecm.graph.CircuitException;
import cj.studio.ecm.graph.IPlug;
import cj.studio.ecm.net.INetboardAtom;

class NetboardAtom implements INetboardAtom {
	IPlug plug;
	Logger logger = LoggerFactory.getLogger(NetboardAtom.class);

	@Override
	public void bind(IPlug plug) {
		this.plug = plug;
	}

	@Override
	public boolean gcIs(String outputPin, String netName)
			throws CircuitException {
		Frame frame = new Frame(String.format("gc.is /%s netsite/1.0", netName));
		frame.head("output", outputPin);
		Circuit circuit = new Circuit("netsite/1.0 200 ok");
		try {
			plug.flow(frame, circuit);
		} catch (Exception e) {
			CircuitException.print(e, logger);
		}
		return "200".equals(circuit.status());
	}

	@Override
	public void gcPlug(String outputPin, String netName, String flow,
			String rewrite, String to) throws CircuitException {
		Frame frame = new Frame(String.format("gc.plug /%s netsite/1.0",
				netName));
		frame.head("output", outputPin);
		frame.parameter("flow", flow);
		frame.parameter("rewrite", rewrite);
		frame.parameter("to", to);
		Circuit circuit = new Circuit("netsite/1.0 200 ok");
		plug.flow(frame, circuit);
	}

	@Override
	public void gcUnplug(String outputPin, String netName) throws CircuitException {
		Frame frame = new Frame(String.format("gc.unplug /%s netsite/1.0",
				netName));
		frame.head("output", outputPin);
		Circuit circuit = new Circuit("netsite/1.0 200 ok");
		plug.flow(frame, circuit);
	}

	@Override
	public boolean ccIs(String clientName) {
		Frame frame = new Frame(String.format("cc.is /%s netsite/1.0", clientName));
		Circuit circuit = new Circuit("netsite/1.0 200 ok");
		try {
			plug.flow(frame, circuit);
		} catch (Exception e) {
			CircuitException.print(e, logger);
		}
		return "200".equals(circuit.status());
	}

	@Override
	public void ccClose(String clientName) throws CircuitException {
		Frame frame = new Frame(String.format("cc.close /%s netsite/1.0",
				clientName));
		Circuit circuit = new Circuit("netsite/1.0 200 ok");
		plug.flow(frame, circuit);
	}

	@Override
	public void ccConnect(String ip, String port, String protocol,
			String clientName, Map<String, String> props) throws CircuitException {
		Frame frame = new Frame(String.format("cc.connect /%s netsite/1.0",
				clientName));
		frame.head("ip", ip);
		frame.head("port", port);
		frame.head("t", protocol);
		for(String key:props.keySet()){
			frame.parameter(key,props.get(key));
		}
		Circuit circuit = new Circuit("netsite/1.0 200 ok");
		plug.flow(frame, circuit);
	}

}