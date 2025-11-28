# 프로젝트 개요

## 프로젝트 소개

**AT_Terminal**은 EFR32BG22 Bluetooth Low Energy 모듈을 제어하기 위한 Android 터미널 애플리케이션입니다. UART 시리얼 통신을 통해 AT 명령어를 전송하고, BLE 스캐닝, 디바이스 연결, 데이터 전송 등의 기능을 제공합니다.

## 주요 목적

- EFR32BG22 BLE 모듈 제어
- AT 명령어 기반 UART 통신
- BLE 디바이스 스캐닝 및 연결
- 실시간 터미널 로그 모니터링
- 하드웨어 플로우 제어 (CTS/RTS)

## 기술 스택

### Android 플랫폼
- **Compile SDK**: 35
- **Target SDK**: 35
- **Minimum SDK**: 24
- **Java Version**: 11

### 개발 언어
- **Kotlin**: 주 UI 및 비즈니스 로직
- **Java**: SerialPort JNI 연동 (google/android-serialport-api)

### 주요 라이브러리

#### 시리얼 통신
- `google/android-serialport-api`: 네이티브 UART 시리얼 포트 통신
- JNI 기반 `/dev/ttyS*` 디바이스 접근

#### Android 표준 라이브러리
- AndroidX Core KTX
- AppCompat
- Material Design Components
- ConstraintLayout
- RecyclerView

#### 코루틴
- Kotlinx Coroutines: 비동기 AT 명령 처리

## 프로젝트 구조

```
app/src/main/java/com/example/bleattest/
├── MainActivity.kt                 # 메인 터미널 화면
├── AtCommandManager.kt             # AT 명령어 관리
├── SerialPortManager.kt            # 시리얼 포트 통신
├── TerminalAdapter.kt              # 터미널 로그 어댑터
├── InputDialogFragment.kt          # 명령 입력 다이얼로그
└── models/
    ├── TerminalLog.kt              # 로그 데이터 모델
    ├── AtCommandResult.kt          # AT 명령 결과 모델
    └── ScanParams.kt               # 스캔 파라미터 모델

app/src/main/java/com/example/bleattest/
└── SerialPort.java                 # JNI 시리얼 포트 클래스

app/src/main/cpp/
└── SerialPort.cpp                  # 네이티브 시리얼 포트 구현
```

## 핵심 기능

### 1. AT 명령어 제어
- **마스터 모드 활성화**: `AT+OBSERVER=0/1`
- **MAC 주소 조회**: `AT+GETMAC`
- **BLE 스캔**: `AT+STARTNEWSCAN`
- **디바이스 연결**: `AT+CONNECT`
- **데이터 전송**: `AT+SEND`

### 2. 시리얼 통신
- UART 디바이스 접근 (`/dev/ttyS0`, `/dev/ttyS1` 등)
- 115200 baud rate 지원
- 하드웨어 플로우 제어 (CTS/RTS)
- 블로킹/논블로킹 I/O

### 3. 터미널 로그
- 실시간 로그 표시
- 송신/수신/스캔/에러/정보 로그 분류
- 색상별 로그 타입 구분
- 최대 1000개 로그 버퍼

### 4. BLE 기능
- 디바이스 스캐닝 (MAC, 이름, RSSI 필터링)
- 디바이스 연결
- GATT 데이터 전송

## 특별한 특징

### 네이티브 UART 통신
- JNI를 통한 직접 시리얼 포트 제어
- `/dev/ttyS*` 디바이스 파일 접근
- termios 기반 포트 설정

### AT 명령어 프로토콜
- EFR32BG22 모듈 전용 AT 명령어 세트
- Guard Time 기반 명령 모드 진입 (`+++`)
- CTS 제어를 통한 Beacon 모드 종료

### 코루틴 기반 비동기 처리
- suspend 함수를 통한 AT 명령 실행
- 백그라운드 데이터 수신
- UI 스레드 블로킹 방지

### 입력값 저장
- SharedPreferences를 통한 이전 입력값 저장
- 자주 사용하는 명령어 빠른 재실행

## 사용 시나리오

1. **산업용 BLE 제어**: 산업용 Android 디바이스에서 BLE 모듈 제어
2. **개발/디버깅**: EFR32BG22 모듈 테스트 및 디버깅
3. **터미널 도구**: AT 명령어 기반 시리얼 터미널
4. **프로토타입**: BLE 기반 제품 프로토타입 개발

## 하드웨어 요구사항

- **BLE 모듈**: EFR32BG22 (Master-Slave 펌웨어)
- **통신 인터페이스**: UART (Native ttyS)
- **보드레이트**: 115200 bps
- **플랫폼**: Android (Rockchip 3566 등 산업용 보드)
