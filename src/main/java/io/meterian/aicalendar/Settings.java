package io.meterian.aicalendar;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

/** Settings from settings.properties. A system property with the same key replaces the file value. */
public final class Settings {

    private final Properties fileValues;
    private final Properties systemValues;

    public Settings(Properties fileValues, Properties systemValues) {
        this.fileValues = fileValues;
        this.systemValues = systemValues;
    }

    public static Settings load(Path file, Properties systemValues) throws IOException {
        Properties fileValues = new Properties();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                fileValues.load(reader);
            }
        }
        return new Settings(fileValues, systemValues);
    }

    public Optional<String> findValue(String key) {
        String value = systemValues.getProperty(key);
        if (value == null || value.isBlank()) {
            value = fileValues.getProperty(key);
        }
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }

    public String readValue(String key, String defaultValue) {
        return findValue(key).orElse(defaultValue);
    }

    public List<String> findMissingKeys(List<String> keys) {
        return keys.stream().filter(key -> findValue(key).isEmpty()).collect(Collectors.toList());
    }
}
