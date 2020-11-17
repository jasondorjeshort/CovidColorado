package covid;

import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import library.MyExecutor;

public class Main {

	public static void main(String[] args) {
		long time = System.currentTimeMillis();
		ColoradoStats stats = new ColoradoStats();

		System.out.println("Read stats in " + (System.currentTimeMillis() - time) + " ms.");

		ChartMaker charts = new ChartMaker();
		String fname = charts.buildCharts(stats);

		if (fname != null) {
			LinkedList<String> process = new LinkedList<>();
			process.add("C:\\Program Files (x86)\\IrfanView\\i_view32.exe");
			process.add(fname);
			try {
				new ProcessBuilder(process).start();
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println("Opened " + fname + ".");
		}

		MyExecutor.awaitTermination(1, TimeUnit.DAYS);

		System.out.println("Built charts in " + (System.currentTimeMillis() - time) + " ms.");
	}
}
