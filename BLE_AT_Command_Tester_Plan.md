# BLE AT Command Tester 앱 개발 계획서

## 프로젝트 개요
- **목적**: EFR32BG22 BLE 모듈의 AT Command 테스트를 위한 에뮬레이터 앱
- **기반**: github.com/mcandle-dev/vpos_scanner (참조용, 새 레포 생성)
- **개발 언어**: Kotlin
- **Native 라이브러리**: libVpos3893_release_20250930.aar
- **핵심 제약**: `At.Lib_ComSend()`, `At.Lib_ComRecvAT()` 만 사용

---

## 1. 프로젝트 구조

```
ble_at_command_tester/
├── app/
│   ├── libs/
│   │   └── libVpos3893_release_20250930.aar    # Native 라이브러리
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/example/bleattest/
│   │   │   │   ├── MainActivity.kt              # 메인 화면
│   │   │   │   ├── AtCommandManager.kt          # AT 명령 송수신 관리
│   │   │   │   ├── TerminalAdapter.kt           # 터미널 로그 RecyclerView 어댑터
│   │   │   │   ├── InputDialogFragment.kt       # 파라미터 입력 팝업
│   │   │   │   └── models/
│   │   │   │       ├── TerminalLog.kt           # 터미널 로그 데이터 모델
│   │   │   │       └── AtCommandResult.kt       # AT 명령 결과 모델
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   ├── activity_main.xml          # 메인 레이아웃
│   │   │   │   │   ├── item_terminal_log.xml      # 터미널 로그 아이템
│   │   │   │   │   └── dialog_input.xml           # 입력 다이얼로그
│   │   │   │   └── values/
│   │   │   │       ├── strings.xml
│   │   │   │       ├── colors.xml
│   │   │   │       └── styles.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── gradle/
└── settings.gradle
```

---

## 2. UI 설계

### 2.1 메인 화면 레이아웃 (Serial USB Terminal 스타일)

```
┌─────────────────────────────────────┐
│  BLE AT Command Tester       [≡]   │ <- Toolbar
├─────────────────────────────────────┤
│                                     │
│  ┌──────────────────────────────┐  │
│  │ Terminal Output Area         │  │
│  │ ───────────────────────────  │  │
│  │ > AT\r\n                     │  │
│  │ < OK                         │  │
│  │ > AT+GETMAC\r\n              │  │
│  │ < MAC: AA:BB:CC:DD:EE:FF     │  │
│  │ < OK                         │  │
│  │ > AT+STARTNEWSCAN=...\r\n    │  │
│  │ < SCAN: AA:BB:CC:DD:EE:FF,   │  │
│  │   -65, MyDevice, 02010611... │  │
│  │ < SCAN: 11:22:33:44:55:66,   │  │
│  │   -72, Unknown, 0201061AFF.. │  │
│  │                              │  │
│  │ [RecyclerView - 스크롤 가능] │  │
│  │                              │  │
│  └──────────────────────────────┘  │
│                                     │
├─────────────────────────────────────┤
│  Command Buttons:                   │
│  ┌──────────┐ ┌──────────┐         │
│  │ Enable   │ │ Get MAC  │         │
│  │ Master   │ │          │         │
│  └──────────┘ └──────────┘         │
│  ┌──────────┐ ┌──────────┐         │
│  │  Scan    │ │ Connect  │         │
│  │          │ │          │         │
│  └──────────┘ └──────────┘         │
│  ┌─────────────────────────┐       │
│  │       Send Data         │       │
│  └─────────────────────────┘       │
│                                     │
│  ┌─────────────────────────┐       │
│  │      Clear Terminal     │       │
│  └─────────────────────────┘       │
└─────────────────────────────────────┘
```

### 2.2 터미널 로그 아이템 디자인

```xml
각 로그 항목:
- 타임스탬프: [HH:mm:ss.SSS]
- 방향 표시: > (송신), < (수신)
- 명령/응답 내용
- 색상 구분: 
  * 송신(파란색)
  * 수신(초록색)
  * 스캔 결과(노란색) - SCAN: 으로 시작하는 라인
  * 에러(빨간색)

예시:
[14:30:25.123] > AT+STARTNEWSCAN=,,-80,,\r\n
[14:30:25.345] < OK
[14:30:26.100] < SCAN: AA:BB:CC:DD:EE:FF, -65, MyDevice, 02010611FF...
[14:30:26.200] < SCAN: 11:22:33:44:55:66, -72, Unknown, 0201061AFF...
[14:30:26.300] < SCAN: 22:33:44:55:66:77, -80, Sensor01, 02010615FF...
```

