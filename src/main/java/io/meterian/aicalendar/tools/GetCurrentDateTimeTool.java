package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

public class GetCurrentDateTimeTool extends AbstractTool {

    private final Clock clock;

    public GetCurrentDateTimeTool(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String getName() {
        return "get-current-date-time";
    }

    @Override
    public String getDescription() {
        return "Get the current date, time, day of the week and time zone. Call this before you turn a relative "
                + "date such as 'Wednesday', 'tomorrow' or 'next Monday' into a date. nextDays gives the date of "
                + "the next Monday to Sunday after today: a weekday name such as 'Friday' means that date, never "
                + "today, even when today is a Friday.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder().build();
    }

    @Override
    protected String run(ToolArguments arguments) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        ObjectNode result = Json.MAPPER.createObjectNode();
        result.put("date", now.toLocalDate().toString());
        result.put("time", now.toLocalTime().truncatedTo(ChronoUnit.MINUTES).toString());
        result.put("dayOfWeek", now.getDayOfWeek().toString());
        result.put("timeZone", now.getZone().getId());
        addNextDays(result.putObject("nextDays"), now.toLocalDate());
        return Json.writeJson(result);
    }

    /** For each weekday, its next date after today. On a Friday, "FRIDAY" is the Friday of next week. */
    private static void addNextDays(ObjectNode nextDays, LocalDate today) {
        for (DayOfWeek day : DayOfWeek.values()) {
            nextDays.put(day.name(), today.with(TemporalAdjusters.next(day)).toString());
        }
    }
}
