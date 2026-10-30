package com.researcher;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Embedded Java Web Server that serves the research portal web interface and API.
 */
public class WebServer {

    private final ResearchApiClient apiClient;
    private final int port;
    private static final Gson gson = new Gson();

    public WebServer(ResearchApiClient apiClient, int port) {
        this.apiClient = apiClient;
        this.port = port;
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/api/chat", new ChatHandler(apiClient));
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("==================================================");
        System.out.println(" 🌐 Smart Research Portal Server Started!");
        System.out.println(" 👉 Open Web Portal in Browser: http://localhost:" + port);
        System.out.println("==================================================");
    }

    static class ChatHandler implements HttpHandler {
        private final ResearchApiClient client;

        public ChatHandler(ResearchApiClient client) {
            this.client = client;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            try {
                InputStream is = exchange.getRequestBody();
                String bodyStr = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject json = JsonParser.parseString(bodyStr).getAsJsonObject();

                if (!json.has("prompt") || json.get("prompt").getAsString().isBlank()) {
                    sendJsonResponse(exchange, 400, "{\"error\":\"Prompt is required\"}");
                    return;
                }

                String userQuery = json.get("prompt").getAsString().trim();
                String summaryResponse = client.getSmartSummary(userQuery);

                JsonObject resJson = new JsonObject();
                resJson.addProperty("status", "success");
                resJson.addProperty("response", summaryResponse);

                sendJsonResponse(exchange, 200, gson.toJson(resJson));

            } catch (Exception e) {
                JsonObject errJson = new JsonObject();
                errJson.addProperty("status", "error");
                errJson.addProperty("error", e.getMessage() != null ? e.getMessage() : "Unknown error");
                sendJsonResponse(exchange, 500, gson.toJson(errJson));
            }
        }

        private void sendJsonResponse(HttpExchange exchange, int statusCode, String responseJson) throws IOException {
            byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            InputStream is = WebServer.class.getResourceAsStream("/index.html");
            if (is == null) {
                String notFound = "<h1>index.html not found in classpath</h1>";
                exchange.sendResponseHeaders(404, notFound.length());
                OutputStream os = exchange.getResponseBody();
                os.write(notFound.getBytes(StandardCharsets.UTF_8));
                os.close();
                return;
            }

            byte[] responseBytes = is.readAllBytes();
            is.close();

            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        }
    }
}
