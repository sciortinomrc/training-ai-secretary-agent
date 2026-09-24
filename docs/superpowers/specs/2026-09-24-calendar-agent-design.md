# Calendar Agent — Design

Date: 2026-09-24

## 1. Goal

A terminal agent in Java that acts as the user's secretary. The user writes requests in plain language. The model does all calendar work through tools.

This is a training exercise. The main goal is to learn how an LLM uses tools. For this reason, the agent loop and the HTTP calls are written by hand. No agent library is used.

## 2. Decisions

| Topic | Decision |
|---|---|
| Language | Java 11 (as installed). No `record` types, no text blocks. |
| Build | Maven 3.6.3 |
| Model | `gpt-oss:120b-cloud`, reached through the local Ollama app |
| API | Ollama native API, `POST http://localhost:11434/api/chat`, no streaming |
| HTTP | `java.net.http.HttpClient` (built into Java 11) |
| JSON | Jackson (`jackson-databind`, `jackson-datatype-jsr310`) |
| Tests | JUnit 5 |
| Storage | One JSON file |
| User interface | Terminal only. A UI comes later. |
| Users | One user, one calendar, no login |
| Time zone | The system default time zone |
| Email | No email service. The outbox folder has two buckets: `drafts/` and `sent/`. Each email is a `.eml` file; writing it to `sent/` takes the place of an SMTP server. It only shows the capability. |
| Settings | `settings.properties` in the working directory |

### 2.1 Code quality

The code must follow Clean Code guidelines:

- **Meaningful names** for classes, methods, fields, parameters and local variables. No abbreviations. A method name says what the method does (`buildX`, `findX`, `listX`, `validateX`).
- **Single responsibility.** Each class has one job, written in its class comment. For example, `CalendarService` only locks and hands work to `ItemValidator`, `CalendarQueries`, `ItemEditor`, `ItemRemover` and `CalendarRepository`.
- **Short methods** that do one thing. A method with several steps calls one private method for each step.
- **Human friendly.** The code reads top-down like prose. Every message to the user or to the model is a plain, complete sentence that says what to do next.

## 3. Data model

### 3.1 Appointment

| Field | Required | Notes |
|---|---|---|
| `id` | yes | Made by the code, for example `A-7` |
| `title` | yes | |
| `date` | yes | `YYYY-MM-DD`. For a series, the date of the first occurrence. |
| `startTime` | yes | `HH:mm` |
| `endTime` | no | `HH:mm` |
| `place` | no | |
| `leadTimeMinutes` | yes | Minutes before `startTime` when the alert shows. 0 to 10080 (7 days). |
| `attendees` | no | List of `{ "name": ..., "email": ... }`. Both fields are required for each attendee. |
| `repeat` | no | See 3.4 |
| `overrides` | no | See 3.5 |

### 3.2 Alarm

An alarm has one of two forms. The code rejects an alarm that has both forms or neither.

- **Fixed alarm:** `id`, `message`, `date`, `time`, optional `repeat`, optional `overrides`.
- **Linked alarm:** `id`, `message`, `appointmentId`, `minutesBefore` (0 to 10080, 7 days). It fires before each occurrence of the appointment, so it follows the appointment when the appointment moves.

### 3.3 Note

`id`, `text`, and one of `date` or `appointmentId`. The code rejects a note that has both or neither.

### 3.4 Repeat rule

| Field | Required | Values |
|---|---|---|
| `frequency` | yes | `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY` |
| `daysOfWeek` | only for `WEEKLY` | For example `["MONDAY", "THURSDAY"]`. "Every weekday" is `MONDAY` to `FRIDAY`. |
| `until` | no | Last possible date, `YYYY-MM-DD`. Without it, the series has no end. |

- `MONTHLY` repeats on the day number of `date`. If a month does not have that day (for example, the 31st in April), that month has no occurrence.
- `YEARLY` repeats on the month and day of `date`. A series that starts on 29 February occurs only in leap years.

### 3.5 Overrides (changes to one occurrence)

`overrides` is a map from the original occurrence date to a change:

```json
"overrides": {
  "2026-09-28": { "cancelled": true },
  "2026-10-05": { "date": "2026-10-06", "startTime": "19:00" }
}
```

- `cancelled: true` removes that one occurrence.
- Any other field replaces the series value for that one occurrence. The fields that can change are `date`, `startTime`, `endTime`, `place`, `title`, `leadTimeMinutes` for appointments, and `date`, `time`, `message` for fixed alarms.
- A moved occurrence shows on its new date, not on its original date.

### 3.6 Email draft

