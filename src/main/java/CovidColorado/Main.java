package CovidColorado;

import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import library.MyExecutor;

public class Main {

	public static void main(String[] args) {
		long time = System.currentTimeMillis();
		CovidStats stats = new CovidStats();
		ChartMaker charts = new ChartMaker();
		String fname = charts.buildCharts(stats);

		LinkedList<String> process = new LinkedList<>();
		process.add("C:\\Program Files (x86)\\IrfanView\\i_view32.exe");
		process.add(fname);
		try {
			new ProcessBuilder(process).start();
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("Opened " + fname + ".");

		MyExecutor.awaitTermination(1, TimeUnit.DAYS);

		time = System.currentTimeMillis() - time;
		System.out.println("Built charts in " + time + " ms.");
	}
}
