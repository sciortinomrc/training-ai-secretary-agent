package io.meterian.aicalendar.calendar;

import static io.meterian.aicalendar.calendar.CalendarServiceTest.buildAppointment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CalendarServiceDraftTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    @TempDir
    Path tempDir;

    private Path file;
    private CalendarService service;

    @BeforeEach
    void createServiceWithOneAppointment() {
        file = tempDir.resolve("calendar.json");
        Clock clock = Clock.fixed(LocalDateTime.of(2026, 9, 24, 10, 0).atZone(ROME).toInstant(), ROME);
        service = new CalendarService(new CalendarStore(file), new CalendarData(), new OccurrenceExpander(), clock);
        service.addAppointment(buildAppointment("Lunch with John", LocalDate.of(2026, 9, 28), LocalTime.of(12, 0)));
    }

    private static Draft buildDraft(String id, String appointmentId, String subject, String body) {
        Draft draft = new Draft();
        draft.id = id;
        draft.appointmentId = appointmentId;
        draft.subject = subject;
        draft.body = body;
        return draft;
    }

    private void assertRejected(String expectedMessage, Runnable action) {
        CalendarException error = assertThrows(CalendarException.class, action::run);
        assertEquals(expectedMessage, error.getMessage());
    }

    @Test
    void saveDraftAssignsAnIdAndSaves() {
        Draft saved = service.saveDraft(buildDraft(null, "A-1", "Lunch", "See you."));

        assertEquals("D-1", saved.id);
        assertEquals("See you.", new CalendarStore(file).load().drafts.get(0).body);
    }

    @Test
    void saveDraftWithAnIdReplacesThatDraft() {
        service.saveDraft(buildDraft(null, "A-1", "Lunch", "See you."));

        service.saveDraft(buildDraft("D-1", "A-1", "Lunch", "I may be late."));

        assertEquals(1, service.listDrafts().size());
        assertEquals("I may be late.", service.findDraft("D-1").body);
    }

    @Test
    void saveDraftChecksItsFields() {
        assertRejected("No item has the id A-9. Use find-items to get the id.",
                () -> service.saveDraft(buildDraft(null, "A-9", "Lunch", "See you.")));
        assertRejected("subject is missing. Ask the user for it.",
                () -> service.saveDraft(buildDraft(null, "A-1", null, "See you.")));
        assertRejected("No draft has the id D-9. Use list-drafts to get the id.",
                () -> service.saveDraft(buildDraft("D-9", "A-1", "Lunch", "See you.")));
    }

    @Test
    void draftNeedsEitherAnAppointmentOrRecipients() {
        Draft plainEmail = buildDraft(null, null, "Hello", "Hi Anna.");
        plainEmail.recipients = List.of(new Attendee("Anna Rossi", "anna@example.com"));
        assertEquals("D-1", service.saveDraft(plainEmail).id);

        assertRejected("Give either appointmentId or recipients.",
                () -> service.saveDraft(buildDraft(null, null, "Hello", "Hi.")));
        Draft both = buildDraft(null, "A-1", "Hello", "Hi.");
        both.recipients = List.of(new Attendee("Anna Rossi", "anna@example.com"));
        assertRejected("Give either appointmentId or recipients.", () -> service.saveDraft(both));
        Draft badAddress = buildDraft(null, null, "Hello", "Hi.");
        badAddress.recipients = List.of(new Attendee("Anna Rossi", "anna-at-example"));
        assertRejected("'anna-at-example' is not a valid email address.", () -> service.saveDraft(badAddress));
    }

    @Test
    void removeDraftDeletesIt() {
        service.saveDraft(buildDraft(null, "A-1", "Lunch", "See you."));

        service.removeDraft("D-1");

        assertTrue(service.listDrafts().isEmpty());
        assertTrue(new CalendarStore(file).load().drafts.isEmpty());
    }
}
