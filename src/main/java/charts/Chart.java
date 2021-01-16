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

	public Chart(BufferedImage image, String... fileNames) {
		for (String fileName : fileNames) {
			this.fileNames.add(fileName);
		}
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
	}

	public String getFileName() {
		for (String fileName : fileNames) {
			return fileName;
		}
		return null;
	}

	public void open() {
		for (String fileName : fileNames) {
			library.OpenImage.openImage(fileName);
			return;
		}
	}

	public BufferedImage getImage() {
		return image;
	}
}
