package cj.studio.ecm.chip.netsite;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.Parser;

import cj.studio.ecm.graph.IResult;

public abstract class AbstractCommand implements ICommand{
	protected Options options;
	protected Parser parser;
	public AbstractCommand() {
		options=createOptions();
		parser=createParser();
	}
	protected abstract Options createOptions();
	protected abstract Parser createParser();
	@Override
	public IResult command(String cmd, String[] args) throws ParseException {
		if(!cognize(cmd))return new Result(504,String.format("不认识的命令.%s",cmd));
		CommandLine line = parser.parse(options, args);
		return command(cmd,line);
	}
	protected abstract IResult command(String cmd,CommandLine line)throws ParseException;
	protected abstract boolean cognize(String cmd);
	class Result implements IResult{
		int state;
		 String message;
		 Object value;
		public Result() {
			// TODO Auto-generated constructor stub
		}
		public Result(int i, String format) {
			state=i;
			message=format;
		}
		@Override
		public int state() {
			// TODO Auto-generated method stub
			return state;
		}

		@Override
		public String message() {
			// TODO Auto-generated method stub
			return message;
		}

		@Override
		public Object value() {
			// TODO Auto-generated method stub
			return value;
		}
		
	}
}
