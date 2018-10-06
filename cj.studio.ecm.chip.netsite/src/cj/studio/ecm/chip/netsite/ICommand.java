package cj.studio.ecm.chip.netsite;

import org.apache.commons.cli.ParseException;

import cj.studio.ecm.graph.IResult;
import cj.ultimate.IDisposable;

public interface ICommand extends IDisposable {
	void init(String home);
	IResult command(String cmd,String[] args)throws ParseException ;
}
