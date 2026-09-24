package io.meterian.aicalendar.email;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class EmlFormatterTest {

    @Test
    void formatsHeadersTextPartAndAttachment() {
        Email email = new Email(
                new EmailAddress("Marco Rossi", "me@example.com"),
                new EmailAddress("Anna Rossi", "anna@example.com"),
                "Our meeting",
                "Dear Anna,\nSee you soon.",
                new EmailAttachment("invite.ics", "text/calendar; charset=UTF-8; method=REQUEST",
                        "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n"));
        ZonedDateTime sentAt = ZonedDateTime.of(2026, 9, 24, 10, 0, 0, 0, ZoneId.of("Europe/Rome"));

        String eml = new EmlFormatter().formatEmail(email, sentAt);

        assertTrue(eml.startsWith("From: \"Marco Rossi\" <me@example.com>\r\n"));
        assertTrue(eml.contains("\r\nTo: \"Anna Rossi\" <anna@example.com>\r\n"));
        assertTrue(eml.contains("\r\nSubject: Our meeting\r\n"));
        assertTrue(eml.contains("\r\nDate: Thu, 24 Sep 2026 10:00:00 +0200\r\n"));
        assertTrue(eml.contains("\r\nContent-Type: multipart/mixed; boundary=\"ai-calendar-part-boundary\"\r\n"));
        assertTrue(eml.contains("\r\n\r\nDear Anna,\r\nSee you soon.\r\n"));
        assertTrue(eml.contains("\r\nContent-Type: text/calendar; charset=UTF-8; method=REQUEST\r\n"));
        assertTrue(eml.contains("\r\nContent-Disposition: attachment; filename=\"invite.ics\"\r\n"));
        assertTrue(eml.contains("\r\n\r\nBEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n"));
        assertTrue(eml.endsWith("\r\n--ai-calendar-part-boundary--\r\n"));
    }
}
