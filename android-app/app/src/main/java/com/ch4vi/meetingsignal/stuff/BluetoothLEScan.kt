package com.ch4vi.meetingsignal.stuff

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat.getSystemService
import com.ch4vi.meetingsignal.entities.Failure
import timber.log.Timber

interface BluetoothScanListener {
    fun onScanFailure(failure: Failure)
    fun onScanResult(device: BluetoothDevice?, rssi: Int, scanRecord: ScanRecord?)
}

class BluetoothLEScan(private val activity: ComponentActivity) {

    companion object {
        private const val SCAN_PERIOD: Long = 20000 // 20 seconds
    }

    var listener: BluetoothScanListener? = null

    private val bluetoothPermission = BluetoothPermission(activity)
    private val bluetoothManager by lazy {
        getSystemService(activity, BluetoothManager::class.java)
    }
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())

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
            result?.let {
                listener?.onScanResult(it.device, it.rssi, it.scanRecord)
            }
        }
    }

    fun run() {
        bluetoothPermission.onResult = { granted ->
            if (granted) {
                when (bluetoothAdapter?.isEnabled) {
                    true -> scanLeDevice()
                    false -> enable()
                    null -> listener?.onScanFailure(Failure.NotSupportedFailure())
                }
            } else listener?.onScanFailure(Failure.PermissionFailure())
        }
        bluetoothPermission.requestPermission(activity)
    }

    private fun enable() {
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            resultLauncher.launch(enableBtIntent)
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanLeDevice() {
        val bluetoothLeScanner = bluetoothManager?.adapter?.bluetoothLeScanner
        if (!scanning) { // Stops scanning after a pre-defined scan period.
            handler.postDelayed({
                scanning = false
                bluetoothLeScanner?.stopScan(scanCallback)
            }, SCAN_PERIOD)
            scanning = true
            bluetoothLeScanner?.startScan(scanCallback)
        } else {
            scanning = false
            bluetoothLeScanner?.stopScan(scanCallback)
        }
    }
}
