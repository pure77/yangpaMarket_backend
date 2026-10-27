# Project: yanpaMarket_backend

## Overview
Spring Boot 기반 경매 백엔드 API 서버입니다.
카카오 OAuth 인증, 경매 CRUD + 이미지 업로드, 실시간 입찰(락 + STOMP 브로드캐스트), 경매 자동 종료,
입찰 경로 조회 캐시까지 구현되어 있습니다. 결제(토스페이먼츠)는 아직 미구현입니다.
API 명세는 `../docs/auction-project-spec.md`, 기능별 설계/계획은 `../docs/superpowers/`에 있습니다.

## Tech Stack
- Runtime: Java 25 (Gradle toolchain)
- Framework: Spring Boot 4.1.2 (webmvc, security, validation)
- Build: Gradle Wrapper
- Data: Spring Data JPA + MySQL Connector/J, Flyway 마이그레이션 (`ddl-auto=validate`)
- Auth: JWT (jjwt 0.12.6) + 카카오 OAuth
- Realtime: Spring WebSocket + STOMP (SockJS, 엔드포인트 `/ws`, 브로커 `/topic`)
- Cache: Spring Cache + Caffeine (`config/CacheConfig`)
- Storage: 로컬 디스크(`!prod`) / AWS S3(`prod`) — `image/storage/ImageStorage` 구현체로 분기
- ID: ULID 기반 publicId (`ulid-creator`)
- Boilerplate: Lombok
- Test: JUnit Platform + Spring Boot Test Starters, H2(테스트 런타임)

## Project Structure
```
.
├── AGENTS.md / CLAUDE.md
├── build.gradle, settings.gradle, gradlew, gradlew.bat
├── docs/
│   └── db-structure.md
└── src/
    ├── main/
    │   ├── java/com/example/yanpaMarket_backend/
    │   │   ├── auction/      # 경매·입찰 (controller, service, domain, dto, repository,
    │   │   │                 #   concurrency=입찰 락, scheduler=자동 종료)
    │   │   ├── auth/         # 카카오 OAuth, 토큰 발급/갱신, dev 테스트 로그인
    │   │   ├── user/         # 사용자, 사용자 조회 캐시(UserLookupService)
    │   │   ├── image/        # 이미지 업로드, storage(Local/S3)
    │   │   ├── security/     # JWT 필터/프로바이더
    │   │   ├── config/       # Security, WebSocket, Cache, WebMvc, S3, properties
    │   │   ├── global/       # ApiResponse, ApiException, ErrorCode, GlobalExceptionHandler
    │   │   └── common/       # BaseTimeEntity, PublicIdGenerator, NicknameMasker, CacheNames
    │   └── resources/
    │       ├── application.properties, application.yml
    │       ├── application-{dev,prod,secret}.yml   # secret은 git 제외
    │       └── db/migration/V1~V4__*.sql
    └── test/
        ├── java/.../auction/load/   # HotAuctionLoadTest (부하 테스트, 기본 skip)
        └── resources/application-{test,mysql}.properties
```

## Docs References
- `../docs/auction-project-spec.md`
  - 통합 명세서: MVP 기획, 6주 개발 계획, API 엔드포인트/요청·응답 형식/공통 에러 코드, 구현 참고사항 참조
  - 인증 방식은 `7-2. 인증(Auth)` 섹션 참조 (카카오 OAuth = 정본)
  - 기존 `api-spec.md` + `auction-mvp-plan-and-api.md`를 통합한 문서
- `docs/db-structure.md`
  - DB 테이블 구조, 관계, 컬럼 설계 기준 참조
- 구현 시 상세 정책은 AGENTS.md에 중복 작성하지 않고 위 문서를 우선 참조

## API Conventions
- Base URL: `/api/v1` (docs 기준)
- 인증: `Authorization: Bearer {accessToken}` 헤더 사용 (docs 기준)
- 응답 포맷:
  - 성공: `{ success: true, data: {...}, message: null }`
  - 실패: `{ success: false, data: null, message: "...", code: "ERROR_CODE" }`
  - 실패 + 부가 정보: `BID_TOO_LOW`만 `data: { currentPrice, minimumBid }`를 담는다 (`ApiResponse.failure(message, code, data)`)
- 에러 코드는 HTTP 표준 상태코드와 도메인 코드(`UNAUTHORIZED`, `VALIDATION_ERROR` 등) 병행

