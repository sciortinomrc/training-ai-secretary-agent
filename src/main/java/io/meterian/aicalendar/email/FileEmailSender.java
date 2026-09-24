package io.meterian.aicalendar.email;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes each email as a .eml file in the outbox folder. It takes the place of an SMTP server, so no email
 * leaves the computer.
 */
public class FileEmailSender implements EmailSender {

    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path outboxFolder;
    private final EmlFormatter formatter;
    private final Clock clock;

    public FileEmailSender(Path outboxFolder, EmlFormatter formatter, Clock clock) {
        this.outboxFolder = outboxFolder;
        this.formatter = formatter;
        this.clock = clock;
    }

    /** Returns the path of the written file. */
    @Override
    public String send(Email email) {
        ZonedDateTime sentAt = ZonedDateTime.now(clock);
        Path file = chooseFreeFilePath(sentAt, email.to.address);
        try {
            Files.createDirectories(outboxFolder);
            Files.writeString(file, formatter.formatEmail(email, sentAt), StandardCharsets.UTF_8);
            return file.toString();
        } catch (IOException e) {
            throw new EmailException("Could not write the email for " + email.to.address + " to " + file + ": "
                    + e.getMessage(), e);
        }
    }

    /** For example outbox/20260924-100000-anna_example.com.eml, then -2, -3 ... when the name is taken. */
    private Path chooseFreeFilePath(ZonedDateTime sentAt, String recipientAddress) {
        String baseName = FILE_TIME_FORMAT.format(sentAt) + "-" + recipientAddress.replaceAll("[^A-Za-z0-9.-]", "_");
        Path file = outboxFolder.resolve(baseName + ".eml");
        for (int copyNumber = 2; Files.exists(file); copyNumber++) {
            file = outboxFolder.resolve(baseName + "-" + copyNumber + ".eml");
        }
        return file;
    }
}
