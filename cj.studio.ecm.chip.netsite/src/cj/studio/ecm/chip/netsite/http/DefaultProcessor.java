package cj.studio.ecm.chip.netsite.http;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import cj.studio.ecm.Scope;
import cj.studio.ecm.annotation.CjService;
import cj.studio.ecm.annotation.CjServiceRef;
import cj.studio.ecm.frame.Circuit;
import cj.studio.ecm.frame.Frame;
import cj.studio.ecm.graph.CircuitException;
import cj.studio.ecm.graph.IGraph;
import cj.studio.ecm.graph.IPlug;
import cj.studio.ecm.graph.ISink;
import cj.ultimate.util.StringUtil;

@CjService(name = "defaultProcessor", scope = Scope.multiton)
public class DefaultProcessor implements ISink {
	@CjServiceRef(refByName = "netSiteDefaultHttpGraph")
	private IGraph defaultWebSite;
	private String rootSiteProcess[];
	List<String> processIds;
	Logger log = Logger.getLogger(DefaultProcessor.class);

	public DefaultProcessor() {
		processIds=new ArrayList<String>();
	}

	public void setProcessIds(List<String> processIds) {
		this.processIds = processIds;
	}

	@Override
	public void flow(Frame frame,Circuit circuit, IPlug plug) throws CircuitException {
		Circuit c = circuit;
		if (plug.hasNext()) {// 根站站点，无上下文根的，即不在进程列表的便是无上下文根，那么就启用默认站点，如果没有默认站点，则执行defaultWebSite即系统站点
			if(c.attribute("switch-name")!=null){//switch net由于是内部net，因此不需要重写地址
				plug.flow(frame, c);
				return;
			}
			Object ssimple = c.attribute("select-simple");
			if ("http".equals(ssimple)
					&& "server".equals(c.attribute("select-type"))
					&& !processIds.contains(frame.rootName())) {
				boolean redirectRootSite = false;
				if (rootSiteProcess == null
						|| StringUtil.isEmpty(rootSiteProcess[0])) {
					redirectRootSite = false;
				} else {
					if (processIds.contains(rootSiteProcess[0])) {
						redirectRootSite = true;
					} else {
						redirectRootSite = false;
					}
				}
				if (redirectRootSite) {
					String defaultUrl = String.format("/%s%s",
							rootSiteProcess[0], frame.url());// 如果默认的根站点也不存在，则执行defaultWebSite即系统站点，如果请求的服务器没有连接到该默认站点，则算挂了，那与此事无关。
					log.debug(String.format("请求地址已重写，原地址：%s,新地址：%s",
							frame.url(), defaultUrl));
					frame.head("url", defaultUrl);
					plug.flow(frame, c);
					return;
				} else {
					if (!defaultWebSite.isInit())
						defaultWebSite.initGraph();
					defaultWebSite.in("input").flow(frame, c);
					return;
				}
			}
			plug.flow(frame, c);
			return;
		}
		if (!"server".equals(c.attribute("select-type"))){
			plug.flow(frame, c);
			return;
		}
		// 空的处理，欢迎页面
		Object ssimple = c.attribute("select-simple");
		if ("http".equals(ssimple)) {
			if ("NET/1.1".equals(frame.protocol()))
				return;
			if (!defaultWebSite.isInit())
				defaultWebSite.initGraph();
			defaultWebSite.in("input").flow(frame, c);
		} else {
			String warnning = String
					.format("告示：\r\n\t如果您看到该信息，说明所请求的服务器还未连接任何站点进程,因此不能为您服务。\r\n请求的服务器类型：%s,服务器名:%s",
							ssimple, c.attribute("select-name"));
			c.content().writeBytes(warnning.getBytes());
		}
		return;

	}

	public void setNetSiteRoot(String[] rootSiteProcess2) {
		this.rootSiteProcess = rootSiteProcess2;
	}

}