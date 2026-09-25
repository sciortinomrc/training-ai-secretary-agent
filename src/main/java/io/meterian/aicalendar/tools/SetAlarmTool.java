package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.Alarm;
import io.meterian.aicalendar.calendar.CalendarService;

public class SetAlarmTool extends AbstractTool {

    private final CalendarService service;

    public SetAlarmTool(CalendarService service) {
        this.service = service;
    }

    @Override
    public String getName() {
        return "set-alarm";
    }

    @Override
    public String getDescription() {
        return "Create an alarm: one alert at a time, with an optional short message. Use one of two forms. Fixed: date and time, "
                + "with an optional repeat. Linked: appointmentId and minutesBefore; it fires before each "
                + "occurrence of that appointment. Returns the new alarm with its id.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder()
                .addString("message", "Optional short message, for example 'Wake up'. Leave it out when the "
                        + "user gives none; never ask for one.", false)
                .addString("date", "Fixed form: date, YYYY-MM-DD.", false)
                .addString("time", "Fixed form: time, HH:mm, 24-hour.", false)
                .addProperty("repeat", SchemaBuilder.buildRepeatSchema(), false)
                .addString("appointmentId", "Linked form: the appointment id, for example 'A-3'.", false)
                .addInteger("minutesBefore", "Linked form: minutes before the appointment start, 0 to 10080.", false)
                .build();
    }

    @Override
    protected String run(ToolArguments arguments) {
        Alarm alarm = new Alarm();
        alarm.message = arguments.readOptionalText("message");
        alarm.date = arguments.readOptionalDate("date");
        alarm.time = arguments.readOptionalTime("time");
        alarm.repeat = arguments.readOptionalRepeat("repeat");
        alarm.appointmentId = arguments.readOptionalText("appointmentId");
        alarm.minutesBefore = arguments.readOptionalInteger("minutesBefore");
        return Json.writeJson(service.addAlarm(alarm));
    }
}
