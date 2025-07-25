import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

public class DownloadHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            String path = exchange.getRequestURI().getPath();
            String certificateId = path.substring("/download/".length());
            
            // Get format from query parameters
            Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
            String format = params.getOrDefault("format", "pdf");
            
            try {
                String filename = "certificates/" + certificateId + "." + format;
                File file = new File(filename);
                
                if (file.exists()) {
                    byte[] fileBytes = Files.readAllBytes(file.toPath());
                    
                    exchange.getResponseHeaders().set("Content-Type", getContentType(format));
                    exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=certificate_" + certificateId + "." + format);
                    exchange.sendResponseHeaders(200, fileBytes.length);
                    
                    OutputStream os = exchange.getResponseBody();
                    os.write(fileBytes);
                    os.close();
                } else {
                    String response = "File not found";
                    exchange.sendResponseHeaders(404, response.getBytes().length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                String errorResponse = "Failed to download file";
                exchange.sendResponseHeaders(500, errorResponse.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(errorResponse.getBytes());
                os.close();
            }
        } else {
            exchange.sendResponseHeaders(405, -1); // Method Not Allowed
        }
    }
    
    private String getContentType(String format) {
        switch (format.toLowerCase()) {
            case "pdf": return "application/pdf";
            case "png": return "image/png";
            default: return "application/octet-stream";
        }
    }
    
    private Map<String, String> queryToMap(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null) return result;
        
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length > 1) {
                result.put(pair[0], URLDecoder.decode(pair[1]));
            } else {
                result.put(pair[0], "");
            }
        }
        return result;
    }
}