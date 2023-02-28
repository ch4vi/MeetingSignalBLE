package com.ch4vi.meetingsignal.bluetooth

import com.ch4vi.meetingsignal.entities.BluetoothDeviceDomainModel

interface BleScanListener {
    fun onScanFailure(error: Throwable)
    fun onScanResult(device: BluetoothDeviceDomainModel)
    fun onStateChanged(isScanning: Boolean)
}
