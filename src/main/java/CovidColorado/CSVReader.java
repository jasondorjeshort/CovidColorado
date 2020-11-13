package CovidColorado;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * Trivial code to read a simple CSV file into an double-array of strings.
 */
public class CSVReader {

	// Most basic CSV reader. If there's quotes or escapes it will completely
	// fail.
	public static List<String[]> read(String csvFile) {
		BufferedReader br = null;
		String line;
		String cvsSplitBy = ",";

		List<String[]> csv = new LinkedList<>();

		try {

			br = new BufferedReader(new FileReader(csvFile));

			// System.out.println("Reading CSV: " + csvFile);
			while ((line = br.readLine()) != null) {

				// use comma as separator
				String[] split = line.split(cvsSplitBy);

				csv.add(split);
			}

		} catch (FileNotFoundException e) {
			return null;
		} catch (IOException e) {
			return null;
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
					return null;
				}
			}
		}

		return csv;
	}
}