---

## 3. 기능 명세

### 3.1 Enable Master 기능
```java
버튼 클릭 → 팝업 표시
┌──────────────────────────┐
│  Enable Master           │
│  ─────────────────────   │
│  ○ Enable (1)            │
│  ○ Disable (0)           │
│                          │
│  [Cancel]  [Execute]     │
└──────────────────────────┘

실행 시:
1. AT+ENABLEMASTER=<0|1>\r\n 전송
2. 응답 수신 및 터미널 출력
```

### 3.2 Get MAC 기능
```java
버튼 클릭 → 직접 실행 (파라미터 없음)

실행 시:
1. AT+GETMAC\r\n 전송
2. 응답 수신 (MAC: XX:XX:XX:XX:XX:XX)
3. 터미널 출력
```

### 3.3 Scan 기능
```kotlin
버튼 클릭 → 스캔 파라미터 입력 팝업 표시
┌──────────────────────────┐
│  Start BLE Scan          │
│  ─────────────────────   │
│  MAC Address (필터):     │
│  [___________________]   │
│                          │
│  Broadcast Name (필터):  │
│  [___________________]   │
│                          │
│  Min RSSI: [-___]        │
│                          │
│  Manufacturer ID (필터): │
│  [___________________]   │
│                          │
│  Data (필터):            │
│  [___________________]   │
│                          │
│  [Cancel]  [Start Scan]  │
└──────────────────────────┘

실행 시:
1. AT+STARTNEWSCAN=<params>\r\n 전송
2. 터미널에 전송 로그 출력: "> AT+STARTNEWSCAN=...\r\n"
3. 스캔 결과 실시간 수신 및 터미널 출력:
   "< SCAN: AA:BB:CC:DD:EE:FF, -65, MyDevice, 02010611FF..."
   "< SCAN: 11:22:33:44:55:66, -72, Unknown, 0201061AFF..."
   (계속 실시간으로 터미널에 추가)
4. 스캔 중지: "Scan" 버튼 다시 클릭 → AT+STOPSCAN\r\n 전송

스캔 중 "Scan" 버튼 상태:
- 스캔 시작 전: "Scan"
- 스캔 중: "Stop Scan" (빨간색)
- 스캔 중지 후: 다시 "Scan"
```

### 3.4 Connect 기능
```java
버튼 클릭 → 팝업 표시
┌──────────────────────────┐
│  Connect to Device       │
│  ─────────────────────   │
│  MAC Address:            │
│  [___________________]   │
│                          │
│  [Cancel]  [Connect]     │
└──────────────────────────┘

실행 시:
1. AT+CONNECT=<MAC>\r\n 전송
2. 연결 결과 수신 및 출력
```

### 3.5 Send Data 기능
```java
버튼 클릭 → 팝업 표시
┌──────────────────────────┐
│  Send Data               │
│  ─────────────────────   │
│  Connection Handle:      │
│  [___]                   │
│                          │
│  Data (Hex):             │
│  [___________________]   │
│                          │
│  [Cancel]  [Send]        │
└──────────────────────────┘

실행 시:
1. AT+SEND=<handle>,<data>\r\n 전송
2. 전송 결과 수신 및 출력
```

---

## 4. 핵심 클래스 설계

### 4.1 AtCommandManager.kt
```kotlin
목적: AT 명령 송수신 통합 관리
주요 메서드:
- fun sendAtCommand(command: String): Int
  └─ At.Lib_ComSend() 사용
  └─ 명령어에 자동으로 \r\n 추가 (단, "+++" 제외)
  
- fun receiveAtResponse(): String?
  └─ At.Lib_ComRecvAT() 사용
  └─ 백그라운드 코루틴에서 지속적 수신
  
- fun enableMaster(enable: Boolean): AtCommandResult
- fun getMacAddress(): AtCommandResult
- fun startScan(params: ScanParams): AtCommandResult
- fun stopScan(): AtCommandResult
- fun connect(macAddress: String): AtCommandResult
- fun sendData(handle: Int, hexData: String): AtCommandResult

상태 관리:
- var isScanning: Boolean = false

콜백 인터페이스:
- interface OnAtResponseListener {
    fun onResponse(response: String)
    fun onError(error: String)
  }
```

