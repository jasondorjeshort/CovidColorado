/* ===========================================================
 * JFreeChart : a free chart library for the Java(tm) platform
 * ===========================================================
 *
 * (C) Copyright 2000-present, by David Gilbert and Contributors.
 *
 * Project Info:  http://www.jfree.org/jfreechart/index.html
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * [Oracle and Java are registered trademarks of Oracle and/or its affiliates. 
 * Other names may be trademarks of their respective owners.]
 *
 * --------------------
 * LogarithmicAxis.java
 * --------------------
 * (C) Copyright 2000-present, by David Gilbert and Contributors.
 *
 * Original Author:  Michael Duffy / Eric Thomas;
 * Contributor(s):   David Gilbert;
 *                   David M. O'Donnell;
 *                   Scott Sams;
 *                   Sergei Ivanov;
 *
 */

package myjfreechart;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;

import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTick;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.ValueAxisPlot;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.Range;

/**
 * A vertical axis on the logit scale: a value v in (0, peak) is drawn at
 * log10(v / (peak - v)), the base-10 log-odds of v as a share of peak. Both ends
 * are stretched, so 1% and 0.1% sit as far apart as 99% and 99.9%, and a share
 * that grows or shrinks logistically -- one lineage displacing another at a
 * steady relative growth rate -- is a straight line.
 * <p>
 * The one caller is charts/ChartSewage.java buildRelative, the relative lineage
 * charts, with a peak of 100 so values and tick labels are percentages. It lets
 * autoAdjustRange pick the upper bound and then sets the lower bound itself.
 * <p>
 * The range must lie strictly inside (0, peak). logit is infinite at either end
 * and NaN beyond them, which is why VocSewage addRelative drops any point at or
 * outside them. Inside, refreshTicksVertical makes nine ticks per decade toward
 * either end, a few thousand at the very worst. Past an end its tick count grows
 * with the distance in multiples of peak, so a runaway bound allocates ticks
 * until the heap is gone -- the failure LogarithmicAxis has on the absolute
 * charts -- and an upper bound past about 2E9 times peak never ends the loop.
 * <p>
 * Adapted from JFreeChart 1.5.4's LogarithmicAxis, whose licence header this
 * file keeps. Only the vertical ticks, the bounds and the value mapping were
 * rewritten for logit; refreshTicksHorizontal was not.
 */
public class LogitAxis extends NumberAxis {

	/** For serialization. */
	private static final long serialVersionUID = -6645500998164790971L;

	/** Useful constant for log(10). */
	public static final double LOG10 = Math.log(10.0);

	/**
	 * Tolerance below the lower bound for keeping a tick, from LogarithmicAxis.
	 * Lost to rounding at any lower bound above about 1E-84, so against the
	 * bounds this program sets the comparison is exact.
	 */
	public static final double SMALL_LOG_VALUE = 1e-100;

	/** Number formatter for generating numeric strings. */
	private final NumberFormat numberFormatterObj = NumberFormat.getInstance();

	private final double peak;

	private double logit(double value) {
		return Math.log(value / (peak - value)) / LOG10;
	}

	/*
	 * v = log(x / (p-x))
	 * 
	 * 10^v (p-x) = x
	 * 
	 * 10^v p = x + 10^v x
	 * 
	 * x = p 10^v / (10^v + 1)
	 * 
	 */
	private double unlogit(double logit) {
		logit = Math.pow(10, logit);
		return peak * logit / (logit + 1);
	}

	/**
	 * Creates a new axis.
	 *
	 * @param label
	 *            the axis label.
	 * @param peak
	 *            the value the top of the scale approaches and never reaches,
	 *            in the data's own units: 100 for percentages. Exactly 100
	 *            also puts a "%" on the tick labels.
	 */
	public LogitAxis(String label, double peak) {
		super(label);
		this.peak = peak;
		setupNumberFmtObj(); // setup number formatter obj
	}

	/** Creates an axis for fractions, with a peak of 1. */
	public LogitAxis(String label) {
		this(label, 1.0);
	}

	/**
	 * Sets tick labels to plain decimals with at most three places, enough for
	 * 99.9% and 0.001%.
	 */
	protected void setupNumberFmtObj() {
		if (this.numberFormatterObj instanceof DecimalFormat) {
			((DecimalFormat) this.numberFormatterObj).applyPattern("0.###");
		}
	}

