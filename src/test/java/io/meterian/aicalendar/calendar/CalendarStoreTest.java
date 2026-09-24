package io.meterian.aicalendar.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.meterian.aicalendar.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CalendarStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void missingFileGivesEmptyCalendar() {
        CalendarData data = new CalendarStore(tempDir.resolve("calendar.json")).load();

        assertTrue(data.appointments.isEmpty());
        assertTrue(data.alarms.isEmpty());
        assertTrue(data.notes.isEmpty());
    }

    @Test
    void saveThenLoadKeepsAllFields() throws Exception {
        Path file = tempDir.resolve("calendar.json");
        CalendarStore store = new CalendarStore(file);
        CalendarData data = new CalendarData();
        data.nextIds.put("A", 2);

        Appointment gym = new Appointment();
        gym.id = "A-1";
        gym.title = "Gym";
        gym.date = LocalDate.of(2026, 9, 28);
        gym.startTime = LocalTime.of(18, 0);
        gym.endTime = LocalTime.of(19, 0);
        gym.place = "FitLife";
        gym.leadTimeMinutes = 15;
        gym.attendees.add(new Attendee("Anna Rossi", "anna@example.com"));
        gym.repeat = new RepeatRule(Frequency.WEEKLY, List.of(DayOfWeek.MONDAY), LocalDate.of(2026, 12, 31));
        OccurrenceChange cancelled = new OccurrenceChange();
        cancelled.cancelled = true;
        gym.overrides.put(LocalDate.of(2026, 10, 5), cancelled);
        data.appointments.add(gym);

        Alarm linked = new Alarm();
        linked.id = "L-1";
        linked.message = "Pack the bag";
        linked.appointmentId = "A-1";
        linked.minutesBefore = 60;
        data.alarms.add(linked);

        Note note = new Note();
        note.id = "N-1";
        note.text = "Bring a towel";
        note.appointmentId = "A-1";
        data.notes.add(note);

        store.save(data);
        CalendarData loaded = store.load();

        Appointment loadedGym = loaded.appointments.get(0);
        assertEquals("Gym", loadedGym.title);
        assertEquals(LocalTime.of(18, 0), loadedGym.startTime);
        assertEquals(15, loadedGym.leadTimeMinutes);
        assertEquals("anna@example.com", loadedGym.attendees.get(0).email);
        assertEquals(List.of(DayOfWeek.MONDAY), loadedGym.repeat.daysOfWeek);
        assertTrue(loadedGym.overrides.get(LocalDate.of(2026, 10, 5)).cancelled);
        assertTrue(loaded.alarms.get(0).isLinked());
        assertEquals(60, loaded.alarms.get(0).minutesBefore);
        assertEquals("A-1", loaded.notes.get(0).appointmentId);
        assertEquals(2, loaded.nextIds.get("A"));

        JsonNode fileJson = Json.MAPPER.readTree(file.toFile());
        assertEquals("18:00", fileJson.get("appointments").get(0).get("startTime").asText());
        assertTrue(fileJson.get("appointments").get(0).get("overrides").has("2026-10-05"));
    }

    @Test
    void badFileThrowsAndIsNotOverwritten() throws Exception {
        Path file = tempDir.resolve("calendar.json");
        Files.writeString(file, "{ not json");

        assertThrows(CalendarFileException.class, () -> new CalendarStore(file).load());
        assertEquals("{ not json", Files.readString(file));
    }

    @Test
    void saveFailureThrowsCalendarFileException() throws Exception {
        Path notADirectory = tempDir.resolve("afile");
        Files.writeString(notADirectory, "x");
        CalendarStore store = new CalendarStore(notADirectory.resolve("calendar.json"));

        CalendarFileException error = assertThrows(CalendarFileException.class, () -> store.save(new CalendarData()));
        assertTrue(error.getMessage().startsWith("Cannot save"));
    }
}
