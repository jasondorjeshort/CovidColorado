package library;

import java.util.LinkedList;

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
public class OpenImage {

	private static final LinkedList<String> files = new LinkedList<>();

	private static int num = 0;

	/**
	 * This just opens the given file name (with extension) in irfanview.
	 * Irfanview must be installed at the location hard-coded here.
	 * 
	 * @param fileName
	 *            File name
	 */
	public static synchronized void openImage(String fileName) {
		if (fileName == null) {
			return;
		}
		if (num > 10) {
			return;
		}
		num++;
		files.add(fileName);
	}

	public static synchronized void open() {
		LinkedList<String> process = new LinkedList<>();
		while (files.size() > 0) {
			String fileName = files.pop();
			process.clear();
			process.add("C:\\Program Files (x86)\\IrfanView\\i_view32.exe");
			process.add(fileName);
			System.out.println("Opened " + fileName + ".");

			try {
				new ProcessBuilder(process).start();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
