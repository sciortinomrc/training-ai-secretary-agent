package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Settings;
import io.meterian.aicalendar.calendar.Appointment;
import io.meterian.aicalendar.calendar.Attendee;
import io.meterian.aicalendar.calendar.CalendarException;
import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.calendar.RepeatRule;
import io.meterian.aicalendar.email.Email;
import io.meterian.aicalendar.email.EmailAddress;
import io.meterian.aicalendar.email.EmailAttachment;
import io.meterian.aicalendar.email.EmailException;
import io.meterian.aicalendar.email.EmailSender;
import io.meterian.aicalendar.email.IcsBuilder;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Builds one invitation email per attendee of an appointment and hands each one to the EmailSender. */
public class SendInviteTool extends AbstractTool {

    static final List<String> REQUIRED_SETTINGS = List.of("profile.name", "profile.surname", "profile.email");
    private static final String INVITE_FILE_NAME = "invite.ics";
    private static final String INVITE_CONTENT_TYPE = "text/calendar; charset=UTF-8; method=REQUEST";
    /** Tells the model that the outbox file is the delivered email, not a queue waiting for another step. */
    private static final String DELIVERED_NOTE = "The emails are delivered. There is no later sending step.";

    private final CalendarService service;
    private final Settings settings;
    private final EmailSender sender;
    private final IcsBuilder icsBuilder;
    private final Clock clock;

    public SendInviteTool(CalendarService service, Settings settings, EmailSender sender, IcsBuilder icsBuilder,
            Clock clock) {
        this.service = service;
        this.settings = settings;
        this.sender = sender;
        this.icsBuilder = icsBuilder;
        this.clock = clock;
    }

    @Override
    public String getName() {
        return "send-invite";
    }

    @Override
    public String getDescription() {
        return "Email an invitation for an appointment to all its attendees, with an .ics calendar file attached. "
                + "Write the subject and the body yourself, including the greeting and the signature; call "
                + "get-user-profile for the signature. Use it only when the user asks. The user must approve "
                + "before the emails go out. In this project, saving the email as a file in the outbox folder IS "
                + "the delivery: after this tool succeeds, the email is sent and there is no later step.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder()
                .addString("appointmentId", "The appointment id, for example 'A-3'. It must have attendees.", true)
                .addString("subject", "The email subject.", true)
                .addString("body", "The full email text, with greeting and signature.", true)
                .build();
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    /** Shows the stored invitation data first, so the user approves what the attendees get, then each email. */
    @Override
    public String describeCall(JsonNode arguments) {
        ToolArguments toolArguments = new ToolArguments(arguments);
        List<Email> emails = buildEmails(toolArguments);
        Appointment appointment = service.findAppointment(toolArguments.readRequiredText("appointmentId"));
        StringBuilder preview = new StringBuilder(describeInvitation(appointment));
        for (Email email : emails) {
            preview.append("From: ").append(email.from.formatForDisplay()).append("\n")
                    .append("To: ").append(email.to.formatForDisplay()).append("\n")
                    .append("Subject: ").append(email.subject).append("\n\n")
                    .append(email.body).append("\n\n")
                    .append("Attachment: ").append(email.attachment.fileName).append("\n")
                    .append("----------\n");
        }
        return preview.toString();
    }

    @Override
    protected String run(ToolArguments arguments) {
        List<String> resultLines = new ArrayList<>();
        boolean anyEmailSent = false;
        for (Email email : buildEmails(arguments)) {
            String recipient = email.to.formatForDisplay();
            try {
                String destination = sender.send(email);
                resultLines.add("Sent to " + recipient + ". Delivered as " + destination + ".");
                anyEmailSent = true;
            } catch (EmailException e) {
                resultLines.add("FAILED for " + recipient + ": " + e.getMessage());
            }
        }
        if (anyEmailSent) {
            resultLines.add(DELIVERED_NOTE);
        }
        return String.join("\n", resultLines);
    }

    private List<Email> buildEmails(ToolArguments arguments) {
        requireProfileSettings();
        Appointment appointment = service.findAppointment(arguments.readRequiredText("appointmentId"));
        String subject = arguments.readRequiredText("subject");
        String body = arguments.readRequiredText("body");
        if (appointment.attendees.isEmpty()) {
            throw new CalendarException(appointment.id + " has no attendees. Add them with edit first.");
        }
        EmailAddress organizer = buildOrganizerAddress();
        List<Email> emails = new ArrayList<>();
        for (Attendee attendee : appointment.attendees) {
            String invite = icsBuilder.buildInvite(
                    appointment, attendee, organizer.name, organizer.address, clock.instant());
            emails.add(new Email(organizer, new EmailAddress(attendee.name, attendee.email), subject, body,
                    new EmailAttachment(INVITE_FILE_NAME, INVITE_CONTENT_TYPE, invite)));
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

    private EmailAddress buildOrganizerAddress() {
        String fullName = settings.readValue("profile.name", "") + " " + settings.readValue("profile.surname", "");
        return new EmailAddress(fullName, settings.readValue("profile.email", ""));
    }
}
