package io.meterian.aicalendar.tools;

import io.meterian.aicalendar.Settings;
import io.meterian.aicalendar.calendar.Appointment;
import io.meterian.aicalendar.calendar.Attendee;
import io.meterian.aicalendar.calendar.CalendarException;
import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.calendar.Draft;
import io.meterian.aicalendar.calendar.RepeatRule;
import io.meterian.aicalendar.email.Email;
import io.meterian.aicalendar.email.EmailAddress;
import io.meterian.aicalendar.email.EmailAttachment;
import io.meterian.aicalendar.email.IcsBuilder;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Builds the emails of a draft, and the preview the user approves. An invitation draft gives one email per attendee
 * of its appointment, with the .ics attached. A plain draft gives one email per recipient, with no attachment.
 */
public class DraftEmailBuilder {

    static final List<String> REQUIRED_SETTINGS = List.of("profile.name", "profile.surname", "profile.email");
    private static final String INVITE_FILE_NAME = "invite.ics";
    private static final String INVITE_CONTENT_TYPE = "text/calendar; charset=UTF-8; method=REQUEST";

    private final CalendarService service;
    private final Settings settings;
    private final IcsBuilder icsBuilder;
    private final Clock clock;

    public DraftEmailBuilder(CalendarService service, Settings settings, IcsBuilder icsBuilder, Clock clock) {
        this.service = service;
        this.settings = settings;
        this.icsBuilder = icsBuilder;
        this.clock = clock;
    }

    public List<Email> buildEmails(Draft draft) {
        requireProfileSettings();
        EmailAddress sender = buildSenderAddress();
        if (draft.appointmentId != null) {
            return buildInvitationEmails(service.findAppointment(draft.appointmentId), sender, draft);
        }
        return buildPlainEmails(sender, draft);
    }

    /** For an invitation, the stored appointment data comes first, so the user approves what attendees get. */
    public String describePreview(Draft draft, List<Email> emails) {
        StringBuilder preview = new StringBuilder();
        if (draft.appointmentId != null) {
            preview.append(describeInvitation(service.findAppointment(draft.appointmentId)));
        }
        for (Email email : emails) {
            preview.append("From: ").append(email.from.formatForDisplay()).append("\n")
                    .append("To: ").append(email.to.formatForDisplay()).append("\n")
                    .append("Subject: ").append(email.subject).append("\n\n")
                    .append(email.body).append("\n\n");
            if (email.attachment != null) {
                preview.append("Attachment: ").append(email.attachment.fileName).append("\n");
            }
            preview.append("----------\n");
        }
        return preview.toString();
    }

    private List<Email> buildInvitationEmails(Appointment appointment, EmailAddress sender, Draft draft) {
        if (appointment.attendees.isEmpty()) {
            throw new CalendarException(appointment.id + " has no attendees. Add them with edit first.");
        }
        List<Email> emails = new ArrayList<>();
        for (Attendee attendee : appointment.attendees) {
            String invite = icsBuilder.buildInvite(appointment, attendee, sender.name, sender.address, clock.instant());
            emails.add(new Email(sender, new EmailAddress(attendee.name, attendee.email), draft.subject, draft.body,
                    new EmailAttachment(INVITE_FILE_NAME, INVITE_CONTENT_TYPE, invite)));
        }
        return emails;
    }

    private static List<Email> buildPlainEmails(EmailAddress sender, Draft draft) {
        List<Email> emails = new ArrayList<>();
        for (Attendee recipient : draft.recipients) {
            emails.add(new Email(sender, new EmailAddress(recipient.name, recipient.email), draft.subject,
                    draft.body, null));
        }
        return emails;
    }

    private static String describeInvitation(Appointment appointment) {
        String time = appointment.endTime == null
                ? appointment.startTime + ", no end time"
                : appointment.startTime + " to " + appointment.endTime;
        return "Invitation details (from the calendar):\n"
                + "  Title: " + appointment.title + "\n"
                + "  Date: " + appointment.date + "\n"
                + "  Time: " + time + "\n"
                + "  Place: " + (appointment.place == null ? "none" : appointment.place) + "\n"
                + "  Repeats: " + describeRepeat(appointment.repeat) + "\n"
                + "----------\n";
    }

    /** For example "weekly on MONDAY, THURSDAY until 2026-12-31", or "no". */
    private static String describeRepeat(RepeatRule rule) {
        if (rule == null) {
            return "no";
        }
        String frequency = rule.frequency.name().toLowerCase(Locale.ROOT);
        String days = rule.daysOfWeek == null || rule.daysOfWeek.isEmpty()
                ? ""
                : " on " + rule.daysOfWeek.stream().map(Enum::name).collect(Collectors.joining(", "));
        String until = rule.until == null ? "" : " until " + rule.until;
        return frequency + days + until;
    }

    private void requireProfileSettings() {
        List<String> missingKeys = settings.findMissingKeys(REQUIRED_SETTINGS);
        if (!missingKeys.isEmpty()) {
            throw new ToolArgumentException(
                    "Missing settings: " + String.join(", ", missingKeys) + ". Add them to settings.properties.");
        }
    }

    private EmailAddress buildSenderAddress() {
        String fullName = settings.readValue("profile.name", "") + " " + settings.readValue("profile.surname", "");
        return new EmailAddress(fullName, settings.readValue("profile.email", ""));
    }
}
