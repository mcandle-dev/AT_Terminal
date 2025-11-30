# AT_Terminal TODO List

프로젝트의 미완료 작업 및 개선 사항을 관리합니다.

---

## 🔥 긴급 (Critical)

### 1. SCAN 실행 시 ERROR 응답 문제 해결

**우선순위**: 🔴 CRITICAL
**담당**: 미할당
**관련 파일**:
- `AtCommandManager.kt:318-415` (startScan)
- `debug.md` (상세 분석)

**문제**:
```
AT+OBSERVER=1,20,,,-80,, 명령이 ERROR 반환
스캔 결과가 전혀 나타나지 않음
```

**조사 필요 사항**:
- [ ] Enable Master를 먼저 실행했는지 확인
- [ ] AT 모드 진입 확인 (+++ 후 AT 명령으로 테스트)
- [ ] EFR32BG22 매뉴얼에서 AT+OBSERVER 정확한 형식 확인
- [ ] 빈 파라미터 (,,) 처리 방식 확인
- [ ] AT+ROLE=1 설정 후 모듈 상태 확인

**디버깅 순서**:
1. AT Command 버튼으로 수동 테스트
   ```
   +++ → AT → AT+ROLE? → AT+OBSERVER=1,20,,,-80,,
   ```
2. 각 명령의 응답 확인 및 기록
3. 매뉴얼과 비교하여 명령어 형식 검증
4. 필요시 시퀀스 재설계

**관련 문서**: `debug.md`

---

## ⚠️ 높음 (High Priority)

### 2. AT 명령어 형식 검증

**우선순위**: 🟠 HIGH
**담당**: 미할당

**작업 내용**:
- [ ] EFR32BG22 Master-Slave Module Protocol V1.7 매뉴얼 재확인
- [ ] AT+OBSERVER 명령어 정확한 문법 및 파라미터 확인
- [ ] 예제 명령어와 현재 구현 비교
- [ ] 필요시 ScanParams.toAtCommand() 수정

**참고**:
```kotlin
// 현재 구현
AT+OBSERVER=1,$scanTime,,,$minRssi,,$nameFilter
// 예: AT+OBSERVER=1,20,,,-80,,

// 매뉴얼 확인 필요:
// - 빈 파라미터 처리 방식
// - RSSI 값 형식 (음수 그대로? 절대값?)
// - NameFilter HEX 형식
```

### 3. AT 모드 진입 확인 메커니즘 추가

**우선순위**: 🟠 HIGH
**담당**: 미할당

**작업 내용**:
- [ ] +++ 명령 후 AT 모드 진입 여부 확인
- [ ] AT 또는 AT+VERSION 명령으로 모드 확인 추가
- [ ] 진입 실패 시 에러 처리 로직 구현

**제안 코드**:
```kotlin
// +++ 전송
sendAtCommand("+++")
delay(1000)

// AT 모드 확인
sendAtCommand("AT")
val response = receiveAtResponse()
if (!response.contains("OK")) {
    return AtCommandResult(
        success = false,
        errorMessage = "Failed to enter AT mode"
    )
}

// 이후 AT+OBSERVER 전송
```

### 4. Enable Master 선행 실행 강제

**우선순위**: 🟠 HIGH
**담당**: 미할당

**작업 내용**:
- [ ] Scan 버튼 클릭 시 Enable Master 실행 여부 확인
- [ ] 미실행 시 경고 메시지 표시 또는 자동 실행
- [ ] MainActivity에 상태 플래그 추가 (isMasterEnabled)

**제안 코드**:
```kotlin
// MainActivity.kt
private var isMasterEnabled = false

override fun onEnableMaster(enable: Boolean) {
    // ...
    if (result.success && enable) {
        isMasterEnabled = true
    }
}

override fun onStartScan(params: ScanParams) {
    if (!isMasterEnabled) {
        addLogToTerminal("Please enable Master mode first", LogType.ERROR)
        return
    }
    // ...
}
```

---

## 📝 보통 (Medium Priority)

### 5. 로그 저장 기능 추가

**우선순위**: 🟡 MEDIUM
**담당**: 미할당

**작업 내용**:
- [ ] 터미널 로그를 파일로 저장하는 기능 추가
- [ ] 타임스탬프 포함
- [ ] 파일 형식: 텍스트 또는 CSV

### 6. 명령 히스토리 기능

**우선순위**: 🟡 MEDIUM
**담당**: 미할당

**작업 내용**:
- [ ] 이전에 실행한 AT 명령어 저장
- [ ] AT Command 다이얼로그에 히스토리 목록 표시
- [ ] SharedPreferences에 저장

### 7. Guard Time 설정 외부화

**우선순위**: 🟡 MEDIUM
**담당**: 미할당

**작업 내용**:
- [ ] Guard Time을 설정 파일 또는 SharedPreferences로 이동
- [ ] UI에서 Guard Time 조정 가능하도록 개선
- [ ] 기본값 1000ms 유지

### 8. CTS 제어 활성화 테스트

**우선순위**: 🟡 MEDIUM
**담당**: 미할당

**작업 내용**:
- [ ] 현재 주석 처리된 CTS 제어 코드 활성화
- [ ] Beacon 모드 종료 필요 여부 확인
- [ ] 테스트 후 결과 기록

