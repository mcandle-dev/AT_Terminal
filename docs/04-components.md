# 컴포넌트 상세 분석

## 1. MainActivity.kt

### 클래스 개요
**위치**: `app/src/main/java/com/mcandle/bledemo/MainActivity.kt`  
**역할**: 애플리케이션의 메인 컨트롤러 및 UI 관리자  
**언어**: Kotlin

### 주요 속성

```kotlin
class MainActivity : AppCompatActivity() {
    // UI 컴포넌트
    private lateinit var recyclerView: RecyclerView
    private lateinit var btn1: Button           // Master 버튼
    private lateinit var btn2: Button           // Scan 버튼  
    private lateinit var btn3: Button           // ComRev 버튼
    private lateinit var btnConfig: Button      // Config 버튼
    private lateinit var btnAdvertise: Button   // Advertise 버튼

    // BLE 관련
    private var bleScan = BleScan()             // BLE 스캔 인스턴스
    private var isScanning = false              // 스캔 상태
    private var scanJob: Job? = null            // 코루틴 작업
    
    // 상태 플래그
    private var mStartFlag = false
    private var mEnableFlag = true

    // 데이터
    private val deviceList = mutableListOf<DeviceModel>()
    private lateinit var adapter: BLEDeviceAdapter
}
```

### 핵심 메서드

#### 초기화 메서드
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContentView(R.layout.activity_main)
    
    initViews()
    setupRecyclerView()
    setupClickListeners()
    setupBLEScanCallback()
}

private fun initViews() {
    // UI 컴포넌트 초기화
    recyclerView = findViewById(R.id.recyclerView)
    btn1 = findViewById(R.id.btn1)  // Master
    btn2 = findViewById(R.id.btn2)  // Scan
    btn3 = findViewById(R.id.btn3)  // ComRev
    btnConfig = findViewById(R.id.btnConfig)
    btnAdvertise = findViewById(R.id.btnAdvertise)
}
```

#### BLE 제어 메서드

**1. 마스터 모드 활성화**
```kotlin
private fun Step1() {
    Log.d("BLE_MANAGER", "Step1 - Enable Master Mode")
    val result = bleScan.enableMasterMode(true)
    Log.d("BLE_MANAGER", "Master mode result: $result")
}
```

**2. 단발 스캔**
```kotlin
private fun Step2() {
    Log.d("BLE_MANAGER", "Step2 - Start New Scan")
    val result = bleScan.startNewScan()
    Log.d("BLE_MANAGER", "New scan result: $result")
}
```

**3. 연속 스캔 토글**
```kotlin
private fun toggleScan() {
    if (isScanning) {
        stopScanning()
    } else {
        startContinuousScanning()
    }
}

private fun startContinuousScanning() {
    isScanning = true
    btn3.text = "Stop"
    
    scanJob = lifecycleScope.launch(Dispatchers.IO) {
        bleScan.startContinuousReceiving { jsonData ->
            runOnUiThread {
                processScanResults(jsonData)
            }
        }
    }
}
```

#### 데이터 처리 메서드
```kotlin
private fun processScanResults(jsonArray: JSONArray) {
    for (i in 0 until jsonArray.length()) {
        val jsonObject = jsonArray.getJSONObject(i)
        val device = createDeviceModel(
            deviceName = jsonObject.optString("deviceName"),
            macAddress = jsonObject.getString("macAddress"),
            rssi = jsonObject.getInt("rssi"),
            // ... 기타 속성
        )
        updateDeviceList(device)
    }
}

private fun updateDeviceList(newDevice: DeviceModel) {
    val existingIndex = deviceList.indexOfFirst { 
        it.address == newDevice.address 
    }
    
    if (existingIndex != -1) {
        deviceList[existingIndex] = newDevice  // 업데이트
    } else {
        deviceList.add(newDevice)              // 새 디바이스 추가
    }
    
    adapter.notifyDataSetChanged()
}
```

---

## 2. BleScan.java

### 클래스 개요
**위치**: `app/src/main/java/com/mcandle/bledemo/BleScan.java`  
**역할**: BLE 스캔 핵심 로직 및 벤더 라이브러리 래퍼  
**언어**: Java

### 주요 인터페이스

```java
public interface ScanResultListener {
    void onScanResult(JSONArray scanData);
}

public interface DataReceiveListener {
    void onDataReceived(String buff);
}
```

### 클래스 속성

```java
public class BleScan {
    private boolean isScanning = false;
    private static final String TAG = "BLEScan";
    private boolean isMaster = false;
    private DataReceiveListener dataReceiveListener;
}
```

### 핵심 메서드

#### 마스터 모드 제어
```java
public int enableMasterMode(boolean enable) {
    if (isMaster == enable) {
        Log.d("BLE_MANAGER", "Already in the requested mode. No changes made.");
        return 0; // 동일한 상태면 변경 없음
    } else {
        int ret = At.Lib_EnableMaster(enable);
        if (!enable) {
            isMaster = true; // 상태 업데이트 로직
        } else {
            isMaster = enable;
        }
        Log.d("BLE_MANAGER", "Master mode updated successfully, Result: " + ret);
        return ret;
    }
}
```

#### 스캔 제어
```java
public int startNewScan() {
    Log.d(TAG, "Starting new BLE scan");
    int result = At.Lib_AtStartNewScan();
    Log.d(TAG, "Scan start result: " + result);
    return result;
}

