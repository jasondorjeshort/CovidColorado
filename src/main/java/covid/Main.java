package covid;

import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import charts.ChartMaker;
import library.MyExecutor;

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

	public static void main(String[] args) {
		long time = System.currentTimeMillis();
		ColoradoStats stats = new ColoradoStats();

		System.out.println("Read stats in " + (System.currentTimeMillis() - time) + " ms.");

		ChartMaker charts = new ChartMaker(stats);
		charts.buildCharts();

		MyExecutor.awaitTermination(1, TimeUnit.DAYS);
		System.out.println("Built charts in " + (System.currentTimeMillis() - time) + " ms.");
	}
}
