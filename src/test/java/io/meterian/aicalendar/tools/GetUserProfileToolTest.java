package io.meterian.aicalendar.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.Settings;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class GetUserProfileToolTest {

    @Test
    void returnsTheProfile() throws Exception {
        Properties values = new Properties();
        values.setProperty("profile.name", "Marco");
        values.setProperty("profile.surname", "Rossi");
        values.setProperty("profile.role", "Engineer");
        values.setProperty("profile.company", "Meterian");

        JsonNode profile = Json.MAPPER.readTree(new GetUserProfileTool(new Settings(values, new Properties()))
                .execute(Json.MAPPER.createObjectNode()));

        assertEquals("Marco", profile.get("name").asText());
        assertEquals("Rossi", profile.get("surname").asText());
        assertEquals("Engineer", profile.get("role").asText());
        assertEquals("Meterian", profile.get("company").asText());
    }

    @Test
    void missingValuesAreNamed() {
        Properties values = new Properties();
        values.setProperty("profile.name", "Marco");

        String result = new GetUserProfileTool(new Settings(values, new Properties()))
                .execute(Json.MAPPER.createObjectNode());

        assertEquals("ERROR: Missing settings: profile.surname, profile.role, profile.company. "
                + "Add them to settings.properties.", result);
    }
}
