package com.ch4vi.meetingsignal.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import com.ch4vi.meetingsignal.bluetooth.Constants.CLIENT_CHARACTERISTIC_DESCRIPTOR_UUID
import com.ch4vi.meetingsignal.bluetooth.Constants.GATT_MAX_MTU_SIZE
import com.ch4vi.meetingsignal.bluetooth.Constants.GATT_MIN_MTU_SIZE
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

@SuppressLint("MissingPermission")
object ConnectionManager {

    private var listeners: MutableSet<WeakReference<ConnectionEventListener>> = mutableSetOf()

    private val deviceGattMap = ConcurrentHashMap<BluetoothDevice, BluetoothGatt>()
    private val operationQueue = ConcurrentLinkedQueue<BleOperation>()
    private var pendingOperation: BleOperation? = null

    fun servicesOnDevice(device: BluetoothDevice): List<BluetoothGattService>? =
        deviceGattMap[device]?.services

    fun listenToBondStateChanges(context: Context) {
        context.applicationContext.registerReceiver(
            broadcastReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        )
    }

    fun registerListener(listener: ConnectionEventListener) {
        if (listeners.map { it.get() }.contains(listener)) {
            return
        }
        listeners.add(WeakReference(listener))
        listeners = listeners.filter { it.get() != null }.toMutableSet()
        Timber.d("Added listener $listener, ${listeners.size} listeners total")
    }

    fun unregisterListener(listener: ConnectionEventListener) {
        // Removing elements while in a loop results in a java.util.ConcurrentModificationException
        listeners.firstOrNull { it.get() == listener }?.let {
            listeners.remove(it)
            Timber.d("Removed listener ${it.get()}, ${listeners.size} listeners total")
        }
    }

    fun connect(device: BluetoothDevice, context: Context) {
        if (device.isConnected()) {
            Timber.e("Already connected to ${device.address}!")
        } else {
            enqueueOperation(BleOperation.Connect(device, context.applicationContext))
        }
    }

    fun teardownConnection(device: BluetoothDevice) {
        if (device.isConnected()) enqueueOperation(BleOperation.Disconnect(device))
        else Timber.e("Not connected to ${device.address}, cannot teardown connection!")
    }

    fun readCharacteristic(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic) {
        if (device.isConnected() && characteristic.isReadable()) {
            enqueueOperation(BleOperation.CharacteristicRead(device, characteristic.uuid))
        } else if (!characteristic.isReadable()) {
            Timber.e("Attempting to read ${characteristic.uuid} that isn't readable!")
        } else if (!device.isConnected()) {
            Timber.e("Not connected to ${device.address}, cannot perform characteristic read")
        }
    }