	/**
	 * Rounds a lower bound down to a tick the scale treats as round: at or
	 * below peak/2, the power of ten at or below it; above peak/2, the mirror
	 * image, peak less the computeLogitCeil of its distance from peak. The power
	 * of ten is of the raw value, not of its share of peak; the two agree only
	 * when peak is itself a power of ten.
	 * <p>
	 * A value at or below zero has no floor on this scale: it prints a stack
	 * trace and comes back floored to an integer, which the axis cannot draw.
	 *
	 * @param lower
	 *            the smallest data value.
	 *
	 * @return the rounded lower bound.
	 */
	protected double computeLogitFloor(double lower) {
		if (lower > peak / 2) {
			return peak - computeLogitCeil(peak - lower);
		}
		if (lower > 0.0) {
			lower = Math.log(lower) / LOG10;
			lower = Math.floor(lower);
			lower = Math.pow(10, lower);
		} else {
			new Exception("Illegal floor of " + lower + " cannot be below zero.").printStackTrace();
			lower = Math.floor(lower);
		}
		return lower;
	}

	/**
	 * Rounds an upper bound up to a tick the scale treats as round: above
	 * peak/2, the mirror image, peak less the computeLogitFloor of its distance
	 * from peak; above peak/10, peak/2 itself; at or below peak/10, meant to be
	 * the power of ten at or above it, mirroring computeLogitFloor. A value at
	 * or below zero comes back rounded up to an integer, which the axis cannot
	 * draw.
	 *
	 * @param upper
	 *            the largest data value.
	 *
	 * @return the rounded upper bound.
	 */
	protected double computeLogitCeil(double upper) {
		if (upper > peak / 2) {
			return peak - computeLogitFloor(peak - upper);
		}
		if (upper > peak / 10) {
			return peak / 2;
		}

		if (upper > 0.0) {
			upper = Math.log(upper) / LOG10;
			upper = Math.ceil(upper);
			/*
			 * Not the power of ten the rest of this method means: unlogit of a
			 * log10 exponent. With a peak of 100, anything in (1, 10] comes back
			 * as 90.9 and anything in (0.1, 1] as 50, and a lower bound of 90 or
			 * more, which computeLogitFloor mirrors through here, comes out far
			 * too low. LogarithmicAxis has Math.pow(10, upper). Recorded as a
			 * finding rather than fixed, since today's charts should not reach
			 * it: each plots about ten lineages sharing every day's 100%, so its
			 * largest point is over 10%, and the caller replaces the lower bound.
			 */
			upper = unlogit(upper);
		} else {
			upper = Math.ceil(upper);
		}
		return upper;
	}

	/**
	 * Rescales the axis to ensure that all data is visible, rounding each end
	 * with computeLogitFloor and computeLogitCeil. With no data the range is 1%
	 * to 99% of peak.
	 */
	@Override
	public void autoAdjustRange() {

		Plot plot = getPlot();
		if (plot == null) {
			return; // no plot, no data.
		}

		if (plot instanceof ValueAxisPlot) {
			ValueAxisPlot vap = (ValueAxisPlot) plot;

			double lower, upper;
			Range r = vap.getDataRange(this);
			if (r == null) {
				// no real data present
				lower = peak * 0.01;
				upper = peak * 0.99;
			} else {
				// actual data is present
				lower = r.getLowerBound();
				lower = computeLogitFloor(lower);
				upper = r.getUpperBound();
				upper = computeLogitCeil(upper);
			}

			setRange(new Range(lower, upper), false, false);
		} else {
			new Exception("Unknown plot type " + plot.getClass().getName()).printStackTrace();
		}
	}

