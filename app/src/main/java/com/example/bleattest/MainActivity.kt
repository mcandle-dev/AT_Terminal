package com.example.bleattest

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bleattest.models.LogType
import com.example.bleattest.models.ScanParams
import com.example.bleattest.models.TerminalLog
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), InputDialogFragment.OnInputListener {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var terminalAdapter: TerminalAdapter
    private lateinit var atCommandManager: AtCommandManager

    private lateinit var btnEnableMaster: Button
    private lateinit var btnGetMac: Button
    private lateinit var btnScan: Button
    private lateinit var btnConnect: Button
    private lateinit var btnSendData: Button
    private lateinit var btnClear: Button

    private var isScanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupRecyclerView()
        setupAtCommandManager()
        setupButtonListeners()

        addLogToTerminal("App started", LogType.INFO)
    }

    private fun initializeViews() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        recyclerView = findViewById(R.id.recyclerViewTerminal)
        btnEnableMaster = findViewById(R.id.btnEnableMaster)
        btnGetMac = findViewById(R.id.btnGetMac)
        btnScan = findViewById(R.id.btnScan)
        btnConnect = findViewById(R.id.btnConnect)
        btnSendData = findViewById(R.id.btnSendData)
        btnClear = findViewById(R.id.btnClear)
    }

    private fun setupRecyclerView() {
        terminalAdapter = TerminalAdapter()
        recyclerView.adapter = terminalAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupAtCommandManager() {
        atCommandManager = AtCommandManager()
        atCommandManager.setOnAtResponseListener(object : AtCommandManager.OnAtResponseListener {
            override fun onResponse(response: String) {
                val logType = when {
                    response.startsWith("SCAN:", ignoreCase = true) -> LogType.SCAN
                    else -> LogType.RECEIVE
                }
                addLogToTerminal(response, logType)
            }

            override fun onError(error: String) {
                // Parse error code and provide friendly message
                val errorMessage = when {
                    error.contains("-2508") -> "Hardware not connected: /dev/ttyS1 not found"
                    error.contains("-2500") -> "Communication timeout"
                    error.contains("-2502") -> "Communication error"
                    else -> error
                }
                addLogToTerminal(errorMessage, LogType.ERROR)
            }
        })

        // Initialize serial port for Rockchip 3566 BLE module
        // Common device paths for industrial Android devices:
        // - /dev/ttyS0, /dev/ttyS1, /dev/ttyS2, /dev/ttyS3 (native UART)
        // - /dev/ttyUSB0 (USB-to-Serial)
        val devicePath = "/dev/ttyS1"  // Change this to your actual device path
        val baudrate = 115200
        val ret = atCommandManager.initSerialPort(devicePath, baudrate)

        when (ret) {
            0 -> {
                addLogToTerminal("Serial port opened: $devicePath @ $baudrate baud", LogType.INFO)
                addLogToTerminal("Ready. Please initialize with 'Enable Master' first.", LogType.INFO)
            }
            -1 -> {
                addLogToTerminal("Device not found: $devicePath", LogType.ERROR)
                addLogToTerminal("Please check device path", LogType.ERROR)
            }
            -2 -> {
                addLogToTerminal("Permission denied: $devicePath", LogType.ERROR)
                addLogToTerminal("Please check app has root access or su permission", LogType.ERROR)
            }
            else -> {
                addLogToTerminal("Failed to open serial port: $devicePath (code: $ret)", LogType.ERROR)
                addLogToTerminal("Please check device path and permissions", LogType.ERROR)
            }
        }
    }

    private fun setupButtonListeners() {
        btnEnableMaster.setOnClickListener {
            InputDialogFragment.newInstance(InputDialogFragment.CommandType.ENABLE_MASTER)
                .show(supportFragmentManager, "ENABLE_MASTER")
        }

        btnGetMac.setOnClickListener {
            executeGetMac()
        }

        btnScan.setOnClickListener {
            if (isScanning) {
                // Stop scan
                executeStopScan()
            } else {
                // Start scan
                InputDialogFragment.newInstance(InputDialogFragment.CommandType.SCAN)
                    .show(supportFragmentManager, "SCAN")
            }
        }

        btnConnect.setOnClickListener {
            InputDialogFragment.newInstance(InputDialogFragment.CommandType.CONNECT)
                .show(supportFragmentManager, "CONNECT")
        }

        btnSendData.setOnClickListener {
            InputDialogFragment.newInstance(InputDialogFragment.CommandType.SEND_DATA)
                .show(supportFragmentManager, "SEND_DATA")
        }

        btnClear.setOnClickListener {
            terminalAdapter.clearLogs()
            addLogToTerminal("Terminal cleared", LogType.INFO)
        }
    }

    private fun addLogToTerminal(content: String, type: LogType) {
        runOnUiThread {
            val log = TerminalLog(
                type = type,
                content = content
            )
            terminalAdapter.addLog(log)

            // Auto scroll to bottom
            recyclerView.scrollToPosition(terminalAdapter.getLogCount() - 1)
        }
    }

    // InputDialogFragment.OnInputListener implementations
    override fun onEnableMaster(enable: Boolean) {
        lifecycleScope.launch {
            addLogToTerminal("AT+ENABLEMASTER=${if (enable) 1 else 0}", LogType.SEND)

            val result = atCommandManager.enableMaster(enable)

            if (result.success) {
                addLogToTerminal("Master mode ${if (enable) "enabled" else "disabled"} successfully", LogType.INFO)

                // Start background receiving after successful initialization
                if (enable && !atCommandManager.isReceiving()) {
                    atCommandManager.startReceiving()
                    addLogToTerminal("Background receiver started", LogType.INFO)
                }
            } else {
                addLogToTerminal(
                    "Error: ${result.errorMessage ?: "Enable master failed"}",
                    LogType.ERROR
                )
            }
        }
    }

    override fun onStartScan(params: ScanParams) {
        lifecycleScope.launch {
            val command = params.toAtCommand()
            addLogToTerminal(command, LogType.SEND)

            val result = atCommandManager.startScan(params)

            if (result.success) {
                isScanning = true
                updateScanButton(true)
            } else {
                addLogToTerminal(
                    "Error: ${result.errorMessage ?: "Start scan failed"}",
                    LogType.ERROR
                )
            }
        }
    }

    override fun onConnect(macAddress: String) {
        lifecycleScope.launch {
            addLogToTerminal("AT+CONNECT=$macAddress", LogType.SEND)

            val result = atCommandManager.connect(macAddress)

            if (!result.success) {
                addLogToTerminal(
                    "Error: ${result.errorMessage ?: "Connect failed"}",
                    LogType.ERROR
                )
            }
        }
    }

    override fun onSendData(handle: Int, hexData: String) {
        lifecycleScope.launch {
            addLogToTerminal("AT+SEND=$handle,$hexData", LogType.SEND)

            val result = atCommandManager.sendData(handle, hexData)

            if (!result.success) {
                addLogToTerminal(
                    "Error: ${result.errorMessage ?: "Send data failed"}",
                    LogType.ERROR
                )
            }
        }
    }

    private fun executeGetMac() {
        lifecycleScope.launch {
            addLogToTerminal("AT+GETMAC", LogType.SEND)

            val result = atCommandManager.getMacAddress()

            if (!result.success) {
                addLogToTerminal(
                    "Error: ${result.errorMessage ?: "Get MAC failed"}",
                    LogType.ERROR
                )
            }
        }
    }

    private fun executeStopScan() {
        lifecycleScope.launch {
            addLogToTerminal("AT+STOPSCAN", LogType.SEND)

            val result = atCommandManager.stopScan()

            isScanning = false
            updateScanButton(false)

            if (!result.success) {
                addLogToTerminal(
                    "Error: ${result.errorMessage ?: "Stop scan failed"}",
                    LogType.ERROR
                )
            }
        }
    }

    private fun updateScanButton(scanning: Boolean) {
        runOnUiThread {
            if (scanning) {
                btnScan.text = "Stop Scan"
                btnScan.setBackgroundColor(getColor(android.R.color.holo_red_dark))
            } else {
                btnScan.text = "Scan"
                btnScan.setBackgroundColor(getColor(com.google.android.material.R.color.design_default_color_primary))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        atCommandManager.stopReceiving()
        if (isScanning) {
            lifecycleScope.launch {
                atCommandManager.stopScan()
            }
        }
        atCommandManager.closeSerialPort()
        Log.d(TAG, "Activity destroyed, serial port closed")
    }
}
