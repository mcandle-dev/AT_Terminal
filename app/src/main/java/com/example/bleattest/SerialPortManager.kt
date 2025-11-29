package com.example.bleattest

import android.util.Log
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Serial Port Manager for AT Command communication
 * Uses google/android-serialport-api for native UART (/dev/ttyS*)
 */
class SerialPortManager {
    companion object {
        private const val TAG = "SerialPortManager"
        private const val DEFAULT_DEVICE = "/dev/ttyS0"
        private const val DEFAULT_BAUDRATE = 115200
    }

    private var serialPort: SerialPort? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var isOpen = false

    /**
     * Open serial port
     * @param devicePath Serial device path (e.g. "/dev/ttyS0", "/dev/ttyS1")
     * @param baudrate Baud rate (default: 115200)
     * @return 0: success, -1: failed
     */
    fun open(devicePath: String = DEFAULT_DEVICE, baudrate: Int = DEFAULT_BAUDRATE): Int {
        if (isOpen) {
            Log.w(TAG, "Serial port already opened")
            return 0
        }

        return try {
            val device = File(devicePath)

            // Check if device exists
            if (!device.exists()) {
                Log.e(TAG, "Device not found: $devicePath")
                return -1
            }

            // Open serial port
            serialPort = SerialPort(device, baudrate, 0)
            inputStream = serialPort?.inputStream
            outputStream = serialPort?.outputStream

            isOpen = true
            Log.d(TAG, "Serial port opened: $devicePath @ $baudrate baud")
            0
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception: no permission to access $devicePath", e)
            -2
        } catch (e: IOException) {
            Log.e(TAG, "IO exception: failed to open $devicePath", e)
            -3
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open serial port", e)
            -4
        }
    }

    /**
     * Close serial port
     */
    fun close() {
        if (!isOpen) {
            return
        }

        try {
            inputStream?.close()
            inputStream = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close input stream", e)
        }

        try {
            outputStream?.close()
            outputStream = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close output stream", e)
        }

        try {
            serialPort?.close()
            serialPort = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close serial port", e)
        }

        isOpen = false
        Log.d(TAG, "Serial port closed")
    }

    /**
     * Send data through serial port
     * @param data Data to send
     * @param length Data length
     * @return 0: success, negative: failed
     */
    fun send(data: ByteArray, length: Int): Int {
        if (!isOpen) {
            Log.e(TAG, "Serial port not opened")
            return -1
        }

        return try {
            outputStream?.write(data, 0, length)
            outputStream?.flush()
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
     * Receive data from serial port (blocking with timeout)
     * @param buffer Buffer to store received data
     * @param length Array to store actual received length (output parameter)
     * @param maxLength Maximum buffer size
     * @param timeout Timeout in milliseconds
     * @return 0: success, negative: failed
     */
    fun receive(buffer: ByteArray, length: IntArray, maxLength: Int, timeout: Int): Int {
        if (!isOpen) {
            Log.e(TAG, "Serial port not opened")
            return -1
        }

        return try {
            val startTime = System.currentTimeMillis()
            var totalBytesRead = 0

            // Wait for data with timeout
            while (totalBytesRead == 0 && (System.currentTimeMillis() - startTime) < timeout) {
                val available = inputStream?.available() ?: 0
                if (available > 0) {
                    val bytesToRead = minOf(available, maxLength - totalBytesRead)
                    val bytesRead = inputStream?.read(buffer, totalBytesRead, bytesToRead) ?: -1
                    if (bytesRead > 0) {
                        totalBytesRead += bytesRead
                    }
                    break
                }
                Thread.sleep(10) // Small delay to avoid busy waiting
            }

            length[0] = totalBytesRead
            if (totalBytesRead > 0) {
                Log.d(TAG, "Received $totalBytesRead bytes: ${String(buffer, 0, totalBytesRead)}")
                0
            } else {
                // Timeout or no data
                -4
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to receive data", e)
            -2
        } catch (e: Exception) {
            Log.e(TAG, "Error receiving data", e)
            -3
        }
    }

    /**
     * Check if serial port is opened
     */
    fun isOpened(): Boolean = isOpen

    /**
     * Get available bytes count
     */
    fun available(): Int {
        return try {
            inputStream?.available() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * CTS Control (Toggle RTS pin for beacon mode termination)
     * @return 0: success, negative: failed
     */
    fun ctsControl(): Int {
        if (!isOpen) {
            Log.e(TAG, "Serial port not opened")
            return -1
        }

        return try {
            val ret = serialPort?.ctsControl() ?: -1
            if (ret == 0) {
                Log.d(TAG, "CTS control (RTS toggle) successful")
            } else {
                Log.e(TAG, "CTS control failed: $ret")
            }
            ret
        } catch (e: Exception) {
            Log.e(TAG, "Error in CTS control", e)
            -2
        }
    }
}
