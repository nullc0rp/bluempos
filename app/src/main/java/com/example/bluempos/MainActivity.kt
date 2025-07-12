package com.example.bluempos

import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Intent
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var bleManager: BluetoothManager
    private lateinit var bleAdapter: BluetoothAdapter
    private lateinit var gattServer: BluetoothGattServer

    private val SERVICE_UUID = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")
    private val CHARACTERISTIC_UUID = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")
    private val BATTERY_UUID = UUID.fromString("00001235-0000-1000-8000-00805f9b34fb")
    private val VERSION_UUID = UUID.fromString("00001236-0000-1000-8000-00805f9b34fb")
    private val RANDOM_UUID = UUID.fromString("00001237-0000-1000-8000-00805f9b34fb")
    private val TXLIST_UUID = UUID.fromString("00001238-0000-1000-8000-00805f9b34fb")
    private val SERIAL_UUID = UUID.fromString("00001239-0000-1000-8000-00805f9b34fb")

    private lateinit var priceDisplay: TextView
    private lateinit var statusDisplay: TextView
    private var currentInput = StringBuilder()
    private var isFlagUnlocked = false
    private val transactionList = listOf("TXN12345: $10.00", "TXN12346: $22.50", "TXN12347: $5.75")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread.setDefaultUncaughtExceptionHandler { thread: Thread?, e: Throwable ->
            try {
                FileWriter(getExternalFilesDir(null).toString() + "/crashlog.txt")
                    .use { writer ->
                        e.printStackTrace(PrintWriter(writer))
                    }
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
        setContentView(R.layout.activity_main)

        priceDisplay = findViewById(R.id.priceDisplay)
        statusDisplay = findViewById(R.id.statusDisplay)

        val keypad = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "←")
        val grid = findViewById<GridLayout>(R.id.keypadGrid)

        keypad.forEach { label ->
            val btn = Button(this).apply {
                text = label
                textSize = 24f
                setOnClickListener {
                    when (label) {
                        "←" -> if (currentInput.isNotEmpty()) currentInput.deleteCharAt(currentInput.length - 1)
                        else -> currentInput.append(label)
                    }
                    priceDisplay.text = "Charge: ${currentInput}"
                }
            }
            grid.addView(btn)
        }

        findViewById<Button>(R.id.acceptButton).setOnClickListener {
            statusDisplay.text = "Waiting for BLE Connection..."
        }

        findViewById<Button>(R.id.cancelButton).setOnClickListener {
            currentInput.clear()
            priceDisplay.text = "Charge: 0.00"
            statusDisplay.text = "Transaction cancelled."
        }
        bleManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bleAdapter = bleManager.adapter

        startGattServer()
        startAdvertising()
    }

    private fun startGattServer() {
        gattServer = bleManager.openGattServer(this, object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                runOnUiThread {
                    statusDisplay.text = "Device connected: ${device.address}"
                }
            }

            override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
                val value = when (characteristic.uuid) {
                    CHARACTERISTIC_UUID -> if (isFlagUnlocked) "FLAG{ble-basic-read}" else "LOCKED"
                    BATTERY_UUID -> "Battery: 86%"
                    VERSION_UUID -> "API Version: 1.3.2"
                    RANDOM_UUID -> UUID.randomUUID().toString()
                    TXLIST_UUID -> transactionList.getOrNull(currentInput.toString().toIntOrNull() ?: -1) ?: "INVALID TX ID"
                    else -> "UNKNOWN"
                }
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value.toByteArray())
            }

            override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
                val str = value?.decodeToString()?.trim()
                when (characteristic.uuid) {
                    CHARACTERISTIC_UUID -> {
                        if (str == "SEND_FLAG_2") isFlagUnlocked = true
                        runOnUiThread { statusDisplay.text = "BLE Command Accepted." }
                    }
                    SERIAL_UUID -> {
                        val response = handleCommand(str ?: "")
                        characteristic.value = response.toByteArray()
                    }
                }
                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        })

        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        listOf(
            CHARACTERISTIC_UUID,
            BATTERY_UUID,
            VERSION_UUID,
            RANDOM_UUID,
            TXLIST_UUID,
            SERIAL_UUID
        ).forEach { uuid ->
            val characteristic = BluetoothGattCharacteristic(
                uuid,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            service.addCharacteristic(characteristic)
        }
        gattServer.addService(service)
    }

    private fun handleCommand(cmd: String): String {
        return when (cmd.lowercase()) {
            "ping" -> "pong"
            "flag" -> if (isFlagUnlocked) "FLAG{ble-basic-read}" else "NO FLAG"
            "price" -> "${currentInput}"
            else -> "UNKNOWN COMMAND"
        }
    }

    private fun startAdvertising() {
        val advertiser = bleAdapter.bluetoothLeAdvertiser
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        advertiser.startAdvertising(settings, data, object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                super.onStartSuccess(settingsInEffect)
                statusDisplay.text = "BLE Advertising Started."
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                statusDisplay.text = "BLE Advertising Failed: $errorCode"
            }
        })
    }
}