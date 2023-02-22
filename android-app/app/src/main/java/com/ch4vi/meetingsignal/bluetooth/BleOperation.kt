package com.ch4vi.meetingsignal.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import java.util.UUID


/** Sealed class containing types of BLE operations */
@SuppressLint("MissingPermission")
sealed class BleOperation(open val device: BluetoothDevice) {

    data class Connect(
        override val device: BluetoothDevice,
        val context: Context
    ) : BleOperation(device)

    data class Disconnect(override val device: BluetoothDevice) : BleOperation(device)

    @Suppress("ArrayInDataClass")
    data class CharacteristicWrite(
        override val device: BluetoothDevice,
        val characteristicUuid: UUID,
        val writeType: Int,
        val payload: ByteArray
    ) : BleOperation(device)

    data class CharacteristicRead(
        override val device: BluetoothDevice,
        val characteristicUuid: UUID
    ) : BleOperation(device)

    @Suppress("ArrayInDataClass")
    data class DescriptorWrite(
        override val device: BluetoothDevice,
        val descriptorUuid: UUID,
        val payload: ByteArray
    ) : BleOperation(device)

    data class DescriptorRead(
        override val device: BluetoothDevice,
        val descriptorUuid: UUID
    ) : BleOperation(device)

    data class EnableNotifications(
        override val device: BluetoothDevice,
        val characteristicUuid: UUID
    ) : BleOperation(device)

    data class DisableNotifications(
        override val device: BluetoothDevice,
        val characteristicUuid: UUID
    ) : BleOperation(device)

    data class MtuRequest(
        override val device: BluetoothDevice,
        val mtu: Int
    ) : BleOperation(device)

    companion object {
        fun callback(
            operation: BleOperation,
            onConnect: Connect.() -> End,
            onDisconnect: Disconnect.() -> End,
            onCharacteristicWrite: CharacteristicWrite.() -> End,
            onCharacteristicRead: CharacteristicRead.() -> End,
            onDescriptorWrite: DescriptorWrite.() -> End,
            onDescriptorRead: DescriptorRead.() -> End,
            onEnableNotifications: EnableNotifications.() -> End,
            onDisableNotifications: DisableNotifications.() -> End,
            onMtuRequest: MtuRequest.() -> End,
        ): End {
            return when (operation) {
                is Connect -> onConnect(operation)
                is Disconnect -> onDisconnect(operation)
                is CharacteristicWrite -> onCharacteristicWrite(operation)
                is CharacteristicRead -> onCharacteristicRead(operation)
                is DescriptorWrite -> onDescriptorWrite(operation)
                is DescriptorRead -> onDescriptorRead(operation)
                is EnableNotifications -> onEnableNotifications(operation)
                is DisableNotifications -> onDisableNotifications(operation)
                is MtuRequest -> onMtuRequest(operation)
            }
        }
    }
}
