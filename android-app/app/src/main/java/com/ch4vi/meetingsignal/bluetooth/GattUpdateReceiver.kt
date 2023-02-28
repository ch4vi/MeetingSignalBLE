package com.ch4vi.meetingsignal.bluetooth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ch4vi.meetingsignal.bluetooth.BleService.Action.BATTERY_LEVEL_UPDATED
import com.ch4vi.meetingsignal.bluetooth.BleService.Action.BATTERY_NOTIFY_DISABLED
import com.ch4vi.meetingsignal.bluetooth.BleService.Action.BATTERY_NOTIFY_ENABLED
import com.ch4vi.meetingsignal.bluetooth.BleService.Action.GATT_CONNECTED
import com.ch4vi.meetingsignal.bluetooth.BleService.Action.GATT_DISCONNECTED
import com.ch4vi.meetingsignal.bluetooth.BleService.Action.MEETING_STATUS_READ
import com.ch4vi.meetingsignal.bluetooth.BleService.Action.MEETING_STATUS_WRITE
import com.ch4vi.meetingsignal.utils.Event
import com.ch4vi.meetingsignal.utils.toEvent

sealed class GattUpdateReceiverState {
    object Connected : GattUpdateReceiverState()
    object Disconnect : GattUpdateReceiverState()
    class BatteryLevelUpdate(val level: ByteArray?) : GattUpdateReceiverState()
    class BatteryNotificationStatus(val enabled: Boolean) : GattUpdateReceiverState()
    object MeetingStatusWritten : GattUpdateReceiverState()
    class MeetingStatusUpdate(val status: ByteArray?) : GattUpdateReceiverState()
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
            BATTERY_LEVEL_UPDATED.action -> {
                intent.getByteArrayExtra(BATTERY_LEVEL_UPDATED.action)?.let {
                    changeState(GattUpdateReceiverState.BatteryLevelUpdate(it))
                } ?: run {
                    changeState(GattUpdateReceiverState.BatteryLevelUpdate(null))
                }
            }
            BATTERY_NOTIFY_ENABLED.action -> {
                changeState(GattUpdateReceiverState.BatteryNotificationStatus(enabled = true))
            }
            BATTERY_NOTIFY_DISABLED.action -> {
                changeState(GattUpdateReceiverState.BatteryNotificationStatus(enabled = false))
            }
            MEETING_STATUS_READ.action -> {
                intent.getByteArrayExtra(MEETING_STATUS_READ.action)?.let {
                    changeState(GattUpdateReceiverState.MeetingStatusUpdate(it))
                } ?: run {
                    changeState(GattUpdateReceiverState.MeetingStatusUpdate(null))
                }
            }
            MEETING_STATUS_WRITE.action -> {
                changeState(GattUpdateReceiverState.MeetingStatusWritten)
            }
        }
    }
}
