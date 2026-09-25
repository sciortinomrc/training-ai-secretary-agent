package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.Appointment;
import io.meterian.aicalendar.calendar.Attendee;
import io.meterian.aicalendar.calendar.CalendarService;
import java.util.List;

public class SetAppointmentTool extends AbstractTool {

    private final CalendarService service;

    public SetAppointmentTool(CalendarService service) {
        this.service = service;
    }

    @Override
    public String getName() {
        return "set-appointment";
    }

    @Override
    public String getDescription() {
        return "Create an appointment. title, date and startTime are required. Without leadTimeMinutes, the "
                + "default alert time is used: do not ask for it. "
                + "It is rejected when the same appointment (same title and date) already exists, or when it overlaps "
                + "another appointment: overlaps are not allowed. "
                + "Derive the title from the request, for example 'Meeting with John Stone'. Ask the user for any "
                + "other missing value; never guess it. Returns the new appointment with its id.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder()
                .addString("title", "Short title, for example 'Dentist'.", true)
                .addString("date", "Date, YYYY-MM-DD. For a series, the first date.", true)
                .addString("startTime", "Start time, HH:mm, 24-hour.", true)
                .addInteger("leadTimeMinutes", "Optional. Minutes before startTime when the alert shows, 0 to "
                        + "10080. Leave it out unless the user asks for a lead time.", false)
                .addString("endTime", "Optional end time, HH:mm, 24-hour.", false)
                .addString("place", "Optional place.", false)
                .addProperty("repeat", SchemaBuilder.buildRepeatSchema(), false)
                .addProperty("attendees", SchemaBuilder.buildAttendeesSchema(), false)
                .build();
    }

    @Override
    protected String run(ToolArguments arguments) {
        Appointment appointment = new Appointment();
        appointment.title = arguments.readRequiredText("title");
        appointment.date = arguments.readRequiredDate("date");
        appointment.startTime = arguments.readRequiredTime("startTime");
        Integer leadTimeMinutes = arguments.readOptionalInteger("leadTimeMinutes");
        appointment.leadTimeMinutes = leadTimeMinutes != null
                ? leadTimeMinutes
                : GetDefaultLeadTimeTool.DEFAULT_LEAD_TIME_MINUTES;
        appointment.endTime = arguments.readOptionalTime("endTime");
        appointment.place = arguments.readOptionalText("place");
        appointment.repeat = arguments.readOptionalRepeat("repeat");
        List<Attendee> attendees = arguments.readOptionalAttendees("attendees");
        if (attendees != null) {
            appointment.attendees = attendees;
        }
        return Json.writeJson(service.addAppointment(appointment));
    }
}
