package io.meterian.aicalendar.tools;

import static io.meterian.aicalendar.tools.CalendarToolsTest.CLOCK;
import static io.meterian.aicalendar.tools.CalendarToolsTest.buildService;
import static io.meterian.aicalendar.tools.CalendarToolsTest.parseArguments;
import static io.meterian.aicalendar.tools.DraftInviteToolTest.buildCompleteSettings;
import static io.meterian.aicalendar.tools.DraftInviteToolTest.buildEmailBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.email.DraftFolder;
import io.meterian.aicalendar.email.Email;
import io.meterian.aicalendar.email.EmailException;
import io.meterian.aicalendar.email.EmailSender;
import io.meterian.aicalendar.email.EmlFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SendDraftToolTest {

    private static final String SEND_FIRST_DRAFT = "{'draftId':'D-1'}";

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
            return "outbox/sent/" + email.to.address + ".eml";
        }
    }

    @TempDir
    Path tempDir;

    private CalendarService service;
    private Path draftsPath;
    private DraftFolder draftFolder;
    private final RecordingEmailSender sender = new RecordingEmailSender();

    @BeforeEach
    void createMeetingWithADraft() throws Exception {
        service = buildService(tempDir.resolve("calendar.json"));
        new SetAppointmentTool(service).execute(parseArguments(
                "{'title':'Business meeting','date':'2026-09-30','startTime':'10:00','leadTimeMinutes':30,"
                        + "'place':'Our office','attendees':[{'name':'Anna Rossi','email':'anna@example.com'},"
                        + "{'name':'Luca Bianchi','email':'luca@example.com'}]}"));
        draftsPath = tempDir.resolve("outbox").resolve("drafts");
        draftFolder = new DraftFolder(draftsPath, new EmlFormatter(), CLOCK);
        new DraftInviteTool(service, buildEmailBuilder(buildCompleteSettings()), draftFolder).execute(parseArguments(
                "{'appointmentId':'A-1','subject':'Business meeting','body':'Dear guest, I am thrilled to meet.'}"));
    }

    private SendDraftTool buildTool() {
        return new SendDraftTool(service, buildEmailBuilder(buildCompleteSettings()), sender, draftFolder);
    }

    @Test
    void needsApproval() {
        assertTrue(buildTool().requiresApproval());
    }

    @Test
    void approvalQuestionSaysThatYesSendsTheEmail() {
        assertEquals("Send this email now?", buildTool().getApprovalQuestion());
    }

    @Test
    void describeCallShowsTheInvitationDetailsAndEveryEmail() throws Exception {
        String preview = buildTool().describeCall(parseArguments(SEND_FIRST_DRAFT));

        assertTrue(preview.contains("Title: Business meeting"), preview);
        assertTrue(preview.contains("Time: 10:00, no end time"), preview);
        assertTrue(preview.contains("Place: Our office"), preview);
        assertTrue(preview.contains("From: Marco Rossi <me@example.com>"), preview);
        assertTrue(preview.contains("To: Anna Rossi <anna@example.com>"), preview);
        assertTrue(preview.contains("To: Luca Bianchi <luca@example.com>"), preview);
        assertTrue(preview.contains("Dear guest, I am thrilled to meet."), preview);
        assertTrue(sender.sentEmails.isEmpty());
    }

    @Test
    void sendsTheDraftAndRemovesIt() throws Exception {
        String result = buildTool().execute(parseArguments(SEND_FIRST_DRAFT));

        assertEquals("Sent to Anna Rossi <anna@example.com>. Delivered as outbox/sent/anna@example.com.eml.\n"
                + "Sent to Luca Bianchi <luca@example.com>. Delivered as outbox/sent/luca@example.com.eml.\n"
                + "The emails are delivered. There is no later sending step.", result);
        assertEquals(2, sender.sentEmails.size());
        assertTrue(sender.sentEmails.get(0).attachment.content.contains("METHOD:REQUEST"));
        assertTrue(service.listDrafts().isEmpty());
        assertFalse(Files.exists(draftsPath.resolve("D-1-anna_example.com.eml")));
    }

    @Test
    void failureKeepsTheDraft() throws Exception {
        sender.failFor.add("anna@example.com");

        String result = buildTool().execute(parseArguments(SEND_FIRST_DRAFT));

        assertTrue(result.startsWith("FAILED for Anna Rossi <anna@example.com>: Could not write"), result);
        assertTrue(result.contains("Sent to Luca Bianchi <luca@example.com>."), result);
        assertTrue(result.endsWith("The draft D-1 is kept, because some emails were not sent."), result);
        assertEquals(1, service.listDrafts().size());
    }

    @Test
    void unknownDraftIsRejected() throws Exception {
        assertEquals("ERROR: No draft has the id D-9. Use list-drafts to get the id.",
                buildTool().execute(parseArguments("{'draftId':'D-9'}")));
    }
}
