package io.meterian.aicalendar.chat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Sends the conversation and the tool definitions to a model and returns its one reply. */
public interface ChatModel {
    ChatMessage requestReply(List<ChatMessage> messages, List<JsonNode> tools);
}
