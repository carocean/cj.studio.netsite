package cj.studio.ecm.chip.netsite;

import java.io.File;
import java.util.List;
import java.util.Map;

import cj.studio.ecm.IAssembly;
import cj.studio.ecm.net.IServer;
import cj.ultimate.IDisposable;

public interface IChipManager extends IDisposable {

	void init(String home);


	Map<String, IAssembly> scanAssemblies(String dir,StringBuffer sb);
	boolean isInstall(String id);

	 IRegistry getRegistry();
	 IAssembly installByFile(File file, StringBuffer sb);
	List<IAssembly> installByDirectory(File f, StringBuffer sb);

	IAssembly installById(String id,  StringBuffer sb);

	void unInistall(String id);

	//进程号，程序集
	Map<String, IAssembly> lsAssemblies();
	String run(String id,String pid);
	void kill(String pid);


	void emptyCache();


	String[] enumCachedKey();


	IAssembly findAssembly(String pid);


	IAssembly getCached(String aid);


	boolean isRunning(String aid);


	List<IServer> getMainChipServers();


	Map<String, IAssembly> findAssemblyProcs(String id);


	void modifyReg(String aid,String key, String v);


}
