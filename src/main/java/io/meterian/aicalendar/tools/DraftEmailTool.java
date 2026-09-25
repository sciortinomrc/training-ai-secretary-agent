package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.Attendee;
import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.calendar.Draft;
import io.meterian.aicalendar.email.DraftFolder;
import io.meterian.aicalendar.email.Email;
import java.util.List;

/**
 * Saves an email as a draft, or replaces the text of an existing draft. The email is an invitation to an
 * appointment, or a plain email to any recipients. It never sends anything.
 */
public class DraftEmailTool extends AbstractTool {

    private final CalendarService service;
    private final DraftEmailBuilder emailBuilder;
    private final DraftFolder draftFolder;

    public DraftEmailTool(CalendarService service, DraftEmailBuilder emailBuilder, DraftFolder draftFolder) {
        this.service = service;
        this.emailBuilder = emailBuilder;
        this.draftFolder = draftFolder;
    }

    @Override
    public String getName() {
        return "draft-email";
    }

    @Override
    public String getDescription() {
        return "Save an email as a draft in the drafts folder, or change an existing draft. Nothing is sent. Give "
                + "either appointmentId (an invitation to that appointment's attendees, with the calendar file "
                + "attached) or to (a plain email, not about an appointment). Write the complete subject and body, "
                + "with greeting and signature; call get-user-profile for the signature. To change a draft, pass "
                + "its draftId with the complete new text. Returns the draftId. To send the draft, call send-draft.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder()
                .addString("subject", "The email subject.", true)
                .addString("body", "The full email text, with greeting and signature.", true)
                .addString("appointmentId", "For an invitation: the appointment id, for example 'A-3'. It must "
                        + "have attendees.", false)
                .addProperty("to", buildRecipientsSchema(), false)
                .addString("draftId", "Only to change an existing draft: its id, for example 'D-1'.", false)
                .build();
    }

    @Override
    protected String run(ToolArguments arguments) {
        Draft draft = buildDraft(arguments);
        List<Email> emails = emailBuilder.buildEmails(draft);
        Draft savedDraft = service.saveDraft(draft);
        List<String> files = draftFolder.writeDraft(savedDraft.id, emails);

        ObjectNode result = Json.MAPPER.createObjectNode();
        result.put("draftId", savedDraft.id);
        result.set("files", Json.MAPPER.valueToTree(files));
        result.put("note", "This is a draft. Nothing was sent. To send it, call send-draft with this draftId.");
        return Json.writeJson(result);
    }

    private static Draft buildDraft(ToolArguments arguments) {
        Draft draft = new Draft();
        draft.id = arguments.readOptionalText("draftId");
        draft.appointmentId = arguments.readOptionalText("appointmentId");
        List<Attendee> recipients = arguments.readOptionalAttendees("to");
        if (recipients != null) {
            draft.recipients = recipients;
        }
        draft.subject = arguments.readRequiredText("subject");
        draft.body = arguments.readRequiredText("body");
        return draft;
    }

    /** The same shape as appointment attendees: a name and an email address each. */
    private static ObjectNode buildRecipientsSchema() {
        ObjectNode schema = SchemaBuilder.buildAttendeesSchema();
        schema.put("description", "For a plain email, not about an appointment: the recipients. Each needs a "
                + "name and an email address.");
        return schema;
    }
}
