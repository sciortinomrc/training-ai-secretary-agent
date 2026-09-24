# Calendar Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a terminal calendar agent in Java 11 that manages appointments, alarms and notes through hand-written LLM tool calls to Ollama, prints due alerts, and writes `.ics` email invitations as `.eml` files in an outbox folder (no email service).

**Architecture:** A hand-written agent loop (`Agent`) sends the conversation and the tool definitions to Ollama `/api/chat`, runs the tool calls that the model returns, and sends the results back until the model answers with text. Tools are thin adapters over `CalendarService`, which holds all calendar rules and saves to a JSON file through `CalendarStore`. A background `AlertScheduler` prints due alerts through the same `UserChannel` that the terminal uses.

**Tech Stack:** Java 11, Maven 3.6.3, `java.net.http.HttpClient`, Jackson 2.17 (+ jsr310), JUnit 5.10.

**Spec:** `docs/superpowers/specs/2026-09-24-calendar-agent-design.md`

## Global Constraints

- Java 11 only. Do not use `record`, text blocks, switch expressions, `instanceof` patterns, or `Stream.toList()`. Use `Collectors.toList()`.
- No agent or LLM library. The agent loop and the HTTP calls are hand-written.
- Dependencies: only `jackson-databind`, `jackson-datatype-jsr310` and `junit-jupiter` (test). No email library and no email service: emails go to `.eml` files.
- Base package: `io.meterian.aicalendar`.
- Model: `gpt-oss:120b-cloud` through `http://localhost:11434/api/chat`, `"stream": false`.
- Dates are `YYYY-MM-DD`. Times are `HH:mm`, 24-hour.
- A tool returns JSON text on success, and text that starts with `ERROR:` on failure. A tool never throws into the agent loop.
- Required-field error text: `<field> is missing. Ask the user for it.`
- `leadTimeMinutes` and `minutesBefore`: 0 to 10080 (7 days).
- Data classes use public fields, so Jackson reads and writes them without getters.
- Method names describe the action: `buildX` constructs, `findX` looks up something that exists, `listX` returns several, `readX` reads an argument. No bare-noun method names. `getX` only for a value that already exists.
- **Clean Code** (the user requires it):
  - Meaningful names for classes, methods, fields, parameters and local variables. No abbreviations such as `tzid`, `NL`, `r`, `tmp`. Lambda parameters are named after what they hold (`appointment -> ...`), except `e` for a caught exception.
  - Single responsibility: each class has one job, written in its class comment. Split a class when its comment needs "and".
  - Short methods that do one thing. When a method has steps, each step is a private method with a name that says what it does.
  - Human friendly: code reads top-down like prose; every message to the user or to the model is a plain, complete sentence that says what to do next.
- Every commit message ends with the line `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.

## Review Focus

1. **The model sends loose formats**: `"30"` instead of `30`, `"9:00"` or `"15:00:00"` instead of `"15:00"`. Expected: accepted. Test in Task 7 (`ToolArgumentsTest.acceptsLooseNumberAndTimeFormats`).
2. **The model sends a tool call with no `arguments`**. Expected: treated as `{}`, so the tool returns a normal missing-field error. Test in Task 10 (`AgentTest.missingArgumentsAreTreatedAsEmptyObject`).
3. **An alert time falls on the day before the appointment** (appointment at 00:15, 30-minute lead). Expected: the alert still fires at 23:45 the day before. Test in Task 11 (`AlertSchedulerTest.alertBeforeMidnightStillFires`).
4. **Saving the calendar fails** (bad path, no permission). Expected: the tool returns `ERROR: Cannot save ...` and the agent keeps running. Tests in Task 2 (`CalendarStoreTest.saveFailureThrowsCalendarFileException`) and Task 8 (`CalendarToolsTest.saveFailureBecomesErrorResult`).
5. **The model replies with empty text** (gpt-oss can return only `thinking`). Expected: the user sees a fallback sentence, not a blank line. Test in Task 10 (`AgentTest.emptyReplyGivesFallbackText`).

---

## File Structure

```
pom.xml
.gitignore
settings.example.properties
README.md
src/main/java/io/meterian/aicalendar/
  Main.java                      wires all parts, runs the terminal loop
  Settings.java                  settings.properties + -D overrides
  Json.java                      shared ObjectMapper, writeJson, copyValue
  calendar/
    Attendee.java  Frequency.java  RepeatRule.java  OccurrenceChange.java
    Appointment.java  Alarm.java  Note.java  CalendarData.java
    AppointmentOccurrence.java  AlarmOccurrence.java  DayAgenda.java  ItemChanges.java
    CalendarException.java  CalendarFileException.java
    ValuePicker.java             picks a changed value or the original one
    CalendarStore.java           reads/writes calendar.json (atomic)
    OccurrenceExpander.java      repeat rules + overrides -> dated occurrences
    ItemIds.java                 ID prefixes and the unknown-ID error
    CalendarRepository.java      in-memory data: find by ID, insert, save
    ItemValidator.java           calendar rules for new and changed items
    DateRange.java               inclusive range of days
    CalendarQueries.java         read-only questions: occurrences, one day, search
    ItemEditor.java              edits a whole item or one occurrence
    ItemRemover.java             removes a whole item or cancels one occurrence
    CalendarService.java         thread-safe entry point, hands work to the classes above
  chat/
    ChatMessage.java  ToolCall.java  ChatModel.java  ChatModelException.java
    OllamaClient.java            POST /api/chat
  tools/
    Tool.java  AbstractTool.java  ToolArguments.java  ToolArgumentException.java
    SchemaBuilder.java  ToolRegistry.java
    GetCurrentDateTimeTool.java  GetDefaultLeadTimeTool.java
    SetAppointmentTool.java  SetAlarmTool.java  AddNoteTool.java
    FindItemsTool.java  ListDayTool.java  EditTool.java  RemoveTool.java
    GetUserProfileTool.java  SendInviteTool.java
  agent/
    Agent.java                   tool-call loop, approval, round limit
    SystemPrompt.java
  channel/
    UserChannel.java  ConsoleChannel.java
  alerts/
    Alert.java  AlertScheduler.java
  email/
    Email.java  EmailAddress.java  EmailAttachment.java  EmailSender.java  EmailException.java
    IcsBuilder.java              .ics invitation text
    EmlFormatter.java            email -> .eml text
    FileEmailSender.java         writes .eml files to the outbox folder
src/test/java/io/meterian/aicalendar/
  SettingsTest.java
  MutableClock.java
  calendar/  CalendarStoreTest.java  OccurrenceExpanderTest.java
             CalendarServiceTest.java  CalendarServiceEditTest.java
  chat/      OllamaClientTest.java
  tools/     ToolArgumentsTest.java  ToolRegistryTest.java  CalendarToolsTest.java
             EditRemoveToolsTest.java  GetUserProfileToolTest.java  SendInviteToolTest.java
  agent/     AgentTest.java
  channel/   RecordingUserChannel.java  ConsoleChannelTest.java
  alerts/    AlertSchedulerTest.java
  email/     IcsBuilderTest.java  EmlFormatterTest.java  FileEmailSenderTest.java
```

---

### Task 1: Project skeleton and settings

**Files:**
- Create: `pom.xml`, `.gitignore`, `settings.example.properties`
- Create: `src/main/java/io/meterian/aicalendar/Settings.java`
- Test: `src/test/java/io/meterian/aicalendar/SettingsTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `new Settings(Properties fileValues, Properties systemValues)`
  - `static Settings Settings.load(Path file, Properties systemValues) throws IOException`
  - `Optional<String> findValue(String key)` — system value first, then file value; blank counts as missing.
  - `String readValue(String key, String defaultValue)`
  - `List<String> findMissingKeys(List<String> keys)`

- [ ] **Step 1: Start the git repository**

The folder is not a git repository yet. Run:

```bash
cd /home/marco/projects/meterian/training/ai/ai-calendar
git init
```

Expected: `Initialized empty Git repository`.

- [ ] **Step 2: Create `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>io.meterian</groupId>
  <artifactId>ai-calendar</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <packaging>jar</packaging>

  <properties>
    <maven.compiler.release>11</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <jackson.version>2.17.2</jackson.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
      <version>${jackson.version}</version>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.datatype</groupId>
      <artifactId>jackson-datatype-jsr310</artifactId>
      <version>${jackson.version}</version>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.10.3</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.13.0</version>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.2.5</version>
      </plugin>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.4.1</version>
        <configuration>
          <mainClass>io.meterian.aicalendar.Main</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 3: Create `.gitignore`**

```
target/
settings.properties
calendar.json
calendar.json.tmp
outbox/
```

- [ ] **Step 4: Create `settings.example.properties`**

```properties
# Copy this file to settings.properties and fill in the values.
# settings.properties holds personal data. Do not commit it.
# A -Dkey=value option on the command line replaces a value from the file.

ollama.url=http://localhost:11434
ollama.model=gpt-oss:120b-cloud
calendar.file=calendar.json

profile.name=
profile.surname=
profile.role=
profile.company=
# Sender address of the invitations
profile.email=

# Emails are written as .eml files to this folder. No email leaves the computer.
outbox.folder=outbox
```

- [ ] **Step 5: Write the failing test**

`src/test/java/io/meterian/aicalendar/SettingsTest.java`:

```java
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
```

- [ ] **Step 6: Run the test to verify it fails**

Run: `mvn -q test -Dtest=SettingsTest`
Expected: COMPILATION ERROR, `cannot find symbol: class Settings`.

- [ ] **Step 7: Write the implementation**

`src/main/java/io/meterian/aicalendar/Settings.java`:

```java
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
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `mvn -q test -Dtest=SettingsTest`
Expected: PASS (5 tests). The first run downloads the dependencies.

- [ ] **Step 9: Commit**

```bash
git add pom.xml .gitignore settings.example.properties CLAUDE.md docs src
git commit -m "feat: add Maven project skeleton and settings loader" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Data model, JSON mapper and calendar file

**Files:**
- Create: `src/main/java/io/meterian/aicalendar/Json.java`
- Create in `src/main/java/io/meterian/aicalendar/calendar/`: `Attendee.java`, `Frequency.java`, `RepeatRule.java`, `OccurrenceChange.java`, `Appointment.java`, `Alarm.java`, `Note.java`, `CalendarData.java`, `CalendarException.java`, `CalendarFileException.java`, `CalendarStore.java`
- Test: `src/test/java/io/meterian/aicalendar/calendar/CalendarStoreTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `Json.MAPPER` (JavaTimeModule, ISO dates, `NON_EMPTY` inclusion, ignores unknown fields), `static String Json.writeJson(Object)`, `static <T> T Json.copyValue(T value, Class<T> type)` (deep copy).
  - Data classes with public fields, exactly as in the code below.
  - `Alarm.isLinked()` — true when `appointmentId != null`.
  - `CalendarException extends RuntimeException` (message is shown to the model). `CalendarFileException extends CalendarException`.
  - `new CalendarStore(Path file)`, `CalendarData load()`, `void save(CalendarData data)`.

- [ ] **Step 1: Write the failing test**

`src/test/java/io/meterian/aicalendar/calendar/CalendarStoreTest.java`:

```java
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=CalendarStoreTest`
Expected: COMPILATION ERROR, `cannot find symbol: class CalendarData`.

- [ ] **Step 3: Write `Json.java`**

`src/main/java/io/meterian/aicalendar/Json.java`:

```java
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
```

- [ ] **Step 4: Write the data classes**

`src/main/java/io/meterian/aicalendar/calendar/Attendee.java`:

```java
package io.meterian.aicalendar.calendar;

public class Attendee {
    public String name;
    public String email;

    public Attendee() {
    }

    public Attendee(String name, String email) {
        this.name = name;
        this.email = email;
    }
}
```

`src/main/java/io/meterian/aicalendar/calendar/Frequency.java`:

```java
package io.meterian.aicalendar.calendar;

public enum Frequency {
    DAILY, WEEKLY, MONTHLY, YEARLY
}
```

`src/main/java/io/meterian/aicalendar/calendar/RepeatRule.java`:

```java
package io.meterian.aicalendar.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class RepeatRule {
    public Frequency frequency;
    /** Only for WEEKLY. */
    public List<DayOfWeek> daysOfWeek;
    /** Last possible date. Null means no end. */
    public LocalDate until;

    public RepeatRule() {
    }

    public RepeatRule(Frequency frequency, List<DayOfWeek> daysOfWeek, LocalDate until) {
        this.frequency = frequency;
        this.daysOfWeek = daysOfWeek == null ? null : new ArrayList<>(daysOfWeek);
        this.until = until;
    }
}
```

`src/main/java/io/meterian/aicalendar/calendar/OccurrenceChange.java`:

```java
package io.meterian.aicalendar.calendar;

import java.time.LocalDate;
import java.time.LocalTime;

/** A change to one occurrence of a series. A null field keeps the series value. */
public class OccurrenceChange {
    public Boolean cancelled;
    public LocalDate date;
    public LocalTime startTime;
    public LocalTime endTime;
    public String place;
    public String title;
    public Integer leadTimeMinutes;
    public LocalTime time;
    public String message;
}
```

`src/main/java/io/meterian/aicalendar/calendar/Appointment.java`:

```java
package io.meterian.aicalendar.calendar;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Appointment {
    public String id;
    public String title;
    /** For a series, the date of the first occurrence. */
    public LocalDate date;
    public LocalTime startTime;
    public LocalTime endTime;
    public String place;
    public Integer leadTimeMinutes;
    public List<Attendee> attendees = new ArrayList<>();
    public RepeatRule repeat;
    /** Key: the original occurrence date. */
    public Map<LocalDate, OccurrenceChange> overrides = new TreeMap<>();
}
```

`src/main/java/io/meterian/aicalendar/calendar/Alarm.java`:

```java
package io.meterian.aicalendar.calendar;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.TreeMap;

/** Either fixed (date + time, optional repeat) or linked (appointmentId + minutesBefore). */
public class Alarm {
    public String id;
    public String message;
    public LocalDate date;
    public LocalTime time;
    public RepeatRule repeat;
    public Map<LocalDate, OccurrenceChange> overrides = new TreeMap<>();
    public String appointmentId;
    public Integer minutesBefore;

    @JsonIgnore
    public boolean isLinked() {
        return appointmentId != null;
    }
}
```

`src/main/java/io/meterian/aicalendar/calendar/Note.java`:

```java
package io.meterian.aicalendar.calendar;

import java.time.LocalDate;

/** Attached to a day (date) or to an appointment (appointmentId), never both. */
public class Note {
    public String id;
    public String text;
    public LocalDate date;
    public String appointmentId;
}
```

`src/main/java/io/meterian/aicalendar/calendar/CalendarData.java`:

```java
package io.meterian.aicalendar.calendar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Everything in calendar.json. */
public class CalendarData {
    /** Next number for each ID prefix: A, L, N. */
    public Map<String, Integer> nextIds = new TreeMap<>();
    public List<Appointment> appointments = new ArrayList<>();
    public List<Alarm> alarms = new ArrayList<>();
    public List<Note> notes = new ArrayList<>();
}
```

`src/main/java/io/meterian/aicalendar/calendar/CalendarException.java`:

```java
package io.meterian.aicalendar.calendar;

/** A calendar rule was broken. The message goes to the model, so it says what to do. */
public class CalendarException extends RuntimeException {

    public CalendarException(String message) {
        super(message);
    }

    public CalendarException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

`src/main/java/io/meterian/aicalendar/calendar/CalendarFileException.java`:

```java
package io.meterian.aicalendar.calendar;

public class CalendarFileException extends CalendarException {

