# 실시간 경매 플랫폼 MVP 기획 및 API 설계

> 기술 스택: React 18 + Spring Boot 3 + MySQL 8 + Redis  
> 목표: 6주 내 경매 등록 → 실시간 입찰 → 낙찰 → 결제 전 사이클 완성

---

## 1. 프로덕트 개요

| 항목 | 내용 |
|------|------|
| 서비스명 | 경매마켓 |
| 핵심 기술 | WebSocket(STOMP) 기반 실시간 입찰 |
| 목표 동시 접속 | 100명 |
| 입찰 지연시간 | 1초 미만 |
| 결제 | 토스페이먼츠 연동 |

### 사용자 역할

| 역할 | 권한 |
|------|------|
| 구매자 | 경매 목록 조회, 입찰, 즉시구매, 결제 |
| 판매자 | 물품 등록, 경매 조건 설정, 낙찰 확인 |
| 관리자 | 경매 검수, 부정 입찰 차단, 강제 종료 |

---

## 2. 기술 스택

### 프론트엔드
- React 18 + Vite
- Zustand (상태관리)
- socket.io-client (WebSocket)
- Tailwind CSS

### 백엔드
- Spring Boot 3
- Spring Security + JWT
- Spring WebSocket (STOMP)
- JPA + QueryDSL
- Redis (입찰 동시성 처리, 토큰 관리)

### 인프라 / 외부 서비스
- MySQL 8
- AWS S3 (이미지 저장)
- 토스페이먼츠 (결제)
- Docker Compose (로컬 개발)
- Vercel / Railway (배포)

---

## 3. 핵심 기능

| 기능 | 설명 |
|------|------|
| 회원 관리 | 회원가입 / 로그인 / JWT 인증 |
| 경매 등록 | 물품 사진·설명 등록, 시작가·즉시구매가·마감시간 설정 |
| 실시간 입찰 | WebSocket STOMP 기반 입찰가 실시간 반영 |
| 경매 종료 처리 | 카운트다운 타이머, 자동 낙찰 처리 |
| 검색 / 필터 | 카테고리 필터, 키워드 검색, 진행 중 / 종료 탭 |
| 결제 | 토스페이먼츠 연동, 낙찰 후 결제 완료까지 |

---

## 4. 6주 개발 로드맵

### 1주차 — 기반 세팅

| 일자 | 작업 |
|------|------|
| 1~2일 | 프로젝트 구조 설정, MySQL 스키마 설계, Docker Compose 구성 |
| 3일 | 회원가입 / 로그인 API, JWT 인증, Spring Security 설정 |
| 4일 | 경매 등록 CRUD API, S3 이미지 업로드 |
| 5~7일 | 경매 목록 / 상세 조회 API, 프론트 목록 화면 |

### 2주차 — 실시간 입찰 엔진

| 일자 | 작업 |
|------|------|
| 8~9일 | WebSocket STOMP 서버 구성, Redis pub/sub 연결 |
| 10일 | 입찰 API + 동시성 처리 (Redis SETNX + Lua 스크립트) |
| 11~12일 | 프론트 실시간 입찰 화면 (타이머, 입찰 내역 실시간 반영) |
| 13~14일 | 통합 테스트, 버그 수정 |

### 3주차 — 낙찰 처리 + 관심목록

| 일자 | 작업 |
|------|------|
| 15~16일 | `@Scheduled` 경매 자동 종료, 낙찰자 선정 로직 |
| 17일 | 낙찰 이메일 알림 (JavaMailSender) |
| 18~19일 | 관심목록 추가 / 조회 API + 프론트 |
| 20~21일 | 즉시구매 기능, 경매 상태 전환 테스트 |

### 4주차 — 결제 연동

| 일자 | 작업 |
|------|------|
| 22~23일 | 토스페이먼츠 결제 요청 / 승인 API |
| 24일 | 결제 완료 상태 처리, 웹훅 수신 (ngrok 활용) |
| 25~26일 | 결제 화면 프론트 구현 |
| 27~28일 | 결제 내역 조회, 엣지 케이스 처리 |

