package io.meterian.aicalendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsTest {

    @TempDir
    Path tempDir;

    @Test
    void loadReadsValuesFromFile() throws Exception {
        Path file = tempDir.resolve("settings.properties");
        Files.writeString(file, "ollama.model=file-model\nprofile.name=Marco\n");

        Settings settings = Settings.load(file, new Properties());

        assertEquals("file-model", settings.readValue("ollama.model", "default"));
        assertEquals("Marco", settings.findValue("profile.name").get());
    }

    @Test
    void systemValueReplacesFileValue() throws Exception {
        Path file = tempDir.resolve("settings.properties");
        Files.writeString(file, "ollama.model=file-model\n");
        Properties system = new Properties();
        system.setProperty("ollama.model", "system-model");

        Settings settings = Settings.load(file, system);

        assertEquals("system-model", settings.readValue("ollama.model", "default"));
    }

    @Test
    void blankValueCountsAsMissing() throws Exception {
        Path file = tempDir.resolve("settings.properties");
        Files.writeString(file, "profile.role=\nprofile.company=   \n");

        Settings settings = Settings.load(file, new Properties());

        assertEquals("fallback", settings.readValue("profile.role", "fallback"));
        assertTrue(settings.findValue("profile.company").isEmpty());
    }

    @Test
    void missingFileGivesDefaults() throws Exception {
        Settings settings = Settings.load(tempDir.resolve("absent.properties"), new Properties());

        assertEquals("calendar.json", settings.readValue("calendar.file", "calendar.json"));
    }

    @Test
    void findMissingKeysListsOnlyAbsentKeys() {
        Properties file = new Properties();
        file.setProperty("profile.name", "Marco");
        Settings settings = new Settings(file, new Properties());

        assertEquals(List.of("profile.surname"), settings.findMissingKeys(List.of("profile.name", "profile.surname")));
    }
}
