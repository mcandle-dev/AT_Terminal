package com.example.bleattest

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException

/**
 * USB Serial Manager for AT Command communication
 * Uses usb-serial-for-android library
 */
class UsbSerialManager(private val context: Context) {
    companion object {
        private const val TAG = "UsbSerialManager"
        private const val ACTION_USB_PERMISSION = "com.example.bleattest.USB_PERMISSION"
    }

    private var usbManager: UsbManager? = null
    private var usbSerialPort: UsbSerialPort? = null
    private var isOpen = false

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let {
                            Log.d(TAG, "USB permission granted for ${it.deviceName}")
                        }
                    } else {
                        Log.w(TAG, "USB permission denied")
                    }
                }
            }
        }
    }

    init {
        usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        // Register USB permission receiver
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbPermissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbPermissionReceiver, filter)
        }
    }

    /**
     * Find and open the first available USB serial device
     * @param baudrate Baud rate (default: 115200)
     * @return 0: success, negative: failed
     */
    fun open(baudrate: Int = 115200): Int {
        if (isOpen) {
            Log.w(TAG, "USB serial port already opened")
            return 0
        }

        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            Log.e(TAG, "No USB serial devices found")
            return -1
        }

        val driver = availableDrivers[0]
        val device = driver.device

        // Check permission
        if (usbManager?.hasPermission(device) != true) {
            Log.w(TAG, "No USB permission, requesting...")
            requestPermission(device)
            return -2  // Permission needed
        }

        val connection = usbManager?.openDevice(device)
        if (connection == null) {
            Log.e(TAG, "Failed to open USB device connection")
            return -3
        }

        return try {
            usbSerialPort = driver.ports[0]
            usbSerialPort?.open(connection)
            usbSerialPort?.setParameters(baudrate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            isOpen = true
            Log.d(TAG, "USB serial port opened: ${device.deviceName} @ $baudrate baud")
            0
        } catch (e: IOException) {
            Log.e(TAG, "Failed to open serial port", e)
            connection.close()
            -4
        } catch (e: Exception) {
            Log.e(TAG, "Error opening serial port", e)
            connection.close()
            -5
        }
    }

    /**
     * Open specific USB device by vendor ID and product ID
     * @param vendorId Vendor ID
     * @param productId Product ID
     * @param baudrate Baud rate (default: 115200)
     * @return 0: success, negative: failed
     */
    fun openByVidPid(vendorId: Int, productId: Int, baudrate: Int = 115200): Int {
        if (isOpen) {
            Log.w(TAG, "USB serial port already opened")
            return 0
        }

        val deviceList = usbManager?.deviceList
        if (deviceList.isNullOrEmpty()) {
            Log.e(TAG, "No USB devices found")
            return -1
        }

        for (device in deviceList.values) {
            if (device.vendorId == vendorId && device.productId == productId) {
                val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
                if (driver != null) {
                    // Check permission
                    if (usbManager?.hasPermission(device) != true) {
                        Log.w(TAG, "No USB permission, requesting...")
                        requestPermission(device)
                        return -2
                    }

                    val connection = usbManager?.openDevice(device)
                    if (connection == null) {
                        Log.e(TAG, "Failed to open USB device connection")
                        return -3
                    }

                    return try {
                        usbSerialPort = driver.ports[0]
                        usbSerialPort?.open(connection)
                        usbSerialPort?.setParameters(baudrate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

                        isOpen = true
                        Log.d(TAG, "USB serial port opened: VID=$vendorId PID=$productId @ $baudrate baud")
                        0
                    } catch (e: IOException) {
                        Log.e(TAG, "Failed to open serial port", e)
                        connection.close()
                        -4
                    } catch (e: Exception) {
                        Log.e(TAG, "Error opening serial port", e)
                        connection.close()
                        -5
                    }
                }
            }
        }

        Log.e(TAG, "USB device not found: VID=$vendorId PID=$productId")
        return -6
    }

    /**
     * Request USB permission
     */
    private fun requestPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION),
            flags
        )
        usbManager?.requestPermission(device, permissionIntent)
    }

    /**
     * Close USB serial port
     */
    fun close() {
        if (!isOpen) {
            return
        }

        try {
            usbSerialPort?.close()
            usbSerialPort = null
            isOpen = false
            Log.d(TAG, "USB serial port closed")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to close serial port", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error closing serial port", e)
        }
    }

    /**
     * Send data through USB serial port
     * @param data Data to send
     * @param length Data length
     * @return 0: success, negative: failed
     */
    fun send(data: ByteArray, length: Int): Int {
        if (!isOpen || usbSerialPort == null) {
            Log.e(TAG, "USB serial port not opened")
            return -1
        }

        return try {
            usbSerialPort?.write(data, length)
            Log.d(TAG, "Sent $length bytes: ${String(data, 0, length)}")
            0
        } catch (e: IOException) {
            Log.e(TAG, "Failed to send data", e)
            -2
        } catch (e: Exception) {
            Log.e(TAG, "Error sending data", e)
            -3
        }
    }

    /**
     * Receive data from USB serial port (blocking with timeout)
     * @param buffer Buffer to store received data
     * @param length Array to store actual received length (output parameter)
     * @param maxLength Maximum buffer size
     * @param timeout Timeout in milliseconds
     * @return 0: success, negative: failed
     */
    fun receive(buffer: ByteArray, length: IntArray, maxLength: Int, timeout: Int): Int {
        if (!isOpen || usbSerialPort == null) {
            Log.e(TAG, "USB serial port not opened")
            return -1
        }

        return try {
            val startTime = System.currentTimeMillis()
            val bytesRead = usbSerialPort?.read(buffer, timeout) ?: -1

            if (bytesRead > 0) {
                length[0] = bytesRead
                Log.d(TAG, "Received $bytesRead bytes: ${String(buffer, 0, bytesRead)}")
                0
            } else if (bytesRead == 0) {
                // Timeout
                length[0] = 0
                -4
            } else {
                // Error
                length[0] = 0
                -2
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to receive data", e)
            length[0] = 0
            -2
        } catch (e: Exception) {
            Log.e(TAG, "Error receiving data", e)
            length[0] = 0
            -3
        }
    }

    /**
     * Check if USB serial port is opened
     */
    fun isOpened(): Boolean = isOpen

    /**
     * Get list of available USB serial devices
     */
    fun getAvailableDevices(): List<String> {
        val devices = mutableListOf<String>()
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

        for (driver in availableDrivers) {
            val device = driver.device
            devices.add("${device.deviceName} (VID: ${device.vendorId}, PID: ${device.productId})")
        }

        return devices
    }

    /**
     * Cleanup - unregister receiver
     */
    fun cleanup() {
        try {
            context.unregisterReceiver(usbPermissionReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister receiver", e)
        }
    }
}
