package com.sh27.androidmouse

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import android.widget.Button

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private var bluetoothHidDevice: BluetoothHidDevice? = null
    private var connectedHostDevice: BluetoothDevice? = null

    private val sdpSettings = BluetoothHidDeviceAppSdpSettings(
        "Android Mouse",
        "Android phone as Bluetooth mouse",
        "Shivam",
        BluetoothHidDevice.SUBCLASS1_MOUSE,
        REPORT_DESCRIPTOR
    )
    private val qosSettings: BluetoothHidDeviceAppQosSettings? = null
    private  val executor = Executors.newSingleThreadExecutor()

    private val hidCallback = object : BluetoothHidDevice.Callback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            runOnUiThread {
                val deviceLabel = pluggedDevice?.name ?: pluggedDevice?.address ?: "no device"

                statusText.text = if (registered) {
                    "HID app registered\nDevice: $deviceLabel"
                } else {
                    "HID app not registered\nDevice: $deviceLabel"
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChanged(
            device: BluetoothDevice,
            state: Int
        ) {
            runOnUiThread {
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connectedHostDevice = device
                        statusText.text = "Host connected ${device.name ?: device.address}"
                    }

                    BluetoothProfile.STATE_CONNECTING -> {
                        statusText.text = "Host connecting..."
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        connectedHostDevice = null
                        statusText.text = "Host disconnected"
                    }

                    BluetoothProfile.STATE_DISCONNECTING -> {
                        statusText.text = "Host disconnecting..."
                    }

                    else -> {
                        statusText.text = "Host state changed: $state"
                    }
                }
            }
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == HID_DEVICE_PROFILE) {
                bluetoothHidDevice = proxy as BluetoothHidDevice

                val registered = bluetoothHidDevice?.registerApp(
                    sdpSettings,
                    null,
                    qosSettings,
                    executor,
                    hidCallback
                ) ?: false

                statusText.text = if (registered) {
                    "Registering HID app..."
                } else {
                    "HID app registration failed"
                }
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == HID_DEVICE_PROFILE) {
                bluetoothHidDevice = null
                statusText.text = getString(R.string.hid_device_api_disconnected)
            }
        }
    }

    private val bluetoothPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) {
            checkBluetoothState()
        } else {
            statusText.text = getString(R.string.bluetooth_permission_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        val moveButton = findViewById<Button>(R.id.moveButton)
        moveButton.setOnClickListener {
            sendMouseMove(20,0)
        }

       if (needsBluetoothConnectPermission()) {
           bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
       } else {
           checkBluetoothState()
       }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendMouseMove(deltaX: Int, deltaY: Int) {
        val host = connectedHostDevice
        val hidDevice = bluetoothHidDevice

        if (host == null || hidDevice == null) {
            statusText.text = "No host connected"
            return
        }

        val report = byteArrayOf(
            0x00,
            deltaX.toByte(),
            deltaY.toByte(),
            0x00
        )

        val success = hidDevice.sendReport(host, MOUSE_REPORT_ID, report)

        statusText.text = if (success) {
            "Mouse move report sent"
        } else {
            "Failed to send mouse move"
        }
    }

    private fun checkBluetoothState() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

        when {
            bluetoothAdapter == null -> {
                statusText.text = getString(R.string.bluetooth_not_available)
            }
            !bluetoothAdapter.isEnabled -> {
                statusText.text = getString(R.string.bluetooth_is_off)
            }
            else -> {
                statusText.text = getString(R.string.checking_hid_device_api)
                val started = bluetoothAdapter.getProfileProxy(
                    this,
                    profileListener,
                    HID_DEVICE_PROFILE
                )

                if(!started) {
                    statusText.text = getString(R.string.could_not_start_hid_api_check)
                }
            }
        }
    }

    private  fun needsBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

        bluetoothAdapter?.closeProfileProxy(HID_DEVICE_PROFILE, bluetoothHidDevice)

        super.onDestroy()
    }

    companion object {
        private  const val HID_DEVICE_PROFILE = 19
        private const val MOUSE_REPORT_ID = 1

        private val REPORT_DESCRIPTOR = byteArrayOf(
            0x05, 0x01,
            0x09, 0x02,
            0xA1.toByte(), 0x01,
            0x09, 0x01,
            0xA1.toByte(), 0x00,
            0x85.toByte(), 0x01,
            0x05, 0x09,
            0x19, 0x01,
            0x29, 0x03,
            0x15, 0x00,
            0x25, 0x01,
            0x95.toByte(), 0x03,
            0x75, 0x01,
            0x81.toByte(), 0x02,
            0x95.toByte(), 0x01,
            0x75, 0x05,
            0x81.toByte(), 0x03,
            0x05, 0x01,
            0x09, 0x30,
            0x09, 0x31,
            0x09, 0x38,
            0x15, 0x81.toByte(),
            0x25, 0x7F,
            0x75, 0x08,
            0x95.toByte(), 0x03,
            0x81.toByte(), 0x06,
            0xC0.toByte(),
            0xC0.toByte()
        )

    }
}