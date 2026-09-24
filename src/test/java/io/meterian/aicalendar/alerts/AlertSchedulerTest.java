package io.meterian.aicalendar.alerts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.meterian.aicalendar.MutableClock;
import io.meterian.aicalendar.calendar.Alarm;
import io.meterian.aicalendar.calendar.Appointment;
import io.meterian.aicalendar.calendar.CalendarData;
import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.calendar.CalendarStore;
import io.meterian.aicalendar.calendar.Frequency;
import io.meterian.aicalendar.calendar.OccurrenceExpander;
import io.meterian.aicalendar.calendar.RepeatRule;
import io.meterian.aicalendar.channel.RecordingUserChannel;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AlertSchedulerTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);

    @TempDir
    Path tempDir;

    private MutableClock clock;
    private CalendarService service;
    private RecordingUserChannel channel;
    private AlertScheduler scheduler;

    @BeforeEach
    void createScheduler() {
        clock = new MutableClock(TODAY.atTime(10, 0), ROME);
        service = new CalendarService(new CalendarStore(tempDir.resolve("calendar.json")), new CalendarData(),
                new OccurrenceExpander(), clock);
        channel = new RecordingUserChannel();
        scheduler = new AlertScheduler(service, channel, clock);
    }

    private void checkAt(LocalDateTime time) {
        clock.setTime(time);
        scheduler.checkDueAlerts();
    }

    private Appointment addAppointment(String title, LocalDate date, LocalTime start, int leadMinutes) {
        Appointment appointment = new Appointment();
        appointment.title = title;
        appointment.date = date;
        appointment.startTime = start;
        appointment.leadTimeMinutes = leadMinutes;
        return service.addAppointment(appointment);
    }

    @Test
    void appointmentAlertPrintsOnceAtLeadTime() {
        Appointment dentist = new Appointment();
        dentist.title = "Dentist";
        dentist.date = TODAY;
        dentist.startTime = LocalTime.of(11, 0);
        dentist.leadTimeMinutes = 30;
        dentist.place = "Via Roma 10";
        service.addAppointment(dentist);

        checkAt(TODAY.atTime(10, 29));
        assertTrue(channel.alerts.isEmpty());

        checkAt(TODAY.atTime(10, 30));
        checkAt(TODAY.atTime(10, 31));
        assertEquals(List.of("⏰ 10:30 Dentist at 11:00 (Via Roma 10)"), channel.alerts);
    }

    @Test
    void fixedAndLinkedAlarmsPrint() {
        addAppointment("Meeting", TODAY, LocalTime.of(12, 0), 0);
        Alarm fixed = new Alarm();
        fixed.message = "Call Anna";
        fixed.date = TODAY;
        fixed.time = LocalTime.of(10, 5);
        service.addAlarm(fixed);
        Alarm linked = new Alarm();
        linked.message = "Print the slides";
        linked.appointmentId = "A-1";
        linked.minutesBefore = 60;
        service.addAlarm(linked);

        checkAt(TODAY.atTime(10, 5, 30));
        checkAt(TODAY.atTime(11, 0));

        assertEquals(List.of("⏰ 10:05 Call Anna", "⏰ 11:00 Print the slides"), channel.alerts);
    }

    @Test
    void cancelledOccurrenceDoesNotAlert() {
        Appointment daily = addAppointment("Standup", TODAY, LocalTime.of(10, 30), 0);
        daily.repeat = new RepeatRule(Frequency.DAILY, null, null);
        service.removeItem(daily.id, TODAY);

        checkAt(TODAY.atTime(10, 45));

        assertTrue(channel.alerts.isEmpty());
    }

    @Test
    void alertBeforeMidnightStillFires() {
        addAppointment("Night train", TODAY.plusDays(1), LocalTime.of(0, 15), 30);

        checkAt(TODAY.atTime(23, 45, 10));

        assertEquals(List.of("⏰ 23:45 Night train at 00:15"), channel.alerts);
    }

    @Test
    void alertsDueBeforeStartupAreNotShown() {
        addAppointment("Early call", TODAY, LocalTime.of(10, 0), 5);

        checkAt(TODAY.atTime(10, 1));

        assertTrue(channel.alerts.isEmpty());
    }
}
