package com.ch4vi.meetingsignal.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.ch4vi.meetingsignal.bluetooth.ConnectionEventListener
import com.ch4vi.meetingsignal.bluetooth.ConnectionManager
import com.ch4vi.meetingsignal.bluetooth.Constants.BATTERY_CHARACTERISTIC_UUID
import com.ch4vi.meetingsignal.bluetooth.isIndicatable
import com.ch4vi.meetingsignal.bluetooth.isNotifiable
import com.ch4vi.meetingsignal.bluetooth.isReadable
import com.ch4vi.meetingsignal.bluetooth.isWritable
import com.ch4vi.meetingsignal.bluetooth.isWritableWithoutResponse
import com.ch4vi.meetingsignal.bluetooth.toUuid
import com.ch4vi.meetingsignal.bluetooth.BleScan
import com.ch4vi.meetingsignal.bluetooth.BleScanListener
import com.ch4vi.meetingsignal.databinding.ActivityPlaygroundBinding
import com.ch4vi.meetingsignal.entities.BluetoothDeviceDomainModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.UUID

@SuppressLint("SetTextI18n")
@AndroidEntryPoint
class PlaygroundActivity : AppCompatActivity() {

    private var bindingView: ActivityPlaygroundBinding? = null
    private var device: BluetoothDeviceDomainModel? = null

    private val scanner = BleScan(this)
    private val scannerListener = object : BleScanListener {
        override fun onScanFailure(error: Throwable) {
            showError(error.message)
        }

        override fun onScanResult(device: BluetoothDeviceDomainModel) {
            Timber.d("Playground $device")
            log("device found $device")
            this@PlaygroundActivity.device = device
            scanner.stop()
            //onDeviceFound()
        }

        override fun onStateChanged(isScanning: Boolean) {
            if (isScanning) {
                log("Scanning ON")
            } else {
                log("Scanning OFF $device")
                onDeviceFound()
            }
            bindingView?.apply {
                connectButton.text = if (isScanning) "Stop Scan" else "Start Scan"
            }
        }
    }

    private val characteristics by lazy {
        device?.let {
            ConnectionManager.servicesOnDevice(it.value)?.flatMap { service ->
                service.characteristics ?: listOf()
            } ?: listOf()
        } ?: listOf()
    }
    private val characteristicProperties by lazy {
        characteristics.associateWith { characteristic ->
            mutableListOf<CharacteristicProperty>().apply {
                if (characteristic.isNotifiable()) add(CharacteristicProperty.Notifiable)
                if (characteristic.isIndicatable()) add(CharacteristicProperty.Indicatable)
                if (characteristic.isReadable()) add(CharacteristicProperty.Readable)
                if (characteristic.isWritable()) add(CharacteristicProperty.Writable)
                if (characteristic.isWritableWithoutResponse()) {
                    add(CharacteristicProperty.WritableWithoutResponse)
                }
            }.toList()
        }
    }

    private enum class CharacteristicProperty {
        Readable,
        Writable,
        WritableWithoutResponse,
        Notifiable,
        Indicatable;

        val action
            get() = when (this) {
                Readable -> "Read"
                Writable -> "Write"
                WritableWithoutResponse -> "Write Without Response"
                Notifiable -> "Toggle Notifications"
                Indicatable -> "Toggle Indications"
            }
    }

    private var notifyingCharacteristics = mutableListOf<UUID>()
    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onConnectionSetupComplete = {
                device?.let {
                    onConnected(it)
                }
            }

            onDisconnect = {
                log("Disconnected from ${it.address}")
            }

            onCharacteristicRead = { _, characteristic, value ->
                log("Read from ${characteristic.uuid}: ${value.decodeToString()}")
            }

            onCharacteristicWrite = { _, characteristic, value ->
                log("Wrote to ${characteristic.uuid} $value")
            }

            onMtuChanged = { _, mtu ->
                log("MTU updated to $mtu")
            }

            onCharacteristicChanged = { _, characteristic, value ->
                log("Value changed on ${characteristic.uuid}: ${value.decodeToString()}")
            }

            onNotificationsEnabled = { _, characteristic ->
                log("Enabled notifications on ${characteristic.uuid}")
                notifyingCharacteristics.add(characteristic.uuid)
            }

            onNotificationsDisabled = { _, characteristic ->
                log("Disabled notifications on ${characteristic.uuid}")
                notifyingCharacteristics.remove(characteristic.uuid)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ConnectionManager.registerListener(connectionEventListener)
        super.onCreate(savedInstanceState)
        val view = bindingView?.root ?: with(ActivityPlaygroundBinding.inflate(layoutInflater)) {
            bindingView = this
            root
        }
        scanner.listener = scannerListener

        setContentView(view)
        initUI()
    }

    override fun onDestroy() {
        ConnectionManager.unregisterListener(connectionEventListener)
        device?.let { ConnectionManager.teardownConnection(it.value) }

        super.onDestroy()
    }

    private fun initUI() {
        bindingView?.apply {
            setSupportActionBar(toolbar)
            disconnectButton.setOnClickListener {
                device?.value?.let {
                    ConnectionManager.teardownConnection(it)
                }
            }
            connectButton.setOnClickListener {
                startScanning("A4:CF:12:72:A0:32")
            }
        }
    }

    private fun startScanning(filterAddress: String) {
        bindingView?.apply {
            connectionStatus.text = "Scanning"
        }
        scanner.run(filterAddress)
    }

    private fun onDeviceFound() {
        bindingView?.apply {
            connectionStatus.text = "Device found"
        }
        scanner.stop()
        device?.let { tryingConnection(it) }
    }

    private fun tryingConnection(device: BluetoothDeviceDomainModel) {
        bindingView?.apply {
            connectionStatus.text = "Trying to connect to ${device.name ?: "Unknown"}"
        }
        ConnectionManager.connect(device.value, this)
    }

    private fun onDisconnected(device: BluetoothDeviceDomainModel?) {
        bindingView?.apply {
            connectionStatus.text = "Disconnected from ${device?.name ?: "unknown"}"
        }
    }

    private fun onConnected(device: BluetoothDeviceDomainModel) {
        bindingView?.apply {
            connectionStatus.text = "Connected to ${device.name ?: "unknown"}"
        }
        findCharacteristic()?.let { characteristic ->
            toggleBatterySubscription(device, characteristic)
        }
    }

    private fun toggleBatterySubscription(
        device: BluetoothDeviceDomainModel,
        characteristic: BluetoothGattCharacteristic
    ) {
        characteristicProperties[characteristic]?.let { properties ->
            if (properties.contains(CharacteristicProperty.Notifiable)) {
                if (notifyingCharacteristics.contains(characteristic.uuid)) {
                    log("Disabling notifications on ${characteristic.uuid}")
                    ConnectionManager.disableNotifications(device.value, characteristic)
                } else {
                    log("Enabling notifications on ${characteristic.uuid}")
                    ConnectionManager.enableNotifications(device.value, characteristic)
                }
            }
        }
    }

    private fun findCharacteristic(): BluetoothGattCharacteristic? {
        return characteristics.firstOrNull { it.uuid == BATTERY_CHARACTERISTIC_UUID.toUuid() }
    }


    private fun log(message: String) {
        bindingView?.apply {
            val formattedMessage = String.format("%s", message)
            val currentLogText = logMessages.text.ifEmpty { "Beginning of log." }
            runOnUiThread {
                logMessages.text = "$currentLogText\n$formattedMessage"
                logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }

    private fun showError(message: String?) {
        bindingView?.apply {
            Snackbar.make(
                this.root,
                "failure ${message ?: "empty"}",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }
}
