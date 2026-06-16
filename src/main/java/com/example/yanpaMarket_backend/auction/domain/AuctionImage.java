package com.example.yanpaMarket_backend.auction.domain; // auction.domain = 경매 도메인 패키지

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.sql.Types;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode; // JDBC 타입을 명시적으로 지정

/**
 * [무엇] 경매와 이미지를 연결하고 "노출 순서(sortOrder)"를 저장하는 매핑 엔티티
 *        (= auction_images 테이블 한 행).
 * [특징]
 *   - 객체 참조 대신 raw Long FK(auctionId, imageId)를 직접 들고 있다(단순/성능 목적).
 *   - (auction_id, image_id) 유니크: 같은 이미지를 한 경매에 중복 연결 금지.
 *   - (auction_id, sort_order) 유니크: 한 경매 안에서 정렬 순서 중복 금지.
 * [연결]
 *   - Auction(경매) ↔ Image(파일 메타) 사이의 연결고리.
 *   - AuctionImageRepository 로 조회/저장, AuctionService 가 이미지 순서를 관리.
 */
@Getter
@Entity
@Table(
        name = "auction_images",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_auction_images_auction_image", columnNames = {"auction_id", "image_id"}),
                @UniqueConstraint(name = "uk_auction_images_auction_sort_order", columnNames = {"auction_id", "sort_order"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuctionImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 연결된 경매의 PK (객체 참조 대신 raw FK) */
    @Column(name = "auction_id", nullable = false)
    private Long auctionId;

    /** 연결된 이미지의 PK (객체 참조 대신 raw FK) */
    @Column(name = "image_id", nullable = false)
    private Long imageId;

    // [타입 매핑 주의] sort_order는 노출 순서라 음수가 없어야 함 → DB는 SMALLINT UNSIGNED(0~65535).
    // 자바엔 unsigned가 없어 Integer로 받는데, Hibernate는 Integer를 INTEGER로 보아 DB와 타입 불일치 오류가 남.
    // → @JdbcTypeCode(Types.SMALLINT)로 "SMALLINT 기준으로 검증하라"고 명시해 해결.
    @JdbcTypeCode(Types.SMALLINT)
    @Column(name = "sort_order", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer sortOrder; // 경매 내 이미지 표시 순서(0부터)

    /** 생성 시각 (이 엔티티는 BaseTimeEntity를 안 쓰고 직접 관리) */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 빌더 생성자. 생성 시각은 자동으로 현재로 채운다. */
    @Builder
    public AuctionImage(Long auctionId, Long imageId, Integer sortOrder) {
        this.auctionId = auctionId;
        this.imageId = imageId;
        this.sortOrder = sortOrder;
        this.createdAt = LocalDateTime.now();
    }
}