public void startContinuousReceiving(DataReceiveListener listener) {
    this.dataReceiveListener = listener;
    isScanning = true;
    
    // 백그라운드 스레드에서 연속 데이터 수신
    new Thread(() -> {
        while (isScanning) {
            try {
                String data = At.Lib_ComRecvAT();
                if (data != null && !data.isEmpty()) {
                    if (dataReceiveListener != null) {
                        dataReceiveListener.onDataReceived(data);
                    }
                }
                Thread.sleep(100); // 100ms 대기
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }).start();
}
```

#### 데이터 파싱
```java
private JSONArray parseReceivedData(String rawData) {
    try {
        // BLE 패킷 데이터를 JSON 배열로 변환
        JSONArray devices = new JSONArray();
        
        // 파싱 로직 (벤더 라이브러리 데이터 형식에 따라)
        // ... 구체적인 파싱 구현
        
        return devices;
    } catch (JSONException e) {
        Log.e(TAG, "Error parsing BLE data", e);
        return new JSONArray();
    }
}
```

---

## 3. DeviceModel.kt

### 클래스 개요
**위치**: `app/src/main/java/com/mcandle/bledemo/DeviceModel.kt`  
**역할**: BLE 디바이스 정보를 표현하는 데이터 클래스  
**언어**: Kotlin

### 데이터 클래스 정의

```kotlin
data class DeviceModel(
    var name: String = "Unknown",           // 장치 이름
    val address: String = "Unknown",        // MAC 주소
    var rssi: Int = 0,                      // 신호 강도 (RSSI)
    var txPower: Int? = null,               // Tx Power Level
    var serviceUuids: String = "",          // 서비스 UUID
    var serviceData: String = "",           // 서비스 데이터
    var manufacturerData: String = "",      // 제조사 데이터
    var timestampNanos: Long = System.nanoTime() // 스캔 타임스탬프
)
```

### 유틸리티 메서드

```kotlin
/** 제조사 데이터를 HEX 문자열로 반환 */
fun getManufacturerDataHex(): String {
    return manufacturerData // 이미 hex string이라고 가정
}

/** 서비스 데이터를 HEX 문자열로 반환 */
fun getServiceDataHex(): String {
    return serviceData // 이미 hex string이라고 가정
}

/** 바이트 배열을 HEX 문자열로 변환 */
private fun bytesToHex(bytes: ByteArray): String {
    return bytes.joinToString(" ") { String.format("%02X", it) }
}
```

### 팩토리 함수

```kotlin
fun createDeviceModel(
    deviceName: String?,
    macAddress: String,
    rssi: Int,
    txPower: Int?,
    serviceUuid: String,
    serviceData: String,
    manufacturerData: String
): DeviceModel {
    return DeviceModel(
        name = deviceName ?: "Unknown",
        address = macAddress,
        rssi = rssi,
        txPower = txPower,
        serviceUuids = serviceUuid,
        serviceData = serviceData,
        manufacturerData = manufacturerData,
        timestampNanos = System.nanoTime()
    )
}
```

---

## 4. BLEDeviceAdapter.kt

### 클래스 개요
**위치**: `app/src/main/java/com/mcandle/bledemo/BLEDeviceAdapter.kt`  
**역할**: RecyclerView를 위한 BLE 디바이스 목록 어댑터  
**언어**: Kotlin

### 어댑터 구조

```kotlin
class BLEDeviceAdapter(
    private val devices: List<DeviceModel>,
    private val onItemClick: (DeviceModel) -> Unit
) : RecyclerView.Adapter<BLEDeviceAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceName: TextView = itemView.findViewById(R.id.tvDeviceName)
        val deviceAddress: TextView = itemView.findViewById(R.id.tvDeviceAddress)
        val deviceRssi: TextView = itemView.findViewById(R.id.tvDeviceRssi)
        val deviceUuid: TextView = itemView.findViewById(R.id.tvServiceUuid)
        val signalIcon: ImageView = itemView.findViewById(R.id.ivSignalStrength)
    }
```

### 핵심 메서드

```kotlin
override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
    val device = devices[position]
    
    holder.deviceName.text = device.name
    holder.deviceAddress.text = device.address
    holder.deviceRssi.text = "${device.rssi} dBm"
    holder.deviceUuid.text = device.serviceUuids
    
    // RSSI에 따른 신호 강도 아이콘 설정
    updateSignalIcon(holder.signalIcon, device.rssi)
    
    holder.itemView.setOnClickListener {
        onItemClick(device)
    }
}

