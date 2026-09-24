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
import io.meterian.aicalendar.email.DraftFolder;
import io.meterian.aicalendar.email.EmlFormatter;
import io.meterian.aicalendar.email.FileEmailSender;
import io.meterian.aicalendar.email.IcsBuilder;
import io.meterian.aicalendar.tools.AddNoteTool;
import io.meterian.aicalendar.tools.DraftInviteTool;
import io.meterian.aicalendar.tools.EditTool;
import io.meterian.aicalendar.tools.FindItemsTool;
import io.meterian.aicalendar.tools.GetCurrentDateTimeTool;
import io.meterian.aicalendar.tools.GetDefaultLeadTimeTool;
import io.meterian.aicalendar.tools.GetUserProfileTool;
import io.meterian.aicalendar.tools.InviteEmailBuilder;
import io.meterian.aicalendar.tools.ListDraftsTool;
import io.meterian.aicalendar.tools.ListDayTool;
import io.meterian.aicalendar.tools.RemoveTool;
import io.meterian.aicalendar.tools.SendDraftTool;
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
import java.nio.file.Path;
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
        Path outboxFolder = Paths.get(settings.readValue("outbox.folder", "outbox"));
        EmlFormatter formatter = new EmlFormatter();
        DraftFolder draftFolder = new DraftFolder(outboxFolder.resolve("drafts"), formatter, clock);
        FileEmailSender sentFolderSender = new FileEmailSender(outboxFolder.resolve("sent"), formatter, clock);
        InviteEmailBuilder emailBuilder = new InviteEmailBuilder(settings, new IcsBuilder(clock.getZone()), clock);
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
                new DraftInviteTool(service, emailBuilder, draftFolder),
                new ListDraftsTool(service),
                new SendDraftTool(service, emailBuilder, sentFolderSender, draftFolder)));
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
