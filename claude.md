# Claude AI 개발 기록

이 문서는 AT_Terminal 프로젝트에서 Claude AI를 활용한 개발 과정을 기록합니다.

## 프로젝트 개요

**프로젝트명**: AT_Terminal
**목적**: EFR32BG22 BLE 모듈을 UART 시리얼 통신으로 제어하는 Android 터미널 애플리케이션
**플랫폼**: Android (산업용 디바이스)
**주요 기술**: Kotlin, JNI, UART Serial, AT Command Protocol

## Claude 지원 내역

### 1. AT 명령어 프로토콜 문제 해결 (2025-11-28)

#### 문제 상황
- `AT+OBSERVER=0` 명령 실행 시 `ERROR` 응답 수신
- SELinux audit denial 경고 발생
- 사용자가 UART 통신 문제로 오인

#### 분석 과정
1. **로그 분석**:
   - SELinux는 `permissive=1` 모드로 실제 차단 안 함
   - 통신은 정상 동작 (데이터 송수신 확인)
   - 실제 문제는 AT 명령 타이밍

2. **원인 파악**:
   - `+++` 명령 후 Guard Time 부족 (100ms → 1000ms 필요)
   - EFR32BG22 모듈은 AT 모드 진입에 최소 1초 필요
   - 타이밍 문제로 인한 명령 실패

3. **해결책 제시**:
   - `AtCommandManager.kt:200` 라인의 `delay(100)`을 `delay(1000)`으로 수정
   - Guard Time 준수를 위한 타이밍 조정

#### 기술적 근거
- EFR32BG22 펌웨어 문서 참조
- AT 명령 프로토콜 표준 (Guard Time 요구사항)
- 시리얼 통신 타이밍 분석

### 2. 프로젝트 문서화 (2025-11-28)

#### 작업 내용
docs 폴더의 모든 문서를 현재 소스 코드에 맞게 전면 개편

#### 업데이트된 문서

1. **README.md**
   - VPOS BLE → AT_Terminal로 프로젝트명 변경
   - 시리얼 라이브러리 정보 업데이트

2. **01-overview.md**
   - EFR32BG22 UART 제어 프로젝트 소개
   - google/android-serialport-api 사용
   - AT 명령어 기반 제어 방식 설명

3. **02-architecture.md**
   - JNI 시리얼 통신 레이어 추가
   - SerialPortManager + AtCommandManager 아키텍처
   - 데이터 플로우 상세 분석

4. **03-ble-workflow.md**
   - AT 명령어 워크플로우 상세 설명
   - enableMaster 시퀀스 (CTS 제어 포함)
   - Guard Time 및 타이밍 요구사항

5. **04-components.md**
   - MainActivity, AtCommandManager, SerialPortManager 분석
   - TerminalAdapter, InputDialogFragment 설명
   - 데이터 모델 (TerminalLog, ScanParams 등)

6. **05-development-guide.md**
   - NDK/JNI 빌드 설정
   - 시리얼 포트 디버깅 방법
   - SELinux 및 권한 설정
   - 코딩 컨벤션

7. **06-ui-structure.md**
   - 터미널 UI 구조
   - 로그 타입별 색상 스킴
   - 입력 다이얼로그 구조

#### 문서화 원칙
- 실제 소스 코드와 100% 일치
- 코드 예제는 실제 파일에서 발췌
- 파일 경로와 라인 번호 명시
- 초보자도 이해할 수 있는 상세한 설명

### 3. 주요 버그 수정 및 기능 개선 (2025-12-01)

#### 3.1. 빌드 오류 수정

**문제**: ScanParams 파라미터 이름 불일치
```
error: No parameter with name 'macAddress' found
error: No parameter with name 'broadcastName' found
```

**원인**: `InputDialogFragment.kt`에서 이전 버전의 파라미터명 사용

**해결**: `InputDialogFragment.kt:155-159` 수정
```kotlin
// 변경 전
val params = ScanParams(
    macAddress = macAddress,
    broadcastName = broadcastName,
    ...
)

// 변경 후
val params = ScanParams(
    scanTime = 20,
    minRssi = minRssi,
    nameFilter = data
)
```

