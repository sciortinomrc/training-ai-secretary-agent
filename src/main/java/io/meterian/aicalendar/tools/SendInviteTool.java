package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Settings;
import io.meterian.aicalendar.calendar.Appointment;
import io.meterian.aicalendar.calendar.Attendee;
import io.meterian.aicalendar.calendar.CalendarException;
import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.email.Email;
import io.meterian.aicalendar.email.EmailAddress;
import io.meterian.aicalendar.email.EmailAttachment;
import io.meterian.aicalendar.email.EmailException;
import io.meterian.aicalendar.email.EmailSender;
import io.meterian.aicalendar.email.IcsBuilder;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/** Builds one invitation email per attendee of an appointment and hands each one to the EmailSender. */
public class SendInviteTool extends AbstractTool {

    static final List<String> REQUIRED_SETTINGS = List.of("profile.name", "profile.surname", "profile.email");
    private static final String INVITE_FILE_NAME = "invite.ics";
    private static final String INVITE_CONTENT_TYPE = "text/calendar; charset=UTF-8; method=REQUEST";

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
                + "before the emails go out. In this project each email is saved as a file in the outbox folder.";
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

    @Override
    public String describeCall(JsonNode arguments) {
        StringBuilder preview = new StringBuilder();
        for (Email email : buildEmails(new ToolArguments(arguments))) {
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
        for (Email email : buildEmails(arguments)) {
            String recipient = email.to.formatForDisplay();
            try {
                String destination = sender.send(email);
                resultLines.add("Sent to " + recipient + " (" + destination + ").");
            } catch (EmailException e) {
                resultLines.add("FAILED for " + recipient + ": " + e.getMessage());
            }
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
