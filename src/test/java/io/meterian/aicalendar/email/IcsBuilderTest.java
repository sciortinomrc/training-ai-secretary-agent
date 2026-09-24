package io.meterian.aicalendar.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.meterian.aicalendar.calendar.Appointment;
import io.meterian.aicalendar.calendar.Attendee;
import io.meterian.aicalendar.calendar.Frequency;
import io.meterian.aicalendar.calendar.OccurrenceChange;
import io.meterian.aicalendar.calendar.RepeatRule;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class IcsBuilderTest {

    private final IcsBuilder builder = new IcsBuilder(ZoneId.of("Europe/Rome"));
    private final Attendee anna = new Attendee("Anna Rossi", "anna@example.com");
    private final Instant stamp = Instant.parse("2026-09-24T08:00:00Z");

    private static Appointment buildDentist() {
        Appointment appointment = new Appointment();
        appointment.id = "A-3";
        appointment.title = "Dentist";
        appointment.date = LocalDate.of(2026, 9, 30);
        appointment.startTime = LocalTime.of(15, 0);
        appointment.endTime = LocalTime.of(15, 30);
        appointment.place = "Via Roma 10";
        appointment.leadTimeMinutes = 30;
        return appointment;
    }

    /** Joins folded lines again, so a test can check a whole logical line. */
    private static String unfoldLines(String ics) {
        return ics.replace("\r\n ", "");
    }

    @Test
    void containsTheRequiredFields() {
        String ics = unfoldLines(builder.buildInvite(buildDentist(), anna, "Marco Rossi", "me@example.com", stamp));

        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"));
        assertTrue(ics.contains("\r\nMETHOD:REQUEST\r\n"));
        assertTrue(ics.contains("\r\nUID:A-3@ai-calendar\r\n"));
        assertTrue(ics.contains("\r\nDTSTAMP:20260924T080000Z\r\n"));
        assertTrue(ics.contains("\r\nDTSTART;TZID=Europe/Rome:20260930T150000\r\n"));
        assertTrue(ics.contains("\r\nDTEND;TZID=Europe/Rome:20260930T153000\r\n"));
        assertTrue(ics.contains("\r\nSUMMARY:Dentist\r\n"));
        assertTrue(ics.contains("\r\nLOCATION:Via Roma 10\r\n"));
        assertTrue(ics.contains("\r\nORGANIZER;CN=\"Marco Rossi\":mailto:me@example.com\r\n"));
        assertTrue(ics.contains(
                "\r\nATTENDEE;CN=\"Anna Rossi\";ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE:mailto:anna@example.com\r\n"));
        assertTrue(ics.endsWith("\r\nEND:VCALENDAR\r\n"));
    }

    @Test
    void noEndTimeMeansNoDtend() {
        Appointment appointment = buildDentist();
        appointment.endTime = null;

        assertFalse(builder.buildInvite(appointment, anna, "Marco Rossi", "me@example.com", stamp).contains("DTEND"));
    }

    @Test
    void weeklyRepeatWithUntilAndCancelledDate() {
        Appointment appointment = buildDentist();
        appointment.date = LocalDate.of(2026, 9, 28);
        appointment.repeat = new RepeatRule(Frequency.WEEKLY,
                List.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), LocalDate.of(2026, 12, 31));
        OccurrenceChange cancelled = new OccurrenceChange();
        cancelled.cancelled = true;
        appointment.overrides.put(LocalDate.of(2026, 10, 5), cancelled);

        String ics = builder.buildInvite(appointment, anna, "Marco Rossi", "me@example.com", stamp);

        assertTrue(ics.contains("\r\nRRULE:FREQ=WEEKLY;BYDAY=MO,TH;UNTIL=20261231T225959Z\r\n"));
        assertTrue(ics.contains("\r\nEXDATE;TZID=Europe/Rome:20261005T150000\r\n"));
    }

    @Test
    void movedOccurrenceGetsItsOwnEvent() {
        Appointment appointment = buildDentist();
        appointment.date = LocalDate.of(2026, 9, 28);
        appointment.repeat = new RepeatRule(Frequency.WEEKLY, List.of(DayOfWeek.MONDAY), null);
        OccurrenceChange moved = new OccurrenceChange();
        moved.date = LocalDate.of(2026, 10, 6);
        moved.startTime = LocalTime.of(19, 0);
        moved.endTime = LocalTime.of(19, 30);
        appointment.overrides.put(LocalDate.of(2026, 10, 5), moved);

        String ics = unfoldLines(builder.buildInvite(appointment, anna, "Marco Rossi", "me@example.com", stamp));

        assertEquals(2, ics.split("BEGIN:VEVENT", -1).length - 1);
        assertTrue(ics.contains("\r\nRECURRENCE-ID;TZID=Europe/Rome:20261005T150000\r\n"));
        assertTrue(ics.contains("\r\nDTSTART;TZID=Europe/Rome:20261006T190000\r\n"));
        assertTrue(ics.contains("\r\nDTEND;TZID=Europe/Rome:20261006T193000\r\n"));
        assertFalse(ics.contains("EXDATE"));
    }

    @Test
    void escapesTextAndFoldsLongLines() {
        Appointment appointment = buildDentist();
        appointment.title = "Lunch, drinks; talk";
        appointment.place = "A very long place name that goes on and on, far past the seventy-five character limit";

        String ics = builder.buildInvite(appointment, anna, "Marco Rossi", "me@example.com", stamp);

        assertTrue(ics.contains("\r\nSUMMARY:Lunch\\, drinks\\; talk\r\n"));
        for (String line : ics.split("\r\n")) {
            assertTrue(line.length() <= 75, "Line too long: " + line);
        }
        assertTrue(ics.contains("\r\n "));
    }
}
