package io.meterian.aicalendar.trace;

import com.fasterxml.jackson.databind.JsonNode;
import io.meterian.aicalendar.chat.ChatMessage;

/** The trace to use when nobody watches: it records nothing. */
public class SilentConversationTrace implements ConversationTrace {

    @Override
    public void recordUserMessage(String text) {
    }

    @Override
    public void recordModelRequest(int messageCount, int toolCount) {
    }

    @Override
    public void recordModelReply(ChatMessage reply) {
    }

    @Override
    public void recordApproval(String question, boolean approved) {
    }

    @Override
    public void recordToolResult(String toolName, JsonNode arguments, String result) {
    }

    @Override
    public void recordAgentReply(String text) {
    }
}