### 5주차 — 마이페이지 + 관리자

| 일자 | 작업 |
|------|------|
| 29~30일 | 마이페이지 (등록 경매, 입찰 내역, 결제 내역) |
| 31~32일 | 관리자 경매 목록 조회, 강제 상태변경 API |
| 33~35일 | 공통 에러 처리, 응답 포맷 통일, API 문서화 (Swagger) |

### 6주차 — QA + 배포

| 일자 | 작업 |
|------|------|
| 36~37일 | 전체 기능 통합 테스트 |
| 38~39일 | 성능 테스트 (동시 입찰 시뮬레이션) |
| 40~42일 | EC2 / Railway 프로덕션 배포, 최종 버그 수정 |

---

## 5. MVP 제외 항목 (v2 이후)

- 소셜 로그인 (카카오)
- 실시간 채팅
- 추천 알고리즘
- 모바일 앱 (React Native)
- 수수료 정산 시스템
- 분쟁 처리 시스템

---

## 6. 리스크 및 대비책

| 리스크 | 대비책 |
|--------|--------|
| 동시 입찰 동시성 버그 | Redis SETNX + Lua 스크립트로 원자적 처리. JPA 낙관적 락 사용 금지 |
| WebSocket + JWT 인증 복잡도 | STOMP Handshake 인터셉터에서 쿼리 파라미터로 토큰 처리 |
| 토스페이먼츠 연동 지연 | 공식 Java SDK + 샘플 코드 사전 검토. ngrok으로 로컬 웹훅 수신 |
| 일정 초과 시 컷 기준 | 마이페이지 → 이메일 알림 → 관리자 순으로 제거. 입찰 실시간 + 결제는 유지 |

---

---

# API 설계 명세

> Base URL: `/api/v1`  
> 인증: `Authorization: Bearer {accessToken}` 헤더  
> 응답 형식: `Content-Type: application/json`

---

## 공통 응답 형식

### 성공
```json
{
  "success": true,
  "data": { },
  "message": null
}
```

### 실패
```json
{
  "success": false,
  "data": null,
  "message": "에러 메시지",
  "code": "ERROR_CODE"
}
```

### 공통 에러 코드

| 코드 | HTTP Status | 설명 |
|------|-------------|------|
| `UNAUTHORIZED` | 401 | 인증 필요 |
| `FORBIDDEN` | 403 | 권한 없음 |
| `NOT_FOUND` | 404 | 리소스 없음 |
| `VALIDATION_ERROR` | 400 | 요청값 오류 |
| `BID_TOO_LOW` | 400 | 입찰가 부족 |
| `AUCTION_ENDED` | 400 | 종료된 경매 |
| `ALREADY_BIDDING` | 409 | 동시 입찰 충돌 |

---

## 🔐 인증 (Auth)

### POST `/auth/signup` — 회원가입

**Request Body**
```json
{
  "email": "string",
  "password": "string (min 8자, 영문+숫자)",
  "nickname": "string (max 20자)",
  "phone": "string (010-0000-0000)"
}
```

**Response `201`**
```json
{
  "userId": 1,
  "email": "user@example.com",
  "nickname": "홍길동"
}
```

> 비밀번호는 BCrypt 암호화 후 저장

---

### POST `/auth/login` — 로그인

**Request Body**
```json
{
  "email": "string",
  "password": "string"
}
```

