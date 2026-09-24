package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.calendar.Note;

public class AddNoteTool extends AbstractTool {

    private final CalendarService service;

    public AddNoteTool(CalendarService service) {
        this.service = service;
    }

    @Override
    public String getName() {
        return "add-note";
    }

    @Override
    public String getDescription() {
        return "Add a free-text note to a day (date) or to an appointment (appointmentId). Give exactly one of "
                + "the two. Returns the new note with its id.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder()
                .addString("text", "The note text.", true)
                .addString("date", "The day, YYYY-MM-DD.", false)
                .addString("appointmentId", "The appointment id, for example 'A-3'.", false)
                .build();
    }

    @Override
    protected String run(ToolArguments arguments) {
        Note note = new Note();
        note.text = arguments.readRequiredText("text");
        note.date = arguments.readOptionalDate("date");
        note.appointmentId = arguments.readOptionalText("appointmentId");
        return Json.writeJson(service.addNote(note));
    }
}
