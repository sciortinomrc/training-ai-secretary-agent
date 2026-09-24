package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.JsonNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.Attendee;
import io.meterian.aicalendar.calendar.Frequency;
import io.meterian.aicalendar.calendar.RepeatRule;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Reads tool arguments from the model, with clear errors. A blank string counts as missing. */
public final class ToolArguments {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm[:ss]");

    private final JsonNode node;

    public ToolArguments(JsonNode node) {
        this.node = node == null || node.isNull() ? Json.MAPPER.createObjectNode() : node;
    }

    public boolean hasField(String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() && !(value.isTextual() && value.asText().isBlank());
    }

    public String readRequiredText(String field) {
        requireField(field);
        return readOptionalText(field);
    }

    public String readOptionalText(String field) {
        return hasField(field) ? node.get(field).asText().trim() : null;
    }

    public LocalDate readRequiredDate(String field) {
        requireField(field);
        return readOptionalDate(field);
    }

    public LocalDate readOptionalDate(String field) {
        String text = readOptionalText(field);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new ToolArgumentException(field + " must be a date in YYYY-MM-DD format, not '" + text + "'.");
        }
    }

    public LocalTime readRequiredTime(String field) {
        requireField(field);
        return readOptionalTime(field);
    }

    public LocalTime readOptionalTime(String field) {
        String text = readOptionalText(field);
        if (text == null) {
            return null;
        }
        try {
            return LocalTime.parse(text, TIME_FORMAT);
        } catch (DateTimeParseException e) {
            throw new ToolArgumentException(field + " must be a time in HH:mm (24-hour) format, not '" + text + "'.");
        }
    }

    public int readRequiredInteger(String field) {
        requireField(field);
        return readOptionalInteger(field);
    }

    public Integer readOptionalInteger(String field) {
        if (!hasField(field)) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value.isIntegralNumber() && value.canConvertToInt()) {
            return value.intValue();
        }
        if (value.isTextual()) {
            try {
                return Integer.parseInt(value.asText().trim());
            } catch (NumberFormatException e) {
                // falls through to the error below
            }
        }
        throw new ToolArgumentException(field + " must be a whole number, not '" + value.asText() + "'.");
    }

    public RepeatRule readOptionalRepeat(String field) {
        if (!hasField(field)) {
            return null;
        }
        JsonNode repeatNode = node.get(field);
        if (!repeatNode.isObject()) {
            throw new ToolArgumentException(field + " must be an object with frequency, daysOfWeek and until.");
        }
        ToolArguments inner = new ToolArguments(repeatNode);
        RepeatRule rule = new RepeatRule();
        rule.frequency = parseEnum(Frequency.class, inner.readRequiredText("frequency"), field + ".frequency");
        if (inner.hasField("daysOfWeek")) {
            JsonNode days = repeatNode.get("daysOfWeek");
            if (!days.isArray()) {
                throw new ToolArgumentException(field + ".daysOfWeek must be a list such as [\"MONDAY\"].");
            }
            rule.daysOfWeek = new ArrayList<>();
            for (JsonNode day : days) {
                rule.daysOfWeek.add(parseEnum(DayOfWeek.class, day.asText(), field + ".daysOfWeek"));
            }
        }
        rule.until = inner.readOptionalDate("until");
        return rule;
    }

    public List<Attendee> readOptionalAttendees(String field) {
        if (!hasField(field)) {
            return null;
        }
        JsonNode list = node.get(field);
        if (!list.isArray()) {
            throw new ToolArgumentException(field + " must be a list of {name, email}.");
        }
        List<Attendee> attendees = new ArrayList<>();
        for (JsonNode item : list) {
            ToolArguments attendee = new ToolArguments(item);
            attendees.add(new Attendee(attendee.readRequiredText("name"), attendee.readRequiredText("email")));
        }
        return attendees;
    }

    private void requireField(String field) {
        if (!hasField(field)) {
            throw new ToolArgumentException(field + " is missing. Ask the user for it.");
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String text, String field) {
        try {
            return Enum.valueOf(type, text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ToolArgumentException(
                    field + " must be one of " + Arrays.toString(type.getEnumConstants()) + ", not '" + text + "'.");
        }
    }
}
