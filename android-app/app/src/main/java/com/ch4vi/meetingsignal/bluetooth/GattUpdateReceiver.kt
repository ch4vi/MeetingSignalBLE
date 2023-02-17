package com.ch4vi.meetingsignal.bluetooth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ch4vi.meetingsignal.bluetooth.BluetoothLEService.Action.GATT_CONNECTED
import com.ch4vi.meetingsignal.bluetooth.BluetoothLEService.Action.GATT_DATA_AVAILABLE
import com.ch4vi.meetingsignal.bluetooth.BluetoothLEService.Action.GATT_DISCONNECTED
import com.ch4vi.meetingsignal.bluetooth.BluetoothLEService.Action.GATT_SERVICES_DISCOVERED
import com.ch4vi.meetingsignal.utils.Event
import com.ch4vi.meetingsignal.utils.toEvent
import timber.log.Timber

sealed class GattUpdateReceiverState {
    object Connected : GattUpdateReceiverState()
    object Disconnect : GattUpdateReceiverState()
    object ServicesDiscovered : GattUpdateReceiverState()
    class DataReceived(val message: String?) : GattUpdateReceiverState()
}

class GattUpdateReceiver : BroadcastReceiver() {

    private val mutableState = MutableLiveData<Event<GattUpdateReceiverState>>()
    val state: LiveData<Event<GattUpdateReceiverState>>
        get() = mutableState

    private fun changeState(state: GattUpdateReceiverState) =
        mutableState.postValue(state.toEvent())


    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            GATT_CONNECTED.action -> {
                changeState(GattUpdateReceiverState.Connected)
            }
            GATT_DISCONNECTED.action -> {
                changeState(GattUpdateReceiverState.Disconnect)
            }
            GATT_SERVICES_DISCOVERED.action -> {
                changeState(GattUpdateReceiverState.ServicesDiscovered)
            }
            GATT_DATA_AVAILABLE.action -> {
                val data = intent.getByteArrayExtra(BluetoothLEService.CHARACTERISTIC_VALUE)
                if (data != null && data.isNotEmpty()) {
                    Timber.d("Got string : " + String(data))
                    val message = StringBuilder(data.size).apply {
                        data.forEach { byteChar ->
                            append(Char(byteChar.toUShort()))
                        }
                    }.toString()
                    changeState(GattUpdateReceiverState.DataReceived(message))
                } else changeState(GattUpdateReceiverState.DataReceived(null))
            }

        }
    }
}
