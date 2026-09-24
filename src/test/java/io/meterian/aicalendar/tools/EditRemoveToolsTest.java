package io.meterian.aicalendar.tools;

import static io.meterian.aicalendar.tools.CalendarToolsTest.buildService;
import static io.meterian.aicalendar.tools.CalendarToolsTest.parseArguments;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.CalendarService;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditRemoveToolsTest {

    @TempDir
    Path tempDir;

    private CalendarService service;

    @BeforeEach
    void createWeeklyGymWithNote() throws Exception {
        service = buildService(tempDir.resolve("calendar.json"));
        new SetAppointmentTool(service).execute(parseArguments(
                "{'title':'Gym','date':'2026-09-28','startTime':'18:00','leadTimeMinutes':15,"
                        + "'repeat':{'frequency':'WEEKLY','daysOfWeek':['MONDAY']}}"));
        new AddNoteTool(service).execute(parseArguments("{'text':'Bring a towel','appointmentId':'A-1'}"));
    }

    private JsonNode listDay(String date) throws Exception {
        return Json.MAPPER.readTree(new ListDayTool(service).execute(parseArguments("{'date':'" + date + "'}")));
    }

    @Test
    void editWholeSeries() throws Exception {
        JsonNode updated = Json.MAPPER.readTree(new EditTool(service).execute(parseArguments(
                "{'id':'A-1','place':'FitLife','attendees':[{'name':'Anna Rossi','email':'anna@example.com'}]}")));

        assertEquals("FitLife", updated.get("place").asText());
        assertEquals("Anna Rossi", updated.get("attendees").get(0).get("name").asText());
    }

    @Test
    void editOneOccurrence() throws Exception {
        new EditTool(service).execute(parseArguments(
                "{'id':'A-1','occurrenceDate':'2026-10-05','startTime':'19:00'}"));

        assertEquals("19:00", listDay("2026-10-05").get("appointments").get(0).get("startTime").asText());
        assertEquals("18:00", listDay("2026-10-12").get("appointments").get(0).get("startTime").asText());
    }

    @Test
    void editErrorsComeBackAsText() throws Exception {
        EditTool tool = new EditTool(service);

        assertEquals("ERROR: No item has the id A-9. Use find-items to get the id.",
                tool.execute(parseArguments("{'id':'A-9','title':'X'}")));
        assertEquals("ERROR: id is missing. Ask the user for it.", tool.execute(parseArguments("{'title':'X'}")));
        assertEquals("ERROR: Give at least one field to change.", tool.execute(parseArguments("{'id':'A-1'}")));
    }

    @Test
    void removeOneOccurrence() throws Exception {
        JsonNode result = Json.MAPPER.readTree(new RemoveTool(service).execute(parseArguments(
                "{'id':'A-1','occurrenceDate':'2026-10-05'}")));

        assertEquals("A-1 on 2026-10-05", result.get("removed").get(0).asText());
        assertEquals(0, listDay("2026-10-05").get("appointments").size());
        assertEquals(1, listDay("2026-10-12").get("appointments").size());
    }

    @Test
    void removeWholeAppointmentListsLinkedItems() throws Exception {
        JsonNode result = Json.MAPPER.readTree(new RemoveTool(service).execute(parseArguments("{'id':'A-1'}")));

        assertEquals("A-1", result.get("removed").get(0).asText());
        assertEquals("N-1", result.get("removed").get(1).asText());
    }
}
