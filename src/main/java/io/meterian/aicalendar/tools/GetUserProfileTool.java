package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.Settings;
import java.util.List;

public class GetUserProfileTool extends AbstractTool {

    private static final List<String> PROFILE_KEYS =
            List.of("profile.name", "profile.surname", "profile.role", "profile.company");

    private final Settings settings;

    public GetUserProfileTool(Settings settings) {
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "get-user-profile";
    }

    @Override
    public String getDescription() {
        return "Get the user's name, surname, role and company. Use them to sign emails. Never guess them.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder().build();
    }

    @Override
    protected String run(ToolArguments arguments) {
        List<String> missing = settings.findMissingKeys(PROFILE_KEYS);
        if (!missing.isEmpty()) {
            throw new ToolArgumentException(
                    "Missing settings: " + String.join(", ", missing) + ". Add them to settings.properties.");
        }
        ObjectNode profile = Json.MAPPER.createObjectNode();
        profile.put("name", settings.readValue("profile.name", ""));
        profile.put("surname", settings.readValue("profile.surname", ""));
        profile.put("role", settings.readValue("profile.role", ""));
        profile.put("company", settings.readValue("profile.company", ""));
        return Json.writeJson(profile);
    }
}
