import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import com.google.gson.*;
import java.security.*;
import java.time.*;
import java.time.format.*;

public class CertiAIServer {
    private static final Logger logger = Logger.getLogger(CertiAIServer.class.getName());
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final String API_KEY = System.getenv("CERTIAI_API_KEY");
    private static final int MAX_REQUEST_SIZE = 1024 * 1024; // 1MB
    private static final Map<String, Certificate> certificateCache = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try {
            int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
            new CertiAIServer().start(port);
        } catch (Exception e) {
            logger.severe("Server failed to start: " + e.getMessage());
            System.exit(1);
        }
    }

    public void start(int port) throws IOException {
        // Configure logging
        setupLogging();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Create context handlers with middleware
        server.createContext("/api/generate", new AuthHandler(new CertificateGenerationHandler()));
        server.createContext("/api/verify/", new AuthHandler(new CertificateVerificationHandler()));
        server.createContext("/api/download/", new AuthHandler(new CertificateDownloadHandler()));
        server.createContext("/api/health", new HealthHandler());

        // Add global middleware
        server.setMiddleware(exchange -> {
            exchange.getResponseHeaders().add("X-Powered-By", "CertiAI");
            exchange.getResponseHeaders().add("Content-Type", "application/json");
        });

        // Configure thread pool
        ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
        server.setExecutor(threadPoolExecutor);

        server.start();
        logger.info("CertiAI Server running on port " + port);
        logger.info("Available endpoints:");
        logger.info("- POST /api/generate");
        logger.info("- GET /api/verify/{id}");
        logger.info("- GET /api/download/{id}");
        logger.info("- GET /api/health");
    }

    private void setupLogging() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.INFO);
        
        // Remove default console handler
        for (Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }

        // Add file handler
        try {
            FileHandler fileHandler = new FileHandler("certiai-server.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            rootLogger.addHandler(fileHandler);
        } catch (IOException e) {
            logger.warning("Failed to setup file logging: " + e.getMessage());
        }

        // Add console handler with better formatting
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new SimpleFormatter() {
            private static final String format = "[%1$tF %1$tT] [%2$-7s] %3$s %n";

            @Override
            public synchronized String format(LogRecord record) {
                return String.format(format,
                        new Date(record.getMillis()),
                        record.getLevel().getLocalizedName(),
                        record.getMessage()
                );
            }
        });
        rootLogger.addHandler(consoleHandler);
    }

    // Middleware for authentication
    static class AuthHandler implements HttpHandler {
        private final HttpHandler next;

        AuthHandler(HttpHandler next) {
            this.next = next;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!authenticate(exchange)) {
                sendResponse(exchange, 401, gson.toJson(Map.of(
                        "error", "Unauthorized",
                        "timestamp", Instant.now().toString()
                )));
                return;
            }
            next.handle(exchange);
        }

        private boolean authenticate(HttpExchange exchange) {
            if (API_KEY == null || API_KEY.isEmpty()) {
                return true; // No auth required if no API key set
            }

            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            return authHeader != null && authHeader.equals("Bearer " + API_KEY);
        }
    }

    // Health check handler
    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "healthy");
            response.put("timestamp", Instant.now().toString());
            response.put("version", "1.0.0");
            response.put("system", System.getProperty("os.name"));
            
            sendResponse(exchange, 200, gson.toJson(response));
        }
    }

    // Certificate generation handler
    static class CertificateGenerationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, gson.toJson(Map.of(
                        "error", "Method not allowed",
                        "allowed_methods", List.of("POST")
                )));
                return;
            }

            try {
                // Read and validate request
                String requestBody = readRequestBody(exchange);
                CertificateRequest request = gson.fromJson(requestBody, CertificateRequest.class);
                
                if (request == null || !request.isValid()) {
                    sendResponse(exchange, 400, gson.toJson(Map.of(
                            "error", "Invalid request",
                            "required_fields", List.of("participantName", "courseName", "completionDate", "issuerName")
                    )));
                    return;
                }

                // Generate certificate
                Certificate certificate = generateCertificate(request);
                certificateCache.put(certificate.id, certificate);

                // Prepare response
                CertificateResponse response = new CertificateResponse(
                        certificate.id,
                        "Certificate generated successfully",
                        "/api/download/" + certificate.id,
                        "/api/verify/" + certificate.id,
                        Instant.now().plus(365, ChronoUnit.DAYS) // 1 year validity
                );

                sendResponse(exchange, 201, gson.toJson(response));
                logger.info("Generated certificate: " + certificate.id);

            } catch (JsonSyntaxException e) {
                sendResponse(exchange, 400, gson.toJson(Map.of(
                        "error", "Invalid JSON format",
                        "details", e.getMessage()
                )));
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Certificate generation failed", e);
                sendResponse(exchange, 500, gson.toJson(Map.of(
                        "error", "Internal server error",
                        "request_id", UUID.randomUUID().toString()
                )));
            }
        }

        private Certificate generateCertificate(CertificateRequest request) throws NoSuchAlgorithmException {
            String id = UUID.randomUUID().toString();
            String qrCode = generateQRCode(id);
            String pdfContent = generatePdfContent(request);
            String pngContent = generatePngContent(request);

            return new Certificate(
                    id,
                    request.participantName,
                    request.courseName,
                    request.completionDate,
                    request.issuerName,
                    qrCode,
                    pdfContent,
                    pngContent,
                    Instant.now()
            );
        }

        private String generateQRCode(String id) {
            // In a real implementation, generate actual QR code
            return "QR_CODE_" + id;
        }

        private String generatePdfContent(CertificateRequest request) {
            // In a real implementation, generate actual PDF
            return "PDF_CONTENT_" + request.hashCode();
        }

        private String generatePngContent(CertificateRequest request) {
            // In a real implementation, generate actual PNG
            return "PNG_CONTENT_" + request.hashCode();
        }
    }

    // Certificate verification handler
    static class CertificateVerificationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, gson.toJson(Map.of(
                        "error", "Method not allowed",
                        "allowed_methods", List.of("GET")
                )));
                return;
            }

            try {
                String path = exchange.getRequestURI().getPath();
                String id = path.substring("/api/verify/".length());

                Certificate certificate = certificateCache.get(id);
                if (certificate == null) {
                    sendResponse(exchange, 404, gson.toJson(Map.of(
                            "error", "Certificate not found",
                            "id", id
                    )));
                    return;
                }

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("id", certificate.id);
                response.put("participantName", certificate.participantName);
                response.put("courseName", certificate.courseName);
                response.put("completionDate", certificate.completionDate);
                response.put("issuerName", certificate.issuerName);
                response.put("issueDate", certificate.issueDate.toString());
                response.put("valid", true);
                response.put("verificationDate", Instant.now().toString());

                sendResponse(exchange, 200, gson.toJson(response));
                logger.info("Verified certificate: " + id);

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Certificate verification failed", e);
                sendResponse(exchange, 500, gson.toJson(Map.of(
                        "error", "Internal server error",
                        "request_id", UUID.randomUUID().toString()
                )));
            }
        }
    }

    // Certificate download handler
    static class CertificateDownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, gson.toJson(Map.of(
                        "error", "Method not allowed",
                        "allowed_methods", List.of("GET")
                )));
                return;
            }

            try {
                String path = exchange.getRequestURI().getPath();
                String id = path.substring("/api/download/".length());
                String format = getQueryParam(exchange, "format", "pdf");

                Certificate certificate = certificateCache.get(id);
                if (certificate == null) {
                    sendResponse(exchange, 404, gson.toJson(Map.of(
                            "error", "Certificate not found",
                            "id", id
                    )));
                    return;
                }

                String content;
                String contentType;
                String fileName = "certificate_" + id;

                switch (format.toLowerCase()) {
                    case "pdf":
                        content = certificate.pdfContent;
                        contentType = "application/pdf";
                        fileName += ".pdf";
                        break;
                    case "png":
                        content = certificate.pngContent;
                        contentType = "image/png";
                        fileName += ".png";
                        break;
                    default:
                        sendResponse(exchange, 400, gson.toJson(Map.of(
                                "error", "Invalid format",
                                "supported_formats", List.of("pdf", "png")
                        )));
                        return;
                }

                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
                exchange.sendResponseHeaders(200, content.length());

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(content.getBytes());
                }

                logger.info("Downloaded certificate: " + id + " as " + format);

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Certificate download failed", e);
                sendResponse(exchange, 500, gson.toJson(Map.of(
                        "error", "Internal server error",
                        "request_id", UUID.randomUUID().toString()
                )));
            }
        }

        private String getQueryParam(HttpExchange exchange, String name, String defaultValue) {
            String query = exchange.getRequestURI().getQuery();
            if (query == null) return defaultValue;
            
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length > 0 && pair[0].equals(name)) {
                    return pair.length > 1 ? pair[1] : defaultValue;
                }
            }
            return defaultValue;
        }
    }

    // Helper methods
    private static String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int bytesRead;
        int totalBytes = 0;

        while ((bytesRead = is.read(buffer)) != -1) {
            bos.write(buffer, 0, bytesRead);
            totalBytes += bytesRead;
            
            if (totalBytes > MAX_REQUEST_SIZE) {
                throw new IOException("Request size exceeds limit of " + MAX_REQUEST_SIZE + " bytes");
            }
        }

        return bos.toString(StandardCharsets.UTF_8);
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.sendResponseHeaders(statusCode, response.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    // Data classes
    static class CertificateRequest {
        String participantName;
        String courseName;
        String completionDate;
        String issuerName;
        String additionalInfo;

        boolean isValid() {
            return participantName != null && !participantName.isEmpty()
                    && courseName != null && !courseName.isEmpty()
                    && completionDate != null && !completionDate.isEmpty()
                    && issuerName != null && !issuerName.isEmpty();
        }
    }

    static class CertificateResponse {
        String id;
        String message;
        String downloadUrl;
        String verifyUrl;
        Instant validUntil;

        CertificateResponse(String id, String message, String downloadUrl, String verifyUrl, Instant validUntil) {
            this.id = id;
            this.message = message;
            this.downloadUrl = downloadUrl;
            this.verifyUrl = verifyUrl;
            this.validUntil = validUntil;
        }
    }

    static class Certificate {
        String id;
        String participantName;
        String courseName;
        String completionDate;
        String issuerName;
        String qrCode;
        String pdfContent;
        String pngContent;
        Instant issueDate;

        Certificate(String id, String participantName, String courseName, String completionDate, 
                   String issuerName, String qrCode, String pdfContent, String pngContent, Instant issueDate) {
            this.id = id;
            this.participantName = participantName;
            this.courseName = courseName;
            this.completionDate = completionDate;
            this.issuerName = issuerName;
            this.qrCode = qrCode;
            this.pdfContent = pdfContent;
            this.pngContent = pngContent;
            this.issueDate = issueDate;
        }
    }
}