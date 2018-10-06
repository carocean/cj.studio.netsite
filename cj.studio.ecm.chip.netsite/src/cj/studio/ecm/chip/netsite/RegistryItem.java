package cj.studio.ecm.chip.netsite;

public class RegistryItem {
	private String location;
	private String mainAssemblyFileName;
	private String name;
	private String desc;
	private String version;
	public String getMainAssemblyFileName() {
		return mainAssemblyFileName;
	}
	public void setMainAssemblyFileName(String mainAssemblyFileName) {
		this.mainAssemblyFileName = mainAssemblyFileName;
	}
	public String getLocation() {
		return location;
	}
	public void setLocation(String location) {
		this.location = location;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getDesc() {
		return desc;
	}
	public void setDesc(String desc) {
		this.desc = desc;
	}
	public String getVersion() {
		return version;
	}
	public void setVersion(String version) {
		this.version = version;
	}
}
