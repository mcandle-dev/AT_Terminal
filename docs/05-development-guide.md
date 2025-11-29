# 개발 가이드

## 개발 환경 설정

### 필수 요구사항

#### Android Studio
- **버전**: Hedgehog (2023.1) 이상 권장
- **Gradle Plugin**: 8.0 이상
- **Kotlin Plugin**: 최신 버전

#### Android SDK
- **Compile SDK**: 35
- **Target SDK**: 35
- **Minimum SDK**: 24
- **Build Tools**: 35.0.0

#### NDK (JNI 빌드용)
- **CMake**: 3.22.1 이상
- **NDK Version**: 26.1 이상

#### Java/Kotlin
- **Java**: JDK 11
- **Kotlin**: 1.9.0 이상

### 프로젝트 설정

#### 1. 저장소 클론
```bash
git clone <repository-url>
cd AT_Terminal
```

#### 2. Gradle 동기화
```bash
# Windows
.\gradlew clean
.\gradlew build

# macOS/Linux
chmod +x ./gradlew
./gradlew clean
./gradlew build
```

#### 3. NDK 설정
```bash
# Android Studio에서 NDK 자동 설치
# Tools → SDK Manager → SDK Tools → NDK (Side by side)
```

## 빌드 및 실행

### 빌드 명령어

#### Debug 빌드
```bash
./gradlew assembleDebug
```
- 출력 위치: `app/build/outputs/apk/debug/app-debug.apk`
- 디버그 정보 포함
- 코드 최적화 비활성화

#### Release 빌드
```bash
./gradlew assembleRelease
```
- 출력 위치: `app/build/outputs/apk/release/app-release-unsigned.apk`
- 코드 최적화 활성화

#### Clean 빌드
```bash
./gradlew clean assembleDebug
```

### 실행 및 설치

#### 디바이스에 설치
```bash
# USB 디버깅 활성화된 Android 디바이스 연결 후
./gradlew installDebug

# 또는 ADB 직접 사용
adb install app/build/outputs/apk/debug/app-debug.apk
```

#### 실행
```bash
adb shell am start -n com.example.bleattest/.MainActivity
```

## 디버깅

### 로그 확인

#### Logcat 필터링
```bash
# AT 명령 관련 로그만 보기
adb logcat | grep -E "(AtCommandManager|SerialPortManager)"

# 앱 전체 로그
adb logcat | grep "com.example.bleattest"

# 에러만 보기
adb logcat | grep -E "ERROR|Exception"
```

#### 주요 로그 태그
- `AtCommandManager`: AT 명령 실행 로그
- `SerialPortManager`: 시리얼 포트 I/O 로그
- `MainActivity`: UI 이벤트 로그
- `TerminalAdapter`: 터미널 로그 표시

### 일반적인 디버깅 시나리오

#### 1. 시리얼 포트가 열리지 않는 경우
```kotlin
// SerialPortManager.kt에서 확인
val ret = open("/dev/ttyS1", 115200)
when (ret) {
    -1 -> "Device not found" // 디바이스 파일이 없음
    -2 -> "Permission denied" // root/su 권한 필요
    -3 -> "IO exception" // 하드웨어 문제
}
```

**해결방법**:
- 디바이스 경로 확인: `adb shell ls /dev/ttyS*`
- SELinux 확인: `adb shell getenforce`
- 권한 확인: `adb shell chmod 666 /dev/ttyS1`

#### 2. AT 명령 응답 없음
```kotlin
// AtCommandManager.kt
fun receiveAtResponse(): String? {
    // 타임아웃: 1000ms
    // 응답이 없으면 null 반환
}
```

**해결방법**:
- 하드웨어 연결 확인
- 보드레이트 확인 (115200)
- CTS/RTS 핀 연결 확인

#### 3. 백그라운드 수신 중단
```kotlin
// AtCommandManager.kt
private var consecutiveErrors = 0
private val maxConsecutiveErrors = 3

// 연속 3회 에러 발생 시 자동 중단
```

**해결방법**:
- 로그에서 에러 원인 확인
- 시리얼 포트 재연결
- 앱 재시작

### 권한 설정

#### AndroidManifest.xml
```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

#### SELinux 설정
산업용 Android 디바이스에서는 SELinux를 permissive 모드로 설정해야 할 수 있습니다:
```bash
adb shell su 0 setenforce 0
```

## 코딩 컨벤션

### Kotlin 스타일

#### 네이밍 규칙
```kotlin
// 클래스: PascalCase
class AtCommandManager

// 함수: camelCase
fun sendAtCommand(command: String)

// 변수: camelCase
private var isScanning = false

// 상수: UPPER_SNAKE_CASE
companion object {
    private const val BUFFER_SIZE = 4096
    private const val RESPONSE_TIMEOUT = 200L
}
```

#### 코드 구조
```kotlin
class AtCommandManager {
    // 1. Companion object
    companion object {
        private const val TAG = "AtCommandManager"
    }

