package io.meterian.aicalendar.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import io.meterian.aicalendar.channel.RecordingUserChannel;
import io.meterian.aicalendar.chat.ChatMessage;
import io.meterian.aicalendar.chat.ChatModel;
import io.meterian.aicalendar.chat.ChatModelException;
import io.meterian.aicalendar.chat.ToolCall;
import io.meterian.aicalendar.tools.SchemaBuilder;
import io.meterian.aicalendar.tools.Tool;
import io.meterian.aicalendar.tools.ToolRegistry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentTest {

    private static final String SYSTEM_PROMPT = "You are a test secretary.";

    private final RecordingUserChannel channel = new RecordingUserChannel();

    /** Returns scripted replies (or throws scripted failures) and records every request. */
    private static class ScriptedChatModel implements ChatModel {
        final Deque<Object> script = new ArrayDeque<>();
        final List<List<ChatMessage>> requests = new ArrayList<>();

        ScriptedChatModel queueReply(ChatMessage reply) {
            script.add(reply);
            return this;
        }

        ScriptedChatModel queueFailure(RuntimeException failure) {
            script.add(failure);
            return this;
        }

        @Override
        public ChatMessage requestReply(List<ChatMessage> messages, List<JsonNode> tools) {
            requests.add(new ArrayList<>(messages));
            Object next = script.removeFirst();
            if (next instanceof RuntimeException) {
                throw (RuntimeException) next;
            }
            return (ChatMessage) next;
        }
    }

    private static class EchoTool implements Tool {
        final boolean needsApproval;
        int executions;

        EchoTool(boolean needsApproval) {
            this.needsApproval = needsApproval;
        }

        @Override
        public String getName() {
            return "echo";
        }

        @Override
        public String getDescription() {
            return "Echo the text.";
        }

        @Override
        public ObjectNode buildParametersSchema() {
            return new SchemaBuilder().addString("text", "Text to echo.", false).build();
        }

        @Override
        public String execute(JsonNode arguments) {
            executions++;
            return "echo:" + arguments.path("text").asText();
        }

        @Override
        public boolean requiresApproval() {
            return needsApproval;
        }

        @Override
        public String describeCall(JsonNode arguments) {
            return "I will echo " + arguments.path("text").asText();
        }
    }

    private static class FailingTool implements Tool {
        @Override
        public String getName() {
            return "fail";
        }

        @Override
        public String getDescription() {
            return "Always fails.";
        }

        @Override
        public ObjectNode buildParametersSchema() {
            return new SchemaBuilder().build();
        }

        @Override
        public String execute(JsonNode arguments) {
            throw new IllegalStateException("boom");
        }
    }

    private static ChatMessage buildToolCallReply(String toolName, String singleQuotedArguments) throws Exception {
        JsonNode arguments = singleQuotedArguments == null
                ? null
                : Json.MAPPER.readTree(singleQuotedArguments.replace('\'', '"'));
        ChatMessage reply = ChatMessage.buildAssistantMessage("");
        reply.toolCalls.add(ToolCall.buildToolCall(toolName, arguments));
        return reply;
    }

    private Agent buildAgent(ChatModel model, Tool... tools) {
        return new Agent(model, new ToolRegistry(List.of(tools)), channel, SYSTEM_PROMPT);
    }

    @Test
    void returnsTextWhenModelAnswersDirectly() {
        ScriptedChatModel model = new ScriptedChatModel().queueReply(ChatMessage.buildAssistantMessage("Hello"));

        assertEquals("Hello", buildAgent(model, new EchoTool(false)).handleUserMessage("Hi"));

        List<ChatMessage> request = model.requests.get(0);
        assertEquals("system", request.get(0).role);
        assertEquals(SYSTEM_PROMPT, request.get(0).content);
        assertEquals("user", request.get(1).role);
        assertEquals("Hi", request.get(1).content);
    }

    @Test
    void runsToolCallAndSendsResultBack() throws Exception {
        ScriptedChatModel model = new ScriptedChatModel()
                .queueReply(buildToolCallReply("echo", "{'text':'hi'}"))
                .queueReply(ChatMessage.buildAssistantMessage("Done"));

        assertEquals("Done", buildAgent(model, new EchoTool(false)).handleUserMessage("Echo hi"));

        List<ChatMessage> second = model.requests.get(1);
        ChatMessage toolResult = second.get(second.size() - 1);
        assertEquals("tool", toolResult.role);
        assertEquals("echo", toolResult.toolName);
        assertEquals("echo:hi", toolResult.content);
        assertEquals("assistant", second.get(second.size() - 2).role);
    }

    @Test
    void unknownToolAndToolExceptionBecomeErrorResults() throws Exception {
        ScriptedChatModel model = new ScriptedChatModel()
                .queueReply(buildToolCallReply("nope", "{}"))
                .queueReply(buildToolCallReply("fail", "{}"))
                .queueReply(ChatMessage.buildAssistantMessage("Sorry"));

        buildAgent(model, new FailingTool()).handleUserMessage("Try");

        List<ChatMessage> second = model.requests.get(1);
        assertEquals("ERROR: unknown tool 'nope'.", second.get(second.size() - 1).content);
        List<ChatMessage> third = model.requests.get(2);
        assertEquals("ERROR: boom", third.get(third.size() - 1).content);
    }

    @Test
    void missingArgumentsAreTreatedAsEmptyObject() throws Exception {
        ScriptedChatModel model = new ScriptedChatModel()
                .queueReply(buildToolCallReply("echo", null))
                .queueReply(ChatMessage.buildAssistantMessage("Ok"));

        buildAgent(model, new EchoTool(false)).handleUserMessage("Echo");

        List<ChatMessage> second = model.requests.get(1);
        assertEquals("echo:", second.get(second.size() - 1).content);
    }

    @Test
    void stopsAfterTenRoundsOfToolCalls() throws Exception {
        ChatMessage loopingReply = buildToolCallReply("echo", "{'text':'again'}");
        List<Integer> calls = new ArrayList<>();
        ChatModel alwaysCallsTools = (messages, tools) -> {
            calls.add(1);
            return loopingReply;
        };

        String reply = buildAgent(alwaysCallsTools, new EchoTool(false)).handleUserMessage("Loop");

        assertEquals(Agent.GIVE_UP_REPLY, reply);
        assertEquals(Agent.MAX_TOOL_ROUNDS, calls.size());
    }

    @Test
    void approvedToolRunsAfterYes() throws Exception {
        EchoTool tool = new EchoTool(true);
        channel.answers.add(true);
        ScriptedChatModel model = new ScriptedChatModel()
                .queueReply(buildToolCallReply("echo", "{'text':'invite'}"))
                .queueReply(ChatMessage.buildAssistantMessage("Sent"));

        buildAgent(model, tool).handleUserMessage("Send it");

        assertEquals(List.of("I will echo invite"), channel.replies);
        assertEquals(List.of(Agent.APPROVAL_QUESTION), channel.questions);
        assertEquals(1, tool.executions);
    }

    @Test
    void declinedToolDoesNotRun() throws Exception {
        EchoTool tool = new EchoTool(true);
        channel.answers.add(false);
        ScriptedChatModel model = new ScriptedChatModel()
                .queueReply(buildToolCallReply("echo", "{'text':'invite'}"))
                .queueReply(ChatMessage.buildAssistantMessage("Not sent"));

        buildAgent(model, tool).handleUserMessage("Send it");

        assertEquals(0, tool.executions);
        List<ChatMessage> second = model.requests.get(1);
        assertEquals(Agent.DECLINED_RESULT, second.get(second.size() - 1).content);
    }

    @Test
    void modelFailureKeepsTheConversation() {
        ScriptedChatModel model = new ScriptedChatModel()
                .queueFailure(new ChatModelException("connection refused"))
                .queueReply(ChatMessage.buildAssistantMessage("Back"));
        Agent agent = buildAgent(model, new EchoTool(false));

        String first = agent.handleUserMessage("Hello?");
        String second = agent.handleUserMessage("Hello again");

        assertTrue(first.startsWith("I cannot reach the model: connection refused"), first);
        assertEquals("Back", second);
        List<ChatMessage> retry = model.requests.get(1);
        assertEquals("Hello?", retry.get(1).content);
        assertEquals("Hello again", retry.get(2).content);
    }

    @Test
    void unexpectedErrorGivesAPlainReplyAndKeepsTheConversation() {
        ScriptedChatModel model = new ScriptedChatModel()
                .queueFailure(new IllegalArgumentException("invalid URI scheme"))
                .queueReply(ChatMessage.buildAssistantMessage("Back"));
        Agent agent = buildAgent(model, new EchoTool(false));

        String first = agent.handleUserMessage("Hello?");
        String second = agent.handleUserMessage("Hello again");

        assertTrue(first.startsWith("Something went wrong: invalid URI scheme."), first);
        assertEquals("Back", second);
    }

    @Test
    void emptyReplyGivesFallbackText() {
        ChatMessage thinkingOnly = ChatMessage.buildAssistantMessage("");
        thinkingOnly.thinking = "The user wants...";
        ScriptedChatModel model = new ScriptedChatModel().queueReply(thinkingOnly);

        assertEquals(Agent.EMPTY_REPLY, buildAgent(model, new EchoTool(false)).handleUserMessage("Hmm"));
    }
}