#### 3.2. Stop Scan 기능 개선

**변경 사항**: 스캔 중지 명령 시퀀스 개선

**AtCommandManager.kt:342-424 (stopScan)**
```kotlin
// 변경 전
suspend fun stopScan() {
    sendAtCommand("AT+STOPSCAN")
}

// 변경 후
suspend fun stopScan() {
    // Step 1: AT+ROLE? - 현재 Role 확인
    // Step 2: AT+ROLE=1 - Master 모드로 설정
    // Step 3: AT+OBSERVER=0 - Observer 모드 비활성화 (스캔 중지)
}
```

**이유**: EFR32BG22 모듈에서 스캔을 안전하게 중지하려면 Role 설정과 Observer 모드를 함께 제어해야 함

#### 3.3. 스캔 결과 수신 문제 해결

**문제**: 스캔 시작 후 OK만 수신되고 스캔 결과가 나타나지 않음
```
Sent: AT+OBSERVER=1,20,,,-80,,
Received: OK
(이후 스캔 결과 없음)
```

**원인**: 백그라운드 수신기가 실행되지 않아 비동기 스캔 결과를 수신하지 못함

**해결**: `MainActivity.kt:204-233` 수정
```kotlin
override fun onStartScan(params: ScanParams) {
    lifecycleScope.launch {
        // 백그라운드 수신기가 실행 중인지 확인하고, 실행 중이 아니면 시작
        if (!atCommandManager.isReceiving()) {
            addLogToTerminal("Starting background receiver...", LogType.INFO)
            atCommandManager.startReceiving()
            addLogToTerminal("Background receiver started", LogType.INFO)
        }

        val result = atCommandManager.startScan(params)
        // ...
    }
}
```

**결과**: 스캔 결과가 실시간으로 터미널에 표시됨
```
SCAN:MAC:FA:8D:0D:27:50:C6,
RSSI:-50
ADV/RSP:0201060C095246737461725F3838383838
```

#### 3.4. AT 모드 진입 시퀀스 추가

**문제**: AT+OBSERVER 명령이 ERROR 반환
```
Sent: AT+OBSERVER=1,20,,,-80,,
Received: ERROR
```

**원인**: AT 모드 진입 없이 AT 명령 전송

**해결**: `AtCommandManager.kt:310-415 (startScan)` 완전한 AT 모드 시퀀스 추가
```kotlin
suspend fun startScan(params: ScanParams) {
    // Step 1: Guard Time 1초 대기
    delay(1000)

    // Step 2: AT 모드 진입
    sendAtCommand("+++")
    delay(1000) // Guard Time

    // Step 3: 스캔 시작
    sendAtCommand(params.toAtCommand())

    // Step 4: AT 모드 종료
    sendAtCommand("AT+EXIT")

    // Step 5: AT 모드 재진입
    sendAtCommand("+++")
    delay(1000) // Guard Time
}
```

#### 3.5. enableMaster() 로직 수정

**문제**: Master 모드 설정이 잘못된 명령 사용

**원인**: AT+OBSERVER와 AT+ROLE 혼동
- `AT+ROLE=1`: Master 모드 설정
- `AT+OBSERVER=1`: 스캔 시작 (별개의 명령)

**해결**: `AtCommandManager.kt:206-230` 수정
```kotlin
// 변경 전
val observerCommand = if (enable) "AT+OBSERVER=1" else "AT+OBSERVER=0"

// 변경 후
val roleCommand = if (enable) "AT+ROLE=1" else "AT+ROLE=0"
```

### 4. 코드 분석 및 이해 지원

#### 분석한 주요 컴포넌트

**AtCommandManager.kt**:
- AT 명령어 전송 및 응답 수신 로직
- 백그라운드 수신 코루틴 관리
- 에러 핸들링 (연속 에러 감지)

**SerialPortManager.kt**:
- JNI 기반 시리얼 포트 제어
- InputStream/OutputStream 관리
- CTS 하드웨어 플로우 제어

**MainActivity.kt**:
- 터미널 UI 관리
- lifecycleScope를 통한 비동기 AT 명령 실행
- 로그 타입별 색상 구분

## 기술적 통찰

