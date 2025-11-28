# 컴포넌트 상세 분석

## 1. MainActivity.kt

### 클래스 개요
**위치**: `app/src/main/java/com/example/bleattest/MainActivity.kt`
**역할**: 터미널 인터페이스 및 AT 명령 제어
**언어**: Kotlin

### 주요 속성

```kotlin
class MainActivity : AppCompatActivity(), InputDialogFragment.OnInputListener {
    // UI 컴포넌트
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnEnableMaster: Button
    private lateinit var btnGetMac: Button
    private lateinit var btnScan: Button
    private lateinit var btnConnect: Button
    private lateinit var btnSendData: Button
    private lateinit var btnClear: Button

    // 핵심 매니저
    private lateinit var atCommandManager: AtCommandManager
    private lateinit var terminalAdapter: TerminalAdapter

    // 상태
    private var isScanning = false
}
```

### 핵심 메서드

#### 초기화
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    initializeViews()
    setupRecyclerView()
    setupAtCommandManager()
    setupButtonListeners()
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
            addLogToTerminal(error, LogType.ERROR)
        }
    })

    // 시리얼 포트 초기화
    val ret = atCommandManager.initSerialPort("/dev/ttyS1", 115200)
}
```

#### AT 명령 실행

**1. 마스터 모드 활성화**
```kotlin
override fun onEnableMaster(enable: Boolean) {
    lifecycleScope.launch {
        addLogToTerminal("AT+ENABLEMASTER=${if (enable) 1 else 0}", LogType.SEND)

        val result = atCommandManager.enableMaster(enable)

        if (result.success) {
            addLogToTerminal("Master mode ${if (enable) "enabled" else "disabled"}", LogType.INFO)

            // 백그라운드 수신 시작
            if (enable && !atCommandManager.isReceiving()) {
                atCommandManager.startReceiving()
            }
        } else {
            addLogToTerminal("Error: ${result.errorMessage}", LogType.ERROR)
        }
    }
}
```

**2. 스캔 시작**
```kotlin
override fun onStartScan(params: ScanParams) {
    lifecycleScope.launch {
        addLogToTerminal(params.toAtCommand(), LogType.SEND)

        val result = atCommandManager.startScan(params)

        if (result.success) {
            isScanning = true
            updateScanButton(true)
        }
    }
}
```

#### UI 업데이트
```kotlin
private fun addLogToTerminal(content: String, type: LogType) {
    runOnUiThread {
        val log = TerminalLog(type = type, content = content)
        terminalAdapter.addLog(log)
        recyclerView.scrollToPosition(terminalAdapter.getLogCount() - 1)
    }
}
```

---

## 2. AtCommandManager.kt

### 클래스 개요
**위치**: `app/src/main/java/com/example/bleattest/AtCommandManager.kt`
**역할**: AT 명령어 관리 및 실행
**언어**: Kotlin

### 주요 속성
```kotlin
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
}
```

### 인터페이스
```kotlin
interface OnAtResponseListener {
    fun onResponse(response: String)
    fun onError(error: String)
}
```

### 핵심 메서드

#### 시리얼 포트 제어
```kotlin
fun initSerialPort(devicePath: String = "/dev/ttyS0", baudrate: Int = 115200): Int {
    return serialPortManager.open(devicePath, baudrate)
}

