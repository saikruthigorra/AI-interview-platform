package com.interviewiq.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.interviewiq.config.AppProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Minimal client for any OpenAI-compatible {@code /chat/completions} endpoint
 * (OpenAI, Azure OpenAI, Groq, Together, OpenRouter, local Ollama, …).
 * Only used when APP_AI_API_KEY is configured; otherwise the app stays offline.
 */
@Component
public class LlmClient {

    private final AppProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public LlmClient(AppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String chat(String systemPrompt, String userPrompt) {
        AppProperties.Ai ai = props.getAi();
        String base = ai.getBaseUrl() == null ? "" : ai.getBaseUrl().replaceAll("/+$", "");

        ObjectNode root = mapper.createObjectNode();
        root.put("model", ai.getModel());
        root.put("temperature", 0.3);
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);

        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/chat/completions"))
                .timeout(Duration.ofSeconds(Math.max(5, ai.getTimeoutSeconds())))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + ai.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(root.toString()))
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("LLM endpoint returned HTTP " + response.statusCode()
                        + ": " + abbreviate(response.body()));
            }
            JsonNode json = mapper.readTree(response.body());
            String content = json.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new IllegalStateException("LLM returned an empty completion");
            }
            return content;
        } catch (IOException ex) {
            throw new IllegalStateException("LLM call failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM call interrupted", ex);
        }
    }

    /** Models sometimes wrap JSON in prose or code fences — dig the object/array out. */
    public static String extractJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        String cleaned = raw.replace("```json", "```").trim();
        int fence = cleaned.indexOf("```");
        if (fence >= 0) {
            int end = cleaned.indexOf("```", fence + 3);
            cleaned = end > fence ? cleaned.substring(fence + 3, end) : cleaned.substring(fence + 3);
        }
        int start = indexOfFirst(cleaned, '{', '[');
        int stop = Math.max(cleaned.lastIndexOf('}'), cleaned.lastIndexOf(']'));
        if (start >= 0 && stop > start) {
            return cleaned.substring(start, stop + 1);
        }
        return cleaned.trim();
    }

    private static int indexOfFirst(String s, char a, char b) {
        int ia = s.indexOf(a);
        int ib = s.indexOf(b);
        if (ia < 0) {
            return ib;
        }
        if (ib < 0) {
            return ia;
        }
        return Math.min(ia, ib);
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 240 ? s : s.substring(0, 240) + "…";
    }
}
