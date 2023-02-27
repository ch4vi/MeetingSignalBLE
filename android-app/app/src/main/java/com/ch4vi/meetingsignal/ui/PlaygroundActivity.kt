package com.ch4vi.meetingsignal.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.ch4vi.meetingsignal.R
import com.ch4vi.meetingsignal.bluetooth.BleScan
import com.ch4vi.meetingsignal.bluetooth.BleScanListener
import com.ch4vi.meetingsignal.bluetooth.ConnectionEventListener
import com.ch4vi.meetingsignal.bluetooth.ConnectionManager
import com.ch4vi.meetingsignal.bluetooth.Constants.BATTERY_CHARACTERISTIC_UUID
import com.ch4vi.meetingsignal.bluetooth.isIndicatable
import com.ch4vi.meetingsignal.bluetooth.isNotifiable
import com.ch4vi.meetingsignal.bluetooth.isReadable
import com.ch4vi.meetingsignal.bluetooth.isWritable
import com.ch4vi.meetingsignal.bluetooth.isWritableWithoutResponse
import com.ch4vi.meetingsignal.bluetooth.toUuid
import com.ch4vi.meetingsignal.databinding.ActivityPlaygroundBinding
import com.ch4vi.meetingsignal.entities.BluetoothDeviceDomainModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.UUID

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
            // onDeviceFound()
        }

        override fun onStateChanged(isScanning: Boolean) {
            if (isScanning) {
                log("Scanning ON")
            } else {
                log("Scanning OFF $device")
                onDeviceFound()
            }
            bindingView?.apply {
                val start = getString(R.string.app_playground_scan_start)
                val stop = getString(R.string.app_playground_scan_stop)
                connectButton.text = if (isScanning) start else stop
            }
        }
    }

    private val characteristics by lazy {
        device?.let {
            ConnectionManager.servicesOnDevice(it.value)?.flatMap { service ->
                service.characteristics.orEmpty()
            }.orEmpty()
        }.orEmpty()
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
    }

    private val notifyingCharacteristics = mutableListOf<UUID>()

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onConnectionSetupComplete = {
                device?.let(::onConnected)
            }

            onDisconnect = {
                log("Disconnected from ${it.address}")
                onDisconnected(device)
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
                device?.value?.let(ConnectionManager::teardownConnection)
            }
            connectButton.setOnClickListener {
                startScanning()
            }
        }
    }

    private fun startScanning() {
        bindingView?.connectionStatus?.text = getString(R.string.app_main_scanning)
        scanner.run("A4:CF:12:72:A0:32")
    }

    private fun onDeviceFound() {
        bindingView?.connectionStatus?.text = getString(R.string.app_main_device_found)
        scanner.stop()
        device?.let { tryingConnection(it) }
    }

    private fun tryingConnection(device: BluetoothDeviceDomainModel) {
        bindingView?.apply {
            val deviceName = device.name ?: getString(R.string.app_generic_unknown)
            connectionStatus.text = getString(R.string.app_main_trying_connection, deviceName)
        }
        ConnectionManager.connect(device.value, this)
    }

    private fun onDisconnected(device: BluetoothDeviceDomainModel?) {
        bindingView?.apply {
            val deviceName = device?.name ?: getString(R.string.app_generic_unknown)
            connectionStatus.text = getString(R.string.app_main_disconnected, deviceName)
        }
    }

    private fun onConnected(device: BluetoothDeviceDomainModel) {
        bindingView?.apply {
            val deviceName = device.name ?: getString(R.string.app_generic_unknown)
            connectionStatus.text = getString(R.string.app_main_connected, deviceName)
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

    @SuppressLint("SetTextI18n")
    private fun log(message: String) {
        bindingView?.apply {
            val currentLogText = logMessages.text.ifEmpty { "Beginning of log." }
            runOnUiThread {
                logMessages.text = "$currentLogText\n$message"
                logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }

    private fun showError(message: String?) {
        bindingView?.root?.let { root ->
            val errorMessage = message ?: getString(R.string.app_generic_error)
            Snackbar.make(root, errorMessage, Snackbar.LENGTH_SHORT).show()
        }
    }
}
