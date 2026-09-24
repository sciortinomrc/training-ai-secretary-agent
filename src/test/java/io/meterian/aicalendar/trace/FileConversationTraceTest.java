package io.meterian.aicalendar.trace;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.chat.ChatMessage;
import io.meterian.aicalendar.chat.ToolCall;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileConversationTraceTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    @TempDir
    Path tempDir;

    private Path traceFile;
    private FileConversationTrace trace;

    @BeforeEach
    void createTrace() {
        traceFile = tempDir.resolve("trace.log");
        Clock clock = Clock.fixed(LocalDateTime.of(2026, 9, 24, 17, 5, 1).atZone(ROME).toInstant(), ROME);
        trace = new FileConversationTrace(traceFile, clock);
    }

    /** The file has colors for the terminal; the tests read it without them. */
    private String readTraceWithoutColors() throws Exception {
        return Files.readString(traceFile).replaceAll("\u001B\\[[0-9;]*m", "");
    }

    @Test
    void writesOneReadableLinePerStep() throws Exception {
        ChatMessage toolCallReply = ChatMessage.buildAssistantMessage("");
        toolCallReply.thinking = "The user wants Monday.";
        toolCallReply.toolCalls.add(ToolCall.buildToolCall("list-day",
                Json.MAPPER.readTree("{\"date\":\"2026-09-28\"}")));

        trace.recordUserMessage("What is on Monday?");
        trace.recordModelRequest(3, 14);
        trace.recordModelReply(toolCallReply);
        trace.recordToolResult("list-day", Json.MAPPER.readTree("{\"date\":\"2026-09-28\"}"), "{\"appointments\":[]}");
        trace.recordApproval("Send this email now?", true);
        trace.recordModelReply(ChatMessage.buildAssistantMessage("Monday is free."));
        trace.recordAgentReply("Monday is free.");

        String text = readTraceWithoutColors();
        assertTrue(text.contains("17:05:01 YOU → AGENT    What is on Monday?"), text);
        assertTrue(text.contains("17:05:01 AGENT → LLM    3 messages, 14 tools"), text);
        assertTrue(text.contains("17:05:01 LLM → AGENT    thinking: The user wants Monday."), text);
        assertTrue(text.contains("17:05:01 LLM → AGENT    tool call: list-day {\"date\":\"2026-09-28\"}"), text);
        assertTrue(text.contains("17:05:01 TOOL           list-day → {\"appointments\":[]}"), text);
        assertTrue(text.contains("17:05:01 YOU APPROVE    Send this email now? → yes"), text);
        assertTrue(text.contains("17:05:01 LLM → AGENT    text: Monday is free."), text);
        assertTrue(text.contains("17:05:01 AGENT → YOU    Monday is free."), text);
    }

    @Test
    void longTextIsShortened() throws Exception {
        trace.recordToolResult("find-items", Json.MAPPER.createObjectNode(), "x".repeat(1000));

        String text = readTraceWithoutColors();
        assertTrue(text.contains("x".repeat(400) + "… (600 more characters)"), text);
    }

    @Test
    void anUnwritableTraceNeverStopsTheChat() throws Exception {
        Path notAFolder = tempDir.resolve("afile");
        Files.writeString(notAFolder, "x");
        FileConversationTrace brokenTrace =
                new FileConversationTrace(notAFolder.resolve("trace.log"), Clock.systemDefaultZone());

        brokenTrace.recordUserMessage("Hello");
        brokenTrace.recordAgentReply("Hi");
    }
}
