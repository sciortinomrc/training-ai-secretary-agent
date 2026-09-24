package io.meterian.aicalendar.calendar;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** The fields that an edit changes. A null field stays as it is. */
public class ItemChanges {
    public String title;
    public LocalDate date;
    public LocalTime startTime;
    public LocalTime endTime;
    public String place;
    public Integer leadTimeMinutes;
    public List<Attendee> attendees;
    public RepeatRule repeat;
    public String message;
    public LocalTime time;
    public Integer minutesBefore;
    public String text;

    public List<String> listChangedFields() {
        List<String> fields = new ArrayList<>();
        addIfSet(fields, "title", title);
        addIfSet(fields, "date", date);
        addIfSet(fields, "startTime", startTime);
        addIfSet(fields, "endTime", endTime);
        addIfSet(fields, "place", place);
        addIfSet(fields, "leadTimeMinutes", leadTimeMinutes);
        addIfSet(fields, "attendees", attendees);
        addIfSet(fields, "repeat", repeat);
        addIfSet(fields, "message", message);
        addIfSet(fields, "time", time);
        addIfSet(fields, "minutesBefore", minutesBefore);
        addIfSet(fields, "text", text);
        return fields;
    }

    private static void addIfSet(List<String> fields, String name, Object value) {
        if (value != null) {
            fields.add(name);
        }
    }
}
