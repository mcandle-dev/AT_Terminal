# 프로젝트 개요

## 프로젝트 소개

**VPOS BLE App**은 Bluetooth Low Energy (BLE) 기능을 실험하고 테스트하기 위한 Android 애플리케이션입니다. 벤더에서 제공한 라이브러리를 활용하여 BLE 스캐닝, 광고, 그리고 디바이스 간 통신 기능을 구현했습니다.

## 주요 목적

- BLE 스캐닝 기능 테스트 및 검증
- 벤더 제공 라이브러리(`At` API)의 기능 평가
- BLE 광고(Advertising) 기능 실험
- 실시간 BLE 디바이스 정보 모니터링

## 기술 스택

### Android 플랫폼
- **Compile SDK**: 35
- **Target SDK**: 35  
- **Minimum SDK**: 24
- **Java Version**: 11

### 개발 언어
- **Kotlin**: 주 UI 및 비즈니스 로직
- **Java**: BLE 스캔 핵심 로직 (BleScan.java)

### 주요 라이브러리

#### 벤더 라이브러리
- `libVpos3893_release_20250729.aar`: VPOS BLE 통신을 위한 네이티브 라이브러리
- `vpos.apipackage.At`: BLE 마스터 모드 및 스캔 API
- `vpos.apipackage.Beacon`: 비콘 관련 기능

#### Android 표준 라이브러리
- AndroidX Core KTX
- AppCompat
- Material Design Components
- ConstraintLayout
- RecyclerView

#### 코루틴
- Kotlinx Coroutines: 비동기 작업 처리

## 프로젝트 구조

```
app/src/main/java/com/mcandle/bledemo/
├── MainActivity.kt              # 메인 화면 및 BLE 제어
├── BleScan.java                # BLE 스캔 핵심 로직
├── DeviceModel.kt              # BLE 디바이스 데이터 모델
├── BLEDeviceAdapter.kt         # 디바이스 리스트 어댑터
├── BLEAdvertiseDialogFragment.kt # BLE 광고 다이얼로그
└── utils/
    └── BLEUtils.kt             # BLE 데이터 변환 유틸리티
```

## 핵심 기능

### 1. BLE 스캐닝
- **마스터 모드 활성화**: BLE 모듈을 마스터 모드로 전환
- **단발 스캔**: 한 번의 스캔으로 주변 디바이스 검색
- **연속 스캔**: 지속적인 스캔으로 실시간 모니터링

### 2. 디바이스 정보 표시
- 디바이스 이름, MAC 주소
- RSSI (신호 강도) 및 Tx Power
- 서비스 UUID 및 제조사 데이터
- 실시간 업데이트

### 3. BLE 광고
- 사용자 정의 광고 데이터 전송
- 10초 타이머 기반 자동 종료
- 수동 시작/정지 제어

### 4. 데이터 처리
- HEX ↔ ASCII 변환
- JSON 기반 스캔 결과 파싱
- 실시간 데이터 스트림 처리

## 특별한 특징

### 혼합 언어 구조
- **Kotlin**: UI 로직과 Android 컴포넌트
- **Java**: 벤더 라이브러리와의 밀접한 연동 부분

### 콜백 기반 아키텍처
- `ScanResultListener`: 스캔 결과 전달
- `DataReceiveListener`: 실시간 데이터 수신

### 실시간 업데이트
- 코루틴 기반 비동기 처리
- RecyclerView를 통한 효율적인 UI 업데이트

### 벤더 라이브러리 통합
- 네이티브 AAR 라이브러리 포함
- ARM 기반 아키텍처 지원 (armeabi-v7a, arm64-v8a, x86, x86_64)

## 사용 시나리오

1. **개발/테스트 환경**: BLE 기능의 동작 검증
2. **디버깅 도구**: BLE 통신 문제 진단
3. **프로토타입**: BLE 기반 애플리케이션의 기초
4. **교육 목적**: BLE 통신 방식 학습