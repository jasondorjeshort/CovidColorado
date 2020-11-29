package main;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import charts.ChartMaker;
import covid.ColoradoStats;
import library.MyExecutor;
import web.CovidServer;

/**
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 * 
 * @author jdorje@gmail.com
 */
public class Main {

	static CovidServer server;

	public static void main(String[] args) {
		long time = System.currentTimeMillis();
		ColoradoStats stats = new ColoradoStats();

		System.out.println("Read stats in " + (System.currentTimeMillis() - time) + " ms.");

		ChartMaker charts = new ChartMaker(stats);
		charts.buildCharts();

		if (false) {
			try {
				server = new CovidServer();
			} catch (IOException e) {
				e.printStackTrace();
				MyExecutor.awaitTermination(1, TimeUnit.DAYS);
				System.exit(0);
			}
		} else {
			MyExecutor.awaitTermination(1, TimeUnit.DAYS);
		}

		System.out.println("Exiting in " + (System.currentTimeMillis() - time) + " ms.");
	}
}
