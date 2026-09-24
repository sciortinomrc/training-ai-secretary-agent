package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Holds the tools, builds their definitions for the model, and finds a tool by name. */
public class ToolRegistry {

    private final Map<String, Tool> toolsByName = new LinkedHashMap<>();

    public ToolRegistry(List<Tool> tools) {
        for (Tool tool : tools) {
            if (toolsByName.put(tool.getName(), tool) != null) {
                throw new IllegalArgumentException("Two tools are named " + tool.getName());
            }
        }
    }

    public Optional<Tool> findTool(String name) {
        return Optional.ofNullable(toolsByName.get(name));
    }

    /** One {"type":"function","function":{name, description, parameters}} entry per tool. */
    public List<JsonNode> buildToolDefinitions() {
        List<JsonNode> definitions = new ArrayList<>();
        for (Tool tool : toolsByName.values()) {
            ObjectNode function = Json.MAPPER.createObjectNode();
            function.put("name", tool.getName());
            function.put("description", tool.getDescription());
            function.set("parameters", tool.buildParametersSchema());
            ObjectNode definition = Json.MAPPER.createObjectNode();
            definition.put("type", "function");
            definition.set("function", function);
            definitions.add(definition);
        }
        return definitions;
    }
}
