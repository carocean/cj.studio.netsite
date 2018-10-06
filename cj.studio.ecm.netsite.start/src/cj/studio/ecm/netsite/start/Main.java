package cj.studio.ecm.netsite.start;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.commons.cli.ParseException;

import cj.studio.ecm.Assembly;
import cj.studio.ecm.IAssembly;
import cj.studio.ecm.adapter.IActuator;
import cj.studio.ecm.adapter.IAdaptable;
import cj.ultimate.util.StringUtil;

public class Main {

	private static File netsiteFile(File debugHome) throws IOException {
		String fileName = "cj.studio.ecm.chip.netsite";
		String usr = System.getProperty("user.dir");
		File f = new File(usr);
		File[] arr = f.listFiles(new FilenameFilter() {

			@Override
			public boolean accept(File dir, String name) {
				if (name.startsWith(fileName)) {
					return true;
				}
				return false;
			}
		});
		if (arr.length < 1) {
			File dir = null;
			if (debugHome != null) {
				dir = debugHome;
			} else {
				InputStreamReader input = new InputStreamReader(System.in);
				BufferedReader read = new BufferedReader(input);
				System.out.print("netsite home >");
				String line = read.readLine();
				dir = new File(line);
			}
			try {

				if (null == dir || !dir.exists()) {
					System.out.println("输入的netsite home不存在，退出...");
					return null;
				}
				File[] farr = dir.listFiles(new FilenameFilter() {

					@Override
					public boolean accept(File dir, String name) {
						if (name.startsWith(fileName)) {
							return true;
						}
						return false;
					}
				});
				if (farr.length < 1) {
					throw new IOException("netsite 芯片不存在." + fileName);
				}
				f = farr[0];

			} catch (Exception e) {
				throw new IOException("netsite 芯片不存在." + fileName);
			}
		} else {
			f = arr[0];
		}
		return f;
	}

	public static void main(String... args) throws IOException, ParseException {
		NetsiteStartCommand start = new NetsiteStartCommand();
		start.command(null, args);

		File f = netsiteFile(start.netsiteFile);
		if (f == null)
			return;

		IAssembly assembly = Assembly.loadAssembly(f.toString());
		assembly.start();

		InputStreamReader input = new InputStreamReader(System.in);
		BufferedReader read = new BufferedReader(input);
		Object main = assembly.workbin().part("main");
		IAdaptable a = (IAdaptable) main;
		IActuator act = a.getAdapter(IActuator.class);
		act.exeCommand("init", f.getParent());
		System.out.print("netsite >");
		while (true) {
			String line = read.readLine();
			if(StringUtil.isEmpty(line)){
				System.out.print("netsite >");
				continue;
			}
			
			String cmd = "";
			String[] lineArr = line.split(" ");
			String argsArr[] = new String[lineArr.length - 1];
			if (lineArr.length > 1)
				System.arraycopy(lineArr, 1, argsArr, 0, lineArr.length - 1);
			cmd = lineArr[0];
			
			act.exeCommand("main", cmd, argsArr);
			if ("exit".equals(line) || "bye".equals(line)
					|| "stop".equals(line)) {
				System.out.println("正在退出服务...");
				assembly.stop();
				break;
			}
		}
		try {
			Thread.sleep(3000);//如果3秒后还没退出，则强制
		} catch (InterruptedException e) {
			
		}finally{
			System.exit(0);
		}
	}

}