	/**
	 * Converts a data value to a coordinate in Java2D space, assuming that the
	 * axis runs along one edge of the specified plotArea. Note that it is
	 * possible for the coordinate to fall outside the plotArea, and that a
	 * value at or outside 0 and peak comes back infinite or NaN.
	 *
	 * @param value
	 *            the data value.
	 * @param plotArea
	 *            the area for plotting the data.
	 * @param edge
	 *            the axis location.
	 *
	 * @return The Java2D coordinate.
	 */
	@Override
	public double valueToJava2D(double value, Rectangle2D plotArea, RectangleEdge edge) {

		Range range = getRange();
		double axisMin = logit(range.getLowerBound());
		double axisMax = logit(range.getUpperBound());

		double min = 0.0;
		double max = 0.0;
		if (RectangleEdge.isTopOrBottom(edge)) {
			min = plotArea.getMinX();
			max = plotArea.getMaxX();
		} else if (RectangleEdge.isLeftOrRight(edge)) {
			min = plotArea.getMaxY();
			max = plotArea.getMinY();
		}

		value = logit(value);

		if (isInverted()) {
			return max - (((value - axisMin) / (axisMax - axisMin)) * (max - min));
		} else {
			return min + (((value - axisMin) / (axisMax - axisMin)) * (max - min));
		}

	}

	/**
	 * Converts a coordinate in Java2D space to the corresponding data value,
	 * assuming that the axis runs along one edge of the specified plotArea.
	 *
	 * @param java2DValue
	 *            the coordinate in Java2D space.
	 * @param plotArea
	 *            the area in which the data is plotted.
	 * @param edge
	 *            the axis location.
	 *
	 * @return The data value.
	 */
	@Override
	public double java2DToValue(double java2DValue, Rectangle2D plotArea, RectangleEdge edge) {
		Range range = getRange();
		double axisMin = logit(range.getLowerBound());
		double axisMax = logit(range.getUpperBound());

		double plotMin = 0.0;
		double plotMax = 0.0;
		if (RectangleEdge.isTopOrBottom(edge)) {
			plotMin = plotArea.getX();
			plotMax = plotArea.getMaxX();
		} else if (RectangleEdge.isLeftOrRight(edge)) {
			plotMin = plotArea.getMaxY();
			plotMax = plotArea.getMinY();
		}

		if (isInverted()) {
			return unlogit(axisMax - ((java2DValue - plotMin) / (plotMax - plotMin)) * (axisMax - axisMin));
		} else {
			return unlogit(axisMin + ((java2DValue - plotMin) / (plotMax - plotMin)) * (axisMax - axisMin));
		}
	}

	/**
	 * Zooms in on the current range.
	 *
	 * @param lowerPercent
	 *            the new lower bound.
	 * @param upperPercent
	 *            the new upper bound.
	 */
	@Override
	public void zoomRange(double lowerPercent, double upperPercent) {
		double startLog = logit(getRange().getLowerBound());
		double lengthLog = logit(getRange().getUpperBound()) - startLog;
		Range adjusted;

		if (isInverted()) {
			adjusted = new Range(unlogit(startLog + (lengthLog * (1 - upperPercent))),
					unlogit(startLog + (lengthLog * (1 - lowerPercent))));
		} else {
			adjusted = new Range(unlogit(startLog + (lengthLog * lowerPercent)),
					unlogit(startLog + (lengthLog * upperPercent)));
		}

		setRange(adjusted);
	}

