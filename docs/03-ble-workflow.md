# AT 명령어 및 BLE 워크플로우

## AT 명령어 동작 개요

AT_Terminal의 EFR32BG22 BLE 제어는 크게 **AT 명령어 전송**과 **응답 수신**으로 구성됩니다.

## 1. 마스터 모드 활성화 워크플로우

### 1.1 Enable Master 시퀀스

EFR32BG22 모듈을 Master 모드로 전환하는 과정입니다.

```
사용자 → InputDialog → MainActivity → AtCommandManager → SerialPortManager → EFR32BG22
```

**AT 명령 시퀀스**:
```
Step 0: CTS 제어 (Beacon 모드 종료)
Step 1: +++ (AT 모드 진입, Guard Time 필요)
Step 2: AT+OBSERVER=0 (Master 모드) 또는 AT+OBSERVER=1 (Observer 모드)
Step 3: AT+EXIT (AT 모드 종료)
Step 4: +++ (AT 모드 재진입)
```

**코드 플로우**:
```kotlin
// MainActivity.kt
override fun onEnableMaster(enable: Boolean) {
    lifecycleScope.launch {
        val result = atCommandManager.enableMaster(enable)
        if (result.success) {
            addLogToTerminal("Master mode enabled", LogType.INFO)

            // 백그라운드 수신 시작
            if (enable && !atCommandManager.isReceiving()) {
                atCommandManager.startReceiving()
            }
        }
    }
}

// AtCommandManager.kt
suspend fun enableMaster(enable: Boolean): AtCommandResult {
    // Step 0: CTS 제어
    val ctsRet = ctsControl()
    delay(100)

    // Step 1: +++ 전송 (Guard Time)
    sendAtCommand("+++")
    delay(100)

    // Step 2: OBSERVER 설정
    val observerCommand = if (enable) "AT+OBSERVER=0" else "AT+OBSERVER=1"
    sendAtCommand(observerCommand)
    delay(200)
    val response = receiveAtResponse()

    // Step 3: AT+EXIT
    sendAtCommand("AT+EXIT")
    delay(200)

    // Step 4: +++ 재진입
    sendAtCommand("+++")
    delay(100)

    return AtCommandResult(success = true, response = "Master mode ${if (enable) "enabled" else "disabled"}")
}
```

**타이밍 다이어그램**:
```
[APP]    +++       →  [BLE]
         ├─ 100ms delay
[APP]    AT+OBSERVER=0 →  [BLE]
         ├─ 200ms delay
[BLE]    ← OK       [APP]
[APP]    AT+EXIT    →  [BLE]
         ├─ 200ms delay
[BLE]    ← OK       [APP]
[APP]    +++       →  [BLE]
         ├─ 100ms delay
```

## 2. BLE 스캔 워크플로우

### 2.1 스캔 시작

```kotlin
// MainActivity.kt
override fun onStartScan(params: ScanParams) {
    lifecycleScope.launch {
        // AT+STARTNEWSCAN=<MAC>,<Name>,<RSSI>,<MfgID>,<Data>
        val result = atCommandManager.startScan(params)

        if (result.success) {
            isScanning = true
            updateScanButton(true)
        }
    }
}
```

**스캔 파라미터**:
```kotlin
data class ScanParams(
    val macAddress: String = "",      // 필터링할 MAC (선택)
    val broadcastName: String = "",   // 필터링할 이름 (선택)
    val minRssi: Int = -100,          // 최소 RSSI (선택)
    val manufacturerId: String = "",  // 제조사 ID (선택)
    val data: String = ""             // 필터링할 데이터 (선택)
)

fun toAtCommand(): String {
    val rssiValue = if (minRssi < 0) -minRssi else minRssi
    return "AT+STARTNEWSCAN=$macAddress,$broadcastName,$rssiValue,$manufacturerId,$data"
}
```

**예제 AT 명령**:
```
AT+STARTNEWSCAN=,,80,,           # 모든 디바이스, RSSI >= -80
AT+STARTNEWSCAN=AA:BB:CC:DD:EE:FF,,,, # 특정 MAC만
AT+STARTNEWSCAN=,MyDevice,,,     # 이름으로 필터링
```

### 2.2 스캔 결과 수신

백그라운드에서 지속적으로 스캔 결과를 수신합니다:

```kotlin
// AtCommandManager.kt
fun startReceiving() {
    receiveJob = CoroutineScope(Dispatchers.IO).launch {
        while (isActive && consecutiveErrors < maxConsecutiveErrors) {
            val response = receiveAtResponse()
            if (response != null && response.isNotEmpty()) {
                consecutiveErrors = 0
                withContext(Dispatchers.Main) {
                    listener?.onResponse(response)
                }
                delay(100)
            } else {
                delay(500)
            }
        }
    }
}
```

**스캔 결과 예시**:
```
SCAN:MAC=AA:BB:CC:DD:EE:FF,NAME=Device1,RSSI=-45,TXPW=4
SCAN:MAC=11:22:33:44:55:66,NAME=Device2,RSSI=-60,TXPW=0
```

**UI 업데이트**:
```kotlin
// MainActivity.kt
override fun onResponse(response: String) {
    val logType = when {
        response.startsWith("SCAN:", ignoreCase = true) -> LogType.SCAN
        else -> LogType.RECEIVE
    }
    addLogToTerminal(response, logType)
}
```

### 2.3 스캔 중지

