package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.Alarm;
import io.meterian.aicalendar.calendar.Appointment;
import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.calendar.Note;
import java.util.List;
import java.util.stream.Collectors;

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
                + "All parameters are optional. Returns the full items, each with a 'type' field. Each "
                + "appointment also lists its notes, which often say what it is about.";
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
        List<Object> allNotes = service.findItems(null, "note", null, null);
        ArrayNode result = Json.MAPPER.createArrayNode();
        for (Object item : items) {
            ObjectNode node = Json.MAPPER.valueToTree(item);
            node.put("type", resolveItemType(item));
            if (item instanceof Appointment) {
                node.set("notes", Json.MAPPER.valueToTree(listAttachedNoteTexts(allNotes, ((Appointment) item).id)));
            }
            result.add(node);
        }
        return Json.writeJson(result);
    }

    /** An appointment's notes often say what it is about, so they come with the appointment. */
    private static List<String> listAttachedNoteTexts(List<Object> notes, String appointmentId) {
        return notes.stream()
                .map(note -> (Note) note)
                .filter(note -> appointmentId.equals(note.appointmentId))
                .map(note -> note.text)
                .collect(Collectors.toList());
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
