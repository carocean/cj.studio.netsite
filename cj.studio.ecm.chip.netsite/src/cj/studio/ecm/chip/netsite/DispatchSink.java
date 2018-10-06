package cj.studio.ecm.chip.netsite;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import cj.studio.ecm.IServiceSite;
import cj.studio.ecm.Scope;
import cj.studio.ecm.annotation.CjService;
import cj.studio.ecm.annotation.CjServiceRef;
import cj.studio.ecm.frame.Circuit;
import cj.studio.ecm.frame.Frame;
import cj.studio.ecm.graph.CircuitException;
import cj.studio.ecm.graph.IBranchKey;
import cj.studio.ecm.graph.IBranchSearcher;
import cj.studio.ecm.graph.ICablePin;
import cj.studio.ecm.graph.IPin;
import cj.studio.ecm.graph.IPlug;
import cj.studio.ecm.graph.ISink;
import cj.studio.ecm.net.nio.NetConstans;
import cj.ultimate.util.StringUtil;

@CjService(name = "dispatchSink", scope = Scope.multiton)
public class DispatchSink implements ISink {
	Logger log = Logger.getLogger(DispatchSink.class);
	@CjServiceRef
	private ISink netSiteVisitor;

	@Override
	public void flow(Frame frame,Circuit circuit, IPlug plug) throws CircuitException {
		String isNet = plug.site().getProperty("$.graph.isNet");
		// System.out.println("this is isNet:"+isNet);
		if ("true".equals(isNet)) {
			flowToChipGraph(frame,circuit, plug);
		} else {
			flowToNetGraph(frame,circuit, plug);
		}
	}

	private void flowToNetGraph(Frame frame,Circuit circuit, IPlug plug)
			throws CircuitException {
		// 将来添加问路的功能。查所有的路，问指定的路是否有
		boolean isWatch = "true".equals(plug.option("iswatch"));
		if (isWatch) {
			log.info(String.format("chipGraph(%s)向net请求\r\n\t侦：%s", plug.site()
					.getProperty("$.graph.name"), frame));
		}

		IBranchSearcher searcher = new MyOutputBranchSearcher(frame, circuit);
		IPin p = plug.branch(frame.url(), searcher);
		IBranchKey key = searcher.found();
		searcher.dispose();
		if (key == null) {
			IBranchKey rbkeys[] = plug.enumBranchKey();
			String flows = "";
			String nets = "";
			for (IBranchKey k : rbkeys) {
				RuleBranchKey the = (RuleBranchKey) k;
				flows = String.format("%s,%s", flows, the.flow);
				nets = String.format("%s,%s", nets, the.keyNetName);
			}
			if (nets.startsWith(",")) {
				nets = nets.substring(1);
			}
			throw new CircuitException(NetConstans.STATUS_606, String.format(
					"没有找到可接收当前侦的适配地址配置。请求地址：%s,此插头的可用net有：%s,适配地址模式：%s.",
					frame.url(), nets, flows));
		}

		RuleBranchKey rbkey = (RuleBranchKey) key;
		if (isWatch) {
			System.out.println(String.format(
					"\t找到分支：pid %s flow %s rewrite %s to %s accept %s net %s",
					rbkey.keyPid, rbkey.flow, rbkey.rewrite, rbkey.to,
					rbkey.accept, rbkey.keyNetName));
		}
		if (isWatch) {
			System.out.println("\t成功接受当前侦的协议。");
		}
		if (!StringUtil.isEmpty(rbkey.rewrite)) {// 重写
			String rewrite = getRewriteUrl(frame, rbkey);
			frame.head("url", rewrite);
			if (isWatch) {
				System.out.println(String.format("\t发现重写地址，重写为：%s", rewrite));
			}
		}

		// 此处不必回复类加载器，因为netsite程序集的当前上下文即便是用户程序集也没关系，因为netsite不使用用户程序集的类型。
		//注释掉的原因：当graph内部向外访问时，此处却以netGraph的加载器（为应用加载器，非cj的加载器），因此会导致紧跟其后的代码找不到类型。
//		ClassLoader cl = (ClassLoader) p.site().getService(
//				"$.graph.classloader");
//		Thread.currentThread().setContextClassLoader(cl);

		String sid = (String) frame.parameter("select-id");
		if (!StringUtil.isEmpty(sid))
			circuit.attribute("select-id", sid);
		if (p instanceof ICablePin) {
			ICablePin cable = (ICablePin) p;
			if (cable.isEmpty()) {
				throw NetConstans.newCircuitException(NetConstans.STATUS_606, rbkey.keyNetName);
			} else {
				String cableNet = (String) p.options("cable-net");
				if ("client".equals(cableNet)) {
					cable.flow(frame, circuit);
				} else {
					if (StringUtil.isEmpty(sid)) {
						sid = (String) circuit.attribute("select-id");
					}
					if (StringUtil.isEmpty(sid)) {
						cable.flow(frame, circuit);
//						throw new CircuitException("404", String.format(
//								"选择的导线%s不存在。", sid));
						return;
					}
					cable.flow(sid, frame, circuit);
				}
				return;
			}
		}
		p.flow(frame, circuit);

		// }
	}

