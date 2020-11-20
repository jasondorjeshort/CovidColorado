package covid;

import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.Future;

import com.madgag.gif.fmsware.AnimatedGifEncoder;

public class GifMaker {

	private final LinkedList<Future<BufferedImage>> frames = new LinkedList<>();
	private final String folder, fileName;
	private final int delay, finalDelay;

	public GifMaker(String folder, String fileName, int delay) {
		this(folder, fileName, delay, delay * 20);
	}

	public GifMaker(String folder, String fileName, int delay, int finalDelay) {
		this.folder = folder;
		this.fileName = fileName;
		this.delay = delay;
		this.finalDelay = finalDelay;
	}

	/**
	 * No thread checking is done directly, so it's necessary that this only be
	 * called after all the frames are added.
	 * 
	 * @return filename
	 */
	public String build() {
		AnimatedGifEncoder gif = new AnimatedGifEncoder();
		String name = folder + "\\" + fileName + ".gif";
		gif.start(name);
		gif.setDelay(delay);

		for (Iterator<Future<BufferedImage>> it = frames.iterator(); it.hasNext();) {
			Future<BufferedImage> fbi = it.next();
			if (!it.hasNext()) {
				gif.setDelay(finalDelay);
			}
			BufferedImage bufferedImage;
			try {
				bufferedImage = fbi.get();
			} catch (Exception e) {
				e.printStackTrace();
				continue;
			}
			gif.addFrame(bufferedImage);
		}
		gif.finish();
		return name;
	}

	public void addFrame(Future<BufferedImage> frame) {
		frames.add(frame);
	}
}