An invitation email that is not sent yet: `id` (for example `D-1`), `appointmentId`, `subject`, `body`. All fields are required, and the appointment must exist. Drafts are stored in `calendar.json`; the `.eml` files in `outbox/drafts/` are built from them. Sending a draft removes it.

### 3.7 User profile

The user's `name`, `surname`, `role` and `company` come from `settings.properties` (section 4.1). The model uses them to sign emails. It never guesses them. The user's `email` is the sender address of the invitations.

### 3.8 IDs

The code makes each ID from a prefix and a counter: `A-` for appointments, `L-` for alarms, `N-` for notes, `D-` for email drafts. The file stores the counters. IDs are short, so the model can copy them without errors.

### 3.9 File

Default path: `calendar.json` in the working directory. The system property `-Dcalendar.file=<path>` changes it.

```json
{
  "nextIds": { "A": 3, "L": 2, "N": 1, "D": 1 },
  "appointments": [ ... ],
  "alarms": [ ... ],
  "notes": [ ... ]
}
```

The store writes to a temporary file and then moves it over the old file, so a crash cannot leave a half-written file.

## 4. Parts

```
Terminal ──► ConsoleChannel ──► Agent ──► OllamaClient ──► localhost:11434/api/chat
                  ▲               │
                  │               ▼
            AlertScheduler    ToolRegistry ──► tools ──► CalendarService ──► CalendarStore ──► calendar.json
                  │                                           ▲
                  └──────────── reads occurrences ────────────┘
```

| Part | Job |
|---|---|
| `UserChannel` | Interface: read a line, print a reply, print an alert. A UI can replace the terminal through it. |
| `ConsoleChannel` | Terminal version of `UserChannel` |
| `Agent` | Runs the tool-call loop (section 5) and keeps the conversation for the session |
| `ChatModel` | Interface with one method: send the messages and tools, get one reply. It lets the tests replace the real model. |
| `OllamaClient` | `ChatModel` that calls `/api/chat` |
| `Tool` | Interface: `getName()`, `getDescription()`, `buildParametersSchema()`, `execute(JsonNode arguments)` |
| `ToolRegistry` | Builds the tool list for the request and sends each call to its tool |
| `CalendarService` | Calendar rules: checks fields, makes IDs, expands repeats, applies overrides. All public methods are `synchronized`, because the agent and the alert thread use it at the same time. |
| `OccurrenceExpander` | Turns items and repeat rules into dated occurrences for a date range |
| `CalendarStore` | Reads and writes the JSON file |
| `AlertScheduler` | Background thread that prints due alerts (section 7) |
| `EmailSender` | Interface: deliver one email and say where it went. A real SMTP sender can replace the file sender later. |
| `EmlFormatter` | Formats one email as standard `.eml` text (headers, text part, `.ics` attachment) |
| `FileEmailSender` | `EmailSender` that writes each sent email as a `.eml` file in `outbox/sent/` |
| `DraftFolder` | Writes and deletes the `.eml` files of each draft in `outbox/drafts/` (for example `D-1-john_example.com.eml`) |
| `InviteEmailBuilder` | Builds the invitation emails of an appointment (one per attendee, `.ics` attached) and the approval preview |
| `IcsBuilder` | Builds the `.ics` invitation text for one appointment |
| `Main` | Reads settings, connects the parts, starts the terminal loop |

`CalendarService`, `OccurrenceExpander` and `AlertScheduler` get the time from a `java.time.Clock`, so tests can set the time.

### 4.1 Settings

`Main` reads `settings.properties` from the working directory. A system property with the same name (`-Dname=value`) replaces a value from the file.

| Key | Default | Notes |
|---|---|---|
| `ollama.url` | `http://localhost:11434` | |
| `ollama.model` | `gpt-oss:120b-cloud` | |
| `calendar.file` | `calendar.json` | |
| `profile.name`, `profile.surname`, `profile.role`, `profile.company` | none | Used to sign emails |
| `profile.email` | none | Sender address of the invitations |
| `outbox.folder` | `outbox` | Root folder of the `drafts/` and `sent/` buckets |

`settings.properties` holds personal data. It must not go into version control. The project has a `settings.example.properties` with empty values.

If the profile values are missing, the agent still starts. Only `get-user-profile`, `draft-invite` and `send-draft` return `ERROR:` results that name the missing keys.

## 5. Agent loop

1. Add the user message to the conversation.
2. Send the system prompt, the conversation and the tool definitions to `/api/chat` with `"stream": false`.
3. Add the reply of the model to the conversation.
4. If the reply has `tool_calls`, run each call, add one `tool` message with the result for each call, and go back to step 2.
5. If the reply has no `tool_calls`, show its text to the user.

### 5.1 User approval

