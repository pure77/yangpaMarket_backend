# 양파마켓 DB 스키마 구조

## 개요

이 문서는 양파마켓 MVP 실시간 경매 플랫폼의 MySQL 스키마 구조를 정리한 문서입니다.

스키마는 크게 아래 4개 도메인으로 나뉩니다.

1. 회원 및 약관 관리
2. 경매, 이미지, 입찰 흐름
3. 주문 및 결제 흐름
4. 보조 감사 및 관심 데이터

이 데이터베이스는 식별자를 두 층으로 나눠 사용합니다.

- `id`: 내부 조인과 인덱스를 위한 숫자형 기본 키
- `public_id`: 외부 API 응답에 노출하기 위한 문자열 식별자

## 도메인 요약

### 1. 회원 및 약관 관리

- `users`
  - 회원 계정의 기본 정보를 저장합니다.
  - 계정 상태, 관리자 여부, 마케팅 수신 동의, 프로필 이미지 URL을 포함합니다.
  - 한 사용자는 판매자, 입찰자, 구매자, 낙찰자, 관리자 역할을 모두 가질 수 있습니다.

- `user_terms_agreements`
  - 회원별 약관 동의 정보를 저장합니다.
  - 각 사용자는 약관 코드별로 1개의 동의 레코드를 가집니다.
  - 회원가입 시 필수 약관 검증과 추후 감사 이력 확인에 사용됩니다.

### 2. 경매, 이미지, 입찰 흐름

- `images`
  - 업로드된 이미지의 메타데이터를 저장합니다.
  - 업로더, 저장 위치, 파일 URL, 콘텐츠 타입, 이미지 크기, 업로드 상태를 관리합니다.
  - 이미지는 먼저 업로드되고, 이후 경매에 연결됩니다.

- `auctions`
  - 경매 도메인의 중심 테이블입니다.
  - 판매자, 현재 낙찰자, 현재 최고 입찰, 대표 이미지, 카테고리, 상품 상태, 가격 정보를 저장합니다.
  - MVP 전체 상태 흐름을 지원합니다.
    - `ACTIVE`
    - `PAYMENT_PENDING`
    - `PAID`
    - `ENDED`
    - `CANCELLED`
  - `minimum_bid_increment`, `payment_pending_expires_at`, `version` 컬럼이 있어 입찰 검증과 상태 갱신을 안전하게 처리할 수 있습니다.

- `auction_images`
  - 경매와 이미지를 연결하는 매핑 테이블입니다.
  - 한 경매에 여러 이미지를 붙일 수 있고, `sort_order`로 노출 순서를 관리합니다.

- `bids`
  - 경매 입찰 이력을 저장합니다.
  - 입찰자, 입찰 금액, 생성 시각, 최종 낙찰 입찰 여부를 기록합니다.
  - 현재 상태 값은 `auctions`가 들고 있고, `bids`는 이벤트 이력 역할을 맡습니다.

### 3. 주문 및 결제 흐름

- `orders`
  - 낙찰 또는 즉시구매 이후 생성되는 결제 대상 주문입니다.
  - MVP에서는 경매 1건당 최대 1개의 주문만 생성됩니다.
  - 구매자, 판매자, 연결된 낙찰 입찰, 주문 상태, 금액, 수수료, 만료 시간을 저장합니다.
  - 아래 상태를 지원합니다.
    - `PENDING_PAYMENT`
    - `PAID`
    - `EXPIRED`
    - `CANCELLED`

- `payments`
  - 실제 결제 시도 및 최종 결제 결과를 저장합니다.
  - 사용자 조회 성능을 위해 `orders`와 `auctions` 양쪽에 연결됩니다.
  - Toss 결제 연동에 필요한 `provider_order_id`, `payment_key`, 영수증 URL, 실패 코드, 원본 요청/응답 JSON을 포함합니다.
  - 아래 상태를 지원합니다.
    - `READY`
    - `CONFIRMED`
    - `FAILED`
    - `CANCELLED`

