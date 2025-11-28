# 시스템 아키텍처

## 전체 아키텍처 개요

AT_Terminal은 레이어드 아키텍처를 기반으로 설계되었으며, JNI 시리얼 통신을 통해 EFR32BG22 BLE 모듈을 제어합니다.

```
┌─────────────────────────────────────────────────────────┐
│                    UI Layer (Kotlin)                   │
├─────────────────────────────────────────────────────────┤
│ MainActivity │ TerminalAdapter │ InputDialogFragment   │
└─────────────────────────────────────────────────────────┘
                             ↕
┌─────────────────────────────────────────────────────────┐
│               Business Layer (Kotlin)                  │
├─────────────────────────────────────────────────────────┤
│   AtCommandManager      │    SerialPortManager          │
│   - AT 명령 처리        │    - 시리얼 포트 제어         │
│   - 응답 파싱          │    - I/O 버퍼 관리            │
└─────────────────────────────────────────────────────────┘
                             ↕
┌─────────────────────────────────────────────────────────┐
│              JNI Layer (Java + C++)                    │
├─────────────────────────────────────────────────────────┤
│   SerialPort.java      │   SerialPort.cpp              │
│   - JNI 인터페이스     │   - termios 설정              │
│                        │   - /dev/ttyS* 제어           │
└─────────────────────────────────────────────────────────┘
                             ↕
┌─────────────────────────────────────────────────────────┐
│              Hardware Layer (Linux Kernel)             │
├─────────────────────────────────────────────────────────┤
│    UART Driver (/dev/ttyS0, /dev/ttyS1, ...)           │
│              ↕                                          │
│         EFR32BG22 BLE Module                            │
└─────────────────────────────────────────────────────────┘
```

## 컴포넌트 간 관계

### 1. UI 계층 (Presentation Layer)

#### MainActivity
- **역할**: 메인 터미널 컨트롤러
- **책임**:
  - 사용자 입력 처리 (버튼 클릭)
  - AT 명령 실행 및 결과 표시
  - 터미널 로그 관리
- **의존성**: AtCommandManager, TerminalAdapter, InputDialogFragment

#### TerminalAdapter
- **역할**: RecyclerView 로그 어댑터
- **책임**: 터미널 로그 목록 표시 및 색상 관리
- **의존성**: TerminalLog 모델

#### InputDialogFragment
- **역할**: 명령 입력 다이얼로그
- **책임**: 사용자로부터 AT 명령 파라미터 입력 받기
- **의존성**: MainActivity (콜백)

### 2. 비즈니스 로직 계층 (Business Layer)

#### AtCommandManager
- **역할**: AT 명령어 관리 및 실행
- **책임**:
  - AT 명령 전송 및 응답 수신
  - 백그라운드 데이터 수신
  - 에러 핸들링
- **주요 인터페이스**:
  ```kotlin
  interface OnAtResponseListener {
      fun onResponse(response: String)
      fun onError(error: String)
  }
  ```

#### SerialPortManager
- **역할**: 시리얼 포트 통신 관리
- **책임**:
  - 시리얼 포트 open/close
  - 데이터 send/receive
  - CTS 제어
- **주요 메서드**:
  ```kotlin
  fun open(devicePath: String, baudrate: Int): Int
  fun close()
  fun send(data: ByteArray, length: Int): Int
  fun receive(buffer: ByteArray, length: IntArray, maxLength: Int, timeout: Int): Int
  fun ctsControl(): Int
  ```

### 3. JNI 계층 (Native Layer)

#### SerialPort (Java)
- **역할**: JNI 브리지
- **책임**:
  - 네이티브 메서드 선언
  - InputStream/OutputStream 제공
- **주요 메서드**:
  ```java
  public SerialPort(File device, int baudrate, int flags)
  public InputStream getInputStream()
  public OutputStream getOutputStream()
  public int ctsControl()
  ```

#### SerialPort (C++)
- **역할**: 네이티브 시리얼 포트 구현
- **책임**:
  - termios 설정
  - 파일 디스크립터 관리
  - 하드웨어 플로우 제어

## 데이터 플로우

### 1. AT 명령 전송 플로우

```
UI → MainActivity → AtCommandManager → SerialPortManager → SerialPort (JNI) → UART → BLE Module
```