A tool can mark itself as "needs approval" (`Tool.requiresApproval()`). Before the loop runs such a tool, it:

1. Asks the tool for a text that shows what it will do (`Tool.describeCall(arguments)`). For `send-draft`, this is the stored invitation data (title, date, time, place, repeat) and the full email for each attendee: recipient, subject and text.
2. Prints that text and asks the tool's own question (`Tool.getApprovalQuestion()`, default `Approve this action?`) through `UserChannel`, with `(y/n)`. For `send-draft`, the question is `Send this email now?`, so it is clear that yes sends the email.
3. Runs the tool only after `y`. After any other answer, it returns `ERROR: the user declined.` to the model.

The model cannot skip this step, because the code runs it, not the prompt. Only `send-draft` needs approval.

### 5.2 Round limit

After 10 rounds of tool calls without a text reply, the agent stops and tells the user: "I could not finish this request."

### 5.3 Tool errors

A tool never throws an error into the loop. It always returns a text result. An error result starts with `ERROR:` and says what is wrong, for example `ERROR: startTime is missing. Ask the user for it.` The model then reads it and asks the user.

## 6. Tools

All dates are `YYYY-MM-DD`. All times are `HH:mm`. Every tool returns text.

| Tool | Parameters (* = required) | Result |
|---|---|---|
| `get-current-date-time` | none | Current date, time, day of the week and time zone |
| `get-default-lead-time` | none | `30` (the constant `DEFAULT_LEAD_TIME_MINUTES`) |
| `set-appointment` | `title`*, `date`*, `startTime`*, `leadTimeMinutes`*, `endTime`, `place`, `repeat` | The new appointment and its ID |
| `set-alarm` | `message`*, then either `date`* + `time`* + optional `repeat`, or `appointmentId`* + `minutesBefore`* | The new alarm and its ID |
| `add-note` | `text`*, and one of `date` or `appointmentId` | The new note and its ID |
| `find-items` | `query`, `fromDate`, `toDate`, `type` (`appointment`, `alarm`, `note`) | Matching items with IDs and repeat rules |
| `list-day` | `date`* | All appointments, alarms and notes on that day, sorted by time |
| `edit` | `id`*, `occurrenceDate`, and the fields to change | The item after the change |
| `remove` | `id`*, `occurrenceDate` | What was removed |
| `get-user-profile` | none | Name, surname, role and company |
| `draft-invite` | `appointmentId`*, `subject`*, `body`*, `draftId` | Saves a new draft, or replaces the draft `draftId`. Writes its `.eml` files to `outbox/drafts/`. Sends nothing. Returns the `draftId`. |
| `list-drafts` | none | The drafts that are not sent yet: id, appointmentId, subject, body |
| `send-draft` | `draftId`* | Writes one email per attendee to `outbox/sent/`, then removes the draft. One result line for each attendee: sent, or the error. If an email fails, the draft is kept. Needs approval (5.1). |

For `edit` and `remove`:
- With `occurrenceDate`, the change applies only to that occurrence (it becomes an override).
- Without `occurrenceDate`, the change applies to the whole item or series.
- Removing an appointment also removes its linked alarms and notes. The result lists them.

For `draft-invite` and `send-draft`:
- The appointment must have at least one attendee.
- The model writes `subject` and `body`, including the greeting and the signature. The code does not change them.
- Each attendee gets one `.eml` file with an `.ics` invitation for the appointment attached (`METHOD:REQUEST`, the user as organizer, the attendee as attendee). For a series, the `.ics` includes the repeat rule and the changed occurrences.
- The outbox has two buckets: `drafts/` (not sent) and `sent/` (delivered). A file in `sent/` is the delivered email: there is no later sending step.
- The email is sent only when the user asks for it. Creating an appointment with attendees, or saving a draft, sends nothing.

The code checks every call. A missing required field, a bad date or time, an unknown ID, or a field that does not fit the item type gives an `ERROR:` result.

## 7. Alerts

- Every 30 seconds, `AlertScheduler` finds the alerts with a time after the last check and not after now.
- An appointment alert fires at `startTime` minus `leadTimeMinutes`. A fixed alarm fires at its time. A linked alarm fires at the appointment start minus `minutesBefore`.
- Overrides apply: a cancelled occurrence gives no alert, and a moved occurrence alerts at its new time.
- The scheduler keeps the keys of the alerts that it printed, so it never prints an alert two times.
- At startup, the last check is set to now. Alerts that were due while the agent was closed do not show. The user accepts this limit.
- Alert format: `⏰ 14:30 Dentist at 15:00 (Via Roma 10)` or `⏰ 07:00 Wake up`.
- `ConsoleChannel` prints an alert on a new line and then prints the input prompt again.

