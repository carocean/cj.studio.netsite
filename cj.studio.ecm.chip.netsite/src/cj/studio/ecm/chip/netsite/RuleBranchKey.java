package cj.studio.ecm.chip.netsite;

import cj.studio.ecm.graph.IBranchKey;
import cj.ultimate.util.StringUtil;

public class RuleBranchKey implements IBranchKey {
		String keyNetName;
		String keyPid;
		String flow;
		String rewrite;
		String to;
		String accept;
		public RuleBranchKey() {
			// TODO Auto-generated constructor stub
		}
		public RuleBranchKey(String netName,String pid){
			this.keyNetName = netName;
			this.keyPid=pid;
		}
		public RuleBranchKey(String netName,String pid, String flow, String rewrite,String to,String accept) {
			this.keyNetName = netName;
			this.flow = flow;
			this.rewrite = rewrite;
			this.accept=accept;
			this.to=to;
			this.keyPid=pid;
		}
public String getPid() {
	return keyPid;
}
		public String getFlow() {
			return flow;
		}
		@Override
		public boolean equals(Object obj) {
			RuleBranchKey k=(RuleBranchKey)obj;
			return keyNetName.equals(k.keyNetName)&&keyPid.equals(k.keyPid);
		}
		public String getRewrite() {
			return rewrite;
		}

		public void setFlow(String flow) {
			this.flow = flow;
		}
		public String getTo() {
			return to;
		}
		public void setTo(String to) {
			this.to = to;
		}
		public void setRewrite(String rewrite) {
			this.rewrite = rewrite;
		}
		/**
		 * 此为netName
		 */
		@Override
		public String key() {
			// TODO Auto-generated method stub
			return keyNetName;
		}
		public String getAccept() {
			return accept;
		}
		public void setAccept(String accept) {
			this.accept = accept;
		}
		//在一个端子的调度插头上，只充许一个net连接一个进程
		@Override
		public int hashCode() {
			if(StringUtil.isEmpty(keyNetName)||StringUtil.isEmpty(keyPid)){
				return super.hashCode();
			}
			return String.format("%s-%s", keyPid,keyNetName).hashCode();
		}
	}