package com.ch4vi.meetingsignal.stuff

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ch4vi.meetingsignal.stuff.BluetoothLEService.Action.GATT_CONNECTED
import com.ch4vi.meetingsignal.stuff.BluetoothLEService.Action.GATT_DATA_AVAILABLE
import com.ch4vi.meetingsignal.stuff.BluetoothLEService.Action.GATT_DISCONNECTED
import com.ch4vi.meetingsignal.stuff.BluetoothLEService.Action.GATT_SERVICES_DISCOVERED
import timber.log.Timber

sealed class GattUpdateReceiverState {
    object OnConnected : GattUpdateReceiverState()
    object OnDisconnect : GattUpdateReceiverState()
    object OnServicesDiscovered : GattUpdateReceiverState()
    class OnDataReceived(val message: String?) : GattUpdateReceiverState()
}

class GattUpdateReceiver : BroadcastReceiver() {

    private val mutableState = MutableLiveData<GattUpdateReceiverState>()
    val state: LiveData<GattUpdateReceiverState>
        get() = mutableState

    private fun changeState(state: GattUpdateReceiverState) =
        mutableState.postValue(state)


    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            GATT_CONNECTED.name -> changeState(GattUpdateReceiverState.OnConnected)
            GATT_DISCONNECTED.name -> changeState(GattUpdateReceiverState.OnDisconnect)
            GATT_SERVICES_DISCOVERED.name -> {
                changeState(GattUpdateReceiverState.OnServicesDiscovered)
                //mBluetoothLeService.getSupportedGattServices()
            }
            GATT_DATA_AVAILABLE.name -> {
                val data = intent.getByteArrayExtra(BluetoothLEService.CHARACTERISTIC_VALUE)
                if (data != null && data.isNotEmpty()) {
                    Timber.d("Got string : " + String(data))
                    val message = StringBuilder(data.size).apply {
                        data.forEach { byteChar ->
                            append(Char(byteChar.toUShort()))
                        }
                    }.toString()
                    changeState(GattUpdateReceiverState.OnDataReceived(message))
                } else changeState(GattUpdateReceiverState.OnDataReceived(null))
            }

        }
    }
}
