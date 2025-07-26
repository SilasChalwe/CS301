package com.nextinnomind.aes_file_encryption.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nextinnomind.aes_file_encryption.data.FileMeta;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

import static com.nextinnomind.aes_file_encryption.security.CryptoUtils.*;

@Service
public class FileEncryptionService {

    private final Path encryptedDir = Paths.get("uploads/encrypted/");
    private final Path keyDir = Paths.get("uploads/key/");
    private final ObjectMapper objectMapper = new ObjectMapper();

    private FileStore fileStore;

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(encryptedDir);
        Files.createDirectories(keyDir);
        fileStore = Files.getFileStore(encryptedDir);
    }

    public Map<String, Object> encryptAndSaveFile(
            byte[] fileData,
            String filename,
            String userPassword,
            String deviceFingerprint,
            byte[] arduinoKey
    ) throws Exception {
        SecretKey fileKey = generateAESKey();
        byte[] iv = generateIV();

        byte[] encryptedData = encryptData(fileData, fileKey, iv);
        String sha256Encrypted = calculateSHA256(encryptedData);

        byte[] salt = generateSalt(256);

        byte[] combined = (userPassword + deviceFingerprint).getBytes();
        byte[] hybridSecret = new byte[combined.length + arduinoKey.length];
        System.arraycopy(combined, 0, hybridSecret, 0, combined.length);
        System.arraycopy(arduinoKey, 0, hybridSecret, combined.length, arduinoKey.length);

        SecretKey masterKey = deriveKeyFromPassword(new String(hybridSecret), salt);

        byte[] ivForKeys = generateIV();
        byte[] encryptedFileKey = encryptAES(fileKey.getEncoded(), masterKey, ivForKeys);
        byte[] encryptedIv = encryptAES(iv, masterKey, ivForKeys);

        Path filePath = encryptedDir.resolve(filename);
        Files.write(filePath, encryptedData);

        FileMeta meta = new FileMeta(
                filename,
                Base64.getEncoder().encodeToString(encryptedFileKey),
                Base64.getEncoder().encodeToString(encryptedIv),
                Base64.getEncoder().encodeToString(ivForKeys),
                sha256Encrypted,
                encryptedData.length,
                Base64.getEncoder().encodeToString(salt)
        );

        ObjectNode keyFileJson = objectMapper.createObjectNode();
        keyFileJson.put("filename", filename);
        keyFileJson.put("encryptedAesKey", meta.encryptedAesKey);
        keyFileJson.put("encryptedIv", meta.encryptedIv);
        keyFileJson.put("keysIV", meta.keysIv);
        keyFileJson.put("sha256", meta.sha256);
        keyFileJson.put("salt", meta.salt);
        keyFileJson.put("deviceFingerprint", deviceFingerprint);

        String keyFileName = filename + ".key.json";
        Path keyFilePath = keyDir.resolve(keyFileName);
        Files.writeString(keyFilePath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(keyFileJson));

        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("r--------");
            Files.setPosixFilePermissions(keyFilePath, perms);
        } catch (UnsupportedOperationException | IOException e) {
            keyFilePath.toFile().setReadOnly();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("fileMeta", meta);
        result.put("keyFileContent", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(keyFileJson));
        result.put("keyFilePath", keyFilePath.toString());
        return result;
    }

    public byte[] decryptFile(
            byte[] encryptedData,
            String keyFileJson,
            String userPassword,
            String deviceFingerprint,
            byte[] arduinoKey
    ) throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(keyFileJson);

        String encryptedAesKeyBase64 = root.get("encryptedAesKey").asText();
        String encryptedIvBase64 = root.get("encryptedIv").asText();
        String keysIvBase64 = root.get("keysIV").asText();
        String saltBase64 = root.get("salt").asText();
        String expectedHash = root.get("sha256").asText();
        String storedFingerprint = root.get("deviceFingerprint").asText();

        if (!deviceFingerprint.equals(storedFingerprint)) {
            throw new SecurityException("Device fingerprint mismatch.");
        }

        String actualHash = calculateSHA256(encryptedData);
        if (!expectedHash.equals(actualHash)) {
            throw new SecurityException("Encrypted file hash mismatch.");
        }

        byte[] salt = Base64.getDecoder().decode(saltBase64);
        byte[] encryptedAesKey = Base64.getDecoder().decode(encryptedAesKeyBase64);
        byte[] encryptedIv = Base64.getDecoder().decode(encryptedIvBase64);
        byte[] keysIv = Base64.getDecoder().decode(keysIvBase64);

        byte[] combined = (userPassword + deviceFingerprint).getBytes();
        byte[] hybridSecret = new byte[combined.length + arduinoKey.length];
        System.arraycopy(combined, 0, hybridSecret, 0, combined.length);
        System.arraycopy(arduinoKey, 0, hybridSecret, combined.length, arduinoKey.length);

        SecretKey masterKey = deriveKeyFromPassword(new String(hybridSecret), salt);

        byte[] aesKeyBytes = decryptAES(encryptedAesKey, masterKey, keysIv);
        byte[] iv = decryptAES(encryptedIv, masterKey, keysIv);
        SecretKey fileKey = new SecretKeySpec(aesKeyBytes, "AES");

        return decryptData(encryptedData, fileKey, iv);
    }

    public List<FileMeta> listEncryptedFiles() {
        List<FileMeta> fileList = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(encryptedDir)) {
            for (Path encryptedFile : stream) {
                try {
                    String filename = encryptedFile.getFileName().toString();
                    long size = Files.size(encryptedFile);
                    byte[] fileData = Files.readAllBytes(encryptedFile);
                    String sha256 = calculateSHA256(fileData);

                    // Try load key metadata if available
                    Path keyFile = keyDir.resolve(filename + ".key.json");
                    String encryptedAesKey = "";
                    String encryptedIv = "";
                    String keysIV = "";
                    String salt = "";

                    if (Files.exists(keyFile)) {
                        String keyContent = Files.readString(keyFile);
                        ObjectNode node = (ObjectNode) objectMapper.readTree(keyContent);

                        encryptedAesKey = node.get("encryptedAesKey").asText("");
                        encryptedIv = node.get("encryptedIv").asText("");
                        keysIV = node.get("keysIV").asText("");
                        salt = node.get("salt").asText("");
                    }

                    FileMeta meta = new FileMeta(
                            filename,
                            encryptedAesKey,
                            encryptedIv,
                            keysIV,
                            sha256,
                            size,
                            salt
                    );

                    fileList.add(meta);

                } catch (Exception e) {
                    System.err.println("⚠️ Skipping file due to error: " + encryptedFile + " → " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Failed to list encrypted files: " + e.getMessage());
        }

        return fileList;
    }

    public boolean deleteFile(String filename) throws IOException {
        Path filePath = encryptedDir.resolve(filename);
        Path keyFilePath = keyDir.resolve(filename + ".key.json");
        boolean deleted = false;

        if (Files.exists(filePath)) {
            Files.delete(filePath);
            deleted = true;
        }
        if (Files.exists(keyFilePath)) {
            Files.delete(keyFilePath);
        }

        return deleted;
    }

    public FileMeta getFileMeta(String filename) {
        Path filePath = encryptedDir.resolve(filename);
        Path keyFilePath = keyDir.resolve(filename + ".key.json");

        if (!Files.exists(filePath)) {
            return null;
        }

        try {
            long size = Files.size(filePath);
            byte[] fileData = Files.readAllBytes(filePath);
            String sha256 = calculateSHA256(fileData);

            String encryptedAesKey = "";
            String encryptedIv = "";
            String keysIV = "";
            String salt = "";

            if (Files.exists(keyFilePath)) {
                String keyContent = Files.readString(keyFilePath);
                ObjectNode node = (ObjectNode) objectMapper.readTree(keyContent);

                encryptedAesKey = node.get("encryptedAesKey").asText("");
                encryptedIv = node.get("encryptedIv").asText("");
                keysIV = node.get("keysIV").asText("");
                salt = node.get("salt").asText("");
            }

            return new FileMeta(
                    filename,
                    encryptedAesKey,
                    encryptedIv,
                    keysIV,
                    sha256,
                    size,
                    salt
            );
        } catch (Exception e) {
            System.err.println("Failed to load meta for " + filename + ": " + e.getMessage());
            return null;
        }
    }
}