	/**
	 * Calculates the positions of the tick labels for the axis, storing the
	 * results in the tick label list (ready for drawing).
	 * <p>
	 * Never adapted for logit: this is LogarithmicAxis's power-of-ten loop run
	 * between logit-rounded bounds, and its ticks do not fit this scale. It is
	 * reached only when the axis sits on a top or bottom edge, and the one
	 * caller puts it on the left of a vertical plot.
	 *
	 * @param g2
	 *            the graphics device.
	 * @param dataArea
	 *            the area in which the plot should be drawn.
	 * @param edge
	 *            the location of the axis.
	 *
	 * @return A list of ticks.
	 */
	@Override
	protected List refreshTicksHorizontal(Graphics2D g2, Rectangle2D dataArea, RectangleEdge edge) {

		List ticks = new java.util.ArrayList();
		Range range = getRange();

		// get lower bound value:
		double lowerBoundVal = range.getLowerBound();

		// get upper bound value
		double upperBoundVal = range.getUpperBound();

		// get log10 version of lower bound and round to integer:
		int iBegCount = (int) Math.rint(logit(lowerBoundVal));
		// get log10 version of upper bound and round to integer:
		int iEndCount = (int) Math.rint(logit(upperBoundVal));

		if (iBegCount == iEndCount && iBegCount > 0 && Math.pow(10, iBegCount) > lowerBoundVal) {
			// only 1 power of 10 value, it's > 0 and its resulting
			// tick value will be larger than lower bound of data
			--iBegCount; // decrement to generate more ticks
		}

		double currentTickValue;
		String tickLabel;
		boolean zeroTickFlag = false;
		for (int i = iBegCount; i <= iEndCount; i++) {
			// for each power of 10 value; create ten ticks
			for (int j = 0; j < 10; ++j) {
				// for each tick to be displayed

				if (zeroTickFlag) { // if did zero tick last iter then
					--j; // decrement to do 1.0 tick now
				} // calculate power-of-ten value for tick:
				currentTickValue = (i >= 0) ? Math.pow(10, i) + (Math.pow(10, i) * j)
						: -(Math.pow(10, -i) - (Math.pow(10, -i - 1) * j));
				if (!zeroTickFlag) { // did not do zero tick last iteration
					if (Math.abs(currentTickValue - 1.0) < 0.0001 && lowerBoundVal <= 0.0 && upperBoundVal >= 0.0) {
						// tick value is 1.0 and 0.0 is within data range
						currentTickValue = 0.0; // set tick value to zero
						zeroTickFlag = true; // indicate zero tick
					}
				} else { // did zero tick last iteration
					zeroTickFlag = false; // clear flag
				} // create tick label string:
					// show tick label if "1e#"-style and it's one
					// of the first two, if it's the first or last
					// in the set, or if it's 1-5; beyond that
					// show fewer as the values get larger:
				tickLabel = (j < 1 || (i < 1 && j < 5) || (j < 4 - i) || currentTickValue >= upperBoundVal)
						? makeTickLabel(currentTickValue)
						: "";

				if (currentTickValue > upperBoundVal) {
					return ticks; // if past highest data value then exit
									// method
				}

				if (currentTickValue >= lowerBoundVal - SMALL_LOG_VALUE) {
					// tick value not below lowest data value
					TextAnchor anchor;
					TextAnchor rotationAnchor;
					double angle = 0.0;
					if (isVerticalTickLabels()) {
						anchor = TextAnchor.CENTER_RIGHT;
						rotationAnchor = TextAnchor.CENTER_RIGHT;
						if (edge == RectangleEdge.TOP) {
							angle = Math.PI / 2.0;
						} else {
							angle = -Math.PI / 2.0;
						}
					} else {
						if (edge == RectangleEdge.TOP) {
							anchor = TextAnchor.BOTTOM_CENTER;
							rotationAnchor = TextAnchor.BOTTOM_CENTER;
						} else {
							anchor = TextAnchor.TOP_CENTER;
							rotationAnchor = TextAnchor.TOP_CENTER;
						}
					}

					NumberTick tick = new NumberTick(currentTickValue, tickLabel, anchor, rotationAnchor, angle);
					ticks.add(tick);
				}
			}
		}
		return ticks;
	}

	private final int places = 1;
	private final long places10 = (long) Math.pow(10, places);
	private final int placeOffset = (int) (Math.pow(10, places) / 2) - 1;

	/*
	 * For kinda simplicity each tick line is assigned an integer, counting out
	 * from 0 at peak/2, negative below and positive above as mirror images.
	 * With places = 1, in fractions of peak:
	 *
	 * -22 = 0.001, -13 = 0.01, -12..-5 = 0.02..0.09, -4 = 0.1,
	 *
	 * -3..-1 = 0.2..0.4, 0 = 0.5, 1..3 = 0.6..0.8, 4 = 0.9,
	 *
	 * 5..12 = 0.91..0.98, 13 = 0.99, 22 = 0.999
	 *
	 * so nine lines per decade toward either end. doLabel labels 0 and every
	 * ninth line out from 4 and -4: 0.5, then 0.1, 0.01, ... and 0.9, 0.99, ...
	 *
	 * Other values of places were sketched and are not used: 0 should give
	 * just 0.01, 0.1, 0.5, 0.9, 0.99, and 2 should give 0.5, 0.51,
	 * 0.52...0.9, 0.901, ...
	 */
	private double valueToInteger(double val) {
		if (val > peak * 0.5) {
			return -valueToInteger(peak - val);
		}

		val /= peak;

		long pow10 = (long) Math.floor((Math.log(val) / LOG10));
		val /= Math.pow(10, pow10);

		pow10 = pow10 * (places10 - 1);
		val = pow10 - placeOffset + (val - 1) + (places10 - 1);

		return val;
	}

