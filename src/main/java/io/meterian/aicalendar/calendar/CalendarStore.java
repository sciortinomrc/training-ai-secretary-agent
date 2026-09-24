package io.meterian.aicalendar.calendar;

import io.meterian.aicalendar.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Reads and writes calendar.json. A save creates missing folders, writes a temporary file first, then moves it
 * over the old file.
 */
public class CalendarStore {

    private final Path file;

    public CalendarStore(Path file) {
        this.file = file;
    }

    public CalendarData load() {
        if (!Files.exists(file)) {
            return new CalendarData();
        }
        try {
            return Json.MAPPER.readValue(file.toFile(), CalendarData.class);
        } catch (IOException e) {
            throw new CalendarFileException(
                    "Cannot read " + file + ": " + e.getMessage() + ". Fix or move the file, then start again.", e);
        }
    }

    public void save(CalendarData data) {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Json.MAPPER.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), data);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new CalendarFileException("Cannot save " + file + ": " + e.getMessage(), e);
        }
    }
}
