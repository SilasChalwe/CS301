#include <EEPROM.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>

#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_RESET -1
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);

#define RED_LED_PIN 3
#define GREEN_LED_PIN 5
#define BLUE_LED_PIN 6
#define BUTTON_PIN 4

const int KEY_LENGTH = 16;
const int EEPROM_FLAG_ADDR = 100;
const byte PROVISIONED_FLAG = 0x55;

byte keyPart[KEY_LENGTH];
bool provisioned = false;

enum LedStatus { LED_OFF, LED_BLINKING };
LedStatus ledStatus = LED_OFF;
int ledPin = RED_LED_PIN;
const int BLINK_COUNT_MAX = 6;
const unsigned long BLINK_INTERVAL = 300;
unsigned long lastBlinkTime = 0;
int blinkCount = 0;
bool ledState = false;

unsigned long displayTimeout = 0;
bool autoClearDisplay = false;

void displayCentered(const char* text);
void showStatusLED(String status);
void handleLedBlink();
void clearDisplayIfTimeout();
void showLoadingAnimation(const char* baseText, unsigned long durationMs);
void receiveKey();
void sendKey();
void resetKey();
void displayMessage();

void setup() {
  pinMode(RED_LED_PIN, OUTPUT);
  pinMode(GREEN_LED_PIN, OUTPUT);
  pinMode(BLUE_LED_PIN, OUTPUT);
  pinMode(BUTTON_PIN, INPUT_PULLUP);

  Serial.begin(9600);

  if (!display.begin(SSD1306_SWITCHCAPVCC, 0x3C)) {
    while (true);
  }

  display.clearDisplay();
  display.setTextColor(SSD1306_WHITE);
  display.setTextSize(1);
  displayCentered("Secure Vault\nby NextInnoMind");
  showStatusLED("info");

  if (EEPROM.read(EEPROM_FLAG_ADDR) == PROVISIONED_FLAG) {
    for (int i = 0; i < KEY_LENGTH; i++) {
      keyPart[i] = EEPROM.read(i);
    }
    provisioned = true;
    displayCentered("Key Found\nReady");
    showStatusLED("success");
  } else {
    provisioned = false;
    displayCentered("Waiting for Key\nSend to Arduino");
    showStatusLED("info");
  }
}

void loop() {
  handleLedBlink();
  clearDisplayIfTimeout();

  if (digitalRead(BUTTON_PIN) == LOW) {
    if (provisioned) {
      displayCentered("Button Pressed\nSending Key");
      showStatusLED("info");
      Serial.write(0xD4);  // Button press signal
      Serial.flush();      // Ensure signal is sent immediately
      sendKey();
    } else {
      displayCentered("No Key\nProvisioned");
      showStatusLED("fail");
    }
    delay(1000); // Debounce
  }

  if (Serial.available()) {
    byte command = Serial.read();
    switch (command) {
      case 0xA0: if (!provisioned) receiveKey(); else displayCentered("Key Already\nProvisioned"); break;
      case 0xA5: if (provisioned) sendKey(); else displayCentered("No Key\nProvisioned"); break;
      case 0xC0: resetKey(); break;
      case 0xB0: displayMessage(); break;
      default: displayCentered("Unknown Command"); showStatusLED("fail"); break;
    }
  }
}

void receiveKey() {
  displayCentered("Receiving Key...");
  showStatusLED("info");
  display.display();

  unsigned long startTime = millis();
  int received = 0;

  while (received < KEY_LENGTH) {
    if (Serial.available()) {
      keyPart[received++] = Serial.read();
      startTime = millis(); // Reset timeout on each byte
    }
    if (millis() - startTime > 3000) {
      displayCentered("Timeout\nProvision Failed");
      showStatusLED("fail");
      return;
    }
  }

  for (int i = 0; i < KEY_LENGTH; i++) EEPROM.write(i, keyPart[i]);
  EEPROM.write(EEPROM_FLAG_ADDR, PROVISIONED_FLAG);
  provisioned = true;
  displayCentered("Key Provisioned");
  showStatusLED("success");
  autoClearDisplay = true;
  displayTimeout = millis() + 5000;
}

