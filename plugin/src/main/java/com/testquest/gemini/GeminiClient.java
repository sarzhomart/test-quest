package com.testquest.gemini;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

public final class GeminiClient {
    private static final Gson GSON = new Gson();
    private final HttpClient httpClient;

    public GeminiClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build());
    }

    GeminiClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public GeneratedTaskBatch generate(
            String apiKey,
            String model,
            String systemInstruction,
            String userPrompt,
            JsonObject schema,
            Path projectRoot
    ) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Gemini API key is not configured");
        }

        JsonObject requestBody = new JsonObject();
        requestBody.add("systemInstruction", content(systemInstruction));
        JsonArray contents = new JsonArray();
        JsonObject user = content(userPrompt);
        user.addProperty("role", "user");
        contents.add(user);
        requestBody.add("contents", contents);

        JsonObject generation = new JsonObject();
        generation.addProperty("temperature", 0.2);
        generation.addProperty("maxOutputTokens", 8_192);
        generation.addProperty("responseMimeType", "application/json");
        generation.add("responseJsonSchema", schema);
        requestBody.add("generationConfig", generation);

        LlmExchangeRepository exchange = new LlmExchangeRepository(projectRoot);
        exchange.save("request.json", GSON.toJson(requestBody));

        String encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8)
                .replace("+", "%20");
        URI endpoint = URI.create(
                "https://generativelanguage.googleapis.com/v1beta/models/"
                        + encodedModel + ":generateContent"
        );
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey.trim())
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
        exchange.save("response.json", response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Gemini API returned HTTP " + response.statusCode() + ": "
                            + safeError(response.body())
            );
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray candidates = root.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini returned no candidates");
        }
        JsonArray parts = candidates.get(0).getAsJsonObject()
                .getAsJsonObject("content")
                .getAsJsonArray("parts");
        if (parts == null || parts.isEmpty() || !parts.get(0).getAsJsonObject().has("text")) {
            throw new IllegalStateException("Gemini response has no text payload");
        }
        String json = parts.get(0).getAsJsonObject().get("text").getAsString();
        exchange.save("generated-tasks.json", json);
        return GSON.fromJson(json, GeneratedTaskBatch.class);
    }

    private static JsonObject content(String text) {
        JsonObject part = new JsonObject();
        part.addProperty("text", text);
        JsonArray parts = new JsonArray();
        parts.add(part);
        JsonObject content = new JsonObject();
        content.add("parts", parts);
        return content;
    }

    private static String safeError(String body) {
        if (body == null) {
            return "";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        return compact.substring(0, Math.min(compact.length(), 500));
    }
}
