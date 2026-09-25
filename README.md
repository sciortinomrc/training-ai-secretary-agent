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

Emails are not sent over the network. The `outbox/` folder has two buckets:

- `outbox/drafts/` — drafts you can still change, for example `D-1-anna_example.com.eml`.
- `outbox/sent/` — delivered emails. Writing a file here takes the place of an SMTP server.

Open a file with a mail program (Thunderbird, Outlook) to see the email and the `invite.ics` invitation.

## Commands

- Build: `mvn package`
- Run all tests: `mvn test`
- Run one test: `mvn test -Dtest=ClassName#methodName`
- Start the agent: `mvn -q compile exec:java`
- Change a setting for one run: `mvn -q compile exec:java -Dollama.model=other-model`

Type `exit` to quit. Alerts show only while the agent runs.

## See how the agent talks to the LLM

    ./run-side-by-side.sh

This opens tmux with two panes: the chat on the left, and on the right what the chat does not show — the LLM's
reasoning (THINKING), its tool calls (TOOL CALL) and the tool results (TOOL RESULT). Each of your requests starts
with a separator line. Type `exit` in the chat to close both. The trace is also saved in `trace.log`.

Without tmux, set `trace.file=trace.log` in `settings.properties` and run `tail -f trace.log` in a second terminal.

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
9. `Set a meeting with Anna Rossi on Friday at 10:00 at our office. Her email is anna@example.com. Draft an invite:
   say I'm thrilled to meet up to discuss this business opportunity, and sign with my name, role and company.`
   — the draft appears in `outbox/drafts/`. Nothing is sent.
10. `Edit the draft: tell her I might be slightly late. Then send it.` — the agent shows the invitation details and
    the email, and asks `Approve this action? (y/n)`. After `y`, the email is in `outbox/sent/` and the draft is gone.
11. `Set an alarm in 2 minutes: stretch` — within 30 seconds of that time, `⏰ HH:mm stretch` appears.
