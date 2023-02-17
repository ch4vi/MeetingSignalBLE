package com.ch4vi.meetingsignal.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat


class BluetoothPermission(caller: ActivityResultCaller) {
    companion object {
        @RequiresApi(Build.VERSION_CODES.S)
        private const val scanPermission = Manifest.permission.BLUETOOTH_SCAN

        @RequiresApi(Build.VERSION_CODES.S)
        private const val connectPermission = Manifest.permission.BLUETOOTH_CONNECT

        private const val coarseLocation = Manifest.permission.ACCESS_COARSE_LOCATION
        private const val fineLocation = Manifest.permission.ACCESS_FINE_LOCATION
        private const val bkgLocation = Manifest.permission.ACCESS_BACKGROUND_LOCATION

        fun checkPermissions(context: Context): Boolean =
            notGrantedPermissions(context).isEmpty()

        fun notGrantedPermissions(context: Context): Array<String> {
            val permissions = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val scanCheck = ContextCompat.checkSelfPermission(context, scanPermission)
                val connectCheck = ContextCompat.checkSelfPermission(context, connectPermission)
                permissions.apply {
                    if (scanCheck != PackageManager.PERMISSION_GRANTED) add(scanPermission)
                    if (connectCheck != PackageManager.PERMISSION_GRANTED) add(connectPermission)
                }
            }

            val coarseCheck = ContextCompat.checkSelfPermission(context, coarseLocation)
            if (coarseCheck != PackageManager.PERMISSION_GRANTED) permissions.add(coarseLocation)

            val fineCheck = ContextCompat.checkSelfPermission(context, fineLocation)
            if (fineCheck != PackageManager.PERMISSION_GRANTED) permissions.add(fineLocation)

            val bkgCheck = ContextCompat.checkSelfPermission(context, bkgLocation)
            if (bkgCheck != PackageManager.PERMISSION_GRANTED) permissions.add(bkgLocation)

            return permissions.toTypedArray()
        }

    }

    var onResult: (isGranted: Boolean) -> Unit = {}

    private val requestPermissionLauncher =
        caller.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grantedPermissionList ->
            onResult(grantedPermissionList.filter { !it.value }.none())
        }

    fun requestPermission(context: Context) {
        val permissionsList = notGrantedPermissions(context)
        if (permissionsList.isNotEmpty()) requestPermissionLauncher.launch(permissionsList)
        else onResult(true)
    }
}