void sendKey() {
  // Send key immediately without delay
  for (int i = 0; i < KEY_LENGTH; i++) {
    Serial.write(keyPart[i]);
  }
  Serial.flush();  // Ensure all bytes are transmitted
  
  displayCentered("Key Sent");
  showStatusLED("success");
  autoClearDisplay = true;
  displayTimeout = millis() + 5000;
}

void resetKey() {
  for (int i = 0; i < KEY_LENGTH; i++) EEPROM.write(i, 0x00);
  EEPROM.write(EEPROM_FLAG_ADDR, 0x00);
  provisioned = false;
  displayCentered("Key Reset");
  showStatusLED("success");
  autoClearDisplay = true;
  displayTimeout = millis() + 5000;
}

void displayMessage() {
  delay(5); // Short delay to allow message to arrive
  char buffer[65];
  int i = 0;
  unsigned long start = millis();

  // Read until newline or timeout
  while ((millis() - start < 500) && i < 64) {
    if (Serial.available()) {
      char c = Serial.read();
      if (c == '\n') break;
      buffer[i++] = c;
    }
  }
  buffer[i] = '\0';

  if (strlen(buffer) > 0) {
    displayCentered(buffer);
    showStatusLED("info");
    autoClearDisplay = true;
    displayTimeout = millis() + 5000;
  } else {
    displayCentered("Empty Message");
    showStatusLED("fail");
  }
}

void showStatusLED(String status) {
  digitalWrite(RED_LED_PIN, LOW);
  digitalWrite(GREEN_LED_PIN, LOW);
  digitalWrite(BLUE_LED_PIN, LOW);
  ledPin = (status == "success") ? GREEN_LED_PIN : (status == "info") ? BLUE_LED_PIN : RED_LED_PIN;
  ledStatus = LED_BLINKING;
  blinkCount = 0;
  lastBlinkTime = millis();
  ledState = false;
}

void handleLedBlink() {
  if (ledStatus != LED_BLINKING) return;
  if (millis() - lastBlinkTime >= BLINK_INTERVAL) {
    lastBlinkTime = millis();
    ledState = !ledState;
    digitalWrite(ledPin, ledState ? HIGH : LOW);
    if (++blinkCount >= BLINK_COUNT_MAX) {
      digitalWrite(ledPin, LOW);
      ledStatus = LED_OFF;
    }
  }
}

void clearDisplayIfTimeout() {
  if (autoClearDisplay && millis() > displayTimeout) {
    display.clearDisplay();
    display.display();
    autoClearDisplay = false;
  }
}

void displayCentered(const char* text) {
  display.clearDisplay();
  display.setTextSize(1);

  int lines = 1;
  for (int i = 0; text[i]; i++) if (text[i] == '\n') lines++;
  int y_start = (SCREEN_HEIGHT - (lines * 8)) / 2 - 4;
  if (y_start < 0) y_start = 0;

  const char* ptr = text;
  char lineBuf[33];
  int line = 0;

  while (*ptr) {
    int i = 0;
    while (*ptr && *ptr != '\n') lineBuf[i++] = *ptr++;
    lineBuf[i] = '\0';
    if (*ptr == '\n') ptr++;
    int16_t x, y; uint16_t w, h;
    display.getTextBounds(lineBuf, 0, 0, &x, &y, &w, &h);
    int x_start = (SCREEN_WIDTH - w) / 2;
    display.setCursor(x_start, y_start + line * 10);
    display.print(lineBuf);
    line++;
  }

  display.display();
}

//socat -d -d PTY,raw,echo=0,link=/tmp/ttyS1 PTY,raw,echo=0,link=/tmp/ttyS2
