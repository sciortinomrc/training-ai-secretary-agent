package io.meterian.aicalendar.trace;

import com.fasterxml.jackson.databind.JsonNode;
import io.meterian.aicalendar.chat.ChatMessage;

/** Reports each step between the user, the agent and the LLM, so the conversation can be watched live. */
public interface ConversationTrace {

    void recordUserMessage(String text);

    void recordModelRequest(int messageCount, int toolCount);

    void recordModelReply(ChatMessage reply);

    void recordApproval(String question, boolean approved);

    void recordToolResult(String toolName, JsonNode arguments, String result);

    void recordAgentReply(String text);
}
