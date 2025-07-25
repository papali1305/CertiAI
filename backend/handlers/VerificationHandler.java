import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class VerificationHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            String path = exchange.getRequestURI().getPath();
            String certificateId = path.substring("/verify/".length());
            
            try {
                JSONObject certificate = CertificateGenerator.getCertificate(certificateId);
                JSONObject response = new JSONObject();
                
                if (certificate != null) {
                    response.put("valid", true);
                    response.put("certificate", certificate);
                } else {
                    response.put("valid", false);
                    response.put("message", "Certificate not found");
                }
                
                String responseStr = response.toString();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseStr.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseStr.getBytes());
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
                String errorResponse = "{\"error\":\"Failed to verify certificate\"}";
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