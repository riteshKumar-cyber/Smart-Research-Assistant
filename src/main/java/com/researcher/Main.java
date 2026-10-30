package com.researcher;

import java.io.PrintStream;
import java.net.BindException;
import java.nio.charset.StandardCharsets;

/**
 * Main entry point that launches the Smart Research Web Server.
 */
public class Main {

    private static final String DEFAULT_API_KEY = "YOUR_GEMINI_API_KEY";
    private static final int[] PREFERRED_PORTS = {8080, 8081, 8082, 8085, 9000};

    public static void main(String[] args) {
        try {
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8.name()));
        } catch (Exception ignored) {}

        String apiKey = System.getenv("API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("GEMINI_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = DEFAULT_API_KEY;
        }

        ResearchApiClient apiClient = new ResearchApiClient(apiKey);

        boolean started = false;
        for (int port : PREFERRED_PORTS) {
            try {
                WebServer server = new WebServer(apiClient, port);
                server.start();
                started = true;
                Thread.currentThread().join();
                break;
            } catch (BindException e) {
                continue;
            } catch (Exception e) {
                System.out.println("Error starting server: " + e.getMessage());
                break;
            }
        }

        if (!started) {
            System.out.println("==================================================");
            System.out.println(" ⚠️ Server is already RUNNING!");
            System.out.println(" 👉 Open Browser: http://localhost:8080");
            System.out.println("==================================================");
        }
    }
}
