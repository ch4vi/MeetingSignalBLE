# Meeting Signal Bluethooh Low Energy

![Version](https://img.shields.io/badge/Version-0.1.0-green)
[![Check style](https://github.com/ch4vi/MeetingSignalBLE/actions/workflows/check-style.yml/badge.svg)](https://github.com/ch4vi/MeetingSignalBLE/actions/workflows/check-style.yml)

## Why

Currently I work from home 90% of the time, in this company I have lots of meetings using the camera and I use my headphones all the time ( we have two kids and sometimes the house gets very noisy) and sometimes my wife has to come in to get something from the room, I couple of times she got a bit embarrassed because my camera points to the door, I use the blur effect but she does not like it.

As an engineer I had to build an over complicated solution for a problem that could be resolved with a smart bulb. I started with a 3D model. It was simple, just a box with some holes and some M3 nuts. After that I grabbed an ESP32 that I already had and a led strip from a USB lamp that did not work because of some broken buttons (I bought them on a Flash Sale on Amazon and they did not last more than 2 months). My first idea was to connect the board to the MS Teams API to get my status and just turn the lights on or off depending on my status. While I found a project in which I could re-use [ESPTeamsPresence](https://github.com/toblum/ESPTeamsPresence), the company where I work doesn't allow my user to get its own [Presence](https://learn.microsoft.com/en-us/graph/api/presence-get?view=graph-rest-1.0&tabs=http) status. To solve this I had two options using the ESP32, use Bluetooth or Wifi. I don't have much experience developing on any of those with an ESP32 board. The Wifi option would be similar to the MS Teams API project, but I wanted to make my life harder and proceeded with the Bluetooth option.

## Hardware

- 3D printed models [Thingiverse]()
- ESP32 DEVKIT V1 [Aliexpress](https://www.aliexpress.com/w/wholesale-esp32-devkit-v1.html?catId=0&initiative_id=AS_20230227034951&SearchText=esp32+devkit+v1&spm=a2g0o.productlist.1000002.0)
- Switch
- TP4056 board [Aliexpress](https://www.aliexpress.com/item/1005004444047086.html?spm=a2g0o.productlist.main.1.267b7b31rNElI9&algo_pvid=0a75742f-60ee-45d7-86bb-0546a4c22391&algo_exp_id=0a75742f-60ee-45d7-86bb-0546a4c22391-0&pdp_ext_f=%7B%22sku_id%22%3A%2212000031268385180%22%7D&pdp_npi=3%40dis%21EUR%212.9%211.88%21%21%21%21%21%402145288516774988386518347d06be%2112000031268385180%21sea%21ES%21704501893&curPageLogUid=zywhhtG6sxSh)
- 3.7V LiPo battery [Aliexpress](https://www.aliexpress.com/item/1005001310695209.html?spm=a2g0o.productlist.main.3.28a047d5vpYjNQ&algo_pvid=21665b45-cef0-41bd-8eaf-e5908523fcfa&algo_exp_id=21665b45-cef0-41bd-8eaf-e5908523fcfa-1&pdp_ext_f=%7B%22sku_id%22%3A%2212000015656262817%22%7D&pdp_npi=3%40dis%21EUR%2113.58%218.15%21%21%21%21%21%402100b78b16774991666308982d0714%2112000015656262817%21sea%21ES%21704501893&curPageLogUid=CvSQgTxq4sjm)
- M3 nuts
- Some wires
- 5V LED strip [Aliexpress](https://www.aliexpress.com/item/1005004377378405.html?spm=a2g0o.productlist.main.33.112c6912Wcitqn&algo_pvid=08f9bfd9-a1b8-4f98-a177-aa90fe518e4d&algo_exp_id=08f9bfd9-a1b8-4f98-a177-aa90fe518e4d-16&pdp_ext_f=%7B%22sku_id%22%3A%2212000028965478917%22%7D&pdp_npi=3%40dis%21EUR%214.53%213.35%21%21%21%21%21%402100b84516774992198541301d071b%2112000028965478917%21sea%21ES%21704501893&curPageLogUid=1pvAT3grLcQT)
- 2x 100K resistors [Aliexpress](https://www.aliexpress.com/item/1005003117726705.html?spm=a2g0o.productlist.main.1.4e8b3843eGESSF&algo_pvid=07d86e6d-c8eb-4df2-abc6-c50a2aabcb9d&algo_exp_id=07d86e6d-c8eb-4df2-abc6-c50a2aabcb9d-0&pdp_ext_f=%7B%22sku_id%22%3A%2212000024192658375%22%7D&pdp_npi=3%40dis%21EUR%212.86%212.77%21%21%21%21%21%402100bbf516775020184434105d070f%2112000024192658375%21sea%21ES%21704501893&curPageLogUid=kWlcKLQUfU1Y)


\* *Links are provided as a guide, they are not affiliated links*<br />
** *I already had the ESP board, but if I had to buy a new one I would choose the USB-C version, but for the TP4056 I had the micro-USB version, and the 3D model is adapted to this model.*

## Instructions

<img src="https://github.com/ch4vi/MeetingSignalBLE/blob/feature/xml/resources/circuit_v1.png" width="700">

### ESP32

Very straight forward, it has 2 characteristics, one to control the leds and the other the battery charge. In order to control the leds, the characteristic awaits an input string, **m** if it is to turn on the leds, or any other if it is to turn them off. The battery characteristic works as a notification characteristic, it notifies the battery level on every loop execution. There are some ESP32 boards that integrate battery level measurement. In my case, to get the battery level I followed this [approach](https://iotprojectsideas.com/esp8266-monitor-its-own-battery-level-using-iot/). It works but it is not very precise.

### Android

It needs a better clean-up, but it currently asks for permissions, connects to Bluetooth, and looks for a given MAC address. You have to change this parameter to connect to your device. You can get this address from the Bluetooth screen on your device. Maybe I will implement an additional screen to choose a device from a list (or maybe not ;D ). I have set the minimum SDK to 30 because my device is at this target. If you can, and you have some knowledge of Android development, you can upgrade this and remove some code. This is because currently the Bluetooth API is between 2 versions. There are some deprecations that I have to keep in order to work with my device. Check out this [post](https://punchthrough.com/android-ble-guide/) and their [project](https://github.com/PunchThrough/ble-starter-android) if you're interested in learning about Android Bluetooth API.

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
