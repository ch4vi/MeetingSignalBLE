package com.ch4vi.meetingsignal.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor

/** A listener containing callback methods to be registered with [ConnectionManager].*/
class ConnectionEventListener {
    var onConnectionSetupComplete: ((BluetoothGatt) -> Unit)? = null
    var onDisconnect: ((BluetoothDevice) -> Unit)? = null
    var onDescriptorRead: ((BluetoothDevice, BluetoothGattDescriptor, ByteArray) -> Unit)? = null
    var onDescriptorWrite: ((BluetoothDevice, BluetoothGattDescriptor, ByteArray) -> Unit)? = null
    var onCharacteristicChanged: ((BluetoothDevice, BluetoothGattCharacteristic, ByteArray) -> Unit)? = null
    var onCharacteristicRead: ((BluetoothDevice, BluetoothGattCharacteristic, ByteArray) -> Unit)? = null
    var onCharacteristicWrite: ((BluetoothDevice, BluetoothGattCharacteristic, ByteArray) -> Unit)? = null
    var onNotificationsEnabled: ((BluetoothDevice, BluetoothGattCharacteristic) -> Unit)? = null
    var onNotificationsDisabled: ((BluetoothDevice, BluetoothGattCharacteristic) -> Unit)? = null
    var onMtuChanged: ((BluetoothDevice, Int) -> Unit)? = null
}
