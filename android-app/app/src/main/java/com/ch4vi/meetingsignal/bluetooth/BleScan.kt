package com.ch4vi.meetingsignal.bluetooth

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat.getSystemService
import com.ch4vi.meetingsignal.entities.BluetoothDeviceMapper
import com.ch4vi.meetingsignal.entities.Failure
import timber.log.Timber

@SuppressLint("MissingPermission")
class BleScan(private val activity: ComponentActivity) {

    companion object {
        private const val SCAN_PERIOD: Long = 20000 // 20 seconds
    }

    var listener: BleScanListener? = null

    private val bluetoothPermission = BluetoothPermission(activity)
    private val bluetoothManager by lazy {
        getSystemService(activity, BluetoothManager::class.java)
    }
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private var scanning = false
        set(value) {
            field = value
            listener?.onStateChanged(value)
        }
    private val handler = Handler(Looper.getMainLooper())
    private val deviceMapper = BluetoothDeviceMapper

    private var resultLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                run()
            }
        }

    private val scanCallback = object : ScanCallback() {
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            Timber.d("here")
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Timber.e("Scanner failed with error $errorCode")
            listener?.onScanFailure(Failure.ScanFailure("code $errorCode"))
        }

        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            deviceMapper.map(result?.device)?.let { device ->
                listener?.onScanResult(device)
            }
        }
    }

    private fun enable() {
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            resultLauncher.launch(enableBtIntent)
        }
    }

    private fun scanLeDevice(filterAddress: String?) {
        val filters = filterAddress?.let {
            val filter = ScanFilter.Builder().setDeviceAddress(filterAddress).build()
            listOf(filter)
        }

        val bluetoothLeScanner = bluetoothManager?.adapter?.bluetoothLeScanner
        if (!scanning) { // Stops scanning after a pre-defined scan period.
            handler.postDelayed(::stop, SCAN_PERIOD)
            scanning = true
            bluetoothLeScanner?.startScan(filters, scanSettings, scanCallback)
        } else stop()
    }

    fun run(filterAddress: String? = null) {
        bluetoothPermission.onResult = { granted ->
            if (granted) {
                when (bluetoothAdapter?.isEnabled) {
                    true -> scanLeDevice(filterAddress)
                    false -> enable()
                    null -> listener?.onScanFailure(Failure.NotSupportedFailure())
                }
            } else listener?.onScanFailure(Failure.PermissionFailure())
        }
        bluetoothPermission.requestPermission(activity)
    }

    fun stop() {
        if (scanning) {
            bluetoothManager?.adapter?.bluetoothLeScanner?.let {
                scanning = false
                it.stopScan(scanCallback)
            }
        }
    }
}
