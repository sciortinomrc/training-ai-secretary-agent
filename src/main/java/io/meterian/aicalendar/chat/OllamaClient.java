package io.meterian.aicalendar.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.meterian.aicalendar.Json;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/** Calls POST {baseUri}/api/chat of the Ollama native API, without streaming. */
public class OllamaClient implements ChatModel {

    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);
    private static final int HTTP_OK = 200;

    private final HttpClient httpClient;
    private final URI chatUri;
    private final String model;

    public OllamaClient(HttpClient httpClient, URI baseUri, String model) {
        this.httpClient = httpClient;
        this.chatUri = baseUri.resolve("/api/chat");
        this.model = model;
    }

    @Override
    public ChatMessage requestReply(List<ChatMessage> messages, List<JsonNode> tools) {
        HttpResponse<String> response = sendRequest(buildChatRequest(messages, tools));
        if (response.statusCode() != HTTP_OK) {
            throw new ChatModelException("Ollama returned HTTP " + response.statusCode() + ": " + response.body());
        }
        return parseReplyMessage(response.body());
    }

    private HttpRequest buildChatRequest(List<ChatMessage> messages, List<JsonNode> tools) {
        ObjectNode body = Json.MAPPER.createObjectNode();
        body.put("model", model);
        body.set("messages", Json.MAPPER.valueToTree(messages));
        body.set("tools", Json.MAPPER.valueToTree(tools));
        body.put("stream", false);
        return HttpRequest.newBuilder(chatUri)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.writeJson(body)))
                .build();
    }

    private HttpResponse<String> sendRequest(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ChatModelException("Cannot reach Ollama at " + chatUri + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChatModelException("Interrupted while waiting for Ollama.", e);
        }
    }

    private static ChatMessage parseReplyMessage(String responseBody) {
        try {
            JsonNode message = Json.MAPPER.readTree(responseBody).path("message");
            if (message.isMissingNode()) {
                throw new ChatModelException("Ollama reply has no message: " + responseBody);
            }
            return Json.MAPPER.treeToValue(message, ChatMessage.class);
        } catch (IOException e) {
            throw new ChatModelException("Ollama sent a reply that is not valid JSON: " + responseBody, e);
        }
    }
}
