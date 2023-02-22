package com.ch4vi.meetingsignal.ui

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.ch4vi.meetingsignal.bluetooth.BluetoothLEScan
import com.ch4vi.meetingsignal.bluetooth.BluetoothLEScanListener
import com.ch4vi.meetingsignal.bluetooth.BluetoothLEService
import com.ch4vi.meetingsignal.bluetooth.GattUpdateReceiver
import com.ch4vi.meetingsignal.bluetooth.GattUpdateReceiverState
import com.ch4vi.meetingsignal.databinding.ActivityMainBinding
import com.ch4vi.meetingsignal.entities.BluetoothDeviceDomainModel
import com.ch4vi.meetingsignal.utils.EventObserver
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var bindingView: ActivityMainBinding? = null
    private val viewModel by viewModels<MainViewModel>()

    private val scanner = BluetoothLEScan(this)
    private val scannerListener = object : BluetoothLEScanListener {
        override fun onScanFailure(error: Throwable) {
            viewModel.dispatch(MainEvent.OnError(error))
        }

        override fun onScanResult(device: BluetoothDeviceDomainModel) {
            viewModel.dispatch(MainEvent.OnDeviceResult(device))
        }
    }

    private var bluetoothService: BluetoothLEService? = null
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            (service as? BluetoothLEService.LocalBinder)?.service?.let { BLEService ->
                bluetoothService = BLEService
                if (!BLEService.initialize()) {
                    Timber.e("Unable to initialize Bluetooth")
                }
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            bluetoothService = null
        }
    }

    private val gattUpdateReceiver: GattUpdateReceiver by lazy { GattUpdateReceiver() }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = bindingView?.root ?: with(ActivityMainBinding.inflate(layoutInflater)) {
            bindingView = this
            root
        }
        scanner.listener = scannerListener

        setContentView(view)
        initObservers()
        initUI()
        initService()
    }

    override fun onResume() {
        super.onResume()
        initReceiver()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(gattUpdateReceiver)
    }

    private fun initUI() {
        bindingView?.apply {
            setSupportActionBar(toolbar)
            content.connectionProgress.progress = 1
            content.connectButton.setOnClickListener {
                viewModel.dispatch(MainEvent.StartScan)
            }
            content.send.setOnClickListener {
                viewModel.dispatch(MainEvent.SendMessage("message"))
                sendMessage("hola")
                playground.setOnClickListener {
                    val intent = Intent(this@MainActivity, PlaygroundActivity::class.java)
                    startActivity(intent)
                }
            }
        }
    }

    private fun initObservers() {
        viewModel.viewState.observe(this, EventObserver { onViewStateChanged(it) })
        gattUpdateReceiver.state.observe(this, EventObserver { onReceiverStateChanged(it) })
    }

    private fun onViewStateChanged(state: MainViewState) {
        when (state) {
            MainViewState.GenericError -> showError("TODO")
            MainViewState.ConnectionError -> showError("TODO")
            is MainViewState.OnStartScanning -> startScanning(state.progress)
            is MainViewState.DeviceFound -> onDeviceFound(state.progress)
            is MainViewState.AttemptConnection -> tryingConnection(state.progress, state.device)
            is MainViewState.ConnectionSuccessful -> connected(state.progress, state.name)
            MainViewState.Disconnected -> onDisconnected()
            MainViewState.ServiceDiscovered -> serviceDiscovered()
        }
    }

    private fun onReceiverStateChanged(state: GattUpdateReceiverState) {
        viewModel.dispatch(MainEvent.OnConnectionStateChanged(state))
    }

    private fun startScanning(progress: Int) {
        bindingView?.content?.apply {
            connectionProgress.progress = progress
            connectionStatus.text = "Scanning"
        }
        scanner.run()
    }

    private fun onDeviceFound(progress: Int) {
        bindingView?.content?.apply {
            connectionProgress.progress = progress
            connectionStatus.text = "Device found"
        }
        scanner.stop()
    }

    private fun tryingConnection(progress: Int, device: BluetoothDeviceDomainModel) {
        bindingView?.content?.apply {
            connectionProgress.progress = progress
            connectionStatus.text = "Trying to connect to ${device.name ?: "Unknown"}"
        }
        bluetoothService?.connect(device.address)
    }

    private fun serviceDiscovered() {
        bluetoothService?.getSupportedGattServices()
    }

    private fun onDisconnected() {
        bindingView?.content?.apply {
            connectionProgress.progress = 1
            connectionStatus.text = "Disconnected"
        }
    }

    private fun connected(progress: Int, name: String?) {
        bindingView?.content?.apply {
            connectionProgress.progress = progress
            connectionStatus.text = "Connected to ${name ?: "unknown2"}"
            send.isEnabled = true
        }
    }

    private fun initService() {
        if (bluetoothService == null) {
            val gattServiceIntent = Intent(this, BluetoothLEService::class.java)
            bindService(gattServiceIntent, serviceConnection, BIND_AUTO_CREATE)
        }
    }

    private fun initReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction(BluetoothLEService.Action.GATT_CONNECTED.action)
            addAction(BluetoothLEService.Action.GATT_DISCONNECTED.action)
            addAction(BluetoothLEService.Action.GATT_SERVICES_DISCOVERED.action)
            addAction(BluetoothLEService.Action.GATT_DATA_AVAILABLE.action)
        }
        registerReceiver(gattUpdateReceiver, intentFilter)
    }

    fun sendMessage(message: String) {
        val data = message.toByteArray()
        bluetoothService?.writeCharacteristic(data)
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