### AT 명령어 체계

#### 명령어 분류
1. **Master/Slave 모드 설정 (AT+ROLE)**
   - `AT+ROLE=1`: Master 모드 설정
   - `AT+ROLE=0`: Slave 모드 설정

2. **스캔 제어 (AT+OBSERVER)**
   - `AT+OBSERVER=1,<params>`: 스캔 시작 (Observer 모드 활성화)
   - `AT+OBSERVER=0`: 스캔 중지 (Observer 모드 비활성화)

**중요**: AT+ROLE과 AT+OBSERVER는 **별개의 명령**입니다!

### AT 명령 프로토콜의 이해

#### Enable Master 시퀀스
```
Step 0: CTS 제어 (Beacon 모드 종료)
Step 1: Guard Time 1초
Step 2: +++ (AT 모드 진입)
Step 3: Guard Time 1초
Step 4: AT+ROLE=1 (Master 모드 설정)
Step 5: AT+EXIT (AT 모드 종료)
Step 6: +++ (AT 모드 재진입)
Step 7: Guard Time 1초
```

#### Start Scan 시퀀스
```
Step 1: Guard Time 1초
Step 2: +++ (AT 모드 진입)
Step 3: Guard Time 1초
Step 4: AT+OBSERVER=1,20,,,-80,, (스캔 시작)
Step 5: AT+EXIT (AT 모드 종료)
Step 6: +++ (AT 모드 재진입)
Step 7: Guard Time 1초
```

#### Stop Scan 시퀀스
```
Step 1: AT+ROLE? (현재 Role 확인)
Step 2: AT+ROLE=1 (Master 모드 재설정)
Step 3: AT+OBSERVER=0 (스캔 중지)
```

### JNI 시리얼 통신 구조
```
Kotlin → SerialPortManager → SerialPort (Java) → JNI → Native (C++) → /dev/ttyS*
```

### 에러 처리 전략
- 연속 3회 에러 시 자동 중단
- 타임아웃 1초 설정
- SELinux permissive 모드 활용

## 개발 권장사항

### 1. 타이밍 관련
- `+++` 명령 전후 1초 Guard Time 필수
- AT 명령 응답 대기 200ms
- 백그라운드 수신 주기 100ms/500ms

### 2. 디버깅
```bash
# 로그 확인
adb logcat -s AtCommandManager:D SerialPortManager:D

# 시리얼 포트 확인
adb shell ls -l /dev/ttyS*

# SELinux 확인
adb shell getenforce
```

### 3. 권한 설정
- `/dev/ttyS*` 접근 권한 필요
- SELinux permissive 모드 권장
- AndroidManifest.xml에 BLE 권한 추가

## 향후 개선 사항

### 제안된 개선 사항
1. **타이밍 최적화**: Guard Time을 설정 파일로 외부화
2. **에러 복구**: 자동 재연결 기능 추가
3. **로그 관리**: 파일 저장 기능 추가
4. **UI 개선**: 명령 히스토리 기능
5. **다중 포트**: 여러 시리얼 포트 동시 제어

### 테스트 필요 영역
- [ ] 다양한 보드레이트 테스트 (9600, 57600, 115200)
- [ ] 장시간 연속 스캔 안정성 테스트
- [ ] 메모리 누수 테스트
- [ ] 다양한 Android 버전 호환성 테스트

## AT_Terminal 앱 사용법

### 올바른 사용 순서

#### **STEP 1: Enable Master (필수 - 처음 1회 실행)**
```
버튼: [Enable Master] → "Enable" 선택

실행되는 명령:
  1. Guard Time 1초 대기
  2. +++ (AT 모드 진입)
  3. Guard Time 1초 대기
  4. AT+ROLE=1 (Master 모드 설정)
  5. AT+EXIT (AT 모드 종료)
  6. +++ (AT 모드 재진입)
  7. Guard Time 1초 대기
  8. 백그라운드 수신기 자동 시작

결과: "Master mode enabled successfully"
```

