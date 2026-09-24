package io.meterian.aicalendar.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DraftFolderTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    @TempDir
    Path tempDir;

    private Path draftsPath;
    private DraftFolder draftFolder;

    @BeforeEach
    void createDraftFolder() {
        draftsPath = tempDir.resolve("outbox").resolve("drafts");
        Clock clock = Clock.fixed(LocalDateTime.of(2026, 9, 24, 10, 0).atZone(ROME).toInstant(), ROME);
        draftFolder = new DraftFolder(draftsPath, new EmlFormatter(), clock);
    }

    private static Email buildEmailTo(String name, String address) {
        return new Email(new EmailAddress("Marco Rossi", "me@example.com"), new EmailAddress(name, address),
                "Lunch", "See you.", new EmailAttachment("invite.ics", "text/calendar", "BEGIN:VCALENDAR\r\n"));
    }

    @Test
    void writesOneFilePerEmail() throws Exception {
        List<String> files = draftFolder.writeDraft("D-1", List.of(buildEmailTo("John Stone", "john@example.com")));

        Path file = draftsPath.resolve("D-1-john_example.com.eml");
        assertEquals(List.of(file.toString()), files);
        assertTrue(Files.readString(file).contains("To: \"John Stone\" <john@example.com>"));
    }

    @Test
    void rewritingADraftReplacesItsOldFiles() {
        draftFolder.writeDraft("D-1", List.of(buildEmailTo("John Stone", "john@example.com")));

        draftFolder.writeDraft("D-1", List.of(buildEmailTo("Anna Rossi", "anna@example.com")));

        assertFalse(Files.exists(draftsPath.resolve("D-1-john_example.com.eml")));
        assertTrue(Files.exists(draftsPath.resolve("D-1-anna_example.com.eml")));
    }

    @Test
    void deleteDraftKeepsTheOtherDrafts() {
        draftFolder.writeDraft("D-1", List.of(buildEmailTo("John Stone", "john@example.com")));
        draftFolder.writeDraft("D-10", List.of(buildEmailTo("John Stone", "john@example.com")));

        draftFolder.deleteDraft("D-1");

        assertFalse(Files.exists(draftsPath.resolve("D-1-john_example.com.eml")));
        assertTrue(Files.exists(draftsPath.resolve("D-10-john_example.com.eml")));
    }
}
