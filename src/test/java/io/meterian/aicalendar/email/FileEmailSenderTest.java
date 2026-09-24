package io.meterian.aicalendar.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileEmailSenderTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    private final Clock clock = Clock.fixed(LocalDateTime.of(2026, 9, 24, 10, 0).atZone(ROME).toInstant(), ROME);

    @TempDir
    Path tempDir;

    private static Email buildEmailToAnna() {
        return new Email(
                new EmailAddress("Marco Rossi", "me@example.com"),
                new EmailAddress("Anna Rossi", "anna@example.com"),
                "Our meeting",
                "Dear Anna,\nSee you soon.",
                new EmailAttachment("invite.ics", "text/calendar; charset=UTF-8; method=REQUEST",
                        "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n"));
    }

    @Test
    void writesTheEmailAsAnEmlFile() throws Exception {
        Path outbox = tempDir.resolve("outbox");
        FileEmailSender sender = new FileEmailSender(outbox, new EmlFormatter(), clock);

        String destination = sender.send(buildEmailToAnna());

        Path file = outbox.resolve("20260924-100000-anna_example.com.eml");
        assertEquals(file.toString(), destination);
        assertTrue(Files.readString(file).contains("To: \"Anna Rossi\" <anna@example.com>"));
    }

    @Test
    void takenFileNameGetsANumber() {
        FileEmailSender sender = new FileEmailSender(tempDir.resolve("outbox"), new EmlFormatter(), clock);

        sender.send(buildEmailToAnna());
        String secondDestination = sender.send(buildEmailToAnna());

        assertTrue(secondDestination.endsWith("20260924-100000-anna_example.com-2.eml"), secondDestination);
    }

    @Test
    void unwritableOutboxThrowsEmailException() throws Exception {
        Path notAFolder = tempDir.resolve("afile");
        Files.writeString(notAFolder, "x");
        FileEmailSender sender = new FileEmailSender(notAFolder, new EmlFormatter(), clock);

        EmailException error = assertThrows(EmailException.class, () -> sender.send(buildEmailToAnna()));

        assertTrue(error.getMessage().startsWith("Could not write the email for anna@example.com"), error.getMessage());
    }
}
