package com.example.bleattest

import android.util.Log
import com.example.bleattest.models.AtCommandResult
import com.example.bleattest.models.ScanParams
import kotlinx.coroutines.*

class AtCommandManager {
    companion object {
        private const val TAG = "AtCommandManager"
        private const val BUFFER_SIZE = 4096
        private const val RESPONSE_TIMEOUT = 200L // ms
    }

    var isScanning: Boolean = false
        private set

    private val serialPortManager = SerialPortManager()
    private var receiveJob: Job? = null
    private var listener: OnAtResponseListener? = null
    private var consecutiveErrors = 0
    private val maxConsecutiveErrors = 3

    interface OnAtResponseListener {
        fun onResponse(response: String)
        fun onError(error: String)
    }

    fun setOnAtResponseListener(listener: OnAtResponseListener) {
        this.listener = listener
    }

    /**
     * Initialize serial port connection
     * @param devicePath Serial device path (e.g. "/dev/ttyS0", "/dev/ttyS1")
     * @param baudrate Baud rate (default: 115200)
     * @return 0: success, negative: failed
     */
    fun initSerialPort(devicePath: String = "/dev/ttyS0", baudrate: Int = 115200): Int {
        return serialPortManager.open(devicePath, baudrate)
    }

    /**
     * Close serial port connection
     */
    fun closeSerialPort() {
        serialPortManager.close()
    }

    /**
     * AT 명령 전송
     * @param command AT 명령 (자동으로 \r\n 추가, 단 "+++" 제외)
     * @return 0: 성공, 음수: 실패
     */
    fun sendAtCommand(command: String): Int {
        val finalCommand = if (command == "+++") {
            command
        } else {
            if (command.endsWith("\r\n")) command else "$command\r\n"
        }

        val bytes = finalCommand.toByteArray()
        val ret = serialPortManager.send(bytes, bytes.size)

        if (ret != 0) {
            Log.e(TAG, "Failed to send AT command: $command, ret=$ret")
        } else {
            Log.d(TAG, "Sent AT command: $command")
        }

        return ret
    }

    /**
     * AT 응답 수신 (블로킹)
     * @return 수신된 문자열, null: 에러
     */
    fun receiveAtResponse(): String? {
        val buffer = ByteArray(BUFFER_SIZE)
        val lengthArray = intArrayOf(0)
        val timeout = 1000 // 1초 타임아웃
        val ret = serialPortManager.receive(buffer, lengthArray, BUFFER_SIZE, timeout)

        return if (ret == 0 && lengthArray[0] > 0) {
            String(buffer, 0, lengthArray[0]).trim()
        } else {
            if (ret < 0 && consecutiveErrors == 0) {
                // Only log error on first occurrence to avoid spam
                Log.e(TAG, "Failed to receive AT response, ret=$ret")
            }
            null
        }
    }

