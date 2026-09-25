package io.meterian.aicalendar.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CalendarServiceTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);

    @TempDir
    Path tempDir;

    private Path file;
    private CalendarService service;

    @BeforeEach
    void createService() {
        file = tempDir.resolve("calendar.json");
        Clock clock = Clock.fixed(LocalDateTime.of(2026, 9, 24, 10, 0).atZone(ROME).toInstant(), ROME);
        service = new CalendarService(new CalendarStore(file), new CalendarData(), new OccurrenceExpander(), clock);
    }

    static Appointment buildAppointment(String title, LocalDate date, LocalTime start) {
        Appointment appointment = new Appointment();
        appointment.title = title;
        appointment.date = date;
        appointment.startTime = start;
        appointment.leadTimeMinutes = 30;
        return appointment;
    }

    static Alarm buildFixedAlarm(String message, LocalDate date, LocalTime time) {
        Alarm alarm = new Alarm();
        alarm.message = message;
        alarm.date = date;
        alarm.time = time;
        return alarm;
    }

    static Alarm buildLinkedAlarm(String message, String appointmentId, int minutesBefore) {
        Alarm alarm = new Alarm();
        alarm.message = message;
        alarm.appointmentId = appointmentId;
        alarm.minutesBefore = minutesBefore;
        return alarm;
    }

    static Note buildNote(String text, LocalDate date, String appointmentId) {
        Note note = new Note();
        note.text = text;
        note.date = date;
        note.appointmentId = appointmentId;
        return note;
    }

    private void assertRejected(String expectedMessage, Runnable action) {
        CalendarException error = assertThrows(CalendarException.class, action::run);
        assertEquals(expectedMessage, error.getMessage());
    }

    @Test
    void addAppointmentAssignsIdsAndSaves() {
        assertEquals("A-1", service.addAppointment(buildAppointment("Gym", MONDAY, LocalTime.of(18, 0))).id);
        assertEquals("A-2", service.addAppointment(buildAppointment("Dentist", MONDAY, LocalTime.of(9, 0))).id);

        CalendarData saved = new CalendarStore(file).load();
        assertEquals(2, saved.appointments.size());
        assertEquals(3, saved.nextIds.get("A"));
    }

    @Test
    void addAppointmentRejectsBrokenRules() {
        Appointment noStart = buildAppointment("Dentist", MONDAY, null);
        assertRejected("startTime is missing. Ask the user for it.", () -> service.addAppointment(noStart));

        Appointment endFirst = buildAppointment("Dentist", MONDAY, LocalTime.of(15, 0));
        endFirst.endTime = LocalTime.of(14, 0);
        assertRejected("endTime must be after startTime.", () -> service.addAppointment(endFirst));

        Appointment weeklyNoDays = buildAppointment("Gym", MONDAY, LocalTime.of(18, 0));
        weeklyNoDays.repeat = new RepeatRule(Frequency.WEEKLY, null, null);
        assertRejected("repeat.daysOfWeek is required for WEEKLY.", () -> service.addAppointment(weeklyNoDays));

        Appointment longLead = buildAppointment("Trip", MONDAY, LocalTime.of(8, 0));
        longLead.leadTimeMinutes = 20000;
        assertRejected("leadTimeMinutes must be between 0 and 10080 (7 days).",
                () -> service.addAppointment(longLead));

        Appointment badEmail = buildAppointment("Meeting", MONDAY, LocalTime.of(10, 0));
        badEmail.attendees.add(new Attendee("Anna Rossi", "anna-at-example"));
        assertRejected("'anna-at-example' is not a valid email address.", () -> service.addAppointment(badEmail));
    }

    @Test
    void addAlarmNeedsExactlyOneForm() {
        Alarm neither = new Alarm();
        neither.message = "Hello";
        assertRejected("Give either date and time, or appointmentId and minutesBefore.",
                () -> service.addAlarm(neither));

        Alarm both = buildFixedAlarm("Hello", MONDAY, LocalTime.of(7, 0));
        both.appointmentId = "A-1";
        both.minutesBefore = 10;
        assertRejected("Give either date and time, or appointmentId and minutesBefore.",
                () -> service.addAlarm(both));

        assertRejected("No item has the id A-9. Use find-items to get the id.",
                () -> service.addAlarm(buildLinkedAlarm("Leave", "A-9", 10)));

        assertEquals("L-1", service.addAlarm(buildFixedAlarm("Wake up", MONDAY, LocalTime.of(7, 0))).id);
    }

    @Test
    void alarmNeedsNoMessage() {
        assertEquals("L-1", service.addAlarm(buildFixedAlarm(null, MONDAY, LocalTime.of(6, 30))).id);
    }

    @Test
    void addNoteNeedsExactlyOneTarget() {
        service.addAppointment(buildAppointment("Dentist", MONDAY, LocalTime.of(9, 0)));

        assertRejected("Give either date or appointmentId.",
                () -> service.addNote(buildNote("Both", MONDAY, "A-1")));
        assertRejected("Give either date or appointmentId.",
                () -> service.addNote(buildNote("Neither", null, null)));
        assertEquals("N-1", service.addNote(buildNote("Bring X-ray", null, "A-1")).id);
    }

    @Test
    void listDayShowsOccurrencesAlarmsAndNotes() {
        Appointment gym = buildAppointment("Gym", MONDAY, LocalTime.of(18, 0));
        gym.repeat = new RepeatRule(Frequency.WEEKLY, List.of(DayOfWeek.MONDAY), null);
        service.addAppointment(gym);                                                        // A-1
        service.addAppointment(buildAppointment("Dentist", MONDAY, LocalTime.of(9, 0)));    // A-2
        service.addAlarm(buildFixedAlarm("Wake up", MONDAY, LocalTime.of(7, 0)));           // L-1
        service.addAlarm(buildLinkedAlarm("Leave for dentist", "A-2", 60));                 // L-2
        service.addNote(buildNote("Pay rent", MONDAY, null));                               // N-1
        service.addNote(buildNote("Bring X-ray", null, "A-2"));                             // N-2
        service.addNote(buildNote("Other day", MONDAY.plusDays(1), null));                  // N-3

        DayAgenda agenda = service.listDay(MONDAY);

        assertEquals(List.of("Dentist", "Gym"),
                agenda.appointments.stream().map(o -> o.title).collect(Collectors.toList()));
        assertEquals(List.of("Wake up", "Leave for dentist"),
                agenda.alarms.stream().map(o -> o.message).collect(Collectors.toList()));
        assertEquals(List.of("N-1", "N-2"),
                agenda.notes.stream().map(n -> n.id).collect(Collectors.toList()));

        DayAgenda nextMonday = service.listDay(MONDAY.plusWeeks(1));
        assertEquals(List.of("Gym"),
                nextMonday.appointments.stream().map(o -> o.title).collect(Collectors.toList()));
    }

    @Test
    void findItemsFiltersByQueryTypeAndRange() {
        Appointment gym = buildAppointment("Gym", MONDAY, LocalTime.of(18, 0));
        gym.repeat = new RepeatRule(Frequency.WEEKLY, List.of(DayOfWeek.MONDAY), null);
        service.addAppointment(gym);
        service.addAppointment(buildAppointment("Dentist", LocalDate.of(2026, 9, 30), LocalTime.of(15, 0)));
        service.addAlarm(buildFixedAlarm("Call Anna", MONDAY, LocalTime.of(12, 0)));

        assertEquals(List.of("A-1"), listIds(service.findItems("GYM", null, null, null)));
        assertEquals(List.of("L-1"), listIds(service.findItems(null, "alarm", null, null)));
        assertEquals(List.of("A-1"), listIds(service.findItems(null, "appointment",
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5))));
        assertEquals(List.of(), listIds(service.findItems(null, "appointment",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 4))));
        assertRejected("type must be appointment, alarm or note.",
                () -> service.findItems(null, "meeting", null, null));
    }

    private static List<String> listIds(List<Object> items) {
        return items.stream().map(item -> {
            if (item instanceof Appointment) {
                return ((Appointment) item).id;
            }
            if (item instanceof Alarm) {
                return ((Alarm) item).id;
            }
            return ((Note) item).id;
        }).collect(Collectors.toList());
    }
}