**Response `200`**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "expiresIn": 3600
}
```

> Access Token 유효기간: 1시간 / Refresh Token: 7일 (Redis 저장)

---

### POST `/auth/refresh` — 토큰 갱신

**Request Body**
```json
{
  "refreshToken": "string"
}
```

**Response `200`**
```json
{
  "accessToken": "eyJ...",
  "expiresIn": 3600
}
```

---

### POST `/auth/logout` — 로그아웃 `🔒 인증필요`

**Response `200`**
```json
{
  "message": "로그아웃 완료"
}
```

> Redis에서 Refresh Token 삭제 처리

---

## 👤 사용자 (User)

### GET `/users/me` — 내 프로필 조회 `🔒 인증필요`

**Response `200`**
```json
{
  "userId": 1,
  "email": "user@example.com",
  "nickname": "홍길동",
  "phone": "010-1234-5678",
  "profileImage": "https://s3.../profile.jpg",
  "createdAt": "2024-01-01T00:00:00"
}
```

---

### PUT `/users/me` — 내 프로필 수정 `🔒 인증필요`

**Request Body**
```json
{
  "nickname": "string",
  "profileImage": "string (S3 URL)"
}
```

**Response `200`**
```json
{
  "userId": 1,
  "nickname": "수정된닉네임",
  "profileImage": "https://s3.../new-profile.jpg"
}
```

---

## 📦 경매 (Auction)

### GET `/auctions` — 경매 목록 조회

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `category` | string | N | 전자기기, 패션, 생활/가전, 수집품, 스포츠 |
| `status` | string | N | `ACTIVE` / `ENDED` / `CANCELLED` (기본: ACTIVE) |
| `keyword` | string | N | 제목 검색어 |
| `page` | int | N | 페이지 번호 (기본: 0) |
| `size` | int | N | 페이지 크기 (기본: 20) |
| `sort` | string | N | `endTime,asc` / `currentPrice,desc` / `bidCount,desc` |

**Response `200`**
```json
{
  "content": [
    {
      "auctionId": 1,
      "title": "아이폰 15 Pro 256GB",
      "currentPrice": 50000,
      "buyNowPrice": 200000,
      "bidCount": 5,
      "endTime": "2024-01-10T18:00:00",
      "status": "ACTIVE",
      "thumbnailUrl": "https://s3.../thumb.jpg",
      "category": "전자기기",
      "wishCount": 12
    }
  ],
  "totalPages": 10,
  "totalElements": 200,
  "currentPage": 0
}
```

---

### GET `/auctions/{auctionId}` — 경매 상세 조회

**Response `200`**
```json
{
  "auctionId": 1,
  "title": "아이폰 15 Pro 256GB",
  "description": "상품 설명 텍스트",
  "images": [
    "https://s3.../image1.jpg",
    "https://s3.../image2.jpg"
  ],
  "startPrice": 10000,
  "currentPrice": 55000,
  "buyNowPrice": 200000,
  "bidCount": 7,
  "endTime": "2024-01-10T18:00:00",
  "status": "ACTIVE",
  "category": "전자기기",
  "condition": "거의새것",
  "wishCount": 15,
  "isWished": false,
  "seller": {
    "userId": 2,
    "nickname": "판매왕",
    "profileImage": "https://s3.../seller.jpg"
  }
}
```

---

### POST `/auctions` — 경매 등록 `🔒 인증필요`

**Request Body**
```json
{
  "title": "아이폰 15 Pro 256GB",
  "description": "상품 설명",
  "category": "전자기기",
  "condition": "거의새것",
  "startPrice": 10000,
  "buyNowPrice": 200000,
  "endTime": "2024-01-10T18:00:00",
  "imageIds": [1, 2, 3]
}
```

> `imageIds`: `POST /images/upload` 먼저 호출 후 반환된 ID 목록 전달

**Response `201`**
```json
{
  "auctionId": 1,
  "title": "아이폰 15 Pro 256GB",
  "status": "ACTIVE",
  "createdAt": "2024-01-09T10:00:00"
}
```

---

### PUT `/auctions/{auctionId}` — 경매 수정 `🔒 인증필요`

**Request Body**
```json
{
  "title": "string",
  "description": "string",
  "buyNowPrice": 200000
}
```

**Response `200`**
```json
{
  "auctionId": 1,
  "title": "수정된 제목"
}
```

> 입찰자가 없을 때만 수정 가능. 입찰자 있으면 `400 CANNOT_MODIFY` 반환

---

### DELETE `/auctions/{auctionId}` — 경매 취소 `🔒 인증필요`

**Response `200`**
```json
{
  "message": "경매가 취소되었습니다"
}
```

> 입찰자가 없을 때만 취소 가능

---

## ⚡ 입찰 (Bid)

### POST `/auctions/{auctionId}/bids` — 입찰 `🔒 인증필요` `🔴 Redis`

**Request Body**
```json
{
  "price": 60000
}
```

**Response `201`**
```json
{
  "bidId": 1,
  "price": 60000,
  "bidderId": 3,
  "currentHighestPrice": 60000,
  "bidCount": 8,
  "createdAt": "2024-01-09T12:00:00"
}
```

**에러 케이스**

| 상황 | HTTP | 코드 |
|------|------|------|
| 현재가보다 낮은 입찰 | 400 | `BID_TOO_LOW` |
| 종료된 경매 | 400 | `AUCTION_ENDED` |
| 동시 입찰 충돌 | 409 | `ALREADY_BIDDING` |
| 본인 경매에 입찰 | 400 | `SELF_BID_NOT_ALLOWED` |

> Redis `SETNX` + Lua 스크립트로 동시성 처리. TTL 3초로 락 설정

---

### GET `/auctions/{auctionId}/bids` — 입찰 내역 조회

**Query Parameters**: `?page=0&size=10`

**Response `200`**
```json
{
  "content": [
    {
      "bidId": 1,
      "maskedNickname": "홍**",
      "price": 60000,
      "createdAt": "2024-01-09T12:00:00",
      "isHighest": true
    }
  ],
  "totalElements": 8
}
```

---

### POST `/auctions/{auctionId}/buy-now` — 즉시구매 `🔒 인증필요`

**Response `200`**
```json
{
  "orderId": 1,
  "price": 200000,
  "status": "PAYMENT_PENDING",
  "expiredAt": "2024-01-09T12:30:00"
}
```

> 경매 즉시 종료 → 결제 대기 상태로 전환. 30분 내 미결제 시 자동 취소

---

### WS `/ws/auction/{auctionId}` — 실시간 입찰 구독 `🔒 인증필요` `🟢 WebSocket`

**연결 방법 (STOMP)**
```
CONNECT
Authorization: Bearer {accessToken}

