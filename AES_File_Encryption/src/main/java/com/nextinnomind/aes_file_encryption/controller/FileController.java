package com.nextinnomind.aes_file_encryption.controller;

import com.nextinnomind.aes_file_encryption.data.FileMeta;
import com.nextinnomind.aes_file_encryption.service.FileEncryptionService;
import com.nextinnomind.aes_file_encryption.util.ArduinoKeyFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.Base64;

import static ch.qos.logback.core.encoder.ByteArrayUtil.hexStringToByteArray;

@RestController
@RequestMapping("/api")
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);
    private final FileEncryptionService fileEncryptionService;
    private static final String ARDUINO_SERIAL_PORT = "/tmp/ttyS2";

    public FileController(FileEncryptionService fileEncryptionService) {
        this.fileEncryptionService = fileEncryptionService;
    }

    @GetMapping("/ping")
    public ResponseEntity<?> ping() {
        boolean isOnline = ArduinoKeyFetcher.pingDevice(ARDUINO_SERIAL_PORT);
        String message = isOnline ? "Arduino is ONLINE" : "Arduino is OFFLINE";
        logger.info("Ping check: {}", message);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PostMapping("/files/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file,
                                        @RequestParam("userPassword") String userPassword) throws Exception {
        logger.info("Upload request: filename={}", file.getOriginalFilename());

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        try {
            // Notify Arduino
            ArduinoKeyFetcher.displayOnOLED(ARDUINO_SERIAL_PORT, "waiting \npress key");

            byte[] arduinoKey = ArduinoKeyFetcher.waitForKeyWithTimeout(ARDUINO_SERIAL_PORT);
            String deviceFingerprint = Base64.getEncoder().encodeToString(arduinoKey);

            Map<String, Object> result = fileEncryptionService.encryptAndSaveFile(
                    file.getBytes(),
                    file.getOriginalFilename(),
                    userPassword,
                    deviceFingerprint,
                    arduinoKey
            );

            FileMeta meta = (FileMeta) result.get("fileMeta");
            logger.info("File encrypted: {}", meta.filename);

            // Notify Arduino
            ArduinoKeyFetcher.displayOnOLED(ARDUINO_SERIAL_PORT, "Encryption\nSuccess");

            return ResponseEntity.ok(Map.of(
                    "filename", meta.filename,
                    "sha256", meta.sha256,
                    "size", meta.size
            ));

        } catch (Exception e) {
            // Handle timeout specifically
            if (e.getMessage() != null && e.getMessage().contains("Timeout")) {
                logger.warn("Arduino key timeout: {}", e.getMessage());
                ArduinoKeyFetcher.displayOnOLED(ARDUINO_SERIAL_PORT, "Timeout\nRetry?");
                return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                        .body(Map.of("error", e.getMessage()));
            }

            logger.error("Encryption failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Encryption failed: " + e.getMessage()));
        }
    }

    @PostMapping("/files/decrypt")
    public ResponseEntity<?> decryptFile(@RequestParam("encryptedFile") MultipartFile encryptedFile,
                                         @RequestParam("keyFile") MultipartFile keyFile,
                                         @RequestParam("userPassword") String userPassword) throws Exception {
        if (encryptedFile.isEmpty() || keyFile.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Encrypted file and key file required"));
        }

        try {
            // Notify Arduino
            ArduinoKeyFetcher.displayOnOLED(ARDUINO_SERIAL_PORT, "waiting \npress key");

            byte[] arduinoKey = ArduinoKeyFetcher.waitForKeyWithTimeout(ARDUINO_SERIAL_PORT);
            String deviceFingerprint = Base64.getEncoder().encodeToString(arduinoKey);
            String keyFileJson = new String(keyFile.getBytes());

            byte[] decrypted = fileEncryptionService.decryptFile(
                    encryptedFile.getBytes(),
                    keyFileJson,
                    userPassword,
                    deviceFingerprint,
                    arduinoKey
            );

            // Notify Arduino
            ArduinoKeyFetcher.displayOnOLED(ARDUINO_SERIAL_PORT, "Decryption\nSuccess");

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=decrypted_" + encryptedFile.getOriginalFilename())
                    .body(decrypted);

        } catch (Exception e) {
            // Handle timeout specifically
            if (e.getMessage() != null && e.getMessage().contains("Timeout")) {
                logger.warn("Arduino key timeout: {}", e.getMessage());
                ArduinoKeyFetcher.displayOnOLED(ARDUINO_SERIAL_PORT, "Timeout\nRetry?");
                return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                        .body(Map.of("error", e.getMessage()));
            }

            logger.error("Decryption failed", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Decryption failed: " + e.getMessage()));
        }
    }

    @GetMapping("/files")
    public ResponseEntity<?> listFiles() {
        try {
            List<FileMeta> files = fileEncryptionService.listEncryptedFiles();
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            logger.error("Failed to list files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to list files"));
        }
    }

    @GetMapping("/files/meta/{filename}")
    public ResponseEntity<?> getFileMeta(@PathVariable String filename) {
        FileMeta meta = fileEncryptionService.getFileMeta(filename);
        if (meta == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Metadata not found"));
        }

        return ResponseEntity.ok(Map.of(
                "filename", meta.filename,
                "sha256", meta.sha256,
                "size", meta.size
        ));
    }

    @GetMapping("/files/download/encrypted/{filename:.+}")
    public ResponseEntity<?> downloadEncryptedFile(@PathVariable String filename) {
        try {
            java.nio.file.Path filePath = java.nio.file.Paths.get("uploads/encrypted").resolve(filename);
            if (!java.nio.file.Files.exists(filePath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "File not found"));
            }

            byte[] data = java.nio.file.Files.readAllBytes(filePath);

            return ResponseEntity.ok()
                    .contentLength(data.length)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(new ByteArrayResource(data));

        } catch (java.io.IOException e) {
            logger.error("Failed to download file: {}", filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to download file"));
        }
    }

    @DeleteMapping("/files/delete/{filename}")
    public ResponseEntity<?> deleteFile(@PathVariable String filename) {
        try {
            boolean deleted = fileEncryptionService.deleteFile(filename);
            if (!deleted) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "File not found"));
            }
            return ResponseEntity.ok(Map.of("message", "Deleted successfully"));
        } catch (Exception e) {
            logger.error("Deletion failed for file: {}", filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Deletion failed: " + e.getMessage()));
        }
    }

    @PostMapping("/device/display")
    public ResponseEntity<?> displayMessageOnArduino(@RequestParam String message) {
        try {
            ArduinoKeyFetcher.displayOnOLED(ARDUINO_SERIAL_PORT, message);
            logger.info("Sent message to Arduino OLED: {}", message);
            return ResponseEntity.ok(Map.of("message", "Message sent to Arduino: " + message));
        } catch (Exception e) {
            logger.error("Failed to send message to Arduino", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to send message to Arduino: " + e.getMessage()));
        }
    }

    @PostMapping("/device/provision")
    public ResponseEntity<?> provisionKey(@RequestParam(required = false) String keyHex) {
        try {
            byte[] key;
            if (keyHex != null && keyHex.length() == 32) {
                key = hexStringToByteArray(keyHex);
            } else {
                key = new byte[16];
                new Random().nextBytes(key);
            }

            boolean success = ArduinoKeyFetcher.provisionHardwareKey(ARDUINO_SERIAL_PORT, key);
            if (!success) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Provisioning failed"));
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Key provisioned to Arduino",
                    "keyHex", Base64.getEncoder().encodeToString(key)
            ));
        } catch (Exception e) {
            logger.error("Provisioning failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to provision key: " + e.getMessage()));
        }
    }

    @PostMapping("/device/reset")
    public ResponseEntity<?> resetHardwareKey() {
        try {
            boolean success = ArduinoKeyFetcher.resetHardwareKey(ARDUINO_SERIAL_PORT);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "Key reset on Arduino"));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to reset key"));
            }
        } catch (Exception e) {
            logger.error("Reset failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to reset key: " + e.getMessage()));
        }
    }

    @GetMapping("/device/key")
    public ResponseEntity<?> fetchKeyFromArduinoOnDemand() {
        try {
            byte[] key = ArduinoKeyFetcher.waitForKeyWithTimeout(ARDUINO_SERIAL_PORT);
            String keyHex = bytesToHex(key);

            return ResponseEntity.ok(Map.of(
                    "message", "Key received from Arduino",
                    "keyHex", keyHex
            ));

        } catch (Exception e) {
            // Handle timeout specifically
            if (e.getMessage() != null && e.getMessage().contains("Timeout")) {
                logger.warn("Arduino key timeout: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                        .body(Map.of("error", e.getMessage()));
            }

            logger.error("Failed to retrieve key from Arduino", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve key: " + e.getMessage()));
        }
    }

    // Utility to convert bytes to hex string
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}