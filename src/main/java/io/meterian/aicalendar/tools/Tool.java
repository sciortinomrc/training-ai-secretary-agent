package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** A tool that the model can call. execute never throws: it returns JSON, or text that starts with "ERROR:". */
public interface Tool {

    String getName();

    String getDescription();

    ObjectNode buildParametersSchema();

    String execute(JsonNode arguments);

    /** True when the agent loop must ask the user before it runs this tool. */
    default boolean requiresApproval() {
        return false;
    }

    /**
     * True when the user's latest message already approves this call, for example "send it" for sending an email.
     * Then the agent loop does not ask again.
     */
    default boolean isApprovedByUserRequest(String userMessage) {
        return false;
    }

    /** The yes/no question the user answers before the call runs. It must say what "yes" does. */
    default String getApprovalQuestion() {
        return "Approve this action?";
    }

    /** The text that the user sees before approving a call. It may throw if the arguments are bad. */
    default String describeCall(JsonNode arguments) {
        return getName() + " " + arguments;
    }
}
