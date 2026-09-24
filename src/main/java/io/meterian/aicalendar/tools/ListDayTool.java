package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.AlarmOccurrence;
import io.meterian.aicalendar.calendar.AppointmentOccurrence;
import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.calendar.DayAgenda;
import io.meterian.aicalendar.calendar.Note;
import java.time.LocalDate;

public class ListDayTool extends AbstractTool {

    private final CalendarService service;

    public ListDayTool(CalendarService service) {
        this.service = service;
    }

    @Override
    public String getName() {
        return "list-day";
    }

    @Override
    public String getDescription() {
        return "List everything on one day: appointments, alarms and notes, sorted by time. For a repeating "
                + "item, occurrenceDate is the date to pass to edit or remove for that one occurrence.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder().addString("date", "The day, YYYY-MM-DD.", true).build();
    }

    @Override
    protected String run(ToolArguments arguments) {
        LocalDate day = arguments.readRequiredDate("date");
        DayAgenda agenda = service.listDay(day);
        ObjectNode result = Json.MAPPER.createObjectNode();
        result.put("date", day.toString());
        result.put("dayOfWeek", day.getDayOfWeek().toString());
        addAppointments(result.putArray("appointments"), agenda);
        addAlarms(result.putArray("alarms"), agenda);
        addNotes(result.putArray("notes"), agenda);
        return Json.writeJson(result);
    }

    private static void addAppointments(ArrayNode appointments, DayAgenda agenda) {
        for (AppointmentOccurrence occurrence : agenda.appointments) {
            ObjectNode appointment = appointments.addObject();
            appointment.put("id", occurrence.appointment.id);
            appointment.put("title", occurrence.title);
            appointment.put("startTime", occurrence.startTime.toString());
            if (occurrence.endTime != null) {
                appointment.put("endTime", occurrence.endTime.toString());
            }
            if (occurrence.place != null) {
                appointment.put("place", occurrence.place);
            }
            appointment.put("leadTimeMinutes", occurrence.leadTimeMinutes);
            if (occurrence.appointment.repeat != null) {
                appointment.put("occurrenceDate", occurrence.originalDate.toString());
            }
            if (!occurrence.appointment.attendees.isEmpty()) {
                appointment.set("attendees", Json.MAPPER.valueToTree(occurrence.appointment.attendees));
            }
        }
    }

    private static void addAlarms(ArrayNode alarms, DayAgenda agenda) {
        for (AlarmOccurrence occurrence : agenda.alarms) {
            ObjectNode alarm = alarms.addObject();
            alarm.put("id", occurrence.alarm.id);
            alarm.put("time", occurrence.firesAt.toLocalTime().toString());
            alarm.put("message", occurrence.message);
            if (occurrence.alarm.isLinked()) {
                alarm.put("appointmentId", occurrence.alarm.appointmentId);
            }
            if (occurrence.alarm.repeat != null) {
                alarm.put("occurrenceDate", occurrence.originalDate.toString());
            }
        }
    }

    private static void addNotes(ArrayNode notes, DayAgenda agenda) {
        for (Note note : agenda.notes) {
            ObjectNode noteNode = notes.addObject();
            noteNode.put("id", note.id);
            noteNode.put("text", note.text);
            if (note.appointmentId != null) {
                noteNode.put("appointmentId", note.appointmentId);
            }
        }
    }
}