```kotlin
// MainActivity.kt
private fun executeStopScan() {
    lifecycleScope.launch {
        val result = atCommandManager.stopScan()
        isScanning = false
        updateScanButton(false)
    }
}

// AtCommandManager.kt
suspend fun stopScan(): AtCommandResult {
    val command = "AT+STOPSCAN"
    sendAtCommand(command)
    delay(200)
    val response = receiveAtResponse()

    isScanning = false
    return AtCommandResult(
        success = response.contains("OK", ignoreCase = true),
        response = response
    )
}
```

## 3. 디바이스 연결 워크플로우

### 3.1 Connect 명령

```kotlin
// MainActivity.kt
override fun onConnect(macAddress: String) {
    lifecycleScope.launch {
        addLogToTerminal("AT+CONNECT=$macAddress", LogType.SEND)
        val result = atCommandManager.connect(macAddress)

        if (!result.success) {
            addLogToTerminal("Connect failed", LogType.ERROR)
        }
    }
}

// AtCommandManager.kt
suspend fun connect(macAddress: String): AtCommandResult {
    val command = "AT+CONNECT=$macAddress"
    sendAtCommand(command)
    delay(200)
    val response = receiveAtResponse()

    return AtCommandResult(
        success = response.contains("CONNECTED", ignoreCase = true),
        response = response
    )
}
```

**연결 응답 예시**:
```
CONNECTED:MAC=AA:BB:CC:DD:EE:FF,HANDLE=1
```

### 3.2 데이터 전송

```kotlin
// MainActivity.kt
override fun onSendData(handle: Int, hexData: String) {
    lifecycleScope.launch {
        addLogToTerminal("AT+SEND=$handle,$hexData", LogType.SEND)
        val result = atCommandManager.sendData(handle, hexData)

        if (!result.success) {
            addLogToTerminal("Send failed", LogType.ERROR)
        }
    }
}

// AtCommandManager.kt
suspend fun sendData(handle: Int, hexData: String): AtCommandResult {
    val command = "AT+SEND=$handle,$hexData"
    sendAtCommand(command)
    delay(200)
    val response = receiveAtResponse()

    return AtCommandResult(
        success = response.contains("OK", ignoreCase = true),
        response = response
    )
}
```

**데이터 전송 예시**:
```
AT+SEND=1,48656C6C6F    # "Hello" in hex
OK
```

## 4. 시리얼 통신 상세

### 4.1 데이터 전송 (Send)

```kotlin
// SerialPortManager.kt
fun send(data: ByteArray, length: Int): Int {
    if (!isOpen) return -1

    return try {
        outputStream?.write(data, 0, length)
        outputStream?.flush()
        Log.d(TAG, "Sent $length bytes: ${String(data, 0, length)}")
        0
    } catch (e: IOException) {
        Log.e(TAG, "Failed to send data", e)
        -2
    }
}
```

### 4.2 데이터 수신 (Receive)

```kotlin
// SerialPortManager.kt
fun receive(buffer: ByteArray, length: IntArray, maxLength: Int, timeout: Int): Int {
    if (!isOpen) return -1

    return try {
        val startTime = System.currentTimeMillis()
        var totalBytesRead = 0

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
            Thread.sleep(10)
        }

        length[0] = totalBytesRead
        if (totalBytesRead > 0) 0 else -4
    } catch (e: IOException) {
        -2
    }
}
```

### 4.3 CTS 제어

```kotlin
// SerialPortManager.kt
fun ctsControl(): Int {
    if (!isOpen) return -1

    return try {
        val ret = serialPort?.ctsControl() ?: -1
        if (ret == 0) {
            Log.d(TAG, "CTS control (RTS toggle) successful")
        }
        ret
    } catch (e: Exception) {
        Log.e(TAG, "Error in CTS control", e)
        -2
    }
}
```

## 5. 에러 처리 및 복구

### 5.1 연속 에러 감지

```kotlin
// AtCommandManager.kt
private var consecutiveErrors = 0
private val maxConsecutiveErrors = 3

fun startReceiving() {
    receiveJob = CoroutineScope(Dispatchers.IO).launch {
        while (isActive && consecutiveErrors < maxConsecutiveErrors) {
            try {
                val response = receiveAtResponse()
                if (response != null) {
                    consecutiveErrors = 0 // 성공 시 리셋
                } else {
                    delay(500)
                }
            } catch (e: Exception) {
                consecutiveErrors++
                if (consecutiveErrors >= maxConsecutiveErrors) {
                    withContext(Dispatchers.Main) {
                        listener?.onError("Hardware connection failed")
                    }
                    break
                }
                delay(1000)
            }
        }
    }
}
```

### 5.2 타임아웃 처리

```kotlin
// AtCommandManager.kt
fun receiveAtResponse(): String? {
    val buffer = ByteArray(BUFFER_SIZE)
    val lengthArray = intArrayOf(0)
    val timeout = 1000 // 1초 타임아웃

    val ret = serialPortManager.receive(buffer, lengthArray, BUFFER_SIZE, timeout)

    return if (ret == 0 && lengthArray[0] > 0) {
        String(buffer, 0, lengthArray[0]).trim()
    } else {
        if (ret < 0 && consecutiveErrors == 0) {
            Log.e(TAG, "Failed to receive, ret=$ret")
        }
        null
    }
}
```

## 6. 타이밍 고려사항

### Guard Time (+++  명령)
- `+++` 명령 전후 최소 100ms 대기 필요
- AT 모드 진입을 위한 필수 타이밍

### 응답 대기 시간
- 일반 AT 명령: 200ms
- CTS 제어: 100ms
- 데이터 수신 타임아웃: 1000ms

### 백그라운드 수신 주기
- 응답 있을 때: 100ms 간격
- 응답 없을 때: 500ms 간격