fun closeSerialPort() {
    serialPortManager.close()
}
```

#### AT 명령 전송
```kotlin
fun sendAtCommand(command: String): Int {
    val finalCommand = if (command == "+++") {
        command
    } else {
        if (command.endsWith("\r\n")) command else "$command\r\n"
    }

    val bytes = finalCommand.toByteArray()
    return serialPortManager.send(bytes, bytes.size)
}
```

#### 응답 수신
```kotlin
fun receiveAtResponse(): String? {
    val buffer = ByteArray(BUFFER_SIZE)
    val lengthArray = intArrayOf(0)
    val timeout = 1000

    val ret = serialPortManager.receive(buffer, lengthArray, BUFFER_SIZE, timeout)

    return if (ret == 0 && lengthArray[0] > 0) {
        String(buffer, 0, lengthArray[0]).trim()
    } else {
        null
    }
}
```

#### 백그라운드 수신
```kotlin
fun startReceiving() {
    receiveJob = CoroutineScope(Dispatchers.IO).launch {
        while (isActive && consecutiveErrors < maxConsecutiveErrors) {
            try {
                val response = receiveAtResponse()
                if (response != null && response.isNotEmpty()) {
                    consecutiveErrors = 0
                    withContext(Dispatchers.Main) {
                        listener?.onResponse(response)
                    }
                    delay(100)
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

#### AT 명령 실행

**Enable Master**
```kotlin
suspend fun enableMaster(enable: Boolean): AtCommandResult {
    return withContext(Dispatchers.IO) {
        // Step 0: CTS 제어
        val ctsRet = ctsControl()
        delay(100)

        // Step 1: +++ (AT 모드 진입)
        sendAtCommand("+++")
        delay(100)

        // Step 2: OBSERVER 설정
        val observerCommand = if (enable) "AT+OBSERVER=0" else "AT+OBSERVER=1"
        sendAtCommand(observerCommand)
        delay(RESPONSE_TIMEOUT)
        val response = receiveAtResponse()

        // Step 3: AT+EXIT
        sendAtCommand("AT+EXIT")
        delay(RESPONSE_TIMEOUT)

        // Step 4: +++ 재진입
        sendAtCommand("+++")
        delay(100)

        AtCommandResult(success = true, response = "Master mode ${if (enable) "enabled" else "disabled"}")
    }
}
```

**Start Scan**
```kotlin
suspend fun startScan(params: ScanParams): AtCommandResult {
    return withContext(Dispatchers.IO) {
        val command = params.toAtCommand()
        sendAtCommand(command)
        delay(RESPONSE_TIMEOUT)
        val response = receiveAtResponse() ?: ""

        isScanning = response.contains("OK", ignoreCase = true)

        AtCommandResult(success = isScanning, response = response)
    }
}
```

---

## 3. SerialPortManager.kt

### 클래스 개요
**위치**: `app/src/main/java/com/example/bleattest/SerialPortManager.kt`
**역할**: UART 시리얼 포트 통신 관리
**언어**: Kotlin

### 주요 속성
```kotlin
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
}
```

### 핵심 메서드

#### 포트 열기
```kotlin
fun open(devicePath: String = DEFAULT_DEVICE, baudrate: Int = DEFAULT_BAUDRATE): Int {
    if (isOpen) return 0

    return try {
        val device = File(devicePath)

        if (!device.exists()) {
            Log.e(TAG, "Device not found: $devicePath")
            return -1
        }

        serialPort = SerialPort(device, baudrate, 0)
        inputStream = serialPort?.inputStream
        outputStream = serialPort?.outputStream

        isOpen = true
        Log.d(TAG, "Serial port opened: $devicePath @ $baudrate baud")
        0
    } catch (e: SecurityException) {
        Log.e(TAG, "Security exception", e)
        -2
    } catch (e: IOException) {
        Log.e(TAG, "IO exception", e)
        -3
    }
}
```

#### 데이터 전송
```kotlin
fun send(data: ByteArray, length: Int): Int {
    if (!isOpen) return -1

    return try {
        outputStream?.write(data, 0, length)
        outputStream?.flush()
        Log.d(TAG, "Sent $length bytes")
        0
    } catch (e: IOException) {
        Log.e(TAG, "Failed to send data", e)
        -2
    }
}
```

#### 데이터 수신
```kotlin
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

#### CTS 제어
```kotlin
fun ctsControl(): Int {
    if (!isOpen) return -1

    return try {
        val ret = serialPort?.ctsControl() ?: -1
        if (ret == 0) {
            Log.d(TAG, "CTS control successful")
        }
        ret
    } catch (e: Exception) {
        Log.e(TAG, "Error in CTS control", e)
        -2
    }
}
```

---

## 4. TerminalAdapter.kt

### 클래스 개요
**위치**: `app/src/main/java/com/example/bleattest/TerminalAdapter.kt`
**역할**: 터미널 로그 RecyclerView 어댑터
**언어**: Kotlin

### 주요 속성
```kotlin
class TerminalAdapter : RecyclerView.Adapter<TerminalAdapter.LogViewHolder>() {
    private val logs = mutableListOf<TerminalLog>()
    private val maxLogCount = 1000

    class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val tvDirection: TextView = view.findViewById(R.id.tvDirection)
        val tvContent: TextView = view.findViewById(R.id.tvContent)
    }
}
```

### 로그 타입별 색상
```kotlin
override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
    val log = logs[position]
    holder.tvTimestamp.text = "[${log.formattedTime}]"

    when (log.type) {
        LogType.SEND -> {
            holder.tvDirection.text = ">"
            holder.tvContent.setTextColor(Color.parseColor("#2196F3")) // 파란색
        }
        LogType.RECEIVE -> {
            holder.tvDirection.text = "<"
            holder.tvContent.setTextColor(Color.parseColor("#4CAF50")) // 초록색
        }
        LogType.SCAN -> {
            holder.tvDirection.text = "<"
            holder.tvContent.setTextColor(Color.parseColor("#FFC107")) // 노란색
        }
        LogType.ERROR -> {
            holder.tvDirection.text = "!"
            holder.tvContent.setTextColor(Color.parseColor("#F44336")) // 빨간색
        }
        LogType.INFO -> {
            holder.tvDirection.text = "ℹ"
            holder.tvContent.setTextColor(Color.parseColor("#9E9E9E")) // 회색
        }
    }

    holder.tvContent.text = log.content
}
```

### 로그 관리
```kotlin
fun addLog(log: TerminalLog) {
    if (logs.size >= maxLogCount) {
        logs.removeAt(0)
        notifyItemRemoved(0)
    }

    logs.add(log)
    notifyItemInserted(logs.size - 1)
}

fun clearLogs() {
    val size = logs.size
    logs.clear()
    notifyItemRangeRemoved(0, size)
}
```

---

## 5. InputDialogFragment.kt

### 클래스 개요
**위치**: `app/src/main/java/com/example/bleattest/InputDialogFragment.kt`
**역할**: AT 명령 파라미터 입력 다이얼로그
**언어**: Kotlin

### 명령 타입
```kotlin
enum class CommandType {
    ENABLE_MASTER,
    SCAN,
    CONNECT,
    SEND_DATA
}
```

### 인터페이스
```kotlin
interface OnInputListener {
    fun onEnableMaster(enable: Boolean)
    fun onStartScan(params: ScanParams)
    fun onConnect(macAddress: String)
    fun onSendData(handle: Int, hexData: String)
}
```

### 다이얼로그 설정
```kotlin
private fun setupScanDialog(view: View, btnExecute: Button, sharedPrefs: SharedPreferences) {
    val etMacAddress = view.findViewById<EditText>(R.id.etMacAddress)
    val etBroadcastName = view.findViewById<EditText>(R.id.etBroadcastName)
    val etMinRssi = view.findViewById<EditText>(R.id.etMinRssi)
    val etManufacturerId = view.findViewById<EditText>(R.id.etManufacturerId)
    val etData = view.findViewById<EditText>(R.id.etData)

    // 이전 값 로드
    etMacAddress.setText(sharedPrefs.getString("scan_mac", ""))
    etBroadcastName.setText(sharedPrefs.getString("scan_name", ""))
    etMinRssi.setText(sharedPrefs.getString("scan_rssi", "-80"))

    btnExecute.setOnClickListener {
        val params = ScanParams(
            macAddress = etMacAddress.text.toString().trim(),
            broadcastName = etBroadcastName.text.toString().trim(),
            minRssi = etMinRssi.text.toString().toIntOrNull() ?: -100
        )

        // 값 저장
        sharedPrefs.edit().apply {
            putString("scan_mac", params.macAddress)
            putString("scan_name", params.broadcastName)
        }.apply()

        listener?.onStartScan(params)
        dismiss()
    }
}
```

---

## 6. 데이터 모델

### TerminalLog
```kotlin
data class TerminalLog(
    val timestamp: Long = System.currentTimeMillis(),
    val type: LogType,
    val content: String,
    val formattedTime: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        .format(Date(timestamp))
)

enum class LogType {
    SEND,      // 송신 (파란색)
    RECEIVE,   // 수신 (초록색)
    SCAN,      // 스캔 결과 (노란색)
    ERROR,     // 에러 (빨간색)
    INFO       // 정보 (회색)
}
```

### AtCommandResult
```kotlin
data class AtCommandResult(
    val success: Boolean,
    val response: String,
    val errorMessage: String? = null,
    val executionTime: Long = 0 // ms
)
```

### ScanParams
```kotlin
data class ScanParams(
    val macAddress: String = "",      // 필터링할 MAC
    val broadcastName: String = "",   // 필터링할 이름
    val minRssi: Int = -100,          // 최소 RSSI
    val manufacturerId: String = "",  // 제조사 ID
    val data: String = ""             // 필터링할 데이터
) {
    fun toAtCommand(): String {
        val rssiValue = if (minRssi < 0) -minRssi else minRssi
        return "AT+STARTNEWSCAN=$macAddress,$broadcastName,$rssiValue,$manufacturerId,$data"
    }
}
```