#### **STEP 2: Start Scan (스캔 시작)**
```
버튼: [Scan] → 스캔 파라미터 입력

파라미터:
  - Min RSSI: -80 (기본값, 이 값보다 강한 신호만 표시)
  - Scan Time: 20초 (자동 설정)
  - Name Filter: HEX 형식 필터 (선택사항)

실행되는 명령:
  1. 백그라운드 수신기 확인/시작
  2. Guard Time 1초 대기
  3. +++ (AT 모드 진입)
  4. Guard Time 1초 대기
  5. AT+OBSERVER=1,20,,,-80,, (스캔 시작)
  6. AT+EXIT (AT 모드 종료)
  7. +++ (AT 모드 재진입)
  8. Guard Time 1초 대기

결과: 스캔 결과가 실시간으로 터미널에 표시됨
  SCAN:MAC:FA:8D:0D:27:50:C6,
  RSSI:-50
  ADV/RSP:0201060C095246737461725F3838383838
```

#### **STEP 3: Stop Scan (스캔 중지)**
```
버튼: [Stop Scan]

실행되는 명령:
  1. AT+ROLE? (현재 Role 확인)
  2. AT+ROLE=1 (Master 모드 유지)
  3. AT+OBSERVER=0 (스캔 중지)

결과: "Scan stopped successfully"
```

### 주요 명령어 정리

| 명령어 | 기능 | 사용 위치 |
|--------|------|----------|
| `AT+ROLE=1` | Master 모드 설정 | Enable Master |
| `AT+ROLE=0` | Slave 모드 설정 | Disable Master |
| `AT+ROLE?` | 현재 Role 확인 | Stop Scan |
| `AT+OBSERVER=1,<params>` | 스캔 시작 | Start Scan |
| `AT+OBSERVER=0` | 스캔 중지 | Stop Scan |
| `+++` | AT 모드 진입/재진입 | 모든 AT 명령 전후 |
| `AT+EXIT` | AT 모드 종료 | AT 명령 실행 후 |

### 주의사항

1. **반드시 Enable Master를 먼저 실행**해야 합니다
2. **Guard Time (1초) 준수**: `+++` 명령 전후로 1초 대기 필수
3. **백그라운드 수신기**: 스캔 결과를 받으려면 백그라운드 수신기가 실행 중이어야 함 (자동 시작됨)
4. **AT 모드**: 대부분의 AT 명령은 AT 모드에서만 실행 가능

## 참고 문서

### 내부 문서
- [프로젝트 개요](docs/01-overview.md)
- [아키텍처](docs/02-architecture.md)
- [AT 워크플로우](docs/03-ble-workflow.md)

### 외부 참조
- EFR32BG22 Master-Slave Module Protocol V1.7 (docs 폴더)
- google/android-serialport-api
- AT Command Protocol Standard

## 버전 히스토리

### 2025-12-01
- **빌드 오류 수정**: ScanParams 파라미터 불일치 문제 해결
- **Stop Scan 기능 개선**: AT+STOPSCAN → AT+ROLE? / AT+ROLE=1 / AT+OBSERVER=0 시퀀스로 변경
- **스캔 결과 수신 문제 해결**: 백그라운드 수신기 자동 시작 로직 추가
- **AT 모드 진입 시퀀스 추가**: startScan()에 완전한 AT 모드 진입/종료 시퀀스 구현
- **enableMaster() 로직 수정**: AT+OBSERVER → AT+ROLE로 변경 (Master/Slave 모드 설정)
- **사용법 문서화**: 올바른 앱 사용 순서 및 명령어 정리

### 2025-11-28
- AT 명령 타이밍 문제 분석 및 해결책 제시
- 전체 문서 현행화 (7개 문서)
- claude.md 작성

## 기여자 노트

Claude AI는 다음 영역에서 기여했습니다:
- 🔍 **문제 진단**: 로그 분석을 통한 정확한 원인 파악
- 📚 **문서화**: 실제 코드 기반의 상세한 기술 문서 작성
- 💡 **기술 지원**: AT 프로토콜, JNI, 시리얼 통신 전문 지식 제공
- 🎯 **솔루션 제시**: 구체적인 코드 수정 위치와 방법 안내

---

**Last Updated**: 2025-12-01
**Claude Version**: Sonnet 4.5
**Project Status**: Active Development
