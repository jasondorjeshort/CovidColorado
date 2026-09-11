package sewage;

import java.util.ArrayList;
import java.util.Comparator;

import nwss.DaySewage;

/**
 * An aggregate curve: the weighted mean of the normalized plants included into
 * it, each weighted by population and damped where its reporting starts and
 * stops (docs/reference/wastewater.txt, under AGGREGATION AND THE HIERARCHY).
 * Subclasses supply the name and the chart file; what the curve is, is decided
 * here.
 * <p>
 * The curve and the children are separate. {@link #includeSewage} builds the
 * curve, and {@code nwss/Nwss.java} read() includes each plant directly into
 * every aggregate it belongs to. {@link #addChild} only chooses the thinner
 * lines drawn on this aggregate's charts; nothing about a child feeds the
 * curve.
 * <p>
 * Every include happens in read(), on the main thread, before any chart is
 * drawn; the charts then read the aggregate from the pool. The first of them
 * runs build(), once, and nothing re-runs it, so an include after that would
 * be neither trimmed nor given inflections, and its includeDay could undo the
 * trim.
 */
public abstract class Multi extends Abstract {

	private int numPlants = 0;
	private final ArrayList<Abstract> children = new ArrayList<>(10);

	public Multi() {
		setPopulation(0);
	}

	/**
	 * Empties the curve for a new include pass: the entries, the plant count
	 * and the population. The day range stays, since {@code DailyTracker} has no
	 * reset; {@link All}'s passes include the same plants each time, so it is
	 * the same range.
	 */
	@Override
	public synchronized void clear() {
		super.clear();
		numPlants = 0;
		setPopulation(0);
	}

	/**
	 * How many plants {@link #includeSewage} has added since the last clear();
	 * the ones it skips are not counted.
	 */
	public synchronized int getNumPlants() {
		return numPlants;
	}

	@Override
	public String getTSName() {
		return String.format("%s (%,d plants, %,d pop)", getName(), getNumPlants(), getPopulation());
	}

	/**
	 * Trims the start of the curve. If the first day reads above the national
	 * peak, {@link All#SCALE_PEAK_RENORMALIZER}, first days are dropped while
	 * each reads higher than the day after, stopping at the first day that
	 * does not, or whose next day has no entry; once started, the descent is
	 * followed below the peak too. No commit says what it is for. What it meets
	 * is an aggregate's first days, which hold only the first plants to report,
	 * and a weighted mean of a single plant is its reading at any weight.
	 * <p>
	 * Only the range moves. The entries stay, so the averaged series
	 * makeTimeSeries draws still take the trimmed days into their first points.
	 */
	@Override
	protected void buildBackend() {
		if (getTotalSewage() <= 0) {
			return;
		}
		DaySewage first = getEntry(getFirstDay());
		if (first != null && first.getSewage() > All.SCALE_PEAK_RENORMALIZER) {
			while (true) {
				DaySewage today = getEntry(getFirstDay()), tomorrow = getEntry(getFirstDay() + 1);
				/* Sparse sampling means the next day may simply be missing. */
				if (today == null || tomorrow == null || today.getSewage() <= tomorrow.getSewage()) {
					break;
				}
				bumpFirstDay();
			}
		}
	}

	/**
	 * Adds a plant's normalized readings into this curve, weighted by its
	 * population times {@code popMultiplier} (a county gets 1/n of a plant that
	 * lists n counties) and damped at the edges of its reporting. A plant whose
	 * range is a day or less, or which has no population, is skipped: it adds
	 * nothing and is not counted. Every day the plant has an entry gets an
	 * accumulator here and widens this curve's range, even a day whose damped
	 * weight is 0.
	 * <p>
	 * Reads the plant's normalizer as it stands, which is why {@link All}
	 * clears and includes again after each change to them. Also raises the
	 * population, the "line pop", to the largest undamped population sum any
	 * day has reached.
	 */
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

			/*
			 * A reading of zero is averaged in at the weight its day carries,
			 * like any other value: this is an arithmetic mean, and a non-detect
			 * is the plant's lowest reading rather than a missing one. The two
			 * multipliers below are for a plant joining or leaving the pool,
			 * which a zero day is neither of. a38c69e restarted the fade-in at a
			 * zero as at a gap; ffc5cc6 disabled that the same day.
			 */
			DaySewage ds2 = getOrCreateMultiEntry(day);

			if (day > nextZero) {
				nextZero = sewage.getNextZero(day);
			}
			/*
			 * The fade-in over 182 days, half a year, since lastZero, the last
			 * day without an entry; the fade-out over the 14 days before
			 * nextZero. a38c69e added both with a 21-day fade-out, 765e7c0 cut
			 * it to 14, and neither says why, nor why the square.
			 *
			 * Every day without an entry counts, and most plants sample once or
			 * a few times a week, so most plant-days get (1/182)^2 * (1/14)^2,
			 * about 1.5E-7, and only plants sampling daily for months come near
			 * full weight; see
			 * docs/active/findings/2026-09-10-every-gap-between-samples-restarts-a-plants-fade-in.md.
			 */
			double startMultiplier = Math.min(Math.pow((day - lastZero) / 182.0, 2.0), 1.0);
			double endMultiplier = Math.min(Math.pow((nextZero - day) / 14.0, 2.0), 1.0);
			ds2.addDay(ds1, norm, pop * popMultiplier, startMultiplier * endMultiplier);

			int dayPop = (int) Math.round(ds2.getPop());
			synchronized (this) {
				setPopulation(Math.max(getPopulation(), dayPop));
			}
		}
	}

	/**
	 * Adds a line to this aggregate's charts; the curve is not touched. The
	 * list is sorted by population, largest first, as each child is added and
	 * never after, so a child's population must be final when it is added.
	 * Nwss read() holds to that: a plant's is fixed when it is read, and the
	 * aggregates are added as children only after every include. A child with
	 * no population, a plant whose population_served did not parse, sorts
	 * last.
	 */
	public void addChild(Abstract child) {
		synchronized (children) {
			children.add(child);
			/* Not Integer.compare: unboxing a null population would throw out of read(). */
			children.sort(
					Comparator.comparing(Abstract::getPopulation, Comparator.nullsLast(Comparator.reverseOrder())));
		}
	}

	/**
	 * A copy of the children in drawing order, most populous first: the first
	 * {@code maxChildren} of them, or all of them when it is null.
	 */
	public ArrayList<Abstract> getChildren(Integer maxChildren) {
		synchronized (children) {
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

}