- `payment_webhook_events`
  - 웹훅 이벤트를 중복 없이 처리하기 위한 저장소입니다.
  - `(provider, provider_event_id)` 유니크 제약으로 동일 이벤트 재처리를 막습니다.

### 4. 보조 감사 및 관심 데이터

- `wishlist`
  - 사용자와 경매의 관심 등록 관계를 저장합니다.
  - 관심목록 기능과 `wishlistCount` 집계에 사용됩니다.

- `auction_status_histories`
  - 경매 상태 변경 이력을 저장합니다.
  - 관리자 조작, 결제 대기 전환, 종료 처리 같은 상태 변화를 감사 로그로 남길 때 사용합니다.

## 테이블별 구조

### `users`

목적:
- 플랫폼 활동의 기준이 되는 회원 계정 테이블

주요 컬럼:
- `public_id`
- `email`
- `password_hash`
- `nickname`
- `phone`
- `is_admin`
- `status`
- `marketing_opt_in`

중요 제약조건:
- 이메일 유니크
- 전화번호 유니크
- 외부 공개 ID 유니크

### `user_terms_agreements`

목적:
- 회원별 약관 동의 여부와 동의 시점을 기록

주요 컬럼:
- `user_id`
- `term_code`
- `is_required`
- `agreed`
- `agreed_at`
- `revoked_at`

중요 제약조건:
- `(user_id, term_code)` 유니크

### `images`

목적:
- 경매에 연결되기 전후의 이미지 메타데이터 저장

주요 컬럼:
- `uploader_user_id`
- `storage_provider`
- `object_key`
- `file_url`
- `content_type`
- `file_size_bytes`
- `upload_status`

중요 제약조건:
- `public_id` 유니크
- `object_key` 유니크

### `auctions`

목적:
- 현재 진행 중인 경매 상태를 저장하는 핵심 테이블

주요 컬럼:
- `seller_user_id`
- `winner_user_id`
- `highest_bid_id`
- `primary_image_id`
- `title`
- `description`
- `category`
- `item_condition`
- `status`
- `start_price`
- `current_price`
- `minimum_bid_increment`
- `buy_now_price`
- `bid_count`
- `end_at`
- `payment_pending_expires_at`

중요 제약조건:
- `current_price >= start_price`
- `minimum_bid_increment > 0`
- `buy_now_price IS NULL OR buy_now_price > start_price`

중요 인덱스:
- `(status, end_at)`: 종료 워커 및 진행 중 경매 조회
- `(seller_user_id, status, created_at)`: 마이페이지 판매 목록 조회
- `(category, status, end_at)`: 카테고리 필터 및 진행 상태 조회
- `FULLTEXT(title, description)`: 검색 기능

### `auction_images`

목적:
- 업로드된 여러 이미지를 특정 경매에 연결

주요 컬럼:
- `auction_id`
- `image_id`
- `sort_order`

중요 제약조건:
- `(auction_id, image_id)` 유니크
- `(auction_id, sort_order)` 유니크

### `bids`

목적:
- 경매별 입찰 이벤트 기록

주요 컬럼:
- `auction_id`
- `bidder_user_id`
- `amount`
- `is_winning_bid`
- `created_at`

중요 제약조건:
- `amount > 0`

중요 인덱스:
- `(auction_id, created_at DESC)`
- `(auction_id, amount DESC, created_at DESC)`
- `(bidder_user_id, created_at DESC)`

### `orders`

목적:
- 낙찰 또는 즉시구매 이후 생성되는 결제 대상 주문 저장

주요 컬럼:
- `auction_id`
- `buyer_user_id`
- `seller_user_id`
- `bid_id`
- `order_number`
- `order_type`
- `status`
- `amount`
- `fee_amount`
- `total_amount`
- `expires_at`

중요 제약조건:
- `order_number` 유니크
- `auction_id` 유니크
- `total_amount = amount + fee_amount`

### `payments`

목적:
- 결제 시도와 최종 승인 상태 저장

