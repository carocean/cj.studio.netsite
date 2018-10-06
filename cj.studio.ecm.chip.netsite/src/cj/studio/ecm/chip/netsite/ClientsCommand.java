package cj.studio.ecm.chip.netsite;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cj.studio.ecm.EcmException;
import cj.studio.ecm.annotation.CjService;
import cj.studio.ecm.annotation.CjServiceRef;
import cj.studio.ecm.graph.IResult;
import cj.studio.ecm.net.IClient;
import cj.studio.ecm.net.nio.netty.INettyClient;

@CjService(name = "clientsCommand")
public class ClientsCommand extends AbstractCommand implements ICommand {
	Logger logger = LoggerFactory.getLogger(ClientsCommand.class);

	@CjServiceRef
	private IClientManager clientManager;

	@SuppressWarnings("static-access")
	@Override
	protected Options createOptions() {
		Options options = new Options();
		Option connect = new Option("connect", "connect", true,
				"格式：要么指定连接名，要么指定套节字ip:port");
		options.addOption(connect);
		Option ls = new Option("ls", "list", false, "列出所有客户端");
		options.addOption(ls);
		Option is = new Option("is", "is", false, "判断是否存在指定名字的客户端。必选参数-name");
		options.addOption(is);
		Option t = new Option("t", "protocol", true, "指定协议");
		options.addOption(t);
		Option name = new Option("name", "name", true, "在创建新连接时可指定名称");
		options.addOption(name);
		Option close = new Option("close", "close", true, "关闭指定名称的客户端.");
		options.addOption(close);
		Option save = new Option("save", "save", false, "保存netsite配置快照");
		options.addOption(save);
		Option clear = new Option("clear", "clearmemo", false, "清除netsite配置快照");
		options.addOption(clear);
		Option restore = new Option("restore", "restore", false, "持久化netsite配置");
		options.addOption(restore);
		Option l = new Option("l", "log", false, "充许网络日志输出到控制台");
		options.addOption(l);
		Option m = new Option("m", "man", false, "帮助");
		options.addOption(m);

		Option thread = OptionBuilder
				.withArgName("poolPropName=value")
				.hasArgs(2)
				.withValueSeparator()
				.withDescription(
						"设置客户端线程和连接池属性，"
								+ " \r\n-Twork=n工作线程数,默认为2"
								+ "-Tcapacity=n池容量,默认为1"
								+ "\r\n-Tidle=ms池中空闲信道的休眠时间，超时则关闭连接,默认－1为永远等"
								+ "\r\n-TidleCheck=ms空闲信道休眠是否超时的检查时间间隔，默认-1表示不检查，如果是不检查，则信道池不堵塞，每次在现有连接中负载一个。\r\n\t它用于检测idle是否到期"
								+ "\r\n-TidleCheckin=ms信道被netty检查出空闲并检入到信道池的时间，在一个信道空闲后达到此时间，才会被放入信道池中。\r\n\t默认3600毫秒,-1表示永不空闲，因此也永不会被置入信道池"
								+ "\r\n-TactiveTimeout=ms申请新信道时的等待超时时间，默认－1为永远等")
				.create("T");
		options.addOption(thread);
		Option p = OptionBuilder.withArgName("property=value").hasArgs(2)
				.withValueSeparator()
				.withDescription("设置服务器相关属性,格式为：-Pproperty=value").create("P");
		options.addOption(p);
		return options;
	}

	@Override
	public void init(String home) {
		clientManager.init(home);
	}

	@Override
	public void dispose() {
		clientManager.save();
		clientManager.dispose();
	}

	@Override
	protected Parser createParser() {
		return new GnuParser();
	}

