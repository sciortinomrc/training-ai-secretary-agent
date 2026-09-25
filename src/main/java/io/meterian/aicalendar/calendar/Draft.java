package io.meterian.aicalendar.calendar;

import java.util.ArrayList;
import java.util.List;

/**
 * An email that is not sent yet. Either an invitation (appointmentId: it goes to that appointment's attendees,
 * with the .ics attached) or a plain email (recipients), never both.
 */
public class Draft {
    public String id;
    public String appointmentId;
    public List<Attendee> recipients = new ArrayList<>();
    public String subject;
    public String body;
}
