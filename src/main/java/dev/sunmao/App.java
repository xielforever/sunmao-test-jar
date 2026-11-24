package dev.sunmao;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.Executors;

public class App {
    private static final String SERVICE_NAME = "sunmao-test-jar";

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--self-test".equals(args[0])) {
            runSelfTest();
            return;
        }

        int port = Integer.parseInt(envOr("PORT", "8080"));
        HttpServer server = startServer("0.0.0.0", port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1)));
        System.out.printf("%s v%s listening on :%d%n", SERVICE_NAME, envOr("APP_VERSION", "1.0.0"), port);
    }

    static HttpServer startServer(String host, int port) throws IOException {
        String version = envOr("APP_VERSION", "1.0.0");
        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/healthz", exchange -> {
            String body = String.format(
                "{\"status\":\"ok\",\"service\":\"%s\",\"version\":\"%s\"}%n",
                SERVICE_NAME, escapeJson(version)
            );
            sendJson(exchange, 200, body);
        });

        server.createContext("/api/info", exchange -> sendInfo(exchange, version));
        server.createContext("/", exchange -> {
            if (!"/".equals(exchange.getRequestURI().getPath())) {
                sendJson(exchange, 404, "{\"error\":\"not found\"}\n");
                return;
            }
            sendInfo(exchange, version);
        });

        server.start();
        return server;
    }

    private static void sendInfo(HttpExchange exchange, String version) throws IOException {
        String hostname = resolveHostname();
        String body = String.format(
            "{\"service\":\"%s\",\"version\":\"%s\",\"java_version\":\"%s\",\"hostname\":\"%s\",\"timestamp\":\"%s\"}%n",
            SERVICE_NAME,
            escapeJson(version),
            escapeJson(System.getProperty("java.version")),
            escapeJson(hostname),
            Instant.now().toString()
        );
        sendJson(exchange, 200, body);
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void runSelfTest() throws Exception {
        HttpServer server = startServer("127.0.0.1", 0);
        try {
            int port = server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> health = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/healthz")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
            if (health.statusCode() != 200 || !health.body().contains("\"status\":\"ok\"")) {
                throw new AssertionError("Unexpected /healthz response: " + health.statusCode() + " " + health.body());
            }
            HttpResponse<String> info = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/info")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
            if (info.statusCode() != 200 || !info.body().contains(SERVICE_NAME)) {
                throw new AssertionError("Unexpected /api/info response: " + info.statusCode() + " " + info.body());
            }
            System.out.println("sunmao-test-jar self-test passed");
        } finally {
            server.stop(0);
        }
    }

    private static String envOr(String key, String fallback) {
        String value = System.getenv(key);
        return (value != null && !value.isEmpty()) ? value : fallback;
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
