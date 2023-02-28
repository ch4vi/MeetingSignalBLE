package com.ch4vi.meetingsignal.bluetooth

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.ch4vi.meetingsignal.bluetooth.BleService.Action.BATTERY_LEVEL_UPDATED
import com.ch4vi.meetingsignal.bluetooth.BleService.Action.BATTERY_NOTIFY_DISABLED
import com.ch4vi.meetingsignal.bluetooth.BleService.Action.BATTERY_NOTIFY_ENABLED
import com.ch4vi.meetingsignal.bluetooth.BleService.Action.GATT_CONNECTED
import com.ch4vi.meetingsignal.bluetooth.BleService.Action.GATT_DISCONNECTED
import com.ch4vi.meetingsignal.bluetooth.BleService.Action.MEETING_STATUS_READ
import com.ch4vi.meetingsignal.bluetooth.BleService.Action.MEETING_STATUS_WRITE
import com.ch4vi.meetingsignal.bluetooth.Constants.BATTERY_CHARACTERISTIC_UUID
import com.ch4vi.meetingsignal.bluetooth.Constants.MEETING_CHARACTERISTIC_UUID
import timber.log.Timber

@SuppressLint("MissingPermission")
class BleService : Service() {

    private val binder: IBinder = LocalBinder()

    enum class Action(val action: String) {
        GATT_CONNECTED("meetingsignal.ACTION_GATT_CONNECTED"),
        GATT_DISCONNECTED("meetingsignal.ACTION_GATT_DISCONNECTED"),
        BATTERY_LEVEL_UPDATED("meetingsignal.BATTERY_LEVEL_UPDATED"),
        BATTERY_NOTIFY_ENABLED("meetingsignal.BATTERY_NOTIFY_ENABLED"),
        BATTERY_NOTIFY_DISABLED("meetingsignal.BATTERY_NOTIFY_DISABLED"),
        MEETING_STATUS_WRITE("meetingsignal.MEETING_STATUS_WRITE"),
        MEETING_STATUS_READ("meetingsignal.MEETING_STATUS_READ"),
        ;
    }

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onConnectionSetupComplete = {
                sendBroadcast(Intent(GATT_CONNECTED.action))
            }
            onDisconnect = {
                sendBroadcast(Intent(GATT_DISCONNECTED.action))
            }

            onCharacteristicRead = { _, characteristic, value ->
                with(characteristic) {
                    sendBroadcastUpdate(uuid.toString(), value)
                }
            }

            onCharacteristicWrite = { _, characteristic, value ->
                with(characteristic) {
                    Timber.d("Wrote to $uuid, ${value.decodeToString()}")
                    when (uuid.lowercase()) {
                        MEETING_CHARACTERISTIC_UUID.lowercase() ->
                            sendBroadcast(Intent(MEETING_STATUS_WRITE.action))
                        else -> Unit
                    }
                }
            }

            onMtuChanged = { _, mtu ->
                Timber.d("MTU updated to $mtu")
            }

            onCharacteristicChanged = { _, characteristic, value ->
                with(characteristic) {
                    sendBroadcastUpdate(uuid.toString(), value)
                }
            }

            onNotificationsEnabled = { _, characteristic ->
                with(characteristic) {
                    when (uuid.lowercase()) {
                        BATTERY_CHARACTERISTIC_UUID.lowercase() ->
                            sendBroadcast(Intent(BATTERY_NOTIFY_ENABLED.action))
                        else -> Unit
                    }
                }
            }

            onNotificationsDisabled = { _, characteristic ->
                with(characteristic) {
                    when (uuid.lowercase()) {
                        BATTERY_CHARACTERISTIC_UUID.lowercase() ->
                            sendBroadcast(Intent(BATTERY_NOTIFY_DISABLED.action))
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun sendBroadcastUpdate(uuid: String, value: ByteArray) {
        when (uuid.lowercase()) {
            BATTERY_CHARACTERISTIC_UUID.lowercase() -> {
                Intent(BATTERY_LEVEL_UPDATED.action).apply {
                    putExtra(BATTERY_LEVEL_UPDATED.action, value)
                }
            }
            MEETING_CHARACTERISTIC_UUID.lowercase() -> {
                Intent(MEETING_STATUS_READ.action).apply {
                    putExtra(MEETING_STATUS_READ.action, value)
                }
            }
            else -> null
        }?.let {
            sendBroadcast(it)
        } ?: run {
            Timber.d("Could not send broadcast for $uuid with value ${value.decodeToString()} ${value.toHexString()}")
        }
    }

    inner class LocalBinder : Binder() {
        val service: BleService
            get() = this@BleService
    }

    override fun onBind(intent: Intent?): IBinder {
        ConnectionManager.registerListener(connectionEventListener)
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        ConnectionManager.unregisterListener(connectionEventListener)
        return super.onUnbind(intent)
    }

    fun connect(device: BluetoothDevice) {
        ConnectionManager.connect(device, this)
    }

    fun disconnect(device: BluetoothDevice) {
        ConnectionManager.teardownConnection(device)
    }

    fun subscribeToBatteryLevel(device: BluetoothDevice) {
        ConnectionManager.servicesOnDevice(device)?.firstNotNullOfOrNull {
            it.getCharacteristic(BATTERY_CHARACTERISTIC_UUID.toUuid())
        }?.let { characteristic ->
            ConnectionManager.enableNotifications(device, characteristic)
        }
    }

    fun unsubscribeToBatteryLevel(device: BluetoothDevice) {
        ConnectionManager.servicesOnDevice(device)?.firstNotNullOfOrNull {
            it.getCharacteristic(BATTERY_CHARACTERISTIC_UUID.toUuid())
        }?.let { characteristic ->
            ConnectionManager.disableNotifications(device, characteristic)
        }
    }

    fun writeMeetingStatus(device: BluetoothDevice, bytes: ByteArray) {
        ConnectionManager.servicesOnDevice(device)?.firstNotNullOfOrNull {
            it.getCharacteristic(MEETING_CHARACTERISTIC_UUID.toUuid())
        }?.let { characteristic ->
            ConnectionManager.writeCharacteristic(device, characteristic, bytes)
        }
    }

    fun readMeetingStatus(device: BluetoothDevice) {
        ConnectionManager.servicesOnDevice(device)?.firstNotNullOfOrNull {
            it.getCharacteristic(MEETING_CHARACTERISTIC_UUID.toUuid())
        }?.let { characteristic ->
            ConnectionManager.readCharacteristic(device, characteristic)
        }
    }
}
