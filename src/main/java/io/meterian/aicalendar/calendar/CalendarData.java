package io.meterian.aicalendar.calendar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Everything in calendar.json. */
public class CalendarData {
    /** Next number for each ID prefix: A, L, N. */
    public Map<String, Integer> nextIds = new TreeMap<>();
    public List<Appointment> appointments = new ArrayList<>();
    public List<Alarm> alarms = new ArrayList<>();
    public List<Note> notes = new ArrayList<>();
}
