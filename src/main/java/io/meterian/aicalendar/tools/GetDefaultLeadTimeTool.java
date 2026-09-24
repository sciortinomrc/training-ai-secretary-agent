package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class GetDefaultLeadTimeTool extends AbstractTool {

    public static final int DEFAULT_LEAD_TIME_MINUTES = 30;

    @Override
    public String getName() {
        return "get-default-lead-time";
    }

    @Override
    public String getDescription() {
        return "Get the default number of minutes before an appointment when its alert shows. If the user gave "
                + "no lead time, call this and ask the user if the default is OK.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder().build();
    }

    @Override
    protected String run(ToolArguments arguments) {
        return String.valueOf(DEFAULT_LEAD_TIME_MINUTES);
    }
}
