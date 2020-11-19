package covid;

import java.util.ArrayList;

/**
 * Final and cumulative numbers.
 * 
 * @author jdorje@gmail.com
 *
 */
public class FinalCases {

	private final ArrayList<Integer> cases = new ArrayList<>();

	public int getCasesInInterval(int day, int interval) {
		return getCases(day) - getCases(day - interval);
	}

	public int getDailyCases(int day) {
		return getCasesInInterval(day, 1);
	}

	public int getCases(int day) {
		if (day < 0 || day >= cases.size()) {
			return 0;
		}
		Integer caseCount = cases.get(day);
		if (caseCount == null) {
			return 0;
		}
		return caseCount;
	}

	public void setCases(int day, int numCases) {
		while (cases.size() <= day) {
			cases.add(0);
		}
		cases.set(day, numCases);
	}
}
