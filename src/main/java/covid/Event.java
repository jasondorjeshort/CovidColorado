package covid;

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

}
