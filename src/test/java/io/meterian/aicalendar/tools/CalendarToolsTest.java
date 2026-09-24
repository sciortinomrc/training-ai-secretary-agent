package io.meterian.aicalendar.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.CalendarData;
import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.calendar.CalendarStore;
import io.meterian.aicalendar.calendar.OccurrenceExpander;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CalendarToolsTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    static final Clock CLOCK = Clock.fixed(LocalDateTime.of(2026, 9, 24, 10, 0).atZone(ROME).toInstant(), ROME);

    @TempDir
    Path tempDir;

    private CalendarService service;

    @BeforeEach
    void createService() {
        service = buildService(tempDir.resolve("calendar.json"));
    }

    static CalendarService buildService(Path file) {
        return new CalendarService(new CalendarStore(file), new CalendarData(), new OccurrenceExpander(), CLOCK);
    }

    static JsonNode parseArguments(String singleQuotedJson) throws Exception {
        return Json.MAPPER.readTree(singleQuotedJson.replace('\'', '"'));
    }

    @Test
    void setAppointmentCreatesItem() throws Exception {
        String result = new SetAppointmentTool(service).execute(parseArguments(
                "{'title':'Dentist','date':'2026-09-30','startTime':'15:00','leadTimeMinutes':30,'place':'Via Roma 10'}"));

        JsonNode appointment = Json.MAPPER.readTree(result);
        assertEquals("A-1", appointment.get("id").asText());
        assertEquals("15:00", appointment.get("startTime").asText());
        assertEquals("Via Roma 10", appointment.get("place").asText());
    }

    @Test
    void setAppointmentWithoutStartTimeAsksForIt() throws Exception {
        String result = new SetAppointmentTool(service).execute(parseArguments(
                "{'title':'Dentist','date':'2026-09-30','leadTimeMinutes':30}"));

        assertEquals("ERROR: startTime is missing. Ask the user for it.", result);
    }

    @Test
    void setAppointmentWithWeeklyRepeatAndAttendees() throws Exception {
        String result = new SetAppointmentTool(service).execute(parseArguments(
                "{'title':'Gym','date':'2026-09-28','startTime':'18:00','leadTimeMinutes':15,"
                        + "'repeat':{'frequency':'WEEKLY','daysOfWeek':['MONDAY']},"
                        + "'attendees':[{'name':'Anna Rossi','email':'anna@example.com'}]}"));

        JsonNode appointment = Json.MAPPER.readTree(result);
        assertEquals("WEEKLY", appointment.get("repeat").get("frequency").asText());
        assertEquals("anna@example.com", appointment.get("attendees").get(0).get("email").asText());
    }

    @Test
    void setAlarmSupportsBothForms() throws Exception {
        new SetAppointmentTool(service).execute(parseArguments(
                "{'title':'Dentist','date':'2026-09-30','startTime':'15:00','leadTimeMinutes':30}"));
        SetAlarmTool tool = new SetAlarmTool(service);

        assertEquals("L-1", Json.MAPPER.readTree(tool.execute(parseArguments(
                "{'message':'Wake up','date':'2026-09-25','time':'07:00'}"))).get("id").asText());
        assertEquals("L-2", Json.MAPPER.readTree(tool.execute(parseArguments(
                "{'message':'Leave now','appointmentId':'A-1','minutesBefore':45}"))).get("id").asText());
        assertEquals("ERROR: Give either date and time, or appointmentId and minutesBefore.",
                tool.execute(parseArguments("{'message':'Hello'}")));
    }

    @Test
    void addNoteToAppointment() throws Exception {
        new SetAppointmentTool(service).execute(parseArguments(
                "{'title':'Dentist','date':'2026-09-30','startTime':'15:00','leadTimeMinutes':30}"));

        JsonNode note = Json.MAPPER.readTree(new AddNoteTool(service).execute(parseArguments(
                "{'text':'Bring the X-ray','appointmentId':'A-1'}")));

        assertEquals("N-1", note.get("id").asText());
    }

    @Test
    void findItemsAddsTypeField() throws Exception {
        new SetAppointmentTool(service).execute(parseArguments(
                "{'title':'Dentist','date':'2026-09-30','startTime':'15:00','leadTimeMinutes':30}"));
        new SetAlarmTool(service).execute(parseArguments(
                "{'message':'Call the dentist','date':'2026-09-25','time':'09:00'}"));

        JsonNode items = Json.MAPPER.readTree(new FindItemsTool(service).execute(parseArguments("{'query':'dentist'}")));

        assertEquals(2, items.size());
        assertEquals("appointment", items.get(0).get("type").asText());
        assertEquals("alarm", items.get(1).get("type").asText());
    }

    @Test
    void listDayReturnsEntriesSortedByTime() throws Exception {
        SetAppointmentTool appointments = new SetAppointmentTool(service);
        appointments.execute(parseArguments(
                "{'title':'Gym','date':'2026-09-28','startTime':'18:00','leadTimeMinutes':15,"
                        + "'repeat':{'frequency':'WEEKLY','daysOfWeek':['MONDAY']}}"));
        appointments.execute(parseArguments(
                "{'title':'Dentist','date':'2026-09-28','startTime':'09:00','leadTimeMinutes':30}"));
        new SetAlarmTool(service).execute(parseArguments("{'message':'Wake up','date':'2026-09-28','time':'07:00'}"));
        new AddNoteTool(service).execute(parseArguments("{'text':'Pay rent','date':'2026-09-28'}"));

        JsonNode day = Json.MAPPER.readTree(new ListDayTool(service).execute(parseArguments("{'date':'2026-09-28'}")));

        assertEquals("MONDAY", day.get("dayOfWeek").asText());
        assertEquals("Dentist", day.get("appointments").get(0).get("title").asText());
        assertEquals("Gym", day.get("appointments").get(1).get("title").asText());
        assertEquals("2026-09-28", day.get("appointments").get(1).get("occurrenceDate").asText());
        assertTrue(day.get("appointments").get(0).get("occurrenceDate") == null);
        assertEquals("07:00", day.get("alarms").get(0).get("time").asText());
        assertEquals("Pay rent", day.get("notes").get(0).get("text").asText());
    }

    @Test
    void saveFailureBecomesErrorResult() throws Exception {
        Path notADirectory = tempDir.resolve("afile");
        Files.writeString(notADirectory, "x");
        CalendarService broken = buildService(notADirectory.resolve("calendar.json"));

        String result = new SetAppointmentTool(broken).execute(parseArguments(
                "{'title':'Dentist','date':'2026-09-30','startTime':'15:00','leadTimeMinutes':30}"));

        assertTrue(result.startsWith("ERROR: Cannot save"), result);
    }
}
