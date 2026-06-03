package com.example.yanpaMarket_backend.auction.domain;

import com.example.yanpaMarket_backend.common.domain.BaseTimeEntity;
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
}
