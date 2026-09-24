package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.Alarm;
import io.meterian.aicalendar.calendar.Appointment;
import io.meterian.aicalendar.calendar.CalendarService;
import java.util.List;

public class FindItemsTool extends AbstractTool {

    private final CalendarService service;

    public FindItemsTool(CalendarService service) {
        this.service = service;
    }

    @Override
    public String getName() {
        return "find-items";
    }

    @Override
    public String getDescription() {
        return "Search appointments, alarms and notes. Use it to get the id before edit, remove or send-invite. "
                + "All parameters are optional. Returns the full items, each with a 'type' field.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder()
                .addString("query", "Text to search in titles, places, messages, notes and attendees.", false)
                .addEnum("type", "Only this kind of item.", List.of("appointment", "alarm", "note"), false)
                .addString("fromDate", "Only items that occur on or after this date, YYYY-MM-DD.", false)
                .addString("toDate", "Only items that occur on or before this date, YYYY-MM-DD.", false)
                .build();
    }

    @Override
    protected String run(ToolArguments arguments) {
        List<Object> items = service.findItems(
                arguments.readOptionalText("query"),
                arguments.readOptionalText("type"),
                arguments.readOptionalDate("fromDate"),
                arguments.readOptionalDate("toDate"));
        ArrayNode result = Json.MAPPER.createArrayNode();
        for (Object item : items) {
            ObjectNode node = Json.MAPPER.valueToTree(item);
            node.put("type", resolveItemType(item));
            result.add(node);
        }
        return Json.writeJson(result);
    }

    private static String resolveItemType(Object item) {
        if (item instanceof Appointment) {
            return "appointment";
        }
        if (item instanceof Alarm) {
            return "alarm";
        }
        return "note";
    }
}
