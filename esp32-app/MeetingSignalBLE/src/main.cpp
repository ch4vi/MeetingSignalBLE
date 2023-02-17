#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

BLEServer *pServer = NULL;
BLECharacteristic *pCharacteristic = NULL;
bool deviceConnected = false;
bool oldDeviceConnected = false;
uint8_t value = 0;

// See the following for generating UUIDs:
// https://www.uuidgenerator.net/
#define SERVICE_UUID "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define CHARACTERISTIC_UUID2 "00002a19-0000-1000-8000-00805f9b34fb"

float batt;
BLECharacteristic batteryLevelCharacterisitic(CHARACTERISTIC_UUID2, BLECharacteristic::PROPERTY_NOTIFY | BLECharacteristic::PROPERTY_INDICATE);
BLEDescriptor batteryLevelDescriptor(BLEUUID((uint16_t)0x2902));

// LED Pins
const uint8_t LED_WARM = 13; // 13 corresponds to GPIO13
const uint8_t LED_COLD = 12; // 12 corresponds to GPIO12
#define LED_WARM LED_WARM
#define LED_COLD LED_COLD

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
      Serial.println("*********");
      Serial.print("Received Value: ");
      for (int i = 0; i < rxValue.length(); i++)
        Serial.print(rxValue[i]);

      Serial.println();
      Serial.println("*********");
    }
  }
};

/* LED control functions */

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

void setup()
{
  Serial.begin(115200);

  // Setup led control
  pinMode(LED_COLD, OUTPUT);
  pinMode(LED_WARM, OUTPUT);

  // Create the BLE Device
  BLEDevice::init("MyESP32");

  // Create the BLE Server
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  // Create the BLE Service
  BLEService *pService = pServer->createService(SERVICE_UUID);

  // Create a BLE Characteristic
  pCharacteristic = pService->createCharacteristic(
      CHARACTERISTIC_UUID,
      BLECharacteristic::PROPERTY_READ |
          BLECharacteristic::PROPERTY_WRITE |
          BLECharacteristic::PROPERTY_NOTIFY |
          BLECharacteristic::PROPERTY_INDICATE);

  pCharacteristic->setCallbacks(new MyCallbacks());

  // https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.descriptor.gatt.client_characteristic_configuration.xml
  // Create a BLE Descriptor
  pCharacteristic->addDescriptor(new BLE2902());

  // Battery level characteristic
  pService->addCharacteristic(&batteryLevelCharacterisitic);
  batteryLevelDescriptor.setValue("BME temperature Celsius");
  batteryLevelCharacterisitic.addDescriptor(&batteryLevelDescriptor);

  // Start the service
  pService->start();

  // Start advertising
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06); // functions that help with iPhone connections issue
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();
  Serial.println("Waiting a client connection to notify...");
}

void loop()
{
  // notify changed value
  if (deviceConnected)
  {
    Serial.print("send bluetooth...");
    Serial.println();

    String s = "notification string";
    pCharacteristic->setValue(s.c_str());
    pCharacteristic->notify();

    delay(2000);

    batt = 100;
    static char battByte[6];
    dtostrf(batt, 6, 2, battByte);
    Serial.print(batt);

    batteryLevelCharacterisitic.setValue(battByte);
    batteryLevelCharacterisitic.notify();

    value++;
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
    Serial.println("here 1");
    oldDeviceConnected = deviceConnected;
    connectedAlert();
  }

  if (!deviceConnected && !oldDeviceConnected)
  {
    Serial.println("here 2");
    waitingStatusAlert();
  }
}