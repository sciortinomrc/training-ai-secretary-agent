package io.meterian.aicalendar.calendar;

/** A calendar rule was broken. The message goes to the model, so it says what to do. */
public class CalendarException extends RuntimeException {

    public CalendarException(String message) {
        super(message);
    }

    public CalendarException(String message, Throwable cause) {
        super(message, cause);
    }
}