**상세 플로우**:
1. **UI**: 버튼 클릭 또는 다이얼로그 입력
2. **MainActivity**: suspend 함수로 AT 명령 실행
3. **AtCommandManager**: AT 명령어 포맷팅 및 전송
4. **SerialPortManager**: ByteArray로 데이터 전송
5. **SerialPort (JNI)**: OutputStream.write() 호출
6. **네이티브 코드**: write() 시스템 콜
7. **UART 드라이버**: 시리얼 포트로 데이터 전송
8. **BLE Module**: AT 명령 수신 및 처리

### 2. 응답 수신 플로우

```
BLE Module → UART → SerialPort (JNI) → SerialPortManager → AtCommandManager → MainActivity → UI
```

**상세 플로우**:
1. **BLE Module**: AT 명령 응답 전송
2. **UART 드라이버**: 데이터 버퍼에 저장
3. **네이티브 코드**: read() 시스템 콜로 데이터 읽기
4. **SerialPort (JNI)**: InputStream.read() 반환
5. **SerialPortManager**: ByteArray를 String으로 변환
6. **AtCommandManager**: 응답 파싱 및 콜백 호출
7. **MainActivity**: UI 업데이트 (runOnUiThread)
8. **TerminalAdapter**: 로그 추가 및 화면 표시

### 3. 백그라운드 수신 플로우

AtCommandManager는 백그라운드 코루틴에서 지속적으로 데이터를 수신합니다:

```kotlin
// AtCommandManager.kt
fun startReceiving() {
    receiveJob = CoroutineScope(Dispatchers.IO).launch {
        while (isActive && consecutiveErrors < maxConsecutiveErrors) {
            val response = receiveAtResponse()
            if (response != null && response.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    listener?.onResponse(response)
                }
            }
        }
    }
}
```

## 상태 관리

### AtCommandManager 상태
```kotlin
var isScanning: Boolean = false      // 스캔 진행 상태
private var consecutiveErrors = 0    // 연속 에러 카운트
private val maxConsecutiveErrors = 3 // 최대 에러 허용
```

### MainActivity 상태
```kotlin
private var isScanning = false       // UI 스캔 상태
```

### SerialPortManager 상태
```kotlin
private var isOpen = false           // 포트 열림 상태
```

## 스레드 모델

### UI 스레드 (Main Thread)
- MainActivity에서 UI 업데이트 담당
- 사용자 입력 이벤트 처리
- runOnUiThread로 UI 안전 업데이트

### 백그라운드 스레드 (Dispatchers.IO)
- AT 명령 전송 및 응답 수신
- 시리얼 I/O 작업
- 블로킹 I/O 처리

### 코루틴 스코프
```kotlin
// MainActivity에서 AT 명령 실행
lifecycleScope.launch {
    val result = atCommandManager.enableMaster(true)
    if (result.success) {
        addLogToTerminal("Success", LogType.INFO)
    }
}
```

## 에러 처리 체계

### 1. 시리얼 포트 에러
```kotlin
when (ret) {
    0 -> "Success"
    -1 -> "Device not found"
    -2 -> "Permission denied (Security exception)"
    -3 -> "IO exception"
    -4 -> "Timeout or no data"
}
```

### 2. AT 명령 에러
```kotlin
// AtCommandManager에서 에러 감지
if (consecutiveErrors >= maxConsecutiveErrors) {
    listener?.onError("Hardware connection failed")
}
```

### 3. UI 에러 표시
```kotlin
// MainActivity에서 에러 메시지 파싱
override fun onError(error: String) {
    val errorMessage = when {
        error.contains("-2508") -> "Hardware not connected"
        error.contains("-2500") -> "Communication timeout"
        else -> error
    }
    addLogToTerminal(errorMessage, LogType.ERROR)
}
```

## 메모리 관리

### 로그 버퍼 제한
```kotlin
// TerminalAdapter.kt
private val maxLogCount = 1000 // 최대 로그 개수

fun addLog(log: TerminalLog) {
    if (logs.size >= maxLogCount) {
        logs.removeAt(0)
    }
    logs.add(log)
}
```

### 리소스 정리
```kotlin
// MainActivity.onDestroy()
override fun onDestroy() {
    atCommandManager.stopReceiving()
    atCommandManager.closeSerialPort()
}
```

## 확장성 고려사항

### 1. 새로운 AT 명령 추가
- AtCommandManager에 suspend 함수 추가
- InputDialogFragment에 새 CommandType 추가
- MainActivity에서 UI 연동

### 2. 다른 BLE 모듈 지원
- AT 명령어 포맷 추상화
- Factory 패턴으로 모듈별 구현 분리

### 3. 다중 시리얼 포트 지원
- SerialPortManager를 인스턴스화
- 포트별 독립적인 AtCommandManager 생성
