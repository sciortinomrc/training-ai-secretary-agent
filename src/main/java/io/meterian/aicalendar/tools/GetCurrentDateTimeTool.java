package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

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
                + "date such as 'Wednesday', 'tomorrow' or 'next Monday' into a date.";
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
        return Json.writeJson(result);
    }
}
