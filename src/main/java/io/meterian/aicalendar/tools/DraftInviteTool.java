package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.Appointment;
import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.calendar.Draft;
import io.meterian.aicalendar.email.DraftFolder;
import io.meterian.aicalendar.email.Email;
import java.util.List;

/** Saves an invitation email as a draft, or replaces the text of an existing draft. It never sends anything. */
public class DraftInviteTool extends AbstractTool {

    private final CalendarService service;
    private final InviteEmailBuilder emailBuilder;
    private final DraftFolder draftFolder;

    public DraftInviteTool(CalendarService service, InviteEmailBuilder emailBuilder, DraftFolder draftFolder) {
        this.service = service;
        this.emailBuilder = emailBuilder;
        this.draftFolder = draftFolder;
    }

    @Override
    public String getName() {
        return "draft-invite";
    }

    @Override
    public String getDescription() {
        return "Save an invitation email for an appointment as a draft in the drafts folder, or change an existing "
                + "draft. Nothing is sent. Write the complete subject and body, with greeting and signature; call "
                + "get-user-profile for the signature. To change a draft, pass its draftId with the complete new "
                + "subject and body. Returns the draftId. To send the draft, call send-draft.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder()
                .addString("appointmentId", "The appointment id, for example 'A-3'. It must have attendees.", true)
                .addString("subject", "The email subject.", true)
                .addString("body", "The full email text, with greeting and signature.", true)
                .addString("draftId", "Only to change an existing draft: its id, for example 'D-1'.", false)
                .build();
    }

    @Override
    protected String run(ToolArguments arguments) {
        Appointment appointment = service.findAppointment(arguments.readRequiredText("appointmentId"));
        String subject = arguments.readRequiredText("subject");
        String body = arguments.readRequiredText("body");
        List<Email> emails = emailBuilder.buildEmails(appointment, subject, body);
        Draft draft = buildDraft(arguments.readOptionalText("draftId"), appointment, subject, body);
        Draft savedDraft = service.saveDraft(draft);
        List<String> files = draftFolder.writeDraft(savedDraft.id, emails);

        ObjectNode result = Json.MAPPER.createObjectNode();
        result.put("draftId", savedDraft.id);
        result.set("files", Json.MAPPER.valueToTree(files));
        result.put("note", "This is a draft. Nothing was sent. To send it, call send-draft with this draftId.");
        return Json.writeJson(result);
    }

    private static Draft buildDraft(String draftId, Appointment appointment, String subject, String body) {
        Draft draft = new Draft();
        draft.id = draftId;
        draft.appointmentId = appointment.id;
        draft.subject = subject;
        draft.body = body;
        return draft;
    }
}
