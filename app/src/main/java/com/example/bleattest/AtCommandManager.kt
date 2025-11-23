package com.example.bleattest

import android.util.Log
import com.example.bleattest.models.AtCommandResult
import com.example.bleattest.models.ScanParams
import kotlinx.coroutines.*
import vpos.apipackage.At

class AtCommandManager {
    companion object {
        private const val TAG = "AtCommandManager"
        private const val BUFFER_SIZE = 4096
        private const val RESPONSE_TIMEOUT = 200L // ms
    }

    var isScanning: Boolean = false
        private set

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
        val ret = At.Lib_ComSend(bytes, bytes.size)

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
        val ret = At.Lib_ComRecvAT(buffer, lengthArray, BUFFER_SIZE, timeout)

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
     * Enable/Disable Master 모드
     */
    suspend fun enableMaster(enable: Boolean): AtCommandResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val command = "AT+ENABLEMASTER=${if (enable) 1 else 0}"

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
