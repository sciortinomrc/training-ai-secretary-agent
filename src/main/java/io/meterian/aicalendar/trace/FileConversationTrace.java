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
 * Writes each conversation step as one colored line to a file, for example to watch it with "tail -f" next to
 * the chat. A trace that cannot be written never stops the chat: it prints one warning and goes quiet.
 */
public class FileConversationTrace implements ConversationTrace {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_TEXT_LENGTH = 400;
    private static final String RESET = "\u001B[0m";
    private static final String CYAN = "\u001B[36m";
    private static final String YELLOW = "\u001B[33m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String GREY = "\u001B[90m";

    private final Path file;
    private final Clock clock;
    private boolean isBroken;

    public FileConversationTrace(Path file, Clock clock) {
        this.file = file;
        this.clock = clock;
    }

    @Override
    public void recordUserMessage(String text) {
        writeLine(CYAN, "YOU → AGENT", shortenText(text));
    }

    @Override
    public void recordModelRequest(int messageCount, int toolCount) {
        writeLine(YELLOW, "AGENT → LLM", messageCount + " messages, " + toolCount + " tools");
    }

    @Override
    public void recordModelReply(ChatMessage reply) {
        if (reply.thinking != null && !reply.thinking.isBlank()) {
            writeLine(GREY, "LLM → AGENT", "thinking: " + shortenText(reply.thinking));
        }
        if (reply.hasToolCalls()) {
            for (ToolCall toolCall : reply.toolCalls) {
                String call = toolCall.function.name + " " + toolCall.function.arguments;
                writeLine(MAGENTA, "LLM → AGENT", "tool call: " + shortenText(call));
            }
        } else {
            writeLine(MAGENTA, "LLM → AGENT", "text: " + shortenText(reply.content));
        }
    }

    @Override
    public void recordApproval(String question, boolean approved) {
        writeLine(RED, "YOU APPROVE", question + " → " + (approved ? "yes" : "no"));
    }

    @Override
    public void recordToolResult(String toolName, JsonNode arguments, String result) {
        writeLine(GREEN, "TOOL", toolName + " → " + shortenText(result));
    }

    @Override
    public void recordAgentReply(String text) {
        writeLine(CYAN, "AGENT → YOU", shortenText(text));
    }

    /** For example: "17:05:01 AGENT → LLM    3 messages, 14 tools", with the direction in color. */
    private synchronized void writeLine(String color, String direction, String text) {
        if (isBroken) {
            return;
        }
        String time = TIME_FORMAT.format(LocalTime.now(clock));
        String line = time + " " + color + String.format("%-14s", direction) + RESET + " " + text + "\n";
        try {
            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
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
