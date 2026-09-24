package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.CalendarService;

/** Lists the drafts that are not sent yet, with their full text, so the model can change or send them. */
public class ListDraftsTool extends AbstractTool {

    private final CalendarService service;

    public ListDraftsTool(CalendarService service) {
        this.service = service;
    }

    @Override
    public String getName() {
        return "list-drafts";
    }

    @Override
    public String getDescription() {
        return "List the email drafts that are not sent yet: id, appointmentId, subject and body. Use it to find "
                + "the draftId before you change or send a draft.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder().build();
    }

    @Override
    protected String run(ToolArguments arguments) {
        return Json.writeJson(service.listDrafts());
    }
}
