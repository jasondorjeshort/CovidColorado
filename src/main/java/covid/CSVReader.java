package covid;

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
		String line;
		String csvSplitBy = ",";

		List<String[]> csv = new LinkedList<>();

		try (FileReader fr = new FileReader(csvFile); BufferedReader br = new BufferedReader(fr)) {

			// System.out.println("Reading CSV: " + csvFile);
			while ((line = br.readLine()) != null) {

				// use comma as separator
				String[] split = line.split(csvSplitBy);

				csv.add(split);
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}

		return csv;
	}
}
