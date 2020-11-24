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
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.ui.TextAnchor;

import com.madgag.gif.fmsware.AnimatedGifEncoder;

import covid.ColoradoStats;
import covid.Date;

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
public class Charts {
	public static final BasicStroke stroke = new BasicStroke(2);
	public static final Font font = new Font("normal", 0, 12);

	public static final int WIDTH = 800, HEIGHT = 600;

	// public static final String TOP_FOLDER = "H:\\CovidCoCharts";
	public static final String TOP_FOLDER = "C:\\Users\\jdorj\\Dropbox\\Public\\CovidCoCharts";

	public static ValueMarker getTodayMarker(int dayOfData) {
		ValueMarker marker = new ValueMarker(Date.dayToJavaDate(dayOfData).getTime());
		marker.setPaint(Color.black);
		marker.setLabel("Today");
		marker.setLabelFont(Charts.font);
		marker.setStroke(Charts.stroke);
		marker.setLabelTextAnchor(TextAnchor.TOP_CENTER);
		return marker;
	}

	public static void saveBufferedImageAsPNG(String folder, String name, BufferedImage bufferedImage) {

		new File(folder).mkdir();
		File file = new File(folder + "\\" + name + ".png");

		try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
			EncoderUtil.writeBufferedImage(bufferedImage, ImageFormat.PNG, out);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void setDelay(ColoradoStats stats, int dayOfData, AnimatedGifEncoder gif) {
		if (dayOfData == stats.getLastDay()) {
			gif.setDelay(5000);
		} else if (dayOfData + 10 >= stats.getLastDay()) {
			gif.setDelay(500);
		} else {
			gif.setDelay(50);
		}
	}
}
