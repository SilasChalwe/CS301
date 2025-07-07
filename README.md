# 🔐 CS301: Hardware-Based Data Protection System

A secure, Spring Boot–powered backend system enhanced with **Arduino hardware-based key management** for file encryption and decryption. This project merges **software cryptography (AES)** with a **hardware security module (Arduino)** to enforce physical key presence for enhanced file confidentiality.

---

## 📦 Project Structure

- `controller/`: REST APIs to upload, encrypt, decrypt, and manage files securely.
- `service/`: Business logic to perform AES encryption/decryption and file handling.
- `data/`: Metadata model for stored encrypted files.
- `util/`: Serial communication utilities for Arduino-based key management.
- `arduino/`: Companion Arduino sketch (`arduino_secure_key_device.ino`) and circuit diagram for hardware integration.
- `web_ui/`: GUI pages and logic for interaction with users.

---

## 🛡️ Key Features

### ✅ Hybrid Security (Software + Hardware)
- AES-256 encryption using a combined key:
  - **User-defined password**
  - **Device fingerprint**
  - **Arduino-generated random key**

### 🔌 Arduino Integration
- OLED display feedback (e.g., `Encryption Success`, `Press Key`)
- Serial-based key exchange
- Physical key confirmation via button press
- Timeout handling to prevent unauthorized access

### 📁 File Management APIs
- `/files/upload`: Encrypt and store files using AES with Arduino key support
- `/files/decrypt`: Decrypt a file using the uploaded key and hardware fingerprint
- `/files`: List all encrypted files
- `/files/meta/{filename}`: Get metadata (SHA-256, size, name)
- `/files/download/encrypted/{filename}`: Download an encrypted file
- `/files/delete/{filename}`: Securely delete stored files

### 🔧 Device Control Endpoints
- `/device/display`: Send custom messages to the Arduino OLED
- `/device/provision`: Generate or upload a key and send to Arduino
- `/device/reset`: Reset the Arduino-stored key
- `/device/key`: Retrieve key from Arduino with timeout protection
- `/ping`: Check if Arduino is online and responsive

---

## 🏗️ System Architecture & Design

### Architecture Overview

This system is built using both software and hardware. Here's how it all works together:

1. **User Interface (Web GUI)** – This is what users see. It's a set of web screens for uploading files, entering passwords, and downloading files.
2. **Spring Boot Backend** – The brains of the system. It encrypts and decrypts files, talks to the database and also communicates with the Arduino board.
3. **Arduino Hardware** – A physical board that stores part of the encryption key. The user must press a button on it to confirm actions. It displays messages like "waiting" or "encryption complete".
4. **Firebase Firestore** – Stores file metadata like filename, file size, and SHA-256 hash in the cloud securely.
5. **File Storage (Local)** – Stores the actual encrypted files in folders on the backend server.
## 🏗️ System Architecture and Design

```text
+-------------------------------------------------------------------------------------------+
|                                  User Interface Layer                                    |
|-------------------------------------------------------------------------------------------|
|  ┌─────────────────────────┐        ┌─────────────────────────┐         ┌───────────────┐  |
|  │    Web Application      │ <────> │   RESTful API Server    │ <──────> │  Firebase     │  |
|  │  (React / Angular)      │  HTTPS │  (Spring Boot Backend)  │ Firestore│  Firestore DB │  |
|  └─────────────────────────┘        └─────────┬───────────────┘         └───────┬───────┘  |
|                                              │                                   │          |
|                                              │                                   │ Metadata |
|                                              ▼                                   ▼          |
|                             +------------------------------------+                        |
|                             |       Local File Storage System     |                        |
|                             | - Encrypted Files Folder            |                        |
|                             | - File Metadata Cache (optional)    |                        |
|                             +-----------------┬------------------+                        |
|                                               │                                               |
+-----------------------------------------------│-----------------------------------------------+
                                                │ Serial Communication (USB / Virtual COM Port)
                                                │                                              
+-----------------------------------------------▼-----------------------------------------------+
|                                      Hardware Layer (Arduino)                                   |
|-----------------------------------------------------------------------------------------------|
|  ┌────────────────────────────┐                                                                |
|  │        Arduino UNO Board    │                                                                |
|  │ - EEPROM stores secret key  │                                                                |
|  │ - OLED Display (status)     │                                                                |
|  │ - Physical Button (confirm) │                                                                |
|  │ - Serial communication port │                                                                |
|  └────────────────────────────┘                                                                |
+-----------------------------------------------------------------------------------------------+

### GUI Screens in the System

- 🔐 **Login/Register Page** – Users log in securely to access the platform
- 📤 **Upload File Page** – Select a file and enter your password to encrypt it
- 📥 **Download/Decrypt Page** – Choose an encrypted file and provide the password and key to decrypt
- 📄 **View Files Page** – Lists your encrypted files with size and hash
- 📟 **Arduino Status Page** – Shows real-time hardware availability (via `/ping`)

---

## 🚀 Getting Started

### Requirements
- Java 17+
- Spring Boot
- Firebase Firestore setup
- Arduino UNO or ATmega328-based board
- OLED display (SSD1306, I2C)
- Push button
- USB/Serial communication setup (e.g., `/dev/ttyUSB0` or virtual link via `/tmp/ttyS2`)

### Arduino Hardware Setup

#### 🛠️ Circuit Diagram (Summary)
- **OLED Display**
  - VCC → 5V
  - GND → GND
  - SDA → A4
  - SCL → A5

- **Push Button**
  - One leg → Digital Pin 4
  - Other leg → GND

- **Status LEDs (Optional)**
  - RED → Pin 3
  - GREEN → Pin 5
  - BLUE → Pin 6

#### 🔄 Serial Communication
```bash
socat -d -d PTY,raw,echo=0,link=/tmp/ttyS1 PTY,raw,echo=0,link=/tmp/ttyS2
```

### Upload Arduino Sketch
- Upload `secure_vault_key_handler.ino` to your Arduino
- Ensure the serial port in Java matches (`/dev/ttyUSB0` or `/tmp/ttyS2`)

### Run the Backend Server
```bash
./mvnw spring-boot:run
```

Or using Java:
```bash
java -jar target/aes_file_encryption-1.0.0.jar
```

---

## 🧪 Example API Usage

### Encrypt a file:
```http
POST /api/files/upload
Form data:
- file: [Choose file]
- userPassword: your-password
```

### Decrypt a file:
```http
POST /api/files/decrypt
Form data:
- encryptedFile: [Choose encrypted file]
- keyFile: [Choose key JSON file]
- userPassword: your-password
```

### Ping Arduino:
```http
GET /api/ping
Response:
{
  "message": "Arduino is ONLINE"
}
```

---

## 🛠️ Technical Highlights

- **AES Encryption**: Files are encrypted using a secure AES key formed by combining:
  - Password hash
  - Hardware fingerprint
  - Arduino's physical key
- **Hardware Binding**: Even with the right password, the file cannot be decrypted without the specific Arduino device.
- **OLED Status Display**: Displays current action (waiting, timeout, success, etc.)
- **Timeout Logic**: Avoids indefinite waits for user input or device response
- **Secure API Responses**: All endpoints return status-based JSON for easy integration
- **Firebase Firestore**: Secure and scalable metadata storage

---

## 👨‍💻 Author

**Silas Chalwe**  
`NextInnoMind Technologies`  
GitHub: [@SilasChalwe](https://github.com/SilasChalwe)

---

## 📜 License

This project is part of **CS301: Final Year Project – The Copperbelt University** and may be freely modified and used for academic or educational purposes. Commercial use requires permission.
