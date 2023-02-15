package com.ch4vi.meetingsignal

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanRecord
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ch4vi.meetingsignal.entities.Failure
import com.ch4vi.meetingsignal.stuff.BluetoothLEScan
import com.ch4vi.meetingsignal.stuff.BluetoothScanListener
import com.ch4vi.meetingsignal.ui.theme.MeetingSignalAndroidTheme
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<MainViewModel>()
    private val bluetoothLEScan = BluetoothLEScan(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bluetoothLEScan.listener = object : BluetoothScanListener {
            override fun onScanFailure(failure: Failure) {
                Timber.d("here")
            }

            override fun onScanResult(
                device: BluetoothDevice?,
                rssi: Int,
                scanRecord: ScanRecord?
            ) {
                viewModel.dispatch(MainEvent.OnDeviceResult(device, rssi, scanRecord))
            }

        }

        setContent {
            MeetingSignalAndroidTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column() {
                        ConnectionButton(viewModel, bluetoothLEScan)
                        //ButtonOff(viewModel)
                        DeviceFoundSnackbar(viewModel)
                    }
                }
            }
        }
    }


}

@Composable
fun ConnectionButton(
    viewModel: MainViewModel,
    bluetoothLEScan: BluetoothLEScan,
) {
    Row() {
        Button(onClick = {
            bluetoothLEScan.run()
        }) {
            Text("SCAN")
        }
        Text(text = "BOn dia")
    }
}

@Composable
fun DeviceFoundSnackbar(viewModel: MainViewModel) {
    val uiState = viewModel.viewState.observeAsState()
    when (val state = uiState.value) {
        is MainViewState.DeviceFound -> {
            Text(text = state.name)
        }
        else -> Unit
    }
}

@Composable
fun SignalButtons(viewModel: MainViewModel) {
    val uiState = viewModel.viewState.observeAsState()
    val buttonState = when (val state = uiState.value) {
        is MainViewState.ChangeState -> state.signalState
        else -> SignalState.OFF
    }
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(enabled = buttonState != SignalState.OFF,
            onClick = { viewModel.dispatch(MainEvent.OnChangeSignalState(SignalState.OFF)) }) {
            Text(text = "OFF")
        }
        Button(enabled = buttonState != SignalState.COLD,
            onClick = { viewModel.dispatch(MainEvent.OnChangeSignalState(SignalState.COLD)) }) {
            Text(text = "COLD")
        }
        Button(enabled = buttonState != SignalState.WARM,
            onClick = { viewModel.dispatch(MainEvent.OnChangeSignalState(SignalState.WARM)) }) {
            Text(text = "WARM")
        }
        Button(enabled = buttonState != SignalState.ALL,
            onClick = { viewModel.dispatch(MainEvent.OnChangeSignalState(SignalState.ALL)) }) {
            Text(text = "ALL")
        }
    }
}

@Composable
fun ConnectionButtons(viewModel: MainViewModel) {
    val uiState = viewModel.viewState.observeAsState()
    val buttonState = when (val state = uiState.value) {
        is MainViewState.ChangeState -> state.signalState
        else -> SignalState.OFF
    }
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(enabled = buttonState != SignalState.OFF,
            onClick = { viewModel.dispatch(MainEvent.OnChangeSignalState(SignalState.OFF)) }) {
            Text(text = "OFF")
        }
        Button(enabled = buttonState != SignalState.COLD,
            onClick = { viewModel.dispatch(MainEvent.OnChangeSignalState(SignalState.COLD)) }) {
            Text(text = "COLD")
        }
        Button(enabled = buttonState != SignalState.WARM,
            onClick = { viewModel.dispatch(MainEvent.OnChangeSignalState(SignalState.WARM)) }) {
            Text(text = "WARM")
        }
        Button(enabled = buttonState != SignalState.ALL,
            onClick = { viewModel.dispatch(MainEvent.OnChangeSignalState(SignalState.ALL)) }) {
            Text(text = "ALL")
        }
    }

}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MeetingSignalAndroidTheme {
        val vm = MainViewModel()
        SignalButtons(viewModel = vm)
    }
}
