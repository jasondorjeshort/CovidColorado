package charts;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.jfree.chart.encoders.EncoderUtil;
import org.jfree.chart.encoders.ImageFormat;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.ui.TextAnchor;

import com.madgag.gif.fmsware.AnimatedGifEncoder;

import covid.CalendarUtils;
import covid.DailyTracker;

/**
 * What every chart shares: the stroke and font for marker lines and their
 * labels, the pixel size, the output folders, and the method every live chart
 * is written to disk through.
 * <p>
 * The rest is the first life's. {@code getTodayMarker}, {@code valueDesc},
 * {@code value}, {@code useMedian} and {@code setDelay} are called only from the
 * dead {@code colorado} package, and {@code getIncompleteMarker} and
 * {@code ratio} from nowhere; see
 * docs/ideas/2026-09-10-the-dead-colorado-package-holds-live-api-in-place.md.
 * <p>
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
public class Charts {
	public static final BasicStroke stroke = new BasicStroke(2);
	/*
	 * "normal" names no font, so Java substitutes Dialog. The size was 12 until
	 * the peak and valley markers, whose labels use it, were added.
	 */
	public static final Font font = new Font("normal", 0, 16);

	public static final int WIDTH = 1024, HEIGHT = 800;

	/*
	 * FULL_FOLDER and the folders in ChartSewage are derived from this once, at
	 * class initialization, which is why it is final: an assignment would move
	 * only the folders computed after it.
	 */
	public static final String TOP_FOLDER = "C:\\Users\\jdorj\\Downloads\\CovidCoCharts";
	public static final String FULL_FOLDER = TOP_FOLDER + "\\full";

	public static ValueMarker getTodayMarker(int dayOfData) {
		ValueMarker marker = new ValueMarker(CalendarUtils.dayToJavaDate(dayOfData).getTime());
		marker.setPaint(Color.black);
		marker.setLabel("Today");
		marker.setLabelFont(Charts.font);
		marker.setStroke(Charts.stroke);
		marker.setLabelTextAnchor(TextAnchor.TOP_CENTER);
		return marker;
	}

	/**
	 * Writes {@code bufferedImage} to {@code folder\name.png}, replacing any file
	 * already there. The build pool's threads call it concurrently, which is
	 * safe because it keeps no state.
	 * <p>
	 * {@code name} may carry backslash-separated subfolders, as a sewage chart's
	 * filename does. Only {@code folder} itself is created here, and not
	 * recursively, so those subfolders must already exist; ChartSewage mkdirs()
	 * and reportState() make them before anything is saved.
	 * <p>
	 * Two characters in {@code name} are cleaned, both for variant names: '|'
	 * becomes "or", and ':' is dropped. NTFS does not reject a ':': it writes
	 * the image to an alternate data stream of a file named for the part before
	 * it, which no folder listing shows. Every other character Windows
	 * rejects fails the write, which is why the caller that puts a lineage name
	 * in the file name strips its '*' first. The caller's own copy of the name
	 * is not cleaned, so a path rebuilt from it names no file if it held either
	 * character.
	 * <p>
	 * A failed write prints a stack trace and the name, and the caller is not
	 * told: an I/O failure is caught here and nothing is returned.
	 */
	public static void saveBufferedImageAsPNG(String folder, String name, BufferedImage bufferedImage) {

		new File(folder).mkdir();
		name = name.replaceAll(":", "");
		name = name.replaceAll("\\|", "or");
		File file = new File(folder + "\\" + name + ".png");

		try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
			EncoderUtil.writeBufferedImage(bufferedImage, ImageFormat.PNG, out);
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Fail on file '" + name + "'.");
		}
	}

	public static IntervalMarker getIncompleteMarker(int incompleteDay) {
		IntervalMarker marker = new IntervalMarker(CalendarUtils.dayToTime(incompleteDay), Double.MAX_VALUE);
		marker.setPaint(Color.black);
		// marker.setLabel("Incomplete");
		// marker.setLabelTextAnchor(TextAnchor.TOP_LEFT);
		marker.setStroke(Charts.stroke);
		marker.setAlpha(0.25f);
		marker.setLabelFont(Charts.font);
		marker.setLabelTextAnchor(TextAnchor.TOP_CENTER);
		return marker;
	}

	public static String valueDesc() {
		return "(Incomplete numbers - solid line indicates median value is within 5% of current one)";
	}

	// median expectation VERSUS current value
	public static double value(double current, double median) {
		if (Math.max(current / median, median / current) > 1 + .05) {
			return Double.NaN;
		}
		return median;
	}

	public static boolean useMedian() {
		return true;
	}

	public static void setDelay(DailyTracker stats, int dayOfData, AnimatedGifEncoder gif) {
		if (dayOfData == stats.getLastDay()) {
			gif.setDelay(1000);
		} else if (dayOfData + 10 >= stats.getLastDay()) {
			gif.setDelay(200);
		} else {
			gif.setDelay(50);
		}
	}

	public static double ratio(double v1, double v2) {
		if (!Double.isFinite(v1) || !Double.isFinite(v2)) {
			new Exception("This shouldn't happen!").printStackTrace();
			return 0;
		}
		if (v1 == 0 || v2 == 0) {
			return 0;
		}
		return Math.max(v1 / v2, v2 / v1);
	}
}
