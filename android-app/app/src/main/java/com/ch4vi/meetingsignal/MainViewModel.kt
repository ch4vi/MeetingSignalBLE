package com.ch4vi.meetingsignal

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanRecord
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ch4vi.meetingsignal.entities.Failure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class MainEvent {
    class OnChangeSignalState(val toState: SignalState) : MainEvent()
    class OnDeviceResult(
        val device: BluetoothDevice?,
        val rssi: Int,
        val scanRecord: ScanRecord?
    ) : MainEvent()
}

sealed class MainViewState {
    class ChangeState(val signalState: SignalState) : MainViewState() // enum view state
    class DeviceFound(val name: String) : MainViewState() // enum view state
    object Loading : MainViewState()
    object GenericError : MainViewState()
    object ConnectionError : MainViewState()
}

enum class SignalState {
    COLD, WARM, ALL, OFF
}

@HiltViewModel
class MainViewModel @Inject constructor(
    //private val getGithubRepo: GetGithubRepo,
) : ViewModel() {

    private val mutableViewState = MutableLiveData<MainViewState>()
    val viewState: LiveData<MainViewState>
        get() = mutableViewState

    fun dispatch(event: MainEvent) {
        when (event) {
            is MainEvent.OnChangeSignalState -> changeSignalState(event.toState)
            is MainEvent.OnDeviceResult -> deviceResult(event.device, event.rssi, event.scanRecord)
        }
    }



    @SuppressLint("MissingPermission")
    private fun deviceResult(device: BluetoothDevice?, rssi: Int, scanRecord: ScanRecord?) {
        device?.alias?.let {
            changeViewState(MainViewState.DeviceFound(it))
        }
    }

    private fun changeSignalState(toState: SignalState) {
        when (toState) {
            SignalState.COLD -> {
                // do things
                changeViewState(MainViewState.ChangeState(SignalState.COLD))
            }
            SignalState.WARM -> {
                // do things
                changeViewState(MainViewState.ChangeState(SignalState.WARM))
            }
            SignalState.ALL -> {
                // do things
                changeViewState(MainViewState.ChangeState(SignalState.ALL))
            }
            SignalState.OFF -> {
                // do things
                changeViewState(MainViewState.ChangeState(SignalState.OFF))
            }
        }
    }

    private fun getRepo(repoName: String, ownerName: String) {
        changeViewState(MainViewState.Loading)
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
        mutableViewState.postValue(viewState)

    @VisibleForTesting
    internal fun onError(error: Throwable) {
        when (error) {
            is Failure.ConnectionFailure -> changeViewState(MainViewState.ConnectionError)
            else -> changeViewState(MainViewState.GenericError)
        }
    }
}
