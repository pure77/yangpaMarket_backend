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