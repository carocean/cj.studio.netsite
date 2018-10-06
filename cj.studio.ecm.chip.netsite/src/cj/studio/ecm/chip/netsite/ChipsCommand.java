package cj.studio.ecm.chip.netsite;

import java.io.File;
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

import cj.studio.ecm.AssemblyState;
import cj.studio.ecm.CJSystem;
import cj.studio.ecm.IAssembly;
import cj.studio.ecm.IChipInfo;
import cj.studio.ecm.ServiceCollection;
import cj.studio.ecm.annotation.CjService;
import cj.studio.ecm.annotation.CjServiceRef;
import cj.studio.ecm.graph.IGraph;
import cj.studio.ecm.graph.IPin;
import cj.studio.ecm.graph.IResult;
import cj.studio.ecm.net.layer.NetLayerGraphCreator;

@CjService(name = "chipsCommand")
public class ChipsCommand extends AbstractCommand implements ICommand {
	@CjServiceRef
	private IChipManager chipManager;
	Logger logger = LoggerFactory.getLogger(ChipsCommand.class);

	@SuppressWarnings("static-access")
	@Override
	protected Options createOptions() {
		Options options = new Options();
		Option ls = new Option("scan", "scan", true, "扫描指定位置的jar以发现芯片");
		options.addOption(ls);
		Option view = new Option("ls", "ls", false,
				"查看netsite进程信息，id|pid|reg|cached,id是程序集标识，pid是进程号，reg用于查看注册表，cached用于查看缓冲");
		options.addOption(view);
		Option t = new Option("run", "run", false,
				"运行指定的芯片，需id(程序集id)，可选pid指定进程号，如不选则以程序集名作为进程号");
		options.addOption(t);
		Option stop = new Option("kill", "kill", false, "停止程序集.需pid");
		options.addOption(stop);
		Option install = new Option("install", "install", false,
				"安装芯片.芯片名文件名或路径,必须结合：f|cached|id参数,\r\n如果指定cached则将已缓冲的全部安装\r\n如果f是目录则扫描后安装");
		options.addOption(install);
		Option uninstall = new Option("un", "uninstall", false,
				"删除一个程序集,需id(程序集标识）");
		options.addOption(uninstall);

		Option g = new Option("g", "graph", false, "打印出程序集的graph");
		options.addOption(g);
		Option f = new Option("f", "f", true, "文件路径");
		options.addOption(f);
		Option ch = new Option("mod", "modify", false,
				"结合reg、-P、id参数，可修改注册表。比如调试时将注册表程序集地址改为编译目的地");
		options.addOption(ch);
		Option cached = new Option("cached", "cached", false, "指缓冲区的程序集.");
		options.addOption(cached);
		Option stopall = new Option("pid", "pid", true, "指定程序集进程号.");
		options.addOption(stopall);
		Option aid = new Option("id", "id", true, "指定程序集id.");
		options.addOption(aid);
		Option reg = new Option("reg", "reg", false, "注册表.");
		options.addOption(reg);
		Option l = new Option("l", "log", false, "充许网络日志输出到控制台");
		options.addOption(l);
		Option m = new Option("m", "man", false, "帮助");
		options.addOption(m);

		Option p = OptionBuilder.withArgName("property=value").hasArgs(2)
				.withValueSeparator()
				.withDescription("设置服务器相关属性,格式为：-Pproperty=value").create("P");
		options.addOption(p);
		return options;
	}

	@Override
	public void init(String home) {
		chipManager.init(home);
	}

	@Override
	protected Parser createParser() {
		return new GnuParser();
	}

	@Override
	public void dispose() {
		chipManager.dispose();
	}

