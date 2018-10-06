package cj.studio.ecm.chip.netsite;

import java.util.Map;

import cj.studio.ecm.IAssembly;
import cj.ultimate.IDisposable;

public interface IProcessManager extends IDisposable{
	void killAll();
	void kill(String pid);
	String startAssembly(String aid,String pid);
	Map<String, IAssembly> lsall();
	IAssembly ls(String pid);
	void loadAssembliesByReg();
	String[] getProcIds(String assemblyid);
	void save();
	Map<String, IAssembly> lsById(String id);
}
