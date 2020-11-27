package library;

import java.awt.image.BufferedImage;
import java.util.LinkedList;
import java.util.concurrent.Future;
import java.util.function.Function;

import com.madgag.gif.fmsware.AnimatedGifEncoder;

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
public class GifMaker {

	private final LinkedList<Object> frames = new LinkedList<>();
	private final String fileName;
	private final Integer delay;
	private final Function<Integer, Integer> getDelay;

	public GifMaker(String fileName, int delay) {
		this.fileName = fileName;
		this.delay = delay;
		getDelay = null;
	}

	public GifMaker(String fileName, Function<Integer, Integer> getDelay) {
		this.fileName = fileName;
		this.delay = null;
		this.getDelay = getDelay;
	}

	/**
	 * No thread checking is done directly, so it's necessary that this only be
	 * called after all the frames are added.
	 * 
	 * @return filename
	 */
	public String build() {
		AnimatedGifEncoder gif = new AnimatedGifEncoder();
		String name = fileName;

		if (!name.endsWith(".gif")) {
			name = name + ".gif";
		}
		gif.start(name);

		if (delay != null) {
			gif.setDelay(delay);
		}

		while (frames.size() > 0) {
			Object frame = frames.pop();

			if (getDelay != null) {
				int size = frames.size();
				gif.setDelay(getDelay.apply(size));
			}
			BufferedImage bufferedImage;
			if (frame instanceof Future) {
				try {
					bufferedImage = ((Future<BufferedImage>) frame).get();
				} catch (Exception e) {
					e.printStackTrace();
					continue;
				}
			} else {
				bufferedImage = (BufferedImage) frame;
			}
			gif.addFrame(bufferedImage);
		}

		gif.finish();
		return name;

	}

	public void addFrame(Future<BufferedImage> frame) {
		frames.push(frame);
	}

	public void addFrame(BufferedImage frame) {
		frames.push(frame);
	}

	public void addFrameIf(boolean ifClause, Future<BufferedImage> frame) {
		if (ifClause) {
			addFrame(frame);
		}
	}
}
