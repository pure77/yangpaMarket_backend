# Project: yanpaMarket_backend

## Overview
Spring Boot 기반 경매 백엔드 API 서버입니다.
현재 코드는 초기 스캐폴딩 단계이며, 상세 API 설계는 `docs/` 문서에 정리되어 있습니다.

## Tech Stack
- Runtime: Java 17
- Framework: Spring Boot 4.0.5
- Build: Gradle Wrapper
- Data: Spring Data JPA + MySQL Connector/J
- Realtime: Spring WebSocket (STOMP 기반 예정)
- Boilerplate: Lombok
- Test: JUnit Platform + Spring Boot Test Starters

## Project Structure
```
.
├── AGENTS.md
├── docs/
│   ├── api-spec.md
│   ├── auction-mvp-plan-and-api.md
│   └── db-structure.md
└── yanpaMarket_backend/
    ├── build.gradle
    ├── settings.gradle
    ├── gradlew
    ├── gradlew.bat
    ├── gradle/wrapper/
    └── src/
        ├── main/
        │   ├── java/com/example/yanpaMarket_backend/
        │   │   └── YanpaMarketBackendApplication.java
        │   └── resources/
        │       └── application.properties
        └── test/
            └── java/com/example/yanpaMarket_backend/
                └── YanpaMarketBackendApplicationTests.java
```

## Docs References
- `docs/api-spec.md`
  - API 엔드포인트, 요청/응답 형식, 공통 에러 코드 참조
- `docs/auction-mvp-plan-and-api.md`
  - MVP 범위, 단계별 개발 계획, 경매 도메인 API 설계 배경 참조
- `docs/db-structure.md`
  - DB 테이블 구조, 관계, 컬럼 설계 기준 참조
- 구현 시 상세 정책은 AGENTS.md에 중복 작성하지 않고 위 문서를 우선 참조

## API Conventions
- Base URL: `/api/v1` (docs 기준)
- 인증: `Authorization: Bearer {accessToken}` 헤더 사용 (docs 기준)
- 응답 포맷:
  - 성공: `{ success: true, data: {...}, message: null }`
  - 실패: `{ success: false, data: null, message: "...", code: "ERROR_CODE" }`
- 에러 코드는 HTTP 표준 상태코드와 도메인 코드(`UNAUTHORIZED`, `VALIDATION_ERROR` 등) 병행

## Code Style Rules
- [ ] Controller-Service-Repository(또는 Domain) 레이어 분리
- [ ] 예외는 전역 예외 처리(`@ControllerAdvice`)로 일관되게 관리
- [ ] 트랜잭션 경계는 `@Transactional`로 명시
- [ ] DB 접근은 JPA 파라미터 바인딩 사용 (문자열 결합 쿼리 금지)
- [ ] 민감 정보는 코드 하드코딩 금지, 환경변수/외부 설정으로 분리

## Commands
작업 디렉터리: `yanpaMarket_backend/`

- `./gradlew bootRun` (Windows: `./gradlew.bat bootRun`) - 개발 서버 실행
- `./gradlew test` (Windows: `./gradlew.bat test`) - 테스트 실행
- `./gradlew build` (Windows: `./gradlew.bat build`) - 빌드
- `./gradlew clean` (Windows: `./gradlew.bat clean`) - 빌드 산출물 정리

## Important Notes
- 민감 정보(DB 계정, 토큰 시크릿 등)는 Git에 커밋 금지
- `application.properties`에는 기본 설정만 두고, 환경별 값은 분리 권장
- 현재 코드베이스는 최소 스캐폴딩 상태이며 실제 비즈니스 API는 `docs/` 명세 기반으로 구현 예정
- `docs/*.md` 일부 파일은 인코딩이 깨져 보일 수 있으므로 UTF-8 기준으로 관리 권장