## 8. System prompt rules

The system prompt tells the model to:

1. Call `get-current-date-time` before it turns a relative date ("Wednesday", "tomorrow") into a date.
2. Derive the title from the request, for example "Meeting with John Stone" or "Dentist". Ask for the title only when the request gives no hint. Never guess the date, the start time or an email address: if one is missing, ask the user. Taking a value from context is not guessing: when the user says "also", "that day" or "before the meeting", use the date of the appointment just discussed, or, when the conversation does not show it, of the newest appointment (`find-items`). Always say which date was used.
3. If the user gives no lead time, call `get-default-lead-time` and ask: "Do you want the alert 30 minutes before, or at a different time?"
4. Ask for a place when the event is probably at a physical place, for example a dentist visit. For a meeting, always ask where it is, or if it is online. Otherwise, do not ask for a place.
5. Before an `edit` or `remove`, use `find-items` to get the ID, show the item to the user, and ask for a yes. If two or more items match, ask which one.
6. For a series, change only the named occurrence when the request is clear ("cancel gym next Monday"). When it is not clear ("cancel gym Monday"), ask: "Only one Monday, or the whole series?"
7. To invite attendees, add them to the appointment with `set-appointment` or `edit`. Never guess an email address. Call `get-user-profile` for the signature. Write the email in the tone the user asks for and save it with `draft-invite`. To change a draft, call `draft-invite` again with its `draftId` and the complete new text. Never store an email as a note. Use `list-drafts` to find drafts. Drafting is not sending: "draft", "write" or "prepare" mean `draft-invite` only, and `send-draft` is called only when the user's latest message asks to send. When the user asks to send, call `send-draft` at once (after `draft-invite` if the text changed). Do not ask for confirmation in chat: the approval step (5.1) is the only confirmation.
8. When confirming a change, state only the values in the tool result. If the user gives an end time or a duration, pass `endTime`.
9. When several values are missing, ask for all of them in one message, as a short numbered list.
10. Answer in short, plain sentences.

The confirmation in rule 5 is enforced only by the prompt, not by the code.

## 9. Errors

| Problem | Behavior |
|---|---|
| Ollama not reachable, or an HTTP error | Print a clear message. Keep the conversation. The user can try again. |
| Model calls an unknown tool | `ERROR:` result to the model |
| Bad tool arguments | `ERROR:` result to the model |
| 10 rounds without a text reply | Stop and tell the user |
| `calendar.json` cannot be read or parsed | Stop at startup with a message. Do not overwrite the file. |
| Writing the file fails for one attendee | The result names that attendee and the error. The other emails are still written. |
| Profile settings missing | `ERROR:` result that names the missing keys |
| `calendar.json` does not exist | Start with an empty calendar. Create the file at the first save. |

## 10. Tests

- **Unit tests** (JUnit 5, fixed `Clock`, no network):
  - `OccurrenceExpander`: each frequency, `daysOfWeek`, `until`, the 31st in short months, 29 February, cancelled and moved occurrences.
  - `CalendarService`: required fields, the two alarm forms, the two note forms, IDs, linked removal.
  - `CalendarStore`: write and read back in a temporary folder; bad file stops without overwrite.
  - Each tool: correct result and `ERROR:` results.
  - `Agent`: a fake `ChatModel` returns a scripted sequence (tool call, then text). The test checks the messages and the 10-round limit.
  - `AlertScheduler`: due alerts print once; cancelled occurrences do not print.
  - Approval: a fake `UserChannel` answers `y` or `n`; the tool runs only after `y`.
  - Drafts: saving, replacing, listing and removing drafts in `CalendarService`; `DraftFolder` writes, replaces and deletes only its own files.
  - `draft-invite`: writes to `drafts/`, keeps the id when a draft changes, sends nothing.
  - `send-draft`: needs approval; a fake `EmailSender` records one email per attendee with the `.ics`; the draft is removed on success and kept on a failure.
  - `EmlFormatter`: headers, text part, attachment part and boundaries.
  - `FileEmailSender`: one file per email, a numbered name when the name is taken, an error when the folder cannot be written.
  - `IcsBuilder`: required `.ics` fields, times, and the repeat rule.
- **Manual test** with the real model: a short script of requests in the README (create, ask for missing time, list a day, edit one occurrence, remove with confirmation, send an invite and open the `.eml` file from the outbox folder).

## 11. Out of scope

- A graphical UI
- Alerts while the agent is closed, and missed alerts at startup
- More than one user or calendar
- Saving the conversation between sessions
- Streaming replies
- Other LLM providers
- Sending real emails
- Update or cancel emails to attendees after an edit or a remove
- Reading attendee replies (accept or decline)