SUBSCRIBE
/topic/auction/{auctionId}
```

**서버 → 클라이언트 메시지**

입찰 발생 시:
```json
{
  "type": "BID_UPDATE",
  "currentPrice": 60000,
  "bidCount": 8,
  "remainingTime": 3540,
  "maskedBidder": "홍**"
}
```

경매 종료 시:
```json
{
  "type": "AUCTION_ENDED",
  "finalPrice": 60000,
  "winnerId": 3
}
```

> STOMP 프로토콜 사용. 새 입찰 발생 시 전체 구독자에게 broadcast

---

## ❤️ 관심목록 (Wishlist)

### POST `/auctions/{auctionId}/wish` — 관심 추가/제거 (토글) `🔒 인증필요`

**Response `200`**
```json
{
  "wished": true,
  "wishCount": 12
}
```

> 이미 추가된 경우 제거, 없으면 추가 (토글)

---

### GET `/users/me/wishes` — 내 관심목록 조회 `🔒 인증필요`

**Query Parameters**: `?page=0&size=20`

**Response `200`**
```json
{
  "content": [
    {
      "auctionId": 1,
      "title": "아이폰 15 Pro",
      "currentPrice": 55000,
      "endTime": "2024-01-10T18:00:00",
      "status": "ACTIVE",
      "thumbnailUrl": "https://s3.../thumb.jpg"
    }
  ],
  "totalElements": 5
}
```

---

## 💳 결제 (Payment)

### POST `/payments/request` — 결제 요청 `🔒 인증필요`

**Request Body**
```json
{
  "orderId": 1,
  "paymentMethod": "CARD"
}
```

**Response `200`**
```json
{
  "paymentKey": "string",
  "orderId": "auction_1_1704768000",
  "amount": 60000,
  "successUrl": "https://yourapp.com/payment/success",
  "failUrl": "https://yourapp.com/payment/fail"
}
```

> 클라이언트가 토스페이먼츠 결제창 호출에 필요한 파라미터 반환

---

### POST `/payments/confirm` — 결제 승인 `🔒 인증필요`

**Request Body**
```json
{
  "paymentKey": "string",
  "orderId": "auction_1_1704768000",
  "amount": 60000
}
```

**Response `200`**
```json
{
  "paymentId": 1,
  "status": "DONE",
  "method": "카드",
  "approvedAt": "2024-01-09T12:00:00",
  "receipt": "https://dashboard.tosspayments.com/..."
}
```

> 서버에서 토스페이먼츠 `/v1/payments/confirm` 호출 후 결과 반환. 클라이언트 직접 호출 금지

---

### GET `/payments/history` — 결제 내역 조회 `🔒 인증필요`

**Query Parameters**: `?page=0&size=10`

**Response `200`**
```json
{
  "content": [
    {
      "paymentId": 1,
      "auctionTitle": "아이폰 15 Pro",
      "amount": 60000,
      "status": "DONE",
      "method": "카드",
      "approvedAt": "2024-01-09T12:00:00"
    }
  ],
  "totalElements": 3
}
```

---

## 🖼️ 이미지 (Image)

### POST `/images/upload` — 이미지 업로드 `🔒 인증필요`

**Request**: `multipart/form-data`

| 필드 | 타입 | 설명 |
|------|------|------|
| `file` | File | 이미지 파일 (최대 10MB, jpg/png/webp) |

**Response `201`**
```json
{
  "imageId": 1,
  "url": "https://s3.ap-northeast-2.amazonaws.com/bucket/image.jpg"
}
```

> 경매 등록 전에 먼저 업로드. 반환된 `imageId`를 `POST /auctions`의 `imageIds`에 전달

---

## 🛠️ 관리자 (Admin)

> 모든 관리자 API는 `ROLE_ADMIN` 권한 필요

### GET `/admin/auctions` — 전체 경매 관리 `🔒 관리자`

**Query Parameters**: `?status=ALL&page=0&size=20`

**Response `200`**
```json
{
  "content": [
    {
      "auctionId": 1,
      "title": "string",
      "status": "ACTIVE",
      "bidCount": 5,
      "reportCount": 2,
      "seller": { "userId": 2, "nickname": "판매자" },
      "createdAt": "2024-01-09T10:00:00"
    }
  ],
  "totalElements": 100
}
```

---

### PATCH `/admin/auctions/{auctionId}/status` — 경매 강제 상태변경 `🔒 관리자`

**Request Body**
```json
{
  "status": "CANCELLED",
  "reason": "부정 경매 감지"
}
```

**Response `200`**
```json
{
  "auctionId": 1,
  "status": "CANCELLED",
  "reason": "부정 경매 감지"
}
```

---

## 7. 주요 구현 참고사항

### Redis 동시성 처리 (입찰)

```java
// 입찰 락 키: auction:{auctionId}:lock
// TTL: 3초
// Redis SETNX + Lua 스크립트로 원자적 처리