### 4.2 MainActivity.kt
```kotlin
목적: UI 이벤트 처리 및 화면 제어
주요 컴포넌트:
- RecyclerView: 터미널 로그 표시
- Button: 각 기능별 버튼
- AtCommandManager: AT 명령 관리 객체

주요 메서드:
- private fun initializeViews()
- private fun setupRecyclerView()
- private fun setupButtonListeners()
- private fun addLogToTerminal(log: String, type: LogType)
- private fun showInputDialog(commandType: CommandType)
- private fun executeAtCommand(commandType: CommandType, params: Bundle?)
- private fun updateScanButton(isScanning: Boolean)

상태 관리:
- private var isScanning = false
```

### 4.3 TerminalAdapter.kt
```kotlin
목적: 터미널 로그 표시 RecyclerView 어댑터
기능:
- 송신/수신 로그 색상 구분
- 스캔 결과 로그 별도 색상 (노란색)
- 타임스탬프 자동 추가
- 자동 스크롤 (최신 로그로)
- 최대 로그 개수 제한 (1000개)

로그 타입별 색상:
- SEND: 파란색
- RECEIVE: 초록색
- SCAN: 노란색 (SCAN:으로 시작하는 로그)
- ERROR: 빨간색
- INFO: 회색
```

### 4.4 InputDialogFragment.kt
```kotlin
목적: 범용 입력 다이얼로그
기능:
- CommandType에 따라 동적으로 입력 필드 생성
- 입력 검증 (MAC 주소 형식, Hex 데이터 등)
- 이전 입력값 저장 (SharedPreferences)
```

---

## 5. AT 명령 송수신 시퀀스

### 5.1 명령 전송 흐름 (Kotlin Coroutine 사용)
```
User Action (버튼 클릭)
    ↓
InputDialogFragment (파라미터 입력)
    ↓
MainActivity.executeAtCommand()
    ↓
CoroutineScope(Dispatchers.IO).launch {
    AtCommandManager.sendAtCommand()
    ↓
    withContext(Dispatchers.Main) {
        [로그 추가] "> AT+COMMAND\r\n"
    }
    ↓
    At.Lib_ComSend(command.toByteArray(), length)
    ↓
    delay(200) // 응답 대기
}
```

### 5.2 응답 수신 흐름 (Kotlin Coroutine 사용)
```
GlobalScope.launch(Dispatchers.IO) {
    while (isReceiving) {
        val response = AtCommandManager.receiveAtResponse()
        ↓
        At.Lib_ComRecvAT(buffer, bufferSize)
        ↓
        if (ret > 0) {
            val response = String(buffer, 0, ret)
            ↓
            withContext(Dispatchers.Main) {
                onAtResponseListener?.onResponse(response)
                ↓
                // 스캔 결과인지 확인
                val logType = when {
                    response.startsWith("SCAN:") -> LogType.SCAN
                    else -> LogType.RECEIVE
                }
                ↓
                MainActivity.addLogToTerminal(response, logType)
                ↓
                [로그 추가] "< RESPONSE"
            }
        }
        ↓
        delay(100) // 다음 수신 대기
    }
}
```

### 5.3 스캔 실행 및 결과 수신 흐름 (단순화)
```
User: Scan 버튼 클릭
    ↓
isScanning == false 인 경우:
    ↓
    InputDialogFragment (스캔 파라미터 입력)
    ↓
    "Start Scan" 클릭
    ↓
    AT+STARTNEWSCAN=<params>\r\n 전송
    ↓
    터미널 출력: "> AT+STARTNEWSCAN=...\r\n"
    ↓
    isScanning = true
    버튼 텍스트 변경: "Stop Scan" (빨간색)
    ↓
    백그라운드 코루틴에서 지속 수신:
        ↓
        At.Lib_ComRecvAT() 호출
        ↓
        응답 수신: "SCAN: AA:BB:CC:DD:EE:FF, -65, MyDevice, 02010611FF..."
        ↓
        터미널 출력 (노란색):
        "< SCAN: AA:BB:CC:DD:EE:FF, -65, MyDevice, 02010611FF..."
        ↓
        계속 수신 반복...

isScanning == true 인 경우:
    ↓
    AT+STOPSCAN\r\n 전송
    ↓
    터미널 출력: "> AT+STOPSCAN\r\n"
    ↓
    응답 수신: "< OK"
    ↓
    isScanning = false
    버튼 텍스트 복원: "Scan"
```

