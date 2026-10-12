package com.example.yanpaMarket_backend.auction.domain; // auction.domain = 경매 도메인 패키지

import com.example.yanpaMarket_backend.auction.dto.BidTooLowData;
import com.example.yanpaMarket_backend.common.domain.BaseTimeEntity;
import com.example.yanpaMarket_backend.global.error.ApiException;
import com.example.yanpaMarket_backend.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 경매 애그리거트 엔티티.
 *
 * [설계 원칙]
 * - raw Long FK 컬럼(seller_user_id 등)을 사용하여 auth 도메인과 물리적 결합을 피한다.
 * - 낙관적 잠금(@Version)으로 동시 입찰 충돌을 방지한다.
 * - public_id(ULID 26자)를 외부 노출 식별자로, id(PK)를 내부 식별자로 분리한다.
 *
 * [핵심 비즈니스 규칙]
 * - isLive(now): status==ACTIVE && start_at <= now
 *   → start_at을 5분 뒤로 설정해 "5분 공개 유예" 구현
 * - isModifiable(): status==ACTIVE && bid_count==0
 *   → 입찰자 없을 때만 수정/삭제 허용
 * - cancel(reason): 삭제 = 소프트 취소 (status→CANCELLED, 사유/시각 기록)
 * - update(...): 시작가 변경 시 현재가를 시작가로 재설정
 *
 * [연결]
 * - BaseTimeEntity 상속 → 생성/수정 시각 자동 기록.
 * - AuctionRepository 로 조회/저장, AuctionService 가 비즈니스 규칙을 호출.
 * - 응답 시 AuctionSummaryResponse/AuctionDetailResponse 로 변환됨.
 * - isModifiable() 위반 시 서비스에서 ErrorCode.CANNOT_MODIFY 예외로 이어짐.
 */
