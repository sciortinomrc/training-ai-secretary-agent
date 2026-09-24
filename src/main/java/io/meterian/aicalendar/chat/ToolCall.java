package io.meterian.aicalendar.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/** One tool call from the model. In the Ollama native API, arguments is a JSON object, not a string. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolCall {
    public FunctionCall function;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FunctionCall {
        public String name;
        public JsonNode arguments;
    }

    public static ToolCall buildToolCall(String name, JsonNode arguments) {
        ToolCall call = new ToolCall();
        call.function = new FunctionCall();
        call.function.name = name;
        call.function.arguments = arguments;
        return call;
    }
}
