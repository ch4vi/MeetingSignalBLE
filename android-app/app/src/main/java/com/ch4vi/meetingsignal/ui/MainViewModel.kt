package com.ch4vi.meetingsignal.ui

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ch4vi.meetingsignal.bluetooth.GattUpdateReceiverState
import com.ch4vi.meetingsignal.entities.BluetoothDeviceDomainModel
import com.ch4vi.meetingsignal.entities.Failure
import com.ch4vi.meetingsignal.utils.Event
import com.ch4vi.meetingsignal.utils.toEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val TARGET_ADDRESS = "A4:CF:12:72:A0:32"

sealed class MainEvent {
    object StartScan : MainEvent()
    class OnChangeSignalState(val toState: SignalState) : MainEvent()
    class OnDeviceResult(val device: BluetoothDeviceDomainModel) : MainEvent()
    class OnConnectionStateChanged(val currentState: GattUpdateReceiverState) : MainEvent()
    class SendMessage(val message: String) : MainEvent()
    class OnError(val error: Throwable) : MainEvent()
}

sealed class MainViewState {
    class OnStartScanning(val progress: Int) : MainViewState()

    class DeviceFound(val progress: Int) : MainViewState()

    class AttemptConnection(
        val progress: Int,
        val device: BluetoothDeviceDomainModel
    ) : MainViewState()

    object ServiceDiscovered : MainViewState()
    object Disconnected : MainViewState()
    class ConnectionSuccessful(
        val progress: Int,
        val name: String?
    ) : MainViewState()

    object GenericError : MainViewState()
    object ConnectionError : MainViewState()
}

enum class SignalState {
    COLD, WARM, ALL, OFF
}

sealed class ConnectionProgress(private val step: Int) {
    object SCANNING : ConnectionProgress(1)
    object DEVICE_FOUND : ConnectionProgress(2)
    object CONNECTING : ConnectionProgress(3)
    object CONNECTED : ConnectionProgress(4)

    object FAILED : ConnectionProgress(0)
    object DISCONNECTED : ConnectionProgress(0)
    ;

    private val totalSteps = 4
    fun getProgress() = this.step.times(100).div(totalSteps)
}

@HiltViewModel
class MainViewModel @Inject constructor(
    //private val getGithubRepo: GetGithubRepo,
) : ViewModel() {

    private val mutableViewState = MutableLiveData<Event<MainViewState>>()
    val viewState: LiveData<Event<MainViewState>>
        get() = mutableViewState

    private var device: BluetoothDeviceDomainModel? = null
    private var gattState: ConnectionProgress = ConnectionProgress.DISCONNECTED

    fun dispatch(event: MainEvent) {
        when (event) {
            is MainEvent.OnChangeSignalState -> changeSignalState(event.toState)
            is MainEvent.OnDeviceResult -> deviceResult(event.device)
            is MainEvent.OnConnectionStateChanged -> onConnectionStateChanged(event.currentState)
            is MainEvent.OnError -> onError(event.error)
            is MainEvent.SendMessage -> Unit //TODO()
            MainEvent.StartScan ->
                changeViewState(MainViewState.OnStartScanning(ConnectionProgress.SCANNING.getProgress()))
        }
    }

    private fun deviceResult(device: BluetoothDeviceDomainModel) {
        changeViewState(MainViewState.DeviceFound(ConnectionProgress.DEVICE_FOUND.getProgress()))
        if (device.address == TARGET_ADDRESS &&
            gattState != ConnectionProgress.CONNECTING &&
            gattState != ConnectionProgress.CONNECTED
        ) {
            gattState = ConnectionProgress.CONNECTING
            this.device = device
            mutableViewState.value =
                (MainViewState.AttemptConnection(
                    ConnectionProgress.CONNECTING.getProgress(),
                    device
                ).toEvent()
            )
        }
    }

    private fun onConnectionStateChanged(state: GattUpdateReceiverState) {
        when (state) {
            GattUpdateReceiverState.Connected -> {
                gattState = ConnectionProgress.CONNECTED
                changeViewState(
                    MainViewState.ConnectionSuccessful(
                        ConnectionProgress.CONNECTED.getProgress(),
                        device?.name
                    )
                )
            }
            GattUpdateReceiverState.Disconnect -> {
                gattState = ConnectionProgress.DISCONNECTED
                changeViewState(MainViewState.Disconnected)
            }
            GattUpdateReceiverState.ServicesDiscovered -> {
                Timber.d("ServicesDiscovered")
                changeViewState(MainViewState.ServiceDiscovered)
            }
            is GattUpdateReceiverState.DataReceived -> {
                Timber.d("DataReceived ${state.message}")
            }
        }
    }

    private fun changeSignalState(toState: SignalState) {
//        when (toState) {
//            SignalState.COLD -> {
//                // do things
//                changeViewState(MainViewState.ChangeState(SignalState.COLD))
//            }
//            SignalState.WARM -> {
//                // do things
//                changeViewState(MainViewState.ChangeState(SignalState.WARM))
//            }
//            SignalState.ALL -> {
//                // do things
//                changeViewState(MainViewState.ChangeState(SignalState.ALL))
//            }
//            SignalState.OFF -> {
//                // do things
//                changeViewState(MainViewState.ChangeState(SignalState.OFF))
//            }
//        }
    }

    private fun getRepo(repoName: String, ownerName: String) {
//        changeViewState(MainViewState.Loading)
        viewModelScope.launch {
//            val request = GetGithubRepoRequest(ownerName, repoName)
//            getGithubRepo(request)
//                .collect { resource ->
//                    when (resource) {
//                        is Resource.Error -> onError(resource.throwable)
//                        is Resource.Loading ->
//                            if (resource.data != null) {
//                                changeViewState(RepoDetailViewState.ShowRepo(resource.data))
//                            }
//                        is Resource.Success ->
//                            changeViewState(RepoDetailViewState.ShowRepo(resource.data))
//                    }
//                }
        }
    }

    private fun changeViewState(viewState: MainViewState) =
        mutableViewState.postValue(viewState.toEvent())

    @VisibleForTesting
    internal fun onError(error: Throwable) {
        when (error) {
            is Failure.ConnectionFailure -> changeViewState(MainViewState.ConnectionError)
            else -> changeViewState(MainViewState.GenericError)
        }
    }
}
