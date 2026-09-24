package io.meterian.aicalendar.calendar;

import static io.meterian.aicalendar.calendar.CalendarServiceTest.buildAppointment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A change that cannot be saved must not stay in the calendar. */
class CalendarServiceSaveFailureTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);

    @TempDir
    Path tempDir;

    private CalendarStore unwritableStore;
    private Clock clock;

    @BeforeEach
    void createUnwritableStore() throws Exception {
        Path notAFolder = tempDir.resolve("afile");
        Files.writeString(notAFolder, "x");
        unwritableStore = new CalendarStore(notAFolder.resolve("calendar.json"));
        clock = Clock.fixed(LocalDateTime.of(2026, 9, 24, 10, 0).atZone(ROME).toInstant(), ROME);
    }

    @Test
    void failedAddLeavesTheCalendarEmpty() {
        CalendarService service =
                new CalendarService(unwritableStore, new CalendarData(), new OccurrenceExpander(), clock);

        assertThrows(CalendarFileException.class,
                () -> service.addAppointment(buildAppointment("Dentist", MONDAY, LocalTime.of(15, 0))));

        assertTrue(service.findItems(null, null, null, null).isEmpty());
        assertTrue(service.listDay(MONDAY).appointments.isEmpty());
    }

    @Test
    void failedRemoveKeepsTheItem() {
        CalendarData data = new CalendarData();
        Appointment dentist = buildAppointment("Dentist", MONDAY, LocalTime.of(15, 0));
        dentist.id = "A-1";
        data.appointments.add(dentist);
        CalendarService service = new CalendarService(unwritableStore, data, new OccurrenceExpander(), clock);

        assertThrows(CalendarFileException.class, () -> service.removeItem("A-1", null));

        assertEquals("Dentist", service.findAppointment("A-1").title);
    }
}
