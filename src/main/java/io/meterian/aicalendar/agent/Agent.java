package io.meterian.aicalendar.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.channel.UserChannel;
import io.meterian.aicalendar.chat.ChatMessage;
import io.meterian.aicalendar.chat.ChatModel;
import io.meterian.aicalendar.chat.ChatModelException;
import io.meterian.aicalendar.chat.ToolCall;
import io.meterian.aicalendar.tools.Tool;
import io.meterian.aicalendar.tools.ToolRegistry;
import io.meterian.aicalendar.trace.ConversationTrace;
import io.meterian.aicalendar.trace.SilentConversationTrace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The hand-written tool-call loop: send the conversation, run the tool calls in the reply, send the results back,
 * and repeat until the model answers with text.
 */
public class Agent {

    public static final int MAX_TOOL_ROUNDS = 10;
    public static final String GIVE_UP_REPLY = "I could not finish this request.";
    public static final String EMPTY_REPLY = "I have no answer. Please say it in a different way.";
    public static final String DECLINED_RESULT = "ERROR: the user declined.";

    private final ChatModel model;
    private final ToolRegistry tools;
    private final UserChannel channel;
    private final String systemPrompt;
    private final ConversationTrace trace;
    private final List<ChatMessage> conversation = new ArrayList<>();

    public Agent(ChatModel model, ToolRegistry tools, UserChannel channel, String systemPrompt) {
        this(model, tools, channel, systemPrompt, new SilentConversationTrace());
    }

    public Agent(ChatModel model, ToolRegistry tools, UserChannel channel, String systemPrompt,
            ConversationTrace trace) {
        this.model = model;
        this.tools = tools;
        this.channel = channel;
        this.systemPrompt = systemPrompt;
        this.trace = trace;
    }

    public String handleUserMessage(String text) {
        trace.recordUserMessage(text);
        conversation.add(ChatMessage.buildUserMessage(text));
        String reply = answerUserMessage();
        trace.recordAgentReply(reply);
        return reply;
    }

    public List<ChatMessage> getConversation() {
        return Collections.unmodifiableList(conversation);
    }

    private String answerUserMessage() {
        try {
            return runToolLoop();
        } catch (ChatModelException e) {
            return "I cannot reach the model: " + e.getMessage()
                    + " Please check that Ollama is running, then try again.";
        } catch (RuntimeException e) {
            return "Something went wrong: " + describeError(e)
                    + ". Please try again. If it happens again, type 'exit' and check settings.properties.";
        }
    }

    private String runToolLoop() {
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            List<ChatMessage> messages = buildRequestMessages();
            List<JsonNode> toolDefinitions = tools.buildToolDefinitions();
            trace.recordModelRequest(messages.size(), toolDefinitions.size());
            ChatMessage reply = model.requestReply(messages, toolDefinitions);
            trace.recordModelReply(reply);
            conversation.add(reply);
            if (!reply.hasToolCalls()) {
                return readReplyText(reply);
            }
            runToolCalls(reply.toolCalls);
        }
        return GIVE_UP_REPLY;
    }

    private List<ChatMessage> buildRequestMessages() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.buildSystemMessage(systemPrompt));
        messages.addAll(conversation);
        return messages;
    }

    private void runToolCalls(List<ToolCall> toolCalls) {
        for (ToolCall toolCall : toolCalls) {
            String result = runToolCall(toolCall);
            trace.recordToolResult(toolCall.function.name, readArguments(toolCall), result);
            conversation.add(ChatMessage.buildToolResultMessage(toolCall.function.name, result));
        }
    }

    private String runToolCall(ToolCall toolCall) {
        Optional<Tool> tool = tools.findTool(toolCall.function.name);
        if (tool.isEmpty()) {
            return "ERROR: unknown tool '" + toolCall.function.name + "'.";
        }
        JsonNode arguments = readArguments(toolCall);
        try {
            if (tool.get().requiresApproval() && !askUserForApproval(tool.get(), arguments)) {
                return DECLINED_RESULT;
            }
            return tool.get().execute(arguments);
        } catch (RuntimeException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private boolean askUserForApproval(Tool tool, JsonNode arguments) {
        channel.printReply(tool.describeCall(arguments));
        boolean approved = channel.askYesNo(tool.getApprovalQuestion());
        trace.recordApproval(tool.getApprovalQuestion(), approved);
        return approved;
    }

    /** A model can send a tool call without arguments. The tool then gets an empty object. */
    private static JsonNode readArguments(ToolCall toolCall) {
        JsonNode arguments = toolCall.function.arguments;
        return arguments == null || arguments.isNull() ? Json.MAPPER.createObjectNode() : arguments;
    }

    private static String readReplyText(ChatMessage reply) {
        return reply.content == null || reply.content.isBlank() ? EMPTY_REPLY : reply.content;
    }

    /** Some exceptions have no message, for example a NullPointerException. Then the type name is shown. */
    private static String describeError(RuntimeException error) {
        return error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
    }
}
