package charts;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;

import org.jfree.chart.encoders.EncoderUtil;
import org.jfree.chart.encoders.ImageFormat;

/**
 * A rendered chart image, the file paths it is saved under, and the day it
 * depicts. It belongs to the program's first life: the only code that makes
 * one is in the dead {@code colorado/} package, where one image could be saved
 * under both a dated and a stable name and the last 21 days of images fed a
 * GIF.
 *
 * The live program uses none of its behaviour. {@code nwss/Nwss.java} and
 * {@code charts/ChartSewage.java} name it only as the type argument of
 * {@code ASync<Chart>}, and every task they queue goes through
 * {@code execute(Runnable)}, so no Chart is ever made; live charts are written
 * by {@code saveBufferedImageAsPNG} in {@code charts/Charts.java}. That type
 * argument is why the class moved from {@code colorado/} to {@code charts/}
 * in e240358. New chart code should not use it.
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
public class Chart {
	private final HashSet<String> fileNames = new HashSet<>();
	private BufferedImage image;
	private boolean openQueued;
	private boolean saved;
	public final int dayOfChart;

	public Chart(BufferedImage image, int dayOfChart, String... fileNames) {
		for (String fileName : fileNames) {
			this.fileNames.add(fileName);
		}
		this.dayOfChart = dayOfChart;
		this.image = image;
	}

	public void addFileName(String fileName) {
		fileNames.add(fileName);
	}

	public void saveAsPNG() {
		for (String fileName : fileNames) {
			File file = new File(fileName);
			try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
				EncoderUtil.writeBufferedImage(image, ImageFormat.PNG, out);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		saved = true;
		if (openQueued) {
			open();
			openQueued = false;
		}
	}

	public String getFileName() {
		for (String fileName : fileNames) {
			return fileName;
		}
		return null;
	}

	public void open() {
		if (!saved) {
			openQueued = true;
			return;
		}
		String fName = null;
		// Arbitrary trick/hack : open the longest file name since it's probably
		// the most specific
		for (String fileName : fileNames) {
			if (fName == null || fName.length() < fileName.length()) {
				fName = fileName;
			}
		}
		library.OpenImage.openImage(fName);
	}

	public BufferedImage getImage() {
		return image;
	}
}
