package io.meterian.aicalendar.calendar;

/** Item IDs are a prefix and a number: A-1 is an appointment, L-1 an alarm, N-1 a note. */
final class ItemIds {

    static final String APPOINTMENT_PREFIX = "A";
    static final String ALARM_PREFIX = "L";
    static final String NOTE_PREFIX = "N";

    private ItemIds() {
    }

    static boolean isAppointmentId(String id) {
        return hasPrefix(id, APPOINTMENT_PREFIX);
    }

    static boolean isAlarmId(String id) {
        return hasPrefix(id, ALARM_PREFIX);
    }

    static boolean isNoteId(String id) {
        return hasPrefix(id, NOTE_PREFIX);
    }

    static CalendarException buildUnknownIdError(String id) {
        return new CalendarException("No item has the id " + id + ". Use find-items to get the id.");
    }

    private static boolean hasPrefix(String id, String prefix) {
        return id != null && id.startsWith(prefix + "-");
    }
}
