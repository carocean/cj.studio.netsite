package cj.studio.ecm.netsite.start;

import org.apache.commons.cli.ParseException;

public interface ICommand {
	void command(String cmd,String[] args)throws ParseException ;
}