    public CalendarFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 5: Write `CalendarStore.java`**

`src/main/java/io/meterian/aicalendar/calendar/CalendarStore.java`:

```java
package io.meterian.aicalendar.calendar;

import io.meterian.aicalendar.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Reads and writes calendar.json. A save writes a temporary file first, then moves it over the old file. */
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
            Json.MAPPER.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), data);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new CalendarFileException("Cannot save " + file + ": " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `mvn -q test -Dtest=CalendarStoreTest`
Expected: PASS (4 tests).

- [ ] **Step 7: Commit**

```bash
git add src
git commit -m "feat: add calendar data model and JSON file store" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Occurrence expander (repeat rules and overrides)

**Files:**
- Create in `src/main/java/io/meterian/aicalendar/calendar/`: `AppointmentOccurrence.java`, `AlarmOccurrence.java`, `ValuePicker.java`, `OccurrenceExpander.java`
- Test: `src/test/java/io/meterian/aicalendar/calendar/OccurrenceExpanderTest.java`

**Interfaces:**
- Consumes: data classes from Task 2.
- Produces:
  - `AppointmentOccurrence` — public final fields `appointment`, `originalDate`, `date`, `startTime`, `endTime`, `title`, `place`, `int leadTimeMinutes`; methods `LocalDateTime computeStart()`, `LocalDateTime computeAlertTime()`.
  - `AlarmOccurrence` — public final fields `alarm`, `originalDate`, `LocalDateTime firesAt`, `message`.
  - `OccurrenceExpander`:
    - `boolean isSeriesDate(LocalDate start, RepeatRule rule, LocalDate day)`
    - `List<AppointmentOccurrence> expandAppointment(Appointment a, LocalDate from, LocalDate to)` — inclusive range, sorted by start.
    - `List<AlarmOccurrence> expandFixedAlarm(Alarm alarm, LocalDate from, LocalDate to)` — sorted by `firesAt`.
    - `List<AlarmOccurrence> expandLinkedAlarm(Alarm alarm, List<AppointmentOccurrence> occurrences)`
  - `ValuePicker.pickChangedValue(T changed, T original)` — package-private static; returns `changed` when it is not null.

- [ ] **Step 1: Write the failing test**

`src/test/java/io/meterian/aicalendar/calendar/OccurrenceExpanderTest.java`:

```java
package io.meterian.aicalendar.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OccurrenceExpanderTest {

    private final OccurrenceExpander expander = new OccurrenceExpander();

    private static Appointment buildAppointment(LocalDate date, LocalTime start, RepeatRule repeat) {
        Appointment appointment = new Appointment();
        appointment.id = "A-1";
        appointment.title = "Gym";
        appointment.date = date;
        appointment.startTime = start;
        appointment.leadTimeMinutes = 30;
        appointment.repeat = repeat;
        return appointment;
    }

    private List<LocalDate> listDates(Appointment appointment, LocalDate from, LocalDate to) {
        return expander.expandAppointment(appointment, from, to).stream()
                .map(occurrence -> occurrence.date)
                .collect(Collectors.toList());
    }

    @Test
    void oneTimeAppointmentOccursOnlyOnItsDate() {
        Appointment dentist = buildAppointment(LocalDate.of(2026, 9, 30), LocalTime.of(15, 0), null);

        assertEquals(List.of(LocalDate.of(2026, 9, 30)),
                listDates(dentist, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31)));
        assertTrue(listDates(dentist, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).isEmpty());
    }

    @Test
    void dailyRepeatStopsAtUntil() {
        Appointment daily = buildAppointment(LocalDate.of(2026, 9, 24), LocalTime.of(7, 0),
                new RepeatRule(Frequency.DAILY, null, LocalDate.of(2026, 9, 26)));

        assertEquals(List.of(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 26)),
                listDates(daily, LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 30)));
    }

    @Test
    void weeklyRepeatUsesSelectedDays() {
        Appointment weekly = buildAppointment(LocalDate.of(2026, 9, 24), LocalTime.of(18, 0),
                new RepeatRule(Frequency.WEEKLY, List.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), null));

        assertEquals(List.of(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 28),
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)),
                listDates(weekly, LocalDate.of(2026, 9, 24), LocalDate.of(2026, 10, 5)));
    }

    @Test
    void monthlyRepeatSkipsMonthsWithoutTheDay() {
        Appointment monthly = buildAppointment(LocalDate.of(2026, 1, 31), LocalTime.of(9, 0),
                new RepeatRule(Frequency.MONTHLY, null, null));

        assertEquals(List.of(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 5, 31)),
                listDates(monthly, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 31)));
    }

    @Test
    void yearlyRepeatOn29FebruaryOccursOnlyInLeapYears() {
        Appointment birthday = buildAppointment(LocalDate.of(2024, 2, 29), LocalTime.of(12, 0),
                new RepeatRule(Frequency.YEARLY, null, null));

        assertEquals(List.of(LocalDate.of(2024, 2, 29), LocalDate.of(2028, 2, 29)),
                listDates(birthday, LocalDate.of(2024, 1, 1), LocalDate.of(2028, 12, 31)));
    }

    @Test
    void cancelledOccurrenceIsSkipped() {
        Appointment weekly = buildAppointment(LocalDate.of(2026, 9, 28), LocalTime.of(18, 0),
                new RepeatRule(Frequency.WEEKLY, List.of(DayOfWeek.MONDAY), null));
        OccurrenceChange cancelled = new OccurrenceChange();
        cancelled.cancelled = true;
        weekly.overrides.put(LocalDate.of(2026, 10, 5), cancelled);

        assertEquals(List.of(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 12)),
                listDates(weekly, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 12)));
    }

    @Test
    void movedOccurrenceShowsOnlyOnItsNewDate() {
        Appointment weekly = buildAppointment(LocalDate.of(2026, 9, 28), LocalTime.of(18, 0),
                new RepeatRule(Frequency.WEEKLY, List.of(DayOfWeek.MONDAY), null));
        OccurrenceChange moved = new OccurrenceChange();
        moved.date = LocalDate.of(2026, 10, 6);
        moved.startTime = LocalTime.of(19, 0);
        weekly.overrides.put(LocalDate.of(2026, 10, 5), moved);

        assertTrue(listDates(weekly, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5)).isEmpty());

        List<AppointmentOccurrence> tuesday =
                expander.expandAppointment(weekly, LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 6));
        assertEquals(1, tuesday.size());
        assertEquals(LocalDate.of(2026, 10, 5), tuesday.get(0).originalDate);
        assertEquals(LocalTime.of(19, 0), tuesday.get(0).startTime);
        assertEquals("Gym", tuesday.get(0).title);
    }

    @Test
    void fixedAlarmUsesOverrideTimeAndMessage() {
        Alarm alarm = new Alarm();
        alarm.id = "L-1";
        alarm.message = "Wake up";
        alarm.date = LocalDate.of(2026, 9, 24);
        alarm.time = LocalTime.of(7, 0);
        alarm.repeat = new RepeatRule(Frequency.DAILY, null, null);
        OccurrenceChange later = new OccurrenceChange();
        later.time = LocalTime.of(9, 0);
        later.message = "Sleep in";
        alarm.overrides.put(LocalDate.of(2026, 9, 26), later);

        List<AlarmOccurrence> occurrences =
                expander.expandFixedAlarm(alarm, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 26));

        assertEquals(LocalDateTime.of(2026, 9, 25, 7, 0), occurrences.get(0).firesAt);
        assertEquals("Wake up", occurrences.get(0).message);
        assertEquals(LocalDateTime.of(2026, 9, 26, 9, 0), occurrences.get(1).firesAt);
        assertEquals("Sleep in", occurrences.get(1).message);
    }

    @Test
    void linkedAlarmFiresBeforeEachOccurrenceEvenOnThePreviousDay() {
        Appointment late = buildAppointment(LocalDate.of(2026, 9, 25), LocalTime.of(0, 15), null);
        Alarm alarm = new Alarm();
        alarm.id = "L-2";
        alarm.message = "Leave now";
        alarm.appointmentId = "A-1";
        alarm.minutesBefore = 30;

        List<AlarmOccurrence> occurrences = expander.expandLinkedAlarm(alarm,
                expander.expandAppointment(late, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 25)));

        assertEquals(LocalDateTime.of(2026, 9, 24, 23, 45), occurrences.get(0).firesAt);
        assertEquals(LocalDate.of(2026, 9, 25), occurrences.get(0).originalDate);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=OccurrenceExpanderTest`
Expected: COMPILATION ERROR, `cannot find symbol: class OccurrenceExpander`.

- [ ] **Step 3: Write the occurrence classes**

`src/main/java/io/meterian/aicalendar/calendar/AppointmentOccurrence.java`:

```java
package io.meterian.aicalendar.calendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** One dated occurrence of an appointment, with its override already applied. */
public final class AppointmentOccurrence {
    public final Appointment appointment;
    /** The series date before any move. Overrides use this date as key. */
    public final LocalDate originalDate;
    public final LocalDate date;
    public final LocalTime startTime;
    public final LocalTime endTime;
    public final String title;
    public final String place;
    public final int leadTimeMinutes;

    public AppointmentOccurrence(Appointment appointment, LocalDate originalDate, LocalDate date,
            LocalTime startTime, LocalTime endTime, String title, String place, int leadTimeMinutes) {
        this.appointment = appointment;
        this.originalDate = originalDate;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.title = title;
        this.place = place;
        this.leadTimeMinutes = leadTimeMinutes;
    }

    public LocalDateTime computeStart() {
        return date.atTime(startTime);
    }

    public LocalDateTime computeAlertTime() {
        return computeStart().minusMinutes(leadTimeMinutes);
    }
}
```

`src/main/java/io/meterian/aicalendar/calendar/AlarmOccurrence.java`:

```java
package io.meterian.aicalendar.calendar;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One moment when an alarm fires. */
public final class AlarmOccurrence {
    public final Alarm alarm;
    public final LocalDate originalDate;
    public final LocalDateTime firesAt;
    public final String message;

    public AlarmOccurrence(Alarm alarm, LocalDate originalDate, LocalDateTime firesAt, String message) {
        this.alarm = alarm;
        this.originalDate = originalDate;
        this.firesAt = firesAt;
        this.message = message;
    }
}
```

- [ ] **Step 4: Write `ValuePicker.java` and `OccurrenceExpander.java`**

`src/main/java/io/meterian/aicalendar/calendar/ValuePicker.java`:

```java
package io.meterian.aicalendar.calendar;

/** Picks the changed value when there is one, and the original value when there is not. */
final class ValuePicker {

    private ValuePicker() {
    }

    static <T> T pickChangedValue(T changed, T original) {
        return changed != null ? changed : original;
    }
}
```

`src/main/java/io/meterian/aicalendar/calendar/OccurrenceExpander.java`:

```java
package io.meterian.aicalendar.calendar;

import static io.meterian.aicalendar.calendar.ValuePicker.pickChangedValue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Turns items and their repeat rules into dated occurrences, with the overrides applied. */
public class OccurrenceExpander {

    private static final OccurrenceChange NO_CHANGE = new OccurrenceChange();

    public boolean isSeriesDate(LocalDate start, RepeatRule rule, LocalDate day) {
        if (day.isBefore(start)) {
            return false;
        }
        if (rule == null) {
            return day.equals(start);
        }
        if (rule.until != null && day.isAfter(rule.until)) {
            return false;
        }
        switch (rule.frequency) {
            case DAILY:
                return true;
            case WEEKLY:
                return rule.daysOfWeek != null && rule.daysOfWeek.contains(day.getDayOfWeek());
            case MONTHLY:
                return day.getDayOfMonth() == start.getDayOfMonth();
            case YEARLY:
                return day.getMonth() == start.getMonth() && day.getDayOfMonth() == start.getDayOfMonth();
            default:
                throw new IllegalStateException("Unknown frequency " + rule.frequency);
        }
    }

    public List<AppointmentOccurrence> expandAppointment(Appointment appointment, LocalDate from, LocalDate to) {
        List<AppointmentOccurrence> occurrences = new ArrayList<>();
        for (LocalDate originalDate : listCandidateDates(
                appointment.date, appointment.repeat, appointment.overrides, from, to)) {
            OccurrenceChange change = appointment.overrides.getOrDefault(originalDate, NO_CHANGE);
            LocalDate date = pickChangedValue(change.date, originalDate);
            if (isCancelled(change) || !isWithin(date, from, to)) {
                continue;
            }
            occurrences.add(new AppointmentOccurrence(appointment, originalDate, date,
                    pickChangedValue(change.startTime, appointment.startTime),
                    pickChangedValue(change.endTime, appointment.endTime),
                    pickChangedValue(change.title, appointment.title),
                    pickChangedValue(change.place, appointment.place),
                    pickChangedValue(change.leadTimeMinutes, appointment.leadTimeMinutes)));
        }
        occurrences.sort(Comparator.comparing(AppointmentOccurrence::computeStart));
        return occurrences;
    }

    public List<AlarmOccurrence> expandFixedAlarm(Alarm alarm, LocalDate from, LocalDate to) {
        List<AlarmOccurrence> occurrences = new ArrayList<>();
        for (LocalDate originalDate : listCandidateDates(alarm.date, alarm.repeat, alarm.overrides, from, to)) {
            OccurrenceChange change = alarm.overrides.getOrDefault(originalDate, NO_CHANGE);
            LocalDate date = pickChangedValue(change.date, originalDate);
            if (isCancelled(change) || !isWithin(date, from, to)) {
                continue;
            }
            occurrences.add(new AlarmOccurrence(alarm, originalDate,
                    date.atTime(pickChangedValue(change.time, alarm.time)),
                    pickChangedValue(change.message, alarm.message)));
        }
        occurrences.sort(Comparator.comparing(occurrence -> occurrence.firesAt));
        return occurrences;
    }

    public List<AlarmOccurrence> expandLinkedAlarm(Alarm alarm, List<AppointmentOccurrence> appointmentOccurrences) {
        List<AlarmOccurrence> occurrences = new ArrayList<>();
        for (AppointmentOccurrence appointmentOccurrence : appointmentOccurrences) {
            occurrences.add(new AlarmOccurrence(alarm, appointmentOccurrence.originalDate,
                    appointmentOccurrence.computeStart().minusMinutes(alarm.minutesBefore), alarm.message));
        }
        return occurrences;
    }

    /** Series dates in [from, to], plus every overridden series date, because a move can bring it into range. */
    private List<LocalDate> listCandidateDates(LocalDate start, RepeatRule rule,
            Map<LocalDate, OccurrenceChange> overrides, LocalDate from, LocalDate to) {
        TreeSet<LocalDate> candidateDates = new TreeSet<>();
        LocalDate firstDay = start.isAfter(from) ? start : from;
        LocalDate lastDay = findLastPossibleDay(start, rule, to);
        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
            if (isSeriesDate(start, rule, day)) {
                candidateDates.add(day);
            }
        }
        for (LocalDate overriddenDate : overrides.keySet()) {
            if (isSeriesDate(start, rule, overriddenDate)) {
                candidateDates.add(overriddenDate);
            }
        }
        return new ArrayList<>(candidateDates);
    }

    private static LocalDate findLastPossibleDay(LocalDate start, RepeatRule rule, LocalDate to) {
        LocalDate seriesEnd = rule == null ? start : rule.until;
        if (seriesEnd == null || seriesEnd.isAfter(to)) {
            return to;
        }
        return seriesEnd;
    }

    private static boolean isCancelled(OccurrenceChange change) {
        return Boolean.TRUE.equals(change.cancelled);
    }

    private static boolean isWithin(LocalDate date, LocalDate from, LocalDate to) {
        return !date.isBefore(from) && !date.isAfter(to);
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -q test -Dtest=OccurrenceExpanderTest`
Expected: PASS (9 tests).

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: expand repeat rules and overrides into occurrences" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Calendar service — create, find and list

**Files:**
- Create in `src/main/java/io/meterian/aicalendar/calendar/`: `ItemIds.java`, `CalendarRepository.java`, `ItemValidator.java`, `DateRange.java`, `DayAgenda.java`, `CalendarQueries.java`, `CalendarService.java`
- Test: `src/test/java/io/meterian/aicalendar/calendar/CalendarServiceTest.java`

One job per class:

| Class | Its one job |
|---|---|
| `ItemIds` | Knows the ID prefixes and builds the "unknown ID" error |
| `CalendarRepository` | Holds the data in memory: finds items by ID, inserts new items with a new ID, saves |
| `ItemValidator` | Checks the calendar rules; every error says what to do |
| `CalendarQueries` | Answers read-only questions: occurrences in a range, one day, search |
| `CalendarService` | The thread-safe entry point; hands each call to one of the classes above |

Only `CalendarService` is public. The tests use only `CalendarService`, so the other classes can change without breaking them.

**Interfaces:**
- Consumes: Task 2 data classes and `CalendarStore`; Task 3 `OccurrenceExpander`, occurrence classes and `ValuePicker`.
- Produces (all `public synchronized` on `CalendarService`):
  - `new CalendarService(CalendarStore store, CalendarData data, OccurrenceExpander expander, Clock clock)`
  - `Appointment addAppointment(Appointment)`, `Alarm addAlarm(Alarm)`, `Note addNote(Note)` — validate, give the ID (`A-n`, `L-n`, `N-n`), save, return the stored object.
  - `Appointment findAppointment(String id)` — throws `CalendarException` for an unknown ID.
  - `List<AppointmentOccurrence> listAppointmentOccurrences(LocalDate from, LocalDate to)`
  - `List<AlarmOccurrence> listAlarmOccurrences(LocalDate from, LocalDate to)` — fixed and linked, by `firesAt` date.
  - `DayAgenda listDay(LocalDate day)`
  - `List<Object> findItems(String query, String type, LocalDate from, LocalDate to)` — all arguments may be null.
  - `DayAgenda` — public final fields `day`, `appointments`, `alarms`, `notes`.
- Package-private, used by Task 5: `CalendarRepository` (`getAppointments`, `getAlarms`, `getNotes`, `findAppointment`, `findAlarm`, `findNote`, `insertAppointment`, `insertAlarm`, `insertNote`, `saveChanges`), `ItemIds` (`isAppointmentId`, `isAlarmId`, `isNoteId`, `buildUnknownIdError`), `ItemValidator` (`validateAppointment`, `validateAlarm`, `validateNote`, `validateAppointmentOccurrence`, `requireSeriesDate`, `requireFixedAlarm`, static `buildNoteOccurrenceError`).

- [ ] **Step 1: Write the failing test**

`src/test/java/io/meterian/aicalendar/calendar/CalendarServiceTest.java`:

```java
package io.meterian.aicalendar.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CalendarServiceTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);

    @TempDir
    Path tempDir;

    private Path file;
    private CalendarService service;

    @BeforeEach
    void createService() {
        file = tempDir.resolve("calendar.json");
        Clock clock = Clock.fixed(LocalDateTime.of(2026, 9, 24, 10, 0).atZone(ROME).toInstant(), ROME);
        service = new CalendarService(new CalendarStore(file), new CalendarData(), new OccurrenceExpander(), clock);
    }

    static Appointment buildAppointment(String title, LocalDate date, LocalTime start) {
        Appointment appointment = new Appointment();
        appointment.title = title;
        appointment.date = date;
        appointment.startTime = start;
        appointment.leadTimeMinutes = 30;
        return appointment;
    }

    static Alarm buildFixedAlarm(String message, LocalDate date, LocalTime time) {
        Alarm alarm = new Alarm();
        alarm.message = message;
        alarm.date = date;
        alarm.time = time;
        return alarm;
    }

    static Alarm buildLinkedAlarm(String message, String appointmentId, int minutesBefore) {
        Alarm alarm = new Alarm();
        alarm.message = message;
        alarm.appointmentId = appointmentId;
        alarm.minutesBefore = minutesBefore;
        return alarm;
    }

    static Note buildNote(String text, LocalDate date, String appointmentId) {
        Note note = new Note();
        note.text = text;
        note.date = date;
        note.appointmentId = appointmentId;
        return note;
    }

    private void assertRejected(String expectedMessage, Runnable action) {
        CalendarException error = assertThrows(CalendarException.class, action::run);
        assertEquals(expectedMessage, error.getMessage());
    }

    @Test
    void addAppointmentAssignsIdsAndSaves() {
        assertEquals("A-1", service.addAppointment(buildAppointment("Gym", MONDAY, LocalTime.of(18, 0))).id);
        assertEquals("A-2", service.addAppointment(buildAppointment("Dentist", MONDAY, LocalTime.of(9, 0))).id);

        CalendarData saved = new CalendarStore(file).load();
        assertEquals(2, saved.appointments.size());
        assertEquals(3, saved.nextIds.get("A"));
    }

    @Test
    void addAppointmentRejectsBrokenRules() {
        Appointment noStart = buildAppointment("Dentist", MONDAY, null);
        assertRejected("startTime is missing. Ask the user for it.", () -> service.addAppointment(noStart));

        Appointment endFirst = buildAppointment("Dentist", MONDAY, LocalTime.of(15, 0));
        endFirst.endTime = LocalTime.of(14, 0);
        assertRejected("endTime must be after startTime.", () -> service.addAppointment(endFirst));

        Appointment weeklyNoDays = buildAppointment("Gym", MONDAY, LocalTime.of(18, 0));
        weeklyNoDays.repeat = new RepeatRule(Frequency.WEEKLY, null, null);
        assertRejected("repeat.daysOfWeek is required for WEEKLY.", () -> service.addAppointment(weeklyNoDays));

        Appointment longLead = buildAppointment("Trip", MONDAY, LocalTime.of(8, 0));
        longLead.leadTimeMinutes = 20000;
        assertRejected("leadTimeMinutes must be between 0 and 10080 (7 days).",
                () -> service.addAppointment(longLead));

        Appointment badEmail = buildAppointment("Meeting", MONDAY, LocalTime.of(10, 0));
        badEmail.attendees.add(new Attendee("Anna Rossi", "anna-at-example"));
        assertRejected("'anna-at-example' is not a valid email address.", () -> service.addAppointment(badEmail));
    }

    @Test
    void addAlarmNeedsExactlyOneForm() {
        Alarm neither = new Alarm();
        neither.message = "Hello";
        assertRejected("Give either date and time, or appointmentId and minutesBefore.",
                () -> service.addAlarm(neither));

        Alarm both = buildFixedAlarm("Hello", MONDAY, LocalTime.of(7, 0));
        both.appointmentId = "A-1";
        both.minutesBefore = 10;
        assertRejected("Give either date and time, or appointmentId and minutesBefore.",
                () -> service.addAlarm(both));

        assertRejected("No item has the id A-9. Use find-items to get the id.",
                () -> service.addAlarm(buildLinkedAlarm("Leave", "A-9", 10)));

        assertEquals("L-1", service.addAlarm(buildFixedAlarm("Wake up", MONDAY, LocalTime.of(7, 0))).id);
    }

    @Test
    void addNoteNeedsExactlyOneTarget() {
        service.addAppointment(buildAppointment("Dentist", MONDAY, LocalTime.of(9, 0)));

        assertRejected("Give either date or appointmentId.",
                () -> service.addNote(buildNote("Both", MONDAY, "A-1")));
        assertRejected("Give either date or appointmentId.",
                () -> service.addNote(buildNote("Neither", null, null)));
        assertEquals("N-1", service.addNote(buildNote("Bring X-ray", null, "A-1")).id);
    }

    @Test
    void listDayShowsOccurrencesAlarmsAndNotes() {
        Appointment gym = buildAppointment("Gym", MONDAY, LocalTime.of(18, 0));
        gym.repeat = new RepeatRule(Frequency.WEEKLY, List.of(DayOfWeek.MONDAY), null);
        service.addAppointment(gym);                                                        // A-1
        service.addAppointment(buildAppointment("Dentist", MONDAY, LocalTime.of(9, 0)));    // A-2
        service.addAlarm(buildFixedAlarm("Wake up", MONDAY, LocalTime.of(7, 0)));           // L-1
        service.addAlarm(buildLinkedAlarm("Leave for dentist", "A-2", 60));                 // L-2
        service.addNote(buildNote("Pay rent", MONDAY, null));                               // N-1
        service.addNote(buildNote("Bring X-ray", null, "A-2"));                             // N-2
        service.addNote(buildNote("Other day", MONDAY.plusDays(1), null));                  // N-3

        DayAgenda agenda = service.listDay(MONDAY);

        assertEquals(List.of("Dentist", "Gym"),
                agenda.appointments.stream().map(o -> o.title).collect(Collectors.toList()));
        assertEquals(List.of("Wake up", "Leave for dentist"),
                agenda.alarms.stream().map(o -> o.message).collect(Collectors.toList()));
        assertEquals(List.of("N-1", "N-2"),
                agenda.notes.stream().map(n -> n.id).collect(Collectors.toList()));

        DayAgenda nextMonday = service.listDay(MONDAY.plusWeeks(1));
        assertEquals(List.of("Gym"),
                nextMonday.appointments.stream().map(o -> o.title).collect(Collectors.toList()));
    }

    @Test
    void findItemsFiltersByQueryTypeAndRange() {
        Appointment gym = buildAppointment("Gym", MONDAY, LocalTime.of(18, 0));
        gym.repeat = new RepeatRule(Frequency.WEEKLY, List.of(DayOfWeek.MONDAY), null);
        service.addAppointment(gym);
        service.addAppointment(buildAppointment("Dentist", LocalDate.of(2026, 9, 30), LocalTime.of(15, 0)));
        service.addAlarm(buildFixedAlarm("Call Anna", MONDAY, LocalTime.of(12, 0)));

        assertEquals(List.of("A-1"), listIds(service.findItems("GYM", null, null, null)));
        assertEquals(List.of("L-1"), listIds(service.findItems(null, "alarm", null, null)));
        assertEquals(List.of("A-1"), listIds(service.findItems(null, "appointment",
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5))));
        assertEquals(List.of(), listIds(service.findItems(null, "appointment",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 4))));
        assertRejected("type must be appointment, alarm or note.",
                () -> service.findItems(null, "meeting", null, null));
    }

    private static List<String> listIds(List<Object> items) {
        return items.stream().map(item -> {
            if (item instanceof Appointment) {
                return ((Appointment) item).id;
            }
            if (item instanceof Alarm) {
                return ((Alarm) item).id;
            }
            return ((Note) item).id;
        }).collect(Collectors.toList());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=CalendarServiceTest`
Expected: COMPILATION ERROR, `cannot find symbol: class CalendarService`.

- [ ] **Step 3: Write `ItemIds.java` and `CalendarRepository.java`**

`src/main/java/io/meterian/aicalendar/calendar/ItemIds.java`:

```java
package io.meterian.aicalendar.calendar;

/** Item IDs are a prefix and a number: A-1 is an appointment, L-1 an alarm, N-1 a note. */
final class ItemIds {

    static final String APPOINTMENT_PREFIX = "A";
    static final String ALARM_PREFIX = "L";
    static final String NOTE_PREFIX = "N";

    private ItemIds() {
    }

    static boolean isAppointmentId(String id) {
        return hasPrefix(id, APPOINTMENT_PREFIX);
    }

    static boolean isAlarmId(String id) {
        return hasPrefix(id, ALARM_PREFIX);
    }

    static boolean isNoteId(String id) {
        return hasPrefix(id, NOTE_PREFIX);
    }

    static CalendarException buildUnknownIdError(String id) {
        return new CalendarException("No item has the id " + id + ". Use find-items to get the id.");
    }

    private static boolean hasPrefix(String id, String prefix) {
        return id != null && id.startsWith(prefix + "-");
    }
}
```

`src/main/java/io/meterian/aicalendar/calendar/CalendarRepository.java`:

```java
package io.meterian.aicalendar.calendar;

import java.util.List;
import java.util.function.Function;

/**
 * Holds the calendar data in memory: finds items by ID, inserts new items with a new ID, and saves the data.
 * It does no locking, because CalendarService is its only caller and CalendarService locks.
 */
class CalendarRepository {

    private final CalendarStore store;
    private final CalendarData data;

    CalendarRepository(CalendarStore store, CalendarData data) {
        this.store = store;
        this.data = data;
    }

    List<Appointment> getAppointments() {
        return data.appointments;
    }

    List<Alarm> getAlarms() {
        return data.alarms;
    }

    List<Note> getNotes() {
        return data.notes;
    }

    Appointment findAppointment(String id) {
        return findById(data.appointments, id, appointment -> appointment.id);
    }

    Alarm findAlarm(String id) {
        return findById(data.alarms, id, alarm -> alarm.id);
    }

    Note findNote(String id) {
        return findById(data.notes, id, note -> note.id);
    }

    void insertAppointment(Appointment appointment) {
        appointment.id = assignNextId(ItemIds.APPOINTMENT_PREFIX);
        data.appointments.add(appointment);
    }

    void insertAlarm(Alarm alarm) {
        alarm.id = assignNextId(ItemIds.ALARM_PREFIX);
        data.alarms.add(alarm);
    }

    void insertNote(Note note) {
        note.id = assignNextId(ItemIds.NOTE_PREFIX);
        data.notes.add(note);
    }

    void saveChanges() {
        store.save(data);
    }

    private String assignNextId(String prefix) {
        int nextNumber = data.nextIds.getOrDefault(prefix, 1);
        data.nextIds.put(prefix, nextNumber + 1);
        return prefix + "-" + nextNumber;
    }

    private static <T> T findById(List<T> items, String id, Function<T, String> readId) {
        return items.stream()
                .filter(item -> readId.apply(item).equals(id))
                .findFirst()
                .orElseThrow(() -> ItemIds.buildUnknownIdError(id));
    }
}
```

- [ ] **Step 4: Write `ItemValidator.java`**

`src/main/java/io/meterian/aicalendar/calendar/ItemValidator.java`:

```java
package io.meterian.aicalendar.calendar;

import static io.meterian.aicalendar.calendar.ValuePicker.pickChangedValue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.regex.Pattern;

/** Checks the calendar rules for new and changed items. Every error message says what is wrong and what to do. */
class ItemValidator {

    static final int MAX_LEAD_MINUTES = 7 * 24 * 60;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private final CalendarRepository repository;
    private final OccurrenceExpander expander;

    ItemValidator(CalendarRepository repository, OccurrenceExpander expander) {
        this.repository = repository;
        this.expander = expander;
    }

    void validateAppointment(Appointment appointment) {
        requireText(appointment.title, "title");
        requireValue(appointment.date, "date");
        requireValue(appointment.startTime, "startTime");
        requireValue(appointment.leadTimeMinutes, "leadTimeMinutes");
        requireMinutesInRange(appointment.leadTimeMinutes, "leadTimeMinutes");
        requireEndAfterStart(appointment.startTime, appointment.endTime);
        validateRepeat(appointment.repeat, appointment.date);
        appointment.attendees.forEach(ItemValidator::validateAttendee);
    }

    void validateAlarm(Alarm alarm) {
        requireText(alarm.message, "message");
        boolean hasFixedFields = alarm.date != null || alarm.time != null || alarm.repeat != null;
        boolean hasLinkedFields = alarm.appointmentId != null || alarm.minutesBefore != null;
        if (hasFixedFields == hasLinkedFields) {
            throw new CalendarException("Give either date and time, or appointmentId and minutesBefore.");
        }
        if (hasFixedFields) {
            validateFixedAlarm(alarm);
        } else {
            validateLinkedAlarm(alarm);
        }
    }

    void validateNote(Note note) {
        requireText(note.text, "text");
        if ((note.date == null) == (note.appointmentId == null)) {
            throw new CalendarException("Give either date or appointmentId.");
        }
        if (note.appointmentId != null) {
            repository.findAppointment(note.appointmentId);
        }
    }

    /** Checks one occurrence of an appointment as it will be after the change. */
    void validateAppointmentOccurrence(Appointment appointment, OccurrenceChange change) {
        requireEndAfterStart(pickChangedValue(change.startTime, appointment.startTime),
                pickChangedValue(change.endTime, appointment.endTime));
        if (change.leadTimeMinutes != null) {
            requireMinutesInRange(change.leadTimeMinutes, "leadTimeMinutes");
        }
    }

    void requireSeriesDate(String id, LocalDate start, RepeatRule rule, LocalDate day) {
        if (rule == null) {
            throw new CalendarException(id + " does not repeat. Call this tool without occurrenceDate.");
        }
        if (!expander.isSeriesDate(start, rule, day)) {
            throw new CalendarException(id + " has no occurrence on " + day + ".");
        }
    }

    void requireFixedAlarm(Alarm alarm) {
        if (alarm.isLinked()) {
            throw new CalendarException(
                    "A linked alarm follows its appointment. Change the appointment occurrence instead.");
        }
    }

    static CalendarException buildNoteOccurrenceError() {
        return new CalendarException("Notes do not repeat. Call this tool without occurrenceDate.");
    }

    private void validateFixedAlarm(Alarm alarm) {
        requireValue(alarm.date, "date");
        requireValue(alarm.time, "time");
        validateRepeat(alarm.repeat, alarm.date);
    }

    private void validateLinkedAlarm(Alarm alarm) {
        requireText(alarm.appointmentId, "appointmentId");
        repository.findAppointment(alarm.appointmentId);
        requireValue(alarm.minutesBefore, "minutesBefore");
        requireMinutesInRange(alarm.minutesBefore, "minutesBefore");
    }

    private static void validateRepeat(RepeatRule rule, LocalDate start) {
        if (rule == null) {
            return;
        }
        requireValue(rule.frequency, "repeat.frequency");
        boolean hasDays = rule.daysOfWeek != null && !rule.daysOfWeek.isEmpty();
        if (rule.frequency == Frequency.WEEKLY && !hasDays) {
            throw new CalendarException("repeat.daysOfWeek is required for WEEKLY.");
        }
        if (rule.frequency != Frequency.WEEKLY && hasDays) {
            throw new CalendarException("repeat.daysOfWeek is only for WEEKLY.");
        }
        if (rule.until != null && rule.until.isBefore(start)) {
            throw new CalendarException("repeat.until must not be before the first date.");
        }
    }

    private static void validateAttendee(Attendee attendee) {
        requireText(attendee.name, "attendee name");
        requireText(attendee.email, "attendee email");
        if (!EMAIL_PATTERN.matcher(attendee.email).matches()) {
            throw new CalendarException("'" + attendee.email + "' is not a valid email address.");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw buildMissingFieldError(field);
        }
    }

    private static void requireValue(Object value, String field) {
        if (value == null) {
            throw buildMissingFieldError(field);
        }
    }

    private static void requireMinutesInRange(int minutes, String field) {
        if (minutes < 0 || minutes > MAX_LEAD_MINUTES) {
            throw new CalendarException(field + " must be between 0 and " + MAX_LEAD_MINUTES + " (7 days).");
        }
    }

    private static void requireEndAfterStart(LocalTime start, LocalTime end) {
        if (end != null && !end.isAfter(start)) {
            throw new CalendarException("endTime must be after startTime.");
        }
    }

    private static CalendarException buildMissingFieldError(String field) {
        return new CalendarException(field + " is missing. Ask the user for it.");
    }
}
```

- [ ] **Step 5: Write `DateRange.java`, `DayAgenda.java` and `CalendarQueries.java`**

`src/main/java/io/meterian/aicalendar/calendar/DateRange.java`:

```java
package io.meterian.aicalendar.calendar;

import java.time.LocalDate;

/** An inclusive range of days. */
final class DateRange {
    final LocalDate from;
    final LocalDate to;

    DateRange(LocalDate from, LocalDate to) {
        this.from = from;
        this.to = to;
    }

    boolean contains(LocalDate day) {
        return !day.isBefore(from) && !day.isAfter(to);
    }
}
```

`src/main/java/io/meterian/aicalendar/calendar/DayAgenda.java`:

```java
package io.meterian.aicalendar.calendar;

import java.time.LocalDate;
import java.util.List;

/** Everything on one day, sorted by time. */
public final class DayAgenda {
    public final LocalDate day;
    public final List<AppointmentOccurrence> appointments;
    public final List<AlarmOccurrence> alarms;
    public final List<Note> notes;

    public DayAgenda(LocalDate day, List<AppointmentOccurrence> appointments, List<AlarmOccurrence> alarms,
            List<Note> notes) {
        this.day = day;
        this.appointments = appointments;
        this.alarms = alarms;
        this.notes = notes;
    }
}
```

`src/main/java/io/meterian/aicalendar/calendar/CalendarQueries.java`:

```java
package io.meterian.aicalendar.calendar;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Answers read-only questions about the calendar: occurrences in a range, everything on one day, and search. */
class CalendarQueries {

    private static final List<String> ITEM_TYPES = List.of("appointment", "alarm", "note");
    private static final int DEFAULT_SEARCH_DAYS = 365;
    private static final int MINUTES_PER_DAY = 24 * 60;

    private final CalendarRepository repository;
    private final OccurrenceExpander expander;
    private final Clock clock;

    CalendarQueries(CalendarRepository repository, OccurrenceExpander expander, Clock clock) {
        this.repository = repository;
        this.expander = expander;
        this.clock = clock;
    }

    List<AppointmentOccurrence> listAppointmentOccurrences(LocalDate from, LocalDate to) {
        List<AppointmentOccurrence> occurrences = new ArrayList<>();
        for (Appointment appointment : repository.getAppointments()) {
            occurrences.addAll(expander.expandAppointment(appointment, from, to));
        }
        occurrences.sort(Comparator.comparing(AppointmentOccurrence::computeStart));
        return occurrences;
    }

    List<AlarmOccurrence> listAlarmOccurrences(LocalDate from, LocalDate to) {
        List<AlarmOccurrence> occurrences = new ArrayList<>();
        for (Alarm alarm : repository.getAlarms()) {
            occurrences.addAll(expandAlarm(alarm, new DateRange(from, to)));
        }
        occurrences.sort(Comparator.comparing(occurrence -> occurrence.firesAt));
        return occurrences;
    }

    DayAgenda listDay(LocalDate day) {
        List<AppointmentOccurrence> appointments = listAppointmentOccurrences(day, day);
        Set<String> appointmentIdsOnDay = appointments.stream()
                .map(occurrence -> occurrence.appointment.id)
                .collect(Collectors.toSet());
        List<Note> notes = repository.getNotes().stream()
                .filter(note -> day.equals(note.date) || appointmentIdsOnDay.contains(note.appointmentId))
                .collect(Collectors.toList());
        return new DayAgenda(day, appointments, listAlarmOccurrences(day, day), notes);
    }

    /** Each argument may be null. With no dates, there is no date filter. */
    List<Object> findItems(String query, String type, LocalDate from, LocalDate to) {
        requireKnownType(type);
        Optional<DateRange> searchRange = resolveSearchRange(from, to);
        List<Object> items = new ArrayList<>();
        if (includesType(type, "appointment")) {
            items.addAll(findAppointments(query, searchRange));
        }
        if (includesType(type, "alarm")) {
            items.addAll(findAlarms(query, searchRange));
        }
        if (includesType(type, "note")) {
            items.addAll(findNotes(query, searchRange));
        }
        return items;
    }

    private List<Appointment> findAppointments(String query, Optional<DateRange> searchRange) {
        return repository.getAppointments().stream()
                .filter(appointment -> matchesQuery(query,
                        appointment.title, appointment.place, joinAttendees(appointment)))
                .filter(appointment -> searchRange.map(range -> hasOccurrences(appointment, range)).orElse(true))
                .collect(Collectors.toList());
    }

    private List<Alarm> findAlarms(String query, Optional<DateRange> searchRange) {
        return repository.getAlarms().stream()
                .filter(alarm -> matchesQuery(query, alarm.message))
                .filter(alarm -> searchRange.map(range -> !expandAlarm(alarm, range).isEmpty()).orElse(true))
                .collect(Collectors.toList());
    }

    private List<Note> findNotes(String query, Optional<DateRange> searchRange) {
        return repository.getNotes().stream()
                .filter(note -> matchesQuery(query, note.text))
                .filter(note -> searchRange.map(range -> isNoteInRange(note, range)).orElse(true))
                .collect(Collectors.toList());
    }

    private List<AlarmOccurrence> expandAlarm(Alarm alarm, DateRange range) {
        if (!alarm.isLinked()) {
            return expander.expandFixedAlarm(alarm, range.from, range.to);
        }
        // A linked alarm fires before its appointment, maybe on an earlier day, so look further ahead.
        Appointment appointment = repository.findAppointment(alarm.appointmentId);
        long extraDays = alarm.minutesBefore / MINUTES_PER_DAY + 1;
        List<AppointmentOccurrence> appointmentOccurrences =
                expander.expandAppointment(appointment, range.from, range.to.plusDays(extraDays));
        return expander.expandLinkedAlarm(alarm, appointmentOccurrences).stream()
                .filter(occurrence -> range.contains(occurrence.firesAt.toLocalDate()))
                .collect(Collectors.toList());
    }

    private boolean hasOccurrences(Appointment appointment, DateRange range) {
        return !expander.expandAppointment(appointment, range.from, range.to).isEmpty();
    }

    private boolean isNoteInRange(Note note, DateRange range) {
        if (note.date != null) {
            return range.contains(note.date);
        }
        return hasOccurrences(repository.findAppointment(note.appointmentId), range);
    }

    private Optional<DateRange> resolveSearchRange(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return Optional.empty();
        }
        LocalDate start = from != null ? from : LocalDate.now(clock);
        LocalDate end = to != null ? to : start.plusDays(DEFAULT_SEARCH_DAYS);
        if (end.isBefore(start)) {
            throw new CalendarException("toDate must not be before fromDate.");
        }
        return Optional.of(new DateRange(start, end));
    }

    private static void requireKnownType(String type) {
        if (type != null && !ITEM_TYPES.contains(type)) {
            throw new CalendarException("type must be appointment, alarm or note.");
        }
    }

    private static boolean includesType(String requestedType, String itemType) {
        return requestedType == null || requestedType.equals(itemType);
    }

    private static boolean matchesQuery(String query, String... texts) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String searchText = query.toLowerCase(Locale.ROOT);
        for (String text : texts) {
            if (text != null && text.toLowerCase(Locale.ROOT).contains(searchText)) {
                return true;
            }
        }
        return false;
    }

    private static String joinAttendees(Appointment appointment) {
        return appointment.attendees.stream()
                .map(attendee -> attendee.name + " " + attendee.email)
                .collect(Collectors.joining(" "));
    }
}
```

- [ ] **Step 6: Write `CalendarService.java`**

`src/main/java/io/meterian/aicalendar/calendar/CalendarService.java`:

```java
package io.meterian.aicalendar.calendar;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * The entry point to the calendar. The agent thread and the alert thread both call it, so every public method is
 * synchronized. Each method hands the work to one focused class, and every change is saved at once.
 */
public class CalendarService {

    private final CalendarRepository repository;
    private final ItemValidator validator;
    private final CalendarQueries queries;

    public CalendarService(CalendarStore store, CalendarData data, OccurrenceExpander expander, Clock clock) {
        this.repository = new CalendarRepository(store, data);
        this.validator = new ItemValidator(repository, expander);
        this.queries = new CalendarQueries(repository, expander, clock);
    }

    public synchronized Appointment addAppointment(Appointment appointment) {
        validator.validateAppointment(appointment);
        repository.insertAppointment(appointment);
        repository.saveChanges();
        return appointment;
    }

    public synchronized Alarm addAlarm(Alarm alarm) {
        validator.validateAlarm(alarm);
        repository.insertAlarm(alarm);
        repository.saveChanges();
        return alarm;
    }

    public synchronized Note addNote(Note note) {
        validator.validateNote(note);
        repository.insertNote(note);
        repository.saveChanges();
        return note;
    }

    public synchronized Appointment findAppointment(String id) {
        return repository.findAppointment(id);
    }

    public synchronized List<AppointmentOccurrence> listAppointmentOccurrences(LocalDate from, LocalDate to) {
        return queries.listAppointmentOccurrences(from, to);
    }

    public synchronized List<AlarmOccurrence> listAlarmOccurrences(LocalDate from, LocalDate to) {
        return queries.listAlarmOccurrences(from, to);
    }

    public synchronized DayAgenda listDay(LocalDate day) {
        return queries.listDay(day);
    }

    public synchronized List<Object> findItems(String query, String type, LocalDate from, LocalDate to) {
        return queries.findItems(query, type, from, to);
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `mvn -q test -Dtest=CalendarServiceTest`
Expected: PASS (6 tests).

- [ ] **Step 8: Commit**

```bash
git add src
git commit -m "feat: add calendar service for creating, finding and listing items" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Calendar service — edit and remove

**Files:**
- Create in `src/main/java/io/meterian/aicalendar/calendar/`: `ItemChanges.java`, `ItemEditor.java`, `ItemRemover.java`
- Modify: `src/main/java/io/meterian/aicalendar/calendar/CalendarService.java` (two fields, two constructor lines, two methods)
- Test: `src/test/java/io/meterian/aicalendar/calendar/CalendarServiceEditTest.java`

| Class | Its one job |
|---|---|
| `ItemChanges` | Carries the fields that an edit changes |
| `ItemEditor` | Changes a whole item (on a copy, so a failed edit changes nothing) or one occurrence |
| `ItemRemover` | Removes a whole item with its linked items, or cancels one occurrence |

**Interfaces:**
- Consumes: Task 4 `CalendarRepository`, `ItemIds`, `ItemValidator`; Task 3 `ValuePicker`; `Json.copyValue`.
- Produces:
  - `ItemChanges` — public nullable fields `title, date, startTime, endTime, place, leadTimeMinutes, attendees, repeat, message, time, minutesBefore, text`; `List<String> listChangedFields()`.
  - `public synchronized Object editItem(String id, LocalDate occurrenceDate, ItemChanges changes)` on `CalendarService` — returns the updated `Appointment`, `Alarm` or `Note`.
  - `public synchronized List<String> removeItem(String id, LocalDate occurrenceDate)` on `CalendarService` — returns what was removed, for example `["A-1", "L-1", "N-1"]` or `["A-1 on 2026-10-05"]`.

- [ ] **Step 1: Write the failing test**

`src/test/java/io/meterian/aicalendar/calendar/CalendarServiceEditTest.java`:

```java
package io.meterian.aicalendar.calendar;

import static io.meterian.aicalendar.calendar.CalendarServiceTest.buildAppointment;
import static io.meterian.aicalendar.calendar.CalendarServiceTest.buildFixedAlarm;
import static io.meterian.aicalendar.calendar.CalendarServiceTest.buildLinkedAlarm;
import static io.meterian.aicalendar.calendar.CalendarServiceTest.buildNote;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CalendarServiceEditTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);
    private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 10, 5);

    @TempDir
    Path tempDir;

    private Path file;
    private CalendarService service;

    @BeforeEach
    void createServiceWithWeeklyGym() {
        file = tempDir.resolve("calendar.json");
        Clock clock = Clock.fixed(LocalDateTime.of(2026, 9, 24, 10, 0).atZone(ROME).toInstant(), ROME);
        service = new CalendarService(new CalendarStore(file), new CalendarData(), new OccurrenceExpander(), clock);
        Appointment gym = buildAppointment("Gym", MONDAY, LocalTime.of(18, 0));
        gym.repeat = new RepeatRule(Frequency.WEEKLY, List.of(DayOfWeek.MONDAY), null);
        service.addAppointment(gym); // A-1
    }

    private void assertRejected(String expectedMessage, Runnable action) {
        CalendarException error = assertThrows(CalendarException.class, action::run);
        assertEquals(expectedMessage, error.getMessage());
    }

    @Test
    void editWholeSeriesChangesFieldsKeepsIdAndSaves() {
        ItemChanges changes = new ItemChanges();
        changes.startTime = LocalTime.of(19, 0);
        changes.place = "FitLife";

        Appointment updated = (Appointment) service.editItem("A-1", null, changes);

        assertEquals("A-1", updated.id);
        assertEquals(LocalTime.of(19, 0), updated.startTime);
        assertEquals("FitLife", updated.place);
        assertEquals(LocalTime.of(19, 0), service.listDay(NEXT_MONDAY).appointments.get(0).startTime);
        assertEquals("FitLife", new CalendarStore(file).load().appointments.get(0).place);
    }

    @Test
    void editRejectsFieldsThatDoNotApply() {
        ItemChanges changes = new ItemChanges();
        changes.message = "Hello";

        assertRejected("message cannot be changed on an appointment.", () -> service.editItem("A-1", null, changes));
    }

    @Test
    void editWithNoChangesIsRejected() {
        assertRejected("Give at least one field to change.", () -> service.editItem("A-1", null, new ItemChanges()));
    }

    @Test
    void failedEditLeavesItemUnchanged() {
        ItemChanges changes = new ItemChanges();
        changes.endTime = LocalTime.of(17, 0);

        assertRejected("endTime must be after startTime.", () -> service.editItem("A-1", null, changes));
        assertEquals(null, service.findAppointment("A-1").endTime);
    }

    @Test
    void editOneOccurrenceChangesOnlyThatDate() {
        ItemChanges changes = new ItemChanges();
        changes.startTime = LocalTime.of(19, 0);

        service.editItem("A-1", NEXT_MONDAY, changes);

        assertEquals(LocalTime.of(19, 0), service.listDay(NEXT_MONDAY).appointments.get(0).startTime);
        assertEquals(LocalTime.of(18, 0), service.listDay(NEXT_MONDAY.plusWeeks(1)).appointments.get(0).startTime);
    }

    @Test
    void editOccurrenceNeedsARepeatingItemAndASeriesDate() {
        service.addAppointment(buildAppointment("Dentist", MONDAY, LocalTime.of(9, 0))); // A-2
        ItemChanges changes = new ItemChanges();
        changes.startTime = LocalTime.of(10, 0);

        assertRejected("A-2 does not repeat. Call this tool without occurrenceDate.",
                () -> service.editItem("A-2", MONDAY, changes));
        assertRejected("A-1 has no occurrence on 2026-10-06.",
                () -> service.editItem("A-1", LocalDate.of(2026, 10, 6), changes));
    }

    @Test
    void editNoteAndLinkedAlarm() {
        service.addAlarm(buildLinkedAlarm("Pack the bag", "A-1", 60)); // L-1
        service.addNote(buildNote("Bring a towel", null, "A-1"));       // N-1

        ItemChanges alarmChanges = new ItemChanges();
        alarmChanges.minutesBefore = 90;
        assertEquals(90, ((Alarm) service.editItem("L-1", null, alarmChanges)).minutesBefore);

        ItemChanges noteChanges = new ItemChanges();
        noteChanges.text = "Bring two towels";
        assertEquals("Bring two towels", ((Note) service.editItem("N-1", null, noteChanges)).text);

        ItemChanges moveNote = new ItemChanges();
        moveNote.date = MONDAY;
        assertRejected("This note belongs to an appointment. Only text can change.",
                () -> service.editItem("N-1", null, moveNote));
    }

    @Test
    void removeOneOccurrenceCancelsOnlyThatDate() {
        assertEquals(List.of("A-1 on 2026-10-05"), service.removeItem("A-1", NEXT_MONDAY));

        assertTrue(service.listDay(NEXT_MONDAY).appointments.isEmpty());
        assertEquals(1, service.listDay(NEXT_MONDAY.plusWeeks(1)).appointments.size());
    }

    @Test
    void removeAppointmentAlsoRemovesLinkedAlarmsAndNotes() {
        service.addAlarm(buildLinkedAlarm("Pack the bag", "A-1", 60));        // L-1
        service.addAlarm(buildFixedAlarm("Wake up", MONDAY, LocalTime.of(7, 0))); // L-2
        service.addNote(buildNote("Bring a towel", null, "A-1"));              // N-1

        assertEquals(List.of("A-1", "L-1", "N-1"), service.removeItem("A-1", null));

        CalendarData saved = new CalendarStore(file).load();
        assertTrue(saved.appointments.isEmpty());
        assertEquals("L-2", saved.alarms.get(0).id);
        assertTrue(saved.notes.isEmpty());
    }

    @Test
    void removeUnknownIdIsRejected() {
        assertRejected("No item has the id A-7. Use find-items to get the id.", () -> service.removeItem("A-7", null));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=CalendarServiceEditTest`
Expected: COMPILATION ERROR, `cannot find symbol: class ItemChanges`.

- [ ] **Step 3: Write `ItemChanges.java`**

`src/main/java/io/meterian/aicalendar/calendar/ItemChanges.java`:

```java
package io.meterian.aicalendar.calendar;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** The fields that an edit changes. A null field stays as it is. */
public class ItemChanges {
    public String title;
    public LocalDate date;
    public LocalTime startTime;
    public LocalTime endTime;
    public String place;
    public Integer leadTimeMinutes;
    public List<Attendee> attendees;
    public RepeatRule repeat;
    public String message;
    public LocalTime time;
    public Integer minutesBefore;
    public String text;

    public List<String> listChangedFields() {
        List<String> fields = new ArrayList<>();
        addIfSet(fields, "title", title);
        addIfSet(fields, "date", date);
        addIfSet(fields, "startTime", startTime);
        addIfSet(fields, "endTime", endTime);
        addIfSet(fields, "place", place);
        addIfSet(fields, "leadTimeMinutes", leadTimeMinutes);
        addIfSet(fields, "attendees", attendees);
        addIfSet(fields, "repeat", repeat);
        addIfSet(fields, "message", message);
        addIfSet(fields, "time", time);
        addIfSet(fields, "minutesBefore", minutesBefore);
        addIfSet(fields, "text", text);
        return fields;
    }

    private static void addIfSet(List<String> fields, String name, Object value) {
        if (value != null) {
            fields.add(name);
        }
    }
}
```

- [ ] **Step 4: Write `ItemEditor.java`**

`src/main/java/io/meterian/aicalendar/calendar/ItemEditor.java`:

```java
package io.meterian.aicalendar.calendar;

import static io.meterian.aicalendar.calendar.ValuePicker.pickChangedValue;

import io.meterian.aicalendar.Json;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Changes a whole item, or one occurrence of a repeating item. A whole-item edit works on a copy, so the stored
 * item stays unchanged when the result breaks a rule.
 */
class ItemEditor {

    private static final Set<String> APPOINTMENT_FIELDS = Set.of(
            "title", "date", "startTime", "endTime", "place", "leadTimeMinutes", "attendees", "repeat");
    private static final Set<String> APPOINTMENT_OCCURRENCE_FIELDS = Set.of(
            "title", "date", "startTime", "endTime", "place", "leadTimeMinutes");
    private static final Set<String> FIXED_ALARM_FIELDS = Set.of("message", "date", "time", "repeat");
    private static final Set<String> FIXED_ALARM_OCCURRENCE_FIELDS = Set.of("message", "date", "time");
    private static final Set<String> LINKED_ALARM_FIELDS = Set.of("message", "minutesBefore");
    private static final Set<String> NOTE_FIELDS = Set.of("text", "date");

    private final CalendarRepository repository;
    private final ItemValidator validator;

    ItemEditor(CalendarRepository repository, ItemValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    /** Without occurrenceDate, changes the whole item or series. With it, changes only that occurrence. */
    Object editItem(String id, LocalDate occurrenceDate, ItemChanges changes) {
        List<String> changedFields = changes.listChangedFields();
        if (changedFields.isEmpty()) {
            throw new CalendarException("Give at least one field to change.");
        }
        return occurrenceDate == null
                ? editWholeItem(id, changes, changedFields)
                : editOneOccurrence(id, occurrenceDate, changes, changedFields);
    }

    private Object editWholeItem(String id, ItemChanges changes, List<String> changedFields) {
        if (ItemIds.isAppointmentId(id)) {
            return editAppointment(id, changes, changedFields);
        }
        if (ItemIds.isAlarmId(id)) {
            return editAlarm(id, changes, changedFields);
        }
        if (ItemIds.isNoteId(id)) {
            return editNote(id, changes, changedFields);
        }
        throw ItemIds.buildUnknownIdError(id);
    }

    private Object editOneOccurrence(String id, LocalDate occurrenceDate, ItemChanges changes,
            List<String> changedFields) {
        if (ItemIds.isAppointmentId(id)) {
            return editAppointmentOccurrence(id, occurrenceDate, changes, changedFields);
        }
        if (ItemIds.isAlarmId(id)) {
            return editAlarmOccurrence(id, occurrenceDate, changes, changedFields);
        }
        if (ItemIds.isNoteId(id)) {
            throw ItemValidator.buildNoteOccurrenceError();
        }
        throw ItemIds.buildUnknownIdError(id);
    }

    private Appointment editAppointment(String id, ItemChanges changes, List<String> changedFields) {
        requireAllowedFields(changedFields, APPOINTMENT_FIELDS, "an appointment");
        Appointment current = repository.findAppointment(id);
        Appointment updated = Json.copyValue(current, Appointment.class);
        updated.title = pickChangedValue(changes.title, updated.title);
        updated.date = pickChangedValue(changes.date, updated.date);
        updated.startTime = pickChangedValue(changes.startTime, updated.startTime);
        updated.endTime = pickChangedValue(changes.endTime, updated.endTime);
        updated.place = pickChangedValue(changes.place, updated.place);
        updated.leadTimeMinutes = pickChangedValue(changes.leadTimeMinutes, updated.leadTimeMinutes);
        updated.attendees = pickChangedValue(changes.attendees, updated.attendees);
        updated.repeat = pickChangedValue(changes.repeat, updated.repeat);
        validator.validateAppointment(updated);
        replaceItem(repository.getAppointments(), current, updated);
        return updated;
    }

    private Appointment editAppointmentOccurrence(String id, LocalDate occurrenceDate, ItemChanges changes,
            List<String> changedFields) {
        requireAllowedFields(changedFields, APPOINTMENT_OCCURRENCE_FIELDS, "one occurrence of an appointment");
        Appointment appointment = repository.findAppointment(id);
        validator.requireSeriesDate(appointment.id, appointment.date, appointment.repeat, occurrenceDate);
        OccurrenceChange change = copyExistingChange(appointment.overrides, occurrenceDate);
        change.title = pickChangedValue(changes.title, change.title);
        change.date = pickChangedValue(changes.date, change.date);
        change.startTime = pickChangedValue(changes.startTime, change.startTime);
        change.endTime = pickChangedValue(changes.endTime, change.endTime);
        change.place = pickChangedValue(changes.place, change.place);
        change.leadTimeMinutes = pickChangedValue(changes.leadTimeMinutes, change.leadTimeMinutes);
        validator.validateAppointmentOccurrence(appointment, change);
        appointment.overrides.put(occurrenceDate, change);
        return appointment;
    }

    private Alarm editAlarm(String id, ItemChanges changes, List<String> changedFields) {
        Alarm current = repository.findAlarm(id);
        if (current.isLinked()) {
            requireAllowedFields(changedFields, LINKED_ALARM_FIELDS, "a linked alarm");
        } else {
            requireAllowedFields(changedFields, FIXED_ALARM_FIELDS, "a fixed alarm");
        }
        Alarm updated = Json.copyValue(current, Alarm.class);
        updated.message = pickChangedValue(changes.message, updated.message);
        updated.date = pickChangedValue(changes.date, updated.date);
        updated.time = pickChangedValue(changes.time, updated.time);
        updated.repeat = pickChangedValue(changes.repeat, updated.repeat);
        updated.minutesBefore = pickChangedValue(changes.minutesBefore, updated.minutesBefore);
        validator.validateAlarm(updated);
        replaceItem(repository.getAlarms(), current, updated);
        return updated;
    }

    private Alarm editAlarmOccurrence(String id, LocalDate occurrenceDate, ItemChanges changes,
            List<String> changedFields) {
        Alarm alarm = repository.findAlarm(id);
        validator.requireFixedAlarm(alarm);
        requireAllowedFields(changedFields, FIXED_ALARM_OCCURRENCE_FIELDS, "one occurrence of an alarm");
        validator.requireSeriesDate(alarm.id, alarm.date, alarm.repeat, occurrenceDate);
        OccurrenceChange change = copyExistingChange(alarm.overrides, occurrenceDate);
        change.message = pickChangedValue(changes.message, change.message);
        change.date = pickChangedValue(changes.date, change.date);
        change.time = pickChangedValue(changes.time, change.time);
        alarm.overrides.put(occurrenceDate, change);
        return alarm;
    }

    private Note editNote(String id, ItemChanges changes, List<String> changedFields) {
        requireAllowedFields(changedFields, NOTE_FIELDS, "a note");
        Note current = repository.findNote(id);
        if (changes.date != null && current.appointmentId != null) {
            throw new CalendarException("This note belongs to an appointment. Only text can change.");
        }
        Note updated = Json.copyValue(current, Note.class);
        updated.text = pickChangedValue(changes.text, updated.text);
        updated.date = pickChangedValue(changes.date, updated.date);
        validator.validateNote(updated);
        replaceItem(repository.getNotes(), current, updated);
        return updated;
    }

    private static OccurrenceChange copyExistingChange(Map<LocalDate, OccurrenceChange> overrides, LocalDate day) {
        OccurrenceChange existingChange = overrides.get(day);
        return existingChange == null
                ? new OccurrenceChange()
                : Json.copyValue(existingChange, OccurrenceChange.class);
    }

    private static void requireAllowedFields(List<String> changedFields, Set<String> allowedFields,
            String itemKind) {
        List<String> rejectedFields = changedFields.stream()
                .filter(field -> !allowedFields.contains(field))
                .collect(Collectors.toList());
        if (!rejectedFields.isEmpty()) {
            throw new CalendarException(
                    String.join(", ", rejectedFields) + " cannot be changed on " + itemKind + ".");
        }
    }

    private static <T> void replaceItem(List<T> items, T current, T updated) {
        items.set(items.indexOf(current), updated);
    }
}
```

- [ ] **Step 5: Write `ItemRemover.java`**

`src/main/java/io/meterian/aicalendar/calendar/ItemRemover.java`:

```java
package io.meterian.aicalendar.calendar;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Removes a whole item with its linked items, or cancels one occurrence of a repeating item. */
class ItemRemover {

    private final CalendarRepository repository;
    private final ItemValidator validator;

    ItemRemover(CalendarRepository repository, ItemValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    /** Returns one short line for each removed thing, for example "A-1" or "A-1 on 2026-10-05". */
    List<String> removeItem(String id, LocalDate occurrenceDate) {
        return occurrenceDate == null ? removeWholeItem(id) : cancelOccurrence(id, occurrenceDate);
    }

    private List<String> cancelOccurrence(String id, LocalDate occurrenceDate) {
        Map<LocalDate, OccurrenceChange> overrides = findSeriesOverrides(id, occurrenceDate);
        overrides.computeIfAbsent(occurrenceDate, day -> new OccurrenceChange()).cancelled = true;
        return List.of(id + " on " + occurrenceDate);
    }

    private Map<LocalDate, OccurrenceChange> findSeriesOverrides(String id, LocalDate occurrenceDate) {
        if (ItemIds.isAppointmentId(id)) {
            Appointment appointment = repository.findAppointment(id);
            validator.requireSeriesDate(appointment.id, appointment.date, appointment.repeat, occurrenceDate);
            return appointment.overrides;
        }
        if (ItemIds.isAlarmId(id)) {
            Alarm alarm = repository.findAlarm(id);
            validator.requireFixedAlarm(alarm);
            validator.requireSeriesDate(alarm.id, alarm.date, alarm.repeat, occurrenceDate);
            return alarm.overrides;
        }
        if (ItemIds.isNoteId(id)) {
            throw ItemValidator.buildNoteOccurrenceError();
        }
        throw ItemIds.buildUnknownIdError(id);
    }

    private List<String> removeWholeItem(String id) {
        if (ItemIds.isAppointmentId(id)) {
            return removeAppointmentWithLinkedItems(id);
        }
        if (ItemIds.isAlarmId(id)) {
            repository.getAlarms().remove(repository.findAlarm(id));
            return List.of(id);
        }
        if (ItemIds.isNoteId(id)) {
            repository.getNotes().remove(repository.findNote(id));
            return List.of(id);
        }
        throw ItemIds.buildUnknownIdError(id);
    }

    private List<String> removeAppointmentWithLinkedItems(String appointmentId) {
        repository.getAppointments().remove(repository.findAppointment(appointmentId));
        List<String> removedIds = new ArrayList<>();
        removedIds.add(appointmentId);
        removedIds.addAll(removeMatchingItems(repository.getAlarms(),
                alarm -> appointmentId.equals(alarm.appointmentId), alarm -> alarm.id));
        removedIds.addAll(removeMatchingItems(repository.getNotes(),
                note -> appointmentId.equals(note.appointmentId), note -> note.id));
        return removedIds;
    }

    private static <T> List<String> removeMatchingItems(List<T> items, Predicate<T> isMatch,
            Function<T, String> readId) {
        List<String> removedIds = items.stream().filter(isMatch).map(readId).collect(Collectors.toList());
        items.removeIf(isMatch);
        return removedIds;
    }
}
```

- [ ] **Step 6: Connect the editor and the remover to `CalendarService.java`**

Add these two fields below `queries`:

```java
    private final ItemEditor editor;
    private final ItemRemover remover;
```

Add these two lines at the end of the constructor:

```java
        this.editor = new ItemEditor(repository, validator);
        this.remover = new ItemRemover(repository, validator);
```

Add these two methods after `addNote`:

```java
    /** Without occurrenceDate, changes the whole item or series. With it, changes only that occurrence. */
    public synchronized Object editItem(String id, LocalDate occurrenceDate, ItemChanges changes) {
        Object updatedItem = editor.editItem(id, occurrenceDate, changes);
        repository.saveChanges();
        return updatedItem;
    }

    /** Without occurrenceDate, removes the whole item (and an appointment's linked alarms and notes). */
    public synchronized List<String> removeItem(String id, LocalDate occurrenceDate) {
        List<String> removed = remover.removeItem(id, occurrenceDate);
        repository.saveChanges();
        return removed;
    }
```

- [ ] **Step 7: Run both service tests to verify they pass**

Run: `mvn -q test -Dtest='CalendarService*'`
Expected: PASS (16 tests).

- [ ] **Step 8: Commit**

```bash
git add src
git commit -m "feat: edit and remove items, whole series or one occurrence" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Ollama chat client

**Files:**
- Create in `src/main/java/io/meterian/aicalendar/chat/`: `ChatMessage.java`, `ToolCall.java`, `ChatModel.java`, `ChatModelException.java`, `OllamaClient.java`
- Test: `src/test/java/io/meterian/aicalendar/chat/OllamaClientTest.java`

**Interfaces:**
- Consumes: `Json.MAPPER`.
- Produces:
  - `ChatMessage` — public fields `role`, `content`, `thinking`, `toolCalls` (JSON `tool_calls`), `toolName` (JSON `tool_name`); static `buildSystemMessage(String)`, `buildUserMessage(String)`, `buildAssistantMessage(String)`, `buildToolResultMessage(String toolName, String content)`; `boolean hasToolCalls()`.
  - `ToolCall` — public field `FunctionCall function`; nested `ToolCall.FunctionCall` with `String name`, `JsonNode arguments`; static `buildToolCall(String name, JsonNode arguments)`.
  - `interface ChatModel { ChatMessage requestReply(List<ChatMessage> messages, List<JsonNode> tools); }`
  - `ChatModelException extends RuntimeException`
  - `new OllamaClient(HttpClient httpClient, URI baseUri, String model)` implements `ChatModel`.

- [ ] **Step 1: Write the failing test**

`src/test/java/io/meterian/aicalendar/chat/OllamaClientTest.java`:

```java
package io.meterian.aicalendar.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import io.meterian.aicalendar.Json;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OllamaClientTest {

    private HttpServer server;
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private volatile int replyStatus = 200;
    private volatile String replyBody = "{}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = replyBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(replyStatus, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private OllamaClient buildClient() {
        return new OllamaClient(HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "test-model");
    }