String lockKey = "auction:" + auctionId + ":lock";
Boolean acquired = redisTemplate.opsForValue()
    .setIfAbsent(lockKey, userId.toString(), 3, TimeUnit.SECONDS);
if (!acquired) throw new AuctionException(ALREADY_BIDDING);
```

### WebSocket STOMP 인증

```java
// HandshakeInterceptor에서 JWT 파싱
// 연결 URL: ws://host/ws?token={accessToken}
@Override
public boolean beforeHandshake(...) {
    String token = request.getServletRequest().getParameter("token");
    // JWT 검증 후 Principal 설정
}
```

### 경매 자동 종료 스케줄러

```java
@Scheduled(fixedDelay = 10000) // 10초마다 체크
public void closeExpiredAuctions() {
    List<Auction> expired = auctionRepository
        .findByStatusAndEndTimeBefore(ACTIVE, LocalDateTime.now());
    expired.forEach(this::processAuctionEnd);
}
```

### 토스페이먼츠 결제 승인 (서버 호출)

```java
// 반드시 서버에서 호출 — 클라이언트 직접 호출 금지
HttpHeaders headers = new HttpHeaders();
headers.set("Authorization", "Basic " + Base64.encode(secretKey + ":"));
// POST https://api.tosspayments.com/v1/payments/confirm
```

---

*문서 버전: v1.0 | 작성일: 2026-04-16*
