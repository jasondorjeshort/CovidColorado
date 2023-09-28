package sewage;

import java.util.ArrayList;

import nwss.DaySewage;

public abstract class Multi extends Abstract {

	private int numPlants = 0;
	private final ArrayList<Abstract> children = new ArrayList<>(10);

	public Multi() {
		setPopulation(0);
	}

	@Override
	public synchronized void clear() {
		super.clear();
		numPlants = 0;
	}

	public synchronized int getNumPlants() {
		return numPlants;
	}

	@Override
	public String getTSName() {
		return String.format("%s (%,d plants, %,d pop)", getName(), getNumPlants(), getPopulation());
	}

	@Override
	protected void fixStarting() {
		if (getFirstDay() >= getLastDay()) {
			// There's empty counties somehow?
			return;
		}
		try {
			if (getEntry(getFirstDay()).getSewage() > 100) {
				while (getEntry(getFirstDay()).getSewage() > getEntry(getFirstDay() + 1).getSewage()) {
					bumpFirstDay();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(
					"Error on " + getClass() + " for " + getName() + " as " + getFirstDay() + " to " + getLastDay());
		}
	}

	public void includeSewage(Plant sewage, double popMultiplier) {
		if (sewage.numDays() <= 1) {
			return;
		}
		Integer pop = sewage.getPopulation();
		if (pop == null) {
			// new Exception("Uhhh no pop on " + sewage.id).printStackTrace();
			return;
		}
		synchronized (this) {
			numPlants++;
		}
		int sFirstDay = sewage.getFirstDay(), sLastDay = sewage.getLastDay();
		int lastZero = sFirstDay - 1, nextZero = sewage.getNextZero(sFirstDay);
		double norm = sewage.getNormalizer();
		for (int day = sFirstDay; day <= sLastDay; day++) {
			DaySewage ds1 = sewage.getEntry(day);
			if (ds1 == null) {
				lastZero = day;
				continue;
			}

			if (ds1.getSewage() == 0.0) {
				// lastZero = day;
			}
			DaySewage ds2 = getOrCreateMultiEntry(day);

			if (day > nextZero) {
				nextZero = sewage.getNextZero(day);
			}
			double startMultiplier = Math.min(Math.pow((day - lastZero) / 182.0, 2.0), 1.0);
			double endMultiplier = Math.min(Math.pow((nextZero - day) / 14.0, 2.0), 1.0);
			ds2.addDay(ds1, norm, pop * popMultiplier, startMultiplier * endMultiplier);

			int dayPop = (int) Math.round(ds2.getPop());
			synchronized (this) {
				setPopulation(Math.max(getPopulation(), dayPop));
			}
		}
	}

	public void addChild(Abstract child) {
		synchronized (children) {
			children.add(child);
			children.sort((c1, c2) -> -Integer.compare(c1.getPopulation(), c2.getPopulation()));
		}
	}

	public ArrayList<Abstract> getChildren(Integer maxChildren) {
		if (maxChildren == null) {
			return new ArrayList<>(children);
		}
		ArrayList<Abstract> c = new ArrayList<>(maxChildren);
		for (int i = 0; i < maxChildren && i < children.size(); i++) {
			c.add(children.get(i));
		}
		return c;
	}

}
