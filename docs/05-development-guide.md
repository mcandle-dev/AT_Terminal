# 개발 가이드

## 개발 환경 설정

### 필수 요구사항

#### Android Studio
- **버전**: Arctic Fox (2020.3.1) 이상 권장
- **Gradle Plugin**: 7.0 이상
- **Kotlin Plugin**: 최신 버전

#### Android SDK
- **Compile SDK**: 35
- **Target SDK**: 35
- **Minimum SDK**: 24
- **Build Tools**: 35.0.0

#### Java/Kotlin
- **Java**: JDK 11
- **Kotlin**: 1.5.0 이상

### 프로젝트 설정

#### 1. 저장소 클론
```bash
git clone <repository-url>
cd mcandle-simple
```

#### 2. Gradle 동기화
```bash
# Windows
./gradlew clean
./gradlew build

# macOS/Linux  
chmod +x ./gradlew
./gradlew clean
./gradlew build
```

#### 3. 벤더 라이브러리 확인
`app/libs/libVpos3893_release_20250729.aar` 파일이 존재하는지 확인하세요.

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
- ProGuard 적용 (현재 비활성화)
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
adb shell am start -n com.mcandle.bledemo/.MainActivity
```

## 테스트

### 단위 테스트

#### 테스트 실행
```bash
./gradlew test
```

#### 테스트 파일 위치
- **Unit Tests**: `app/src/test/java/com/mcandle/bledemo/`
- **Instrumented Tests**: `app/src/androidTest/java/com/mcandle/bledemo/`

#### 현재 테스트
- `ExampleUnitTest.kt`: 기본 단위 테스트
- `ExampleInstrumentedTest.kt`: 기본 계측 테스트

### 통합 테스트
```bash
# Android 디바이스나 에뮬레이터에서 실행
./gradlew connectedAndroidTest
```

## 디버깅

### 로그 확인

#### Logcat 필터링
```bash
# BLE 관련 로그만 보기
adb logcat | grep -E "(BLE_|BLEScan)"

# 앱 전체 로그
adb logcat | grep "com.mcandle.bledemo"
```

#### 주요 로그 태그
- `BLE_MANAGER`: 메인 BLE 동작 로그
- `BLEScan`: BleScan 클래스 로그
- `BLE_ADVERTISE`: 광고 관련 로그

### 일반적인 디버깅 시나리오

#### 1. BLE 스캔이 동작하지 않는 경우
```kotlin
// BleScan.java에서 확인
Log.d("BLE_MANAGER", "Master mode: " + isMaster);
Log.d("BLE_MANAGER", "Scanning: " + isScanning);

// 벤더 라이브러리 호출 결과 확인
int result = At.Lib_EnableMaster(true);
Log.d("BLE_MANAGER", "Enable master result: " + result);
```

#### 2. 권한 문제
```xml
<!-- AndroidManifest.xml에 추가 -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

#### 3. 메모리 누수 확인
```kotlin
// MainActivity.onDestroy()에서
override fun onDestroy() {
    scanJob?.cancel()
    bleScan.stopAllScanning()
    super.onDestroy()
}
```

## 코딩 컨벤션

### Kotlin 스타일

#### 네이밍 규칙
```kotlin
// 클래스: PascalCase
class MainActivity : AppCompatActivity()

// 함수: camelCase
private fun startContinuousScanning()

// 변수: camelCase
private var isScanning = false

// 상수: UPPER_SNAKE_CASE
companion object {
    private const val SCAN_TIMEOUT = 10000L
}
```

#### 코드 구조
```kotlin
class MainActivity : AppCompatActivity() {
    // 1. 컴패니언 객체
    companion object {
        private const val TAG = "MainActivity"
    }
    
    // 2. 프로퍼티
    private lateinit var recyclerView: RecyclerView
    private var isScanning = false
    
    // 3. 생명주기 메서드
    override fun onCreate(savedInstanceState: Bundle?) {
        // 구현
    }
    
    // 4. 퍼블릭 메서드
    fun startScanning() {
        // 구현
    }
    
    // 5. 프라이빗 메서드
    private fun setupViews() {
        // 구현
    }
}
```

### Java 스타일

#### 네이밍 규칙
```java
// 클래스: PascalCase
public class BleScan {
    // 상수: UPPER_SNAKE_CASE
    private static final String TAG = "BLEScan";
    
    // 변수: camelCase
    private boolean isScanning;
    
    // 메서드: camelCase
    public int enableMasterMode(boolean enable) {
        return 0;
    }
}
```

#### 코드 구조
```java
public class BleScan {
    // 1. 상수
    private static final String TAG = "BLEScan";
    
    // 2. 인터페이스
    public interface ScanResultListener {
        void onScanResult(JSONArray scanData);
    }
    
    // 3. 필드
    private boolean isScanning = false;
    
    // 4. 생성자
    public BleScan() {
        // 초기화
    }
    
    // 5. 퍼블릭 메서드
    public int enableMasterMode(boolean enable) {
        // 구현
    }
    
    // 6. 프라이빗 메서드
    private void logResult(int result) {
        // 구현
    }
}
```

