package io.meterian.aicalendar.tools;

import static io.meterian.aicalendar.tools.CalendarToolsTest.CLOCK;
import static io.meterian.aicalendar.tools.CalendarToolsTest.buildService;
import static io.meterian.aicalendar.tools.CalendarToolsTest.parseArguments;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.Settings;
import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.email.DraftFolder;
import io.meterian.aicalendar.email.EmlFormatter;
import io.meterian.aicalendar.email.IcsBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DraftInviteToolTest {

    private static final String FIRST_DRAFT =
            "{'appointmentId':'A-1','subject':'Lunch','body':'Dear John, see you there.'}";

    @TempDir
    Path tempDir;

    private CalendarService service;
    private Path draftsPath;
    private DraftFolder draftFolder;

    @BeforeEach
    void createLunchWithJohn() throws Exception {
        service = buildService(tempDir.resolve("calendar.json"));
        new SetAppointmentTool(service).execute(parseArguments(
                "{'title':'Lunch with John','date':'2026-09-28','startTime':'12:00','leadTimeMinutes':30,"
                        + "'place':'Olive Garden','attendees':[{'name':'John Stone','email':'john@example.com'}]}"));
        draftsPath = tempDir.resolve("outbox").resolve("drafts");
        draftFolder = new DraftFolder(draftsPath, new EmlFormatter(), CLOCK);
    }

    static Settings buildCompleteSettings() {
        Properties values = new Properties();
        values.setProperty("profile.name", "Marco");
        values.setProperty("profile.surname", "Rossi");
        values.setProperty("profile.email", "me@example.com");
        return new Settings(values, new Properties());
    }

    static InviteEmailBuilder buildEmailBuilder(Settings settings) {
        return new InviteEmailBuilder(settings, new IcsBuilder(ZoneId.of("Europe/Rome")), CLOCK);
    }

    private DraftInviteTool buildTool(Settings settings) {
        return new DraftInviteTool(service, buildEmailBuilder(settings), draftFolder);
    }

    @Test
    void savesADraftFileAndSendsNothing() throws Exception {
        JsonNode result = Json.MAPPER.readTree(
                buildTool(buildCompleteSettings()).execute(parseArguments(FIRST_DRAFT)));

        assertEquals("D-1", result.get("draftId").asText());
        assertTrue(result.get("note").asText().contains("Nothing was sent"));
        Path file = draftsPath.resolve("D-1-john_example.com.eml");
        assertTrue(Files.readString(file).contains("Dear John, see you there."));
        assertEquals(1, service.listDrafts().size());
    }

    @Test
    void editingADraftKeepsItsIdAndReplacesTheText() throws Exception {
        DraftInviteTool tool = buildTool(buildCompleteSettings());
        tool.execute(parseArguments(FIRST_DRAFT));

        JsonNode result = Json.MAPPER.readTree(tool.execute(parseArguments(
                "{'draftId':'D-1','appointmentId':'A-1','subject':'Lunch',"
                        + "'body':'Dear John, I may be late. Food is on me.'}")));

        assertEquals("D-1", result.get("draftId").asText());
        assertEquals(1, service.listDrafts().size());
        String eml = Files.readString(draftsPath.resolve("D-1-john_example.com.eml"));
        assertTrue(eml.contains("I may be late. Food is on me."));
        assertFalse(eml.contains("see you there"));
    }

    @Test
    void unknownDraftIdIsRejected() throws Exception {
        String result = buildTool(buildCompleteSettings()).execute(parseArguments(
                "{'draftId':'D-9','appointmentId':'A-1','subject':'Lunch','body':'Hello'}"));

        assertEquals("ERROR: No draft has the id D-9. Use list-drafts to get the id.", result);
    }

    @Test
    void missingSettingsAreNamed() throws Exception {
        String result = buildTool(new Settings(new Properties(), new Properties()))
                .execute(parseArguments(FIRST_DRAFT));

        assertEquals("ERROR: Missing settings: profile.name, profile.surname, profile.email. "
                + "Add them to settings.properties.", result);
    }

    @Test
    void listDraftsShowsTheSavedDraft() throws Exception {
        buildTool(buildCompleteSettings()).execute(parseArguments(FIRST_DRAFT));

        JsonNode drafts = Json.MAPPER.readTree(new ListDraftsTool(service).execute(parseArguments("{}")));

        assertEquals(1, drafts.size());
        assertEquals("D-1", drafts.get(0).get("id").asText());
        assertEquals("A-1", drafts.get(0).get("appointmentId").asText());
        assertEquals("Dear John, see you there.", drafts.get(0).get("body").asText());
    }
}
