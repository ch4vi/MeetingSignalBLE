package com.ch4vi.meetingsignal.bluetooth

object Constants {
    /** UUID of the Client Characteristic Configuration Descriptor (0x2902). */
    const val CLIENT_CHARACTERISTIC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805F9B34FB"
    const val MEETING_CHARACTERISTIC_UUID = "00002AE2-0000-1000-8000-00805F9B34FB"
    const val BATTERY_CHARACTERISTIC_UUID = "00002A19-0000-1000-8000-00805F9B34FB"

    const val GATT_MIN_MTU_SIZE = 23

    /** Maximum BLE MTU size as defined in gatt_api.h. */
    const val GATT_MAX_MTU_SIZE = 517

    const val MEETING_ON = "m"
    const val MEETING_OFF = "x"
}

class End(private val skip: Boolean = false) {
    fun doAfter(b: () -> Unit) = if (!skip) b() else Unit
}
