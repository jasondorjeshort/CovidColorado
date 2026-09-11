package colorado;

import java.util.concurrent.TimeUnit;

import library.MyExecutor;
import nwss.Nwss;

/**
 * The program's entry point: {@code build.gradle} names this class as
 * {@code mainClass}, and {@link #main} is the whole of the live program's
 * control flow. It sits in {@code colorado/} only because that is where the
 * program started; the live code is under {@code nwss/}.
 *
 * {@link #old} is the entry point of the program's first life, the Colorado
 * case charts that make up the rest of this package. Nothing calls it.
 *
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
public class CovidColorado {

	public static void old(@SuppressWarnings("unused") String[] args) {
		long time = System.currentTimeMillis();
		ColoradoStats stats = new ColoradoStats();

		System.out.println("Read stats in " + (System.currentTimeMillis() - time) / 1000.0 + " s.");

		ChartMaker charts = new ChartMaker(stats);
		charts.buildCharts();

		MyExecutor.awaitTermination(1, TimeUnit.DAYS);
		MyExecutor.shutdown();

		System.out.println("Exiting in " + (System.currentTimeMillis() - time) / 1000.0 + " s.");
	}

	public static void main(String[] args) {
		long time = System.currentTimeMillis();

		try {
			Nwss nwss = new Nwss();
			nwss.read();
			nwss.build();
		} finally {
			/*
			 * The pool threads are non-daemon, so the JVM cannot exit until the
			 * pools are shut down; awaitTermination shuts them down first, then
			 * waits. On a run that got here normally both of Nwss's ASyncs have
			 * completed, so the pools are idle and this returns at once; the
			 * one-day timeout only means "no limit".
			 *
			 * The finally is what makes the other path terminate. An unchecked
			 * exception thrown on this thread after the first task was queued
			 * would otherwise skip the shutdown entirely, and the idle pool
			 * threads would hold the JVM up forever: the run would hang instead
			 * of failing. Shutting down here lets work already in flight finish
			 * and then lets the exception out of main.
			 */
			MyExecutor.awaitTermination(1, TimeUnit.DAYS);
		}

		// After the finally, not inside it: the line reports a run that
		// finished, and on the throwing path the stack trace is the outcome.
		System.out.println("Exiting in " + (System.currentTimeMillis() - time) / 1000.0 + " s.");
	}
}