	private void flowToChipGraph(Frame frame,Circuit circuit, IPlug plug)
			throws CircuitException {

		if ("NET/1.1".equals(frame.protocol())) {
			if ("connect".equals(frame.command())
					|| "disconnect".equals(frame.command())) {
				IBranchKey[] arr = plug.enumBranchKey();
				for (IBranchKey key : arr) {
					IPin p = plug.branch(key);
					IServiceSite site = p.site();
					if (site != null) {
						ClassLoader cl = (ClassLoader) site
								.getService("$.cj.studio.resource");
						Thread.currentThread().setContextClassLoader(cl);
					}
					if (p instanceof ICablePin) {
						ICablePin cp = (ICablePin) p;
						String sid = (String) circuit.attribute("select-id");
						if ("connect".equals(frame.command())) {
							cp.newWire(sid);
							cp.flow(sid, frame, circuit);
						}else if("disconnect".equals(frame.command())){
							cp.newWire(sid);
							cp.flow(sid, frame, circuit);
							cp.removeWire(sid);
						}
					} else {
						p.flow(frame, circuit);
					}
				}

			}
			return;
		}
		boolean isWatch = "true".equals(plug.option("iswatch"));
		if (isWatch) {
			log.info(String.format("请求chipGraph(%s)\r\n\t侦：%s", plug.site()
					.getProperty("$.graph.name"), frame));
		}
		if ("NETSITE/1.0".equals(frame.protocol())) {// 访问netsite，管理客户端net，连接等功能。
			netSiteVisitor.flow(frame,circuit, plug);
			return;
		}
		// 将来添加问路的功能。
		IBranchSearcher searcher = new MyInputBranchSearcher(frame, circuit);
		IPin p = plug.branch(frame.url(), searcher);
		IBranchKey key = searcher.found();
		searcher.dispose();
		if (key == null) {
			IBranchKey rbkeys[] = plug.enumBranchKey();
			String flows = "";
			String pids = "";
			for (IBranchKey k : rbkeys) {
				RuleBranchKey the = (RuleBranchKey) k;
				flows = String.format("%s,%s", flows, the.flow);
				pids = String.format("%s,%s", pids, the.keyPid);
			}
			if (pids.startsWith(",")) {
				pids = pids.substring(1);
			}
			throw new CircuitException(NetConstans.STATUS_606, String.format(
					"没有找到可接收当前侦的适配地址配置. 请求地址：%s,此插头的进程有：%s,适配地址模式：%s.",
					frame.url(), pids, flows));
		}
		RuleBranchKey rbkey = (RuleBranchKey) key;
		if (isWatch) {
			System.out.println(String.format(
					"\t找到分支：pid %s flow %s rewrite %s to %s accept %s net %s",
					rbkey.keyPid, rbkey.flow, rbkey.rewrite, rbkey.to,
					rbkey.accept, rbkey.keyNetName));
		}
		if (!matchProtocol(rbkey.accept, frame.protocol())) {// 检查graph是否接受当前协议。
			throw new CircuitException(NetConstans.STATUS_600, String.format(
					"不接受应用协议%s,侦的协议：%s", rbkey.accept, frame.protocol()));
		}
		if (isWatch) {
			System.out.println("\t成功接受当前侦的协议。");
		}
		if (!StringUtil.isEmpty(rbkey.rewrite)) {// 重写
			String rewrite = getRewriteUrl(frame, rbkey);
			frame.head("url", rewrite);
			if (isWatch) {
				System.out.println(String.format("\t发现重写地址，重写为：%s", rewrite));
			}
		}
		// 切换当前线程的加载器，因为jss脚本上下文以默认当前线程的类加载器，如此处不指定会使用netsite的加载器，导致用户程序集内的jss找不到类型，只是为netsite内的应用构建一个上下文执行环境。
		// 该方法只对jss脚本文件起作用。
		IServiceSite site = p.site();
		if (site != null) {
			ClassLoader cl = (ClassLoader) site
					.getService("$.cj.studio.resource");
			Thread.currentThread().setContextClassLoader(cl);
		}

		if (p instanceof ICablePin) {
			String sid = (String) circuit.attribute("select-id");
			if (StringUtil.isEmpty(sid)) {
				throw new CircuitException(NetConstans.STATUS_604, "回路中未指定select-id");
			}
			ICablePin cable = (ICablePin) p;
			if (cable.containsWire(sid)) {
				cable.flow(sid, frame, circuit);
				return;
			}
			if (cable.newWire(sid)) {
				cable.flow(sid, frame, circuit);
				return;
			}
		}

		p.flow(frame, circuit);
		// }

	}