	@Override
	protected IResult command(String cmd, CommandLine line) throws ParseException {
		Result result=new Result();
		if (line.hasOption("m")) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(cmd, options);
			String prev = "netsite >";
			System.out.print(prev);
			result.state=200;
			result.message="ok";
			return result;
		}
		try {
			if (line.hasOption("scan")) {
				scan(line);
			} else if (line.hasOption("install")) {
				install(line);
			} else if (line.hasOption("ls")) {
				ls(line);
			} else if (line.hasOption("run")) {
				run(line);
			} else if (line.hasOption("kill")) {
				kill(line);
			} else if (line.hasOption("uninstall")) {
				uninstall(line);
			} else if (line.hasOption("mod")) {
				modify(line);
			} else {
				throw new ParseException("不能识别的指令");
			}
			String prev = "netsite >";
			System.out.print(prev);
			result.state=200;
			result.message="ok";
			return result;
		} catch (Exception e) {
			result.state=503;
			result.message=e.getMessage();
			logger.error(e.getMessage());
			String prev = "netsite >";
			System.out.print(prev);
			return result;
		}
	}

	private void modify(CommandLine line) {
		if (!line.hasOption("reg")) {
			System.out.println("请指定reg参数");
			return;
		}
		if (!line.hasOption("id")) {
			System.out.println("请指定id参数");
			return;
		}
		if (!line.hasOption("P")) {
			System.out.println("请指定P参数");
			return;
		}
		String id = line.getOptionValue("id");
		Properties props = line.getOptionProperties("P");
		for (Object key : props.keySet()) {
			String v = props.getProperty((String) key);
			chipManager.modifyReg(id, (String) key, v);
		}

	}

	private void uninstall(CommandLine line) {
		if (!line.hasOption("id")) {
			System.out.println("\t\terror:缺少参数id");
			return;
		}
		try {
			chipManager.unInistall(line.getOptionValue("id"));
			System.out.println("\t已处理");
		} catch (Exception e) {
			System.out.println("\terror:" + e);
		}
	}

	private void ls(CommandLine line) {
		if (line.hasOption("pid") && line.hasOption("reg")
				&& line.hasOption("cached")) {
			System.out.println("\terror:pid or reg or cached 三选一，或不用");
			return;
		}
		if (line.hasOption("pid")) {
			String pid = line.getOptionValue("pid");
			IAssembly a = chipManager.findAssembly(pid);
			if (a == null) {
				System.out.println("\terror:不存在指定进程号的程序集" + pid);
				return;
			}
			System.out.println("\tpid:" + pid);
			printAssembly(pid, a);
		} else if (line.hasOption("id")) {
			String id = line.getOptionValue("id");
			Map<String, IAssembly> map = chipManager.findAssemblyProcs(id);
			System.out.println("\tit has procs:" + map.size());
			for (String pid : map.keySet()) {
				IAssembly a = map.get(pid);
				printAssembly(pid, a);
			}

		} else if (line.hasOption("reg")) {
			IRegistry reg = chipManager.getRegistry();
			String ids[] = reg.enumAssemblyId();
			System.out.println("\ttotal:" + ids.length);
			for (String aid : ids) {
				RegistryItem i = reg.get(aid);
				System.out
						.println("\tis running?" + chipManager.isRunning(aid));
				System.out.println("\t---------------------");
				System.out.println("\t\tid:" + aid);
				System.out.println("\t\tname:" + i.getName());
				System.out.println("\t\tversion:" + i.getVersion());
				System.out.println("\t\tdesc:" + i.getDesc());
				System.out.println("\t\tlocation:" + i.getLocation());
				System.out.println("\t\tmainFileName:"
						+ i.getMainAssemblyFileName());
			}
		} else if (line.hasOption("cached")) {
			String ids[] = chipManager.enumCachedKey();
			System.out.println("\tcached total:" + ids.length);
			for (String aid : ids) {
				IAssembly i = chipManager.getCached(aid);
				printAssembly(aid, i);
			}
		} else {
			Map<String, IAssembly> map = chipManager.lsAssemblies();
			System.out.println("\ttotal:" + map.size());
			for (String pid : map.keySet()) {
				IAssembly a = map.get(pid);
				System.out.println("\tpid:" + pid);
				printAssembly(pid, a);
			}
		}

	}

	private void install(CommandLine line) {
		if (!line.hasOption("id") && line.hasOption("cached")
				&& !!line.hasOption("f")) {
			System.out.println("\terror:id|cached|f 参数三选一");
			return;
		}
		StringBuffer sb = new StringBuffer();
		try {
			if (line.hasOption("f")) {
				String fn = line.getOptionValue("f");
				File f = new File(fn);
				if (f.isDirectory()) {
					chipManager.installByDirectory(f, sb);
				} else {
					chipManager.installByFile(f, sb);
				}
				System.out.println(sb);

			}
			if (line.hasOption("id")) {
				sb = new StringBuffer();
				chipManager.installById(line.getOptionValue("id"), sb);
				System.out.println(sb);
				return;// 如果有id则不论是否有cached只装指定的id
			}
			if (line.hasOption("cached")) {
				String[] keys = chipManager.enumCachedKey();
				for (String k : keys) {
					sb = new StringBuffer();
					chipManager.installById(k, sb);
					System.out.println(sb);
				}
			}
		} catch (Exception e) {
			System.out.println("\terror:" + e);
			CJSystem.current().environment().logging().error(getClass(),e);
			throw e;
		} catch (NoClassDefFoundError e2) {
			System.out.println("\terror:" + e2);
			CJSystem.current().environment().logging().error(getClass(),e2);
		}
	}

	private void scan(CommandLine line) {
		Map<String, IAssembly> scan = null;
		StringBuffer sb = new StringBuffer();
		chipManager.emptyCache();

		scan = chipManager.scanAssemblies(line.getOptionValue("scan"), sb);
		System.out.println(sb);
		System.out.println("\tcached total:" + scan.size());
		for (String k : scan.keySet()) {
			IAssembly a = scan.get(k);
			printAssembly(a.info().getGuid(), a);
		}

	}

	private void printAssembly(String k, IAssembly a) {
		System.out.println("\t---------------------");
		if (a.state() == AssemblyState.unloaded) {
			System.out.println("\t\tinfo:程序集未被装载,pid:" + k);
			return;
		}
		IChipInfo i = a.workbin().chipInfo();
		System.out.println("\t\tid:" + i.getId());
		System.out.println("\t\tname:" + i.getName());
		System.out.println("\t\tversion:" + i.getVersion());
		System.out.println("\t\tstatus:" + a.state());
		System.out.println("\t\tdesc:" + i.getDescription());
		System.out.println("\t\tresource:" + i.getResource());
		System.out.println("\t\tfile:" + a.fileName());
		ServiceCollection<IGraph> col = a.workbin().part(IGraph.class);
		for (IGraph g : col) {
			System.out.println("\t\tgraph:" + g.name());
			System.out.println("\t\t\tprotocol:"
					+ (g.protocolFactory() == null ? "-" : g.protocolFactory()
							.getProtocol()));
			System.out.println("\t\t\taccept:" + (g.acceptProtocol()));
			String[] ins = g.enumInputPin();
			System.out.println("\t\t\tinput total:" + ins.length);
			for (String in : ins) {
				System.out.println("\t\t\t\tname:" + in);
				IPin inpin = g.in(in);
				System.out
						.println("\t\t\t\thasNetLayer:"
								+ (inpin.contains(NetLayerGraphCreator.KEY_SESSION_SINK)?"会话管理器":"-"));
			}
			String[] outs = g.enumOutputPin();
			System.out.println("\t\t\toutput total:" + outs.length);
			for (String out : outs) {
				System.out.println("\t\t\t\tname:" + out);
				IPin outpin = g.out(out);
				System.out
				.println("\t\t\t\thasNetLayer:"
						+ (outpin.contains(NetLayerGraphCreator.KEY_COOKIE_CONTAINER_SINK)?"Cookie容器":"-"));
			}
		}
	}

	private void kill(CommandLine line) {
		if (!line.hasOption("pid")) {
			System.out.println("\terror:必须参数pid");
			return;
		}
		IAssembly a = chipManager.findAssembly(line.getOptionValue("pid"));
		if (a == null) {
			System.out.println("\terror:不存在指定的进程");
			return;
		}
		if (a.state() != AssemblyState.actived) {
			System.out.println("\terror:进程未被激活，无需停止");
			return;
		}
		chipManager.kill(line.getOptionValue("pid"));
		System.out.println("\t已杀:" + line.getOptionValue("pid"));
	}

	private void run(CommandLine line) {
		if (!line.hasOption("id")) {
			System.out.println("\terror:必须参数id");
			return;
		}
		if (!chipManager.isInstall(line.getOptionValue("id"))) {
			System.out.println("\terror:注册表不存在指定的程序集");
			return;
		}
		String pid = "";
		if (line.hasOption("pid")) {
			pid = line.getOptionValue("pid");
		}
		pid = chipManager.run(line.getOptionValue("id"), pid);
		System.out.println("\t成功运行，pid:" + pid);
	}

	@Override
	protected boolean cognize(String cmd) {
		return "ac".equals(cmd);
	}

}
