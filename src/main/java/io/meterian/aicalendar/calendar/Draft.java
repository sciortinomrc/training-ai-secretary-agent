package io.meterian.aicalendar.calendar;

/** An invitation email that is not sent yet: its text for the attendees of one appointment. */
public class Draft {
    public String id;
    public String appointmentId;
    public String subject;
    public String body;
}
