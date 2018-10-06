package cj.studio.ecm.chip.netsite;

import cj.studio.ecm.annotation.CjService;
import cj.studio.ecm.frame.Circuit;
import cj.studio.ecm.frame.Frame;
import cj.studio.ecm.graph.CircuitException;
import cj.studio.ecm.graph.IPlug;
import cj.studio.ecm.graph.ISink;
@CjService(name="commandSink")
public class CommandSink implements ISink{

	@Override
	public void flow(Frame frame,Circuit circuit, IPlug plug) throws CircuitException {
		// TODO Auto-generated method stub
		
	}

}
