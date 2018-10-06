package cj.studio.ecm.chip.netsite;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import cj.studio.ecm.IServiceProvider;
import cj.studio.ecm.ServiceCollection;
import cj.ultimate.gson2.com.google.gson.Gson;
import cj.ultimate.gson2.com.google.gson.GsonBuilder;
import cj.ultimate.gson2.com.google.gson.reflect.TypeToken;

class NetsiteAccessServiceProvider implements IServiceProvider {
	String home;
	public NetsiteAccessServiceProvider(String home) {
		this.home=home;
	}
	@Override
	public <T> ServiceCollection<T> getServices(Class<T> serviceClazz) {
		return new ServiceCollection<>();
	}

	@Override
	public Object getService(String serviceId) {
		if("$.netsite.registry".equals(serviceId)){
			return loadRegistry();
		}
		if("$.netsite.home".equals(serviceId)){
			return home;
		}
		return null;
	}
	public Map<String, Object> loadRegistry() {
		FileReader w = null;
		try {
			File f = new File(home + File.separator + "registry.properties");
			if (!f.exists()) {
				return new HashMap<>();
			}
			w = new FileReader(f);
			// Gson gson = new Gson();
			GsonBuilder gb = new GsonBuilder();
			gb.enableComplexMapKeySerialization();
			gb.setPrettyPrinting();
			Gson gson = gb.create();
			
			Map<String, Object> map = gson.fromJson(w,
					new TypeToken<HashMap<String, Object>>() {
					}.getType());
			return map;
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
}