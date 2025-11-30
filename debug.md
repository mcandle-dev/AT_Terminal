# AT_Terminal 디버그 로그

이 문서는 AT_Terminal 프로젝트에서 발생하는 미해결 오류 및 디버깅 과정을 기록합니다.

---

## 🔴 미해결 오류

### [CRITICAL] SCAN 실행 시 ERROR 응답 (2025-12-01)

#### 오류 상태
- **심각도**: CRITICAL
- **발생 시점**: Scan 버튼 클릭 시
- **증상**: AT+OBSERVER 명령이 ERROR 반환, 스캔 결과 없음
- **재현율**: 100%

#### 오류 로그

```
Failed to receive AT response, ret=-4
Sending command: +++
Sent 3 bytes: +++
Sent AT command: +++
Waiting 1000ms Guard Time for AT mode entry...
Guard Time complete, AT mode should be active now
Sending command: AT+OBSERVER=1,20,,,-80,,
Sent 26 bytes: AT+OBSERVER=1,20,,,-80,,
Sent AT command: AT+OBSERVER=1,20,,,-80,,
Received 7 bytes: ERROR                           ← 명령 거부!
Failed to receive AT response, ret=-4
Failed to receive AT response, ret=-4
OBSERVER response:
```

#### 코드 위치
- **파일**: `AtCommandManager.kt`
- **함수**: `startScan()` (라인 318-415)
- **관련 파일**: `MainActivity.kt` (라인 204-233)

#### 현재 구현된 시퀀스

```kotlin
suspend fun startScan(params: ScanParams) {
    // Step 1: Guard Time 1초 대기
    delay(1000)

    // Step 2: AT 모드 진입
    sendAtCommand("+++")
    delay(1000) // Guard Time

    // Step 3: 스캔 시작
    sendAtCommand("AT+OBSERVER=1,20,,,-80,,")
    // ERROR 응답 수신!
}
```

#### 분석

##### 1. ERROR 원인 추정

**가설 1: AT 모드 상태 문제**
- `+++` 명령이 성공했는지 확인 불가 (응답 없음)
- AT 모드가 실제로 진입되지 않았을 가능성

**가설 2: 이전 상태 충돌**
- Enable Master를 먼저 실행했는지 여부 확인 필요
- Enable Master도 AT 모드로 종료 (`+++`)되므로 중복 진입 시도?

**가설 3: 명령어 형식 또는 파라미터 문제**
- `AT+OBSERVER=1,20,,,-80,,` 형식이 올바른지 확인 필요
- 빈 파라미터 (,,) 처리 문제 가능성

**가설 4: 모듈 상태**
- ROLE이 Master로 설정되지 않았을 수 있음
- 다른 선행 조건이 필요할 수 있음

##### 2. 에러 코드 분석

**ret=-4**
- SerialPort의 에러 코드
- 의미: 데이터 없음 (No data available)
- 타임아웃 또는 모듈이 응답하지 않음

#### 시도한 해결책

✅ **해결 시도 1**: AT 모드 진입 시퀀스 추가
- 결과: 실패 (여전히 ERROR)

❌ **해결 시도 2**: 백그라운드 수신기 자동 시작
- 결과: OK는 받지만 스캔 결과 없음 (별개 문제)

#### 추가 조사 필요 사항

1. **Enable Master 선행 실행 여부**
   - [ ] Enable Master를 먼저 실행했는가?
   - [ ] Enable Master가 성공했는가?
   - [ ] Enable Master 후 모듈 상태는?

2. **AT 모드 진입 확인**
   - [ ] `+++` 명령 후 실제로 AT 모드에 진입하는가?
   - [ ] AT 모드 확인 명령어가 있는가? (예: `AT` 또는 `AT+VERSION`)

3. **명령어 형식 확인**
   - [ ] EFR32BG22 매뉴얼에서 AT+OBSERVER 정확한 형식 확인
   - [ ] 빈 파라미터 처리 방법 확인
   - [ ] 예제 명령어와 비교

4. **모듈 상태 확인**
   - [ ] AT+ROLE=1 설정 후 재부팅 필요 여부
   - [ ] AT+OBSERVER 실행 전 필요한 선행 조건

5. **타이밍 문제**
   - [ ] Guard Time 1초가 충분한가?
   - [ ] AT+EXIT 후 바로 +++를 실행해도 되는가?

#### 권장 디버깅 순서

##### Phase 1: 상태 확인
```
1. Enable Master 실행
2. AT 모드 진입 테스트
   - +++ 전송
   - delay(1000)
   - AT 또는 AT+VERSION 전송 (모드 확인)
3. ROLE 확인
   - AT+ROLE? 전송
```

