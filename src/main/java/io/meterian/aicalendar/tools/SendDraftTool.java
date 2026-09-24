package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.calendar.Appointment;
import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.calendar.Draft;
import io.meterian.aicalendar.email.DraftFolder;
import io.meterian.aicalendar.email.Email;
import io.meterian.aicalendar.email.EmailException;
import io.meterian.aicalendar.email.EmailSender;
import java.util.ArrayList;
import java.util.List;

/**
 * Sends a saved draft to every attendee and moves it from the drafts folder to the sent folder. The agent loop
 * asks the user to approve it first.
 */
public class SendDraftTool extends AbstractTool {

    /** Tells the model that the sent file is the delivered email, not a queue waiting for another step. */
    private static final String DELIVERED_NOTE = "The emails are delivered. There is no later sending step.";

    private final CalendarService service;
    private final InviteEmailBuilder emailBuilder;
    private final EmailSender sender;
    private final DraftFolder draftFolder;

    public SendDraftTool(CalendarService service, InviteEmailBuilder emailBuilder, EmailSender sender,
            DraftFolder draftFolder) {
        this.service = service;
        this.emailBuilder = emailBuilder;
        this.sender = sender;
        this.draftFolder = draftFolder;
    }

    @Override
    public String getName() {
        return "send-draft";
    }

    @Override
    public String getDescription() {
        return "Send a saved draft to all attendees of its appointment. Call it as soon as the user asks to send: "
                + "do not ask for confirmation in chat, because the program shows the full email and asks the "
                + "user to approve it. When it succeeds, the emails are in the sent folder: they are delivered, "
                + "and there is no later step.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder().addString("draftId", "The draft id, for example 'D-1'.", true).build();
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    @Override
    public String describeCall(JsonNode arguments) {
        Draft draft = service.findDraft(new ToolArguments(arguments).readRequiredText("draftId"));
        Appointment appointment = service.findAppointment(draft.appointmentId);
        return emailBuilder.describePreview(appointment, buildDraftEmails(draft));
    }

    @Override
    protected String run(ToolArguments arguments) {
        Draft draft = service.findDraft(arguments.readRequiredText("draftId"));
        List<String> resultLines = new ArrayList<>();
        boolean allEmailsSent = true;
        for (Email email : buildDraftEmails(draft)) {
            String recipient = email.to.formatForDisplay();
            try {
                String destination = sender.send(email);
                resultLines.add("Sent to " + recipient + ". Delivered as " + destination + ".");
            } catch (EmailException e) {
                resultLines.add("FAILED for " + recipient + ": " + e.getMessage());
                allEmailsSent = false;
            }
        }
        resultLines.add(allEmailsSent ? moveDraftToSent(draft) : buildDraftKeptNote(draft));
        return String.join("\n", resultLines);
    }

    private List<Email> buildDraftEmails(Draft draft) {
        Appointment appointment = service.findAppointment(draft.appointmentId);
        return emailBuilder.buildEmails(appointment, draft.subject, draft.body);
    }

    /** The emails are in the sent folder now, so the draft and its files go away. */
    private String moveDraftToSent(Draft draft) {
        service.removeDraft(draft.id);
        draftFolder.deleteDraft(draft.id);
        return DELIVERED_NOTE;
    }

    private static String buildDraftKeptNote(Draft draft) {
        return "The draft " + draft.id + " is kept, because some emails were not sent.";
    }
}
