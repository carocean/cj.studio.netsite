package cj.studio.ecm.chip.netsite.http;

import cj.studio.ecm.annotation.CjService;
import cj.studio.ecm.graph.GraphCreator;
import cj.studio.ecm.net.layer.ISessionEvent;
import cj.studio.ecm.net.layer.ISessionManager;
import cj.studio.ecm.net.web.WebSiteGraph;
import cj.studio.ecm.net.web.WebSiteGraphCreator;

@CjService(name = "netSiteDefaultHttpGraph")
public class NetSiteDefaultHttpGraph extends WebSiteGraph {
	@Override
	protected GraphCreator newCreator() {
		return new MySiteGraphCreator();
	}

	public class MySiteGraphCreator extends WebSiteGraphCreator {
		
		@Override
		protected ISessionManager createSessionManager() {
			ISessionManager sm=super.createSessionManager();
			sm.addEvent(new SessionManagerEvent());
			return sm;
		}
	}
	public class SessionManagerEvent implements ISessionEvent {

		@Override
		public void doEvent(String eventType, Object... args) {
//			System.out.println("netSiteDefaultHttpGraph :"+eventType+" "+((ISession)args[0]).id());
			
		}
		
	}
}