	private boolean matchProtocol(String accept, String protocol) {
		return Pattern.matches(accept, protocol);
	}

	private int trunk(String url, StringBuffer sb, int pos, int start, int end,
			String text) {
		String tmp = url.substring(pos, start);
		sb.append(tmp);
		sb.append(text);
		return end;
	}

	private String getRewriteUrl(Frame frame, RuleBranchKey rbkey)
			throws CircuitException {
		String url = frame.url();
		String rewrite = rbkey.rewrite;
		String to = rbkey.to;
		Map<Integer, String> map = new HashMap<Integer, String>();
		String[] arr = to.split(",");
		Pattern p = Pattern.compile("\\$(\\d+)\\{(\\w+)\\}");
		for (String key : arr) {
			Matcher m = p.matcher(key);
			if (!m.matches()) {
				throw new CircuitException("503", "插头的重写规则to参数格式有错.");
			}
			int i = Integer.valueOf(m.group(1));
			map.put(i, m.group(2));
		}
		Pattern pattern = Pattern.compile(rewrite, Pattern.DOTALL);
		Matcher matcher = pattern.matcher(url);
		StringBuffer sb = new StringBuffer();
		int pos = 0;
		if (matcher.matches()) {
			for (int i = 0; i < matcher.groupCount(); i++) {
				int start = matcher.start(i + 1);
				int end = matcher.end(i + 1);
				pos = trunk(url, sb, pos, start, end, map.get(i + 1));// 每次以原串来截，最后拼成，每次截的结果位置都变了
			}
			String end = url.substring(pos, url.length());
			sb.append(end);
		}
		return sb.toString();
	}

	// public static void main(String...strings){
	// String url="/website2/33";
	// System.out.println(Pattern.matches("/website/.*", url));
	// }
	class MyInputBranchSearcher implements IBranchSearcher {
		IBranchKey found;
		Circuit circuit;
		Frame frame;

		public MyInputBranchSearcher(Frame frame, Circuit circuit) {
			this.circuit = circuit;
			this.frame = frame;
		}

