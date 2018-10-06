package cj.studio.ecm.netsite.start;

import java.io.File;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class NetsiteStartCommand implements ICommand{
		  File netsiteFile;
		@Override
		public void command(String cmd, String[] args) throws ParseException {
			
			GnuParser parser = new GnuParser();
			Options options = new Options();
			Option d = new Option("debug", "debug",true, "调试时使用。运行时主目录，\r\n即netsite包所在的目录");
			options.addOption(d);
			Option m = new Option("m", "help",false, "帮助，支持ac,sc,cc,gc命令。\r\nac:程序集及进程管理命令。\r\nsc:服务器管理命令。\r\ncc:客户端管理命令。\r\ngc:netgraph与chipgraph连接管理命令");
			options.addOption(m);
			CommandLine line = parser.parse(options, args);
			
			if(line.hasOption("m")){
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp( "netsite", options );
				System.exit(0);
				return ;
			}
			if(line.hasOption("debug")){
				netsiteFile=new File(line.getOptionValue("debug"));
			}
		}
		
	}