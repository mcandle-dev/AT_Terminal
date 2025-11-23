# 시스템 아키텍처

## 전체 아키텍처 개요

VPOS BLE App은 레이어드 아키텍처를 기반으로 설계되었으며, 벤더 라이브러리와의 통합을 통해 BLE 기능을 제공합니다.

```
┌─────────────────────────────────────────────────────────┐
│                    UI Layer (Kotlin)                   │
├─────────────────────────────────────────────────────────┤
│ MainActivity │ BLEDeviceAdapter │ DialogFragments       │
└─────────────────────────────────────────────────────────┘
                             ↕
┌─────────────────────────────────────────────────────────┐
│                  Business Layer                        │
├─────────────────────────────────────────────────────────┤
│     BleScan (Java)     │    DeviceModel (Kotlin)       │
│   - Callback 관리       │    - 데이터 모델               │
│   - 상태 관리           │    - 변환 유틸리티             │
└─────────────────────────────────────────────────────────┘
                             ↕
┌─────────────────────────────────────────────────────────┐
│              Vendor Library Layer                      │
├─────────────────────────────────────────────────────────┤
│   vpos.apipackage.At   │   vpos.apipackage.Beacon      │
│   - Lib_EnableMaster   │   - 비콘 관련 기능             │
│   - Lib_AtStartNewScan │                               │
│   - Lib_ComRecvAT      │                               │
└─────────────────────────────────────────────────────────┘
                             ↕
┌─────────────────────────────────────────────────────────┐
│              Hardware Abstraction                      │
├─────────────────────────────────────────────────────────┤
│               Native BLE Hardware                       │
└─────────────────────────────────────────────────────────┘
```

## 컴포넌트 간 관계

### 1. UI 계층 (Presentation Layer)

#### MainActivity
- **역할**: 메인 컨트롤러 및 이벤트 처리기
- **책임**:
  - 사용자 입력 처리 (버튼 클릭)
  - BLE 스캔 결과 표시
  - 다이얼로그 관리
- **의존성**: BleScan, BLEDeviceAdapter

#### BLEDeviceAdapter
- **역할**: RecyclerView 어댑터
- **책임**: 스캔된 디바이스 목록 표시
- **의존성**: DeviceModel

#### BLEAdvertiseDialogFragment
- **역할**: BLE 광고 UI 다이얼로그
- **책임**: 광고 시작/정지 제어 인터페이스
- **의존성**: MainActivity (콜백)

### 2. 비즈니스 로직 계층 (Business Layer)

#### BleScan (Java)
- **역할**: BLE 스캔 로직의 핵심
- **책임**:
  - 벤더 라이브러리 API 래핑
  - 스캔 상태 관리
  - 콜백 인터페이스 제공
- **주요 인터페이스**:
  ```java
  public interface ScanResultListener {
      void onScanResult(JSONArray scanData);
  }
  
  public interface DataReceiveListener {
      void onDataReceived(String buff);
  }
  ```

#### DeviceModel (Kotlin)
- **역할**: BLE 디바이스 데이터 표현
- **책임**: 
  - 디바이스 정보 구조화
  - HEX 데이터 변환
- **주요 속성**:
  ```kotlin
  data class DeviceModel(
      var name: String,
      val address: String,
      var rssi: Int,
      var serviceUuids: String,
      var manufacturerData: String
  )
  ```

### 3. 벤더 라이브러리 계층 (Vendor Layer)

#### vpos.apipackage.At
- **주요 API**:
  - `Lib_EnableMaster(boolean)`: 마스터 모드 활성화
  - `Lib_AtStartNewScan()`: 새 스캔 시작
  - `Lib_ComRecvAT()`: 데이터 수신

## 데이터 플로우

### 1. BLE 스캔 데이터 플로우

```
Hardware → Vendor Library → BleScan → MainActivity → BLEDeviceAdapter → UI
```

**상세 플로우**:
1. **하드웨어**: BLE 신호 수신
2. **벤더 라이브러리**: 원시 데이터를 구조화된 형태로 변환
3. **BleScan**: JSON 파싱 및 콜백 호출
4. **MainActivity**: DeviceModel 객체 생성 및 리스트 업데이트
5. **BLEDeviceAdapter**: UI 업데이트
6. **UI**: 사용자에게 표시

### 2. 사용자 입력 플로우

```
UI → MainActivity → BleScan → Vendor Library → Hardware
```

**상세 플로우**:
1. **UI**: 버튼 클릭 (Master/Scan/ComRev)
2. **MainActivity**: 이벤트 처리 메서드 호출
3. **BleScan**: 해당 기능 실행
4. **벤더 라이브러리**: 하드웨어 제어 명령 전송
5. **하드웨어**: BLE 동작 수행

## 상태 관리

### BleScan 상태
```java
private boolean isScanning = false;  // 스캔 진행 상태
private boolean isMaster = false;    // 마스터 모드 상태
```

### MainActivity 상태
```kotlin
private var isScanning = false       // UI 스캔 상태
private var scanJob: Job? = null     // 코루틴 작업 관리
private var mStartFlag = false       // 시작 플래그
private var mEnableFlag = true       // 활성화 플래그
```

## 스레드 모델

### UI 스레드
- MainActivity에서 UI 업데이트 담당
- 사용자 입력 이벤트 처리

### 백그라운드 스레드
- BleScan에서 데이터 수신 처리
- 벤더 라이브러리 호출

### 코루틴 (Kotlin)
```kotlin
// MainActivity에서 비동기 작업 처리
lifecycleScope.launch {
    // 백그라운드 작업
    withContext(Dispatchers.IO) {
        // BLE 작업 수행
    }
    // UI 업데이트는 Main 스레드에서
}
```

## 메모리 관리

### 디바이스 목록 관리
```kotlin
private val deviceList = mutableListOf<DeviceModel>()
```

### 콜백 참조 관리
- BleScan에서 weak reference 패턴 사용 고려
- Activity lifecycle과 연동하여 메모리 누수 방지

## 확장성 고려사항

### 1. 새로운 BLE 기능 추가
- BleScan 클래스에 새 메서드 추가
- 해당 인터페이스 정의
- MainActivity에서 UI 연동

### 2. 다른 벤더 라이브러리 통합
- 추상 인터페이스 레이어 도입
- Factory 패턴으로 라이브러리 선택

### 3. 데이터 지속성
- Room 데이터베이스 통합
- 스캔 이력 저장 기능

## 보안 고려사항

### BLE 권한
- `BLUETOOTH_ADMIN`: 필수
- `ACCESS_FINE_LOCATION`: BLE 스캔용
- 런타임 권한 요청 구현

### 데이터 보호
- 민감한 디바이스 정보 암호화
- 로그 출력 시 개인정보 마스킹