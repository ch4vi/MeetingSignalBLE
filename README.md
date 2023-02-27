# Meeting Signal Bluethooh Low Energy


[![CI](https://github.com/ch4vi/MeetingSignalBLE/actions/workflows/github.yml/badge.svg)](https://github.com/ch4vi/MeetingSignalBLE/actions/workflows/github.yml)

## Why

Currently I work from home 90% of the time, in this company I have lots of meetings using the camera and I use my headphones all the time ( we have two kids and sometimes the house gets very noisy) and sometimes my wife has to come in to get something from the room, I couple of times she got a bit embarrased because my camera points to the door, I use the blur effect but she does not like it. 

As an engineer I had to build an over complicated solution for a problem that could be resolved with a smart bulb. I started with the 3D models, it was simple, just a box with some holes and some M3 nuts. 
After that I grabbed an ESP32 that I already had and a led strip from a USB lamp that did not work because of some broken buttons (I got them on a Flash Sale in Amazon and they did not last more than 2 months). My first idea was to connect the board to the MS Teams API to get my status and just turn the leds on or off depending on my status, I found a project I could re-use [ESPTeamsPresence](https://github.com/toblum/ESPTeamsPresence) but in the company where I work, my user does not has rights to get its own status [Presence](https://learn.microsoft.com/en-us/graph/api/presence-get?view=graph-rest-1.0&tabs=http). To solve this I had two options using the ESP32, use bluetooth or Wifi. I don't have much experience developing on any of those with an ESP32 board. The Wifi option would be similar to the MS Teams API project, but I wanted to make my life harder and go with the bluetooth option. 


## Hardware

- 3D printed models [thingiverse]()
- ESP32 DEVKIT V1 [Aliexpress](https://www.aliexpress.com/w/wholesale-esp32-devkit-v1.html?catId=0&initiative_id=AS_20230227034951&SearchText=esp32+devkit+v1&spm=a2g0o.productlist.1000002.0)
- Switch
- TP4056 board [Aliexpress](https://www.aliexpress.com/item/1005004444047086.html?spm=a2g0o.productlist.main.1.267b7b31rNElI9&algo_pvid=0a75742f-60ee-45d7-86bb-0546a4c22391&algo_exp_id=0a75742f-60ee-45d7-86bb-0546a4c22391-0&pdp_ext_f=%7B%22sku_id%22%3A%2212000031268385180%22%7D&pdp_npi=3%40dis%21EUR%212.9%211.88%21%21%21%21%21%402145288516774988386518347d06be%2112000031268385180%21sea%21ES%21704501893&curPageLogUid=zywhhtG6sxSh)
- 3.7V LiPo battery [Aliexpress](https://www.aliexpress.com/item/1005001310695209.html?spm=a2g0o.productlist.main.3.28a047d5vpYjNQ&algo_pvid=21665b45-cef0-41bd-8eaf-e5908523fcfa&algo_exp_id=21665b45-cef0-41bd-8eaf-e5908523fcfa-1&pdp_ext_f=%7B%22sku_id%22%3A%2212000015656262817%22%7D&pdp_npi=3%40dis%21EUR%2113.58%218.15%21%21%21%21%21%402100b78b16774991666308982d0714%2112000015656262817%21sea%21ES%21704501893&curPageLogUid=CvSQgTxq4sjm)
- M3 nuts
- Some wires
- 5V led strip [Aliexpress](https://www.aliexpress.com/item/1005004377378405.html?spm=a2g0o.productlist.main.33.112c6912Wcitqn&algo_pvid=08f9bfd9-a1b8-4f98-a177-aa90fe518e4d&algo_exp_id=08f9bfd9-a1b8-4f98-a177-aa90fe518e4d-16&pdp_ext_f=%7B%22sku_id%22%3A%2212000028965478917%22%7D&pdp_npi=3%40dis%21EUR%214.53%213.35%21%21%21%21%21%402100b84516774992198541301d071b%2112000028965478917%21sea%21ES%21704501893&curPageLogUid=1pvAT3grLcQT)
- 2x 100K resistors [Aliexpress](https://www.aliexpress.com/item/1005003117726705.html?spm=a2g0o.productlist.main.1.4e8b3843eGESSF&algo_pvid=07d86e6d-c8eb-4df2-abc6-c50a2aabcb9d&algo_exp_id=07d86e6d-c8eb-4df2-abc6-c50a2aabcb9d-0&pdp_ext_f=%7B%22sku_id%22%3A%2212000024192658375%22%7D&pdp_npi=3%40dis%21EUR%212.86%212.77%21%21%21%21%21%402100bbf516775020184434105d070f%2112000024192658375%21sea%21ES%21704501893&curPageLogUid=kWlcKLQUfU1Y)


\* *Links for reference, they are not affiliated links*<br />
** *I already had the ESP board but if I had to buy a new one I would chose the USB-C version, but for the TP4056 I had the micro-USB version, and the 3D model is adapted to this version, maybe I make the USB-C version.*

## Instructions

<img src="https://github.com/ch4vi/MeetingSignalBLE/blob/feature/xml/resources/circuit_v1.png" width="700">

### ESP32

Very straight forward, it has 2 characteristics, one to control the leds and the other the battery charge. The charasteristics to control the leds waits for an input string, **m** to turn on the leds, any other to turn them off. The battery charasteristic works as a notification characterisitic, it notifies the battery level on every loop execution.
There are some ESP32 boards that integrate battery level measurement, on my case, to get the battery level I followed this [approach](https://iotprojectsideas.com/esp8266-monitor-its-own-battery-level-using-iot/), It works but it is not very precise.

### Android

It needs a better clean up, but the app ask for permissions, tuns on the bluetooth and looks for a MAC address to connect. You have to change this parameter to connect to your device. You can get this addres from the bluetooth screen on your device. Maybe I will implement a new screen to choose a device from a list (or maybe not ;D ). I have set the minumum SDK to 30 because my device is on this target, if you can, and you have some knowdelege on Android development, you can upgrade this and remove some code because currently the Blouetooth API is between 2 versions and there are some deprecations that I have to keep to be able to work with my device. 
If youy have interest on learn about the Android Bluetooth API I recommend to check this [post](https://punchthrough.com/android-ble-guide/) and their [project](https://github.com/PunchThrough/ble-starter-android) it was super helpful to me.

# Reference links:

* https://punchthrough.com/android-ble-guide/
* https://github.com/PunchThrough/ble-starter-android
* https://github.com/toblum/ESPTeamsPresence
* https://randomnerdtutorials.com/esp32-bluetooth-low-energy-ble-arduino-ide/
* https://github.com/kakopappa/bluetooth_android_esp32_example
* https://btprodspecificationrefs.blob.core.windows.net/assigned-numbers/Assigned%20Number%20Types/Assigned%20Numbers.pdf
* https://iotprojectsideas.com/esp8266-monitor-its-own-battery-level-using-iot/
* https://randomnerdtutorials.com/esp32-ble-server-client/#ESP32-BLE-Server
* https://docs.platformio.org/en/stable/tutorials/espressif32/arduino_debugging_unit_testing.html#writing-unit-tests

# LICENSE

    Meeting Signal BLE
    Copyright (C) 2023  Xavi Anyo

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, version 3 of the License, or any later 
    version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
