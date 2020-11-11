package CovidColorado;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import library.MyExecutor;

public class Main {

	public static void main(String[] args) {
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
	}
}