    fun writeCharacteristic(
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray
    ) {
        val writeType = when {
            characteristic.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.isWritableWithoutResponse() -> {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            else -> {
                Timber.e("Characteristic ${characteristic.uuid} cannot be written to")
                return
            }
        }
        if (device.isConnected()) {
            enqueueOperation(
                BleOperation.CharacteristicWrite(
                    device,
                    characteristic.uuid,
                    writeType,
                    payload
                )
            )
        } else {
            Timber.e("Not connected to ${device.address}, cannot perform characteristic write")
        }
    }

    fun readDescriptor(device: BluetoothDevice, descriptor: BluetoothGattDescriptor) {
        if (device.isConnected() && descriptor.isReadable()) {
            enqueueOperation(BleOperation.DescriptorRead(device, descriptor.uuid))
        } else if (!descriptor.isReadable()) {
            Timber.e("Attempting to read ${descriptor.uuid} that isn't readable!")
        } else if (!device.isConnected()) {
            Timber.e("Not connected to ${device.address}, cannot perform descriptor read")
        }
    }

    fun writeDescriptor(
        device: BluetoothDevice,
        descriptor: BluetoothGattDescriptor,
        payload: ByteArray
    ) {
        if (device.isConnected() && (descriptor.isWritable() || descriptor.isConfigDescriptor())) {
            enqueueOperation(BleOperation.DescriptorWrite(device, descriptor.uuid, payload))
        } else if (!device.isConnected()) {
            Timber.e("Not connected to ${device.address}, cannot perform descriptor write")
        } else if (!descriptor.isWritable() && !descriptor.isConfigDescriptor()) {
            Timber.e("Descriptor ${descriptor.uuid} cannot be written to")
        }
    }

    fun enableNotifications(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic) {
        if (device.isConnected() &&
            (characteristic.isIndicatable() || characteristic.isNotifiable())
        ) {
            enqueueOperation(BleOperation.EnableNotifications(device, characteristic.uuid))
        } else if (!device.isConnected()) {
            Timber.e("Not connected to ${device.address}, cannot enable notifications")
        } else if (!characteristic.isIndicatable() && !characteristic.isNotifiable()) {
            Timber.e("Characteristic ${characteristic.uuid} doesn't support notifications/indications")
        }
    }

    fun disableNotifications(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic) {
        if (device.isConnected() &&
            (characteristic.isIndicatable() || characteristic.isNotifiable())
        ) {
            enqueueOperation(BleOperation.DisableNotifications(device, characteristic.uuid))
        } else if (!device.isConnected()) {
            Timber.e("Not connected to ${device.address}, cannot disable notifications")
        } else if (!characteristic.isIndicatable() && !characteristic.isNotifiable()) {
            Timber.e("Characteristic ${characteristic.uuid} doesn't support notifications/indications")
        }
    }

    fun requestMtu(device: BluetoothDevice, mtu: Int) {
        if (device.isConnected()) {
            enqueueOperation(
                BleOperation.MtuRequest(
                    device,
                    mtu.coerceIn(GATT_MIN_MTU_SIZE, GATT_MAX_MTU_SIZE)
                )
            )
        } else {
            Timber.e("Not connected to ${device.address}, cannot request MTU update!")
        }
    }

    // - Beginning of PRIVATE functions

    @Synchronized
    private fun enqueueOperation(operation: BleOperation) {
        operationQueue.add(operation)
        if (pendingOperation == null) getNextOperation()
    }

    @Synchronized
    private fun signalEndOfOperation(): Boolean {
        Timber.d("End of $pendingOperation")
        pendingOperation = null
        if (operationQueue.isNotEmpty()) getNextOperation()
        return true
    }

    /**
     * Perform a given [BleOperation]. All permission checks are performed before an operation
     * can be enqueued by [enqueueOperation].
     */
    @Synchronized
    private fun getNextOperation() {
        if (pendingOperation != null) {
            Timber.e("doNextOperation() called when an operation is pending! Aborting.")
            return
        }

        val operation = operationQueue.poll() ?: run {
            Timber.v("Operation queue empty, returning")
            return
        }
        pendingOperation = operation

        // Handle Connect separately from other operations that require device to be connected
        if (operation is BleOperation.Connect) {
            with(operation) {
                Timber.w("Connecting to ${device.address}")
                device.connectGatt(context, false, callback)
            }
        } else {
            // Check BluetoothGatt availability for other operations
            deviceGattMap[operation.device]?.let { gatt ->
                doNext(operation, gatt)
            } ?: this@ConnectionManager.run {
                Timber.e("Not connected to ${operation.device.address}! Aborting $operation operation.")
                signalEndOfOperation()
            }
        }
    }

    /** Skipped End() callbacks are managed in BluetoothGattCallback, these operations are not
     * finished until BluetoothGattCallback is called
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    @Synchronized
    private fun doNext(operation: BleOperation, gatt: BluetoothGatt) {
        BleOperation.autoCloseOperation(
            operation,
            onConnect = {
                /* At this point we are requiring a BluetoothGatt object to be connected
                 * we have to manage this state before.
                 */
                End()
            },
            onDisconnect = {
                Timber.w("Disconnecting from ${device.address}")
                gatt.close()
                deviceGattMap.remove(device)
                listeners.forEach { it.get()?.onDisconnect?.invoke(device) }
                End()
            },
            onCharacteristicWrite = {
                gatt.findCharacteristic(characteristicUuid)?.let { characteristic ->
                    characteristic.writeType = writeType
                    gatt.write(characteristic, payload)
                    End(skip = true)
                } ?: run {
                    Timber.e("Cannot find $characteristicUuid to write to")
                    End()
                }
            },
            onCharacteristicRead = {
                gatt.findCharacteristic(characteristicUuid)?.let { characteristic ->
                    gatt.readCharacteristic(characteristic)
                    End(skip = true)
                } ?: this@ConnectionManager.run {
                    Timber.e("Cannot find $characteristicUuid to read from")
                    End()
                }
            },
            onDescriptorWrite = {
                gatt.findDescriptor(descriptorUuid)?.let { descriptor ->
                    gatt.write(descriptor, payload)
                    End(skip = true)
                } ?: this@ConnectionManager.run {
                    Timber.e("Cannot find $descriptorUuid to write to")
                    End()
                }
            },
            onDescriptorRead = {
                gatt.findDescriptor(descriptorUuid)?.let { descriptor ->
                    gatt.readDescriptor(descriptor)
                    End(skip = true)
                } ?: this@ConnectionManager.run {
                    Timber.e("Cannot find $descriptorUuid to read from")
                    End()
                }
            },
            onEnableNotifications = {
                gatt.findCharacteristic(characteristicUuid)?.let { characteristic ->
                    val descriptorUuid = UUID.fromString(CLIENT_CHARACTERISTIC_DESCRIPTOR_UUID)
                    val payload = when {
                        characteristic.isIndicatable() ->
                            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        characteristic.isNotifiable() ->
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        else -> {
                            Timber.e("${characteristic.uuid} doesn't support notifications/indications")
                            error("${characteristic.uuid} doesn't support notifications/indications")
                        }
                    }

                    characteristic.getDescriptor(descriptorUuid)?.let { descriptor ->
                        if (!gatt.setCharacteristicNotification(characteristic, true)) {
                            Timber.e("setCharacteristicNotification failed for ${characteristic.uuid}")
                            End()
                        }
                        gatt.write(descriptor, payload)
                        End(skip = true)
                    } ?: this@ConnectionManager.run {
                        Timber.e("${characteristic.uuid} doesn't contain the CCC descriptor!")
                        End()
                    }
                } ?: this@ConnectionManager.run {
                    Timber.e("Cannot find $characteristicUuid! Failed to enable notifications.")
                    End()
                }
            },
            onDisableNotifications = {
                gatt.findCharacteristic(characteristicUuid)?.let { characteristic ->
                    val characteristicUuid = UUID.fromString(CLIENT_CHARACTERISTIC_DESCRIPTOR_UUID)
                    characteristic.getDescriptor(characteristicUuid)?.let { descriptor ->
                        if (!gatt.setCharacteristicNotification(characteristic, false)) {
                            Timber.e("setCharacteristicNotification failed for ${characteristic.uuid}")
                            End()
                        }
                        gatt.write(descriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
                        End(skip = true)
                    } ?: run {
                        Timber.e("${characteristic.uuid} doesn't contain the CCC descriptor!")
                        End()
                    }
                } ?: run {
                    Timber.e("Cannot find $characteristicUuid! Failed to disable notifications.")
                    End()
                }
            },
            onMtuRequest = {
                gatt.requestMtu(mtu)
                End(skip = true)
            },
        ).doAfter {
            signalEndOfOperation()
        }
    }

    private val callback = object : GattCallbackWrapper() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, state: ConnectionState) {
            val deviceAddress = gatt.device.address
            when (state) {
                is ConnectionState.Connected -> {
                    Timber.w("onConnectionStateChange: connected to $deviceAddress")
                    deviceGattMap[gatt.device] = gatt
                    Handler(Looper.getMainLooper()).post {
                        gatt.discoverServices()
                    }
                }
                is ConnectionState.Disconnected -> {
                    Timber.e("onConnectionStateChange: disconnected from $deviceAddress")
                    teardownConnection(gatt.device)
                }
                else -> {
                    Timber.e("onConnectionStateChange: status ${state.status} encountered for $deviceAddress!")
                    if (pendingOperation is BleOperation.Connect) {
                        signalEndOfOperation()
                    }
                    teardownConnection(gatt.device)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: GattStatus) {
            with(gatt) {
                when (status) {
                    GattStatus.SUCCESS -> {
                        Timber.w("Discovered ${services.size} services for ${device.address}.")
                        printGattTable()
                        requestMtu(device, GATT_MAX_MTU_SIZE)
                        listeners.forEach { it.get()?.onConnectionSetupComplete?.invoke(this) }
                    }
                    else -> {
                        Timber.e("Service discovery failed due to status $status")
                        teardownConnection(gatt.device)
                    }
                }
            }

            if (pendingOperation is BleOperation.Connect) {
                signalEndOfOperation()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: GattStatus) {
            Timber.w("ATT MTU changed to $mtu, success: ${status == GattStatus.SUCCESS}")
            listeners.forEach { it.get()?.onMtuChanged?.invoke(gatt.device, mtu) }

            if (pendingOperation is BleOperation.MtuRequest) {
                signalEndOfOperation()
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: GattStatus
        ) {
            with(characteristic) {
                when (status) {
                    GattStatus.SUCCESS -> {
                        Timber.i("Read characteristic $uuid | value: ${value.decodeToString()}")
                        listeners.forEach {
                            it.get()?.onCharacteristicRead?.invoke(gatt.device, this, value)
                        }
                    }
                    GattStatus.READ_NOT_PERMITTED ->
                        Timber.e("Read not permitted for $uuid!")
                    else ->
                        Timber.e("Characteristic read failed for $uuid, error: $status")
                }
            }

            if (pendingOperation is BleOperation.CharacteristicRead) {
                signalEndOfOperation()
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: GattStatus
        ) {
            with(characteristic) {
                when (status) {
                    GattStatus.SUCCESS -> {
                        Timber.i("Wrote to characteristic $uuid | value: ${value.decodeToString()}")
                        listeners.forEach {
                            it.get()?.onCharacteristicWrite?.invoke(gatt.device, this, value)
                        }
                    }
                    GattStatus.WRITE_NOT_PERMITTED ->
                        Timber.e("Write not permitted for $uuid!")

                    else ->
                        Timber.e("Characteristic write failed for $uuid, error: $status")
                }
            }

            if (pendingOperation is BleOperation.CharacteristicWrite) {
                signalEndOfOperation()
            }
        }

        override fun onCharacteristicUpdated(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            with(characteristic) {
                Timber.i("Characteristic $uuid changed | value: ${value.decodeToString()}")
                listeners.forEach {
                    it.get()?.onCharacteristicChanged?.invoke(gatt.device, this, value)
                }
            }
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            value: ByteArray,
            status: GattStatus
        ) {
            with(descriptor) {
                when (status) {
                    GattStatus.SUCCESS -> {
                        Timber.i("Read descriptor $uuid | value: ${value.decodeToString()}")
                        listeners.forEach {
                            it.get()?.onDescriptorRead?.invoke(gatt.device, this, value)
                        }
                    }
                    GattStatus.READ_NOT_PERMITTED -> {
                        Timber.e("Read not permitted for $uuid!")
                    }
                    else -> {
                        Timber.e("Descriptor read failed for $uuid, error: $status")
                    }
                }
            }

            if (pendingOperation is BleOperation.DescriptorRead) {
                signalEndOfOperation()
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            value: ByteArray,
            status: GattStatus
        ) {
            when (status) {
                GattStatus.SUCCESS -> {
                    Timber.i("Wrote to descriptor ${descriptor.uuid} | value: ${value.toHexString()}")

                    if (descriptor.isConfigDescriptor()) {
                        onClientConfigCharacteristicWrite(gatt, value, descriptor.characteristic)
                    } else {
                        listeners.forEach {
                            it.get()?.onDescriptorWrite?.invoke(gatt.device, descriptor, value)
                        }
                    }
                }
                GattStatus.WRITE_NOT_PERMITTED ->
                    Timber.e("Write not permitted for ${descriptor.uuid}!")
                else ->
                    Timber.e("Descriptor write failed for ${descriptor.uuid}, error: $status")
            }

            val isNotifiable = pendingOperation is BleOperation.EnableNotifications ||
                pendingOperation is BleOperation.DisableNotifications

            when {
                descriptor.isConfigDescriptor() && isNotifiable ->
                    signalEndOfOperation()

                !descriptor.isConfigDescriptor() &&
                    pendingOperation is BleOperation.DescriptorWrite ->
                    signalEndOfOperation()
            }
        }

        private fun onClientConfigCharacteristicWrite(
            gatt: BluetoothGatt,
            value: ByteArray,
            characteristic: BluetoothGattCharacteristic
        ) {
            val charUuid = characteristic.uuid
            val notificationsEnabled =
                value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                    value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
            val notificationsDisabled =
                value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)

            when {
                notificationsEnabled -> {
                    Timber.w("Notifications or indications ENABLED on $charUuid")
                    listeners.forEach {
                        it.get()?.onNotificationsEnabled?.invoke(gatt.device, characteristic)
                    }
                }
                notificationsDisabled -> {
                    Timber.w("Notifications or indications DISABLED on $charUuid")
                    listeners.forEach {
                        it.get()?.onNotificationsDisabled?.invoke(gatt.device, characteristic)
                    }
                }
                else ->
                    Timber.e("Unexpected value ${value.decodeToString()} on CCCD of $charUuid")
            }
        }
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            with(intent) {
                if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                    val device = getParcelExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val previousBondState =
                        getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)
                    val bondState = getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                    val bondTransition = "${previousBondState.toBondStateDescription()} to " +
                        bondState.toBondStateDescription()
                    Timber.w("${device?.address} bond state changed | $bondTransition")
                }
            }
        }

        private fun Int.toBondStateDescription() = when (this) {
            BluetoothDevice.BOND_BONDED -> "BONDED"
            BluetoothDevice.BOND_BONDING -> "BONDING"
            BluetoothDevice.BOND_NONE -> "NOT BONDED"
            else -> "ERROR: $this"
        }
    }

    private fun BluetoothDevice.isConnected() = deviceGattMap.containsKey(this)
}