**위치**: `AtCommandManager.kt:175-186`

---

## 🔍 낮음 (Low Priority)

### 9. 에러 복구 메커니즘 개선

**우선순위**: 🟢 LOW
**담당**: 미할당

**작업 내용**:
- [ ] 연속 에러 발생 시 자동 재연결 기능
- [ ] 시리얼 포트 재초기화 로직
- [ ] 사용자에게 재시도 옵션 제공

### 10. UI/UX 개선

**우선순위**: 🟢 LOW
**담당**: 미할당

**작업 내용**:
- [ ] 버튼 상태 표시 개선 (활성화/비활성화)
- [ ] 진행 상태 표시 (ProgressBar 또는 로딩 인디케이터)
- [ ] 스캔 결과 리스트 뷰로 별도 표시

### 11. 다양한 보드레이트 지원

**우선순위**: 🟢 LOW
**담당**: 미할당

**작업 내용**:
- [ ] 9600, 57600, 115200 등 다양한 보드레이트 테스트
- [ ] UI에서 보드레이트 선택 가능하도록 개선
- [ ] 설정 화면 추가

### 12. 다중 시리얼 포트 지원

**우선순위**: 🟢 LOW
**담당**: 미할당

**작업 내용**:
- [ ] 여러 시리얼 포트 동시 제어
- [ ] 포트 선택 UI 추가
- [ ] 각 포트별 독립적인 터미널 관리

---

## ✅ 완료된 작업 (2025-12-01)

- [x] 빌드 오류 수정 - ScanParams 파라미터 불일치
- [x] Stop Scan 기능 개선 - AT+ROLE? / AT+ROLE=1 / AT+OBSERVER=0 시퀀스
- [x] 스캔 결과 수신 문제 - 백그라운드 수신기 자동 시작
- [x] AT 모드 진입 시퀀스 추가 - startScan()에 완전한 시퀀스 구현
- [x] enableMaster() 로직 수정 - AT+OBSERVER → AT+ROLE로 변경
- [x] 사용법 문서화 - CLAUDE.md에 사용 순서 추가
- [x] 디버그 문서 작성 - debug.md 생성

---

## 📊 테스트 계획

### 단위 테스트 필요 항목

- [ ] AT 명령어 전송 및 응답 수신
- [ ] Guard Time 준수 여부
- [ ] 백그라운드 수신기 동작
- [ ] 에러 핸들링 (연속 에러 감지)

### 통합 테스트 필요 항목

- [ ] Enable Master → Scan → Stop Scan 전체 플로우
- [ ] 장시간 연속 스캔 안정성
- [ ] 메모리 누수 테스트
- [ ] 다양한 Android 버전 호환성

### 성능 테스트 필요 항목

- [ ] 스캔 결과 수신 지연 시간 측정
- [ ] CPU 사용률 모니터링
- [ ] 배터리 소모량 측정

---

## 📚 문서화 작업

### 작성 필요 문서

- [ ] API 문서 (KDoc 주석 추가)
- [ ] 설치 및 배포 가이드
- [ ] 트러블슈팅 가이드
- [ ] FAQ 문서

### 업데이트 필요 문서

- [ ] README.md - 최신 기능 반영
- [ ] CHANGELOG.md - 버전별 변경사항
- [ ] CLAUDE.md - 해결된 문제 추가

---

## 🎯 마일스톤

### v1.0 (안정화 릴리스)

**목표 날짜**: TBD
**필수 작업**:
- [x] 기본 AT 명령어 기능 구현
- [ ] SCAN ERROR 문제 해결 ← **현재 블로킹**
- [ ] Enable Master 선행 실행 강제
- [ ] 안정성 테스트 완료

### v1.1 (기능 개선)

**목표 날짜**: TBD
**계획 작업**:
- [ ] 로그 저장 기능
- [ ] 명령 히스토리
- [ ] UI/UX 개선

### v2.0 (확장)

**목표 날짜**: TBD
**계획 작업**:
- [ ] 다중 포트 지원
- [ ] 고급 필터링 기능
- [ ] 자동화 스크립트 지원

---

## 📞 도움이 필요한 사항

1. **EFR32BG22 매뉴얼 확인**
   - AT+OBSERVER 명령어 정확한 스펙
   - 선행 조건 및 모듈 상태 요구사항

2. **실제 하드웨어 테스트**
   - 현재 코드로 실제 디바이스 테스트 필요
   - 로그 캡처 및 분석

3. **원인 파악**
   - ERROR 응답의 정확한 원인
   - 모듈 상태 확인 방법

---

## 📝 메모

### 2025-12-01
- SCAN ERROR 문제로 인해 핵심 기능 사용 불가
- debug.md에 상세 분석 기록
- 다음 디버깅 세션에서 우선 해결 필요

### 참고 링크
- [CLAUDE.md](CLAUDE.md) - 전체 개발 기록
- [debug.md](debug.md) - 미해결 오류 상세 분석
- [docs/](docs/) - 기술 문서

---

**Last Updated**: 2025-12-01
**Total Tasks**: 24
**Completed**: 7
**In Progress**: 0
**Pending**: 17
**Critical Issues**: 1
