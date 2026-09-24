package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.calendar.ItemChanges;

public class EditTool extends AbstractTool {

    private final CalendarService service;

    public EditTool(CalendarService service) {
        this.service = service;
    }

    @Override
    public String getName() {
        return "edit";
    }

    @Override
    public String getDescription() {
        return "Change an appointment, alarm or note. Give the id and only the fields to change. Without "
                + "occurrenceDate, the whole item or series changes. With occurrenceDate, only that one occurrence "
                + "of a repeating item changes. Before you call this, show the item to the user and get a yes.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder()
                .addString("id", "The item id, for example 'A-3', 'L-1' or 'N-2'.", true)
                .addString("occurrenceDate", "Only for repeating items: the original date of the one occurrence "
                        + "to change, YYYY-MM-DD. Leave it out to change the whole item.", false)
                .addString("title", "Appointment: new title.", false)
                .addString("date", "New date, YYYY-MM-DD.", false)
                .addString("startTime", "Appointment: new start time, HH:mm.", false)
                .addString("endTime", "Appointment: new end time, HH:mm.", false)
                .addString("place", "Appointment: new place.", false)
                .addInteger("leadTimeMinutes", "Appointment: new alert lead time in minutes.", false)
                .addProperty("attendees", buildReplacingAttendeesSchema(), false)
                .addProperty("repeat", SchemaBuilder.buildRepeatSchema(), false)
                .addString("message", "Alarm: new message.", false)
                .addString("time", "Fixed alarm: new time, HH:mm.", false)
                .addInteger("minutesBefore", "Linked alarm: new minutes before the appointment.", false)
                .addString("text", "Note: new text.", false)
                .addBoolean("allowOverlap",
                        "Set to true only after the user agreed to book it although it overlaps another appointment.", false)
                .build();
    }

    @Override
    protected String run(ToolArguments arguments) {
        ItemChanges changes = new ItemChanges();
        changes.title = arguments.readOptionalText("title");
        changes.date = arguments.readOptionalDate("date");
        changes.startTime = arguments.readOptionalTime("startTime");
        changes.endTime = arguments.readOptionalTime("endTime");
        changes.place = arguments.readOptionalText("place");
        changes.leadTimeMinutes = arguments.readOptionalInteger("leadTimeMinutes");
        changes.attendees = arguments.readOptionalAttendees("attendees");
        changes.repeat = arguments.readOptionalRepeat("repeat");
        changes.message = arguments.readOptionalText("message");
        changes.time = arguments.readOptionalTime("time");
        changes.minutesBefore = arguments.readOptionalInteger("minutesBefore");
        changes.text = arguments.readOptionalText("text");
        Object updated = service.editItem(arguments.readRequiredText("id"),
                arguments.readOptionalDate("occurrenceDate"), changes, arguments.readOptionalBoolean("allowOverlap"));
        return Json.writeJson(updated);
    }

    /** The same attendee list as in set-appointment, but it warns the model that the new list replaces the old one. */
    private static ObjectNode buildReplacingAttendeesSchema() {
        ObjectNode schema = SchemaBuilder.buildAttendeesSchema();
        schema.put("description", "The full new list of attendees. It replaces the current list, so include the "
                + "people who are already invited.");
        return schema;
    }
}
