package cj.studio.ecm.chip.netsite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

import cj.studio.ecm.EcmException;
import cj.studio.ecm.IAssembly;
import cj.studio.ecm.annotation.CjService;
import cj.studio.ecm.annotation.CjServiceRef;
import cj.studio.ecm.graph.IResult;
import cj.studio.ecm.net.INetboard;
import cj.studio.ecm.net.INetboardEvent;

@CjService(name = INetboard.KEY_NETBOARD)
public class Netboard implements INetboard {
	@CjServiceRef(refByName = "serversCommand")
	ServersCommand server;
	@CjServiceRef(refByName = "clientsCommand")
	ClientsCommand client;
	@CjServiceRef(refByName = "customGraphConnectRequest")
	GraphConnectCommand connect;
	Logger logger = Logger.getLogger(Netboard.class);
	List<INetboardEvent> events;

	Map<String, IAssembly> processes;

	public Netboard() {
		events = new ArrayList<INetboardEvent>();
		processes = new HashMap<String, IAssembly>();
	}

	@Override
	public void dispose() {
		server = null;
		client = null;
		connect = null;
		events.clear();
		processes.clear();
	}


	@Override
	public Object doCommand(String line) {
		String cmd = "";
		String[] lineArr = line.split(" ");
		String argsArr[] = new String[lineArr.length - 1];
		if (lineArr.length > 1)
			System.arraycopy(lineArr, 1, argsArr, 0, lineArr.length - 1);
		cmd = lineArr[0];
		IResult result = null;
		try {
			result = server.command(cmd, argsArr);
			if (result.state() != 504)
				return result;
			result = client.command(cmd, argsArr);
			if (result.state() != 504)
				return result;
			result = connect.command(cmd, argsArr);
			if (result.state() != 504)
				return result;
			else {
				throw new ParseException(
						String.format("netborad不支持该命令。%s", cmd));
			}
		} catch (Exception e) {
			logger.error(e);
			throw new EcmException(e);
		}
	}

	@Override
	public boolean containsEvent(Object e) {
		return events.contains(e);
	}

	public void addEvent(INetboardEvent e) {
		events.add(e);
	}

	public void removeEvent(INetboardEvent e) {
		events.remove(e);
	}

	@Override
	public void doEvent(String cmd, Object... args) {
		switch(cmd){
		case INetboardEvent.EVENT_RUNPROCE:
			processes.put((String)args[0], (IAssembly)args[1]);
			break;
		case INetboardEvent.EVENT_KILLPROCE:
			processes.remove((String)args[0]);
			break;
		}
		for (INetboardEvent e : events) {
			e.event(cmd, args);
		}
	}
}