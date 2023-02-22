package com.ch4vi.meetingsignal.bluetooth

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile

/**
 * The only reason this class exists is because we are using deprecated and not deprecated code
 * depending on the Android API. We are writing descriptors and characteristics using an extension
 * here [BluetoothGatt.write], doing it like this will be easier in the future to replace the
 * deprecated code when minSDK >= TIRAMISU
 */

@Suppress("OVERRIDE_DEPRECATION")
open class GattCallbackWrapper : BluetoothGattCallback() {
    sealed class ConnectionState(val status: Int) {
        class Connected(status: Int) : ConnectionState(status)
        class Disconnected(status: Int) : ConnectionState(status)
        class Other(status: Int) : ConnectionState(status)
    }

    enum class GattStatus {
        SUCCESS,
        READ_NOT_PERMITTED,
        WRITE_NOT_PERMITTED,
        OTHER,
    }

    private fun Int.toGattStatus(): GattStatus {
        return when (this) {
            BluetoothGatt.GATT_SUCCESS -> GattStatus.SUCCESS
            BluetoothGatt.GATT_READ_NOT_PERMITTED -> GattStatus.READ_NOT_PERMITTED
            BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> GattStatus.WRITE_NOT_PERMITTED
            else -> GattStatus.OTHER
        }
    }

    open fun onConnectionStateChange(gatt: BluetoothGatt, state: ConnectionState) {}
    open fun onServicesDiscovered(gatt: BluetoothGatt, status: GattStatus) {}
    open fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: GattStatus) {}
    open fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: GattStatus
    ) {
    }

    open fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: GattStatus
    ) {
    }

    open fun onCharacteristicUpdated(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
    }

    open fun onDescriptorRead(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
        status: GattStatus
    ) {
    }

    open fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
        status: GattStatus
    ) {
    }


    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        when {
            status == BluetoothGatt.GATT_SUCCESS &&
                    newState == BluetoothProfile.STATE_CONNECTED -> {
                onConnectionStateChange(gatt, ConnectionState.Connected(status))
            }
            status == BluetoothGatt.GATT_SUCCESS &&
                    newState == BluetoothProfile.STATE_DISCONNECTED -> {
                onConnectionStateChange(gatt, ConnectionState.Disconnected(status))
            }
            else -> {
                onConnectionStateChange(gatt, ConnectionState.Other(status))
            }
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) =
        onServicesDiscovered(gatt, status.toGattStatus())

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) =
        onMtuChanged(gatt, mtu, status.toGattStatus())

    override fun onCharacteristicRead(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        if (gatt != null && characteristic != null) {
            onCharacteristicRead(
                gatt,
                characteristic,
                characteristic.value,
                status.toGattStatus()
            )
        }
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) = onCharacteristicRead(gatt, characteristic, value, status.toGattStatus())


    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) = onCharacteristicWrite(gatt, characteristic, characteristic.value, status.toGattStatus())


    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?
    ) {
        if (gatt != null && characteristic != null) {
            this@GattCallbackWrapper.onCharacteristicChanged(
                gatt,
                characteristic,
                characteristic.value
            )
        }
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) = onCharacteristicUpdated(gatt, characteristic, value)

    override fun onDescriptorRead(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    ) {
        if (gatt != null && descriptor != null) {
            onDescriptorRead(gatt, descriptor, descriptor.value, status.toGattStatus())
        }
    }

    override fun onDescriptorRead(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int,
        value: ByteArray
    ) = onDescriptorRead(gatt, descriptor, value, status.toGattStatus())

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) = onDescriptorWrite(gatt, descriptor, descriptor.value, status.toGattStatus())

}