	@Override
	protected IResult command(String cmd, CommandLine line)
			throws ParseException {
		Result result = new Result();
		if (line.hasOption("m")) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(cmd, options);
			String prev = "netsite >";
			System.out.print(prev);
			result.state = 200;
			result.message = "ok";
			return result;
		}
		try {
			if (line.hasOption("ls")) {
				ls(cmd, line);
			} else if (line.hasOption("is")) {
				is(cmd, line);
			} else if (line.hasOption("connect")) {
				connect(line);
			} else if (line.hasOption("close")) {
				close(line);
			} else if (line.hasOption("clear")) {
				clear(line);
			} else if (line.hasOption("restore")) {
				restore(line);
			} else if (line.hasOption("save")) {
				save(cmd, line);
			} else {
				throw new ParseException("不能识别的指令");
			}
			String prev = "netsite >";
			System.out.print(prev);
			result.state = 200;
			result.message = "ok";
			return result;
		} catch (Exception e) {
			result.state = 503;
			result.message = e.getMessage();
			logger.error(e.getMessage());
			String prev = "netsite >";
			System.out.print(prev);
			return result;
		}

	}

	private void is(String cmd, CommandLine line) {
		if (!line.hasOption("name"))
			throw new EcmException("必须指定参数name.");
		String name = line.getOptionValue("name");
		if (!clientManager.connected(name)) {
			throw new EcmException("没有包含指定的客户端");
		}
	}

	private void save(String cmd, CommandLine line) {
		clientManager.save();
	}

	private void restore(CommandLine line) {
		clientManager.restore();
	}

	private void clear(CommandLine line) {
		clientManager.clear();
	}

	private void close(CommandLine line) {
		clientManager.close(line.getOptionValue("close"));
	}

	private void connect(CommandLine line) {
		if (!line.hasOption("t"))
			throw new EcmException(
					"必须指定参数t t为协议：udt|tcp|http|local and so on etc.");
		String arg = line.getOptionValue("connect");
		if (!arg.contains(":"))
			throw new EcmException("输入错误。ip:port");
		String host=arg.substring(0,arg.lastIndexOf(":"));
		String port=arg.substring(arg.lastIndexOf(":")+1,arg.length());
		
		Map<String, String> props = new HashMap<String, String>();
		if (line.hasOption("P")) {
			Properties list = line.getOptionProperties("P");
			for (Object k : list.keySet()) {
				if ("port".equals(k)) {
					System.out.println("端口不能更新，因此本次更新无效");
					return;
				}
				props.put((String) k, list.getProperty((String) k));
			}
		}
		if (line.hasOption("T")) {
			Properties list = line.getOptionProperties("T");
			for (Object k : list.keySet()) {
				if ("port".equals(k)) {
					System.out.println("端口不能更新，因此本次更新无效");
					return;
				}
				props.put((String) k, list.getProperty((String) k));
			}
		}
		try {
			if (line.hasOption("name")) {
				props.put("clientName", line.getOptionValue("name"));
			}
			clientManager.connect(host,port, line.getOptionValue("t"),
					props);
		} catch (InterruptedException e) {
			throw new EcmException(e);
		}
	}

	private void ls(String cmd, CommandLine line) {
		if (line.hasOption("name")) {
			List<IClient> clients = clientManager.getAll();
			for (IClient s : clients) {
				if (s.buildNetGraph().name().equals(line.getOptionValue("name"))) {
					printClient(s, false);
					break;
				}
			}
			return;
		}
		List<IClient> clients = clientManager.getAll();
		for (IClient s : clients) {
			printClient(s, false);
		}
	}

	private void printClient(IClient c, boolean b) {
		System.out.println("--------" + c.buildNetGraph().name());
		System.out.println("\tstatus:" + c.getProperty("status"));
		System.out.println("\thost:" + c.getHost());
		System.out.println("\tport:" + c.getPort());
		System.out.println("\tsimple:" + c.simple());
		System.out.println("\ttype:" + c.toString());
		if (c instanceof INettyClient) {
			INettyClient nc = (INettyClient) c;
			System.out.println("\tconnector count:" + nc.connectCount());
			System.out.println("\t\tidle count:" + nc.idleCount());
			System.out.println("\t\tworking count:" + nc.busyCount());
		}
		System.out.println("\tnetGraph:" + c.buildNetGraph().name());
		try {
			System.out.println("\t\tprotocol:"
					+ (c.buildNetGraph().protocolFactory() == null ? "-" : c
							.buildNetGraph().protocolFactory().getProtocol()));
			String[] arr = c.buildNetGraph().enumInputPin();
			System.out.println("\t\tinputs:" + arr.length);
			for (String inname : arr) {
				System.out.println("\t\t\t" + inname);
			}
			String outputs[] = c.buildNetGraph().enumOutputPin();
			if (outputs.length > 0) {
				System.out.println("\t\toutput:"
						+ c.buildNetGraph().enumOutputPin()[0]);
			}
		} catch (Exception e) {
			System.out.println("\t\terror:-不合法的Client,其netGraph不可用." + e);
		}
		String[] props = c.enumProp();
		System.out.println("\tproperties:");
		for (String k : props) {
			if ("port".equals(k))
				continue;
			if ("host".equals(k))
				continue;
			if ("role".equals(k))
				continue;
			if ("status".equals(k))
				continue;
			System.out.println("\t\t" + k + "=" + c.getProperty(k));
		}
	}

	@Override
	protected boolean cognize(String cmd) {
		return "cc".equals(cmd);
	}

	class Result implements IResult {

		private int state;
		private String message;
		private Object value;

		@Override
		public int state() {
			// TODO Auto-generated method stub
			return state;
		}

		@Override
		public String message() {
			// TODO Auto-generated method stub
			return message;
		}

		@Override
		public Object value() {
			// TODO Auto-generated method stub
			return value;
		}

	}
}