### XML 리소스 스타일

#### 레이아웃 네이밍
```xml
<!-- activity_main.xml -->
<LinearLayout
    android:id="@+id/mainContainer"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    
    <Button
        android:id="@+id/btnMaster"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Master" />
        
</LinearLayout>
```

#### 리소스 ID 네이밍 규칙
- **Button**: `btn + 기능명` (예: `btnMaster`, `btnScan`)
- **TextView**: `tv + 내용` (예: `tvDeviceName`, `tvRssi`)
- **EditText**: `et + 용도` (예: `etInput`, `etSearch`)
- **RecyclerView**: `rv + 목록` (예: `rvDeviceList`)

## 의존성 관리

### 현재 의존성

#### build.gradle.kts (app)
```kotlin
dependencies {
    // Android 기본
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    
    // 테스트
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    
    // 벤더 라이브러리
    implementation(fileTree(mapOf(
        "dir" to "libs", 
        "include" to listOf("*.aar", "*.jar")
    )))
}
```

### 새 의존성 추가

#### 1. libs.versions.toml 수정
```toml
[versions]
newLibrary = "1.0.0"

[libraries]
new-library = { module = "com.example:new-library", version.ref = "newLibrary" }

[plugins]
# 플러그인 추가 시
```

#### 2. build.gradle.kts에서 사용
```kotlin
implementation(libs.new.library)
```

## 성능 최적화

### 메모리 최적화

#### 1. 디바이스 리스트 크기 제한
```kotlin
private fun updateDeviceList(newDevice: DeviceModel) {
    // 최대 100개로 제한
    if (deviceList.size >= 100) {
        deviceList.removeAt(0)  // 가장 오래된 항목 제거
    }
    // ... 나머지 로직
}
```

#### 2. 이미지 최적화
```kotlin
// Glide나 Picasso 사용 권장
implementation("com.github.bumptech.glide:glide:4.14.2")
```

### 배터리 최적화

#### 1. 불필요한 스캔 방지
```kotlin
override fun onPause() {
    super.onPause()
    if (isScanning) {
        // 앱이 백그라운드로 이동하면 스캔 일시정지
        pauseScanning()
    }
}

override fun onResume() {
    super.onResume()
    if (wasScanningBeforePause) {
        // 앱이 다시 포어그라운드로 오면 스캔 재개
        resumeScanning()
    }
}
```

#### 2. 스캔 주기 조절
```kotlin
// 연속 스캔에서 적절한 대기 시간 추가
Thread.sleep(100)  // 100ms 대기
```

## 보안 고려사항

### 권한 관리

#### 런타임 권한 체크
```kotlin
private fun checkBLEPermissions(): Boolean {
    val permissions = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    
    return permissions.all { 
        ContextCompat.checkSelfPermission(this, it) == 
        PackageManager.PERMISSION_GRANTED 
    }
}

private fun requestBLEPermissions() {
    ActivityCompat.requestPermissions(
        this,
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        ),
        PERMISSION_REQUEST_CODE
    )
}
```

### 데이터 보호

#### 1. 로그에서 민감 정보 제거
```kotlin
// 나쁜 예
Log.d("BLE", "Device MAC: ${device.address}")

// 좋은 예  
Log.d("BLE", "Device MAC: ${maskMacAddress(device.address)}")

private fun maskMacAddress(mac: String): String {
    return mac.take(8) + "****" + mac.takeLast(2)
}
```

#### 2. 데이터 암호화
```kotlin
// SharedPreferences 사용 시 암호화
// implementation "androidx.security:security-crypto:1.0.0"
```

## 문제 해결

### 자주 발생하는 문제

#### 1. AAR 라이브러리 로딩 실패
```
java.lang.UnsatisfiedLinkError: dalvik.system.PathClassLoader
```
**해결방법**:
- `app/libs/` 폴더에 AAR 파일이 있는지 확인
- `build.gradle.kts`의 `fileTree` 설정 확인
- Clean & Rebuild 실행

#### 2. BLE 스캔 권한 에러
```
java.lang.SecurityException: Need ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION permission
```
**해결방법**:
- AndroidManifest.xml에 권한 추가
- 런타임 권한 요청 구현
- 위치 서비스 활성화 확인

#### 3. 메모리 부족
```
java.lang.OutOfMemoryError
```
**해결방법**:
- 디바이스 리스트 크기 제한
- 사용하지 않는 객체 null 처리
- 백그라운드 스레드 정리

### 디버깅 도구

#### 1. ADB 명령어
```bash
# 앱 로그 실시간 확인
adb logcat | grep "mcandle"

# 메모리 사용량 확인
adb shell dumpsys meminfo com.mcandle.bledemo

# CPU 사용량 확인  
adb shell top | grep bledemo
```

#### 2. Android Studio 프로파일러
- **Memory Profiler**: 메모리 누수 탐지
- **CPU Profiler**: 성능 병목 지점 분석
- **Network Profiler**: BLE 통신 분석