---

## 6. 데이터 모델

### 6.1 TerminalLog.kt
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

### 6.2 AtCommandResult.kt
```kotlin
data class AtCommandResult(
    val success: Boolean,
    val response: String,
    val errorMessage: String? = null,
    val executionTime: Long = 0 // ms
)
```

### 6.3 ScanParams.kt
```kotlin
data class ScanParams(
    val macAddress: String = "",      // 필터링할 MAC (선택)
    val broadcastName: String = "",   // 필터링할 이름 (선택)
    val minRssi: Int = -100,          // 최소 RSSI (선택)
    val manufacturerId: String = "",  // 제조사 ID (선택)
    val data: String = ""             // 필터링할 데이터 (선택)
) {
    fun toAtCommand(): String {
        return "AT+STARTNEWSCAN=$macAddress,$broadcastName,${-minRssi},$manufacturerId,$data\r\n"
    }
}
```

---

## 7. AT 명령어 포맷 정리

### 7.1 기본 규칙
```
1. 명령어 시작: "AT" 또는 "AT+"
2. 명령어 종료: "\r\n" (CRLF)
3. 예외: "+++" 명령은 CRLF 없이 전송
4. 응답 대기: 명령 전송 후 200ms 대기
```

### 7.2 주요 명령어
```
1. Enable Master:
   송신: AT+ENABLEMASTER=1\r\n
   응답: OK\r\n

2. Get MAC:
   송신: AT+GETMAC\r\n
   응답: MAC: AA:BB:CC:DD:EE:FF\r\n
        OK\r\n

3. Start Scan:
   송신: AT+STARTNEWSCAN=<MAC>,<Name>,<RSSI>,<MfgID>,<Data>\r\n
   응답: (지속적 스캔 결과)
        SCAN: MAC, RSSI, Name, Data
        ...

4. Stop Scan:
   송신: AT+STOPSCAN\r\n
   응답: OK\r\n

5. Connect:
   송신: AT+CONNECT=AA:BB:CC:DD:EE:FF\r\n
   응답: CONNECTED: Handle=1\r\n
        OK\r\n

6. Send Data:
   송신: AT+SEND=1,48656C6C6F\r\n  (Hello in hex)
   응답: OK\r\n
```

---

## 8. 에러 처리

### 8.1 송신 에러
```java
if (ret != 0) {
    Log.e(TAG, "Failed to send AT command: " + ret);
    addLogToTerminal("Error: Send failed (code: " + ret + ")", LogType.ERROR);
    return;
}
```

### 8.2 수신 타임아웃
```java
int retryCount = 0;
while (retryCount < 5) {
    ret = At.Lib_ComRecvAT(buffer, bufferSize);
    if (ret > 0) break;
    Thread.sleep(100);
    retryCount++;
}

if (ret <= 0) {
    addLogToTerminal("Error: No response", LogType.ERROR);
}
```

### 8.3 포맷 에러
```java
// MAC 주소 검증
if (!macAddress.matches("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")) {
    showToast("Invalid MAC address format");
    return;
}

// Hex 데이터 검증
if (!hexData.matches("^[0-9A-Fa-f]+$")) {
    showToast("Invalid hex data format");
    return;
}
```

---

## 9. 권한 및 설정

