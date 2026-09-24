package io.meterian.aicalendar;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** The one ObjectMapper of the application, and small helpers around it. */
public final class Json {

    public static final ObjectMapper MAPPER = buildMapper();

    private Json() {
    }

    private static ObjectMapper buildMapper() {
        JavaTimeModule timeModule = new JavaTimeModule();
        // Times are HH:mm everywhere: in the file, in tool results and in tool arguments.
        timeModule.addSerializer(LocalTime.class, new LocalTimeSerializer(DateTimeFormatter.ofPattern("HH:mm")));
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(timeModule);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        return mapper;
    }

    public static String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <T> T copyValue(T value, Class<T> type) {
        try {
            return MAPPER.readValue(MAPPER.writeValueAsBytes(value), type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