@Getter
@Entity
@Table(
        name = "auctions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_auctions_public_id", columnNames = "public_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Auction extends BaseTimeEntity {

    /** 내부 PK (외부 노출 금지) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 외부 노출 식별자 — ULID 26자 */
    @Column(name = "public_id", nullable = false, length = 26)
    private String publicId;

    /** 판매자 User PK (raw FK, auth 도메인과 물리 조인 없음) */
    @Column(name = "seller_user_id", nullable = false)
    private Long sellerUserId;

    /** 대표 이미지 Image PK (null 허용) */
    @Column(name = "primary_image_id")
    private Long primaryImageId;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    /** 카테고리 (ELECTRONICS, FASHION, ...) */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private AuctionCategory category;

    /** 상품 상태 (NEW, LIKE_NEW, USED, ...) */
    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition", nullable = false, length = 10)
    private AuctionItemCondition itemCondition;

    /** 경매 진행 상태 (ACTIVE / ENDED / CANCELLED) */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AuctionStatus status;

    /** 등록 시 설정한 시작가 */
    @Column(name = "start_price", nullable = false)
    private Long startPrice;

    /**
     * 현재 최고 입찰가.
     * 생성 시에는 시작가와 동일하며, 입찰이 들어올 때마다 갱신된다.
     */
    @Column(name = "current_price", nullable = false)
    private Long currentPrice;

    /** 즉시 구매 가격 (null = 즉시구매 불가) */
    @Column(name = "buy_now_price")
    private Long buyNowPrice;

    /** 현재까지 접수된 입찰 건수 */
    @Column(name = "bid_count", nullable = false)
    private Integer bidCount;

    /** 경매 공개(시작) 시각 — 이 시각 이후부터 isLive() == true */
    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    /** 경매 마감 시각 */
    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    /** 취소 처리된 시각 (소프트 삭제) */
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /** 취소 사유 */
    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    /** 최소 입찰 인상폭 (DB 기본 10000). 입찰가는 현재가 + 이 값 이상이어야 한다. */
    @Column(name = "minimum_bid_increment", nullable = false)
    private Long minimumBidIncrement;

    /** 낙찰자 User PK (종료 시 입찰자 있으면 기록). */
    @Column(name = "winner_user_id")
    private Long winnerUserId;

    /** 현재 최고 입찰 Bid PK (입찰마다 갱신). */
    @Column(name = "highest_bid_id")
    private Long highestBidId;

    /** 경매 종료 처리 시각. */
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /**
     * 낙관적 잠금 버전.
     * 동시 입찰 시 충돌 감지 — 같은 버전을 동시에 수정하면 OptimisticLockException 발생.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    /**
     * 경매 생성 빌더.
     * - currentPrice = startPrice (등록 시 현재가 = 시작가)
     * - bidCount = 0
     * - status = ACTIVE
     */
    @Builder
    public Auction(
            String publicId,
            Long sellerUserId,
            Long primaryImageId,
            String title,
            String description,
            AuctionCategory category,
            AuctionItemCondition itemCondition,
            Long startPrice,
            Long buyNowPrice,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        this.publicId = publicId;
        this.sellerUserId = sellerUserId;
        this.primaryImageId = primaryImageId;
        this.title = title;
        this.description = description;
        this.category = category;
        this.itemCondition = itemCondition;
        this.startPrice = startPrice;
        this.currentPrice = startPrice; // 등록 시 현재가 = 시작가
        this.buyNowPrice = buyNowPrice;
        this.bidCount = 0;
        this.minimumBidIncrement = 10000L; // DB DEFAULT와 동일. 엔티티 매핑 추가로 INSERT에 포함되므로 명시 초기화 필수
        this.status = AuctionStatus.ACTIVE;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    /**
     * 경매가 현재 라이브 상태인지 판정.
     *
     * 조건: status == ACTIVE && start_at <= now
     * "5분 공개 유예"는 start_at 자체를 5분 뒤로 설정함으로써 구현 — 이 메서드 자체는 변경 불필요.
     *
     * @param now 판정 기준 시각 (주입하여 테스트 가능성 확보)
     */
    public boolean isLive(LocalDateTime now) {
        return status == AuctionStatus.ACTIVE && !startAt.isAfter(now);
    }

    /**
     * 수정/삭제 가능 여부.
     *
     * ACTIVE 상태이고 아직 아무도 입찰하지 않은 경우에만 허용.
     */
    public boolean isModifiable() {
        return status == AuctionStatus.ACTIVE && bidCount == 0;
    }

    /**
     * 경매 정보 수정 (입찰 0건 전제로 호출할 것).
     *
     * 시작가가 변경되면 현재가도 시작가로 재설정한다.
     */
    public void update(
            String title,
            String description,
            AuctionCategory category,
            AuctionItemCondition itemCondition,
            Long startPrice,
            Long buyNowPrice,
            LocalDateTime endAt
    ) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.itemCondition = itemCondition;
        this.startPrice = startPrice;
        this.currentPrice = startPrice; // 시작가 변경 시 현재가 동기화
        this.buyNowPrice = buyNowPrice;
        this.endAt = endAt;
    }

    /**
     * 공개(시작) 예정 시각 재설정.
     * 수정 시점에 아직 공개 전인 경매의 공개 유예를 다시 부여할 때 사용한다.
     */
    public void reschedulePublishAt(LocalDateTime newStartAt) {
        this.startAt = newStartAt;
    }

    /**
     * 대표 이미지 지정.
     *
     * @param primaryImageId Image 엔티티의 PK
     */
    public void assignPrimaryImage(Long primaryImageId) {
        this.primaryImageId = primaryImageId;
    }

    /**
     * 경매 소프트 취소 (논리 삭제).
     *
     * 물리적으로 레코드를 삭제하지 않고 status를 CANCELLED로 변경한다.
     * 이후 isModifiable() == false가 되어 추가 수정이 차단된다.
     *
     * @param reason 취소 사유 (사용자/시스템 메시지)
     */
    public void cancel(String reason) {
        this.status = AuctionStatus.CANCELLED;
        this.cancelReason = reason;
        this.cancelledAt = LocalDateTime.now();
    }

    /**
     * 입찰 반영(검증 포함). 임계구역(경매별 락) 안에서 호출할 것.
     * 규칙 위반 시 ApiException을 던진다. 통과 시 현재가/입찰수만 갱신한다.
     * (highest_bid_id는 Bid 저장 후 assignHighestBid로 별도 반영)
     *
     * [왜 서비스가 아니라 엔티티에 있나]
     *   검증에 필요한 값(status/startAt/endAt/sellerUserId/currentPrice/minimumBidIncrement)이
     *   전부 이 클래스의 필드다. 서비스로 빼면 게터를 6개 열어야 하고(캡슐화 붕괴),
     *   누군가 검증을 건너뛰고 값을 바꿀 수 있다.
     *   지금은 currentPrice를 바꾸는 유일한 통로가 이 메서드라 규칙을 우회할 방법이 없다.
     *
     * [왜 now를 파라미터로 받나 — 시간 주입]
     *   메서드 안에서 LocalDateTime.now()를 부르지 않는다.
     *   (1) 테스트: 마감 1초 전/후를 자유롭게 시뮬레이션 가능(DB·스프링 불필요)
     *   (2) 일관성: BidService.doPlaceBid가 계산한 now 하나를 검증·Bid 생성·remaining 계산이 공유
     *
     * [검증 순서] 비용이 아니라 "의미의 강도" 순이다.
     *   종료 여부(경매 자체가 성립 안 함) → 본인 여부(자격 없음) → 금액(자격은 있는데 부족)
     *   종료된 경매에 "10,000원 더 올리세요"라고 안내하면 이상하다.
     *
     * [반환값이 void인 이유] 성공하면 조용히 상태만 바꾸고 실패는 예외로 알린다.
     *   boolean이면 호출자가 무시할 수 있고, 어떤 규칙을 어겼는지도 전달할 수 없다.
     */
    public void placeBid(Long bidderUserId, long amount, LocalDateTime now) {
        // [규칙 ①] 진행중 여부 — 조건이 둘로 나뉜 데는 이유가 있다.
        //   isLive(now)        : status==ACTIVE && startAt <= now  → "공개 유예"가 지났는가
        //   endAt.isAfter(now) : 아직 마감 전인가
        //
        //   등록 ──공개 유예── startAt ──── 입찰 가능 ──── endAt ──── 종료
        //        입찰 X                  입찰 O                  입찰 X
        //
        //   [핵심] isLive는 종료 시각을 보지 않는다. 스케줄러가 10초 주기라 마감이 지나도
        //   최대 10초 동안 status는 여전히 ACTIVE다. 그 틈에 들어온 입찰을 막는 게 endAt 조건이다.
        //   → 스케줄러의 지연을 도메인이 방어한다. 이 한 줄 덕에 주기를 느슨하게 둬도 데이터가 정확하다.
        //
        //   [경계값] 부등호가 서로 다른 건 의도적이다. 입찰 가능 구간은 [startAt, endAt).
        //     startAt <= now  → 시작 정각부터 허용
        //     now < endAt     → 마감 정각은 거절
        if (!isLive(now) || !endAt.isAfter(now)) {
            throw new ApiException(ErrorCode.AUCTION_ENDED);
        }
        // [규칙 ②] 본인 입찰 금지 — 자기 물건 값을 인위적으로 올리는 자전거래(shill bidding) 방지.
        //   [== 대신 equals인 이유] Long은 객체라 ==는 참조 비교다. 자바는 -128~127만 캐싱하므로
        //   테스트 데이터(작은 ID)에서는 우연히 통과하고 실서비스 ID에서만 조용히 뚫린다.
        //   null이 아닌 sellerUserId(nullable=false)를 앞에 둔 것도 방어적 습관.
        //   [한계] 부계정을 만들면 우회된다. 근본 방어가 아니라 최소 방어선이다.
        if (sellerUserId.equals(bidderUserId)) {
            throw new ApiException(ErrorCode.SELF_BID_NOT_ALLOWED);
        }
        // [규칙 ③] 최소 입찰 단위 — "현재가보다 크면 통과"가 아니라 "현재가 + 인상폭 이상"이어야 한다.
        //   1원씩 올리는 눈치싸움(nibbling)을 막는다. 그대로 두면 입찰이 수천 건 쌓이고
        //   WebSocket broadcast도 그만큼 나가 성능과 UX가 동시에 나빠진다.
        //   [왜 상수가 아니라 필드인가] 경매별로 다르게 설정할 여지를 남긴 것.
        //   "시작가의 5%" 같은 정책이 생겨도 스키마 변경 없이 코드만 바꾸면 된다.
        //   [거절에 숫자를 싣는다] 클라이언트가 "얼마여야 하는지"를 알아야 재시도가 추측이 아니게 된다.
        //   경합 구간에서는 응답을 기다리는 사이 현재가가 올라 대부분의 입찰이 여기서 걸린다.
        if (amount < currentPrice + minimumBidIncrement) {
            throw ApiException.withData(ErrorCode.BID_TOO_LOW,
                    new BidTooLowData(currentPrice, currentPrice + minimumBidIncrement));
        }
        // 통과 시 딱 두 값만 갱신한다. highestBidId는 Bid가 아직 저장 전이라 PK가 없어 건드릴 수 없다.
        this.currentPrice = amount;
        this.bidCount += 1;
    }

    /** 저장된 최고 입찰의 PK를 현재 최고가 입찰로 지정한다. */
    public void assignHighestBid(Long bidId) {
        this.highestBidId = bidId;
    }

    /**
     * 경매 종료 처리.
     * - 입찰자 있음(highestBid != null): status=PAYMENT_PENDING, winner/highestBid 기록.
     * - 입찰자 없음: status=ENDED.
     * 두 경우 모두 endedAt을 기록한다.
     */
    public void close(Bid highestBid, LocalDateTime now) {
        this.endedAt = now;
        if (highestBid != null) {
            this.status = AuctionStatus.PAYMENT_PENDING;
            this.winnerUserId = highestBid.getBidderUserId();
            this.highestBidId = highestBid.getId();
        } else {
            this.status = AuctionStatus.ENDED;
        }
    }
}
