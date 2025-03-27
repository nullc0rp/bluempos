package com.example.bluempos

// BLE mPOS App with Keypad and NFC Stub
// Updated to include price input via keypad UI

import android.Manifest
import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.*
import android.util.Log
import android.widget.*
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var bleManager: BluetoothManager
    private lateinit var bleAdapter: BluetoothAdapter
    private lateinit var gattServer: BluetoothGattServer
    private lateinit var nfcAdapter: NfcAdapter

    private val SERVICE_UUID = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")
    private val CHARACTERISTIC_UUID = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")

    private lateinit var priceDisplay: TextView
    private lateinit var statusDisplay: TextView
    private var currentInput = StringBuilder()
    private var isFlagUnlocked = false

    override fun onPause() {
        super.onPause()
        nfcAdapter.disableForegroundDispatch(this)
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT])
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            statusDisplay.text = "Waiting for NFC Card..."
        }

        findViewById<Button>(R.id.cancelButton).setOnClickListener {
            currentInput.clear()
            priceDisplay.text = "Charge: 0.00"
            statusDisplay.text = "Transaction cancelled."
        }
        bleManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bleAdapter = bleManager.adapter

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        startGattServer()
        startAdvertising()
    }

    @RequiresPermission(allOf = [
        android.Manifest.permission.BLUETOOTH_ADVERTISE,
        android.Manifest.permission.BLUETOOTH_CONNECT
    ])
    private fun startGattServer() {
        gattServer = bleManager.openGattServer(this, object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                runOnUiThread {
                    statusDisplay.text = "Device connected: ${device.address}"
                }
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
                if (characteristic.uuid == CHARACTERISTIC_UUID) {
                    val value = if (isFlagUnlocked) "FLAG{ble-basic-read}" else "LOCKED"
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value.toByteArray())
                }
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
                if (characteristic.uuid == CHARACTERISTIC_UUID && value?.decodeToString()?.trim() == "SEND_FLAG_2") {
                    isFlagUnlocked = true
                    runOnUiThread {
                        statusDisplay.text = "BLE Command Accepted. Notifying..."
                    }
                }
                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        })

        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(characteristic)
        gattServer.addService(service)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        intent?.let {
            if (NfcAdapter.ACTION_TECH_DISCOVERED == it.action) {
                val tag: Tag? = it.getParcelableExtra(NfcAdapter.EXTRA_TAG)
                statusDisplay.text = "NFC Tag detected!"

                tag?.let { nfcTag ->
                    val isoDep = IsoDep.get(nfcTag)

                    if (isoDep != null) {
                        try {
                            isoDep.connect()
                            if (isoDep.isConnected) {
                                val atr = isoDep.hiLayerResponse ?: byteArrayOf()
                                val historicalBytes = isoDep.historicalBytes ?: byteArrayOf()
                                statusDisplay.text = "NFC Connected!\nATR: ${atr.toHex()}"
                            }
                            isoDep.close()
                        } catch (e: Exception) {
                            statusDisplay.text = "NFC Error: ${e.message}"
                        }
                    } else {
                        statusDisplay.text = "Unsupported tag tech (not IsoDep)"
                    }

                    Log.d("NFC", "TAG: ${tag.techList.joinToString()}")
                    if (isoDep.historicalBytes?.contentEquals(byteArrayOf(0x12, 0x34)) == true) {
                        statusDisplay.text = "FLAG{nfc-hello-world}"
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        val filters = arrayOf<IntentFilter>()
        val techLists = arrayOf(arrayOf(IsoDep::class.java.name))

        nfcAdapter.enableForegroundDispatch(this, pendingIntent, filters, techLists)

        // Handle NFC if resumed from intent
        if (intent.action == NfcAdapter.ACTION_TECH_DISCOVERED) {
            onNewIntent(intent)
        }
        intent?.let { if (it.action == NfcAdapter.ACTION_TAG_DISCOVERED) onNewIntent(it) }

    }

    fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }

}
