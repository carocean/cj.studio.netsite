package cj.studio.ecm.chip.netsite;

import cj.studio.ecm.IAssembly;

public interface IRegistry {
	String[] enumAssemblyId();
	void save();
	RegistryItem get(String aid);
	void load();

	boolean isInstall(String id);

	void install(IAssembly a);

	void uninstall(String id);

	void empty();
	
	void print(StringBuffer sb);
	IAssembly loadAssembly(String id);
	void modify(String aid,String key, String v);
}
