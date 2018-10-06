package cj.studio.ecm.chip.netsite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
/**
 * 表示一个chip graph 到net 的连接点信息。
 * <pre>
 *
 * </pre>
 * @author carocean
 *
 */
public class Junction {
	private String graphProtocol;
	private Map<String, List<RuleBranchKey>> inputs; // inputName<-key[net(name),flow,rewrite]
	private Map<String, List<RuleBranchKey>> outputs;
	private String chipGraphname;

	public Junction() {
		inputs = new HashMap<String, List<RuleBranchKey>>(2);
		outputs = new HashMap<String, List<RuleBranchKey>>(2);
	}

	public String[] enumInput(){
		return inputs.keySet().toArray(new String[0]);
		
	}
	public String[] enumOutput(){
		return outputs.keySet().toArray(new String[0]);
		
	}
	public void setGraphProtocol(String graphProtocol) {
		this.graphProtocol = graphProtocol;
	}

	public String getGraphProtocol() {
		return graphProtocol;
	}
	public void setChipGraphname(String chipGraphname) {
		this.chipGraphname = chipGraphname;
	}
	public boolean appendInput(String in, RuleBranchKey key) {
		List<RuleBranchKey> list=null;
		if(!inputs.containsKey(in)){
			list=new ArrayList<RuleBranchKey>();
			inputs.put(in, list);
		}else{
			list=inputs.get(in);
		}
		if(!list.contains(key)){
			list.add(key);
			return true;
		}
		return false;
	}
	public boolean appendOutput(String out, RuleBranchKey key) {
		List<RuleBranchKey> list=null;
		if(!outputs.containsKey(out)){
			list=new ArrayList<RuleBranchKey>();
			outputs.put(out, list);
		}else{
			list=outputs.get(out);
		}
		if(!list.contains(key)){
			list.add(key);
			return true;
		}
		return false;
	}
	public void removeOutput(String out){
		outputs.remove(out);
	}
	public void removeInput(String in){
		inputs.remove(in);
	}
	public Iterator<String> itInput(){
		return inputs.keySet().iterator();
	}
	public List<RuleBranchKey> getInputList(String in){
		return inputs.get(in);
	}
	public Iterator<String> itoutput(){
		return outputs.keySet().iterator();
	}
	public List<RuleBranchKey> getOutputList(String out){
		return outputs.get(out);
	}
	public boolean containsInput(String in){
		return inputs.containsKey(in);
	}
	public boolean containsOutput(String out){
		return outputs.containsKey(out);
	}
	public String getChipGraphname() {
		return chipGraphname;
	}
	public void setChipGraphName(String name) {
		// TODO Auto-generated method stub
		this.chipGraphname=name;
	}

	public void removeOutputConnect(String chipGraphPin, RuleBranchKey key) {
		List<RuleBranchKey> list=outputs.get(chipGraphPin);
		if(list==null||list.isEmpty()){
			return;
		}
		list.remove(key);
		
	}
	public void removeInputConnect(String chipGraphPin, RuleBranchKey key) {
		List<RuleBranchKey> list=inputs.get(chipGraphPin);
		if(list==null||list.isEmpty()){
			return;
		}
		list.remove(key);
		
	}

}