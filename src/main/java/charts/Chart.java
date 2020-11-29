package charts;

import java.awt.image.BufferedImage;

public class Chart {
	public String fileName;
	public BufferedImage image;

	public Chart(String fileName, BufferedImage image) {
		this.fileName = fileName;
		this.image = image;
	}
}
