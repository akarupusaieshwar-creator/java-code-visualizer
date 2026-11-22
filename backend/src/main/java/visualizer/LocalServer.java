package visualizer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class LocalServer {
    private static final int MAX_SOURCE_BYTES = 256 * 1024;
    private static final int MAX_INPUT_BYTES = 8 * 1024;

    private LocalServer() {}

    public static void main(String[] args) throws Exception {
        Path frontend = Path.of(args.length > 0 ? args[0] : "../frontend").toAbsolutePath().normalize();
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8765;
        if (!Files.isDirectory(frontend)) throw new IllegalArgumentException("Frontend folder not found: " + frontend);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        Set<String> allowedOrigins = Set.of(
                "http://127.0.0.1:" + port,
                "http://localhost:" + port,
                "https://akarupusaieshwar-creator.github.io");
        server.createContext("/", exchange -> handle(exchange, frontend, allowedOrigins));
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("Java Code Visualizer is running at http://127.0.0.1:" + port);
        System.out.println("Keep this window open while using the visualizer. Press Ctrl+C to stop.");
    }

    private static void handle(HttpExchange exchange, Path frontend, Set<String> allowedOrigins) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/api/trace".equals(path)) handleTrace(exchange, allowedOrigins);
        else if ("/api/status".equals(path)) handleStatus(exchange, allowedOrigins);
        else serveFile(exchange, frontend, path);
    }

    private static boolean configureCors(HttpExchange exchange, Set<String> allowedOrigins) throws IOException {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (!allowedOrigins.contains(origin)) {
            sendError(exchange, 403, "This page is not allowed to connect to the local runner.");
            return false;
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Program-Input");
        exchange.getResponseHeaders().set("Access-Control-Allow-Private-Network", "true");
        exchange.getResponseHeaders().set("Vary", "Origin");
        return true;
    }

    private static boolean handlePreflight(HttpExchange exchange) throws IOException {
        if (!"OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) return false;
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
        return true;
    }

    private static void handleStatus(HttpExchange exchange, Set<String> allowedOrigins) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())
                && !"OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use GET to check the local runner.");
            return;
        }
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendRunnerStatus(exchange);
            return;
        }
        if (!configureCors(exchange, allowedOrigins) || handlePreflight(exchange)) return;
        sendRunnerStatus(exchange);
    }

    private static void sendRunnerStatus(HttpExchange exchange) throws IOException {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("ready", true);
        status.put("engine", "JDI");
        sendJson(exchange, 200, status);
    }

    private static void handleTrace(HttpExchange exchange, Set<String> allowedOrigins) throws IOException {
        if (!configureCors(exchange, allowedOrigins) || handlePreflight(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use POST to run a Java program.");
            return;
        }
        byte[] sourceBytes = exchange.getRequestBody().readNBytes(MAX_SOURCE_BYTES + 1);
        if (sourceBytes.length > MAX_SOURCE_BYTES) {
            sendError(exchange, 413, "Code is too large. The current limit is 256 KB.");
            return;
        }
        String source = new String(sourceBytes, StandardCharsets.UTF_8).trim();
        if (source.isEmpty()) {
            sendError(exchange, 400, "Paste a Java program before running it.");
            return;
        }
        if (source.matches("(?s).*\\bpackage\\s+[\\w.]+\\s*;.*")) {
            sendError(exchange, 400, "For now, use a package-free class named Main.");
            return;
        }
        if (!source.matches("(?s).*\\bpublic\\s+class\\s+Main\\b.*")) {
            sendError(exchange, 400, "Add public class Main so the tracer can start your program.");
            return;
        }

        byte[] input;
        try {
            String encoded = exchange.getRequestHeaders().getFirst("X-Program-Input");
            input = encoded == null || encoded.isBlank() ? new byte[0] : Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, "Program input could not be decoded.");
            return;
        }
        if (input.length > MAX_INPUT_BYTES) {
            sendError(exchange, 413, "Program input is too large. The current limit is 8 KB.");
            return;
        }

        Path work = Files.createTempDirectory("java-visualizer-");
        try {
            Path sourceFile = work.resolve("Main.java");
            Path traceFile = work.resolve("trace.json");
            Path inputFile = null;
            Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
            if (input.length > 0) {
                inputFile = work.resolve("input.txt");
                Files.write(inputFile, input);
            }
            new Tracer().run(sourceFile.toString(), traceFile.toString(),
                    inputFile == null ? null : inputFile.toString());
            send(exchange, 200, "application/json; charset=utf-8", Files.readAllBytes(traceFile));
        } catch (RuntimeException e) {
            sendError(exchange, 422, e.getMessage() == null ? "Could not trace this program." : e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, "The local tracer could not finish this program.");
            e.printStackTrace(System.err);
        } finally {
            deleteTree(work);
        }
    }

    private static void serveFile(HttpExchange exchange, Path frontend, String requestPath) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use GET to open the visualizer.");
            return;
        }
        String relative = requestPath.equals("/") ? "index.html" : requestPath.substring(1);
        Path file = frontend.resolve(relative).normalize();
        if (!file.startsWith(frontend)) {
            sendError(exchange, 403, "That file is outside the frontend folder.");
            return;
        }
        if (Files.isDirectory(file)) file = file.resolve("index.html");
        if (!Files.isRegularFile(file)) {
            sendError(exchange, 404, "Page not found.");
            return;
        }
        String name = file.getFileName().toString().toLowerCase();
        String type = name.endsWith(".html") ? "text/html; charset=utf-8"
                : name.endsWith(".css") ? "text/css; charset=utf-8"
                : name.endsWith(".js") ? "text/javascript; charset=utf-8"
                : name.endsWith(".json") ? "application/json; charset=utf-8"
                : "application/octet-stream";
        send(exchange, 200, type, Files.readAllBytes(file));
    }

    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        sendJson(exchange, status, body);
    }

    private static void sendJson(HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
        StringWriter writer = new StringWriter();
        Json.write(body, writer);
        send(exchange, status, "application/json; charset=utf-8",
                writer.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void deleteTree(Path root) {
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
