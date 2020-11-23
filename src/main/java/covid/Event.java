package covid;

import java.awt.Color;

import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.TextAnchor;

import charts.Charts;

public class Event {
	public final String name;
	public final long time;

	public static final Event[] events = new Event[] { new Event("SaH", "3-26-2020"), new Event("Bars", "06-30-2020"),
			new Event("Masks", "7-16-2020"), new Event("Snow", "9-9-2020"), new Event("CU/DPS", "8-24-2020"),
			new Event("ENS", "10-25-2020"),
			// new Event("Intervention", "11-05-2020")
	};

	public Event(String name, String date) {
		this.name = name;
		this.time = Date.dateToTime(date);
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