##### Phase 2: 명령어 검증
```
1. EFR32BG22 매뉴얼 재확인
   - AT+OBSERVER 정확한 문법
   - 파라미터 형식
   - 예제 명령어
2. 단순화된 명령어 시도
   - AT+OBSERVER=1 (최소 파라미터)
   - AT+OBSERVER=1,20 (일부 파라미터)
```

##### Phase 3: 시퀀스 재설계
```
현재 시퀀스:
  +++
  delay(1000)
  AT+OBSERVER=1,20,,,-80,,
  ERROR

대안 시퀀스 1 (AT 모드 확인 추가):
  +++
  delay(1000)
  AT (모드 확인)
  OK 확인
  AT+OBSERVER=1,20,,,-80,,

대안 시퀀스 2 (Enable Master와 통합):
  Enable Master 없이 전체 시퀀스를 한 번에:
  +++
  AT+ROLE=1
  AT+OBSERVER=1,20,,,-80,,
  AT+EXIT
  +++
```

#### 긴급 해결 방법 (임시)

EFR32BG22 매뉴얼 확인 후:

**옵션 A**: AT Command 직접 입력 기능 사용
```
1. "AT Command" 버튼 클릭
2. +++ 전송 (1초 대기)
3. AT 전송 (모드 확인)
4. AT+ROLE? 전송 (상태 확인)
5. AT+OBSERVER=1,20,,,-80,, 전송
6. 응답 확인
```

**옵션 B**: Enable Master와 통합
```kotlin
// startScan()에서 AT 모드 진입 제거
// Enable Master 실행 → 이미 AT 모드임
// 바로 AT+OBSERVER 전송
```

#### 관련 파일

- `AtCommandManager.kt:318-415` - startScan() 함수
- `MainActivity.kt:204-233` - onStartScan() 함수
- `ScanParams.kt:19-23` - toAtCommand() 함수
- `CLAUDE.md` - 전체 개발 기록

#### 참고 로그

**Enable Master 성공 시 로그** (비교용, 필요 시 추가):
```
(Enable Master 로그가 있다면 여기에 추가)
```

---

## 🟡 해결 대기 중인 문제

*(아직 없음)*

---

## ✅ 해결된 문제

### [FIXED] 빌드 오류 - ScanParams 파라미터 불일치 (2025-12-01)

**문제**:
```
error: No parameter with name 'macAddress' found
```

**해결**: `InputDialogFragment.kt:155-159` 수정
```kotlin
val params = ScanParams(
    scanTime = 20,
    minRssi = minRssi,
    nameFilter = data
)
```

**상태**: ✅ 해결 완료

---

### [FIXED] 스캔 결과 미수신 - 백그라운드 수신기 미실행 (2025-12-01)

**문제**: AT+OBSERVER 명령 후 OK만 수신, 스캔 결과 없음

**원인**: 백그라운드 수신기가 실행되지 않아 비동기 응답 미수신

**해결**: `MainActivity.kt:204-233`에 백그라운드 수신기 자동 시작 추가

**상태**: ✅ 해결 완료

---

### [FIXED] enableMaster() 잘못된 명령어 사용 (2025-12-01)

**문제**: AT+OBSERVER=0/1을 사용하여 Master 모드 설정 시도

**원인**: AT+ROLE과 AT+OBSERVER 혼동

**해결**: `AtCommandManager.kt:206-230`에서 AT+ROLE=1/0로 변경

**상태**: ✅ 해결 완료

---

## 📝 디버깅 노트

### EFR32BG22 AT 명령어 참조

#### Master/Slave 모드
```
AT+ROLE=1    : Master 모드
AT+ROLE=0    : Slave 모드
AT+ROLE?     : 현재 Role 확인
```

#### Observer 모드 (스캔)
```
AT+OBSERVER=<Enable>,<ScanTime>,<Param3>,<Param4>,<RSSI>,<Param6>,<NameFilter>
예: AT+OBSERVER=1,20,,,-60,,020106

Enable: 1=시작, 0=중지
ScanTime: 스캔 시간(초)
RSSI: 최소 신호 강도 (음수, 예: -60)
NameFilter: HEX 형식 필터
```

#### AT 모드
```
+++          : AT 모드 진입/재진입 (Guard Time 1초 필요)
AT           : AT 모드 확인 (응답: OK)
AT+EXIT      : AT 모드 종료
```

### 유용한 디버깅 명령어

```bash
# Android 디바이스 로그 확인
adb logcat -s AtCommandManager:D SerialPortManager:D

# 시리얼 포트 상태 확인
adb shell ls -l /dev/ttyS*

# 실시간 로그 필터링
adb logcat | grep -E "Sent|Received|ERROR|OBSERVER"
```

---

**Last Updated**: 2025-12-01
**Status**: 1 Critical Issue Pending Investigation