## Code Style Rules
- [ ] Controller-Service-Repository(또는 Domain) 레이어 분리
- [ ] 예외는 전역 예외 처리(`@ControllerAdvice`)로 일관되게 관리
- [ ] 트랜잭션 경계는 `@Transactional`로 명시
- [ ] DB 접근은 JPA 파라미터 바인딩 사용 (문자열 결합 쿼리 금지)
- [ ] 민감 정보는 코드 하드코딩 금지, 환경변수/외부 설정으로 분리

## Commands
작업 디렉터리: 이 저장소 루트 (`yangpaMarket_back/`)

- `./gradlew bootRun` (Windows: `./gradlew.bat bootRun`) - 개발 서버 실행
- `./gradlew test` (Windows: `./gradlew.bat test`) - 테스트 실행
- `./gradlew build` (Windows: `./gradlew.bat build`) - 빌드
- `./gradlew clean` (Windows: `./gradlew.bat clean`) - 빌드 산출물 정리
- 테스트 로그인 포함 실행: `./gradlew.bat bootRun --args='--spring.profiles.active=secret,dev'` (`POST /api/v1/auth/dev/login` 활성화)
- 부하 테스트: `./gradlew.bat test --tests '*HotAuctionLoadTest*' -Dloadtest=true` (파라미터는 `build.gradle`의 `load.*` 참고, `_loadtest` 접미사 DB에서만 실행)

## Important Notes
- 민감 정보(DB 계정, 토큰 시크릿 등)는 Git에 커밋 금지
- `application.properties`에는 기본 설정만 두고, 민감 값은 `application-secret.yml`(git 제외, 기본 활성 프로필)에 둔다
- dev 프로필은 테스트 로그인을 연다. 운영(prod)에서 절대 활성화하지 않는다
- DB 스키마 변경은 반드시 새 Flyway 마이그레이션(`V5__...`)으로 추가한다. 기존 V1~V4는 수정하지 않는다
- `app.auction.publish-delay-seconds` 기본값은 테스트용 10초다
- `docs/*.md` 일부 파일은 인코딩이 깨져 보일 수 있으므로 UTF-8 기준으로 관리 권장

## Rule
- 수정사항이 생겨서 코드를 수정할시 바로 수정하는게아닌 어떻게 수정할지 코드를 보여주기
- 승낙을 받은후 코드를 수정
- 코드수정이나 기능개발후 이게어떤 기능인지 어떻게돌아가는지 주석으로 핵심정리하기

---

## 하네스: yanpaMarket 백엔드 개발

**목표:** Spring Boot API 구현/수정/버그 수정을 에이전트 팀으로 자동화하되, 반드시 설계 제안 → 사용자 승낙 → 구현 순서를 지킨다.

**트리거:** 백엔드 API 구현, 엔드포인트 추가, Entity/Service/Repository 작성, 버그 수정, 보안 설정 변경 등 개발 작업 요청 시 `yangpa-backend-dev` 스킬을 사용하라. 단순 질문은 직접 응답 가능.

**변경 이력:**
| 날짜 | 변경 내용 | 대상 | 사유 |
|------|----------|------|------|
| 2026-05-30 | 초기 구성 | 전체 | - |
| 2026-06-05 | 경매 CRUD + 이미지 업로드 API 구현(Flyway V4, auction/image 도메인) | auction/image | MVP 경매 풀사이클 |
| 2026-06-23 | 실시간 입찰(Bid, BidLock, BidService, STOMP 브로드캐스트), 경매 자동 종료 스케줄러 | auction/config | 실시간 입찰 |
| 2026-09-07 | 실패 응답 data 확장, BID_TOO_LOW에 유효 입찰가 포함, 종료 배치 건별 트랜잭션, CORS 설정 분리 | global/auction/config | 입찰 거절 피드백 개선 |
| 2026-09-29 | dev 프로필 테스트 로그인, 토큰 동시 갱신 500 수정, Flyway V1~V3 추가 | auth/db | 다중 사용자 테스트 |
| 2026-10-12 | Caffeine 조회 캐시(경매 ID, 사용자 요약), 다중 경매 부하 테스트 | common/user/auction | 입찰 경로 DB 커넥션 감소 |
| 2026-10-14 | CLAUDE.md/AGENTS.md를 현재 코드 상태에 맞게 갱신 | 문서 | 문서-코드 불일치 해소 |
