package io.meterian.aicalendar.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.Attendee;
import io.meterian.aicalendar.calendar.Frequency;
import io.meterian.aicalendar.calendar.RepeatRule;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolArgumentsTest {

    /** Single quotes keep the JSON readable in Java strings. */
    private static ToolArguments parseArguments(String singleQuotedJson) throws Exception {
        return new ToolArguments(Json.MAPPER.readTree(singleQuotedJson.replace('\'', '"')));
    }

    private static void assertArgumentError(String expectedMessage, Runnable action) {
        ToolArgumentException error = assertThrows(ToolArgumentException.class, action::run);
        assertEquals(expectedMessage, error.getMessage());
    }

    @Test
    void readsTextDateTimeAndInteger() throws Exception {
        ToolArguments arguments = parseArguments(
                "{'title':' Dentist ','date':'2026-09-30','startTime':'15:00','leadTimeMinutes':30}");

        assertEquals("Dentist", arguments.readRequiredText("title"));
        assertEquals(LocalDate.of(2026, 9, 30), arguments.readRequiredDate("date"));
        assertEquals(LocalTime.of(15, 0), arguments.readRequiredTime("startTime"));
        assertEquals(30, arguments.readRequiredInteger("leadTimeMinutes"));
        assertNull(arguments.readOptionalText("place"));
    }

    @Test
    void acceptsLooseNumberAndTimeFormats() throws Exception {
        ToolArguments arguments = parseArguments("{'lead':'30','early':'9:00','seconds':'15:00:00'}");

        assertEquals(30, arguments.readRequiredInteger("lead"));
        assertEquals(LocalTime.of(9, 0), arguments.readRequiredTime("early"));
        assertEquals(LocalTime.of(15, 0), arguments.readRequiredTime("seconds"));
    }

    @Test
    void missingOrBlankRequiredFieldIsNamed() throws Exception {
        ToolArguments arguments = parseArguments("{'title':'   '}");

        assertFalse(arguments.hasField("title"));
        assertArgumentError("title is missing. Ask the user for it.", () -> arguments.readRequiredText("title"));
        assertArgumentError("startTime is missing. Ask the user for it.",
                () -> arguments.readRequiredTime("startTime"));
    }

    @Test
    void badFormatsAreExplained() throws Exception {
        ToolArguments arguments = parseArguments("{'date':'30/09/2026','time':'3pm','lead':'soon'}");

        assertArgumentError("date must be a date in YYYY-MM-DD format, not '30/09/2026'.",
                () -> arguments.readRequiredDate("date"));
        assertArgumentError("time must be a time in HH:mm (24-hour) format, not '3pm'.",
                () -> arguments.readRequiredTime("time"));
        assertArgumentError("lead must be a whole number, not 'soon'.",
                () -> arguments.readRequiredInteger("lead"));
    }

    @Test
    void readsRepeatRuleWithLowercaseValues() throws Exception {
        ToolArguments arguments = parseArguments(
                "{'repeat':{'frequency':'weekly','daysOfWeek':['monday','Thursday'],'until':'2026-12-31'}}");

        RepeatRule rule = arguments.readOptionalRepeat("repeat");

        assertEquals(Frequency.WEEKLY, rule.frequency);
        assertEquals(List.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), rule.daysOfWeek);
        assertEquals(LocalDate.of(2026, 12, 31), rule.until);
        assertNull(parseArguments("{}").readOptionalRepeat("repeat"));
    }

    @Test
    void badFrequencyListsTheAllowedValues() throws Exception {
        ToolArguments arguments = parseArguments("{'repeat':{'frequency':'hourly'}}");

        assertArgumentError("repeat.frequency must be one of [DAILY, WEEKLY, MONTHLY, YEARLY], not 'hourly'.",
                () -> arguments.readOptionalRepeat("repeat"));
    }

    @Test
    void readsAttendeesAndNeedsBothFields() throws Exception {
        List<Attendee> attendees = parseArguments(
                "{'attendees':[{'name':'Anna Rossi','email':'anna@example.com'}]}").readOptionalAttendees("attendees");

        assertEquals("Anna Rossi", attendees.get(0).name);
        assertEquals("anna@example.com", attendees.get(0).email);

        ToolArguments noEmail = parseArguments("{'attendees':[{'name':'Anna Rossi'}]}");
        assertArgumentError("email is missing. Ask the user for it.", () -> noEmail.readOptionalAttendees("attendees"));
    }
}
