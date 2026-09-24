package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.JsonNode;
import io.meterian.aicalendar.calendar.CalendarException;
import io.meterian.aicalendar.email.EmailException;

/** Base for all tools: turns argument, calendar and email errors into "ERROR: ..." results. */
public abstract class AbstractTool implements Tool {

    @Override
    public final String execute(JsonNode arguments) {
        try {
            return run(new ToolArguments(arguments));
        } catch (ToolArgumentException | CalendarException | EmailException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    protected abstract String run(ToolArguments arguments);
}
