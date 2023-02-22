package com.ch4vi.meetingsignal.ui

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.ch4vi.meetingsignal.bluetooth.Constants.MEETING_OFF
import com.ch4vi.meetingsignal.bluetooth.Constants.MEETING_ON
import com.ch4vi.meetingsignal.entities.BluetoothDeviceDomainModel
import com.ch4vi.meetingsignal.entities.Failure
import com.ch4vi.meetingsignal.ui.ConnectionProgress.CONNECTED
import com.ch4vi.meetingsignal.ui.ConnectionProgress.CONNECTING
import com.ch4vi.meetingsignal.ui.ConnectionProgress.DEVICE_FOUND
import com.ch4vi.meetingsignal.ui.ConnectionProgress.DISCONNECTED
import com.ch4vi.meetingsignal.ui.ConnectionProgress.SCANNING
import com.ch4vi.meetingsignal.utils.Event
import com.ch4vi.meetingsignal.utils.toEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

private const val MEETING_SIGNAL_ADDRESS = "A4:CF:12:72:A0:32"

sealed class MainEvent {
    object StartScan : MainEvent()
    class DeviceFound(val device: BluetoothDeviceDomainModel) : MainEvent()
    object AttemptConnection : MainEvent()
    object DeviceConnected : MainEvent()
    object DeviceDisconnected : MainEvent()
    class BatteryLevelUpdated(val data: ByteArray?) : MainEvent()
    class OnFailure(val error: Throwable) : MainEvent()
    object MeetingOn : MainEvent()
    object MeetingOff : MainEvent()
    class MeetingStatusUpdated(val data: ByteArray?) : MainEvent()
    object MeetingStatusWritten : MainEvent()
}

sealed class MainViewState(
    open val device: BluetoothDeviceDomainModel?
) {
    class OnStartScanning(
        val progress: Int,
        val deviceAddress: String
    ) : MainViewState(null)

    class OnDeviceFound(
        val progress: Int,
        device: BluetoothDeviceDomainModel,
    ) : MainViewState(device)

    class OnConnecting(
        val progress: Int,
        override val device: BluetoothDeviceDomainModel
    ) : MainViewState(device)

    class OnConnected(
        val progress: Int,
        override val device: BluetoothDeviceDomainModel
    ) : MainViewState(device)

    class OnDisconnected(
        val progress: Int,
        device: BluetoothDeviceDomainModel?
    ) : MainViewState(device)

    class OnBatteryUpdated(
        val level: Int,
        device: BluetoothDeviceDomainModel?
    ) : MainViewState(device)

    class WriteMeetingStatus(
        val data: ByteArray,
        override val device: BluetoothDeviceDomainModel
    ) : MainViewState(device)

    class ReadMeetingStatus(
        override val device: BluetoothDeviceDomainModel
    ) : MainViewState(device)

    class OnMeetingStatusUpdated(
        val status: Boolean,
        device: BluetoothDeviceDomainModel?
    ) : MainViewState(device)

    object GenericError : MainViewState(null)
    object BluetoothError : MainViewState(null)
}

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {

    private val mutableViewState = MutableLiveData<Event<MainViewState>>()
    val viewState: LiveData<Event<MainViewState>>
        get() = mutableViewState

    private val device: BluetoothDeviceDomainModel?
        get() = mutableViewState.value?.forceGet()?.device

    private var meetingStatus = false

    fun dispatch(event: MainEvent) {
        when (event) {
            MainEvent.StartScan -> startScan()
            is MainEvent.DeviceFound -> deviceFound(event.device)
            MainEvent.AttemptConnection -> connect()
            MainEvent.DeviceConnected -> connected()
            MainEvent.DeviceDisconnected -> disconnected()
            is MainEvent.BatteryLevelUpdated -> batteryLevelUpdated(event.data)
            is MainEvent.OnFailure -> onError(event.error)
            MainEvent.MeetingOff -> toggleMeetingStatus(false)
            MainEvent.MeetingOn -> toggleMeetingStatus(true)
            is MainEvent.MeetingStatusUpdated -> meetingStatusUpdated(event.data)
            MainEvent.MeetingStatusWritten -> onMeetingStatusWritten()
        }
    }

    private fun startScan() {
        changeViewState(
            MainViewState.OnStartScanning(SCANNING.getProgress(), MEETING_SIGNAL_ADDRESS)
        )
    }

    private fun deviceFound(device: BluetoothDeviceDomainModel) {
        changeViewState(MainViewState.OnDeviceFound(DEVICE_FOUND.getProgress(), device))
    }

    private fun connect() {
        device?.let { device ->
            changeViewState(MainViewState.OnConnecting(CONNECTING.getProgress(), device))
        } ?: run { onError(Failure.ScanFailure("empty device")) }
    }

    private fun connected() {
        device?.let { device ->
            changeViewState(MainViewState.OnConnected(CONNECTED.getProgress(), device))
        } ?: run { onError(Failure.ConnectionFailure("empty device")) }
    }

    private fun disconnected() {
        device?.let { device ->
            changeViewState(MainViewState.OnDisconnected(DISCONNECTED.getProgress(), device))
        } ?: run { onError(Failure.ConnectionFailure("empty device")) }
    }

    private fun batteryLevelUpdated(data: ByteArray?) {
        val level = data?.decodeToString()?.trim()?.toFloatOrNull()?.toInt() ?: 0
        changeViewState(MainViewState.OnBatteryUpdated(level, device))
    }

    private fun toggleMeetingStatus(newStatus: Boolean) {
        device?.let { device ->
            if (newStatus != meetingStatus) {
                val data = if (newStatus) MEETING_ON.toByteArray() else MEETING_OFF.toByteArray()
                changeViewState(MainViewState.WriteMeetingStatus(data, device))
                meetingStatus = newStatus // TODO not sure if here or wait to answer
            }
        } ?: run { onError(Failure.ConnectionFailure("empty device")) }
    }

    private fun onMeetingStatusWritten() {
        device?.let { device ->
            changeViewState(MainViewState.ReadMeetingStatus(device))
        } ?: run { onError(Failure.ConnectionFailure("empty device")) }
    }

    private fun meetingStatusUpdated(data: ByteArray?) {
        val status = data?.decodeToString()?.trim()?.equals(MEETING_ON) ?: false
        changeViewState(MainViewState.OnMeetingStatusUpdated(status, device))
    }

    private fun changeViewState(viewState: MainViewState) =
        mutableViewState.postValue(viewState.toEvent())

    @VisibleForTesting
    internal fun onError(error: Throwable) {
        when (error) {
            is Failure.ConnectionFailure,
            is Failure.ScanFailure -> changeViewState(MainViewState.BluetoothError)
            else -> changeViewState(MainViewState.GenericError)
        }
    }
}
