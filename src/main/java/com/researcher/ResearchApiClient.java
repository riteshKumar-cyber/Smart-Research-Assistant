package com.researcher;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance client for AI search and summarization with model fallbacks,
 * intelligent caching, and optimized token generation for rapid analysis.
 */
public class ResearchApiClient {

    // Valid, ultra-fast production models in priority order (no bogus 404 models)
    private static final String[] CANDIDATE_MODELS = {
            "gemini-1.5-flash",
            "gemini-flash-latest",
            "gemini-2.0-flash"
    };

    private static final String INSTRUCTION_PROMPT =
            "You are an ultra-fast, high-precision research engine. " +
            "Analyze the query rapidly and deliver direct, impactful insights: " +
            "1) Jump straight into facts and findings without conversational fluff. " +
            "2) Use crisp bullet points (with '-') with key terms in bold. " +
            "3) Include exact numbers, dates, benchmarks, and verifiable details. " +
            "4) Keep the analysis concise, structured, and fast to read.";

    private final String apiKey;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    private final List<JsonObject> history = new ArrayList<>();
    // Fast in-memory cache to return instant results for repeated/related queries
    private final Map<String, String> resultCache = new ConcurrentHashMap<>();

    public ResearchApiClient(String apiKey) {
        this.apiKey = apiKey.trim();
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public String getSmartSummary(String userQuery) throws Exception {
        String cacheKey = userQuery.trim().toLowerCase();
        if (resultCache.containsKey(cacheKey)) {
            return resultCache.get(cacheKey);
        }

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", userQuery);
        parts.add(part);
        userMsg.add("parts", parts);
        history.add(userMsg);

        // Keep history bounded to 4 messages (last 2 turns) to keep payload small and processing ultra-fast
        if (history.size() > 4) {
            history.subList(0, history.size() - 4).clear();
        }

        String summary = executeWithFastFallback();
        
        // Cache result (capped to 100 entries to prevent memory growth)
        if (resultCache.size() > 100) {
            resultCache.clear();
        }
        resultCache.put(cacheKey, summary);

        return summary;
    }

    private String executeWithFastFallback() throws Exception {
        Exception lastException = null;

        for (String model : CANDIDATE_MODELS) {
            try {
                return executeQuery(model);
            } catch (Exception e) {
                lastException = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";

                // For transient 503/429, perform a single quick retry with minimal wait
                if (msg.contains("503") || msg.contains("429") || msg.contains("500")) {
                    try {
                        Thread.sleep(300L);
                        return executeQuery(model);
                    } catch (Exception retryEx) {
                        lastException = retryEx;
                    }
                }
                // Swiftly proceed to candidate fallback model
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        throw new RuntimeException("Service is currently experiencing high demand. Please retry in a moment.");
    }

    private String executeQuery(String modelName) throws Exception {
        JsonObject body = new JsonObject();

        JsonObject sysInstruction = new JsonObject();
        JsonArray sysParts = new JsonArray();
        JsonObject sysPart = new JsonObject();
        sysPart.addProperty("text", INSTRUCTION_PROMPT);
        sysParts.add(sysPart);
        sysInstruction.add("parts", sysParts);
        body.add("systemInstruction", sysInstruction);

        JsonArray contents = new JsonArray();
        for (JsonObject msg : history) {
            contents.add(msg);
        }
        body.add("contents", contents);

        JsonObject genConfig = new JsonObject();
        genConfig.addProperty("temperature", 0.2);
        genConfig.addProperty("maxOutputTokens", 1024);
        body.add("generationConfig", genConfig);

        String targetUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();

        if (response.statusCode() != 200) {
            if (responseJson.has("error")) {
                JsonObject err = responseJson.getAsJsonObject("error");
                String errMsg = err.has("message") ? err.get("message").getAsString() : response.body();
                throw new RuntimeException("Service Error (" + response.statusCode() + "): " + errMsg);
            }
            throw new RuntimeException("Service Error (" + response.statusCode() + "): " + response.body());
        }

        if (responseJson.has("candidates")) {
            JsonArray candidates = responseJson.getAsJsonArray("candidates");
            if (candidates.size() > 0) {
                JsonObject candidate = candidates.get(0).getAsJsonObject();
                if (candidate.has("content")) {
                    JsonObject contentObj = candidate.getAsJsonObject("content");
                    if (contentObj.has("parts")) {
                        JsonArray respParts = contentObj.getAsJsonArray("parts");
                        StringBuilder textBuilder = new StringBuilder();
                        for (JsonElement p : respParts) {
                            JsonObject pObj = p.getAsJsonObject();
                            if (pObj.has("text")) {
                                textBuilder.append(pObj.get("text").getAsString());
                            }
                        }

                        String resultText = textBuilder.toString().trim();

                        JsonObject modelMsg = new JsonObject();
                        modelMsg.addProperty("role", "model");
                        JsonArray modelParts = new JsonArray();
                        JsonObject modelPart = new JsonObject();
                        modelPart.addProperty("text", resultText);
                        modelParts.add(modelPart);
                        modelMsg.add("parts", modelParts);
                        history.add(modelMsg);

                        return resultText;
                    }
                }
            }
        }

        throw new RuntimeException("No summary generated from response.");
    }
}
