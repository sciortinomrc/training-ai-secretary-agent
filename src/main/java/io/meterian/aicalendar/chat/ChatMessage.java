package io.meterian.aicalendar.chat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/** One message in the Ollama /api/chat format. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage {
    public String role;
    public String content;
    /** Reasoning text from models such as gpt-oss. Sent back as it came. */
    public String thinking;
    @JsonProperty("tool_calls")
    public List<ToolCall> toolCalls = new ArrayList<>();
    /** Only for role "tool": the tool that made this result. */
    @JsonProperty("tool_name")
    public String toolName;

    public static ChatMessage buildSystemMessage(String content) {
        return buildMessage("system", content);
    }

    public static ChatMessage buildUserMessage(String content) {
        return buildMessage("user", content);
    }

    public static ChatMessage buildAssistantMessage(String content) {
        return buildMessage("assistant", content);
    }

    public static ChatMessage buildToolResultMessage(String toolName, String content) {
        ChatMessage message = buildMessage("tool", content);
        message.toolName = toolName;
        return message;
    }

    @JsonIgnore
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    private static ChatMessage buildMessage(String role, String content) {
        ChatMessage message = new ChatMessage();
        message.role = role;
        message.content = content;
        return message;
    }
}
