package library;

import java.io.IOException;
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

	private static int opened = 0;

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
		if (opened > 10) {
			new Exception("Cannot open that many " + fileName).printStackTrace();
			return;
		}
		try {
			LinkedList<String> process = new LinkedList<>();
			process.add("C:\\Program Files (x86)\\IrfanView\\i_view32.exe");
			process.add(fileName);
			try {
				new ProcessBuilder(process).start();
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println("Opened " + fileName + ".");
			opened++;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
