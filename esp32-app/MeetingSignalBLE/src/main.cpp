#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

BLEServer *pServer = NULL;
bool deviceConnected = false;
bool oldDeviceConnected = false;

// See the following for generating UUIDs:
// https://www.uuidgenerator.net/
#define SERVICE_UUID "e0387cb2-42b1-42dd-850d-a8d068ee7047"
#define MEETING_CHARACTERISTIC_UUID BLEUUID((uint16_t)0x2AE2)
#define BATTERY_CHARACTERISTIC_UUID BLEUUID((uint16_t)0x2A19)
#define CCC_DESCRIPTOR_UUID BLEUUID((uint16_t)0x2902)

BLECharacteristic meetingStateCharacterisitic(
    MEETING_CHARACTERISTIC_UUID,
    BLECharacteristic::PROPERTY_READ |
        BLECharacteristic::PROPERTY_WRITE |
        BLECharacteristic::PROPERTY_NOTIFY);
BLEDescriptor meetingStateDescriptor(CCC_DESCRIPTOR_UUID);

BLECharacteristic batteryLevelCharacterisitic(BATTERY_CHARACTERISTIC_UUID, BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
BLEDescriptor batteryLevelDescriptor(CCC_DESCRIPTOR_UUID);

// LED parameters
#define LED_ON "m"
#define LED_OFF "x"
String led_currentState = LED_OFF;

const uint8_t PIN_WARM = 14; // corresponds to GPIO14
const uint8_t PIN_COLD = 12; // corresponds to GPIO12
#define LED_WARM PIN_WARM
#define LED_COLD PIN_COLD

// Battery parameters
float batt_level = 0;

const uint8_t ANALOG_BATT = 36; // 13 corresponds to GPIO36 ADC1_CH0
#define INPUT_BATT ANALOG_BATT
float batt_calibration = 0.52; // Check Battery voltage using multimeter (multimeterRead - float_voltage) check getBatteryLevel()

/*
 * LED control functions
 */

void waitingStatusAlert()
{
  Serial.println("waiting status");

  digitalWrite(LED_COLD, HIGH);
  delay(2000);
  digitalWrite(LED_WARM, HIGH);
  delay(2000);

  digitalWrite(LED_COLD, LOW);
  delay(2000);
  digitalWrite(LED_WARM, LOW);
  delay(2000);
}

void connectedAlert()
{
  Serial.println("connection successful");

  for (size_t i = 0; i < 4; i++)
  {
    digitalWrite(LED_COLD, HIGH);
    digitalWrite(LED_WARM, HIGH);
    delay(1000);

    digitalWrite(LED_COLD, LOW);
    digitalWrite(LED_WARM, LOW);
    delay(1000);
  }
}

void disconnectedAlert()
{
  Serial.println("disconnected successful");

  for (size_t i = 0; i < 4; i++)
  {
    digitalWrite(LED_COLD, HIGH);
    digitalWrite(LED_WARM, HIGH);
    delay(1000);

    digitalWrite(LED_COLD, LOW);
    digitalWrite(LED_WARM, LOW);
    delay(1000);
  }
}

void changeLedState(String state)
{
  if (led_currentState.equals(state))
  {
    return;
  }
  else if (state.equals(LED_ON))
  {
    digitalWrite(LED_COLD, HIGH);
    digitalWrite(LED_WARM, HIGH);
  }
  else
  {
    digitalWrite(LED_COLD, LOW);
    digitalWrite(LED_WARM, LOW);
  }
  led_currentState = state;
}

/*
 * BATTERY control functions
 */

float mapfloat(float x, float in_min, float in_max, float out_min, float out_max)
{
  return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
}

int getBatteryLevel(int analogValue)
{

  // divide by 4095 because analog read range in ESP32 is up to 4095
  // multiply by two as voltage divider network is 100K & 100K Resistor
  float voltage = (((analogValue * 3.3) / 4095) * 2 + batt_calibration);

  // 2.8V as Battery Cut off Voltage & 4.2V as Maximum Voltage
  int bat_percentage = mapfloat(voltage, 2.8, 4.2, 0, 100);

  if (bat_percentage >= 100)
  {
    bat_percentage = 100;
  }

  if (bat_percentage <= 0)
  {
    bat_percentage = 0;
  }

  Serial.println("===BATTERY===");
  Serial.print("Read = ");
  Serial.println(analogValue);
  Serial.print("Output Voltage = ");
  Serial.println(voltage);
  Serial.print("Battery Percentage = ");
  Serial.println(bat_percentage);

  return bat_percentage;
}

/*
 * BLUETOOTH Callbacks
 */

class MyServerCallbacks : public BLEServerCallbacks
{
  void onConnect(BLEServer *pServer)
  {
    deviceConnected = true;
  };

  void onDisconnect(BLEServer *pServer)
  {
    deviceConnected = false;
  }
};

class MyCallbacks : public BLECharacteristicCallbacks
{
  void onWrite(BLECharacteristic *pCharacteristic)
  {
    std::string rxValue = pCharacteristic->getValue();

    if (rxValue.length() > 0)
    {

      String inputMessage = rxValue.c_str();
      if (inputMessage.equals(LED_ON))
      {
        changeLedState(LED_ON);
      }
      else
      {
        changeLedState(LED_OFF);
      }

      Serial.println("*********");
      Serial.print("Received raw Value: ");
      Serial.println(inputMessage);
      Serial.println("*********");
    }
  }
};

/*
 * MAIN
 */

void setup()
{
#if defined(DEBUG)
  Serial.begin(115200);
#endif

  // Setup led control
  pinMode(LED_COLD, OUTPUT);
  pinMode(LED_WARM, OUTPUT);

  // Create the BLE Device
  BLEDevice::init("MeetingSignal");

  // Create the BLE Server
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  // Create the BLE Service
  BLEService *pService = pServer->createService(SERVICE_UUID);

  // Meeting state characteristic
  pService->addCharacteristic(&meetingStateCharacterisitic);
  meetingStateDescriptor.setValue("BLE Led state");
  meetingStateCharacterisitic.setCallbacks(new MyCallbacks());
  meetingStateCharacterisitic.addDescriptor(&meetingStateDescriptor);

  // Battery level characteristic
  pService->addCharacteristic(&batteryLevelCharacterisitic);
  batteryLevelDescriptor.setValue("BLE Battery level");
  batteryLevelCharacterisitic.addDescriptor(&batteryLevelDescriptor);

  // Start the service
  pService->start();

  // Start advertising
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();
  Serial.println("Waiting a client connection to notify...");
}

void loop()
{
  // connected
  if (deviceConnected)
  {
    Serial.println("send bluetooth...");

    meetingStateCharacterisitic.setValue(led_currentState.c_str());
    meetingStateCharacterisitic.notify();

    delay(2000);

    int analogValue = analogRead(INPUT_BATT);
    batt_level = getBatteryLevel(analogValue);
    static char battByte[6];
    dtostrf(batt_level, 6, 2, battByte);
    Serial.println(battByte);

    batteryLevelCharacterisitic.setValue(battByte);
    batteryLevelCharacterisitic.notify();

    delay(2000); // bluetooth stack will go into congestion, if too many packets are sent
  }

  // disconnecting
  if (!deviceConnected && oldDeviceConnected)
  {
    delay(500);                    // give the bluetooth stack the chance to get things ready
    BLEDevice::startAdvertising(); // restart advertising
    Serial.println("start advertising");
    oldDeviceConnected = deviceConnected;
    disconnectedAlert();
  }

  // connecting
  if (deviceConnected && !oldDeviceConnected)
  {
    oldDeviceConnected = deviceConnected;
    connectedAlert();
  }

  // advertising
  if (!deviceConnected && !oldDeviceConnected)
  {
    waitingStatusAlert();
  }
}