package library;

import java.awt.image.BufferedImage;
import java.util.LinkedList;
import java.util.concurrent.Future;
import java.util.function.Function;

import com.madgag.gif.fmsware.AnimatedGifEncoder;

/**
 * Collects frames, either finished images or {@link Future}s still rendering on
 * another thread, and writes them as one animated GIF through
 * {@link AnimatedGifEncoder}. Nothing on the live program's path uses it; it
 * survives from the Colorado-era animated charts.
 * <p>
 * Frames play in the reverse of the order they were added: the last frame added
 * is the first one written. Not thread-safe; add frames from one thread or
 * synchronize externally.
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
public class GifMaker {

	/*
	 * BufferedImages and Future<BufferedImage>s, used as a stack: push on add,
	 * pop on build, which is what reverses the order. Deliberate since 2efa6cc;
	 * the version before it played frames in the order added.
	 */
	private final LinkedList<Object> frames = new LinkedList<>();
	private final String fileName;
	private final Integer delay;
	private final Function<Integer, Integer> getDelay;

	/**
	 * @param fileName path of the GIF, used as given; ".gif" is appended unless
	 *                 it already ends with it
	 * @param delay    delay applied to every frame, in milliseconds; the encoder
	 *                 rounds it to hundredths of a second
	 */
	public GifMaker(String fileName, int delay) {
		this.fileName = fileName;
		this.delay = delay;
		getDelay = null;
	}

	/**
	 * @param fileName path of the GIF, as in the fixed-delay constructor
	 * @param getDelay given, for each frame, the number of frames still to be
	 *                 written after it, which is that frame's zero-based index in
	 *                 the order added, so the last frame played gets 0 (the hook
	 *                 for a long final hold); returns that frame's delay in
	 *                 milliseconds, which the encoder rounds to hundredths of a
	 *                 second
	 */
	public GifMaker(String fileName, Function<Integer, Integer> getDelay) {
		this.fileName = fileName;
		this.delay = null;
		this.getDelay = getDelay;
	}

	/**
	 * Writes the GIF, blocking on each {@link Future} frame in turn. Nothing
	 * checks that adding has finished, so call this only after the last
	 * {@code addFrame}. It drains the frames, so a second call overwrites the
	 * file with one that has no frames.
	 * <p>
	 * Failures never reach the caller. A {@code Future} that throws is skipped
	 * with a printed stack trace and the GIF goes on without it. If the file
	 * cannot be opened the encoder writes nothing and says so only through
	 * return values this ignores, so the name comes back all the same.
	 *
	 * @return the file name used, with ".gif" appended if it was missing, even
	 *         when nothing was written
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
