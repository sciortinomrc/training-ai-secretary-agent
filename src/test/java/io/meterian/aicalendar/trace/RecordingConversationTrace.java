package io.meterian.aicalendar.trace;

import com.fasterxml.jackson.databind.JsonNode;
import io.meterian.aicalendar.chat.ChatMessage;
import java.util.ArrayList;
import java.util.List;

/** A ConversationTrace for tests: keeps one short line per step, in order. */
public class RecordingConversationTrace implements ConversationTrace {

    public final List<String> steps = new ArrayList<>();

    @Override
    public void recordUserMessage(String text) {
        steps.add("user: " + text);
    }

    @Override
    public void recordModelRequest(int messageCount, int toolCount) {
        steps.add("request: " + messageCount + " messages, " + toolCount + " tools");
    }

    @Override
    public void recordModelReply(ChatMessage reply) {
        steps.add(reply.hasToolCalls()
                ? "reply: tool " + reply.toolCalls.get(0).function.name
                : "reply: text " + reply.content);
    }

    @Override
    public void recordApproval(String question, boolean approved) {
        steps.add("approval: " + question + " " + (approved ? "yes" : "no"));
    }

    @Override
    public void recordToolResult(String toolName, JsonNode arguments, String result) {
        steps.add("tool: " + toolName + " " + arguments + " -> " + result);
    }

    @Override
    public void recordAgentReply(String text) {
        steps.add("agent: " + text);
    }
}
