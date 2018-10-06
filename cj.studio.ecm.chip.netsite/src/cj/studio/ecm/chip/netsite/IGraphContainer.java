package cj.studio.ecm.chip.netsite;

import cj.studio.ecm.graph.IGraph;
import cj.studio.ecm.net.graph.INetGraph;
import cj.ultimate.IDisposable;

//netsite的graph容器。
//1.netgraphs
//2.chipGraphs
//功能：为之添加、移除netGraph，为之添加、移除chipGraph
public interface IGraphContainer extends IDisposable{
//	Set<String> getProcessIds();
	IGraph[] getChipGraphis();
	void init(String home);
	void startedNet(INetGraph ng,StringBuffer sb);
	void removeNetGraph(String key,StringBuffer sb);
	void addChipGraph(String key,IGraph g,StringBuffer sb);
	void removeChipGraph(String key,StringBuffer sb);
	boolean connect(String chipGraphPID,String graphName,String chipGraphPin, String method, String netName, String flow, String rewrite, String to);
	boolean disconnect(String chipGraphPID,String graphName,String chipGraphPin, String method, String netName);
	boolean lsConnectInfo(String chipGraphPID,String graphName,String chipGraphPin,String method);
	boolean lsConnectInfo(String chipProcessId, String graphName);
	boolean lsConnectAll();
//	void setSessionCenter(INetSiteSessionCenter sessionCenter) ;
	/**
	 * 获取两图之间的连接端子
	 * <pre>
	 *
	 * </pre>
	 * @param chipProcessId
	 * @param graphName
	 * @param method
	 * @param netName
	 * @return 如果已连接，则返回端子名。如果没有连接则返回null
	 */
	boolean isconnect(String chipProcessId, String graphName,String pinName,
			String method, String netName);
	void setNetSiteRoot(String d);
	boolean watch(String pid, String n, String g, boolean off);
	void retrieveClientNetConnectors(INetGraph ng);
	void retrieveServerNetConnectors(INetGraph ng);
	void runNetsiteFinished();
}
