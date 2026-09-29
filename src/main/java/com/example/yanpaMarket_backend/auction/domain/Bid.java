package com.example.yanpaMarket_backend.auction.domain;

import com.example.yanpaMarket_backend.common.util.PublicIdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 입찰 1건. bids 테이블 매핑.
 * - public_id(ULID 26자)를 외부 식별자로 사용.
 * - created_at은 생성 시점에 직접 주입한다(updated_at 컬럼이 없으므로 BaseTimeEntity 미사용).
 */
@Getter
@Entity
@Table(
        name = "bids",
        uniqueConstraints = {@UniqueConstraint(name = "uk_bids_public_id", columnNames = "public_id")}
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 26, updatable = false)
    private String publicId;

    @Column(name = "auction_id", nullable = false, updatable = false)
    private Long auctionId;

    @Column(name = "bidder_user_id", nullable = false, updatable = false)
    private Long bidderUserId;

    @Column(name = "amount", nullable = false, updatable = false)
    private Long amount;

    @Column(name = "is_winning_bid", nullable = false)
    private boolean isWinningBid;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Bid(String publicId, Long auctionId, Long bidderUserId, Long amount, LocalDateTime createdAt) {
        this.publicId = publicId;
        this.auctionId = auctionId;
        this.bidderUserId = bidderUserId;
        this.amount = amount;
        this.isWinningBid = false;
        this.createdAt = createdAt;
    }

    /** 신규 입찰 생성. publicId는 ULID로 자동 발급, created_at은 호출자가 주입한다. */
    public static Bid create(Long auctionId, Long bidderUserId, long amount, LocalDateTime now) {
        return new Bid(PublicIdGenerator.newUlid(), auctionId, bidderUserId, amount, now);
    }
}
