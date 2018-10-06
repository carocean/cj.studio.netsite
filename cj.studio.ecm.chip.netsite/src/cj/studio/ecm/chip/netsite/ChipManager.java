package cj.studio.ecm.chip.netsite;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import cj.studio.ecm.Assembly;
import cj.studio.ecm.AssemblyState;
import cj.studio.ecm.EcmException;
import cj.studio.ecm.IAssembly;
import cj.studio.ecm.IAssemblyInfo;
import cj.studio.ecm.annotation.CjService;
import cj.studio.ecm.annotation.CjServiceRef;
import cj.studio.ecm.graph.IGraph;
import cj.studio.ecm.net.IServer;
import cj.ultimate.IDisposable;
import cj.ultimate.collection.ICollection;
import cj.ultimate.gson2.com.google.gson.Gson;
import cj.ultimate.gson2.com.google.gson.GsonBuilder;
import cj.ultimate.gson2.com.google.gson.reflect.TypeToken;
import cj.ultimate.util.FileHelper;
import cj.ultimate.util.StringUtil;

@CjService(name = "chipManager")
public class ChipManager implements IDisposable, IChipManager {
	String home;
	IProcessManager pm;
	IRegistry registry;
	Map<String, IAssembly> cache;// key=guid
	@CjServiceRef
	INetSite netSite;
	Logger logger = Logger.getLogger(ChipManager.class);

	public ChipManager() {
		registry = new Registry();
		pm = new ProcessManager();
		cache = new HashMap<String, IAssembly>();
	}

	@Override
	public List<IServer> getMainChipServers() {
		List<IServer> servers = new ArrayList<IServer>(2);
		return servers;
	}

	@Override
	public void dispose() {
		pm.save();
		pm.dispose();
		registry.save();
		cache.clear();
	}

	public void init(String home) {
		this.home = home;
		registry.load();
		netSite.init(home);
		pm.loadAssembliesByReg();
		netSite.chipsLoadFinished(pm.lsall());
	}

