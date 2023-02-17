package com.ch4vi.meetingsignal.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanRecord
import com.ch4vi.meetingsignal.entities.BluetoothDeviceDomainModel

interface BluetoothLEScanListener {
    fun onScanFailure(error: Throwable)
    fun onScanResult(device: BluetoothDeviceDomainModel)
}
