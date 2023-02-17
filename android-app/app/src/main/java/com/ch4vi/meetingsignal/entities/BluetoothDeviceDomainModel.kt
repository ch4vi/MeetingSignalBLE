package com.ch4vi.meetingsignal.entities

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Parcelable
import com.ch4vi.meetingsignal.utils.Mapper
import kotlinx.parcelize.Parcelize
import timber.log.Timber

@Parcelize
data class BluetoothDeviceDomainModel(
    val address: String,
    val name: String?
) : Parcelable {
    override fun toString(): String {
        return "n: ${name ?: "unknown"} adr: $address"
    }
}

@SuppressLint("MissingPermission")
object BluetoothDeviceMapper : Mapper<BluetoothDevice?, BluetoothDeviceDomainModel?> {
    override fun map(dto: BluetoothDevice?): BluetoothDeviceDomainModel? {
        return dto?.address?.let { address ->
            val uuid = dto.uuids
            Timber.d(uuid?.joinToString(", "))
            BluetoothDeviceDomainModel(address, dto.name)
        }
    }
}