    // 2. Properties
    private val serialPortManager = SerialPortManager()
    private var receiveJob: Job? = null

    // 3. Interface
    interface OnAtResponseListener {
        fun onResponse(response: String)
        fun onError(error: String)
    }

    // 4. Public methods
    fun initSerialPort(devicePath: String, baudrate: Int): Int {
        // 구현
    }

    // 5. Private methods
    private fun receiveAtResponse(): String? {
        // 구현
    }
}
```

### 비동기 처리

#### Coroutines 사용
```kotlin
// MainActivity에서 AT 명령 실행
lifecycleScope.launch {
    val result = atCommandManager.enableMaster(true)
    if (result.success) {
        addLogToTerminal("Success", LogType.INFO)
    }
}

// AtCommandManager에서 suspend 함수
suspend fun enableMaster(enable: Boolean): AtCommandResult {
    return withContext(Dispatchers.IO) {
        // 블로킹 I/O 작업
    }
}
```

#### 백그라운드 작업
```kotlin
// 백그라운드 수신
fun startReceiving() {
    receiveJob = CoroutineScope(Dispatchers.IO).launch {
        while (isActive) {
            val response = receiveAtResponse()
            withContext(Dispatchers.Main) {
                listener?.onResponse(response)
            }
        }
    }
}

// 정리
fun stopReceiving() {
    receiveJob?.cancel()
    receiveJob = null
}
```

## 의존성 관리

### 현재 의존성

#### build.gradle.kts (app)
```kotlin
dependencies {
    // Android 기본
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // 테스트
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

### JNI/NDK 설정

#### CMakeLists.txt
```cmake
cmake_minimum_required(VERSION 3.22.1)

project("serialport")

add_library(serial-port
        SHARED
        SerialPort.cpp)

find_library(log-lib log)

target_link_libraries(serial-port ${log-lib})
```

#### build.gradle.kts
```kotlin
externalNativeBuild {
    cmake {
        path = file("src/main/cpp/CMakeLists.txt")
    }
}
```

## 성능 최적화

### 메모리 최적화

#### 로그 버퍼 제한
```kotlin
// TerminalAdapter.kt
private val maxLogCount = 1000

fun addLog(log: TerminalLog) {
    if (logs.size >= maxLogCount) {
        logs.removeAt(0)
    }
    logs.add(log)
}
```

#### 리소스 정리
```kotlin
// MainActivity.onDestroy()
override fun onDestroy() {
    atCommandManager.stopReceiving()
    if (isScanning) {
        lifecycleScope.launch {
            atCommandManager.stopScan()
        }
    }
    atCommandManager.closeSerialPort()
}
```

### 배터리 최적화

#### 백그라운드 제한
```kotlin
override fun onPause() {
    super.onPause()
    // 앱이 백그라운드로 이동 시 불필요한 작업 중지
}

override fun onResume() {
    super.onResume()
    // 앱 재개 시 필요한 작업 재시작
}
```

## 문제 해결

### 자주 발생하는 문제

#### 1. JNI 라이브러리 로딩 실패
```
java.lang.UnsatisfiedLinkError: dlopen failed
```
**해결방법**:
- NDK 버전 확인
- CMakeLists.txt 설정 확인
- Clean & Rebuild 실행

#### 2. 시리얼 포트 권한 에러
```
SecurityException: Permission denied
```
**해결방법**:
- Root 권한 확인: `adb shell su`
- 디바이스 파일 권한 확인: `ls -l /dev/ttyS*`
- SELinux 확인: `getenforce`

#### 3. AT 명령 타임아웃
```
Failed to receive AT response, ret=-4
```
**해결방법**:
- 하드웨어 연결 확인
- BLE 모듈 전원 확인
- 보드레이트 확인

### 디버깅 도구

#### ADB 명령어
```bash
# 로그 실시간 확인
adb logcat -s AtCommandManager:D SerialPortManager:D

# 메모리 사용량 확인
adb shell dumpsys meminfo com.example.bleattest

# CPU 사용량 확인
adb shell top | grep bleattest

# 시리얼 포트 상태 확인
adb shell ls -l /dev/ttyS*
adb shell cat /proc/tty/driver/serial
```

#### Android Studio Profiler
- **Memory Profiler**: 메모리 누수 탐지
- **CPU Profiler**: 성능 병목 분석
- **Network Profiler**: 통신 패킷 분석

## 테스트

### 단위 테스트
```bash
./gradlew test
```

### Instrumented 테스트
```bash
./gradlew connectedAndroidTest
```

### 수동 테스트 체크리스트
- [ ] 시리얼 포트 연결 확인
- [ ] Master 모드 활성화
- [ ] MAC 주소 조회
- [ ] BLE 스캔 시작/중지
- [ ] 디바이스 연결
- [ ] 데이터 전송
- [ ] 에러 처리 확인
- [ ] 앱 재시작 시 동작 확인
