package io.meterian.aicalendar.alerts;

import io.meterian.aicalendar.calendar.AlarmOccurrence;
import io.meterian.aicalendar.calendar.AppointmentOccurrence;
import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.channel.UserChannel;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Every 30 seconds, prints the alerts that became due since the last check. */
public class AlertScheduler {

    public static final long CHECK_INTERVAL_SECONDS = 30;
    /** More than the 7-day lead-time limit, so no appointment alert is missed. */
    private static final int LOOKAHEAD_DAYS = 8;
    private static final String ALARM_CLOCK_SYMBOL = "⏰";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final CalendarService service;
    private final UserChannel channel;
    private final Clock clock;
    private final Set<String> printedAlertKeys = new HashSet<>();
    private LocalDateTime lastCheck;
    private ScheduledExecutorService executor;

    public AlertScheduler(CalendarService service, UserChannel channel, Clock clock) {
        this.service = service;
        this.channel = channel;
        this.clock = clock;
        this.lastCheck = LocalDateTime.now(clock);
    }

    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "alert-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(this::checkDueAlertsSafely,
                CHECK_INTERVAL_SECONDS, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public synchronized void checkDueAlerts() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (Alert alert : collectAlertsBetween(lastCheck, now)) {
            if (printedAlertKeys.add(alert.key)) {
                channel.printAlert(alert.text);
            }
        }
        lastCheck = now;
    }

    /** A scheduled task that throws is never run again, so every error is caught here. */
    private void checkDueAlertsSafely() {
        try {
            checkDueAlerts();
        } catch (RuntimeException e) {
            channel.printAlert("The alert check failed: " + e.getMessage());
        }
    }

    private List<Alert> collectAlertsBetween(LocalDateTime after, LocalDateTime upTo) {
        List<Alert> alerts = new ArrayList<>();
        alerts.addAll(collectAppointmentAlerts(after, upTo));
        alerts.addAll(collectAlarmAlerts(after, upTo));
        alerts.sort(Comparator.comparing(alert -> alert.firesAt));
        return alerts;
    }

    private List<Alert> collectAppointmentAlerts(LocalDateTime after, LocalDateTime upTo) {
        List<Alert> alerts = new ArrayList<>();
        List<AppointmentOccurrence> occurrences = service.listAppointmentOccurrences(
                after.toLocalDate(), upTo.toLocalDate().plusDays(LOOKAHEAD_DAYS));
        for (AppointmentOccurrence occurrence : occurrences) {
            LocalDateTime firesAt = occurrence.computeAlertTime();
            if (isInWindow(firesAt, after, upTo)) {
                String key = occurrence.appointment.id + "|" + occurrence.originalDate + "|" + firesAt;
                alerts.add(new Alert(key, firesAt, formatAppointmentAlert(occurrence, firesAt)));
            }
        }
        return alerts;
    }

    private List<Alert> collectAlarmAlerts(LocalDateTime after, LocalDateTime upTo) {
        List<Alert> alerts = new ArrayList<>();
        for (AlarmOccurrence occurrence : service.listAlarmOccurrences(after.toLocalDate(), upTo.toLocalDate())) {
            if (isInWindow(occurrence.firesAt, after, upTo)) {
                String key = occurrence.alarm.id + "|" + occurrence.originalDate + "|" + occurrence.firesAt;
                alerts.add(new Alert(key, occurrence.firesAt, formatAlarmAlert(occurrence)));
            }
        }
        return alerts;
    }

    private static boolean isInWindow(LocalDateTime time, LocalDateTime after, LocalDateTime upTo) {
        return time.isAfter(after) && !time.isAfter(upTo);
    }

    private static String formatAppointmentAlert(AppointmentOccurrence occurrence, LocalDateTime firesAt) {
        String text = ALARM_CLOCK_SYMBOL + " " + TIME_FORMAT.format(firesAt) + " " + occurrence.title
                + " at " + TIME_FORMAT.format(occurrence.startTime);
        return occurrence.place == null ? text : text + " (" + occurrence.place + ")";
    }

    private static String formatAlarmAlert(AlarmOccurrence occurrence) {
        return ALARM_CLOCK_SYMBOL + " " + TIME_FORMAT.format(occurrence.firesAt) + " " + occurrence.message;
    }
}
