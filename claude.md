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

### 3. 코드 분석 및 이해 지원

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

### AT 명령 프로토콜의 이해
```
Step 0: CTS 제어 (Beacon 모드 종료)
Step 1: +++ (Guard Time 1초)
Step 2: AT+OBSERVER=0/1
Step 3: AT+EXIT
Step 4: +++ (Guard Time 1초)
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

**Last Updated**: 2025-11-28
**Claude Version**: Sonnet 4.5
**Project Status**: Active Development
