package io.meterian.aicalendar.email;

import io.meterian.aicalendar.calendar.Appointment;
import io.meterian.aicalendar.calendar.Attendee;
import io.meterian.aicalendar.calendar.Frequency;
import io.meterian.aicalendar.calendar.OccurrenceChange;
import io.meterian.aicalendar.calendar.RepeatRule;
import static io.meterian.aicalendar.calendar.ValuePicker.pickChangedValue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Builds an iCalendar (RFC 5545) invitation for one appointment and one attendee. */
public class IcsBuilder {

    private static final DateTimeFormatter LOCAL_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter UTC_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final int MAX_LINE_LENGTH = 75;
    private static final String LINE_BREAK = "\r\n";

    private final ZoneId zone;

    public IcsBuilder(ZoneId zone) {
        this.zone = zone;
    }

    public String buildInvite(Appointment appointment, Attendee attendee, String organizerName,
            String organizerEmail, Instant stamp) {
        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:-//ai-calendar//EN");
        lines.add("METHOD:REQUEST");
        List<String> peopleLines = buildPeopleLines(attendee, organizerName, organizerEmail);
        addSeriesEvent(lines, appointment, stamp, peopleLines);
        addChangedOccurrenceEvents(lines, appointment, stamp, peopleLines);
        lines.add("END:VCALENDAR");
        return joinFoldedLines(lines);
    }

    /** The appointment itself; for a series, with its repeat rule and cancelled dates. */
    private void addSeriesEvent(List<String> lines, Appointment appointment, Instant stamp,
            List<String> peopleLines) {
        addEventStart(lines, appointment, stamp);
        addTimeLines(lines, appointment.date, appointment.startTime, appointment.endTime);
        addDescriptionLines(lines, appointment.title, appointment.place);
        lines.addAll(peopleLines);
        addRepeatLines(lines, appointment);
        lines.add("END:VEVENT");
    }

    /**
     * One extra event for each occurrence that was moved or changed. RECURRENCE-ID names the original occurrence,
     * so the attendee's calendar replaces it with the changed one.
     */
    private void addChangedOccurrenceEvents(List<String> lines, Appointment appointment, Instant stamp,
            List<String> peopleLines) {
        if (appointment.repeat == null) {
            return;
        }
        for (Map.Entry<LocalDate, OccurrenceChange> override : appointment.overrides.entrySet()) {
            OccurrenceChange change = override.getValue();
            if (Boolean.TRUE.equals(change.cancelled)) {
                continue;
            }
            LocalDate originalDate = override.getKey();
            addEventStart(lines, appointment, stamp);
            lines.add("RECURRENCE-ID" + buildLocalTimeValue(originalDate, appointment.startTime));
            addTimeLines(lines, pickChangedValue(change.date, originalDate),
                    pickChangedValue(change.startTime, appointment.startTime),
                    pickChangedValue(change.endTime, appointment.endTime));
            addDescriptionLines(lines, pickChangedValue(change.title, appointment.title),
                    pickChangedValue(change.place, appointment.place));
            lines.addAll(peopleLines);
            lines.add("END:VEVENT");
        }
    }

    private static void addEventStart(List<String> lines, Appointment appointment, Instant stamp) {
        lines.add("BEGIN:VEVENT");
        lines.add("UID:" + appointment.id + "@ai-calendar");
        lines.add("DTSTAMP:" + UTC_TIME_FORMAT.format(stamp));
    }

    private void addTimeLines(List<String> lines, LocalDate date, LocalTime startTime, LocalTime endTime) {
        lines.add("DTSTART" + buildLocalTimeValue(date, startTime));
        if (endTime != null) {
            lines.add("DTEND" + buildLocalTimeValue(date, endTime));
        }
    }

    private static void addDescriptionLines(List<String> lines, String title, String place) {
        lines.add("SUMMARY:" + escapeText(title));
        if (place != null) {
            lines.add("LOCATION:" + escapeText(place));
        }
    }

    private static List<String> buildPeopleLines(Attendee attendee, String organizerName, String organizerEmail) {
        return List.of(
                "ORGANIZER;CN=" + quoteParameter(organizerName) + ":mailto:" + organizerEmail,
                "ATTENDEE;CN=" + quoteParameter(attendee.name)
                        + ";ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE:mailto:" + attendee.email);
    }

    private void addRepeatLines(List<String> lines, Appointment appointment) {
        if (appointment.repeat == null) {
            return;
        }
        lines.add(buildRepeatRuleLine(appointment.repeat));
        for (Map.Entry<LocalDate, OccurrenceChange> override : appointment.overrides.entrySet()) {
            if (Boolean.TRUE.equals(override.getValue().cancelled)) {
                lines.add("EXDATE" + buildLocalTimeValue(override.getKey(), appointment.startTime));
            }
        }
    }

    private String buildRepeatRuleLine(RepeatRule rule) {
        StringBuilder line = new StringBuilder("RRULE:FREQ=").append(rule.frequency.name());
        if (rule.frequency == Frequency.WEEKLY) {
            line.append(";BYDAY=").append(rule.daysOfWeek.stream()
                    .map(day -> day.name().substring(0, 2))
                    .collect(Collectors.joining(",")));
        }
        if (rule.until != null) {
            // With a TZID start time, UNTIL must be in UTC. Use the end of the last day.
            Instant endOfLastDay = rule.until.atTime(23, 59, 59).atZone(zone).toInstant();
            line.append(";UNTIL=").append(UTC_TIME_FORMAT.format(endOfLastDay));
        }
        return line.toString();
    }

    /** For example ";TZID=Europe/Rome:20260930T150000". */
    private String buildLocalTimeValue(LocalDate date, LocalTime time) {
        return ";TZID=" + zone.getId() + ":" + LOCAL_TIME_FORMAT.format(date.atTime(time));
    }

    private static String joinFoldedLines(List<String> lines) {
        return lines.stream().map(IcsBuilder::foldLine).collect(Collectors.joining(LINE_BREAK, "", LINE_BREAK));
    }

    static String escapeText(String text) {
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }

    static String quoteParameter(String value) {
        return "\"" + value.replace("\"", "'") + "\"";
    }

    /** A line longer than 75 characters continues on the next line, which starts with one space. */
    static String foldLine(String line) {
        if (line.length() <= MAX_LINE_LENGTH) {
            return line;
        }
        int continuationLength = MAX_LINE_LENGTH - 1;
        StringBuilder folded = new StringBuilder(line.substring(0, MAX_LINE_LENGTH));
        for (int start = MAX_LINE_LENGTH; start < line.length(); start += continuationLength) {
            int end = Math.min(start + continuationLength, line.length());
            folded.append(LINE_BREAK).append(' ').append(line, start, end);
        }
        return folded.toString();
    }
}
