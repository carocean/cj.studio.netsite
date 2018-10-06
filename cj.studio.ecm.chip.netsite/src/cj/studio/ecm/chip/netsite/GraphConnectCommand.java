package cj.studio.ecm.chip.netsite;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cj.studio.ecm.EcmException;
import cj.studio.ecm.IServiceAfter;
import cj.studio.ecm.IServiceProvider;
import cj.studio.ecm.IServiceSite;
import cj.studio.ecm.ServiceCollection;
import cj.studio.ecm.annotation.CjService;
import cj.studio.ecm.graph.IResult;
import cj.studio.ecm.util.IConsole;
import cj.ultimate.util.StringUtil;

@CjService(name = "customGraphConnectRequest")
public class GraphConnectCommand extends AbstractCommand implements
		IServiceProvider, IServiceAfter {
	public class NetSiteConsole implements IConsole {
		String color = "\033[0;30m";

		@Override
		public String color() {
			return color;
		}

		@Override
		public void color(String mode) {
			System.out.print(mode);
		}

		@Override
		public void colorBlack() {
			System.out.print("\033[0;30m");

		}

		@Override
		public void println(String line) {
			System.out.println(line);

		}

		@Override
		public String waitInput(String tips) {
			System.out.print(tips);
			InputStreamReader in = new InputStreamReader(System.in);
			BufferedReader read = new BufferedReader(in);
			try {
				String input = read.readLine();
				return input;
			} catch (IOException e) {
				throw new EcmException(e);
			}
		}
	}

	private IGraphContainer container;
	private ClientsCommand clientsCommand;
	Logger logger = LoggerFactory.getLogger(GraphConnectCommand.class);

	public GraphConnectCommand() {
	}

	@Override
	public void onAfter(IServiceSite site) {
		clientsCommand = (ClientsCommand) site.getService("clientsCommand");
	}

	public void setContainer(IGraphContainer container) {
		this.container = container;
	}

	// public static void main(String... args) {
	// String cmdLine =
	// "$.cmdline(gc)>plug -pid website -t http/1.1 -p input -o input -n website";
	// Pattern p = Pattern.compile("^\\$\\.cmdline\\((.+)\\)\\>(.+)$",
	// Pattern.DOTALL);
	// Matcher m = p.matcher(cmdLine);
	// if (m.find()) {
	// System.out.println(m.group(1));
	// System.out.println(m.group(2));
	// }
	//
	// }

	// output to which netGraph,格式：命令 customgraph名 操作 netgraph名 ,例：connect -c
	// httpGraph -p output -n website
	// input from which netGraph 例：connect -c httpGraph -o input -n website
	// disconnect
	// 对于服务器，如果不存在则返回null
	// 对于客户端，如果不存在，默认情况下充许自动为之分配客户端。
	@Override
	public Object getService(String cmdLine) {
		Result result = new Result();
		if (StringUtil.isEmpty(cmdLine))
			return null;
		if (!cmdLine.startsWith("$.cmdline(")) {
			return null;
		}
		Pattern p = Pattern.compile("^\\$\\.cmdline\\((.+)\\)\\>(.*)$",
				Pattern.DOTALL);
		Matcher m = p.matcher(cmdLine);
		if (!m.find()) {
			if (m.groupCount() < 3) {
				result.state = 503;
				result.message = String.format("指令格式错误：%s", cmdLine);
				return result;
			}
			result.state = 200;
			result.message = "ok";
			return result;

		}
		String argsstr = String.format("-%s", m.group(2).trim());
		String[] args = argsstr.split(" ");
		String cmd = m.group(1);
		IResult ret=null;
		try {
			switch (cmd) {
			case "gc":
				if (options == null) {
					options = createOptions();
				}
				CommandLine line = parser.parse(options, args);
				ret = doCommand(cmd, line, options);
				result.message=ret.message();
				result.state=ret.state();
				result.value=ret.value();
				return result;
			case "cc":
				ret = clientsCommand.command(cmd, args);
				result.message=ret.message();
				result.state=ret.state();
				result.value=ret.value();
				return result;
			case "console":
				result.message="ok";
				result.state=200;
				result.value=new NetSiteConsole();
				return result;

			default:
				result.message="暂不支持的命令";
				result.state=504;
				return result;
			}
		} catch (Exception e) {
			logger.error(e.getMessage());
			result.message=e.getMessage();
			result.state=503;
			return result;
		}
	}

	@Override
	protected boolean cognize(String cmd) {
		return "gc".equals(cmd);
	}

	@Override
	protected IResult command(String cmd, CommandLine line)
			throws ParseException {
		return doCommand(cmd, line, options);
	}

	@Override
	public void dispose() {
		container = null;
	}

	@Override
	public void init(String home) {
//		GraphContainer c = (GraphContainer) container;
//		c.init(home);
	}

	@Override
	protected Parser createParser() {
		// TODO Auto-generated method stub
		return new GnuParser();
	}

	protected IResult doCommand(String cmd, CommandLine line, Options options)
			throws ParseException {
		Result res = new Result();
		if (line.hasOption("m")) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(cmd, options);
			res.state = 200;
			res.message = "ok";
			return res;
		}
		boolean ret = false;
		try {
			if (line.hasOption("plug")) {
				ret = connect(line);
			} else if (line.hasOption("unplug")) {
				ret = disconnect(line);
			} else if (line.hasOption("ls")) {
				ret = ls(line);
			} else if (line.hasOption("is")) {
				ret = is(line);
			} else if (line.hasOption("watch")) {
				ret = watch(line);
			} else {
				throw new ParseException("不能识别的指令");
			}

			String prev = "netsite >";
			System.out.print(prev);
			res.state = ret ? 200 : 503;
			res.message = ret ? "ok" : "error";
			return res;
		} catch (Exception e) {
			res.state = 503;
			res.message = e.getMessage();
			logger.error(e.getMessage());
			String prev = "netsite >";
			System.out.print(prev);
			return res;
		}
	}

	private boolean watch(CommandLine line) {
		if (!line.hasOption("pid") && !line.hasOption("n")) {
			throw new EcmException("参数pid或n必须指定一个.");
		}
		if (!line.hasOption("g")) {
			throw new EcmException("参数g必须.");
		}

		String pid = line.getOptionValue("pid");
		String n = line.getOptionValue("n");
		String g = line.getOptionValue("g");
		boolean off = line.hasOption("off");
		return container.watch(pid, n, g, off);
	}

	private boolean is(CommandLine line) {
		if (!line.hasOption("pid")) {
			throw new EcmException("参数pid必须.");
		}
		if (!line.hasOption("o")) {
			throw new EcmException("参数o必须.");
		}
		if (!line.hasOption("p")) {
			throw new EcmException("参数p必须.");
		}
		if (!line.hasOption("n")) {
			throw new EcmException("参数n必须.");
		}
		if (!line.hasOption("g")) {
			throw new EcmException("参数g必须.");
		}
		String method = line.getOptionValue("o");
		String netName = line.getOptionValue("n");
		String g = line.getOptionValue("g");
		String chipProcessId = line.getOptionValue("pid");
		String p=line.getOptionValue("p");
		return container.isconnect(chipProcessId, g,p, method, netName);
	}

	// 查询定义图到net图的所有连接装态。
	private boolean ls(CommandLine line) {
		if (line.hasOption("all")) {
			return container.lsConnectAll();
		}

		if (!line.hasOption("pid")) {
			throw new EcmException("参数pid必须.");
		}

		if (!line.hasOption("g")) {
			throw new EcmException("参数g必须.");
		}
		String graphName = line.getOptionValue("g");
		String chipProcessId = line.getOptionValue("pid");
		if (!line.hasOption("o")) {
			return container.lsConnectInfo(chipProcessId, graphName);
		}
		if (!line.hasOption("o")) {
			throw new EcmException("参数o必须.");
		}
		if (!line.hasOption("p")) {
			throw new EcmException("参数o必须.");
		}
		String method = line.getOptionValue("o");
		String chipPinName = line.getOptionValue("p");
		return container.lsConnectInfo(chipProcessId, graphName, chipPinName,
				method);
	}

	// connect -c httpGraph -p myOutput ss -o output -n website
	private boolean connect(CommandLine line) {
		if (!line.hasOption("pid")) {
			throw new EcmException("参数pid必须.");
		}
		if (!line.hasOption("o")) {
			throw new EcmException("参数o必须.");
		}
		if (!line.hasOption("n")) {
			throw new EcmException("参数n必须.");
		}
		if (!line.hasOption("p")) {
			throw new EcmException("参数o必须.");
		}
		if (!line.hasOption("g")) {
			throw new EcmException("参数g必须.");
		}
		String method = line.getOptionValue("o");
		String netName = line.getOptionValue("n");
		String g = line.getOptionValue("g");
		String chipProcessId = line.getOptionValue("pid");
		String chipGraphPin = line.getOptionValue("p");
		String flow = "";
		String rewrite = "";
		String to = "";
		if (line.hasOption("fl")) {
			flow = line.getOptionValue("fl");
		}
		if (line.hasOption("re")) {
			if (!line.hasOption("to")) {
				throw new EcmException("参数to必须.");
			}
			rewrite = line.getOptionValue("re");
			to = line.getOptionValue("to");
		}
		return container.connect(chipProcessId, g, chipGraphPin, method,
				netName, flow, rewrite, to);
	}

	private boolean disconnect(CommandLine line) {
		if (!line.hasOption("pid")) {
			throw new EcmException("参数pid必须.");
		}
		if (!line.hasOption("o")) {
			throw new EcmException("参数o必须.");
		}
		if (!line.hasOption("n")) {
			throw new EcmException("参数n必须.");
		}
		if (!line.hasOption("p")) {
			throw new EcmException("参数o必须.");
		}
		if (!line.hasOption("g")) {
			throw new EcmException("参数g必须.");
		}
		String method = line.getOptionValue("o");
		String netName = line.getOptionValue("n");
		String g = line.getOptionValue("g");
		String chipProcessId = line.getOptionValue("pid");
		String chipGraphPin = line.getOptionValue("p");
		return container.disconnect(chipProcessId, g, chipGraphPin, method,
				netName);
	}

	protected Options createOptions() {
		Options options = new Options();
		Option plug = new Option(
				"plug",
				"plug",
				false,
				"将chipgraph的指定pin插入到netgraph,说明：\r\n必须参数：pid,g,o,p,n,例：\r\ngc -plug -pid test -g myGraph -p input -o input -n myNet\r\n"
						+ "可选参数：-flow -rewirte 默认不重写侦地址，且以目标graph的名作为flow的目标。");
		options.addOption(plug);
		Option isplug = new Option(
				"is",
				"isplug",
				false,
				"是否已将chipgraph的指定pin插入到netgraph,说明：\r\n必须参数：pid,g,o,p,n,例：\r\ngc -plug -pid test -g myGraph -p input -o input -n myNet");
		options.addOption(isplug);
		Option unplug = new Option(
				"unplug",
				"unplug",
				false,
				"将chipgraph的指定pin拔掉从指定的netgraph，说明：\r\n必须参数：pid,g,o,p,n,例：\r\ngc -unplug -pid website -g myGraph -p output -o output -n myNet");
		options.addOption(unplug);
		Option ls = new Option("ls", "list", false,
				"查询连接情况。说明：\r\n必须参数：pid,t,可选：o,p");
		options.addOption(ls);
		Option w = new Option("watch", "watch", false,
				"监视指定的输出插头的侦流。\r\n\t必选参数：pid|n,g");
		options.addOption(w);

		Option all = new Option("a", "all", false,
				"结合指令ls，查看chip graph 上的所有连接，因此需要组合pid,g");
		options.addOption(all);
		Option off = new Option("off", "off", false, "关闭watch");
		options.addOption(off);

		Option c = new Option("pid", "chipProcessId", true, "芯片的进程号");
		options.addOption(c);
		Option o = new Option("o", "o", true, "操作：input,output等");
		options.addOption(o);
		Option g = new Option("g", "g", true, "chipGraph的名字");
		options.addOption(g);
		Option p = new Option("p", "pin", true, "chipGraph的端子");
		options.addOption(p);
		Option n = new Option("n", "netgraphName", true, "操作的目标网络图名称。");
		options.addOption(n);
		Option flow = new Option("fl", "flow", true,
				"充许在此插头中流动的侦地址匹配正则表达式。如果未指定该参数，则默认为按net名或graph名导向侦");
		options.addOption(flow);
		Option rewrite = new Option("re", "rewirte", true,
				"匹配要重写的侦地址，正则要将替换的分成组。");
		options.addOption(rewrite);
		Option to = new Option("to", "to", true,
				"rewrite的替换内容。格式：$1{value},\r\n"
						+ "$表示在rewrite中的分组,$1表示分组1，括号内是被替换内容");
		options.addOption(to);
		Option m = new Option("m", "man", false, "帮助");
		options.addOption(m);
		return options;
	}

	@Override
	public <T> ServiceCollection<T> getServices(Class<T> serviceClazz) {
		return null;
	}

	class Result implements IResult {
		int state;
		String message;
		Object value;

		public Result() {
			// TODO Auto-generated constructor stub
		}

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