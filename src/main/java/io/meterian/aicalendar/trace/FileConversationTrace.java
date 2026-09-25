package io.meterian.aicalendar.trace;

import com.fasterxml.jackson.databind.JsonNode;
import io.meterian.aicalendar.chat.ChatMessage;
import io.meterian.aicalendar.chat.ToolCall;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes what the chat does not show to a file: the LLM's reasoning, its tool calls and the tool results. Each
 * user request starts with a separator line. It is meant to be watched with "tail -f" next to the chat. A trace
 * that cannot be written never stops the chat: it prints one warning and goes quiet.
 */
public class FileConversationTrace implements ConversationTrace {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_TEXT_LENGTH = 400;
    private static final String REQUEST_SEPARATOR = "──── new request ────";
    private static final String RESET = "\u001B[0m";
    private static final String GREY = "\u001B[90m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String GREEN = "\u001B[32m";

    private final Path file;
    private final Clock clock;
    private boolean isBroken;

    public FileConversationTrace(Path file, Clock clock) {
        this.file = file;
        this.clock = clock;
    }

    /** The chat shows the message itself; the trace only marks where a new request starts. */
    @Override
    public void recordUserMessage(String text) {
        appendToFile(GREY + REQUEST_SEPARATOR + RESET + "\n");
    }

    @Override
    public void recordModelRequest(int messageCount, int toolCount) {
    }

    /** Shows the reasoning and the tool calls. A text reply is skipped, because the chat shows it. */
    @Override
    public void recordModelReply(ChatMessage reply) {
        if (reply.thinking != null && !reply.thinking.isBlank()) {
            writeLine(GREY, "THINKING", shortenText(reply.thinking));
        }
        for (ToolCall toolCall : reply.toolCalls) {
            String call = toolCall.function.name + " " + toolCall.function.arguments;
            writeLine(MAGENTA, "TOOL CALL", shortenText(call));
        }
    }

    /** The chat shows the approval question and the answer. */
    @Override
    public void recordApproval(String question, boolean approved) {
    }

    @Override
    public void recordToolResult(String toolName, JsonNode arguments, String result) {
        writeLine(GREEN, "TOOL RESULT", toolName + " → " + shortenText(result));
    }

    /** The chat shows the reply. */
    @Override
    public void recordAgentReply(String text) {
    }

    /** For example: "17:05:01 TOOL CALL    list-day {...}", with the label in color. */
    private void writeLine(String color, String label, String text) {
        String time = TIME_FORMAT.format(LocalTime.now(clock));
        appendToFile(time + " " + color + String.format("%-12s", label) + RESET + " " + text + "\n");
    }

    private synchronized void appendToFile(String text) {
        if (isBroken) {
            return;
        }
        try {
            Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            isBroken = true;
            System.err.println("The conversation trace cannot be written to " + file + ": " + e.getMessage()
                    + ". The chat goes on without it.");
        }
    }

    /** Keeps each line readable: line breaks become ⏎, and long text is cut after MAX_TEXT_LENGTH characters. */
    private static String shortenText(String text) {
        String singleLine = String.valueOf(text).replace("\n", " ⏎ ");
        if (singleLine.length() <= MAX_TEXT_LENGTH) {
            return singleLine;
        }
        int hiddenLength = singleLine.length() - MAX_TEXT_LENGTH;
        return singleLine.substring(0, MAX_TEXT_LENGTH) + "… (" + hiddenLength + " more characters)";
    }
}
