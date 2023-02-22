package com.ch4vi.meetingsignal.entities

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Parcelable
import com.ch4vi.meetingsignal.utils.Mapper
import kotlinx.parcelize.Parcelize

@Parcelize
data class BluetoothDeviceDomainModel(
    val address: String,
    val name: String?,
    val value: BluetoothDevice
) : Parcelable {
    override fun toString(): String {
        return "n: ${name ?: "unknown"} adr: $address"
    }
}

@SuppressLint("MissingPermission")
object BluetoothDeviceMapper : Mapper<BluetoothDevice?, BluetoothDeviceDomainModel?> {
    override fun map(dto: BluetoothDevice?): BluetoothDeviceDomainModel? {
        dto ?: return null
        return dto.address?.let { address ->
            BluetoothDeviceDomainModel(address, dto.name, dto)
        }
    }
}
