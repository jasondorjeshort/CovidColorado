package colorado;

import java.awt.Color;

import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.TextAnchor;

import charts.Charts;
import covid.CalendarUtils;

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
public class Event {
	public final String name;
	public final long time;

	public static final Event[] events = new Event[] {

			new Event("SoE", "3-10-2020"),
			// new Event()
			new Event("SaH", "3-26-2020"),

			new Event("BLM", "05-28-2020"),

			new Event("Bars", "06-30-2020"),

			new Event("Masks", "7-16-2020"),

			new Event("❄", "9-9-2020"), // snow
			new Event("❄", "10-25-2020"), // snow
			new Event("❄", "2-24-2021"), // snow
			new Event("❄", "3-12-2021"), // snow

			new Event("CU/DPS open", "8-24-2020"),
			// new Event("ENS", "10-25-2020"),
			new Event("DPS close", "10-30-2020"),

			new Event("RED", "11-20-2020"),
			// new Event("Intervention", "11-05-2020")
			new Event("TG", "11-26-2020"), // Thanksgiving

			new Event("💉", "12-20-2020"), // vaccine start + 6

			new Event("C", "12-25-2020"), // Christmas

			new Event(null, "3-21-2021"),

			new Event("💉", "4-13-2021"), // vaccine start + 11

			// keep this below
	};

	public Event(String name, String date) {
		long time_ = 0;
		try {
			time_ = CalendarUtils.dateToTime(date);
			if (name == null) {
				String[] split = date.split("[-/]");
				name = split[0] + "-" + split[1];
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		this.name = name;
		this.time = time_;
	}

	public static void addEvents(XYPlot plot) {
		for (Event event : Event.events) {
			ValueMarker marker = new ValueMarker(event.time);
			marker.setPaint(Color.green);
			marker.setLabel(event.name);
			marker.setStroke(Charts.stroke);
			marker.setLabelFont(Charts.font);
			marker.setLabelTextAnchor(TextAnchor.TOP_CENTER);
			plot.addDomainMarker(marker);
		}
	}

}
