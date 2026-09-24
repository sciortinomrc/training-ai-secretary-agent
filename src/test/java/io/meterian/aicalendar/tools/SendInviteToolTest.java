package io.meterian.aicalendar.tools;

import static io.meterian.aicalendar.tools.CalendarToolsTest.CLOCK;
import static io.meterian.aicalendar.tools.CalendarToolsTest.buildService;
import static io.meterian.aicalendar.tools.CalendarToolsTest.parseArguments;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.meterian.aicalendar.Settings;
import io.meterian.aicalendar.calendar.CalendarException;
import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.email.Email;
import io.meterian.aicalendar.email.EmailException;
import io.meterian.aicalendar.email.EmailSender;
import io.meterian.aicalendar.email.IcsBuilder;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SendInviteToolTest {

    private static final String INVITE_ARGUMENTS =
            "{'appointmentId':'A-1','subject':'Business meeting','body':'Dear guest, I am thrilled to meet.'}";

    /** Records the emails instead of writing files; fails for the addresses in failFor. */
    private static class RecordingEmailSender implements EmailSender {
        final List<Email> sentEmails = new ArrayList<>();
        final Set<String> failFor = new HashSet<>();

        @Override
        public String send(Email email) {
            if (failFor.contains(email.to.address)) {
                throw new EmailException("Could not write the email for " + email.to.address + ": disk full", null);
            }
            sentEmails.add(email);
            return "outbox/" + email.to.address + ".eml";
        }
    }

    @TempDir
    Path tempDir;

    private CalendarService service;
    private final RecordingEmailSender sender = new RecordingEmailSender();

    @BeforeEach
    void createMeetingWithTwoAttendees() throws Exception {
        service = buildService(tempDir.resolve("calendar.json"));
        new SetAppointmentTool(service).execute(parseArguments(
                "{'title':'Business meeting','date':'2026-09-30','startTime':'10:00','leadTimeMinutes':30,"
                        + "'place':'Our office','attendees':[{'name':'Anna Rossi','email':'anna@example.com'},"
                        + "{'name':'Luca Bianchi','email':'luca@example.com'}]}"));
    }

    private static Settings buildCompleteSettings() {
        Properties values = new Properties();
        values.setProperty("profile.name", "Marco");
        values.setProperty("profile.surname", "Rossi");
        values.setProperty("profile.email", "me@example.com");
        return new Settings(values, new Properties());
    }

    private SendInviteTool buildTool(Settings settings) {
        return new SendInviteTool(service, settings, sender, new IcsBuilder(ZoneId.of("Europe/Rome")), CLOCK);
    }

    @Test
    void needsApproval() {
        assertTrue(buildTool(buildCompleteSettings()).requiresApproval());
    }

    @Test
    void describeCallShowsEveryEmail() throws Exception {
        String preview = buildTool(buildCompleteSettings()).describeCall(parseArguments(INVITE_ARGUMENTS));

        assertTrue(preview.contains("From: Marco Rossi <me@example.com>"));
        assertTrue(preview.contains("To: Anna Rossi <anna@example.com>"));
        assertTrue(preview.contains("To: Luca Bianchi <luca@example.com>"));
        assertTrue(preview.contains("Subject: Business meeting"));
        assertTrue(preview.contains("Dear guest, I am thrilled to meet."));
        assertTrue(sender.sentEmails.isEmpty());
    }

    @Test
    void describeCallShowsTheStoredInvitationDetails() throws Exception {
        String preview = buildTool(buildCompleteSettings()).describeCall(parseArguments(INVITE_ARGUMENTS));

        assertTrue(preview.contains("Title: Business meeting"), preview);
        assertTrue(preview.contains("Date: 2026-09-30"), preview);
        assertTrue(preview.contains("Time: 10:00, no end time"), preview);
        assertTrue(preview.contains("Place: Our office"), preview);
        assertTrue(preview.contains("Repeats: no"), preview);
    }

    @Test
    void sendsOneEmailPerAttendeeWithInvite() throws Exception {
        String result = buildTool(buildCompleteSettings()).execute(parseArguments(INVITE_ARGUMENTS));

        assertEquals("Sent to Anna Rossi <anna@example.com>. Delivered as outbox/anna@example.com.eml.\n"
                + "Sent to Luca Bianchi <luca@example.com>. Delivered as outbox/luca@example.com.eml.\n"
                + "The emails are delivered. There is no later sending step.", result);
        assertEquals(2, sender.sentEmails.size());
        Email first = sender.sentEmails.get(0);
        assertEquals("me@example.com", first.from.address);
        assertEquals("invite.ics", first.attachment.fileName);
        assertTrue(first.attachment.content.contains("METHOD:REQUEST"));
        assertTrue(first.attachment.content.contains("ATTENDEE;CN=\"Anna Rossi\""));
        assertTrue(first.attachment.content.contains("ORGANIZER;CN=\"Marco Rossi\":mailto:me@example.com"));
    }

    @Test
    void failureForOneAttendeeStillSendsTheOthers() throws Exception {
        sender.failFor.add("anna@example.com");

        String result = buildTool(buildCompleteSettings()).execute(parseArguments(INVITE_ARGUMENTS));

        assertTrue(result.startsWith("FAILED for Anna Rossi <anna@example.com>: Could not write"), result);
        assertTrue(result.contains("Sent to Luca Bianchi <luca@example.com>. Delivered as outbox/luca@example.com.eml."),
                result);
        assertEquals(1, sender.sentEmails.size());
    }

    @Test
    void missingSettingsAreNamed() throws Exception {
        Settings emptySettings = new Settings(new Properties(), new Properties());

        String result = buildTool(emptySettings).execute(parseArguments(INVITE_ARGUMENTS));

        assertEquals("ERROR: Missing settings: profile.name, profile.surname, profile.email. "
                + "Add them to settings.properties.", result);
    }

    @Test
    void appointmentWithoutAttendeesIsRejected() throws Exception {
        new SetAppointmentTool(service).execute(parseArguments(
                "{'title':'Alone','date':'2026-09-30','startTime':'12:00','leadTimeMinutes':30}"));
        SendInviteTool tool = buildTool(buildCompleteSettings());

        assertEquals("ERROR: A-2 has no attendees. Add them with edit first.", tool.execute(parseArguments(
                "{'appointmentId':'A-2','subject':'Hi','body':'Hello'}")));
        assertThrows(CalendarException.class, () -> tool.describeCall(parseArguments(
                "{'appointmentId':'A-2','subject':'Hi','body':'Hello'}")));
    }
}
