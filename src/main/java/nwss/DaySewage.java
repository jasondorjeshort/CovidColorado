package nwss;

import java.util.Objects;

/**
 * One day of one sewage series; {@code sewage/Abstract.java} keeps them by day.
 * Each entry is one of two kinds, fixed at construction by whether
 * {@code effPop} is null.
 *
 * A plant reading, made by {@code addEntry} for a {@code sewage/Plant.java},
 * holds the value in the plant's own units; callers apply the plant's
 * normalizer.
 *
 * An aggregate accumulator, made by {@code getOrCreateMultiEntry} for a
 * {@code sewage/Multi.java}, is filled only through {@link #addDay} and reads
 * as the population-weighted mean of the normalized plant readings added.
 *
 * addDay and getSewage lock the entry they touch and never hold two locks at
 * once. getPop does not lock, and nothing needs it to today: every aggregate
 * is filled on the main thread, in {@code read()} in {@code nwss/Nwss.java}.
 */
public class DaySewage {
	/*
	 * A plant reading: sewageTot is the value and both populations are null. An
	 * accumulator: sewageTot is the sum of weighted normalized readings, effPop
	 * the sum of the weights (the divisor), realPop the undamped population sum.
	 */
	private double sewageTot;
	private Double effPop;
	private Double realPop;

	/** A plant reading of {@code sewage}, in the plant's own units. */
	public DaySewage(double sewage) {
		this.sewageTot = sewage;
		this.effPop = this.realPop = null;
	}

	/** An empty aggregate accumulator, to be filled through {@link #addDay}. */
	public DaySewage() {
		this.sewageTot = 0.0;
		this.effPop = this.realPop = 0.0;
	}

	/**
	 * Null for a plant reading; for an accumulator, the undamped population of
	 * the plants added. {@code Multi} reports the largest of these over all days
	 * as the aggregate's population.
	 */
	public Double getPop() {
		return realPop;
	}

	/**
	 * Adds plant reading {@code day}, scaled by that plant's {@code normalizer},
	 * with weight {@code dayPop * weighting}, where {@code weighting} is Multi's
	 * fade-in/fade-out multiplier in [0, 1]; {@code dayPop} goes undamped into
	 * {@link #getPop}.
	 *
	 * Must be called on an accumulator: on a plant reading the null populations
	 * throw NullPointerException. {@code day} must be a plant reading: given an
	 * accumulator it prints an "Uh oh." stack trace and adds that entry's
	 * weighted total as if it were a value, a wrong number rather than a failure.
	 */
	public void addDay(DaySewage day, double normalizer, double dayPop, double weighting) {
		double daySewage;
		synchronized (day) {
			if (day.effPop != null) {
				new Exception("Uh oh.").printStackTrace();
			}
			daySewage = day.sewageTot;
		}
		daySewage *= normalizer;

		double ePop = dayPop * weighting;
		synchronized (this) {
			sewageTot += ePop * daySewage;
			effPop += ePop;
			realPop += dayPop;
		}
	}

	/**
	 * The value, for a plant reading; the weighted mean of the readings added,
	 * for an accumulator. An accumulator whose every contribution had zero
	 * weight returns 1.
	 */
	public double getSewage() {
		synchronized (this) {
			// Zero total weight: every plant contributing that day had population 0
			// or read exactly zero, which Multi weights out because getNextZero
			// counts a zero as the series' end (see
			// docs/active/findings/2026-09-10-a-zero-reading-is-weighted-out-of-every-aggregate.md).
			// The result must be positive, not 0: makeFitSeries takes its log
			// (VocSewage's division by it skips a zero reading first), and every
			// other accumulator day is positive
			// since only positive readings carry weight. The 1 has no recorded
			// derivation; on All's renormalized axis (peak 100) it is 1% of the
			// pandemic peak. It replaced a return of 0 that never ran: from
			// 6c3d500 to a38c69e the check compared a Double with Integer 0, so
			// these days were NaN.
			if (Objects.equals(effPop, 0.0)) {
				return 1;
			}
			return effPop == null ? sewageTot : sewageTot / effPop;
		}
	}
}