private fun updateSignalIcon(imageView: ImageView, rssi: Int) {
    val signalLevel = when {
        rssi >= -50 -> R.drawable.ic_signal_strong
        rssi >= -70 -> R.drawable.ic_signal_medium
        rssi >= -90 -> R.drawable.ic_signal_weak
        else -> R.drawable.ic_signal_none
    }
    imageView.setImageResource(signalLevel)
}
```

---

## 5. BLEAdvertiseDialogFragment.kt

### 클래스 개요
**위치**: `app/src/main/java/com/mcandle/bledemo/BLEAdvertiseDialogFragment.kt`  
**역할**: BLE 광고 기능을 위한 다이얼로그 프래그먼트  
**언어**: Kotlin

### 주요 속성

```kotlin
class BLEAdvertiseDialogFragment : DialogFragment() {
    private var countDownTimer: CountDownTimer? = null
    private lateinit var timerText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var restartButton: Button
    
    private var isAdvertising = false
}
```

### 타이머 관리

```kotlin
private fun startAdvertiseTimer() {
    countDownTimer = object : CountDownTimer(10000, 1000) {
        override fun onTick(millisUntilFinished: Long) {
            val seconds = millisUntilFinished / 1000
            timerText.text = "${seconds}초"
            updateUI(isAdvertising = true, secondsLeft = seconds.toInt())
        }

        override fun onFinish() {
            timerText.text = "완료"
            stopAdvertising()
        }
    }.start()
}

private fun stopAdvertising() {
    countDownTimer?.cancel()
    isAdvertising = false
    updateUI(isAdvertising = false)
    
    // MainActivity에 광고 중지 요청
    (activity as? MainActivity)?.stopAdvertise()
}
```

### UI 상태 관리

```kotlin
private fun updateUI(isAdvertising: Boolean, secondsLeft: Int = 0) {
    startButton.isEnabled = !isAdvertising
    stopButton.isEnabled = isAdvertising
    restartButton.isEnabled = !isAdvertising
    
    if (isAdvertising) {
        timerText.text = "${secondsLeft}초"
        timerText.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
    } else {
        timerText.text = "대기 중"
        timerText.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray))
    }
}
```

---

## 6. BLEUtils.kt

### 클래스 개요
**위치**: `app/src/main/java/com/mcandle/bledemo/utils/BLEUtils.kt`  
**역할**: BLE 데이터 변환 및 처리 유틸리티  
**언어**: Kotlin

### 유틸리티 함수

```kotlin
object BLEUtils {
    
    /** ASCII 문자열을 HEX 문자열로 변환 */
    fun asciiToHex(ascii: String): String {
        return ascii.toByteArray().joinToString("") { 
            String.format("%02X", it) 
        }
    }
    
    /** HEX 문자열을 ASCII 문자열로 변환 */
    fun hexToAscii(hex: String): String {
        val cleanHex = hex.replace("\\s".toRegex(), "")
        return cleanHex.chunked(2).map { 
            it.toInt(16).toChar() 
        }.joinToString("")
    }
    
    /** 바이트 배열을 HEX 문자열로 변환 */
    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { 
            String.format("%02X", it) 
        }
    }
    
    /** HEX 문자열을 바이트 배열로 변환 */
    fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace("\\s".toRegex(), "")
        return cleanHex.chunked(2).map { 
            it.toInt(16).toByte() 
        }.toByteArray()
    }
    
    /** RSSI 값을 신호 강도 레벨로 변환 */
    fun rssiToSignalLevel(rssi: Int): Int {
        return when {
            rssi >= -50 -> 4  // 매우 강함
            rssi >= -60 -> 3  // 강함
            rssi >= -70 -> 2  // 보통
            rssi >= -80 -> 1  // 약함
            else -> 0         // 매우 약함
        }
    }
    
    /** MAC 주소 형식 검증 */
    fun isValidMacAddress(mac: String): Boolean {
        val macPattern = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$"
        return mac.matches(macPattern.toRegex())
    }
}
```

---

## 컴포넌트 간 상호작용

### 데이터 플로우 다이어그램

```
벤더 라이브러리 → BleScan → MainActivity → BLEDeviceAdapter → UI
      ↓              ↓           ↓              ↓
  At.Lib_*()    JSON 파싱   DeviceModel    ViewHolder    사용자 표시
```

### 콜백 체인

```
1. BleScan.ScanResultListener → MainActivity.processScanResults()
2. BLEDeviceAdapter.onItemClick → MainActivity.showDeviceDetails()
3. BLEAdvertiseDialogFragment → MainActivity.sendAdvertise()
```

### 생명주기 관리

- **MainActivity**: Activity 생명주기 관리
- **BleScan**: 백그라운드 스레드 및 콜백 관리  
- **BLEAdvertiseDialogFragment**: Dialog 생명주기 관리
- **BLEDeviceAdapter**: RecyclerView 생명주기 연동