주요 컬럼:
- `order_id`
- `auction_id`
- `buyer_user_id`
- `provider`
- `payment_method`
- `status`
- `provider_order_id`
- `payment_key`
- `requested_amount`
- `approved_amount`
- `receipt_url`
- `failure_code`
- `raw_request_json`
- `raw_response_json`

중요 제약조건:
- `public_id` 유니크
- `(provider, payment_key)` 유니크

### `payment_webhook_events`

목적:
- 중복 없는 웹훅 이벤트 처리 저장소

주요 컬럼:
- `provider`
- `provider_event_id`
- `event_type`
- `payment_key`
- `payload`
- `processed`
- `processed_at`

중요 제약조건:
- `(provider, provider_event_id)` 유니크

### `wishlist`

목적:
- 사용자의 관심 경매 저장

주요 컬럼:
- `user_id`
- `auction_id`
- `created_at`

중요 제약조건:
- 기본 키 `(user_id, auction_id)`

### `auction_status_histories`

목적:
- 경매 상태 변경 감사 로그 저장

주요 컬럼:
- `auction_id`
- `from_status`
- `to_status`
- `changed_by_user_id`
- `reason`
- `created_at`

## 관계 요약

### 사용자 중심 관계

- `users 1:N user_terms_agreements`
- `users 1:N images`
- `users 1:N auctions` 판매자 기준
- `users 1:N auctions` 낙찰자 기준
- `users 1:N bids`
- `users 1:N orders` 구매자 기준
- `users 1:N orders` 판매자 기준
- `users 1:N payments`
- `users N:M auctions` through `wishlist`

### 경매 중심 관계

- `auctions 1:N auction_images`
- `auctions 1:N bids`
- `auctions 1:1 orders`
- `auctions 1:N payments`
- `auctions 1:N auction_status_histories`
- `auctions N:1 users` 판매자 기준
- `auctions N:1 users` 낙찰자 기준
- `auctions N:1 bids` 현재 최고 입찰 기준
- `auctions N:1 images` 대표 이미지 기준

### 이미지 중심 관계

- `images 1:N auction_images`
- `images N:1 users` 업로더 기준

### 주문 및 결제 관계

- `orders N:1 users` 구매자 기준
- `orders N:1 users` 판매자 기준
- `orders N:1 bids` 낙찰 입찰 기준
- `payments N:1 orders`
- `payments N:1 auctions`
- `payments N:1 users` 구매자 기준

## 운영 관점 메모

### 입찰 트랜잭션 패턴

권장 백엔드 처리 순서:

1. 대상 경매 row lock
2. 경매 상태와 입찰 금액 검증
3. `bids` insert
4. `auctions.current_price`, `bid_count`, `highest_bid_id`, `version` update
5. commit

이 방식으로 `auctions`는 현재 상태의 source of truth가 되고, `bids`는 이력 스트림 역할을 유지합니다.

### 경매 종료 흐름

경매 종료 시:

1. 최고 입찰이 없으면 상태를 `ENDED`로 변경
2. 최고 입찰이 있으면 `orders` 생성 후 경매 상태를 `PAYMENT_PENDING`으로 변경
3. `auction_status_histories` 기록

### 결제 승인 흐름

결제 승인 시:

1. `payments` insert 또는 update
2. `orders.status`를 `PAID`로 변경
3. `auctions.status`를 `PAID`로 변경
4. `auction_status_histories` 기록

### 웹훅 처리 흐름

웹훅 처리 시:

1. `payment_webhook_events` insert
2. `(provider, provider_event_id)`가 이미 존재하면 즉시 중단
3. 중복이 아닐 때만 `payments`, `orders`, `auctions` 갱신

## 개발자 추천 읽기 순서

처음 이 스키마를 보는 개발자라면 아래 순서로 읽는 것을 추천합니다.

1. `users`
2. `images`
3. `auctions`
4. `bids`
5. `orders`
6. `payments`
7. `wishlist`
8. `auction_status_histories`
9. `payment_webhook_events`

이 순서는 회원가입부터 이미지 업로드, 경매 참여, 결제, 감사 이력까지 실제 사용자 흐름과 맞닿아 있습니다.
