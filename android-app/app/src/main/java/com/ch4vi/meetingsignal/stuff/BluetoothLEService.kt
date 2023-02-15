package com.ch4vi.meetingsignal.stuff


import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.ch4vi.meetingsignal.stuff.BluetoothLEService.Action.GATT_CONNECTED
import com.ch4vi.meetingsignal.stuff.BluetoothLEService.Action.GATT_DATA_AVAILABLE
import com.ch4vi.meetingsignal.stuff.BluetoothLEService.Action.GATT_DISCONNECTED
import com.ch4vi.meetingsignal.stuff.BluetoothLEService.Action.GATT_SERVICES_DISCOVERED
import timber.log.Timber
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothLEService : Service() {

    companion object {
        const val CHARACTERISTIC_VALUE = "characteristicValue"
    }

    private val bluetoothManager by lazy { getSystemService(BluetoothManager::class.java) }
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private var bluetoothDeviceAddress: String? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private val binder: IBinder = LocalBinder()
    private val uuidNotify = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    enum class Action(val action: String) {
        GATT_CONNECTED("gattConnected"),
        GATT_DISCONNECTED("gattDisconnected"),
        GATT_SERVICES_DISCOVERED("gattServicesDiscovered"),
        GATT_DATA_AVAILABLE("gattDataAvailable"),
        ;

        fun get(s: String): Action? = Action.values().firstOrNull { it.action == s }
    }

    private val gattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                broadcastUpdate(GATT_CONNECTED.action)
                Timber.i("Connected to GATT server.")
                bluetoothGatt?.discoverServices()
                Timber.i("Attempting to start service discovery:")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                broadcastUpdate(GATT_DISCONNECTED.action)
                Timber.w("Disconnected from GATT server.")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(GATT_SERVICES_DISCOVERED.action)
            } else {
                Timber.e("onServicesDiscovered received : $status")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.i("onCharacteristicRead()")
                broadcastUpdate(GATT_DATA_AVAILABLE.action, value)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            Timber.i("onCharacteristicChanged()")
            broadcastUpdate(GATT_DATA_AVAILABLE.action, value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Timber.i("onCharacteristicWrite()")
            super.onCharacteristicWrite(gatt, characteristic, status)
        }

        private fun broadcastUpdate(action: String) {
            val intent = Intent(action)
            sendBroadcast(intent)
        }

        private fun broadcastUpdate(action: String, value: ByteArray) {
            val intent = Intent(action).apply {
                putExtra(CHARACTERISTIC_VALUE, value)
            }
            sendBroadcast(intent)
        }
    }

    private inner class LocalBinder : Binder() {
        val service: BluetoothLEService
            get() = this@BluetoothLEService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }

    private fun initialize(): Boolean {
        if (!BluetoothPermission.checkPermissions(applicationContext)) {
            Timber.e("Mandatory permissions not granted, run BluetoothScan first")
            return false
        }

        if (bluetoothManager == null) {
            Timber.e("Unable to initialize BluetoothManager.")
            return false
        }

        if (bluetoothAdapter == null) {
            Timber.e("Unable to obtain a BluetoothAdapter.")
            return false
        }

        Timber.i("Initialize BluetoothLEService success!")
        return true
    }

    private fun connect(address: String): Boolean {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Timber.e("BluetoothAdapter not initialized")
            return false
        }

        val deviceAddress = bluetoothDeviceAddress
        val gatt = bluetoothGatt
        if (deviceAddress != null && address == deviceAddress && gatt != null) {
            Timber.w("Trying to use an existing BluetoothGatt for connection.")
            return gatt.connect()
        }

        val device = adapter.getRemoteDevice(address)
        if (device == null) {
            Timber.e("Device not found or unable to connect.")
            return false
        }

        bluetoothGatt = device.connectGatt(this, false, gattCallback)
        Timber.w("Trying to create a new connection.")
        bluetoothDeviceAddress = address
        return true
    }

    fun disconnect() {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            Timber.e("BluetoothAdapter not initialized")
            return
        }
        bluetoothGatt?.disconnect()
    }

    fun close() {
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    fun getSupportedGattServices(): List<BluetoothGattService>? {
        val gatt = bluetoothGatt ?: return null
        val gattServices = gatt.services

        for (gattService in gattServices) {
            val gattCharacteristics = gattService.characteristics
            for (gattCharacteristic in gattCharacteristics) {
                val uuid = gattCharacteristic.uuid.toString()
                Timber.i("uuid : $uuid")
                if (uuid.equals(uuidNotify.toString(), ignoreCase = true)) {
                    notifyCharacteristic = gattCharacteristic
                    gatt.setCharacteristicNotification(gattCharacteristic, true)
                    Timber.i("setCharacteristicNotification : $uuid")

                    val magicUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                    writeDescriptor(gatt, gattCharacteristic.getDescriptor(magicUUID))
                }
            }
        }
        return gattServices
    }

    private fun writeDescriptor(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }

    fun writeCharacteristic(data: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifyCharacteristic?.let { characteristic ->
                bluetoothGatt?.writeCharacteristic(
                    characteristic,
                    data,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            }
        } else {
            notifyCharacteristic?.run {
                value = data
                bluetoothGatt?.writeCharacteristic(this)
            }
        }
    }
}
