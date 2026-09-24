package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import java.util.List;

/** Builds the JSON schema of a tool's parameters. */
public final class SchemaBuilder {

    private static final List<String> DAYS = List.of(
            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");

    private final ObjectNode properties = Json.MAPPER.createObjectNode();
    private final ArrayNode required = Json.MAPPER.createArrayNode();

    public SchemaBuilder addString(String name, String description, boolean isRequired) {
        return addProperty(name, buildTypedNode("string", description), isRequired);
    }

    public SchemaBuilder addInteger(String name, String description, boolean isRequired) {
        return addProperty(name, buildTypedNode("integer", description), isRequired);
    }

    public SchemaBuilder addEnum(String name, String description, List<String> values, boolean isRequired) {
        ObjectNode schema = buildTypedNode("string", description);
        schema.set("enum", Json.MAPPER.valueToTree(values));
        return addProperty(name, schema, isRequired);
    }

    public SchemaBuilder addProperty(String name, ObjectNode schema, boolean isRequired) {
        properties.set(name, schema);
        if (isRequired) {
            required.add(name);
        }
        return this;
    }

    public ObjectNode build() {
        ObjectNode schema = Json.MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", properties);
        if (required.size() > 0) {
            schema.set("required", required);
        }
        return schema;
    }

    public static ObjectNode buildTypedNode(String type, String description) {
        ObjectNode node = Json.MAPPER.createObjectNode();
        node.put("type", type);
        node.put("description", description);
        return node;
    }

    public static ObjectNode buildRepeatSchema() {
        ObjectNode dayItem = Json.MAPPER.createObjectNode();
        dayItem.put("type", "string");
        dayItem.set("enum", Json.MAPPER.valueToTree(DAYS));
        ObjectNode days = buildTypedNode("array", "Only for WEEKLY: the days, for example [\"MONDAY\", \"THURSDAY\"]. "
                + "Every weekday is MONDAY to FRIDAY.");
        days.set("items", dayItem);
        ObjectNode schema = new SchemaBuilder()
                .addEnum("frequency", "How often it repeats.", List.of("DAILY", "WEEKLY", "MONTHLY", "YEARLY"), true)
                .addProperty("daysOfWeek", days, false)
                .addString("until", "Optional last date, YYYY-MM-DD. Leave it out for no end.", false)
                .build();
        schema.put("description", "Optional repeat rule. Leave it out for a one-time item.");
        return schema;
    }

    public static ObjectNode buildAttendeesSchema() {
        ObjectNode item = new SchemaBuilder()
                .addString("name", "Full name of the attendee.", true)
                .addString("email", "Email address of the attendee. Never guess it.", true)
                .build();
        ObjectNode schema = buildTypedNode("array", "People to invite. Each needs a name and an email address.");
        schema.set("items", item);
        return schema;
    }
}
