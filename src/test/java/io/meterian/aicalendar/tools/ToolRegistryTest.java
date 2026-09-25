package io.meterian.aicalendar.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.meterian.aicalendar.Json;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    private final Clock clock = Clock.fixed(LocalDateTime.of(2026, 9, 24, 10, 0).atZone(ROME).toInstant(), ROME);

    @Test
    void buildToolDefinitionsUsesOllamaFormat() {
        ToolRegistry registry = new ToolRegistry(List.of(new GetCurrentDateTimeTool(clock), new GetDefaultLeadTimeTool()));

        List<JsonNode> definitions = registry.buildToolDefinitions();

        assertEquals(2, definitions.size());
        JsonNode first = definitions.get(0);
        assertEquals("function", first.get("type").asText());
        assertEquals("get-current-date-time", first.get("function").get("name").asText());
        assertTrue(first.get("function").get("description").asText().length() > 20);
        assertEquals("object", first.get("function").get("parameters").get("type").asText());
    }

    @Test
    void findToolByName() {
        ToolRegistry registry = new ToolRegistry(List.of(new GetDefaultLeadTimeTool()));

        assertTrue(registry.findTool("get-default-lead-time").isPresent());
        assertTrue(registry.findTool("no-such-tool").isEmpty());
    }

    @Test
    void duplicateNamesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ToolRegistry(List.of(new GetDefaultLeadTimeTool(), new GetDefaultLeadTimeTool())));
    }

    @Test
    void currentDateTimeToolReportsClockValues() throws Exception {
        JsonNode result = Json.MAPPER.readTree(
                new GetCurrentDateTimeTool(clock).execute(Json.MAPPER.createObjectNode()));

        assertEquals("2026-09-24", result.get("date").asText());
        assertEquals("10:00", result.get("time").asText());
        assertEquals("THURSDAY", result.get("dayOfWeek").asText());
        assertEquals("Europe/Rome", result.get("timeZone").asText());
    }

    @Test
    void currentDateTimeToolListsTheNextDateOfEachWeekdayAfterToday() throws Exception {
        JsonNode nextDays = Json.MAPPER.readTree(
                new GetCurrentDateTimeTool(clock).execute(Json.MAPPER.createObjectNode())).get("nextDays");

        assertEquals("2026-09-25", nextDays.get("FRIDAY").asText());
        assertEquals("2026-09-28", nextDays.get("MONDAY").asText());
        assertEquals("2026-10-01", nextDays.get("THURSDAY").asText(), "today is Thursday, so the next one is a week later");
    }

    @Test
    void defaultLeadTimeToolReturnsThirty() {
        assertEquals("30", new GetDefaultLeadTimeTool().execute(Json.MAPPER.createObjectNode()));
    }
}