    private static List<JsonNode> buildOneToolDefinition() {
        ObjectNode function = Json.MAPPER.createObjectNode();
        function.put("name", "list-day");
        ObjectNode definition = Json.MAPPER.createObjectNode();
        definition.put("type", "function");
        definition.set("function", function);
        return List.of(definition);
    }

    @Test
    void sendsModelMessagesToolsAndNoStreaming() throws Exception {
        replyBody = "{\"model\":\"test-model\",\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"},\"done\":true}";

        ChatMessage reply = buildClient().requestReply(
                List.of(ChatMessage.buildUserMessage("Hi")), buildOneToolDefinition());

        assertEquals("Hello", reply.content);
        assertFalse(reply.hasToolCalls());
        JsonNode request = Json.MAPPER.readTree(receivedBody.get());
        assertEquals("test-model", request.get("model").asText());
        assertFalse(request.get("stream").asBoolean());
        assertEquals("user", request.get("messages").get(0).get("role").asText());
        assertEquals("Hi", request.get("messages").get(0).get("content").asText());
        assertEquals("list-day", request.get("tools").get(0).get("function").get("name").asText());
    }

    @Test
    void parsesToolCalls() {
        replyBody = "{\"message\":{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":[{\"function\":"
                + "{\"name\":\"list-day\",\"arguments\":{\"date\":\"2026-09-28\"}}}]},\"done\":true}";

        ChatMessage reply = buildClient().requestReply(List.of(ChatMessage.buildUserMessage("Monday?")), List.of());

        assertTrue(reply.hasToolCalls());
        assertEquals("list-day", reply.toolCalls.get(0).function.name);
        assertEquals("2026-09-28", reply.toolCalls.get(0).function.arguments.get("date").asText());
    }

    @Test
    void toolResultMessageUsesOllamaFieldNames() {
        JsonNode json = Json.MAPPER.valueToTree(ChatMessage.buildToolResultMessage("list-day", "[]"));

        assertEquals("tool", json.get("role").asText());
        assertEquals("list-day", json.get("tool_name").asText());
        assertEquals("[]", json.get("content").asText());
    }

    @Test
    void httpErrorThrowsWithStatusAndBody() {
        replyStatus = 500;
        replyBody = "model not found";

        ChatModelException error = assertThrows(ChatModelException.class,
                () -> buildClient().requestReply(List.of(ChatMessage.buildUserMessage("Hi")), List.of()));

        assertTrue(error.getMessage().contains("HTTP 500"));
        assertTrue(error.getMessage().contains("model not found"));
    }

    @Test
    void unreachableServerThrows() {
        OllamaClient client = buildClient();
        server.stop(0);

        assertThrows(ChatModelException.class,
                () -> client.requestReply(List.of(ChatMessage.buildUserMessage("Hi")), List.of()));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=OllamaClientTest`
Expected: COMPILATION ERROR, `cannot find symbol: class OllamaClient`.

- [ ] **Step 3: Write the chat classes**

`src/main/java/io/meterian/aicalendar/chat/ToolCall.java`:

```java
package io.meterian.aicalendar.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/** One tool call from the model. In the Ollama native API, arguments is a JSON object, not a string. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolCall {
    public FunctionCall function;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FunctionCall {
        public String name;
        public JsonNode arguments;
    }

    public static ToolCall buildToolCall(String name, JsonNode arguments) {
        ToolCall call = new ToolCall();
        call.function = new FunctionCall();
        call.function.name = name;
        call.function.arguments = arguments;
        return call;
    }
}
```

`src/main/java/io/meterian/aicalendar/chat/ChatMessage.java`:

```java
package io.meterian.aicalendar.chat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/** One message in the Ollama /api/chat format. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage {
    public String role;
    public String content;
    /** Reasoning text from models such as gpt-oss. Sent back as it came. */
    public String thinking;
    @JsonProperty("tool_calls")
    public List<ToolCall> toolCalls = new ArrayList<>();
    /** Only for role "tool": the tool that made this result. */
    @JsonProperty("tool_name")
    public String toolName;

    public static ChatMessage buildSystemMessage(String content) {
        return buildMessage("system", content);
    }

    public static ChatMessage buildUserMessage(String content) {
        return buildMessage("user", content);
    }

    public static ChatMessage buildAssistantMessage(String content) {
        return buildMessage("assistant", content);
    }

    public static ChatMessage buildToolResultMessage(String toolName, String content) {
        ChatMessage message = buildMessage("tool", content);
        message.toolName = toolName;
        return message;
    }

    @JsonIgnore
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    private static ChatMessage buildMessage(String role, String content) {
        ChatMessage message = new ChatMessage();
        message.role = role;
        message.content = content;
        return message;
    }
}
```

`src/main/java/io/meterian/aicalendar/chat/ChatModel.java`:

```java
package io.meterian.aicalendar.chat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Sends the conversation and the tool definitions to a model and returns its one reply. */
public interface ChatModel {
    ChatMessage requestReply(List<ChatMessage> messages, List<JsonNode> tools);
}
```

`src/main/java/io/meterian/aicalendar/chat/ChatModelException.java`:

```java
package io.meterian.aicalendar.chat;

public class ChatModelException extends RuntimeException {

    public ChatModelException(String message) {
        super(message);
    }

    public ChatModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 4: Write `OllamaClient.java`**

`src/main/java/io/meterian/aicalendar/chat/OllamaClient.java`:

```java
package io.meterian.aicalendar.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/** Calls POST {baseUri}/api/chat of the Ollama native API, without streaming. */
public class OllamaClient implements ChatModel {

    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);
    private static final int HTTP_OK = 200;

    private final HttpClient httpClient;
    private final URI chatUri;
    private final String model;

    public OllamaClient(HttpClient httpClient, URI baseUri, String model) {
        this.httpClient = httpClient;
        this.chatUri = baseUri.resolve("/api/chat");
        this.model = model;
    }

    @Override
    public ChatMessage requestReply(List<ChatMessage> messages, List<JsonNode> tools) {
        HttpResponse<String> response = sendRequest(buildChatRequest(messages, tools));
        if (response.statusCode() != HTTP_OK) {
            throw new ChatModelException("Ollama returned HTTP " + response.statusCode() + ": " + response.body());
        }
        return parseReplyMessage(response.body());
    }

    private HttpRequest buildChatRequest(List<ChatMessage> messages, List<JsonNode> tools) {
        ObjectNode body = Json.MAPPER.createObjectNode();
        body.put("model", model);
        body.set("messages", Json.MAPPER.valueToTree(messages));
        body.set("tools", Json.MAPPER.valueToTree(tools));
        body.put("stream", false);
        return HttpRequest.newBuilder(chatUri)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.writeJson(body)))
                .build();
    }

    private HttpResponse<String> sendRequest(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ChatModelException("Cannot reach Ollama at " + chatUri + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChatModelException("Interrupted while waiting for Ollama.", e);
        }
    }

    private static ChatMessage parseReplyMessage(String responseBody) {
        try {
            JsonNode message = Json.MAPPER.readTree(responseBody).path("message");
            if (message.isMissingNode()) {
                throw new ChatModelException("Ollama reply has no message: " + responseBody);
            }
            return Json.MAPPER.treeToValue(message, ChatMessage.class);
        } catch (IOException e) {
            throw new ChatModelException("Ollama sent a reply that is not valid JSON: " + responseBody, e);
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -q test -Dtest=OllamaClientTest`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: add hand-written Ollama /api/chat client" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Tool framework and the two time tools

**Files:**
- Create in `src/main/java/io/meterian/aicalendar/tools/`: `Tool.java`, `AbstractTool.java`, `ToolArgumentException.java`, `ToolArguments.java`, `SchemaBuilder.java`, `ToolRegistry.java`, `GetCurrentDateTimeTool.java`, `GetDefaultLeadTimeTool.java`
- Test: `src/test/java/io/meterian/aicalendar/tools/ToolArgumentsTest.java`, `src/test/java/io/meterian/aicalendar/tools/ToolRegistryTest.java`

**Interfaces:**
- Consumes: `Json`, `CalendarException`, `Attendee`, `RepeatRule`, `Frequency`.
- Produces:
  - `interface Tool { String getName(); String getDescription(); ObjectNode buildParametersSchema(); String execute(JsonNode arguments); default boolean requiresApproval() {false}; default String describeCall(JsonNode arguments); }`
  - `abstract class AbstractTool implements Tool` — `final execute` wraps `protected abstract String run(ToolArguments arguments)` and turns `ToolArgumentException` and `CalendarException` into `ERROR: <message>`.
  - `ToolArguments(JsonNode)` — `hasField`, `readRequiredText`, `readOptionalText`, `readRequiredDate`, `readOptionalDate`, `readRequiredTime`, `readOptionalTime`, `readRequiredInteger` (`int`), `readOptionalInteger` (`Integer`), `readOptionalRepeat` (`RepeatRule`), `readOptionalAttendees` (`List<Attendee>`).
  - `SchemaBuilder` — `addString`, `addInteger`, `addEnum`, `addProperty`, `build`; static `buildTypedNode`, `buildRepeatSchema`, `buildAttendeesSchema`.
  - `ToolRegistry(List<Tool>)` — `Optional<Tool> findTool(String name)`, `List<JsonNode> buildToolDefinitions()`.
  - `GetCurrentDateTimeTool(Clock)`, `GetDefaultLeadTimeTool()` with `static final int DEFAULT_LEAD_TIME_MINUTES = 30`.

- [ ] **Step 1: Write the failing tests**

`src/test/java/io/meterian/aicalendar/tools/ToolArgumentsTest.java`:

```java
package io.meterian.aicalendar.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.Attendee;
import io.meterian.aicalendar.calendar.Frequency;
import io.meterian.aicalendar.calendar.RepeatRule;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolArgumentsTest {

    /** Single quotes keep the JSON readable in Java strings. */
    private static ToolArguments parseArguments(String singleQuotedJson) throws Exception {
        return new ToolArguments(Json.MAPPER.readTree(singleQuotedJson.replace('\'', '"')));
    }

    private static void assertArgumentError(String expectedMessage, Runnable action) {
        ToolArgumentException error = assertThrows(ToolArgumentException.class, action::run);
        assertEquals(expectedMessage, error.getMessage());
    }

    @Test
    void readsTextDateTimeAndInteger() throws Exception {
        ToolArguments arguments = parseArguments(
                "{'title':' Dentist ','date':'2026-09-30','startTime':'15:00','leadTimeMinutes':30}");

        assertEquals("Dentist", arguments.readRequiredText("title"));
        assertEquals(LocalDate.of(2026, 9, 30), arguments.readRequiredDate("date"));
        assertEquals(LocalTime.of(15, 0), arguments.readRequiredTime("startTime"));
        assertEquals(30, arguments.readRequiredInteger("leadTimeMinutes"));
        assertNull(arguments.readOptionalText("place"));
    }

    @Test
    void acceptsLooseNumberAndTimeFormats() throws Exception {
        ToolArguments arguments = parseArguments("{'lead':'30','early':'9:00','seconds':'15:00:00'}");

        assertEquals(30, arguments.readRequiredInteger("lead"));
        assertEquals(LocalTime.of(9, 0), arguments.readRequiredTime("early"));
        assertEquals(LocalTime.of(15, 0), arguments.readRequiredTime("seconds"));
    }

    @Test
    void missingOrBlankRequiredFieldIsNamed() throws Exception {
        ToolArguments arguments = parseArguments("{'title':'   '}");

        assertFalse(arguments.hasField("title"));
        assertArgumentError("title is missing. Ask the user for it.", () -> arguments.readRequiredText("title"));
        assertArgumentError("startTime is missing. Ask the user for it.",
                () -> arguments.readRequiredTime("startTime"));
    }

    @Test
    void badFormatsAreExplained() throws Exception {
        ToolArguments arguments = parseArguments("{'date':'30/09/2026','time':'3pm','lead':'soon'}");

        assertArgumentError("date must be a date in YYYY-MM-DD format, not '30/09/2026'.",
                () -> arguments.readRequiredDate("date"));
        assertArgumentError("time must be a time in HH:mm (24-hour) format, not '3pm'.",
                () -> arguments.readRequiredTime("time"));
        assertArgumentError("lead must be a whole number, not 'soon'.",
                () -> arguments.readRequiredInteger("lead"));
    }

    @Test
    void readsRepeatRuleWithLowercaseValues() throws Exception {
        ToolArguments arguments = parseArguments(
                "{'repeat':{'frequency':'weekly','daysOfWeek':['monday','Thursday'],'until':'2026-12-31'}}");

        RepeatRule rule = arguments.readOptionalRepeat("repeat");

        assertEquals(Frequency.WEEKLY, rule.frequency);
        assertEquals(List.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), rule.daysOfWeek);
        assertEquals(LocalDate.of(2026, 12, 31), rule.until);
        assertNull(parseArguments("{}").readOptionalRepeat("repeat"));
    }

    @Test
    void badFrequencyListsTheAllowedValues() throws Exception {
        ToolArguments arguments = parseArguments("{'repeat':{'frequency':'hourly'}}");

        assertArgumentError("repeat.frequency must be one of [DAILY, WEEKLY, MONTHLY, YEARLY], not 'hourly'.",
                () -> arguments.readOptionalRepeat("repeat"));
    }

    @Test
    void readsAttendeesAndNeedsBothFields() throws Exception {
        List<Attendee> attendees = parseArguments(
                "{'attendees':[{'name':'Anna Rossi','email':'anna@example.com'}]}").readOptionalAttendees("attendees");

        assertEquals("Anna Rossi", attendees.get(0).name);
        assertEquals("anna@example.com", attendees.get(0).email);

        ToolArguments noEmail = parseArguments("{'attendees':[{'name':'Anna Rossi'}]}");
        assertArgumentError("email is missing. Ask the user for it.", () -> noEmail.readOptionalAttendees("attendees"));
    }
}
```

`src/test/java/io/meterian/aicalendar/tools/ToolRegistryTest.java`:

```java
package io.meterian.aicalendar.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.meterian.aicalendar.Json;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    private final Clock clock = Clock.fixed(LocalDateTime.of(2026, 9, 24, 10, 0).atZone(ROME).toInstant(), ROME);

    @Test
    void buildToolDefinitionsUsesOllamaFormat() {
        ToolRegistry registry = new ToolRegistry(List.of(new GetCurrentDateTimeTool(clock), new GetDefaultLeadTimeTool()));

        List<JsonNode> definitions = registry.buildToolDefinitions();

        assertEquals(2, definitions.size());
        JsonNode first = definitions.get(0);
        assertEquals("function", first.get("type").asText());
        assertEquals("get-current-date-time", first.get("function").get("name").asText());
        assertTrue(first.get("function").get("description").asText().length() > 20);
        assertEquals("object", first.get("function").get("parameters").get("type").asText());
    }

    @Test
    void findToolByName() {
        ToolRegistry registry = new ToolRegistry(List.of(new GetDefaultLeadTimeTool()));

        assertTrue(registry.findTool("get-default-lead-time").isPresent());
        assertTrue(registry.findTool("no-such-tool").isEmpty());
    }

    @Test
    void duplicateNamesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ToolRegistry(List.of(new GetDefaultLeadTimeTool(), new GetDefaultLeadTimeTool())));
    }

    @Test
    void currentDateTimeToolReportsClockValues() throws Exception {
        JsonNode result = Json.MAPPER.readTree(
                new GetCurrentDateTimeTool(clock).execute(Json.MAPPER.createObjectNode()));

        assertEquals("2026-09-24", result.get("date").asText());
        assertEquals("10:00", result.get("time").asText());
        assertEquals("THURSDAY", result.get("dayOfWeek").asText());
        assertEquals("Europe/Rome", result.get("timeZone").asText());
    }

    @Test
    void defaultLeadTimeToolReturnsThirty() {
        assertEquals("30", new GetDefaultLeadTimeTool().execute(Json.MAPPER.createObjectNode()));
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q test -Dtest='ToolArgumentsTest,ToolRegistryTest'`
Expected: COMPILATION ERROR, `cannot find symbol: class ToolArguments`.

- [ ] **Step 3: Write `Tool`, `AbstractTool` and `ToolArgumentException`**

`src/main/java/io/meterian/aicalendar/tools/Tool.java`:

```java
package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** A tool that the model can call. execute never throws: it returns JSON, or text that starts with "ERROR:". */
public interface Tool {

    String getName();

    String getDescription();

    ObjectNode buildParametersSchema();

    String execute(JsonNode arguments);

    /** True when the agent loop must ask the user before it runs this tool. */
    default boolean requiresApproval() {
        return false;
    }

    /** The text that the user sees before approving a call. It may throw if the arguments are bad. */
    default String describeCall(JsonNode arguments) {
        return getName() + " " + arguments;
    }
}
```

`src/main/java/io/meterian/aicalendar/tools/ToolArgumentException.java`:

```java
package io.meterian.aicalendar.tools;

/** A tool argument is missing or has a bad format. The message goes to the model. */
public class ToolArgumentException extends RuntimeException {

    public ToolArgumentException(String message) {
        super(message);
    }
}
```

`src/main/java/io/meterian/aicalendar/tools/AbstractTool.java`:

```java
package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.JsonNode;
import io.meterian.aicalendar.calendar.CalendarException;

/** Base for all tools: turns argument and calendar errors into "ERROR: ..." results. */
public abstract class AbstractTool implements Tool {

    @Override
    public final String execute(JsonNode arguments) {
        try {
            return run(new ToolArguments(arguments));
        } catch (ToolArgumentException | CalendarException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    protected abstract String run(ToolArguments arguments);
}
```

- [ ] **Step 4: Write `ToolArguments.java`**

`src/main/java/io/meterian/aicalendar/tools/ToolArguments.java`:

```java
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
```

- [ ] **Step 5: Write `SchemaBuilder.java` and `ToolRegistry.java`**

`src/main/java/io/meterian/aicalendar/tools/SchemaBuilder.java`:

```java
package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import java.util.List;

/** Builds the JSON schema of a tool's parameters. */
public final class SchemaBuilder {

    private static final List<String> DAYS = List.of(
            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");

    private final ObjectNode properties = Json.MAPPER.createObjectNode();
    private final ArrayNode required = Json.MAPPER.createArrayNode();

    public SchemaBuilder addString(String name, String description, boolean isRequired) {
        return addProperty(name, buildTypedNode("string", description), isRequired);
    }

    public SchemaBuilder addInteger(String name, String description, boolean isRequired) {
        return addProperty(name, buildTypedNode("integer", description), isRequired);
    }

    public SchemaBuilder addEnum(String name, String description, List<String> values, boolean isRequired) {
        ObjectNode schema = buildTypedNode("string", description);
        schema.set("enum", Json.MAPPER.valueToTree(values));
        return addProperty(name, schema, isRequired);
    }

    public SchemaBuilder addProperty(String name, ObjectNode schema, boolean isRequired) {
        properties.set(name, schema);
        if (isRequired) {
            required.add(name);
        }
        return this;
    }

    public ObjectNode build() {
        ObjectNode schema = Json.MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", properties);
        if (required.size() > 0) {
            schema.set("required", required);
        }
        return schema;
    }

    public static ObjectNode buildTypedNode(String type, String description) {
        ObjectNode node = Json.MAPPER.createObjectNode();
        node.put("type", type);
        node.put("description", description);
        return node;
    }

    public static ObjectNode buildRepeatSchema() {
        ObjectNode dayItem = Json.MAPPER.createObjectNode();
        dayItem.put("type", "string");
        dayItem.set("enum", Json.MAPPER.valueToTree(DAYS));
        ObjectNode days = buildTypedNode("array", "Only for WEEKLY: the days, for example [\"MONDAY\", \"THURSDAY\"]. "
                + "Every weekday is MONDAY to FRIDAY.");
        days.set("items", dayItem);
        ObjectNode schema = new SchemaBuilder()
                .addEnum("frequency", "How often it repeats.", List.of("DAILY", "WEEKLY", "MONTHLY", "YEARLY"), true)
                .addProperty("daysOfWeek", days, false)
                .addString("until", "Optional last date, YYYY-MM-DD. Leave it out for no end.", false)
                .build();
        schema.put("description", "Optional repeat rule. Leave it out for a one-time item.");
        return schema;
    }

    public static ObjectNode buildAttendeesSchema() {
        ObjectNode item = new SchemaBuilder()
                .addString("name", "Full name of the attendee.", true)
                .addString("email", "Email address of the attendee. Never guess it.", true)
                .build();
        ObjectNode schema = buildTypedNode("array", "People to invite. Each needs a name and an email address.");
        schema.set("items", item);
        return schema;
    }
}
```

`src/main/java/io/meterian/aicalendar/tools/ToolRegistry.java`:

```java
package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Holds the tools, builds their definitions for the model, and finds a tool by name. */
public class ToolRegistry {

    private final Map<String, Tool> toolsByName = new LinkedHashMap<>();

    public ToolRegistry(List<Tool> tools) {
        for (Tool tool : tools) {
            if (toolsByName.put(tool.getName(), tool) != null) {
                throw new IllegalArgumentException("Two tools are named " + tool.getName());
            }
        }
    }

    public Optional<Tool> findTool(String name) {
        return Optional.ofNullable(toolsByName.get(name));
    }

    /** One {"type":"function","function":{name, description, parameters}} entry per tool. */
    public List<JsonNode> buildToolDefinitions() {
        List<JsonNode> definitions = new ArrayList<>();
        for (Tool tool : toolsByName.values()) {
            ObjectNode function = Json.MAPPER.createObjectNode();
            function.put("name", tool.getName());
            function.put("description", tool.getDescription());
            function.set("parameters", tool.buildParametersSchema());
            ObjectNode definition = Json.MAPPER.createObjectNode();
            definition.put("type", "function");
            definition.set("function", function);
            definitions.add(definition);
        }
        return definitions;
    }
}
```

- [ ] **Step 6: Write the two time tools**

`src/main/java/io/meterian/aicalendar/tools/GetCurrentDateTimeTool.java`:

```java
package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

public class GetCurrentDateTimeTool extends AbstractTool {

    private final Clock clock;

    public GetCurrentDateTimeTool(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String getName() {
        return "get-current-date-time";
    }

    @Override
    public String getDescription() {
        return "Get the current date, time, day of the week and time zone. Call this before you turn a relative "
                + "date such as 'Wednesday', 'tomorrow' or 'next Monday' into a date.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder().build();
    }

    @Override
    protected String run(ToolArguments arguments) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        ObjectNode result = Json.MAPPER.createObjectNode();
        result.put("date", now.toLocalDate().toString());
        result.put("time", now.toLocalTime().truncatedTo(ChronoUnit.MINUTES).toString());
        result.put("dayOfWeek", now.getDayOfWeek().toString());
        result.put("timeZone", now.getZone().getId());
        return Json.writeJson(result);
    }
}
```

`src/main/java/io/meterian/aicalendar/tools/GetDefaultLeadTimeTool.java`:

```java
package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class GetDefaultLeadTimeTool extends AbstractTool {

    public static final int DEFAULT_LEAD_TIME_MINUTES = 30;

    @Override
    public String getName() {
        return "get-default-lead-time";
    }

    @Override
    public String getDescription() {
        return "Get the default number of minutes before an appointment when its alert shows. If the user gave "
                + "no lead time, call this and ask the user if the default is OK.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder().build();
    }

    @Override
    protected String run(ToolArguments arguments) {
        return String.valueOf(DEFAULT_LEAD_TIME_MINUTES);
    }
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `mvn -q test -Dtest='ToolArgumentsTest,ToolRegistryTest'`
Expected: PASS (12 tests).

- [ ] **Step 8: Commit**

```bash
git add src
git commit -m "feat: add tool framework, argument reader and time tools" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: Tools to create, find and list

**Files:**
- Create in `src/main/java/io/meterian/aicalendar/tools/`: `SetAppointmentTool.java`, `SetAlarmTool.java`, `AddNoteTool.java`, `FindItemsTool.java`, `ListDayTool.java`
- Test: `src/test/java/io/meterian/aicalendar/tools/CalendarToolsTest.java`

**Interfaces:**
- Consumes: Task 4 `CalendarService` (`addAppointment`, `addAlarm`, `addNote`, `findItems`, `listDay`); Task 7 `AbstractTool`, `ToolArguments`, `SchemaBuilder`.
- Produces: tool classes, each with constructor `(CalendarService service)` and names `set-appointment`, `set-alarm`, `add-note`, `find-items`, `list-day`.

- [ ] **Step 1: Write the failing test**

`src/test/java/io/meterian/aicalendar/tools/CalendarToolsTest.java`:

```java
package io.meterian.aicalendar.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.CalendarData;
import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.calendar.CalendarStore;
import io.meterian.aicalendar.calendar.OccurrenceExpander;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CalendarToolsTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    static final Clock CLOCK = Clock.fixed(LocalDateTime.of(2026, 9, 24, 10, 0).atZone(ROME).toInstant(), ROME);

    @TempDir
    Path tempDir;

    private CalendarService service;

    @BeforeEach
    void createService() {
        service = buildService(tempDir.resolve("calendar.json"));
    }

    static CalendarService buildService(Path file) {
        return new CalendarService(new CalendarStore(file), new CalendarData(), new OccurrenceExpander(), CLOCK);
    }

    static JsonNode parseArguments(String singleQuotedJson) throws Exception {
        return Json.MAPPER.readTree(singleQuotedJson.replace('\'', '"'));
    }

    @Test
    void setAppointmentCreatesItem() throws Exception {
        String result = new SetAppointmentTool(service).execute(parseArguments(
                "{'title':'Dentist','date':'2026-09-30','startTime':'15:00','leadTimeMinutes':30,'place':'Via Roma 10'}"));

        JsonNode appointment = Json.MAPPER.readTree(result);
        assertEquals("A-1", appointment.get("id").asText());
        assertEquals("15:00", appointment.get("startTime").asText());
        assertEquals("Via Roma 10", appointment.get("place").asText());
    }

    @Test
    void setAppointmentWithoutStartTimeAsksForIt() throws Exception {
        String result = new SetAppointmentTool(service).execute(parseArguments(
                "{'title':'Dentist','date':'2026-09-30','leadTimeMinutes':30}"));

        assertEquals("ERROR: startTime is missing. Ask the user for it.", result);
    }

    @Test
    void setAppointmentWithWeeklyRepeatAndAttendees() throws Exception {
        String result = new SetAppointmentTool(service).execute(parseArguments(
                "{'title':'Gym','date':'2026-09-28','startTime':'18:00','leadTimeMinutes':15,"
                        + "'repeat':{'frequency':'WEEKLY','daysOfWeek':['MONDAY']},"
                        + "'attendees':[{'name':'Anna Rossi','email':'anna@example.com'}]}"));

        JsonNode appointment = Json.MAPPER.readTree(result);
        assertEquals("WEEKLY", appointment.get("repeat").get("frequency").asText());
        assertEquals("anna@example.com", appointment.get("attendees").get(0).get("email").asText());
    }

    @Test
    void setAlarmSupportsBothForms() throws Exception {
        new SetAppointmentTool(service).execute(parseArguments(
                "{'title':'Dentist','date':'2026-09-30','startTime':'15:00','leadTimeMinutes':30}"));
        SetAlarmTool tool = new SetAlarmTool(service);

        assertEquals("L-1", Json.MAPPER.readTree(tool.execute(parseArguments(
                "{'message':'Wake up','date':'2026-09-25','time':'07:00'}"))).get("id").asText());
        assertEquals("L-2", Json.MAPPER.readTree(tool.execute(parseArguments(
                "{'message':'Leave now','appointmentId':'A-1','minutesBefore':45}"))).get("id").asText());
        assertEquals("ERROR: Give either date and time, or appointmentId and minutesBefore.",
                tool.execute(parseArguments("{'message':'Hello'}")));
    }

    @Test
    void addNoteToAppointment() throws Exception {
        new SetAppointmentTool(service).execute(parseArguments(
                "{'title':'Dentist','date':'2026-09-30','startTime':'15:00','leadTimeMinutes':30}"));

        JsonNode note = Json.MAPPER.readTree(new AddNoteTool(service).execute(parseArguments(
                "{'text':'Bring the X-ray','appointmentId':'A-1'}")));

        assertEquals("N-1", note.get("id").asText());
    }

    @Test
    void findItemsAddsTypeField() throws Exception {
        new SetAppointmentTool(service).execute(parseArguments(
                "{'title':'Dentist','date':'2026-09-30','startTime':'15:00','leadTimeMinutes':30}"));
        new SetAlarmTool(service).execute(parseArguments(
                "{'message':'Call the dentist','date':'2026-09-25','time':'09:00'}"));

        JsonNode items = Json.MAPPER.readTree(new FindItemsTool(service).execute(parseArguments("{'query':'dentist'}")));

        assertEquals(2, items.size());
        assertEquals("appointment", items.get(0).get("type").asText());
        assertEquals("alarm", items.get(1).get("type").asText());
    }

    @Test
    void listDayReturnsEntriesSortedByTime() throws Exception {
        SetAppointmentTool appointments = new SetAppointmentTool(service);
        appointments.execute(parseArguments(
                "{'title':'Gym','date':'2026-09-28','startTime':'18:00','leadTimeMinutes':15,"
                        + "'repeat':{'frequency':'WEEKLY','daysOfWeek':['MONDAY']}}"));
        appointments.execute(parseArguments(
                "{'title':'Dentist','date':'2026-09-28','startTime':'09:00','leadTimeMinutes':30}"));
        new SetAlarmTool(service).execute(parseArguments("{'message':'Wake up','date':'2026-09-28','time':'07:00'}"));
        new AddNoteTool(service).execute(parseArguments("{'text':'Pay rent','date':'2026-09-28'}"));

        JsonNode day = Json.MAPPER.readTree(new ListDayTool(service).execute(parseArguments("{'date':'2026-09-28'}")));

        assertEquals("MONDAY", day.get("dayOfWeek").asText());
        assertEquals("Dentist", day.get("appointments").get(0).get("title").asText());
        assertEquals("Gym", day.get("appointments").get(1).get("title").asText());
        assertEquals("2026-09-28", day.get("appointments").get(1).get("occurrenceDate").asText());
        assertTrue(day.get("appointments").get(0).get("occurrenceDate") == null);
        assertEquals("07:00", day.get("alarms").get(0).get("time").asText());
        assertEquals("Pay rent", day.get("notes").get(0).get("text").asText());
    }

    @Test
    void saveFailureBecomesErrorResult() throws Exception {
        Path notADirectory = tempDir.resolve("afile");
        Files.writeString(notADirectory, "x");
        CalendarService broken = buildService(notADirectory.resolve("calendar.json"));

        String result = new SetAppointmentTool(broken).execute(parseArguments(
                "{'title':'Dentist','date':'2026-09-30','startTime':'15:00','leadTimeMinutes':30}"));

        assertTrue(result.startsWith("ERROR: Cannot save"), result);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=CalendarToolsTest`
Expected: COMPILATION ERROR, `cannot find symbol: class SetAppointmentTool`.

- [ ] **Step 3: Write the create tools**

`src/main/java/io/meterian/aicalendar/tools/SetAppointmentTool.java`:

```java
package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.Appointment;
import io.meterian.aicalendar.calendar.Attendee;
import io.meterian.aicalendar.calendar.CalendarService;
import java.util.List;

public class SetAppointmentTool extends AbstractTool {

    private final CalendarService service;

    public SetAppointmentTool(CalendarService service) {
        this.service = service;
    }

    @Override
    public String getName() {
        return "set-appointment";
    }

    @Override
    public String getDescription() {
        return "Create an appointment. title, date, startTime and leadTimeMinutes are required. "
                + "Ask the user for any missing value; never guess it. Returns the new appointment with its id.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder()
                .addString("title", "Short title, for example 'Dentist'.", true)
                .addString("date", "Date, YYYY-MM-DD. For a series, the first date.", true)
                .addString("startTime", "Start time, HH:mm, 24-hour.", true)
                .addInteger("leadTimeMinutes", "Minutes before startTime when the alert shows, 0 to 10080.", true)
                .addString("endTime", "Optional end time, HH:mm, 24-hour.", false)
                .addString("place", "Optional place.", false)
                .addProperty("repeat", SchemaBuilder.buildRepeatSchema(), false)
                .addProperty("attendees", SchemaBuilder.buildAttendeesSchema(), false)
                .build();
    }

    @Override
    protected String run(ToolArguments arguments) {
        Appointment appointment = new Appointment();
        appointment.title = arguments.readRequiredText("title");
        appointment.date = arguments.readRequiredDate("date");
        appointment.startTime = arguments.readRequiredTime("startTime");
        appointment.leadTimeMinutes = arguments.readRequiredInteger("leadTimeMinutes");
        appointment.endTime = arguments.readOptionalTime("endTime");
        appointment.place = arguments.readOptionalText("place");
        appointment.repeat = arguments.readOptionalRepeat("repeat");
        List<Attendee> attendees = arguments.readOptionalAttendees("attendees");
        if (attendees != null) {
            appointment.attendees = attendees;
        }
        return Json.writeJson(service.addAppointment(appointment));
    }
}
```

`src/main/java/io/meterian/aicalendar/tools/SetAlarmTool.java`:

```java
package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.Alarm;
import io.meterian.aicalendar.calendar.CalendarService;

public class SetAlarmTool extends AbstractTool {

    private final CalendarService service;

    public SetAlarmTool(CalendarService service) {
        this.service = service;
    }

    @Override
    public String getName() {
        return "set-alarm";
    }

    @Override
    public String getDescription() {
        return "Create an alarm: one alert with a short message. Use one of two forms. Fixed: date and time, "
                + "with an optional repeat. Linked: appointmentId and minutesBefore; it fires before each "
                + "occurrence of that appointment. Returns the new alarm with its id.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder()
                .addString("message", "Short message to show, for example 'Wake up'.", true)
                .addString("date", "Fixed form: date, YYYY-MM-DD.", false)
                .addString("time", "Fixed form: time, HH:mm, 24-hour.", false)
                .addProperty("repeat", SchemaBuilder.buildRepeatSchema(), false)
                .addString("appointmentId", "Linked form: the appointment id, for example 'A-3'.", false)
                .addInteger("minutesBefore", "Linked form: minutes before the appointment start, 0 to 10080.", false)
                .build();
    }

    @Override
    protected String run(ToolArguments arguments) {
        Alarm alarm = new Alarm();
        alarm.message = arguments.readRequiredText("message");
        alarm.date = arguments.readOptionalDate("date");
        alarm.time = arguments.readOptionalTime("time");
        alarm.repeat = arguments.readOptionalRepeat("repeat");
        alarm.appointmentId = arguments.readOptionalText("appointmentId");
        alarm.minutesBefore = arguments.readOptionalInteger("minutesBefore");
        return Json.writeJson(service.addAlarm(alarm));
    }
}
```

`src/main/java/io/meterian/aicalendar/tools/AddNoteTool.java`:

```java
package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.calendar.Note;

public class AddNoteTool extends AbstractTool {

    private final CalendarService service;

    public AddNoteTool(CalendarService service) {
        this.service = service;
    }

    @Override
    public String getName() {
        return "add-note";
    }

    @Override
    public String getDescription() {
        return "Add a free-text note to a day (date) or to an appointment (appointmentId). Give exactly one of "
                + "the two. Returns the new note with its id.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder()
                .addString("text", "The note text.", true)
                .addString("date", "The day, YYYY-MM-DD.", false)
                .addString("appointmentId", "The appointment id, for example 'A-3'.", false)
                .build();
    }

    @Override
    protected String run(ToolArguments arguments) {
        Note note = new Note();
        note.text = arguments.readRequiredText("text");
        note.date = arguments.readOptionalDate("date");
        note.appointmentId = arguments.readOptionalText("appointmentId");
        return Json.writeJson(service.addNote(note));
    }
}
```

- [ ] **Step 4: Write the find and list tools**

`src/main/java/io/meterian/aicalendar/tools/FindItemsTool.java`:

```java
package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.Alarm;
import io.meterian.aicalendar.calendar.Appointment;
import io.meterian.aicalendar.calendar.CalendarService;
import java.util.List;

public class FindItemsTool extends AbstractTool {

    private final CalendarService service;

    public FindItemsTool(CalendarService service) {
        this.service = service;
    }

    @Override
    public String getName() {
        return "find-items";
    }

    @Override
    public String getDescription() {
        return "Search appointments, alarms and notes. Use it to get the id before edit, remove or send-invite. "
                + "All parameters are optional. Returns the full items, each with a 'type' field.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder()
                .addString("query", "Text to search in titles, places, messages, notes and attendees.", false)
                .addEnum("type", "Only this kind of item.", List.of("appointment", "alarm", "note"), false)
                .addString("fromDate", "Only items that occur on or after this date, YYYY-MM-DD.", false)
                .addString("toDate", "Only items that occur on or before this date, YYYY-MM-DD.", false)
                .build();
    }

    @Override
    protected String run(ToolArguments arguments) {
        List<Object> items = service.findItems(
                arguments.readOptionalText("query"),
                arguments.readOptionalText("type"),
                arguments.readOptionalDate("fromDate"),
                arguments.readOptionalDate("toDate"));
        ArrayNode result = Json.MAPPER.createArrayNode();
        for (Object item : items) {
            ObjectNode node = Json.MAPPER.valueToTree(item);
            node.put("type", resolveItemType(item));
            result.add(node);
        }
        return Json.writeJson(result);
    }

    private static String resolveItemType(Object item) {
        if (item instanceof Appointment) {
            return "appointment";
        }
        if (item instanceof Alarm) {
            return "alarm";
        }
        return "note";
    }
}
```

`src/main/java/io/meterian/aicalendar/tools/ListDayTool.java`:

```java
package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.AlarmOccurrence;
import io.meterian.aicalendar.calendar.AppointmentOccurrence;
import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.calendar.DayAgenda;
import io.meterian.aicalendar.calendar.Note;
import java.time.LocalDate;

public class ListDayTool extends AbstractTool {

    private final CalendarService service;

    public ListDayTool(CalendarService service) {
        this.service = service;
    }

    @Override
    public String getName() {
        return "list-day";
    }

    @Override
    public String getDescription() {
        return "List everything on one day: appointments, alarms and notes, sorted by time. For a repeating "
                + "item, occurrenceDate is the date to pass to edit or remove for that one occurrence.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder().addString("date", "The day, YYYY-MM-DD.", true).build();
    }

    @Override
    protected String run(ToolArguments arguments) {
        LocalDate day = arguments.readRequiredDate("date");
        DayAgenda agenda = service.listDay(day);
        ObjectNode result = Json.MAPPER.createObjectNode();
        result.put("date", day.toString());
        result.put("dayOfWeek", day.getDayOfWeek().toString());
        addAppointments(result.putArray("appointments"), agenda);
        addAlarms(result.putArray("alarms"), agenda);
        addNotes(result.putArray("notes"), agenda);
        return Json.writeJson(result);
    }

    private static void addAppointments(ArrayNode appointments, DayAgenda agenda) {
        for (AppointmentOccurrence occurrence : agenda.appointments) {
            ObjectNode appointment = appointments.addObject();
            appointment.put("id", occurrence.appointment.id);
            appointment.put("title", occurrence.title);
            appointment.put("startTime", occurrence.startTime.toString());
            if (occurrence.endTime != null) {
                appointment.put("endTime", occurrence.endTime.toString());
            }
            if (occurrence.place != null) {
                appointment.put("place", occurrence.place);
            }
            appointment.put("leadTimeMinutes", occurrence.leadTimeMinutes);
            if (occurrence.appointment.repeat != null) {
                appointment.put("occurrenceDate", occurrence.originalDate.toString());
            }
            if (!occurrence.appointment.attendees.isEmpty()) {
                appointment.set("attendees", Json.MAPPER.valueToTree(occurrence.appointment.attendees));
            }
        }
    }

    private static void addAlarms(ArrayNode alarms, DayAgenda agenda) {
        for (AlarmOccurrence occurrence : agenda.alarms) {
            ObjectNode alarm = alarms.addObject();
            alarm.put("id", occurrence.alarm.id);
            alarm.put("time", occurrence.firesAt.toLocalTime().toString());
            alarm.put("message", occurrence.message);
            if (occurrence.alarm.isLinked()) {
                alarm.put("appointmentId", occurrence.alarm.appointmentId);
            }
            if (occurrence.alarm.repeat != null) {
                alarm.put("occurrenceDate", occurrence.originalDate.toString());
            }
        }
    }

    private static void addNotes(ArrayNode notes, DayAgenda agenda) {
        for (Note note : agenda.notes) {
            ObjectNode noteNode = notes.addObject();
            noteNode.put("id", note.id);
            noteNode.put("text", note.text);
            if (note.appointmentId != null) {
                noteNode.put("appointmentId", note.appointmentId);
            }
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -q test -Dtest=CalendarToolsTest`
Expected: PASS (8 tests).

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: add tools to create, find and list calendar items" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: Edit and remove tools

**Files:**
- Create: `src/main/java/io/meterian/aicalendar/tools/EditTool.java`, `src/main/java/io/meterian/aicalendar/tools/RemoveTool.java`
- Test: `src/test/java/io/meterian/aicalendar/tools/EditRemoveToolsTest.java`

**Interfaces:**
- Consumes: Task 5 `CalendarService.editItem`, `removeItem`, `ItemChanges`; Task 8 test helpers `CalendarToolsTest.buildService`, `CalendarToolsTest.parseArguments`.
- Produces: `EditTool(CalendarService)` named `edit`; `RemoveTool(CalendarService)` named `remove`, result `{"removed":[...]}`.

- [ ] **Step 1: Write the failing test**

`src/test/java/io/meterian/aicalendar/tools/EditRemoveToolsTest.java`:

```java
package io.meterian.aicalendar.tools;

import static io.meterian.aicalendar.tools.CalendarToolsTest.buildService;
import static io.meterian.aicalendar.tools.CalendarToolsTest.parseArguments;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.CalendarService;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditRemoveToolsTest {

    @TempDir
    Path tempDir;

    private CalendarService service;

    @BeforeEach
    void createWeeklyGymWithNote() throws Exception {
        service = buildService(tempDir.resolve("calendar.json"));
        new SetAppointmentTool(service).execute(parseArguments(
                "{'title':'Gym','date':'2026-09-28','startTime':'18:00','leadTimeMinutes':15,"
                        + "'repeat':{'frequency':'WEEKLY','daysOfWeek':['MONDAY']}}"));
        new AddNoteTool(service).execute(parseArguments("{'text':'Bring a towel','appointmentId':'A-1'}"));
    }

    private JsonNode listDay(String date) throws Exception {
        return Json.MAPPER.readTree(new ListDayTool(service).execute(parseArguments("{'date':'" + date + "'}")));
    }

    @Test
    void editWholeSeries() throws Exception {
        JsonNode updated = Json.MAPPER.readTree(new EditTool(service).execute(parseArguments(
                "{'id':'A-1','place':'FitLife','attendees':[{'name':'Anna Rossi','email':'anna@example.com'}]}")));

        assertEquals("FitLife", updated.get("place").asText());
        assertEquals("Anna Rossi", updated.get("attendees").get(0).get("name").asText());
    }

    @Test
    void editOneOccurrence() throws Exception {
        new EditTool(service).execute(parseArguments(
                "{'id':'A-1','occurrenceDate':'2026-10-05','startTime':'19:00'}"));

        assertEquals("19:00", listDay("2026-10-05").get("appointments").get(0).get("startTime").asText());
        assertEquals("18:00", listDay("2026-10-12").get("appointments").get(0).get("startTime").asText());
    }

    @Test
    void editErrorsComeBackAsText() throws Exception {
        EditTool tool = new EditTool(service);

        assertEquals("ERROR: No item has the id A-9. Use find-items to get the id.",
                tool.execute(parseArguments("{'id':'A-9','title':'X'}")));
        assertEquals("ERROR: id is missing. Ask the user for it.", tool.execute(parseArguments("{'title':'X'}")));
        assertEquals("ERROR: Give at least one field to change.", tool.execute(parseArguments("{'id':'A-1'}")));
    }

    @Test
    void removeOneOccurrence() throws Exception {
        JsonNode result = Json.MAPPER.readTree(new RemoveTool(service).execute(parseArguments(
                "{'id':'A-1','occurrenceDate':'2026-10-05'}")));

        assertEquals("A-1 on 2026-10-05", result.get("removed").get(0).asText());
        assertEquals(0, listDay("2026-10-05").get("appointments").size());
        assertEquals(1, listDay("2026-10-12").get("appointments").size());
    }

    @Test
    void removeWholeAppointmentListsLinkedItems() throws Exception {
        JsonNode result = Json.MAPPER.readTree(new RemoveTool(service).execute(parseArguments("{'id':'A-1'}")));

        assertEquals("A-1", result.get("removed").get(0).asText());
        assertEquals("N-1", result.get("removed").get(1).asText());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=EditRemoveToolsTest`
Expected: COMPILATION ERROR, `cannot find symbol: class EditTool`.

- [ ] **Step 3: Write `EditTool.java` and `RemoveTool.java`**

`src/main/java/io/meterian/aicalendar/tools/EditTool.java`:

```java
package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.calendar.ItemChanges;

public class EditTool extends AbstractTool {

    private final CalendarService service;

    public EditTool(CalendarService service) {
        this.service = service;
    }

    @Override
    public String getName() {
        return "edit";
    }

    @Override
    public String getDescription() {
        return "Change an appointment, alarm or note. Give the id and only the fields to change. Without "
                + "occurrenceDate, the whole item or series changes. With occurrenceDate, only that one occurrence "
                + "of a repeating item changes. Before you call this, show the item to the user and get a yes.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder()
                .addString("id", "The item id, for example 'A-3', 'L-1' or 'N-2'.", true)
                .addString("occurrenceDate", "Only for repeating items: the original date of the one occurrence "
                        + "to change, YYYY-MM-DD. Leave it out to change the whole item.", false)
                .addString("title", "Appointment: new title.", false)
                .addString("date", "New date, YYYY-MM-DD.", false)
                .addString("startTime", "Appointment: new start time, HH:mm.", false)
                .addString("endTime", "Appointment: new end time, HH:mm.", false)
                .addString("place", "Appointment: new place.", false)
                .addInteger("leadTimeMinutes", "Appointment: new alert lead time in minutes.", false)
                .addProperty("attendees", SchemaBuilder.buildAttendeesSchema(), false)
                .addProperty("repeat", SchemaBuilder.buildRepeatSchema(), false)
                .addString("message", "Alarm: new message.", false)
                .addString("time", "Fixed alarm: new time, HH:mm.", false)
                .addInteger("minutesBefore", "Linked alarm: new minutes before the appointment.", false)
                .addString("text", "Note: new text.", false)
                .build();
    }

    @Override
    protected String run(ToolArguments arguments) {
        ItemChanges changes = new ItemChanges();
        changes.title = arguments.readOptionalText("title");
        changes.date = arguments.readOptionalDate("date");
        changes.startTime = arguments.readOptionalTime("startTime");
        changes.endTime = arguments.readOptionalTime("endTime");
        changes.place = arguments.readOptionalText("place");
        changes.leadTimeMinutes = arguments.readOptionalInteger("leadTimeMinutes");
        changes.attendees = arguments.readOptionalAttendees("attendees");
        changes.repeat = arguments.readOptionalRepeat("repeat");
        changes.message = arguments.readOptionalText("message");
        changes.time = arguments.readOptionalTime("time");
        changes.minutesBefore = arguments.readOptionalInteger("minutesBefore");
        changes.text = arguments.readOptionalText("text");
        Object updated = service.editItem(
                arguments.readRequiredText("id"), arguments.readOptionalDate("occurrenceDate"), changes);
        return Json.writeJson(updated);
    }
}
```

`src/main/java/io/meterian/aicalendar/tools/RemoveTool.java`:

```java
package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.calendar.CalendarService;
import java.util.List;

public class RemoveTool extends AbstractTool {

    private final CalendarService service;

    public RemoveTool(CalendarService service) {
        this.service = service;
    }

    @Override
    public String getName() {
        return "remove";
    }

    @Override
    public String getDescription() {
        return "Remove an appointment, alarm or note. Without occurrenceDate, the whole item or series goes; "
                + "removing an appointment also removes its linked alarms and notes. With occurrenceDate, only "
                + "that one occurrence of a repeating item is cancelled. Before you call this, show the item to "
                + "the user and get a yes.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder()
                .addString("id", "The item id, for example 'A-3'.", true)
                .addString("occurrenceDate", "Only for repeating items: the original date of the one occurrence "
                        + "to cancel, YYYY-MM-DD.", false)
                .build();
    }

    @Override
    protected String run(ToolArguments arguments) {
        List<String> removed = service.removeItem(
                arguments.readRequiredText("id"), arguments.readOptionalDate("occurrenceDate"));
        ObjectNode result = Json.MAPPER.createObjectNode();
        result.set("removed", Json.MAPPER.valueToTree(removed));
        return Json.writeJson(result);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q test -Dtest=EditRemoveToolsTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat: add edit and remove tools" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 10: Agent loop with approval and round limit

**Files:**
- Create: `src/main/java/io/meterian/aicalendar/channel/UserChannel.java`
- Create: `src/main/java/io/meterian/aicalendar/agent/Agent.java`
- Create (test helper): `src/test/java/io/meterian/aicalendar/channel/RecordingUserChannel.java`
- Test: `src/test/java/io/meterian/aicalendar/agent/AgentTest.java`

**Interfaces:**
- Consumes: Task 6 `ChatModel`, `ChatMessage`, `ToolCall`, `ChatModelException`; Task 7 `Tool`, `ToolRegistry`, `SchemaBuilder`.
- Produces:
  - `interface UserChannel { String readLine(); void printReply(String text); void printAlert(String text); boolean askYesNo(String question); }` — `readLine` returns null at end of input.
  - `new Agent(ChatModel model, ToolRegistry tools, UserChannel channel, String systemPrompt)`
  - `String handleUserMessage(String text)` — returns the reply to show.
  - `List<ChatMessage> getConversation()` — unmodifiable view, for tests.
  - Constants: `MAX_TOOL_ROUNDS = 10`, `GIVE_UP_REPLY`, `EMPTY_REPLY`, `APPROVAL_QUESTION = "Approve this action?"`, `DECLINED_RESULT = "ERROR: the user declined."`.
  - Test helper `RecordingUserChannel` with public lists `replies`, `alerts`, `questions` and a queue `answers`.

- [ ] **Step 1: Write the test helper and the failing test**

`src/test/java/io/meterian/aicalendar/channel/RecordingUserChannel.java`:

```java
package io.meterian.aicalendar.channel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** A UserChannel for tests: records what is printed and answers questions from a queue (default: no). */
public class RecordingUserChannel implements UserChannel {

    public final List<String> replies = Collections.synchronizedList(new ArrayList<>());
    public final List<String> alerts = Collections.synchronizedList(new ArrayList<>());
    public final List<String> questions = Collections.synchronizedList(new ArrayList<>());
    public final Deque<Boolean> answers = new ArrayDeque<>();

    @Override
    public String readLine() {
        return null;
    }

    @Override
    public void printReply(String text) {
        replies.add(text);
    }

    @Override
    public void printAlert(String text) {
        alerts.add(text);
    }

    @Override
    public boolean askYesNo(String question) {
        questions.add(question);
        return !answers.isEmpty() && answers.removeFirst();
    }
}
```

`src/test/java/io/meterian/aicalendar/agent/AgentTest.java`:

```java
package io.meterian.aicalendar.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.channel.RecordingUserChannel;
import io.meterian.aicalendar.chat.ChatMessage;
import io.meterian.aicalendar.chat.ChatModel;
import io.meterian.aicalendar.chat.ChatModelException;
import io.meterian.aicalendar.chat.ToolCall;
import io.meterian.aicalendar.tools.SchemaBuilder;
import io.meterian.aicalendar.tools.Tool;
import io.meterian.aicalendar.tools.ToolRegistry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentTest {

    private static final String SYSTEM_PROMPT = "You are a test secretary.";

    private final RecordingUserChannel channel = new RecordingUserChannel();

    /** Returns scripted replies (or throws scripted failures) and records every request. */
    private static class ScriptedChatModel implements ChatModel {
        final Deque<Object> script = new ArrayDeque<>();
        final List<List<ChatMessage>> requests = new ArrayList<>();

        ScriptedChatModel queueReply(ChatMessage reply) {
            script.add(reply);
            return this;
        }

        ScriptedChatModel queueFailure(RuntimeException failure) {
            script.add(failure);
            return this;
        }

        @Override
        public ChatMessage requestReply(List<ChatMessage> messages, List<JsonNode> tools) {
            requests.add(new ArrayList<>(messages));
            Object next = script.removeFirst();
            if (next instanceof RuntimeException) {
                throw (RuntimeException) next;
            }
            return (ChatMessage) next;
        }
    }

    private static class EchoTool implements Tool {
        final boolean needsApproval;
        int executions;

        EchoTool(boolean needsApproval) {
            this.needsApproval = needsApproval;
        }

        @Override
        public String getName() {
            return "echo";
        }

        @Override
        public String getDescription() {
            return "Echo the text.";
        }

        @Override
        public ObjectNode buildParametersSchema() {
            return new SchemaBuilder().addString("text", "Text to echo.", false).build();
        }

        @Override
        public String execute(JsonNode arguments) {
            executions++;
            return "echo:" + arguments.path("text").asText();
        }

        @Override
        public boolean requiresApproval() {
            return needsApproval;
        }

        @Override
        public String describeCall(JsonNode arguments) {
            return "I will echo " + arguments.path("text").asText();
        }
    }

    private static class FailingTool implements Tool {
        @Override
        public String getName() {
            return "fail";
        }

        @Override
        public String getDescription() {
            return "Always fails.";
        }

        @Override
        public ObjectNode buildParametersSchema() {
            return new SchemaBuilder().build();
        }

        @Override
        public String execute(JsonNode arguments) {
            throw new IllegalStateException("boom");
        }
    }

    private static ChatMessage buildToolCallReply(String toolName, String singleQuotedArguments) throws Exception {
        JsonNode arguments = singleQuotedArguments == null
                ? null
                : Json.MAPPER.readTree(singleQuotedArguments.replace('\'', '"'));
        ChatMessage reply = ChatMessage.buildAssistantMessage("");
        reply.toolCalls.add(ToolCall.buildToolCall(toolName, arguments));
        return reply;
    }

    private Agent buildAgent(ChatModel model, Tool... tools) {
        return new Agent(model, new ToolRegistry(List.of(tools)), channel, SYSTEM_PROMPT);
    }

    @Test
    void returnsTextWhenModelAnswersDirectly() {
        ScriptedChatModel model = new ScriptedChatModel().queueReply(ChatMessage.buildAssistantMessage("Hello"));

        assertEquals("Hello", buildAgent(model, new EchoTool(false)).handleUserMessage("Hi"));

        List<ChatMessage> request = model.requests.get(0);
        assertEquals("system", request.get(0).role);
        assertEquals(SYSTEM_PROMPT, request.get(0).content);
        assertEquals("user", request.get(1).role);
        assertEquals("Hi", request.get(1).content);
    }

    @Test
    void runsToolCallAndSendsResultBack() throws Exception {
        ScriptedChatModel model = new ScriptedChatModel()
                .queueReply(buildToolCallReply("echo", "{'text':'hi'}"))
                .queueReply(ChatMessage.buildAssistantMessage("Done"));

        assertEquals("Done", buildAgent(model, new EchoTool(false)).handleUserMessage("Echo hi"));

        List<ChatMessage> second = model.requests.get(1);
        ChatMessage toolResult = second.get(second.size() - 1);
        assertEquals("tool", toolResult.role);
        assertEquals("echo", toolResult.toolName);
        assertEquals("echo:hi", toolResult.content);
        assertEquals("assistant", second.get(second.size() - 2).role);
    }

    @Test
    void unknownToolAndToolExceptionBecomeErrorResults() throws Exception {
        ScriptedChatModel model = new ScriptedChatModel()
                .queueReply(buildToolCallReply("nope", "{}"))
                .queueReply(buildToolCallReply("fail", "{}"))
                .queueReply(ChatMessage.buildAssistantMessage("Sorry"));

        buildAgent(model, new FailingTool()).handleUserMessage("Try");

        List<ChatMessage> second = model.requests.get(1);
        assertEquals("ERROR: unknown tool 'nope'.", second.get(second.size() - 1).content);
        List<ChatMessage> third = model.requests.get(2);
        assertEquals("ERROR: boom", third.get(third.size() - 1).content);
    }

    @Test
    void missingArgumentsAreTreatedAsEmptyObject() throws Exception {
        ScriptedChatModel model = new ScriptedChatModel()
                .queueReply(buildToolCallReply("echo", null))
                .queueReply(ChatMessage.buildAssistantMessage("Ok"));

        buildAgent(model, new EchoTool(false)).handleUserMessage("Echo");

        List<ChatMessage> second = model.requests.get(1);
        assertEquals("echo:", second.get(second.size() - 1).content);
    }

    @Test
    void stopsAfterTenRoundsOfToolCalls() throws Exception {
        ChatMessage loopingReply = buildToolCallReply("echo", "{'text':'again'}");
        List<Integer> calls = new ArrayList<>();
        ChatModel alwaysCallsTools = (messages, tools) -> {
            calls.add(1);
            return loopingReply;
        };

        String reply = buildAgent(alwaysCallsTools, new EchoTool(false)).handleUserMessage("Loop");

        assertEquals(Agent.GIVE_UP_REPLY, reply);
        assertEquals(Agent.MAX_TOOL_ROUNDS, calls.size());
    }

    @Test
    void approvedToolRunsAfterYes() throws Exception {
        EchoTool tool = new EchoTool(true);
        channel.answers.add(true);
        ScriptedChatModel model = new ScriptedChatModel()
                .queueReply(buildToolCallReply("echo", "{'text':'invite'}"))
                .queueReply(ChatMessage.buildAssistantMessage("Sent"));

        buildAgent(model, tool).handleUserMessage("Send it");

        assertEquals(List.of("I will echo invite"), channel.replies);
        assertEquals(List.of(Agent.APPROVAL_QUESTION), channel.questions);
        assertEquals(1, tool.executions);
    }

    @Test
    void declinedToolDoesNotRun() throws Exception {
        EchoTool tool = new EchoTool(true);
        channel.answers.add(false);
        ScriptedChatModel model = new ScriptedChatModel()
                .queueReply(buildToolCallReply("echo", "{'text':'invite'}"))
                .queueReply(ChatMessage.buildAssistantMessage("Not sent"));

        buildAgent(model, tool).handleUserMessage("Send it");

        assertEquals(0, tool.executions);
        List<ChatMessage> second = model.requests.get(1);
        assertEquals(Agent.DECLINED_RESULT, second.get(second.size() - 1).content);
    }

    @Test
    void modelFailureKeepsTheConversation() {
        ScriptedChatModel model = new ScriptedChatModel()
                .queueFailure(new ChatModelException("connection refused"))
                .queueReply(ChatMessage.buildAssistantMessage("Back"));
        Agent agent = buildAgent(model, new EchoTool(false));

        String first = agent.handleUserMessage("Hello?");
        String second = agent.handleUserMessage("Hello again");

        assertTrue(first.startsWith("I cannot reach the model: connection refused"), first);
        assertEquals("Back", second);
        List<ChatMessage> retry = model.requests.get(1);
        assertEquals("Hello?", retry.get(1).content);
        assertEquals("Hello again", retry.get(2).content);
    }

    @Test
    void emptyReplyGivesFallbackText() {
        ChatMessage thinkingOnly = ChatMessage.buildAssistantMessage("");
        thinkingOnly.thinking = "The user wants...";
        ScriptedChatModel model = new ScriptedChatModel().queueReply(thinkingOnly);

        assertEquals(Agent.EMPTY_REPLY, buildAgent(model, new EchoTool(false)).handleUserMessage("Hmm"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=AgentTest`
Expected: COMPILATION ERROR, `cannot find symbol: class UserChannel` (or `Agent`).

- [ ] **Step 3: Write `UserChannel.java`**

`src/main/java/io/meterian/aicalendar/channel/UserChannel.java`:

```java
package io.meterian.aicalendar.channel;

/** How the agent talks to the user. The terminal is one version; a UI can be another. */
public interface UserChannel {

    /** Reads the next request. Returns null at the end of input. */
    String readLine();

    void printReply(String text);

    /** May be called from the alert thread at any time. */
    void printAlert(String text);

    boolean askYesNo(String question);
}
```

- [ ] **Step 4: Write `Agent.java`**

`src/main/java/io/meterian/aicalendar/agent/Agent.java`:

```java
package io.meterian.aicalendar.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.channel.UserChannel;
import io.meterian.aicalendar.chat.ChatMessage;
import io.meterian.aicalendar.chat.ChatModel;
import io.meterian.aicalendar.chat.ChatModelException;
import io.meterian.aicalendar.chat.ToolCall;
import io.meterian.aicalendar.tools.Tool;
import io.meterian.aicalendar.tools.ToolRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The hand-written tool-call loop: send the conversation, run the tool calls in the reply, send the results back,
 * and repeat until the model answers with text.
 */
public class Agent {

    public static final int MAX_TOOL_ROUNDS = 10;
    public static final String GIVE_UP_REPLY = "I could not finish this request.";
    public static final String EMPTY_REPLY = "I have no answer. Please say it in a different way.";
    public static final String APPROVAL_QUESTION = "Approve this action?";
    public static final String DECLINED_RESULT = "ERROR: the user declined.";

    private final ChatModel model;
    private final ToolRegistry tools;
    private final UserChannel channel;
    private final String systemPrompt;
    private final List<ChatMessage> conversation = new ArrayList<>();

    public Agent(ChatModel model, ToolRegistry tools, UserChannel channel, String systemPrompt) {
        this.model = model;
        this.tools = tools;
        this.channel = channel;
        this.systemPrompt = systemPrompt;
    }

    public String handleUserMessage(String text) {
        conversation.add(ChatMessage.buildUserMessage(text));
        try {
            return runToolLoop();
        } catch (ChatModelException e) {
            return "I cannot reach the model: " + e.getMessage()
                    + " Please check that Ollama is running, then try again.";
        }
    }

    public List<ChatMessage> getConversation() {
        return Collections.unmodifiableList(conversation);
    }

    private String runToolLoop() {
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            ChatMessage reply = model.requestReply(buildRequestMessages(), tools.buildToolDefinitions());
            conversation.add(reply);
            if (!reply.hasToolCalls()) {
                return readReplyText(reply);
            }
            runToolCalls(reply.toolCalls);
        }
        return GIVE_UP_REPLY;
    }

    private List<ChatMessage> buildRequestMessages() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.buildSystemMessage(systemPrompt));
        messages.addAll(conversation);
        return messages;
    }

    private void runToolCalls(List<ToolCall> toolCalls) {
        for (ToolCall toolCall : toolCalls) {
            String result = runToolCall(toolCall);
            conversation.add(ChatMessage.buildToolResultMessage(toolCall.function.name, result));
        }
    }

    private String runToolCall(ToolCall toolCall) {
        Optional<Tool> tool = tools.findTool(toolCall.function.name);
        if (tool.isEmpty()) {
            return "ERROR: unknown tool '" + toolCall.function.name + "'.";
        }
        JsonNode arguments = readArguments(toolCall);
        try {
            if (tool.get().requiresApproval() && !askUserForApproval(tool.get(), arguments)) {
                return DECLINED_RESULT;
            }
            return tool.get().execute(arguments);
        } catch (RuntimeException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private boolean askUserForApproval(Tool tool, JsonNode arguments) {
        channel.printReply(tool.describeCall(arguments));
        return channel.askYesNo(APPROVAL_QUESTION);
    }

    /** A model can send a tool call without arguments. The tool then gets an empty object. */
    private static JsonNode readArguments(ToolCall toolCall) {
        JsonNode arguments = toolCall.function.arguments;
        return arguments == null || arguments.isNull() ? Json.MAPPER.createObjectNode() : arguments;
    }

    private static String readReplyText(ChatMessage reply) {
        return reply.content == null || reply.content.isBlank() ? EMPTY_REPLY : reply.content;
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -q test -Dtest=AgentTest`
Expected: PASS (9 tests).

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: add hand-written agent loop with approval and round limit" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 11: Alert scheduler

**Files:**
- Create: `src/main/java/io/meterian/aicalendar/alerts/Alert.java`, `src/main/java/io/meterian/aicalendar/alerts/AlertScheduler.java`
- Create (test helper): `src/test/java/io/meterian/aicalendar/MutableClock.java`
- Test: `src/test/java/io/meterian/aicalendar/alerts/AlertSchedulerTest.java`

**Interfaces:**
- Consumes: Task 4 `CalendarService.listAppointmentOccurrences`, `listAlarmOccurrences`; Task 10 `UserChannel`, `RecordingUserChannel`.
- Produces:
  - `Alert` — public final `key`, `firesAt`, `text`.
  - `new AlertScheduler(CalendarService service, UserChannel channel, Clock clock)` — last check starts at "now".
  - `synchronized void checkDueAlerts()` — prints alerts with a time after the last check and not after now, each key once.
  - `void start()` (every 30 seconds, daemon thread), `void stop()`.
  - Test helper `MutableClock(LocalDateTime start, ZoneId zone)` with `setTime(LocalDateTime)`.

- [ ] **Step 1: Write the test helper and the failing test**

`src/test/java/io/meterian/aicalendar/MutableClock.java`:

```java
package io.meterian.aicalendar;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** A clock that tests can move. */
public class MutableClock extends Clock {

    private final ZoneId zone;
    private volatile Instant instant;

    public MutableClock(LocalDateTime start, ZoneId zone) {
        this.zone = zone;
        setTime(start);
    }

    public void setTime(LocalDateTime time) {
        instant = time.atZone(zone).toInstant();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(LocalDateTime.ofInstant(instant, newZone), newZone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
```

`src/test/java/io/meterian/aicalendar/alerts/AlertSchedulerTest.java`:

```java
package io.meterian.aicalendar.alerts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.meterian.aicalendar.MutableClock;
import io.meterian.aicalendar.calendar.Alarm;
import io.meterian.aicalendar.calendar.Appointment;
import io.meterian.aicalendar.calendar.CalendarData;
import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.calendar.CalendarStore;
import io.meterian.aicalendar.calendar.Frequency;
import io.meterian.aicalendar.calendar.OccurrenceExpander;
import io.meterian.aicalendar.calendar.RepeatRule;
import io.meterian.aicalendar.channel.RecordingUserChannel;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AlertSchedulerTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);

    @TempDir
    Path tempDir;

    private MutableClock clock;
    private CalendarService service;
    private RecordingUserChannel channel;
    private AlertScheduler scheduler;

    @BeforeEach
    void createScheduler() {
        clock = new MutableClock(TODAY.atTime(10, 0), ROME);
        service = new CalendarService(new CalendarStore(tempDir.resolve("calendar.json")), new CalendarData(),
                new OccurrenceExpander(), clock);
        channel = new RecordingUserChannel();
        scheduler = new AlertScheduler(service, channel, clock);
    }

    private void checkAt(LocalDateTime time) {
        clock.setTime(time);
        scheduler.checkDueAlerts();
    }

    private Appointment addAppointment(String title, LocalDate date, LocalTime start, int leadMinutes) {
        Appointment appointment = new Appointment();
        appointment.title = title;
        appointment.date = date;
        appointment.startTime = start;
        appointment.leadTimeMinutes = leadMinutes;
        return service.addAppointment(appointment);
    }

    @Test
    void appointmentAlertPrintsOnceAtLeadTime() {
        Appointment dentist = new Appointment();
        dentist.title = "Dentist";
        dentist.date = TODAY;
        dentist.startTime = LocalTime.of(11, 0);
        dentist.leadTimeMinutes = 30;
        dentist.place = "Via Roma 10";
        service.addAppointment(dentist);

        checkAt(TODAY.atTime(10, 29));
        assertTrue(channel.alerts.isEmpty());

        checkAt(TODAY.atTime(10, 30));
        checkAt(TODAY.atTime(10, 31));
        assertEquals(List.of("⏰ 10:30 Dentist at 11:00 (Via Roma 10)"), channel.alerts);
    }

    @Test
    void fixedAndLinkedAlarmsPrint() {
        addAppointment("Meeting", TODAY, LocalTime.of(12, 0), 0);
        Alarm fixed = new Alarm();
        fixed.message = "Call Anna";
        fixed.date = TODAY;
        fixed.time = LocalTime.of(10, 5);
        service.addAlarm(fixed);
        Alarm linked = new Alarm();
        linked.message = "Print the slides";
        linked.appointmentId = "A-1";
        linked.minutesBefore = 60;
        service.addAlarm(linked);

        checkAt(TODAY.atTime(10, 5, 30));
        checkAt(TODAY.atTime(11, 0));

        assertEquals(List.of("⏰ 10:05 Call Anna", "⏰ 11:00 Print the slides"), channel.alerts);
    }

    @Test
    void cancelledOccurrenceDoesNotAlert() {
        Appointment daily = addAppointment("Standup", TODAY, LocalTime.of(10, 30), 0);
        daily.repeat = new RepeatRule(Frequency.DAILY, null, null);
        service.removeItem(daily.id, TODAY);

        checkAt(TODAY.atTime(10, 45));

        assertTrue(channel.alerts.isEmpty());
    }

    @Test
    void alertBeforeMidnightStillFires() {
        addAppointment("Night train", TODAY.plusDays(1), LocalTime.of(0, 15), 30);

        checkAt(TODAY.atTime(23, 45, 10));

        assertEquals(List.of("⏰ 23:45 Night train at 00:15"), channel.alerts);
    }

    @Test
    void alertsDueBeforeStartupAreNotShown() {
        addAppointment("Early call", TODAY, LocalTime.of(10, 0), 5);

        checkAt(TODAY.atTime(10, 1));

        assertTrue(channel.alerts.isEmpty());
    }
}
```

Note: `cancelledOccurrenceDoesNotAlert` sets `daily.repeat` on the stored object after `addAppointment`, then calls `removeItem` with an occurrence date. This works because `addAppointment` returns the stored object.

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=AlertSchedulerTest`
Expected: COMPILATION ERROR, `cannot find symbol: class AlertScheduler`.

- [ ] **Step 3: Write `Alert.java`**

`src/main/java/io/meterian/aicalendar/alerts/Alert.java`:

```java
package io.meterian.aicalendar.alerts;

import java.time.LocalDateTime;

public final class Alert {
    /** Item id, original date and fire time: the same alert always has the same key. */
    public final String key;
    public final LocalDateTime firesAt;
    public final String text;

    public Alert(String key, LocalDateTime firesAt, String text) {
        this.key = key;
        this.firesAt = firesAt;
        this.text = text;
    }
}
```

- [ ] **Step 4: Write `AlertScheduler.java`**

`src/main/java/io/meterian/aicalendar/alerts/AlertScheduler.java`:

```java
package io.meterian.aicalendar.alerts;

import io.meterian.aicalendar.calendar.AlarmOccurrence;
import io.meterian.aicalendar.calendar.AppointmentOccurrence;
import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.channel.UserChannel;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Every 30 seconds, prints the alerts that became due since the last check. */
public class AlertScheduler {

    public static final long CHECK_INTERVAL_SECONDS = 30;
    /** More than the 7-day lead-time limit, so no appointment alert is missed. */
    private static final int LOOKAHEAD_DAYS = 8;
    private static final String ALARM_CLOCK_SYMBOL = "⏰";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final CalendarService service;
    private final UserChannel channel;
    private final Clock clock;
    private final Set<String> printedAlertKeys = new HashSet<>();
    private LocalDateTime lastCheck;
    private ScheduledExecutorService executor;

    public AlertScheduler(CalendarService service, UserChannel channel, Clock clock) {
        this.service = service;
        this.channel = channel;
        this.clock = clock;
        this.lastCheck = LocalDateTime.now(clock);
    }

    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "alert-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(this::checkDueAlertsSafely,
                CHECK_INTERVAL_SECONDS, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public synchronized void checkDueAlerts() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (Alert alert : collectAlertsBetween(lastCheck, now)) {
            if (printedAlertKeys.add(alert.key)) {
                channel.printAlert(alert.text);
            }
        }
        lastCheck = now;
    }

    /** A scheduled task that throws is never run again, so every error is caught here. */
    private void checkDueAlertsSafely() {
        try {
            checkDueAlerts();
        } catch (RuntimeException e) {
            channel.printAlert("The alert check failed: " + e.getMessage());
        }
    }

    private List<Alert> collectAlertsBetween(LocalDateTime after, LocalDateTime upTo) {
        List<Alert> alerts = new ArrayList<>();
        alerts.addAll(collectAppointmentAlerts(after, upTo));
        alerts.addAll(collectAlarmAlerts(after, upTo));
        alerts.sort(Comparator.comparing(alert -> alert.firesAt));
        return alerts;
    }

    private List<Alert> collectAppointmentAlerts(LocalDateTime after, LocalDateTime upTo) {
        List<Alert> alerts = new ArrayList<>();
        List<AppointmentOccurrence> occurrences = service.listAppointmentOccurrences(
                after.toLocalDate(), upTo.toLocalDate().plusDays(LOOKAHEAD_DAYS));
        for (AppointmentOccurrence occurrence : occurrences) {
            LocalDateTime firesAt = occurrence.computeAlertTime();
            if (isInWindow(firesAt, after, upTo)) {
                String key = occurrence.appointment.id + "|" + occurrence.originalDate + "|" + firesAt;
                alerts.add(new Alert(key, firesAt, formatAppointmentAlert(occurrence, firesAt)));
            }
        }
        return alerts;
    }

    private List<Alert> collectAlarmAlerts(LocalDateTime after, LocalDateTime upTo) {
        List<Alert> alerts = new ArrayList<>();
        for (AlarmOccurrence occurrence : service.listAlarmOccurrences(after.toLocalDate(), upTo.toLocalDate())) {
            if (isInWindow(occurrence.firesAt, after, upTo)) {
                String key = occurrence.alarm.id + "|" + occurrence.originalDate + "|" + occurrence.firesAt;
                alerts.add(new Alert(key, occurrence.firesAt, formatAlarmAlert(occurrence)));
            }
        }
        return alerts;
    }

    private static boolean isInWindow(LocalDateTime time, LocalDateTime after, LocalDateTime upTo) {
        return time.isAfter(after) && !time.isAfter(upTo);
    }

    private static String formatAppointmentAlert(AppointmentOccurrence occurrence, LocalDateTime firesAt) {
        String text = ALARM_CLOCK_SYMBOL + " " + TIME_FORMAT.format(firesAt) + " " + occurrence.title
                + " at " + TIME_FORMAT.format(occurrence.startTime);
        return occurrence.place == null ? text : text + " (" + occurrence.place + ")";
    }

    private static String formatAlarmAlert(AlarmOccurrence occurrence) {
        return ALARM_CLOCK_SYMBOL + " " + TIME_FORMAT.format(occurrence.firesAt) + " " + occurrence.message;
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -q test -Dtest=AlertSchedulerTest`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: print due appointment and alarm alerts" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 12: Email invitations written to the outbox

There is no email service. Each email is written as a `.eml` file in the outbox folder; the file takes the place of an SMTP server. A mail program (Thunderbird, Outlook) can open the file and shows the `.ics` invitation.

**Files:**
- Create in `src/main/java/io/meterian/aicalendar/email/`: `EmailAddress.java`, `EmailAttachment.java`, `Email.java`, `EmailSender.java`, `EmailException.java`, `IcsBuilder.java`, `EmlFormatter.java`, `FileEmailSender.java`
- Create: `src/main/java/io/meterian/aicalendar/tools/GetUserProfileTool.java`, `src/main/java/io/meterian/aicalendar/tools/SendInviteTool.java`
- Test: `src/test/java/io/meterian/aicalendar/email/IcsBuilderTest.java`, `src/test/java/io/meterian/aicalendar/email/EmlFormatterTest.java`, `src/test/java/io/meterian/aicalendar/email/FileEmailSenderTest.java`, `src/test/java/io/meterian/aicalendar/tools/GetUserProfileToolTest.java`, `src/test/java/io/meterian/aicalendar/tools/SendInviteToolTest.java`

| Class | Its one job |
|---|---|
| `IcsBuilder` | Builds the `.ics` invitation text for one appointment and one attendee |
| `EmlFormatter` | Formats one email as `.eml` text: headers, text part, attachment part |
| `FileEmailSender` | Writes the `.eml` text to a free file name in the outbox folder |
| `SendInviteTool` | Builds one email per attendee and hands each one to the `EmailSender` |

**Interfaces:**
- Consumes: Task 1 `Settings`; Task 4 `CalendarService.findAppointment`; Task 7 `AbstractTool`, `ToolArguments`, `SchemaBuilder`; Task 8 test helpers `CalendarToolsTest.buildService`, `CalendarToolsTest.parseArguments`, `CalendarToolsTest.CLOCK`.
- Produces:
  - `EmailAddress(String name, String address)` with `String formatForDisplay()` (`Anna Rossi <anna@example.com>`).
  - `EmailAttachment(String fileName, String contentType, String content)`.
  - `Email(EmailAddress from, EmailAddress to, String subject, String body, EmailAttachment attachment)`.
  - `interface EmailSender { String send(Email email); }` — returns where the email went; throws `EmailException` (runtime).
  - `new IcsBuilder(ZoneId zone)`, `String buildInvite(Appointment, Attendee, String organizerName, String organizerEmail, Instant stamp)`.
  - `new EmlFormatter()`, `String formatEmail(Email email, ZonedDateTime sentAt)`.
  - `new FileEmailSender(Path outboxFolder, EmlFormatter formatter, Clock clock)` implements `EmailSender`; returns the file path.
  - `GetUserProfileTool(Settings)` named `get-user-profile`.
  - `SendInviteTool(CalendarService, Settings, EmailSender, IcsBuilder, Clock)` named `send-invite`, `requiresApproval() == true`; needs settings `profile.name`, `profile.surname`, `profile.email`.

- [ ] **Step 1: Write the failing tests**

`src/test/java/io/meterian/aicalendar/email/IcsBuilderTest.java`:

```java
package io.meterian.aicalendar.email;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.meterian.aicalendar.calendar.Appointment;
import io.meterian.aicalendar.calendar.Attendee;
import io.meterian.aicalendar.calendar.Frequency;
import io.meterian.aicalendar.calendar.OccurrenceChange;
import io.meterian.aicalendar.calendar.RepeatRule;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class IcsBuilderTest {

    private final IcsBuilder builder = new IcsBuilder(ZoneId.of("Europe/Rome"));
    private final Attendee anna = new Attendee("Anna Rossi", "anna@example.com");
    private final Instant stamp = Instant.parse("2026-09-24T08:00:00Z");

    private static Appointment buildDentist() {
        Appointment appointment = new Appointment();
        appointment.id = "A-3";
        appointment.title = "Dentist";
        appointment.date = LocalDate.of(2026, 9, 30);
        appointment.startTime = LocalTime.of(15, 0);
        appointment.endTime = LocalTime.of(15, 30);
        appointment.place = "Via Roma 10";
        appointment.leadTimeMinutes = 30;
        return appointment;
    }

    /** Joins folded lines again, so a test can check a whole logical line. */
    private static String unfoldLines(String ics) {
        return ics.replace("\r\n ", "");
    }

    @Test
    void containsTheRequiredFields() {
        String ics = unfoldLines(builder.buildInvite(buildDentist(), anna, "Marco Rossi", "me@example.com", stamp));

        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"));
        assertTrue(ics.contains("\r\nMETHOD:REQUEST\r\n"));
        assertTrue(ics.contains("\r\nUID:A-3@ai-calendar\r\n"));
        assertTrue(ics.contains("\r\nDTSTAMP:20260924T080000Z\r\n"));
        assertTrue(ics.contains("\r\nDTSTART;TZID=Europe/Rome:20260930T150000\r\n"));
        assertTrue(ics.contains("\r\nDTEND;TZID=Europe/Rome:20260930T153000\r\n"));
        assertTrue(ics.contains("\r\nSUMMARY:Dentist\r\n"));
        assertTrue(ics.contains("\r\nLOCATION:Via Roma 10\r\n"));
        assertTrue(ics.contains("\r\nORGANIZER;CN=\"Marco Rossi\":mailto:me@example.com\r\n"));
        assertTrue(ics.contains(
                "\r\nATTENDEE;CN=\"Anna Rossi\";ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE:mailto:anna@example.com\r\n"));
        assertTrue(ics.endsWith("\r\nEND:VCALENDAR\r\n"));
    }

    @Test
    void noEndTimeMeansNoDtend() {
        Appointment appointment = buildDentist();
        appointment.endTime = null;

        assertFalse(builder.buildInvite(appointment, anna, "Marco Rossi", "me@example.com", stamp).contains("DTEND"));
    }

    @Test
    void weeklyRepeatWithUntilAndCancelledDate() {
        Appointment appointment = buildDentist();
        appointment.date = LocalDate.of(2026, 9, 28);
        appointment.repeat = new RepeatRule(Frequency.WEEKLY,
                List.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), LocalDate.of(2026, 12, 31));
        OccurrenceChange cancelled = new OccurrenceChange();
        cancelled.cancelled = true;
        appointment.overrides.put(LocalDate.of(2026, 10, 5), cancelled);

        String ics = builder.buildInvite(appointment, anna, "Marco Rossi", "me@example.com", stamp);

        assertTrue(ics.contains("\r\nRRULE:FREQ=WEEKLY;BYDAY=MO,TH;UNTIL=20261231T225959Z\r\n"));
        assertTrue(ics.contains("\r\nEXDATE;TZID=Europe/Rome:20261005T150000\r\n"));
    }

    @Test
    void escapesTextAndFoldsLongLines() {
        Appointment appointment = buildDentist();
        appointment.title = "Lunch, drinks; talk";
        appointment.place = "A very long place name that goes on and on, far past the seventy-five character limit";

        String ics = builder.buildInvite(appointment, anna, "Marco Rossi", "me@example.com", stamp);

        assertTrue(ics.contains("\r\nSUMMARY:Lunch\\, drinks\\; talk\r\n"));
        for (String line : ics.split("\r\n")) {
            assertTrue(line.length() <= 75, "Line too long: " + line);
        }
        assertTrue(ics.contains("\r\n "));
    }
}
```

`src/test/java/io/meterian/aicalendar/email/EmlFormatterTest.java`:

```java
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
```

`src/test/java/io/meterian/aicalendar/email/FileEmailSenderTest.java`:

```java
package io.meterian.aicalendar.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileEmailSenderTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    private final Clock clock = Clock.fixed(LocalDateTime.of(2026, 9, 24, 10, 0).atZone(ROME).toInstant(), ROME);

    @TempDir
    Path tempDir;

    private static Email buildEmailToAnna() {
        return new Email(
                new EmailAddress("Marco Rossi", "me@example.com"),
                new EmailAddress("Anna Rossi", "anna@example.com"),
                "Our meeting",
                "Dear Anna,\nSee you soon.",
                new EmailAttachment("invite.ics", "text/calendar; charset=UTF-8; method=REQUEST",
                        "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n"));
    }

    @Test
    void writesTheEmailAsAnEmlFile() throws Exception {
        Path outbox = tempDir.resolve("outbox");
        FileEmailSender sender = new FileEmailSender(outbox, new EmlFormatter(), clock);

        String destination = sender.send(buildEmailToAnna());

        Path file = outbox.resolve("20260924-100000-anna_example.com.eml");
        assertEquals(file.toString(), destination);
        assertTrue(Files.readString(file).contains("To: \"Anna Rossi\" <anna@example.com>"));
    }

    @Test
    void takenFileNameGetsANumber() {
        FileEmailSender sender = new FileEmailSender(tempDir.resolve("outbox"), new EmlFormatter(), clock);

        sender.send(buildEmailToAnna());
        String secondDestination = sender.send(buildEmailToAnna());

        assertTrue(secondDestination.endsWith("20260924-100000-anna_example.com-2.eml"), secondDestination);
    }

    @Test
    void unwritableOutboxThrowsEmailException() throws Exception {
        Path notAFolder = tempDir.resolve("afile");
        Files.writeString(notAFolder, "x");
        FileEmailSender sender = new FileEmailSender(notAFolder, new EmlFormatter(), clock);

        EmailException error = assertThrows(EmailException.class, () -> sender.send(buildEmailToAnna()));

        assertTrue(error.getMessage().startsWith("Could not write the email for anna@example.com"), error.getMessage());
    }
}
```

`src/test/java/io/meterian/aicalendar/tools/GetUserProfileToolTest.java`:

```java
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
```

`src/test/java/io/meterian/aicalendar/tools/SendInviteToolTest.java`:

```java
package io.meterian.aicalendar.tools;

import static io.meterian.aicalendar.tools.CalendarToolsTest.CLOCK;
import static io.meterian.aicalendar.tools.CalendarToolsTest.buildService;
import static io.meterian.aicalendar.tools.CalendarToolsTest.parseArguments;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.meterian.aicalendar.Settings;
import io.meterian.aicalendar.calendar.CalendarException;
import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.email.Email;
import io.meterian.aicalendar.email.EmailException;
import io.meterian.aicalendar.email.EmailSender;
import io.meterian.aicalendar.email.IcsBuilder;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SendInviteToolTest {

    private static final String INVITE_ARGUMENTS =
            "{'appointmentId':'A-1','subject':'Business meeting','body':'Dear guest, I am thrilled to meet.'}";

    /** Records the emails instead of writing files; fails for the addresses in failFor. */
    private static class RecordingEmailSender implements EmailSender {
        final List<Email> sentEmails = new ArrayList<>();
        final Set<String> failFor = new HashSet<>();

        @Override
        public String send(Email email) {
            if (failFor.contains(email.to.address)) {
                throw new EmailException("Could not write the email for " + email.to.address + ": disk full", null);
            }
            sentEmails.add(email);
            return "outbox/" + email.to.address + ".eml";
        }
    }

    @TempDir
    Path tempDir;

    private CalendarService service;
    private final RecordingEmailSender sender = new RecordingEmailSender();

    @BeforeEach
    void createMeetingWithTwoAttendees() throws Exception {
        service = buildService(tempDir.resolve("calendar.json"));
        new SetAppointmentTool(service).execute(parseArguments(
                "{'title':'Business meeting','date':'2026-09-30','startTime':'10:00','leadTimeMinutes':30,"
                        + "'place':'Our office','attendees':[{'name':'Anna Rossi','email':'anna@example.com'},"
                        + "{'name':'Luca Bianchi','email':'luca@example.com'}]}"));
    }

    private static Settings buildCompleteSettings() {
        Properties values = new Properties();
        values.setProperty("profile.name", "Marco");
        values.setProperty("profile.surname", "Rossi");
        values.setProperty("profile.email", "me@example.com");
        return new Settings(values, new Properties());
    }

    private SendInviteTool buildTool(Settings settings) {
        return new SendInviteTool(service, settings, sender, new IcsBuilder(ZoneId.of("Europe/Rome")), CLOCK);
    }

    @Test
    void needsApproval() {
        assertTrue(buildTool(buildCompleteSettings()).requiresApproval());
    }

    @Test
    void describeCallShowsEveryEmail() throws Exception {
        String preview = buildTool(buildCompleteSettings()).describeCall(parseArguments(INVITE_ARGUMENTS));

        assertTrue(preview.contains("From: Marco Rossi <me@example.com>"));
        assertTrue(preview.contains("To: Anna Rossi <anna@example.com>"));
        assertTrue(preview.contains("To: Luca Bianchi <luca@example.com>"));
        assertTrue(preview.contains("Subject: Business meeting"));
        assertTrue(preview.contains("Dear guest, I am thrilled to meet."));
        assertTrue(sender.sentEmails.isEmpty());
    }

    @Test
    void sendsOneEmailPerAttendeeWithInvite() throws Exception {
        String result = buildTool(buildCompleteSettings()).execute(parseArguments(INVITE_ARGUMENTS));

        assertEquals("Sent to Anna Rossi <anna@example.com> (outbox/anna@example.com.eml).\n"
                + "Sent to Luca Bianchi <luca@example.com> (outbox/luca@example.com.eml).", result);
        assertEquals(2, sender.sentEmails.size());
        Email first = sender.sentEmails.get(0);
        assertEquals("me@example.com", first.from.address);
        assertEquals("invite.ics", first.attachment.fileName);
        assertTrue(first.attachment.content.contains("METHOD:REQUEST"));
        assertTrue(first.attachment.content.contains("ATTENDEE;CN=\"Anna Rossi\""));
        assertTrue(first.attachment.content.contains("ORGANIZER;CN=\"Marco Rossi\":mailto:me@example.com"));
    }

    @Test
    void failureForOneAttendeeStillSendsTheOthers() throws Exception {
        sender.failFor.add("anna@example.com");

        String result = buildTool(buildCompleteSettings()).execute(parseArguments(INVITE_ARGUMENTS));

        assertTrue(result.startsWith("FAILED for Anna Rossi <anna@example.com>: Could not write"), result);
        assertTrue(result.endsWith("Sent to Luca Bianchi <luca@example.com> (outbox/luca@example.com.eml)."), result);
        assertEquals(1, sender.sentEmails.size());
    }

    @Test
    void missingSettingsAreNamed() throws Exception {
        Settings emptySettings = new Settings(new Properties(), new Properties());

        String result = buildTool(emptySettings).execute(parseArguments(INVITE_ARGUMENTS));

        assertEquals("ERROR: Missing settings: profile.name, profile.surname, profile.email. "
                + "Add them to settings.properties.", result);
    }

    @Test
    void appointmentWithoutAttendeesIsRejected() throws Exception {
        new SetAppointmentTool(service).execute(parseArguments(
                "{'title':'Alone','date':'2026-09-30','startTime':'12:00','leadTimeMinutes':30}"));
        SendInviteTool tool = buildTool(buildCompleteSettings());

        assertEquals("ERROR: A-2 has no attendees. Add them with edit first.", tool.execute(parseArguments(
                "{'appointmentId':'A-2','subject':'Hi','body':'Hello'}")));
        assertThrows(CalendarException.class, () -> tool.describeCall(parseArguments(
                "{'appointmentId':'A-2','subject':'Hi','body':'Hello'}")));
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q test -Dtest='IcsBuilderTest,EmlFormatterTest,FileEmailSenderTest,GetUserProfileToolTest,SendInviteToolTest'`
Expected: COMPILATION ERROR, `cannot find symbol: class IcsBuilder`.

- [ ] **Step 3: Write the email types**

`src/main/java/io/meterian/aicalendar/email/EmailAddress.java`:

```java
package io.meterian.aicalendar.email;

/** A person's name and email address. */
public final class EmailAddress {
    public final String name;
    public final String address;

    public EmailAddress(String name, String address) {
        this.name = name;
        this.address = address;
    }

    /** For example: Anna Rossi <anna@example.com> */
    public String formatForDisplay() {
        return name + " <" + address + ">";
    }
}
```

`src/main/java/io/meterian/aicalendar/email/EmailAttachment.java`:

```java
package io.meterian.aicalendar.email;

/** One file attached to an email, as text. */
public final class EmailAttachment {
    public final String fileName;
    public final String contentType;
    public final String content;

    public EmailAttachment(String fileName, String contentType, String content) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.content = content;
    }
}
```

`src/main/java/io/meterian/aicalendar/email/Email.java`:

```java
package io.meterian.aicalendar.email;

/** One email with one attachment. */
public final class Email {
    public final EmailAddress from;
    public final EmailAddress to;
    public final String subject;
    public final String body;
    public final EmailAttachment attachment;

    public Email(EmailAddress from, EmailAddress to, String subject, String body, EmailAttachment attachment) {
        this.from = from;
        this.to = to;
        this.subject = subject;
        this.body = body;
        this.attachment = attachment;
    }
}
```

`src/main/java/io/meterian/aicalendar/email/EmailSender.java`:

```java
package io.meterian.aicalendar.email;

/** Delivers emails. This project writes them to files; a real SMTP sender could replace it later. */
public interface EmailSender {

    /** Delivers one email and returns where it went, for example a file path. Throws EmailException on failure. */
    String send(Email email);
}
```

`src/main/java/io/meterian/aicalendar/email/EmailException.java`:

```java
package io.meterian.aicalendar.email;

public class EmailException extends RuntimeException {

    public EmailException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 4: Write `IcsBuilder.java`**

`src/main/java/io/meterian/aicalendar/email/IcsBuilder.java`:

```java
package io.meterian.aicalendar.email;

import io.meterian.aicalendar.calendar.Appointment;
import io.meterian.aicalendar.calendar.Attendee;
import io.meterian.aicalendar.calendar.Frequency;
import io.meterian.aicalendar.calendar.OccurrenceChange;
import io.meterian.aicalendar.calendar.RepeatRule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Builds an iCalendar (RFC 5545) invitation for one appointment and one attendee. */
public class IcsBuilder {

    private static final DateTimeFormatter LOCAL_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter UTC_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final int MAX_LINE_LENGTH = 75;
    private static final String LINE_BREAK = "\r\n";

    private final ZoneId zone;

    public IcsBuilder(ZoneId zone) {
        this.zone = zone;
    }

    public String buildInvite(Appointment appointment, Attendee attendee, String organizerName,
            String organizerEmail, Instant stamp) {
        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:-//ai-calendar//EN");
        lines.add("METHOD:REQUEST");
        lines.add("BEGIN:VEVENT");
        lines.add("UID:" + appointment.id + "@ai-calendar");
        lines.add("DTSTAMP:" + UTC_TIME_FORMAT.format(stamp));
        addTimeLines(lines, appointment);
        addDescriptionLines(lines, appointment);
        addPeopleLines(lines, attendee, organizerName, organizerEmail);
        addRepeatLines(lines, appointment);
        lines.add("END:VEVENT");
        lines.add("END:VCALENDAR");
        return joinFoldedLines(lines);
    }

    private void addTimeLines(List<String> lines, Appointment appointment) {
        lines.add("DTSTART" + buildLocalTimeValue(appointment.date, appointment.startTime));
        if (appointment.endTime != null) {
            lines.add("DTEND" + buildLocalTimeValue(appointment.date, appointment.endTime));
        }
    }

    private static void addDescriptionLines(List<String> lines, Appointment appointment) {
        lines.add("SUMMARY:" + escapeText(appointment.title));
        if (appointment.place != null) {
            lines.add("LOCATION:" + escapeText(appointment.place));
        }
    }

    private static void addPeopleLines(List<String> lines, Attendee attendee, String organizerName,
            String organizerEmail) {
        lines.add("ORGANIZER;CN=" + quoteParameter(organizerName) + ":mailto:" + organizerEmail);
        lines.add("ATTENDEE;CN=" + quoteParameter(attendee.name)
                + ";ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE:mailto:" + attendee.email);
    }

    private void addRepeatLines(List<String> lines, Appointment appointment) {
        if (appointment.repeat == null) {
            return;
        }
        lines.add(buildRepeatRuleLine(appointment.repeat));
        for (Map.Entry<LocalDate, OccurrenceChange> override : appointment.overrides.entrySet()) {
            if (Boolean.TRUE.equals(override.getValue().cancelled)) {
                lines.add("EXDATE" + buildLocalTimeValue(override.getKey(), appointment.startTime));
            }
        }
    }

    private String buildRepeatRuleLine(RepeatRule rule) {
        StringBuilder line = new StringBuilder("RRULE:FREQ=").append(rule.frequency.name());
        if (rule.frequency == Frequency.WEEKLY) {
            line.append(";BYDAY=").append(rule.daysOfWeek.stream()
                    .map(day -> day.name().substring(0, 2))
                    .collect(Collectors.joining(",")));
        }
        if (rule.until != null) {
            // With a TZID start time, UNTIL must be in UTC. Use the end of the last day.
            Instant endOfLastDay = rule.until.atTime(23, 59, 59).atZone(zone).toInstant();
            line.append(";UNTIL=").append(UTC_TIME_FORMAT.format(endOfLastDay));
        }
        return line.toString();
    }

    /** For example ";TZID=Europe/Rome:20260930T150000". */
    private String buildLocalTimeValue(LocalDate date, LocalTime time) {
        return ";TZID=" + zone.getId() + ":" + LOCAL_TIME_FORMAT.format(date.atTime(time));
    }

    private static String joinFoldedLines(List<String> lines) {
        return lines.stream().map(IcsBuilder::foldLine).collect(Collectors.joining(LINE_BREAK, "", LINE_BREAK));
    }

    static String escapeText(String text) {
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }

    static String quoteParameter(String value) {
        return "\"" + value.replace("\"", "'") + "\"";
    }

    /** A line longer than 75 characters continues on the next line, which starts with one space. */
    static String foldLine(String line) {
        if (line.length() <= MAX_LINE_LENGTH) {
            return line;
        }
        int continuationLength = MAX_LINE_LENGTH - 1;
        StringBuilder folded = new StringBuilder(line.substring(0, MAX_LINE_LENGTH));
        for (int start = MAX_LINE_LENGTH; start < line.length(); start += continuationLength) {
            int end = Math.min(start + continuationLength, line.length());
            folded.append(LINE_BREAK).append(' ').append(line, start, end);
        }
        return folded.toString();
    }
}
```

- [ ] **Step 5: Write `EmlFormatter.java` and `FileEmailSender.java`**

`src/main/java/io/meterian/aicalendar/email/EmlFormatter.java`:

```java
package io.meterian.aicalendar.email;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Formats one email as standard .eml text (MIME): the headers, the text part and the attachment part.
 * Mail programs such as Thunderbird or Outlook can open the result.
 */
public class EmlFormatter {

    private static final String LINE_BREAK = "\r\n";
    private static final String PART_BOUNDARY = "ai-calendar-part-boundary";

    public String formatEmail(Email email, ZonedDateTime sentAt) {
        StringBuilder eml = new StringBuilder();
        appendHeaders(eml, email, sentAt);
        appendTextPart(eml, email.body);
        appendAttachmentPart(eml, email.attachment);
        appendLine(eml, "--" + PART_BOUNDARY + "--");
        return eml.toString();
    }

    private static void appendHeaders(StringBuilder eml, Email email, ZonedDateTime sentAt) {
        appendLine(eml, "From: " + formatHeaderAddress(email.from));
        appendLine(eml, "To: " + formatHeaderAddress(email.to));
        appendLine(eml, "Subject: " + email.subject);
        appendLine(eml, "Date: " + DateTimeFormatter.RFC_1123_DATE_TIME.format(sentAt));
        appendLine(eml, "MIME-Version: 1.0");
        appendLine(eml, "Content-Type: multipart/mixed; boundary=\"" + PART_BOUNDARY + "\"");
        appendLine(eml, "");
    }

    private static void appendTextPart(StringBuilder eml, String body) {
        appendLine(eml, "--" + PART_BOUNDARY);
        appendLine(eml, "Content-Type: text/plain; charset=UTF-8");
        appendLine(eml, "Content-Transfer-Encoding: 8bit");
        appendLine(eml, "");
        appendLine(eml, convertToEmailLineBreaks(body));
    }

    private static void appendAttachmentPart(StringBuilder eml, EmailAttachment attachment) {
        appendLine(eml, "--" + PART_BOUNDARY);
        appendLine(eml, "Content-Type: " + attachment.contentType);
        appendLine(eml, "Content-Disposition: attachment; filename=\"" + attachment.fileName + "\"");
        appendLine(eml, "Content-Transfer-Encoding: 8bit");
        appendLine(eml, "");
        String content = convertToEmailLineBreaks(attachment.content);
        eml.append(content.endsWith(LINE_BREAK) ? content : content + LINE_BREAK);
    }

    private static String formatHeaderAddress(EmailAddress emailAddress) {
        return "\"" + emailAddress.name + "\" <" + emailAddress.address + ">";
    }

    /** Email text uses CR LF line breaks. */
    private static String convertToEmailLineBreaks(String text) {
        return text.replace("\r\n", "\n").replace("\n", LINE_BREAK);
    }

    private static void appendLine(StringBuilder eml, String line) {
        eml.append(line).append(LINE_BREAK);
    }
}
```

`src/main/java/io/meterian/aicalendar/email/FileEmailSender.java`:

```java
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
```

- [ ] **Step 6: Write the two tools**

`src/main/java/io/meterian/aicalendar/tools/GetUserProfileTool.java`:

```java
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
```

`src/main/java/io/meterian/aicalendar/tools/SendInviteTool.java`:

```java
package io.meterian.aicalendar.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Settings;
import io.meterian.aicalendar.calendar.Appointment;
import io.meterian.aicalendar.calendar.Attendee;
import io.meterian.aicalendar.calendar.CalendarException;
import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.email.Email;
import io.meterian.aicalendar.email.EmailAddress;
import io.meterian.aicalendar.email.EmailAttachment;
import io.meterian.aicalendar.email.EmailException;
import io.meterian.aicalendar.email.EmailSender;
import io.meterian.aicalendar.email.IcsBuilder;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/** Builds one invitation email per attendee of an appointment and hands each one to the EmailSender. */
public class SendInviteTool extends AbstractTool {

    static final List<String> REQUIRED_SETTINGS = List.of("profile.name", "profile.surname", "profile.email");
    private static final String INVITE_FILE_NAME = "invite.ics";
    private static final String INVITE_CONTENT_TYPE = "text/calendar; charset=UTF-8; method=REQUEST";

    private final CalendarService service;
    private final Settings settings;
    private final EmailSender sender;
    private final IcsBuilder icsBuilder;
    private final Clock clock;

    public SendInviteTool(CalendarService service, Settings settings, EmailSender sender, IcsBuilder icsBuilder,
            Clock clock) {
        this.service = service;
        this.settings = settings;
        this.sender = sender;
        this.icsBuilder = icsBuilder;
        this.clock = clock;
    }

    @Override
    public String getName() {
        return "send-invite";
    }

    @Override
    public String getDescription() {
        return "Email an invitation for an appointment to all its attendees, with an .ics calendar file attached. "
                + "Write the subject and the body yourself, including the greeting and the signature; call "
                + "get-user-profile for the signature. Use it only when the user asks. The user must approve "
                + "before the emails go out. In this project each email is saved as a file in the outbox folder.";
    }

    @Override
    public ObjectNode buildParametersSchema() {
        return new SchemaBuilder()
                .addString("appointmentId", "The appointment id, for example 'A-3'. It must have attendees.", true)
                .addString("subject", "The email subject.", true)
                .addString("body", "The full email text, with greeting and signature.", true)
                .build();
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    @Override
    public String describeCall(JsonNode arguments) {
        StringBuilder preview = new StringBuilder();
        for (Email email : buildEmails(new ToolArguments(arguments))) {
            preview.append("From: ").append(email.from.formatForDisplay()).append("\n")
                    .append("To: ").append(email.to.formatForDisplay()).append("\n")
                    .append("Subject: ").append(email.subject).append("\n\n")
                    .append(email.body).append("\n\n")
                    .append("Attachment: ").append(email.attachment.fileName).append("\n")
                    .append("----------\n");
        }
        return preview.toString();
    }

    @Override
    protected String run(ToolArguments arguments) {
        List<String> resultLines = new ArrayList<>();
        for (Email email : buildEmails(arguments)) {
            String recipient = email.to.formatForDisplay();
            try {
                String destination = sender.send(email);
                resultLines.add("Sent to " + recipient + " (" + destination + ").");
            } catch (EmailException e) {
                resultLines.add("FAILED for " + recipient + ": " + e.getMessage());
            }
        }
        return String.join("\n", resultLines);
    }

    private List<Email> buildEmails(ToolArguments arguments) {
        requireProfileSettings();
        Appointment appointment = service.findAppointment(arguments.readRequiredText("appointmentId"));
        String subject = arguments.readRequiredText("subject");
        String body = arguments.readRequiredText("body");
        if (appointment.attendees.isEmpty()) {
            throw new CalendarException(appointment.id + " has no attendees. Add them with edit first.");
        }
        EmailAddress organizer = buildOrganizerAddress();
        List<Email> emails = new ArrayList<>();
        for (Attendee attendee : appointment.attendees) {
            String invite = icsBuilder.buildInvite(
                    appointment, attendee, organizer.name, organizer.address, clock.instant());
            emails.add(new Email(organizer, new EmailAddress(attendee.name, attendee.email), subject, body,
                    new EmailAttachment(INVITE_FILE_NAME, INVITE_CONTENT_TYPE, invite)));
        }
        return emails;
    }

    private void requireProfileSettings() {
        List<String> missingKeys = settings.findMissingKeys(REQUIRED_SETTINGS);
        if (!missingKeys.isEmpty()) {
            throw new ToolArgumentException(
                    "Missing settings: " + String.join(", ", missingKeys) + ". Add them to settings.properties.");
        }
    }

    private EmailAddress buildOrganizerAddress() {
        String fullName = settings.readValue("profile.name", "") + " " + settings.readValue("profile.surname", "");
        return new EmailAddress(fullName, settings.readValue("profile.email", ""));
    }
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `mvn -q test -Dtest='IcsBuilderTest,EmlFormatterTest,FileEmailSenderTest,GetUserProfileToolTest,SendInviteToolTest'`
Expected: PASS (16 tests).

- [ ] **Step 8: Commit**

```bash
git add src
git commit -m "feat: write .ics email invitations to the outbox folder" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 13: Terminal, system prompt, main program and docs

**Files:**
- Create: `src/main/java/io/meterian/aicalendar/channel/ConsoleChannel.java`
- Create: `src/main/java/io/meterian/aicalendar/agent/SystemPrompt.java`
- Create: `src/main/java/io/meterian/aicalendar/Main.java`
- Create: `README.md`
- Modify: `CLAUDE.md` (replace the `## Status` section, extend `## Build`)
- Test: `src/test/java/io/meterian/aicalendar/channel/ConsoleChannelTest.java`

**Interfaces:**
- Consumes: every earlier task.
- Produces:
  - `new ConsoleChannel(BufferedReader in, PrintStream out)` implements `UserChannel`; prompt `"> "`; `askYesNo` appends `" (y/n) "` and accepts only `y` (any case).
  - `SystemPrompt.TEXT`
  - `Main.main(String[])`

- [ ] **Step 1: Write the failing test**

`src/test/java/io/meterian/aicalendar/channel/ConsoleChannelTest.java`:

```java
package io.meterian.aicalendar.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ConsoleChannelTest {

    private static final String LINE_BREAK = System.lineSeparator();

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    private ConsoleChannel buildChannel(Reader input) {
        return new ConsoleChannel(new BufferedReader(input), new PrintStream(output, true, StandardCharsets.UTF_8));
    }

    private String readOutput() {
        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    void readLinePrintsPromptAndReturnsInput() {
        ConsoleChannel channel = buildChannel(new StringReader("hello\n"));

        assertEquals("hello", channel.readLine());
        assertNull(channel.readLine());
        assertTrue(readOutput().startsWith("> "));
    }

    @Test
    void alertWhileWaitingForInputPrintsThePromptAgain() {
        AtomicReference<ConsoleChannel> channelReference = new AtomicReference<>();
        Reader input = new StringReader("hello\n") {
            private boolean alerted;

            @Override
            public int read(char[] buffer, int offset, int length) throws IOException {
                if (!alerted) {
                    alerted = true;
                    channelReference.get().printAlert("ALERT");
                }
                return super.read(buffer, offset, length);
            }
        };
        ConsoleChannel channel = buildChannel(input);
        channelReference.set(channel);

        assertEquals("hello", channel.readLine());
        assertEquals("> " + LINE_BREAK + "ALERT" + LINE_BREAK + "> ", readOutput());
    }

    @Test
    void alertWhileNotWaitingPrintsOnlyTheAlert() {
        buildChannel(new StringReader("")).printAlert("ALERT");

        assertEquals("ALERT" + LINE_BREAK, readOutput());
    }

    @Test
    void askYesNoAcceptsOnlyY() {
        assertTrue(buildChannel(new StringReader("Y\n")).askYesNo("Approve this action?"));
        assertFalse(buildChannel(new StringReader("no\n")).askYesNo("Approve this action?"));
        assertFalse(buildChannel(new StringReader("")).askYesNo("Approve this action?"));
        assertTrue(readOutput().contains("Approve this action? (y/n) "));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=ConsoleChannelTest`
Expected: COMPILATION ERROR, `cannot find symbol: class ConsoleChannel`.

- [ ] **Step 3: Write `ConsoleChannel.java`**

`src/main/java/io/meterian/aicalendar/channel/ConsoleChannel.java`:

```java
package io.meterian.aicalendar.channel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;

/** The terminal version of UserChannel. An alert that arrives while the user types prints the prompt again. */
public class ConsoleChannel implements UserChannel {

    static final String PROMPT = "> ";

    private final BufferedReader in;
    private final PrintStream out;
    /** The prompt on screen while waiting for input, or null. */
    private volatile String activePrompt;

    public ConsoleChannel(BufferedReader in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    @Override
    public String readLine() {
        return readAnswer(PROMPT);
    }

    @Override
    public void printReply(String text) {
        synchronized (out) {
            out.println(text);
            out.flush();
        }
    }

    @Override
    public void printAlert(String text) {
        synchronized (out) {
            String prompt = activePrompt;
            if (prompt != null) {
                out.println();
            }
            out.println(text);
            if (prompt != null) {
                out.print(prompt);
            }
            out.flush();
        }
    }

    @Override
    public boolean askYesNo(String question) {
        String answer = readAnswer(question + " (y/n) ");
        return answer != null && answer.trim().equalsIgnoreCase("y");
    }

    private String readAnswer(String prompt) {
        synchronized (out) {
            out.print(prompt);
            out.flush();
            activePrompt = prompt;
        }
        try {
            return in.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            activePrompt = null;
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q test -Dtest=ConsoleChannelTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Write `SystemPrompt.java`**

`src/main/java/io/meterian/aicalendar/agent/SystemPrompt.java`:

```java
package io.meterian.aicalendar.agent;

/** The rules that the model follows (spec section 8). */
public final class SystemPrompt {

    public static final String TEXT = String.join("\n",
            "You are the user's personal secretary. You manage their calendar: appointments, alarms and notes.",
            "You change the calendar only through the tools. Never say that you did something that no tool did.",
            "Rules:",
            "1. Call get-current-date-time before you turn a relative date such as 'Wednesday' or 'tomorrow' "
                    + "into a date.",
            "2. Never guess a required value. If the title, date or start time is missing, ask the user.",
            "3. If the user gives no lead time for an appointment, call get-default-lead-time and ask: "
                    + "'Do you want the alert N minutes before, or at a different time?'",
            "4. Ask for a place when the event is probably at a physical place, such as a dentist visit or a "
                    + "meeting. Otherwise, do not ask.",
            "5. Before edit or remove, call find-items to get the id, show the item to the user and ask for a "
                    + "yes. If two or more items match, ask which one.",
            "6. For a repeating item, change only the named occurrence when the request is clear "
                    + "('cancel gym next Monday'). When it is not clear ('cancel gym Monday'), ask: "
                    + "'Only one Monday, or the whole series?'",
            "7. To invite people, add them as attendees with set-appointment or edit. Never guess an email "
                    + "address. Call get-user-profile for the signature. Write the email in the tone the user asks "
                    + "for, then call send-invite. Send only when the user asks.",
            "8. If a tool result starts with ERROR:, read it, then fix the call or ask the user.",
            "9. Answer in short, plain sentences.");

    private SystemPrompt() {
    }
}
```

- [ ] **Step 6: Write `Main.java`**

`src/main/java/io/meterian/aicalendar/Main.java`:

```java
package io.meterian.aicalendar;

import io.meterian.aicalendar.agent.Agent;
import io.meterian.aicalendar.agent.SystemPrompt;
import io.meterian.aicalendar.alerts.AlertScheduler;
import io.meterian.aicalendar.calendar.CalendarData;
import io.meterian.aicalendar.calendar.CalendarFileException;
import io.meterian.aicalendar.calendar.CalendarService;
import io.meterian.aicalendar.calendar.CalendarStore;
import io.meterian.aicalendar.calendar.OccurrenceExpander;
import io.meterian.aicalendar.channel.ConsoleChannel;
import io.meterian.aicalendar.channel.UserChannel;
import io.meterian.aicalendar.chat.OllamaClient;
import io.meterian.aicalendar.email.EmlFormatter;
import io.meterian.aicalendar.email.FileEmailSender;
import io.meterian.aicalendar.email.IcsBuilder;
import io.meterian.aicalendar.tools.AddNoteTool;
import io.meterian.aicalendar.tools.EditTool;
import io.meterian.aicalendar.tools.FindItemsTool;
import io.meterian.aicalendar.tools.GetCurrentDateTimeTool;
import io.meterian.aicalendar.tools.GetDefaultLeadTimeTool;
import io.meterian.aicalendar.tools.GetUserProfileTool;
import io.meterian.aicalendar.tools.ListDayTool;
import io.meterian.aicalendar.tools.RemoveTool;
import io.meterian.aicalendar.tools.SendInviteTool;
import io.meterian.aicalendar.tools.SetAlarmTool;
import io.meterian.aicalendar.tools.SetAppointmentTool;
import io.meterian.aicalendar.tools.ToolRegistry;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.List;

/** Connects all parts and runs the conversation in the terminal. */
public final class Main {

    private static final String SETTINGS_FILE = "settings.properties";
    private static final String EXIT_COMMAND = "exit";
    private static final String WELCOME_TEXT =
            "Hello! I am your calendar secretary. Tell me what you need, or type 'exit' to quit.";

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        Settings settings = Settings.load(Paths.get(SETTINGS_FILE), System.getProperties());
        Clock clock = Clock.systemDefaultZone();
        CalendarService service = buildCalendarService(settings, clock);
        UserChannel channel = buildConsoleChannel();
        Agent agent = buildAgent(service, settings, channel, clock);

        AlertScheduler scheduler = new AlertScheduler(service, channel, clock);
        scheduler.start();
        runConversation(agent, channel);
        scheduler.stop();
    }

    private static CalendarService buildCalendarService(Settings settings, Clock clock) {
        CalendarStore store = new CalendarStore(Paths.get(settings.readValue("calendar.file", "calendar.json")));
        try {
            CalendarData data = store.load();
            return new CalendarService(store, data, new OccurrenceExpander(), clock);
        } catch (CalendarFileException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            throw e;
        }
    }

    private static UserChannel buildConsoleChannel() {
        return new ConsoleChannel(
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                new PrintStream(System.out, true, StandardCharsets.UTF_8));
    }

    private static Agent buildAgent(CalendarService service, Settings settings, UserChannel channel, Clock clock) {
        OllamaClient model = new OllamaClient(HttpClient.newHttpClient(),
                URI.create(settings.readValue("ollama.url", "http://localhost:11434")),
                settings.readValue("ollama.model", "gpt-oss:120b-cloud"));
        return new Agent(model, buildToolRegistry(service, settings, clock), channel, SystemPrompt.TEXT);
    }

    private static ToolRegistry buildToolRegistry(CalendarService service, Settings settings, Clock clock) {
        return new ToolRegistry(List.of(
                new GetCurrentDateTimeTool(clock),
                new GetDefaultLeadTimeTool(),
                new SetAppointmentTool(service),
                new SetAlarmTool(service),
                new AddNoteTool(service),
                new FindItemsTool(service),
                new ListDayTool(service),
                new EditTool(service),
                new RemoveTool(service),
                new GetUserProfileTool(settings),
                new SendInviteTool(service, settings, buildFileEmailSender(settings, clock),
                        new IcsBuilder(clock.getZone()), clock)));
    }

    private static FileEmailSender buildFileEmailSender(Settings settings, Clock clock) {
        return new FileEmailSender(Paths.get(settings.readValue("outbox.folder", "outbox")), new EmlFormatter(), clock);
    }

    private static void runConversation(Agent agent, UserChannel channel) {
        channel.printReply(WELCOME_TEXT);
        String request;
        while ((request = channel.readLine()) != null) {
            if (request.isBlank()) {
                continue;
            }
            if (request.trim().equalsIgnoreCase(EXIT_COMMAND)) {
                channel.printReply("Goodbye!");
                return;
            }
            channel.printReply(agent.handleUserMessage(request));
        }
    }
}
```

- [ ] **Step 7: Run all tests**

Run: `mvn -q test`
Expected: PASS, all test classes, no failures.

- [ ] **Step 8: Smoke test with the real model**

This needs Ollama running with `gpt-oss:120b-cloud` (check with `ollama list`). Run:

```bash
printf 'What is the date today?\nexit\n' | mvn -q compile exec:java -Dcalendar.file=target/smoke-calendar.json
```

Expected: the banner line, then a reply that contains today's date (the model called `get-current-date-time`). If Ollama is not running, the reply starts with `I cannot reach the model:` and the program still exits normally. Report which of the two happened.

- [ ] **Step 9: Write `README.md`**

```markdown
# AI Calendar

A terminal calendar agent. You write requests in plain language. The model manages appointments, alarms and notes
through tools, and the agent prints alerts when they are due.

This is a training project for LLM tool use. The agent loop and the calls to the Ollama `/api/chat` API are
written by hand. Read `src/main/java/io/meterian/aicalendar/agent/Agent.java` to see the loop.

## Requirements

- Java 11 and Maven 3.6.3 or newer
- Ollama running at `http://localhost:11434` with the model `gpt-oss:120b-cloud` (`ollama list`)

## Setup

    cp settings.example.properties settings.properties

Fill in your profile, including `profile.email` (the sender address of invitations). `settings.properties` holds personal data and is in `.gitignore`.

Emails are not sent. Each invitation is written as a `.eml` file in the `outbox/` folder, which takes the place of an SMTP server. Open the file with a mail program (Thunderbird, Outlook) to see the email and the invitation.

## Commands

- Build: `mvn package`
- Run all tests: `mvn test`
- Run one test: `mvn test -Dtest=ClassName#methodName`
- Start the agent: `mvn -q compile exec:java`
- Change a setting for one run: `mvn -q compile exec:java -Dollama.model=other-model`

Type `exit` to quit. Alerts show only while the agent runs.

## Manual test script

Run these requests in order and check each result:

1. `What is the date today?` — the model calls `get-current-date-time`.
2. `Remind me to go to the dentist on Wednesday` — the agent asks for the time.
3. Answer `15:00` — the agent asks about the 30-minute alert and the place.
4. `What is on Wednesday?` — the dentist appointment is listed.
5. `Gym every Monday at 18:00 at FitLife, alert 15 minutes before` — a weekly series is created.
6. `Move gym next Monday to 19:00` — only that Monday changes (`list-day` for the Monday after still shows 18:00).
7. `Cancel gym Monday` — the agent asks: one Monday, or the whole series?
8. `Cancel the dentist` — the agent shows the appointment and asks for a yes.
9. `Set a meeting with Anna Rossi on Friday at 10:00 at our office and send her an invite at anna@example.com.
   Say I'm thrilled to meet up to discuss this business opportunity, thank her for her time, and sign with my
   name, role and company.` — the agent shows the email and asks `Approve this action? (y/n)`. After `y`, check
   the `outbox/` folder: open the new `.eml` file and check the text and the `invite.ics` attachment.
10. `Set an alarm in 2 minutes: stretch` — within 30 seconds of that time, `⏰ HH:mm stretch` appears.
```

- [ ] **Step 10: Update `CLAUDE.md`**

Replace the whole `## Status` section (heading and its one paragraph) with:

```markdown
## Layout

Base package `io.meterian.aicalendar`:

- `calendar`: data model; `CalendarService` (thread-safe entry point) hands work to `ItemValidator`, `CalendarQueries`, `ItemEditor`, `ItemRemover` and `CalendarRepository`; `OccurrenceExpander` (repeats and overrides); `CalendarStore` (JSON file, atomic save)
- `chat`: `ChatModel`, `OllamaClient` (hand-written `/api/chat` calls)
- `agent`: `Agent` (tool-call loop, user approval, 10-round limit), `SystemPrompt`
- `tools`: one class per tool; `AbstractTool` turns exceptions into `ERROR:` results
- `alerts`: `AlertScheduler` (background thread, every 30 seconds)
- `email`: `IcsBuilder`, `EmlFormatter`, `FileEmailSender` (writes `.eml` files to `outbox/`; no email service)
- `channel`: `UserChannel`, `ConsoleChannel`

A tool returns JSON on success and text that starts with `ERROR:` on failure. It never throws into the agent loop.
```

In the `## Build` section, add these two lines after the "Run one test" line:

```markdown
- Start the agent: `mvn -q compile exec:java` (copy `settings.example.properties` to `settings.properties` first)
- The implementation plan is in `docs/superpowers/plans/2026-09-24-calendar-agent.md`.
```

- [ ] **Step 11: Commit**

```bash
git add src README.md CLAUDE.md
git commit -m "feat: add terminal channel, system prompt and main program" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```