### 9.1 AndroidManifest.xml
```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

### 9.2 build.gradle (app)
```gradle
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    compileSdkVersion 33
    
    defaultConfig {
        applicationId "com.example.bleattest"
        minSdkVersion 24
        targetSdkVersion 33
        versionCode 1
        versionName "1.0"
    }
    
    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    
    sourceSets {
        main {
            jniLibs.srcDirs = ['libs']  // Native 라이브러리
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
    
    kotlinOptions {
        jvmTarget = '1.8'
    }
    
    buildFeatures {
        viewBinding true
    }
}

repositories {
    flatDir {
        dirs 'libs'  // AAR 파일 로드
    }
}

dependencies {
    // Native 라이브러리
    implementation(name: 'libVpos3893_release_20250930', ext: 'aar')
    
    // Kotlin
    implementation 'org.jetbrains.kotlin:kotlin-stdlib:1.9.0'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    
    // AndroidX
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.6.2'
    
    // Material Design
    implementation 'com.google.android.material:material:1.11.0'
}
```

### 9.3 build.gradle (project)
```gradle
buildscript {
    ext.kotlin_version = '1.9.0'
    
    repositories {
        google()
        mavenCentral()
    }
    
    dependencies {
        classpath 'com.android.tools.build:gradle:8.1.0'
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
```

---

## 10. 개발 순서

### Phase 1: 기본 구조 (1-2일)
1. ✅ 프로젝트 생성 및 기본 레이아웃
2. ✅ AtCommandManager 클래스 구현
3. ✅ 터미널 로그 RecyclerView 구현
4. ✅ 기본 송수신 테스트 (AT 명령)

### Phase 2: 기능 구현 (2-3일)
1. ✅ Enable Master 기능
2. ✅ Get MAC 기능
3. ✅ Scan 기능
4. ✅ Connect 기능
5. ✅ Send Data 기능

### Phase 3: UI/UX 개선 (1-2일)
1. ✅ InputDialogFragment 구현
2. ✅ 터미널 로그 색상/스타일 적용
3. ✅ 자동 스크롤 및 Clear 기능
4. ✅ 이전 입력값 저장/불러오기

### Phase 4: 테스트 및 최적화 (1-2일)
1. ✅ 실제 EFR32BG22 모듈 테스트
2. ✅ 에러 케이스 처리
3. ✅ 성능 최적화 (메모리 누수 체크)
4. ✅ 사용자 피드백 반영

---

## 11. 참고 사항

### 11.1 vpos_scanner 레포에서 재사용할 코드
- Native 라이브러리 로딩 부분
- At.Lib_* 함수 선언부
- SharedPreferences 관련 유틸리티
- 로깅 유틸리티

### 11.2 새로 작성할 코드
- 터미널 UI 전체
- InputDialogFragment
- AtCommandManager의 송수신 로직
- TerminalAdapter

### 11.3 주의사항
```
⚠️ "+++" 명령은 CRLF 없이 전송
⚠️ 다른 모든 AT 명령은 "\r\n" 필수
⚠️ At.Lib_ComSend()와 At.Lib_ComRecvAT()만 사용
⚠️ 백그라운드 쓰레드에서 수신 처리
⚠️ UI 업데이트는 runOnUiThread() 사용
```

---

## 12. 예상 화면 플로우

```
앱 실행
  ↓
MainActivity 표시 (터미널 + 버튼들)
  ↓
"Get MAC" 버튼 클릭
  ↓
터미널 출력: "> AT+GETMAC\r\n"
  ↓
터미널 출력: "< MAC: AA:BB:CC:DD:EE:FF"
터미널 출력: "< OK"
  ↓
"Scan" 버튼 클릭 (isScanning == false)
  ↓
InputDialogFragment 표시 (스캔 파라미터 입력)
  ↓
"Start Scan" 클릭
  ↓
터미널 출력: "> AT+STARTNEWSCAN=...\r\n"
버튼 텍스트 변경: "Stop Scan" (빨간색)
  ↓
터미널 출력 (노란색): "< SCAN: AA:BB:CC:DD:EE:FF, -65, MyDevice..."
터미널 출력 (노란색): "< SCAN: 11:22:33:44:55:66, -72, Unknown..."
(지속적으로 수신)
  ↓
"Stop Scan" 버튼 클릭 (isScanning == true)
  ↓
터미널 출력: "> AT+STOPSCAN\r\n"
터미널 출력: "< OK"
버튼 텍스트 복원: "Scan"
  ↓
"Connect" 버튼 클릭
  ↓
InputDialogFragment 표시 (MAC 주소 입력)
  ↓
"Connect" 클릭
  ↓
터미널 출력: "> AT+CONNECT=AA:BB:CC:DD:EE:FF\r\n"
  ↓
터미널 출력: "< CONNECTED: Handle=1"
터미널 출력: "< OK"
```

---

## 완료! 🎉

이 계획서를 기반으로 단계별로 구현하시면 됩니다.
각 Phase별로 테스트를 진행하면서 안정적으로 개발할 수 있습니다.
