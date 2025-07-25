import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AdvancedCertificateGenerator {
    private static final Logger logger = Logger.getLogger(AdvancedCertificateGenerator.class.getName());
    private static final String CERTIFICATES_DIR = "certificates/";
    private static final Map<String, CertificateMetadata> certificateCache = new ConcurrentHashMap<>();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int QR_CODE_SIZE = 300;
    
    static {
        initializeCertificateDirectory();
    }
    
    private static void initializeCertificateDirectory() {
        try {
            Path path = Paths.get(CERTIFICATES_DIR);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                logger.info("Created certificates directory: " + path.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to create certificates directory", e);
            throw new RuntimeException("Certificate directory initialization failed", e);
        }
    }
    
    public static CertificateGenerationResult generateCertificate(CertificateRequest request) 
            throws CertificateGenerationException {
        try {
            validateCertificateRequest(request);
            
            String certificateId = UUID.randomUUID().toString();
            LocalDate issueDate = LocalDate.now();
            
            // Generate QR code
            String verificationUrl = buildVerificationUrl(certificateId);
            byte[] qrCodeImage = generateQRCodeImage(verificationUrl);
            String qrCodeBase64 = Base64.getEncoder().encodeToString(qrCodeImage);
            
            // Create certificate metadata
            CertificateMetadata metadata = new CertificateMetadata(
                    certificateId,
                    request.getParticipantName(),
                    request.getCourseName(),
                    request.getCompletionDate(),
                    request.getIssuerName(),
                    issueDate,
                    verificationUrl,
                    qrCodeBase64
            );
            
            // Generate certificate files
            byte[] pdfContent = generatePdfCertificate(metadata);
            byte[] pngContent = generatePngCertificate(metadata);
            
            // Save all artifacts
            saveCertificateArtifacts(metadata, pdfContent, pngContent);
            
            // Cache the metadata
            certificateCache.put(certificateId, metadata);
            
            return new CertificateGenerationResult(
                    certificateId,
                    metadata.getVerificationUrl(),
                    getDownloadUrl(certificateId, "pdf"),
                    getDownloadUrl(certificateId, "png"),
                    issueDate
            );
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Certificate generation failed", e);
            throw new CertificateGenerationException("Failed to generate certificate: " + e.getMessage(), e);
        }
    }
    
    public static CertificateMetadata getCertificateMetadata(String certificateId) throws CertificateNotFoundException {
        CertificateMetadata metadata = certificateCache.get(certificateId);
        if (metadata == null) {
            metadata = loadCertificateFromDisk(certificateId);
            if (metadata != null) {
                certificateCache.put(certificateId, metadata);
            } else {
                throw new CertificateNotFoundException("Certificate not found: " + certificateId);
            }
        }
        return metadata;
    }
    
    public static byte[] getCertificateFile(String certificateId, String format) 
            throws CertificateNotFoundException, IOException {
        CertificateMetadata metadata = getCertificateMetadata(certificateId);
        Path filePath = Paths.get(CERTIFICATES_DIR, certificateId + "." + format.toLowerCase());
        
        if (!Files.exists(filePath)) {
            // Regenerate if file is missing but metadata exists
            if (format.equalsIgnoreCase("pdf")) {
                return generatePdfCertificate(metadata);
            } else if (format.equalsIgnoreCase("png")) {
                return generatePngCertificate(metadata);
            } else {
                throw new IllegalArgumentException("Unsupported format: " + format);
            }
        }
        
        return Files.readAllBytes(filePath);
    }
    
    private static void validateCertificateRequest(CertificateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Certificate request cannot be null");
        }
        if (request.getParticipantName() == null || request.getParticipantName().trim().isEmpty()) {
            throw new IllegalArgumentException("Participant name is required");
        }
        if (request.getCourseName() == null || request.getCourseName().trim().isEmpty()) {
            throw new IllegalArgumentException("Course name is required");
        }
        if (request.getIssuerName() == null || request.getIssuerName().trim().isEmpty()) {
            throw new IllegalArgumentException("Issuer name is required");
        }
        if (request.getCompletionDate() == null) {
            throw new IllegalArgumentException("Completion date is required");
        }
    }
    
    private static String buildVerificationUrl(String certificateId) {
        return "https://yourdomain.com/api/certificates/" + certificateId + "/verify";
    }
    
    private static String getDownloadUrl(String certificateId, String format) {
        return "https://yourdomain.com/api/certificates/" + certificateId + "/download?format=" + format;
    }
    
    private static byte[] generateQRCodeImage(String text) throws WriterException, IOException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.MARGIN, 1);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE, hints);
        
        BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix);
        
        // Add logo to center of QR code
        try {
            BufferedImage logo = ImageIO.read(AdvancedCertificateGenerator.class.getResourceAsStream("/logo.png"));
            if (logo != null) {
                Graphics2D graphics = qrImage.createGraphics();
                int logoSize = QR_CODE_SIZE / 5;
                int x = (QR_CODE_SIZE - logoSize) / 2;
                int y = (QR_CODE_SIZE - logoSize) / 2;
                graphics.drawImage(logo, x, y, logoSize, logoSize, null);
                graphics.dispose();
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not add logo to QR code", e);
        }
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(qrImage, "png", baos);
        return baos.toByteArray();
    }
    
    private static byte[] generatePdfCertificate(CertificateMetadata metadata) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                // Add background
                contentStream.setNonStrokingColor(new Color(240, 240, 240));
                contentStream.addRect(0, 0, page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
                contentStream.fill();
                
                // Add border
                contentStream.setStrokingColor(new Color(67, 97, 238));
                contentStream.setLineWidth(15);
                contentStream.addRect(30, 30, 
                    page.getMediaBox().getWidth() - 60, 
                    page.getMediaBox().getHeight() - 60);
                contentStream.stroke();
                
                // Add title
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 36);
                contentStream.setNonStrokingColor(new Color(67, 97, 238));
                contentStream.newLineAtOffset(100, 650);
                contentStream.showText("CERTIFICATE OF COMPLETION");
                contentStream.endText();
                
                // Add body text
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 14);
                contentStream.setNonStrokingColor(Color.BLACK);
                contentStream.newLineAtOffset(100, 600);
                contentStream.showText("This is to certify that");
                contentStream.endText();
                
                // Add participant name
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 28);
                contentStream.setNonStrokingColor(new Color(51, 51, 51));
                contentStream.newLineAtOffset(100, 550);
                contentStream.showText(metadata.getParticipantName());
                contentStream.endText();
                
                // Add course details
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 14);
                contentStream.newLineAtOffset(100, 500);
                contentStream.showText("has successfully completed the course");
                contentStream.endText();
                
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 18);
                contentStream.setNonStrokingColor(new Color(67, 97, 238));
                contentStream.newLineAtOffset(100, 470);
                contentStream.showText(metadata.getCourseName());
                contentStream.endText();
                
                // Add dates
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(100, 430);
                contentStream.showText("Completed on: " + metadata.getCompletionDate().format(DATE_FORMATTER));
                contentStream.endText();
                
                contentStream.beginText();
                contentStream.newLineAtOffset(100, 410);
                contentStream.showText("Issued on: " + metadata.getIssueDate().format(DATE_FORMATTER));
                contentStream.endText();
                
                // Add issuer
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA_OBLIQUE, 12);
                contentStream.newLineAtOffset(100, 350);
                contentStream.showText("Issued by: " + metadata.getIssuerName());
                contentStream.endText();
                
                // Add QR code
                byte[] qrCode = Base64.getDecoder().decode(metadata.getQrCodeBase64());
                PDImageXObject pdImage = PDImageXObject.createFromByteArray(document, qrCode, "qr-code");
                contentStream.drawImage(pdImage, 400, 100, 150, 150);
            }
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }
    
    private static byte[] generatePngCertificate(CertificateMetadata metadata) throws IOException {
        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        
        // Draw background
        graphics.setColor(new Color(240, 240, 240));
        graphics.fillRect(0, 0, 800, 600);
        
        // Draw border
        graphics.setColor(new Color(67, 97, 238));
        graphics.setStroke(new BasicStroke(10));
        graphics.drawRect(20, 20, 760, 560);
        
        // Draw title
        graphics.setColor(new Color(67, 97, 238));
        graphics.setFont(new Font("Helvetica", Font.BOLD, 36));
        graphics.drawString("CERTIFICATE OF COMPLETION", 100, 100);
        
        // Draw body text
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font("Helvetica", Font.PLAIN, 14));
        graphics.drawString("This is to certify that", 100, 150);
        
        // Draw participant name
        graphics.setColor(new Color(51, 51, 51));
        graphics.setFont(new Font("Helvetica", Font.BOLD, 28));
        graphics.drawString(metadata.getParticipantName(), 100, 200);
        
        // Draw course details
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font("Helvetica", Font.PLAIN, 14));
        graphics.drawString("has successfully completed the course", 100, 250);
        
        graphics.setColor(new Color(67, 97, 238));
        graphics.setFont(new Font("Helvetica", Font.BOLD, 18));
        graphics.drawString(metadata.getCourseName(), 100, 280);
        
        // Draw dates
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font("Helvetica", Font.PLAIN, 12));
        graphics.drawString("Completed on: " + metadata.getCompletionDate().format(DATE_FORMATTER), 100, 320);
        graphics.drawString("Issued on: " + metadata.getIssueDate().format(DATE_FORMATTER), 100, 340);
        
        // Draw issuer
        graphics.setFont(new Font("Helvetica", Font.ITALIC, 12));
        graphics.drawString("Issued by: " + metadata.getIssuerName(), 100, 380);
        
        // Draw QR code
        byte[] qrCode = Base64.getDecoder().decode(metadata.getQrCodeBase64());
        BufferedImage qrImage = ImageIO.read(new ByteArrayInputStream(qrCode));
        graphics.drawImage(qrImage, 550, 400, 150, 150, null);
        
        graphics.dispose();
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }
    
    private static void saveCertificateArtifacts(CertificateMetadata metadata, byte[] pdfContent, byte[] pngContent) 
            throws IOException {
        // Save metadata
        JSONObject json = new JSONObject();
        json.put("certificateId", metadata.getCertificateId());
        json.put("participantName", metadata.getParticipantName());
        json.put("courseName", metadata.getCourseName());
        json.put("completionDate", metadata.getCompletionDate().format(DATE_FORMATTER));
        json.put("issuerName", metadata.getIssuerName());
        json.put("issueDate", metadata.getIssueDate().format(DATE_FORMATTER));
        json.put("verificationUrl", metadata.getVerificationUrl());
        json.put("qrCodeBase64", metadata.getQrCodeBase64());
        
        Path jsonPath = Paths.get(CERTIFICATES_DIR, metadata.getCertificateId() + ".json");
        Files.write(jsonPath, json.toString().getBytes());
        
        // Save PDF
        Path pdfPath = Paths.get(CERTIFICATES_DIR, metadata.getCertificateId() + ".pdf");
        Files.write(pdfPath, pdfContent);
        
        // Save PNG
        Path pngPath = Paths.get(CERTIFICATES_DIR, metadata.getCertificateId() + ".png");
        Files.write(pngPath, pngContent);
    }
    
    private static CertificateMetadata loadCertificateFromDisk(String certificateId) throws CertificateNotFoundException {
        try {
            Path jsonPath = Paths.get(CERTIFICATES_DIR, certificateId + ".json");
            if (!Files.exists(jsonPath)) {
                return null;
            }
            
            String content = new String(Files.readAllBytes(jsonPath));
            JSONObject json = new JSONObject(content);
            
            return new CertificateMetadata(
                    json.getString("certificateId"),
                    json.getString("participantName"),
                    json.getString("courseName"),
                    LocalDate.parse(json.getString("completionDate"), DATE_FORMATTER),
                    json.getString("issuerName"),
                    LocalDate.parse(json.getString("issueDate"), DATE_FORMATTER),
                    json.getString("verificationUrl"),
                    json.getString("qrCodeBase64")
            );
        } catch (IOException e) {
            throw new CertificateNotFoundException("Failed to load certificate: " + certificateId, e);
        }
    }
    
    // Data classes
    public static class CertificateRequest {
        private String participantName;
        private String courseName;
        private LocalDate completionDate;
        private String issuerName;
        
        // Getters and setters
        public String getParticipantName() { return participantName; }
        public void setParticipantName(String participantName) { this.participantName = participantName; }
        public String getCourseName() { return courseName; }
        public void setCourseName(String courseName) { this.courseName = courseName; }
        public LocalDate getCompletionDate() { return completionDate; }
        public void setCompletionDate(LocalDate completionDate) { this.completionDate = completionDate; }
        public String getIssuerName() { return issuerName; }
        public void setIssuerName(String issuerName) { this.issuerName = issuerName; }
    }
    
    public static class CertificateMetadata {
        private final String certificateId;
        private final String participantName;
        private final String courseName;
        private final LocalDate completionDate;
        private final String issuerName;
        private final LocalDate issueDate;
        private final String verificationUrl;
        private final String qrCodeBase64;
        
        public CertificateMetadata(String certificateId, String participantName, String courseName, 
                                 LocalDate completionDate, String issuerName, LocalDate issueDate,
                                 String verificationUrl, String qrCodeBase64) {
            this.certificateId = certificateId;
            this.participantName = participantName;
            this.courseName = courseName;
            this.completionDate = completionDate;
            this.issuerName = issuerName;
            this.issueDate = issueDate;
            this.verificationUrl = verificationUrl;
            this.qrCodeBase64 = qrCodeBase64;
        }
        
        // Getters
        public String getCertificateId() { return certificateId; }
        public String getParticipantName() { return participantName; }
        public String getCourseName() { return courseName; }
        public LocalDate getCompletionDate() { return completionDate; }
        public String getIssuerName() { return issuerName; }
        public LocalDate getIssueDate() { return issueDate; }
        public String getVerificationUrl() { return verificationUrl; }
        public String getQrCodeBase64() { return qrCodeBase64; }
    }
    
    public static class CertificateGenerationResult {
        private final String certificateId;
        private final String verificationUrl;
        private final String pdfDownloadUrl;
        private final String pngDownloadUrl;
        private final LocalDate issueDate;
        
        public CertificateGenerationResult(String certificateId, String verificationUrl, 
                                         String pdfDownloadUrl, String pngDownloadUrl,
                                         LocalDate issueDate) {
            this.certificateId = certificateId;
            this.verificationUrl = verificationUrl;
            this.pdfDownloadUrl = pdfDownloadUrl;
            this.pngDownloadUrl = pngDownloadUrl;
            this.issueDate = issueDate;
        }
        
        // Getters
        public String getCertificateId() { return certificateId; }
        public String getVerificationUrl() { return verificationUrl; }
        public String getPdfDownloadUrl() { return pdfDownloadUrl; }
        public String getPngDownloadUrl() { return pngDownloadUrl; }
        public LocalDate getIssueDate() { return issueDate; }
    }
    
    public static class CertificateGenerationException extends Exception {
        public CertificateGenerationException(String message) { super(message); }
        public CertificateGenerationException(String message, Throwable cause) { super(message, cause); }
    }
    
    public static class CertificateNotFoundException extends Exception {
        public CertificateNotFoundException(String message) { super(message); }
        public CertificateNotFoundException(String message, Throwable cause) { super(message, cause); }
    }
}