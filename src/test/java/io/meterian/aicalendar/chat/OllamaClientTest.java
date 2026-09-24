package io.meterian.aicalendar.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import io.meterian.aicalendar.Json;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OllamaClientTest {

    private HttpServer server;
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private volatile int replyStatus = 200;
    private volatile String replyBody = "{}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = replyBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(replyStatus, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private OllamaClient buildClient() {
        return new OllamaClient(HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "test-model");
    }

    private static List<JsonNode> buildOneToolDefinition() {
        ObjectNode function = Json.MAPPER.createObjectNode();
        function.put("name", "list-day");
        ObjectNode definition = Json.MAPPER.createObjectNode();
        definition.put("type", "function");
        definition.set("function", function);
        return List.of(definition);
    }

    @Test
    void sendsModelMessagesToolsAndNoStreaming() throws Exception {
        replyBody = "{\"model\":\"test-model\",\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"},\"done\":true}";

        ChatMessage reply = buildClient().requestReply(
                List.of(ChatMessage.buildUserMessage("Hi")), buildOneToolDefinition());

        assertEquals("Hello", reply.content);
        assertFalse(reply.hasToolCalls());
        JsonNode request = Json.MAPPER.readTree(receivedBody.get());
        assertEquals("test-model", request.get("model").asText());
        assertFalse(request.get("stream").asBoolean());
        assertEquals("user", request.get("messages").get(0).get("role").asText());
        assertEquals("Hi", request.get("messages").get(0).get("content").asText());
        assertEquals("list-day", request.get("tools").get(0).get("function").get("name").asText());
    }

    @Test
    void parsesToolCalls() {
        replyBody = "{\"message\":{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":[{\"function\":"
                + "{\"name\":\"list-day\",\"arguments\":{\"date\":\"2026-09-28\"}}}]},\"done\":true}";

        ChatMessage reply = buildClient().requestReply(List.of(ChatMessage.buildUserMessage("Monday?")), List.of());

        assertTrue(reply.hasToolCalls());
        assertEquals("list-day", reply.toolCalls.get(0).function.name);
        assertEquals("2026-09-28", reply.toolCalls.get(0).function.arguments.get("date").asText());
    }

    @Test
    void toolResultMessageUsesOllamaFieldNames() {
        JsonNode json = Json.MAPPER.valueToTree(ChatMessage.buildToolResultMessage("list-day", "[]"));

        assertEquals("tool", json.get("role").asText());
        assertEquals("list-day", json.get("tool_name").asText());
        assertEquals("[]", json.get("content").asText());
    }

    @Test
    void httpErrorThrowsWithStatusAndBody() {
        replyStatus = 500;
        replyBody = "model not found";

        ChatModelException error = assertThrows(ChatModelException.class,
                () -> buildClient().requestReply(List.of(ChatMessage.buildUserMessage("Hi")), List.of()));

        assertTrue(error.getMessage().contains("HTTP 500"));
        assertTrue(error.getMessage().contains("model not found"));
    }

    @Test
    void unreachableServerThrows() {
        OllamaClient client = buildClient();
        server.stop(0);

        assertThrows(ChatModelException.class,
                () -> client.requestReply(List.of(ChatMessage.buildUserMessage("Hi")), List.of()));
    }
}