    /**
     * 백그라운드에서 지속적으로 AT 응답 수신
     */
    fun startReceiving() {
        if (receiveJob?.isActive == true) {
            Log.w(TAG, "Receive job already running")
            return
        }

        consecutiveErrors = 0
        receiveJob = CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Started receiving AT responses")
            while (isActive && consecutiveErrors < maxConsecutiveErrors) {
                try {
                    val response = receiveAtResponse()
                    if (response != null && response.isNotEmpty()) {
                        consecutiveErrors = 0 // Reset error counter on success
                        withContext(Dispatchers.Main) {
                            listener?.onResponse(response)
                        }
                        delay(100) // 다음 수신 대기
                    } else {
                        // No response, wait longer before retrying
                        delay(500)
                    }
                } catch (e: Exception) {
                    consecutiveErrors++
                    Log.e(TAG, "Error in receive loop (${consecutiveErrors}/${maxConsecutiveErrors})", e)

                    if (consecutiveErrors >= maxConsecutiveErrors) {
                        withContext(Dispatchers.Main) {
                            listener?.onError("Hardware connection failed. Please check device connection.")
                        }
                        Log.e(TAG, "Stopped receiving due to consecutive errors")
                        break
                    }
                    delay(1000) // Wait longer after error
                }
            }
        }
    }

    /**
     * AT 응답 수신 중지
     */
    fun stopReceiving() {
        receiveJob?.cancel()
        receiveJob = null
        Log.d(TAG, "Stopped receiving AT responses")
    }

    /**
     * 수신 중인지 확인
     */
    fun isReceiving(): Boolean {
        return receiveJob?.isActive == true
    }

    /**
     * CTS 제어 (Beacon 모드 종료)
     * @return 0: 성공, 음수: 실패
     */
    fun ctsControl(): Int {
        return serialPortManager.ctsControl()
    }

    /**
     * Enable/Disable Master 모드
     * AT 명령 시퀀스:
     * 0. CTS 제어 (Beacon 모드 종료)
     * 1. +++ (AT 모드 진입, 응답 없음)
     * 2. AT+OBSERVER=0 (Master) 또는 AT+OBSERVER=1 (Observer)
     * 3. AT+EXIT (AT 모드 종료)
     * 4. +++ (AT 모드 재진입, 응답 없음)
     */
    suspend fun enableMaster(enable: Boolean): AtCommandResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            try {
                // Step 0: CTS 제어 (Beacon 모드 종료) - TEMPORARILY DISABLED FOR TESTING
//                Log.d(TAG, "Calling CTS control (beacon mode termination)...")
//                val ctsRet = ctsControl()
//                if (ctsRet != 0) {
//                    Log.e(TAG, "CTS control failed with code: $ctsRet")
//                    return@withContext AtCommandResult(
//                        success = false,
//                        response = "",
//                        errorMessage = "CTS control failed, code: $ctsRet",
//                        executionTime = System.currentTimeMillis() - startTime
//                    )
//                }
                Log.d(TAG, "Waiting 1000ms before +++ (pre-Guard Time)...")
                delay(1000) // Guard Time before +++: ensure no data transmitted for 1 second

                // Step 1: AT 모드 진입 (+++ 응답 없음)
                Log.d(TAG, "Sending command: +++")
                var ret = sendAtCommand("+++")
                if (ret != 0) {
                    return@withContext AtCommandResult(
                        success = false,
                        response = "",
                        errorMessage = "Failed to send +++ command",
                        executionTime = System.currentTimeMillis() - startTime
                    )
                }

                Log.d(TAG, "Waiting 1000ms Guard Time for AT mode entry...")
                delay(1000) // Guard Time: EFR32BG22 requires minimum 1 second for AT mode entry
                Log.d(TAG, "Guard Time complete, AT mode should be active now")

                // Step 2: OBSERVER 모드 설정 (0 = Master mode, 1 = Observer mode)
                val observerCommand = if (enable) "AT+OBSERVER=0" else "AT+OBSERVER=1"
                Log.d(TAG, "Sending command: $observerCommand")
                ret = sendAtCommand(observerCommand)
                if (ret != 0) {
                    return@withContext AtCommandResult(
                        success = false,
                        response = "",
                        errorMessage = "Failed to send OBSERVER command",
                        executionTime = System.currentTimeMillis() - startTime
                    )
                }
                delay(RESPONSE_TIMEOUT)

                // 응답 확인
                val observerResponse = receiveAtResponse() ?: ""
                Log.d(TAG, "OBSERVER response: $observerResponse")
                if (!observerResponse.contains("OK", ignoreCase = true)) {
                    return@withContext AtCommandResult(
                        success = false,
                        response = observerResponse,
                        errorMessage = "OBSERVER command did not return OK",
                        executionTime = System.currentTimeMillis() - startTime
                    )
                }

                // Step 3: AT 모드 종료
                Log.d(TAG, "Sending command: AT+EXIT")
                ret = sendAtCommand("AT+EXIT")
                if (ret != 0) {
                    return@withContext AtCommandResult(
                        success = false,
                        response = "",
                        errorMessage = "Failed to send EXIT command",
                        executionTime = System.currentTimeMillis() - startTime
                    )
                }
                delay(RESPONSE_TIMEOUT)
                val exitResponse = receiveAtResponse() ?: ""
                Log.d(TAG, "EXIT response: $exitResponse")

                // Step 4: AT 모드 재진입 (+++ 응답 없음)
                Log.d(TAG, "Sending command: +++")
                ret = sendAtCommand("+++")
                if (ret != 0) {
                    return@withContext AtCommandResult(
                        success = false,
                        response = "",
                        errorMessage = "Failed to send second +++ command",
                        executionTime = System.currentTimeMillis() - startTime
                    )
                }
                delay(1000) // Guard Time: EFR32BG22 requires minimum 1 second for AT mode re-entry

                val executionTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Master mode ${if (enable) "enabled" else "disabled"} successfully")

                AtCommandResult(
                    success = true,
                    response = "Master mode ${if (enable) "enabled" else "disabled"}",
                    executionTime = executionTime
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in enableMaster: ${e.message}", e)
                AtCommandResult(
                    success = false,
                    response = "",
                    errorMessage = "Error: ${e.message}",
                    executionTime = System.currentTimeMillis() - startTime
                )
            }
        }
    }

    /**
     * Get MAC Address
     */
    suspend fun getMacAddress(): AtCommandResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val command = "AT+GETMAC"

            val ret = sendAtCommand(command)
            if (ret != 0) {
                return@withContext AtCommandResult(
                    success = false,
                    response = "",
                    errorMessage = "Failed to send command, code: $ret",
                    executionTime = System.currentTimeMillis() - startTime
                )
            }

            delay(RESPONSE_TIMEOUT)
            val response = receiveAtResponse() ?: ""
            val executionTime = System.currentTimeMillis() - startTime

            AtCommandResult(
                success = response.contains("MAC:", ignoreCase = true),
                response = response,
                executionTime = executionTime
            )
        }
    }

    /**
     * Start BLE Scan
     */
    suspend fun startScan(params: ScanParams): AtCommandResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val command = params.toAtCommand()

            val ret = sendAtCommand(command)
            if (ret != 0) {
                return@withContext AtCommandResult(
                    success = false,
                    response = "",
                    errorMessage = "Failed to send command, code: $ret",
                    executionTime = System.currentTimeMillis() - startTime
                )
            }

            delay(RESPONSE_TIMEOUT)
            val response = receiveAtResponse() ?: ""
            val executionTime = System.currentTimeMillis() - startTime

            isScanning = response.contains("OK", ignoreCase = true)

            AtCommandResult(
                success = isScanning,
                response = response,
                executionTime = executionTime
            )
        }
    }

    /**
     * Stop BLE Scan
     */
    suspend fun stopScan(): AtCommandResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val command = "AT+STOPSCAN"

            val ret = sendAtCommand(command)
            if (ret != 0) {
                return@withContext AtCommandResult(
                    success = false,
                    response = "",
                    errorMessage = "Failed to send command, code: $ret",
                    executionTime = System.currentTimeMillis() - startTime
                )
            }

            delay(RESPONSE_TIMEOUT)
            val response = receiveAtResponse() ?: ""
            val executionTime = System.currentTimeMillis() - startTime

            isScanning = false

            AtCommandResult(
                success = response.contains("OK", ignoreCase = true),
                response = response,
                executionTime = executionTime
            )
        }
    }

    /**
     * Connect to BLE Device
     */
    suspend fun connect(macAddress: String): AtCommandResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val command = "AT+CONNECT=$macAddress"

            val ret = sendAtCommand(command)
            if (ret != 0) {
                return@withContext AtCommandResult(
                    success = false,
                    response = "",
                    errorMessage = "Failed to send command, code: $ret",
                    executionTime = System.currentTimeMillis() - startTime
                )
            }

            delay(RESPONSE_TIMEOUT)
            val response = receiveAtResponse() ?: ""
            val executionTime = System.currentTimeMillis() - startTime

            AtCommandResult(
                success = response.contains("CONNECTED", ignoreCase = true),
                response = response,
                executionTime = executionTime
            )
        }
    }

    /**
     * Send Data to Connected Device
     */
    suspend fun sendData(handle: Int, hexData: String): AtCommandResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val command = "AT+SEND=$handle,$hexData"

            val ret = sendAtCommand(command)
            if (ret != 0) {
                return@withContext AtCommandResult(
                    success = false,
                    response = "",
                    errorMessage = "Failed to send command, code: $ret",
                    executionTime = System.currentTimeMillis() - startTime
                )
            }

            delay(RESPONSE_TIMEOUT)
            val response = receiveAtResponse() ?: ""
            val executionTime = System.currentTimeMillis() - startTime

            AtCommandResult(
                success = response.contains("OK", ignoreCase = true),
                response = response,
                executionTime = executionTime
            )
        }
    }
}
