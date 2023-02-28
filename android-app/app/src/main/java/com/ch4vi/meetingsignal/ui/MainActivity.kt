package com.ch4vi.meetingsignal.ui

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.ch4vi.meetingsignal.R
import com.ch4vi.meetingsignal.bluetooth.BleScan
import com.ch4vi.meetingsignal.bluetooth.BleScanListener
import com.ch4vi.meetingsignal.bluetooth.BleService
import com.ch4vi.meetingsignal.bluetooth.BluetoothPermission
import com.ch4vi.meetingsignal.bluetooth.GattUpdateReceiver
import com.ch4vi.meetingsignal.bluetooth.GattUpdateReceiverState
import com.ch4vi.meetingsignal.databinding.ActivityMainBinding
import com.ch4vi.meetingsignal.entities.BluetoothDeviceDomainModel
import com.ch4vi.meetingsignal.entities.Failure
import com.ch4vi.meetingsignal.utils.EventObserver
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var bindingView: ActivityMainBinding? = null
    private val viewModel by viewModels<MainViewModel>()

    private val scanner = BleScan(this)
    private val scannerListener = object : BleScanListener {
        override fun onScanFailure(error: Throwable) {
            viewModel.dispatch(MainEvent.OnFailure(error))
        }

        override fun onScanResult(device: BluetoothDeviceDomainModel) {
            viewModel.dispatch(MainEvent.DeviceFound(device))
        }

        override fun onStateChanged(isScanning: Boolean) {
            bindingView?.content?.connectButton?.text =
                if (isScanning) "Stop Scan" else "Start Scan"
        }
    }

    private var bluetoothService: BleService? = null
    private val gattUpdateReceiver: GattUpdateReceiver by lazy { GattUpdateReceiver() }
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            (service as? BleService.LocalBinder)?.service?.let { bleService ->
                bluetoothService = bleService
                if (!BluetoothPermission.checkPermissions(this@MainActivity)) {
                    viewModel.dispatch(MainEvent.OnFailure(Failure.PermissionFailure()))
                }
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            bluetoothService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = bindingView?.root ?: with(ActivityMainBinding.inflate(layoutInflater)) {
            bindingView = this
            root
        }
        scanner.listener = scannerListener

        setContentView(view)
        viewModel.viewState.observe(this, EventObserver { onViewStateChanged(it) })
        gattUpdateReceiver.state.observe(this, EventObserver { onReceiverStateChanged(it) })
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
            with(content) {
                connectionProgress.progress = 1
                playground.setOnClickListener {
                    val intent = Intent(this@MainActivity, PlaygroundActivity::class.java)
                    startActivity(intent)
                }
                connectButton.setOnClickListener {
                    viewModel.dispatch(MainEvent.StartScan)
                }
                meetingOn.setOnClickListener {
                    meetingToggle.isEnabled = false
                    viewModel.dispatch(MainEvent.MeetingOn)
                }
                meetingOff.setOnClickListener {
                    meetingToggle.isEnabled = false
                    viewModel.dispatch(MainEvent.MeetingOff)
                }
            }
        }
    }

    private fun initService() {
        if (bluetoothService == null) {
            val gattServiceIntent = Intent(this, BleService::class.java)
            bindService(gattServiceIntent, serviceConnection, BIND_AUTO_CREATE)
        }
    }

    private fun initReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction(BleService.Action.GATT_CONNECTED.action)
            addAction(BleService.Action.GATT_DISCONNECTED.action)
            addAction(BleService.Action.BATTERY_LEVEL_UPDATED.action)
            addAction(BleService.Action.BATTERY_NOTIFY_ENABLED.action)
            addAction(BleService.Action.BATTERY_NOTIFY_DISABLED.action)
            addAction(BleService.Action.MEETING_STATUS_READ.action)
            addAction(BleService.Action.MEETING_STATUS_WRITE.action)
        }
        registerReceiver(gattUpdateReceiver, intentFilter)
    }

    private fun onViewStateChanged(state: MainViewState) {
        when (state) {
            is MainViewState.OnStartScanning -> startScanning(state.progress, state.deviceAddress)
            is MainViewState.OnDeviceFound -> onDeviceFound(state.progress)
            is MainViewState.OnConnecting -> tryingConnection(state.progress, state.device)
            is MainViewState.OnConnected -> onConnected(state.progress, state.device)
            is MainViewState.OnDisconnected -> onDisconnected(state.progress, state.device)
            MainViewState.BluetoothError -> showError()
            MainViewState.GenericError -> showError()
            is MainViewState.OnBatteryUpdated -> onBatteryUpdated(state.level)
            is MainViewState.WriteMeetingStatus -> toggleMeeting(state.data, state.device)
            is MainViewState.ReadMeetingStatus -> readMeetingStatus(state.device)
            is MainViewState.OnMeetingStatusUpdated -> updateMeetingStatus(state.status)
        }
    }

    private fun onReceiverStateChanged(state: GattUpdateReceiverState) {
        when (state) {
            GattUpdateReceiverState.Disconnect -> viewModel.dispatch(MainEvent.DeviceDisconnected)
            GattUpdateReceiverState.Connected -> viewModel.dispatch(MainEvent.DeviceConnected)
            is GattUpdateReceiverState.BatteryLevelUpdate ->
                viewModel.dispatch(MainEvent.BatteryLevelUpdated(state.level))
            GattUpdateReceiverState.MeetingStatusWritten ->
                viewModel.dispatch(MainEvent.MeetingStatusWritten)
            is GattUpdateReceiverState.MeetingStatusUpdate ->
                viewModel.dispatch(MainEvent.MeetingStatusUpdated(state.status))
            is GattUpdateReceiverState.BatteryNotificationStatus -> {
                Toast.makeText(
                    this,
                    "notifications ${if (state.enabled) "enabled" else "disabled"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun startScanning(progress: Int, filterAddress: String) {
        bindingView?.content?.apply {
            connectionProgress.progress = progress
            connectionStatus.text = getString(R.string.app_main_scanning)
        }
        scanner.run(filterAddress)
    }

    private fun onDeviceFound(progress: Int) {
        bindingView?.content?.apply {
            connectionProgress.progress = progress
            connectionStatus.text = getString(R.string.app_main_device_found)
        }
        scanner.stop()
        viewModel.dispatch(MainEvent.AttemptConnection)
    }

    private fun tryingConnection(progress: Int, device: BluetoothDeviceDomainModel) {
        bindingView?.content?.apply {
            connectionProgress.progress = progress
            val deviceName = device.name ?: getString(R.string.app_generic_unknown)
            connectionStatus.text = getString(R.string.app_main_trying_connection, deviceName)
        }
        bluetoothService?.connect(device.value)
    }

    private fun onDisconnected(progress: Int, device: BluetoothDeviceDomainModel?) {
        bindingView?.content?.apply {
            connectionProgress.progress = progress
            val deviceName = device?.name ?: getString(R.string.app_generic_unknown)
            connectionStatus.text = getString(R.string.app_main_disconnected, deviceName)
        }
    }

    private fun onConnected(progress: Int, device: BluetoothDeviceDomainModel) {
        bindingView?.content?.apply {
            connectionProgress.progress = progress
            val deviceName = device.name ?: getString(R.string.app_generic_unknown)
            connectionStatus.text = getString(R.string.app_main_connected, deviceName)
            meetingToggle.isEnabled = true
        }
        bluetoothService?.subscribeToBatteryLevel(device.value)
        readMeetingStatus(device)
    }

    private fun readMeetingStatus(device: BluetoothDeviceDomainModel) {
        bluetoothService?.readMeetingStatus(device.value)
    }

    private fun onBatteryUpdated(level: Int) {
        bindingView?.content?.apply {
            batteryProgress.progress = level
            batteryLevel.text = getString(R.string.app_main_battery_level, level)
        }
    }

    private fun toggleMeeting(inMeeting: ByteArray, device: BluetoothDeviceDomainModel) {
        bluetoothService?.writeMeetingStatus(device.value, inMeeting)
    }

    private fun updateMeetingStatus(status: Boolean) {
        bindingView?.content?.apply {
            meetingToggle.isEnabled = true
            meetingOn.isChecked = status
            meetingOff.isChecked = !status
        }
    }

    private fun showError() {
        bindingView?.root?.let { root ->
            Snackbar.make(root, R.string.app_generic_error, Snackbar.LENGTH_SHORT).show()
        }
    }
}
