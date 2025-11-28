# AT_Terminal App 분석 문서

이 폴더에는 AT_Terminal App의 상세한 분석 문서들이 포함되어 있습니다.

## 문서 구조

### 📋 [01-overview.md](01-overview.md)
- 프로젝트 개요 및 목적
- 기술 스택 및 요구사항
- 주요 특징

### 🏗️ [02-architecture.md](02-architecture.md)
- 전체 아키텍처 구조
- 컴포넌트 간 관계도
- 데이터 플로우

### 🔄 [03-ble-workflow.md](03-ble-workflow.md)
- AT 명령어 워크플로우
- BLE 스캐닝 및 연결 프로세스
- 시리얼 통신 방식

### 🧩 [04-components.md](04-components.md)
- 각 클래스별 상세 분석
- 인터페이스 및 콜백 구조
- 주요 메서드 설명

### 🛠️ [05-development-guide.md](05-development-guide.md)
- 개발 환경 설정
- 빌드 및 테스트 방법
- 코딩 컨벤션

### 📱 [06-ui-structure.md](06-ui-structure.md)
- UI 레이아웃 구조
- 터미널 인터페이스 플로우
- 다이얼로그 구조

## 빠른 시작

1. [개요 문서](01-overview.md)부터 읽어보세요
2. [아키텍처](02-architecture.md)를 통해 전체 구조를 파악하세요
3. [AT 명령어 워크플로우](03-ble-workflow.md)로 핵심 동작을 이해하세요
4. 필요에 따라 [컴포넌트](04-components.md)나 [개발 가이드](05-development-guide.md)를 참조하세요

## 업데이트 정보

- 문서 업데이트일: 2025-11-28
- 분석 대상: AT_Terminal (EFR32BG22 UART Control)
- 시리얼 라이브러리: google/android-serialport-api
