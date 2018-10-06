package cj.studio.ecm.chip.netsite;

import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cj.studio.ecm.annotation.CjService;
import cj.studio.ecm.annotation.CjServiceRef;
import cj.studio.ecm.graph.IResult;

@CjService(name = "main", isExoteric = true)
public class Main {
	Logger logger = LoggerFactory.getLogger(Main.class);
	@CjServiceRef
	ICommand serversCommand;
	@CjServiceRef
	ICommand clientsCommand;
	@CjServiceRef
	ICommand chipsCommand;
	@CjServiceRef(refByName = "customGraphConnectRequest")
	ICommand gcommand;
	@CjServiceRef(refByName = "netSite")
	INetSite netsite;
	public void init(String netsiteHome) {
		try {
			/*
			 * 正确加载顺序应为：
			 * 1.加载芯片(尝试找回连接，但由于服务器和客户端此时未启动，因此并不能建立任何连接）
			 * 2.加载服务器(连接芯片端子）
			 * 3.加载客户端(连接芯片端子）
			 * 4.加载连接服务（只是启动连接服务，连接此步前已完成加载）
			 * 
			 * 以上为什么芯片和net均需要尝试找回连接？
			 * 因为不只是netsite启动时，运行时比如启动了一个进程，或启动了一个服务，都需要尝试找回
			 */
			chipsCommand.init(netsiteHome);
			serversCommand.init(netsiteHome);
			clientsCommand.init(netsiteHome);
			gcommand.init(netsiteHome);
			netsite.finished();
		} catch (Exception e) {
			e.printStackTrace(System.out);
			logger.error(e.getMessage());
		}
	}

	public void main(String cmd, String[] args) throws ParseException {
		if ("exit".equals(cmd) || "bye".equals(cmd) || "stop".equals(cmd)) {
			chipsCommand.dispose();
			gcommand.dispose();
			serversCommand.dispose();
			clientsCommand.dispose();
			return;
		}

		IResult result = null;
		try {
			result = serversCommand.command(cmd, args);
			if (result.state() != 504)
				return;
			result = clientsCommand.command(cmd, args);
			if (result.state() != 504)
				return;
			result = chipsCommand.command(cmd, args);
			if (result.state() != 504)
				return;
			result = gcommand.command(cmd, args);
			if (result.state() != 504)
				return;
			else {
				throw new ParseException(String.format("不认识的命令。%s", cmd));
			}
		} catch (Exception e) {
			logger.error(errormsg(e));
			String prev = "netsite >";
			System.out.print(prev);
		}
	}

	private String errormsg(Throwable e) {
		StringBuffer sb = new StringBuffer();
		sb.append(e.getMessage() + "\r\n");
		for (StackTraceElement st : e.getStackTrace()) {
			sb.append("\tat " + st.getClassName() + "." + st.getMethodName()
					+ "(" + st.getFileName() + ":" + st.getLineNumber()
					+ ")\r\n");
		}
		e = e.getCause();
		Throwable prev = null;
		while (e != null) {
			prev = e;
			e = e.getCause();
		}
		if (prev != null) {
			sb.append("Caused by :\r\n");
			for (StackTraceElement st : prev.getStackTrace()) {
				sb.append("\tat " + st.getClassName() + "."
						+ st.getMethodName() + "(" + st.getFileName() + ":"
						+ st.getLineNumber() + ")\r\n");
			}
		}
		return sb.toString();
	}
}
