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
import cj.studio.ecm.graph.ICablePin;
import cj.studio.ecm.graph.IResult;
import cj.studio.ecm.net.IServer;

@CjService(name = "serversCommand")
public class ServersCommand extends AbstractCommand implements ICommand {
	Logger logger = LoggerFactory.getLogger(ServersCommand.class);
	@CjServiceRef
	private IServerManager serverManager;

	public ServersCommand() {

	}

	@Override
	protected boolean cognize(String cmd) {
		return "sc".equals(cmd);
	}

	@Override
	public void init(String home) {
		serverManager.init(home);
		
	}

	@Override
	public void dispose() {
		serverManager.save();
		serverManager.dispose();
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
			} else if (line.hasOption("save")) {
				save(line);
			} else if (line.hasOption("d")) {
				setRootSiteProcess(line);
			} else if (line.hasOption("update")) {
				update(line);
			} else if (line.hasOption("clear")) {
				clear(line);
			} else if (line.hasOption("restore")) {
				restore(line);
			} else if (line.hasOption("stop")) {
				stop(cmd, line);
			} else if (line.hasOption("stopall")) {
				stopAll(cmd, line);
			} else if (line.hasOption("start")) {
				start(cmd, line);
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

	private void setRootSiteProcess(CommandLine line) {
		if (!line.hasOption("d")) {
			throw new EcmException("参数d必须");
		}
		serverManager.setRootSiteProcess(line.getOptionValue("d"));
		System.out.println("成功设置");
	}

	private void update(CommandLine line) {
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
		serverManager.update(line.getOptionValue("update"), props);
	}

	private void clear(CommandLine line) {
		serverManager.clearmemo();
	}

	private void restore(CommandLine line) {
		serverManager.restore();
	}

	private void save(CommandLine line) {
		serverManager.save();
	}

	private void start(String cmd, CommandLine line) throws ParseException {
		if (!line.hasOption("name")) {
			if (!line.hasOption("p"))
				throw new ParseException("没有指定端口");
			if (!line.hasOption("t"))
				throw new ParseException("没有指定协议");
		}
		Map<String, String> map = new HashMap<String, String>();
		if (line.hasOption("name"))
			map.put("serverName", line.getOptionValue("name"));
		if (line.hasOption("l"))
			map.put("log", line.getOptionValue("l"));
		IServer s = null;
		if (line.hasOption("P")) {
			Properties list = line.getOptionProperties("P");
			for (Object k : list.keySet()) {
				if ("port".equals(k)) {
					System.out.println("端口不能更新，因此本次更新无效");
					return;
				}
				map.put((String) k, list.getProperty((String) k));
			}
		}
		if (line.hasOption("T")) {
			Properties list = line.getOptionProperties("T");
			for (Object k : list.keySet()) {
				if ("port".equals(k)) {
					System.out.println("端口不能更新，因此本次更新无效");
					return;
				}
				map.put((String) k, list.getProperty((String) k));
			}
		}
		s = serverManager.startServer(line.getOptionValue("p"),
				line.getOptionValue("t"), map);
		if (s != null)
			printServer(s, false);
	}

	private void stopAll(String cmd, CommandLine line) {
		serverManager.stopAll();
	}

	private void stop(String cmd, CommandLine line) throws ParseException {
		serverManager.stopServer(line.getOptionValue("stop"));
	}

	private void printServer(IServer s, boolean isDeploy) {
		System.out.println("--------" + s.buildNetGraph().name());
		System.out.println("\tstatus:" + s.getProperty("status"));
		System.out.println("\tport:" + s.getPort());
		// if (isDeploy)
		// System.out.println("\trole:deployServer");
		// else
		// System.out.println("\trole:workServer");
		System.out.println("\tsimple:" + s.simple());
		System.out.println("\trole:" + s.getProperty("role"));
		System.out.println("\ttype:" + s.toString());
		System.out.println("\tnetGraph:" + s.buildNetGraph().name());
		try {
			System.out.println("\t\tprotocol:"
					+ (s.buildNetGraph().protocolFactory() == null ? "-" : s
							.buildNetGraph().protocolFactory().getProtocol()));
			System.out.println(String.format("\t\tinput:%s type:%s", s
					.buildNetGraph().enumInputPin()[0], s.buildNetGraph()
					.netInput() instanceof ICablePin ? "cable" : "wire"));
			System.out.println(String.format("\t\toutput:%s type:%s", s
					.buildNetGraph().enumOutputPin()[0], s.buildNetGraph()
					.netInput() instanceof ICablePin ? "cable" : "wire"));
		} catch (Exception e) {
			System.out.println("\t\terror:-不合法的server,其netGraph不可用." + e);
		}
		String[] props = s.enumProp();
		System.out.println("\tproperties:");
		for (String k : props) {
			if ("port".equals(k))
				continue;
			if ("role".equals(k))
				continue;
			if ("status".equals(k))
				continue;
			System.out.println("\t\t" + k + "=" + s.getProperty(k));
		}
	}

	private void ls(String cmd, CommandLine line) {
		if (line.hasOption("name")) {
			List<IServer> servers = serverManager.getAll();
			for (IServer s : servers) {
				if (s.buildNetGraph().name().equals(line.getOptionValue("name"))) {
					printServer(s, false);
					break;
				}
			}
			return;
		}
		List<IServer> servers = serverManager.getAll();
		for (IServer s : servers) {
			printServer(s, false);
		}
	}

	@SuppressWarnings("static-access")
	@Override
	protected Options createOptions() {
		Options options = new Options();
		Option h = new Option("p", "port", true, "服务端口号");
		options.addOption(h);
		Option ls = new Option("ls", "list", false, "列出所有服务器");
		options.addOption(ls);
		Option start = new Option(
				"start",
				"start",
				false,
				"启动服务.如果指定参数name，且存在服务名则按服务名重起；\r\n否则尝试启动新服务必须参数是p,t，可选name，\r\n如果新服务与预配置服务相同，则重启，\r\n否则新建服务");
		options.addOption(start);
		Option d = new Option("d", "defaultSitePid", true,
				"为netsite服务器设访问的根站点进程，不需要其它参数。作用：\r\n\t当请求netsite任意服务器时，如果请求地址中不含进程号，则使用该默认进程。");
		options.addOption(d);
		Option save = new Option("save", "save", false, "保存netsite配置快照");
		options.addOption(save);
		Option clear = new Option("clear", "clearmemo", false, "清除netsite配置快照");
		options.addOption(clear);
		Option restore = new Option("restore", "restore", false, "持久化netsite配置");
		options.addOption(restore);
		Option s = new Option("stop", "stop", true, "停止指定的名称的服务器");
		options.addOption(s);
		Option update = new Option("update", "update", true,
				"更新指定名称的服务器，该参数会导致服务重启");
		options.addOption(update);
		Option name = new Option("name", "name", true,
				"为服务器指定名字，应用在启动新建服务时，\r\n在创建时如果省略则以端口号作为名字");
		options.addOption(name);
		Option sa = new Option("stopall", "stopall", false, "停止所有服务器");
		options.addOption(sa);
		Option t = new Option("t", "tranprotocol", true,
				"指定传输协议，支持：\r\nudt,tcp,http,websocket,udp,local,switch");
		options.addOption(t);
		Option l = new Option("l", "log", false, "充许网络日志输出到控制台");
		options.addOption(l);
		Option m = new Option("m", "man", false, "帮助");
		options.addOption(m);

		Option thread = OptionBuilder
				.withArgName("poolPropName=value")
				.hasArgs(2)
				.withValueSeparator()
				.withDescription(
						"设置客户端连接池属性， \r\n工作线程数-Twork=n,池最大大小-Tmax=n,\r\n池空间数-Tidle=n,\r\n池最小数-Tmin=n,\r\n超时时间-Ttimeout=n")
				.create("T");
		options.addOption(thread);
		Option p = OptionBuilder.withArgName("property=value").hasArgs(2)
				.withValueSeparator()
				.withDescription("设置服务器相关属性,格式为：-Pproperty=value").create("P");
		options.addOption(p);
		return options;
	}

	@Override
	protected Parser createParser() {
		return new GnuParser();
	}

}
