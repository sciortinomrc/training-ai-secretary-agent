package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.CalendarService;
import java.util.List;

public class RemoveTool extends AbstractTool {

    private final CalendarService service;

    public RemoveTool(CalendarService service) {
        this.service = service;
    }

    @Override
    public String getName() {
        return "remove";
    }

    @Override
    public String getDescription() {
        return "Remove an appointment, alarm or note. Without occurrenceDate, the whole item or series goes; "
                + "removing an appointment also removes its linked alarms and notes. With occurrenceDate, only "
                + "that one occurrence of a repeating item is cancelled. Before you call this, show the item to "
                + "the user and get a yes.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder()
                .addString("id", "The item id, for example 'A-3'.", true)
                .addString("occurrenceDate", "Only for repeating items: the original date of the one occurrence "
                        + "to cancel, YYYY-MM-DD.", false)
                .build();
    }

    @Override
    protected String run(ToolArguments arguments) {
        List<String> removed = service.removeItem(
                arguments.readRequiredText("id"), arguments.readOptionalDate("occurrenceDate"));
        ObjectNode result = Json.MAPPER.createObjectNode();
        result.set("removed", Json.MAPPER.valueToTree(removed));
        return Json.writeJson(result);
    }
}
