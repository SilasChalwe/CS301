package com.nextinnomind.aes_file_encryption.config;

import com.nextinnomind.aes_file_encryption.util.ArduinoKeyFetcher;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class ArduinoButtonListenerConfig {

    private static final String PORT_NAME = "/tmp/ttyS2"; // change to /dev/ttyUSB0 or COMx if needed

    @PostConstruct
    public void startButtonListener() {
        new Thread(() -> {
            try {
                System.out.println("🔄 Listening for button press on Arduino...");
                byte[] key = ArduinoKeyFetcher.waitForKeyWithTimeout(PORT_NAME);

                if (key.length != 16 || isAllZeros(key)) {
                    System.err.println("⚠️ Key missing or not provisioned. Received: " + Arrays.toString(key));
                    return;
                }

                System.out.println("[✅ KEY RECEIVED] " + Arrays.toString(key));

                // TODO: Handle the key (e.g., save securely, start decryption, notify UI)
            } catch (Exception e) {
                System.err.println("❌ Failed to receive key from Arduino: " + e.getMessage());
            }
        }).start();
    }

    private boolean isAllZeros(byte[] key) {
        for (byte b : key) {
            if (b != 0x00) return false;
        }
        return true;
    }


}