		@Override
		public IBranchKey found() {
			return found;
		}
		@Override
		public void dispose() {
			frame=null;
			circuit=null;
		}
		@Override
		public IPin search(String url, HashMap<IBranchKey, IPin> branches) {
			// if (branches.containsKey(url))
			// return branches.get(url);
			IBranchKey[] keys = branches.keySet().toArray(new IBranchKey[0]);
			// key是否匹配上name?key格式：%s#%s为：进程号，可接受的协议集合
			// key例：terminus#* 表示可接受所有协议输入
			// name为输入文本，例：terminus#HTTP/1.1
			IBranchKey other = null;
			for (IBranchKey key : keys) {
				RuleBranchKey k = (RuleBranchKey) key;
				if (k.flow.startsWith("?CA")) {
					String head = k.flow.substring(3, k.flow.length());
					if (circuit.containsHead(head)) {
						IPin ret = branches.get(key);
						found = key;
						return ret;
					}
					continue;
				}
				if (k.flow.startsWith("$CA")) {
					String kv = k.flow.substring(3, k.flow.length());
					String[] arr = kv.split("=");
					if (arr[1].equals(circuit.attribute(arr[0]))) {
						IPin ret = branches.get(key);
						found = key;
						return ret;
					}
					continue;
				}
				if (k.flow.startsWith("$FH")) {
					String kv = k.flow.substring(3, k.flow.length());
					String[] arr = kv.split("=");
					if (arr[1].equals(frame.head(arr[0]))) {
						IPin ret = branches.get(key);
						found = key;
						return ret;
					}
					continue;
				}
				if (k.flow.startsWith("$FP")) {
					String kv = k.flow.substring(3, k.flow.length());
					String[] arr = kv.split("=");
					if (arr[1].equals(frame.parameter(arr[0]))) {
						IPin ret = branches.get(key);
						found = key;
						return ret;
					}
					continue;
				}
				if (".*".equals(k.flow) || "/.*".equals(k.flow)
						|| "^/.*".equals(k.flow) || "^/.*$".equals(k.flow)) {
					other = k;
					continue;
				}
				IPin ret = null;
				if (StringUtil.isEmpty(k.flow)) {// 则使用pid名
					String s = String.format("/%s/.*|/%s\\?.*", k.keyPid,
							k.keyPid);
					if (Pattern.matches(s, url)) {
						ret = branches.get(key);
					}
				}
				if (ret == null && Pattern.matches(k.flow, url)) {
					ret = branches.get(key);
				}

				if (ret != null) {
					found = key;
					return ret;
				}
			}
			if (other != null) {
				found = other;
				return branches.get(other);
			}
			return null;
		}
	}

	class MyOutputBranchSearcher implements IBranchSearcher {
		IBranchKey found;
		Circuit circuit;
		Frame frame;

		public MyOutputBranchSearcher(Frame frame, Circuit circuit) {
			this.circuit = circuit;
			this.frame = frame;
		}
		@Override
		public void dispose() {
			frame=null;
			circuit=null;
		}
		@Override
		public IBranchKey found() {
			return found;
		}

		@Override
		public IPin search(String url, HashMap<IBranchKey, IPin> branches) {
			// if (branches.containsKey(url))
			// return branches.get(url);
			IBranchKey[] keys = branches.keySet().toArray(new IBranchKey[0]);
			// key是否匹配上name?key格式：%s#%s为：进程号，可接受的协议集合
			// key例：terminus#* 表示可接受所有协议输入
			// name为输入文本，例：terminus#HTTP/1.1
			IBranchKey other = null;
			for (IBranchKey key : keys) {
				RuleBranchKey k = (RuleBranchKey) key;
				if (k.flow.startsWith("?CA")) {
					String head = k.flow.substring(3, k.flow.length());
					if (circuit.containsHead(head)) {
						IPin ret = branches.get(key);
						found = key;
						return ret;
					}
					continue;
				}
				if (k.flow.startsWith("$CA")) {
					String kv = k.flow.substring(3, k.flow.length());
					String[] arr = kv.split("=");
					if (arr[1].equals(circuit.attribute(arr[0]))) {
						IPin ret = branches.get(key);
						found = key;
						return ret;
					}
					continue;
				}
				if (k.flow.startsWith("$FH")) {
					String kv = k.flow.substring(3, k.flow.length());
					String[] arr = kv.split("=");
					if (arr[1].equals(frame.head(arr[0]))) {
						IPin ret = branches.get(key);
						found = key;
						return ret;
					}
					continue;
				}
				if (k.flow.startsWith("$FP")) {
					String kv = k.flow.substring(3, k.flow.length());
					String[] arr = kv.split("=");
					if (arr[1].equals(frame.parameter(arr[0]))) {
						IPin ret = branches.get(key);
						found = key;
						return ret;
					}
					continue;
				}
				if (".*".equals(k.flow) || "/.*".equals(k.flow)
						|| "^/.*".equals(k.flow) || "^/.*$".equals(k.flow)) {
					other = k;
					continue;
				}
				IPin ret = null;
				if (StringUtil.isEmpty(k.flow)) {// 则使用key名
					String s = String.format("/%s/.*|/%s\\?.*", k.keyNetName,
							k.keyNetName);
					if (Pattern.matches(s, url)) {
						ret = branches.get(key);
					}
				}
				if (ret == null && Pattern.matches(k.flow, url)) {
					ret = branches.get(key);
				}

				if (ret != null) {
					found = key;
					return ret;
				}
			}
			if (other != null) {
				found = other;
				return branches.get(other);
			}
			return null;
		}
	}
}
