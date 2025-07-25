import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class CertificateHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            // Read request body
            InputStream is = exchange.getRequestBody();
            String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(requestBody);
            
            try {
                // Generate certificate
                JSONObject certificate = CertificateGenerator.generateCertificate(
                    json.getString("participantName"),
                    json.getString("courseName"),
                    json.getString("completionDate"),
                    json.getString("issuerName")
                );
                
                // Send response
                String response = certificate.toString();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
                String errorResponse = "{\"error\":\"Failed to generate certificate\"}";
                exchange.sendResponseHeaders(500, errorResponse.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(errorResponse.getBytes());
                os.close();
            }
        } else {
            exchange.sendResponseHeaders(405, -1); // Method Not Allowed
        }
    }
}