	private double integerToValue(long integer) {
		if (integer > 0) {
			return peak - integerToValue(-integer);
		}
		if (integer == 0) {
			return peak / 2.0;
		}

		integer += placeOffset - places10 + 1;
		long pow10 = integer / (places10 - 1) - 1;
		long remainder = integer % (places10 - 1);

		double val = Math.pow(10, pow10) * (places10 + remainder);
		val *= peak;

		return val;
	}

	private boolean doLabel(int integer) {
		if (integer == 0) {
			return true;
		}
		integer %= places10 - 1;
		if (integer > 0) {
			return integer == placeOffset;
		}
		return integer == -placeOffset;
	}

	/**
	 * Calculates the positions of the tick labels for the axis, storing the
	 * results in the tick label list (ready for drawing).
	 * <p>
	 * With a peak of 100 the lines fall at 1, 2, ... 9, 10, 20, 30, 40, 50, 60,
	 * ... 90, 91, ... 99, 99.1, ... and so on toward either end, and the labels
	 * at 50 and each power of ten and its mirror: 1%, 10%, 50%, 90%, 99%.
	 * Unlabelled lines carry a null label.
	 *
	 * @param g2
	 *            the graphics device.
	 * @param dataArea
	 *            the area in which the plot should be drawn.
	 * @param edge
	 *            the location of the axis.
	 *
	 * @return A list of ticks.
	 */
	@Override
	protected List<NumberTick> refreshTicksVertical(Graphics2D g2, Rectangle2D dataArea, RectangleEdge edge) {
		List<NumberTick> ticks = new java.util.ArrayList<>();

		// upper and lower bounds
		double lowerBoundVal = getRange().getLowerBound();
		double upperBoundVal = getRange().getUpperBound();

		int iLower = (int) Math.ceil(valueToInteger(lowerBoundVal));
		int iUpper = (int) Math.floor(valueToInteger(upperBoundVal));

		for (int i = iLower; i <= iUpper; i++) {
			double tickVal = integerToValue(i);
			String tickLabel = null;

			if (doLabel(i)) {
				tickLabel = makeTickLabel(tickVal, true) + (peak == 100 ? "%" : ""); // TODO
			}

			if (tickVal >= lowerBoundVal - SMALL_LOG_VALUE) {
				// tick value not below the axis's lower bound
				TextAnchor anchor;
				TextAnchor rotationAnchor;
				double angle = 0.0;
				if (isVerticalTickLabels()) {
					if (edge == RectangleEdge.LEFT) {
						anchor = TextAnchor.BOTTOM_CENTER;
						rotationAnchor = TextAnchor.BOTTOM_CENTER;
						angle = -Math.PI / 2.0;
					} else {
						anchor = TextAnchor.BOTTOM_CENTER;
						rotationAnchor = TextAnchor.BOTTOM_CENTER;
						angle = Math.PI / 2.0;
					}
				} else {
					if (edge == RectangleEdge.LEFT) {
						anchor = TextAnchor.CENTER_RIGHT;
						rotationAnchor = TextAnchor.CENTER_RIGHT;
					} else {
						anchor = TextAnchor.CENTER_LEFT;
						rotationAnchor = TextAnchor.CENTER_LEFT;
					}
				}
				// create tick object and add to list:
				ticks.add(new NumberTick(tickVal, tickLabel, anchor, rotationAnchor, angle));
			}

		}
		return ticks;

	}

	/**
	 * Converts the given value to a tick label string.
	 *
	 * @param val
	 *            the value to convert.
	 * @param forceFmtFlag
	 *            true to force the number-formatter object to be used.
	 *
	 * @return The tick label string.
	 */
	protected String makeTickLabel(double val, boolean forceFmtFlag) {
		if (forceFmtFlag) {
			// toLowerCase is left from LogarithmicAxis's "1e#" labels; "0.###"
			// never writes an E.
			return this.numberFormatterObj.format(val).toLowerCase();
		}
		return getTickUnit().valueToString(val);
	}

	/**
	 * Converts the given value to a tick label string.
	 * 
	 * @param val
	 *            the value to convert.
	 *
	 * @return The tick label string.
	 */
	protected String makeTickLabel(double val) {
		return makeTickLabel(val, false);
	}

}
