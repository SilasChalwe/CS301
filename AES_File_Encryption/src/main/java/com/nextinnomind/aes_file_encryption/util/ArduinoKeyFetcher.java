package com.nextinnomind.aes_file_encryption.util;

import com.fazecast.jSerialComm.SerialPort;

import java.io.InputStream;

public class ArduinoKeyFetcher {

    public static byte[] getHardwareKey(String portName) throws Exception {
        SerialPort port = openPort(portName);

        // Clear input buffer
        while (port.bytesAvailable() > 0) {
            port.readBytes(new byte[port.bytesAvailable()], port.bytesAvailable());
        }

        // Send 0xA5 to request the key
        port.getOutputStream().write(0xA5);
        port.getOutputStream().flush();

        // Read 16-byte response (the key)
        byte[] key = new byte[16];
        int bytesRead = 0;
        while (bytesRead < 16) {
            int readNow = port.getInputStream().read(key, bytesRead, 16 - bytesRead);
            if (readNow > 0) {
                bytesRead += readNow;
            }
        }

        port.closePort();
        return key;
    }

    public static void provisionKey(String portName, byte[] key) throws Exception {
        SerialPort port = openPort(portName);
        port.getOutputStream().write(0xA0);
        port.getOutputStream().flush();
        Thread.sleep(50);
        port.getOutputStream().write(key);
        port.getOutputStream().flush();
        port.closePort();
    }

    public static void resetKey(String portName) throws Exception {
        SerialPort port = openPort(portName);
        port.getOutputStream().write(0xC0);
        port.getOutputStream().flush();
        port.closePort();
    }

    public static void displayOnOLED(String portName, String message) throws Exception {
        SerialPort port = openPort(portName);
        port.getOutputStream().write(0xB0);
        port.getOutputStream().write((message + "\n").getBytes());
        port.getOutputStream().flush();
        port.closePort();
    }

    public static boolean pingDevice(String portName) {
        try {
            SerialPort port = openPort(portName);
            boolean opened = port.isOpen();
            port.closePort();
            return opened;
        } catch (Exception e) {
            return false;
        }
    }

    private static SerialPort openPort(String portName) {
        SerialPort port = SerialPort.getCommPort(portName);
        port.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        port.setComPortTimeouts(
                SerialPort.TIMEOUT_READ_BLOCKING | SerialPort.TIMEOUT_WRITE_BLOCKING,
                5000, 0
        );
        if (!port.openPort()) {
            throw new RuntimeException("Failed to open port: " + portName);
        }
        return port;
    }

    public static boolean provisionHardwareKey(String portName, byte[] key) {
        if (key == null || key.length != 16) return false;

        try {
            provisionKey(portName, key);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean resetHardwareKey(String portName) {
        try {
            resetKey(portName);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Waits (blocking) for up to 60 seconds for a button press signal (0xD4) and a valid 16-byte key.
     * @param portName The COM port name
     * @return the 16-byte key
     * @throws Exception if timeout or read error
     */
    public static byte[] waitForKeyWithTimeout(String portName) throws Exception {
        SerialPort port = openPort(portName);
        InputStream in = port.getInputStream();

        long startTime = System.currentTimeMillis();
        System.out.println("🔁 Waiting for Arduino button press (0xD4)...");

        try {
            // Wait for button press signal (0xD4)
            while (System.currentTimeMillis() - startTime < 60000) {
                if (in.available() > 0) {
                    int signal = in.read();
                    if (signal == 0xD4) {
                        System.out.println("✅ Button press detected. Reading key...");
                        return readKeyBytes(in);
                    }
                }
                Thread.sleep(50);
            }
            throw new RuntimeException("Timeout: No button press within 60 seconds.");
        } finally {
            if (port != null && port.isOpen()) {
                port.closePort();
            }
        }
    }

    /**
     * Reads 16-byte key with cumulative timeout handling
     * @param in Input stream from serial port
     * @return 16-byte encryption key
     * @throws Exception on timeout or read error
     */
    private static byte[] readKeyBytes(InputStream in) throws Exception {
        byte[] key = new byte[16];
        int bytesRead = 0;
        long keyStartTime = System.currentTimeMillis();

        while (bytesRead < 16) {
            // Calculate remaining time (6 seconds total)
            long remainingTime = 6000 - (System.currentTimeMillis() - keyStartTime);
            if (remainingTime <= 0) {
                throw new RuntimeException("Timeout: Received only " + bytesRead +
                        "/16 bytes. Check Arduino connection.");
            }

            // Blocking read with timeout handling
            if (in.available() > 0) {
                int readNow = in.read(key, bytesRead, 16 - bytesRead);
                if (readNow > 0) {
                    bytesRead += readNow;
                }
            } else {
                // Adaptive sleep to reduce CPU load
                Thread.sleep(Math.max(10, Math.min(remainingTime / 10, 100)));
            }
        }
        System.out.println("🔐 Key received successfully.");
        return key;
    }
}