	public Map<String, IAssembly> scanAssemblies(String dir, StringBuffer sb) {
		FilenameFilter ff = new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.endsWith(".jar");
			}
		};
		// if (dir.equals("$embed")) {
		// File embed = new File(home + File.separator + registry.embedDir());
		// File inembed[] = embed.listFiles(ff);
		// if (inembed.length > 0) {
		// sb.append("\r\n\tscans embed:");
		// scans(inembed, sb);
		// }
		// } else if (dir.equals("$local")) {
		// File local = new File(home + File.separator + registry.localDir());
		// File inlocal[] = local.listFiles(ff);
		// if (inlocal.length > 0) {
		// sb.append("\r\n\tscans local:");
		// scans(inlocal, sb);
		// }
		// } else {
		File f = new File(dir);
		if (!f.exists()) {
			sb.append("\r\n\t\t目录不存在:" + dir);
			return cache;
		}
		File other = new File(dir);
		File inother[] = other.listFiles(ff);
		sb.append("\r\n\tscans dir:" + dir);
		scans(inother, sb);
		return cache;
		// }
		// return cache;
	}

	@Override
	public void emptyCache() {
		cache.clear();
	}

	private void scans(File[] files, StringBuffer sb) {
		for (File a : files) {
			IAssembly ass = null;
			try {
				sb.append("\r\n\t\tfound：" + a.getAbsolutePath());
				IAssemblyInfo ai = Assembly.viewAssembly(a.getAbsolutePath());
				ass = (Assembly) ai;

				String id = ass.info().getGuid();
				if (registry.isInstall(id)) {
					IAssembly installed = registry.loadAssembly(id);
					if (ass.info().getVersion()
							.equals(installed.info().getVersion())) {
						sb.append("\r\n\t\terror:冲突！该程序集版本已安装。" + id + "  "
								+ ass.info().getVersion());
						continue;
					} else {
						sb.append("\r\n\t\tinfo:安装区发现新的版本：" + id + "  "
								+ ass.info().getVersion() + " 旧版本是："
								+ installed.info().getVersion());
					}
				}
				if (cache.containsKey(id)) {
					IAssembly installed = cache.get(id);
					if (ass.info().getVersion()
							.equals(installed.info().getVersion())) {
						sb.append("\r\n\t\terror:冲突！该程序集版本已缓存。" + id + "  "
								+ ass.info().getVersion());
						continue;
					} else {
						sb.append("\r\n\t\tinfo:缓冲区发现新的版本：" + id + "  "
								+ ass.info().getVersion() + " 旧版本是："
								+ installed.info().getVersion());
					}
				}
				cache.put(id, ass);
				sb.append("\r\n\t\tloaded:" + ass.info().getGuid() + " "
						+ ass.info().getVersion() + " " + a.getName());
			} catch (Exception e) {
				sb.append(
						"\r\n\t\terror to load:" + a.getName() + " cause:" + e);
				continue;
			} catch (NoClassDefFoundError e2) {
				sb.append("\r\n\t\terror to load:" + a.getName() + " cause:"
						+ e2);
				continue;
			}
		}
	}

	@Override
	public boolean isInstall(String id) {
		return registry.isInstall(id);
	}

	@Override
	public IRegistry getRegistry() {
		return registry;
	}

	@Override
	public List<IAssembly> installByDirectory(File file, StringBuffer sb) {
		if (!file.exists()) {
			sb.append("\terror:路径不存在。" + file.getAbsolutePath());
			return null;
		}
		if (!file.isDirectory()) {
			sb.append("\terror:路径不是目录。" + file.getAbsolutePath());
			return null;
		}
		List<IAssembly> list = new ArrayList<IAssembly>();

		Map<String, IAssembly> map = scanAssemblies(file.getAbsolutePath(), sb);
		String[] keys = map.keySet().toArray(new String[0]);
		for (String k : keys) {
			IAssembly ass = installById(k, sb);
			if (ass != null)
				list.add(ass);
		}
		return list;
	}

	public IAssembly installByFile(File file, StringBuffer sb) {
		if (file.isDirectory())
			throw new RuntimeException("不是文件。" + file);
		IAssemblyInfo i = Assembly.viewAssembly(file.getAbsolutePath());
		IAssembly a = (IAssembly) i;
		// verify(a);
		cache.put(a.info().getGuid(), a);
		installById(a.info().getGuid(), sb);
		return a;
	}

	public static void main(String... sb) throws IOException {
		File f = new File(
				"/Users/carocean/studio/lns/build/netsite/assemblies/cj.test.website-1.0.jar");
		System.out.println(f.getName());
		System.out.println(f.getParent());
		System.out.println(f.getPath());
		System.out.println(f.getCanonicalPath());

		Pattern p = Pattern.compile("\\$\\((\\w+)\\)");
		String v = "/dddd/$(web)/dddd$(ccc)/sss";
		Matcher m = p.matcher(v);
		while (m.find()) {
			String ref = m.group(1);
			System.out.println(ref);
			String s = m.replaceFirst("tttt");
			m = p.matcher(s);
			System.out.println(s);
		}
	}

	@Override
	public IAssembly installById(String id, StringBuffer sb) {
		if (!cache.containsKey(id)) {
			sb.append("\terror:在待安装区不存在指定的程序集id." + id);
			return null;
		}
		if (registry.isInstall(id)) {
			sb.append("\terror:已安装了程序集。id" + id);
			return registry.loadAssembly(id);
		}

		IAssembly a = cache.get(id);
		String newPos = "";
		File oldPos = new File(a.home() + File.separator + a.fileName());
		String dest = home + File.separator + "assemblies" + File.separator
				+ FileHelper.getFileNameNoEx(oldPos.getName());
		copyFile(oldPos, dest);
		newPos = dest + File.separator + oldPos.getName();
		cache.remove(id);
		IAssemblyInfo ai = Assembly.viewAssembly(newPos);
		a = (Assembly) ai;
		registry.install(a);
		sb.append("\r\n\tinstall ok:" + id);
		return a;
	}

	@Override
	public boolean isRunning(String aid) {
		String[] pids = pm.getProcIds(aid);
		return pids.length > 0 ? true : false;
	}

	private void verify(IAssembly a, String pid) {
		if (a.state() != AssemblyState.actived)
			a.start();
		ICollection<IGraph> col = a.workbin().part(IGraph.class);
		for (IGraph g : col) {
			if (!g.isInit()) {
				NetsiteAccessServiceProvider sp = new NetsiteAccessServiceProvider(
						home);
				Map<String, Object> map = new HashMap<>();
				map.put("netsite.access", sp);
				map.put("processid-in-netsite", pid);// 当前进程号
				g.initGraph(map);
			}
			if (g.protocolFactory() == null)
				throw new RuntimeException("缺少协议定义.assemblyId:"
						+ a.info().getGuid() + " assemblyName:"
						+ a.info().getName() + " at Graph:" + g.name());

		}
	}

	@Override
	public void modifyReg(String aid, String key, String v) {
		registry.modify(aid, key, v);
	}

	@Override
	public IAssembly getCached(String aid) {
		return cache.get(aid);
	}

	@Override
	public IAssembly findAssembly(String pid) {
		return pm.ls(pid);
	}

	@Override
	public Map<String, IAssembly> findAssemblyProcs(String id) {
		return pm.lsById(id);
	}

	private void copyFile(File src, String toDir) {
		InputStream inStream = null;
		FileOutputStream fs = null;
		try {
			int byteread = 0;
			inStream = new FileInputStream(src); // 读入原文件
			File f = new File(toDir);
			if (!f.exists()) {
				f.mkdirs();
			}
			fs = new FileOutputStream(toDir + File.separator + src.getName());
			byte[] buffer = new byte[1444];
			while ((byteread = inStream.read(buffer)) != -1) {
				fs.write(buffer, 0, byteread);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);

		} finally {
			try {
				if (inStream != null)
					inStream.close();
				if (fs != null)
					fs.close();
			} catch (IOException e) {

			}
		}
	}

	@Override
	public String[] enumCachedKey() {
		return cache.keySet().toArray(new String[0]);
	}

	@Override
	public void unInistall(String id) {
		String pids[] = pm.getProcIds(id);
		for (String pid : pids) {
			pm.kill(pid);
		}
		registry.uninstall(id);
	}

	// 查看进程
	@Override
	public Map<String, IAssembly> lsAssemblies() {
		return pm.lsall();
	}

	@Override
	public String run(String id, String pid) {
		return pm.startAssembly(id, pid);
	}

	@Override
	public void kill(String pid) {
		pm.kill(pid);
	}

	class Registry implements IRegistry {
		String fn = "registry.properties";
		Map<String, RegistryItem> map;// key=assembly id

		public Registry() {
			map = new HashMap<String, RegistryItem>();
		}

		@Override
		public String[] enumAssemblyId() {
			// TODO Auto-generated method stub
			return map.keySet().toArray(new String[0]);
		}

		@Override
		public void modify(String aid, String key, String v) {
			// TODO Auto-generated method stub
			if (!map.containsKey(aid)) {
				return;
			}
			RegistryItem i = map.get(aid);
			switch (key) {
			case "name":
				i.setName(v);
				break;
			case "version":
				i.setVersion(v);
				break;
			case "location":
				i.setLocation(v);
				break;
			case "desc":
				i.setDesc(v);
				break;
			}
			save();
		}

		@Override
		public void save() {
			Gson gson = new Gson();
			String json = gson.toJson(map);
			FileWriter w = null;
			try {
				w = new FileWriter(home + File.separator + fn);
				w.write(json);
			} catch (IOException e) {
				throw new RuntimeException(e);
			} finally {
				try {
					if (w != null)
						w.close();
				} catch (IOException e) {

				}
			}

		}

		@Override
		public void load() {
			FileReader w = null;
			try {
				File f = new File(home + File.separator + fn);
				if (!f.exists()) {
					f.createNewFile();
				}
				w = new FileReader(f);
				// Gson gson = new Gson();
				GsonBuilder gb = new GsonBuilder();
				gb.enableComplexMapKeySerialization();
				gb.setPrettyPrinting();
				Gson gson = gb.create();

				Map<String, RegistryItem> map = gson.fromJson(w,
						new TypeToken<HashMap<String, RegistryItem>>() {
						}.getType());
				this.map = map;
			} catch (IOException e) {
				throw new RuntimeException(e);
			} finally {
				try {
					if (w != null)
						w.close();
				} catch (IOException e) {

				}
			}

		}

		@Override
		public boolean isInstall(String id) {
			return map.containsKey(id);
		}

		@Override
		public void install(IAssembly a) {
			IAssemblyInfo info = (IAssemblyInfo) a;
			RegistryItem item = new RegistryItem();
			item.setDesc(info.getDescription());
			// item.setLocation(a.home());
			item.setLocation(".");// 默认都安装到默认位置，便于移植。调试时可通过mod命令修改为指定的位置
			item.setMainAssemblyFileName(a.fileName());
			item.setName(info.getName());
			item.setVersion(info.getVersion());
			map.put(info.getGuid(), item);
			save();
		}

		@Override
		public void uninstall(String id) {
			RegistryItem item = map.get(id);
			map.remove(id);
			save();
			if (item == null)
				return;
			String location = "";
			if (".".equals(item.getLocation())) {
				String dir = FileHelper
						.getFileNameNoEx(item.getMainAssemblyFileName());
				location = String.format("%s/assemblies/%s", home, dir);
			}
			File f = new File(location);
			if (f.exists()) {
				FileHelper.deleteDir(f);
			}
		}

		@Override
		public void empty() {
			map.clear();
			save();
		}

		@Override
		public void print(StringBuffer sb) {
			for (String id : map.keySet()) {
				RegistryItem i = map.get(id);
				sb.append("id:" + id);
				sb.append("name:" + i.getName());
				sb.append("version:" + i.getVersion());
				sb.append("location:" + i.getLocation());
				sb.append("assemblyFileName:" + i.getMainAssemblyFileName());
			}
		}

		public RegistryItem get(String aid) {
			return map.get(aid);
		}

		@Override
		public IAssembly loadAssembly(String id) {
			RegistryItem im = map.get(id);
			if (im == null)
				return null;
			try {
				String location = "";
				boolean defaultPath = false;
				if (".".equals(im.getLocation())) {
					defaultPath = true;
				} else {
					location = String.format("%s%s%s", im.getLocation(),
							File.separator, im.getMainAssemblyFileName());
					File f = new File(location);
					if (!f.exists()) {
						defaultPath = true;
					}
				}
				if (defaultPath) {// 使用程序集当前位置：netsitehome/assemblies/程序集文件名/
					location = String.format("%s/%s/%s/%s", home, "assemblies",
							FileHelper.getFileNameNoEx(
									im.getMainAssemblyFileName()),
							im.getMainAssemblyFileName());
					location = location.replace("/", File.separator)
							.replace("//", File.separator);
					File f = new File(location);
					if (!f.exists()) {
						throw new EcmException(
								String.format("程序集文件不存在:%s", location));
					}
				}

				return Assembly.loadAssembly(location);
			} catch (Exception e) {
				logger.error(e);
				throw e;
			}
		}

	}

	class ProcessManager implements IProcessManager {
		Map<String, IAssembly> map;// key=pid 一个程序集多个进程号，进程在管理器中是唯一的.
		String fn;

		public ProcessManager() {
			map = new HashMap<String, IAssembly>();

		}

		public void save() {
			Writer w = null;
			try {
				fn = home + File.separator + "proc";
				w = new FileWriter(fn);
				Properties props = new Properties();
				for (String pid : map.keySet()) {
					IAssembly a = map.get(pid);
					props.put(pid, a.info().getGuid());
				}
				props.store(w, "进程文件.");
			} catch (IOException e) {
				throw new RuntimeException(e);
			} finally {
				try {
					if (w != null)
						w.close();
				} catch (IOException e) {
				}
			}

		}

		private Properties load() {
			Reader r = null;
			try {
				fn = home + File.separator + "proc";
				File f = new File(fn);
				if (!f.exists())
					return new Properties();
				r = new FileReader(f);
				Properties props = new Properties();
				props.load(r);
				return props;
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			} catch (IOException e) {
				throw new RuntimeException(e);
			} finally {
				try {
					if (r != null)
						r.close();
				} catch (IOException e) {
				}
			}
		}

		@Override
		public void dispose() {
			save();
			killAll();
			map.clear();
		}

		@Override
		public void killAll() {
			String[] pids = map.keySet().toArray(new String[0]);
			for (String pid : pids) {
				kill(pid);
			}
		}

		public void loadAssembliesByReg() {
			map.clear();
			Properties props = load();
			for (Object pido : props.keySet()) {
				String pid = (String) pido;
				String id = props.getProperty(pid);
				if (!StringUtil.isEmpty(id))
					startAssembly(id, pid);
			}
		}

		@Override
		public void kill(String pid) {
			if (!map.containsKey(pid))
				return;
			IAssembly a = map.get(pid);
			netSite.notifyKillProc(pid, a);
			a.stop();
			map.remove(pid);
		}

		public String[] getProcIds(String id) {
			List<String> list = new ArrayList<String>();
			for (String k : map.keySet()) {
				IAssembly a = map.get(k);
				if (a.info().getGuid().equals(id)) {
					list.add(k);
				}
			}
			return list.toArray(new String[0]);
		}

		@Override
		public String startAssembly(String aid, String pid) {

			if (StringUtil.isEmpty(pid)) {
				RegistryItem ri = registry.get(aid);
				pid = ri.getName();
			}

			String[] pids = getProcIds(aid);
			String pidstr = "";
			boolean exists = map.containsKey(pid);
			for (String p : pids) {
				pidstr += p + ",";
			}
			if (exists) {
				throw new RuntimeException(
						String.format("-程序集%s已存在进程号%s", aid, pidstr));
			}

			IAssembly a = registry.loadAssembly(aid);
			if (a != null) {
				a.start();
				verify(a,pid);
				map.put(pid, a);
				netSite.notifyRunProc(pid, a);
				return pid;
			}
			return null;
		}

		@Override
		public Map<String, IAssembly> lsall() {
			return map;
		}

		@Override
		public Map<String, IAssembly> lsById(String id) {
			Map<String, IAssembly> ret = new HashMap<String, IAssembly>();
			String procs[] = getProcIds(id);
			for (String pid : procs) {
				IAssembly a = map.get(pid);
				if (a != null) {
					ret.put(pid, a);
				}
			}
			return ret;
		}

		@Override
		public IAssembly ls(String pid) {
			return map.get(pid);
		}

	}

}
