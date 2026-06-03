package com.example.yanpaMarket_backend.auction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 경매-이미지 매핑(정렬 순서 포함). raw Long FK 사용.
 * (auction_id, sort_order) 유니크로 같은 경매 내 정렬 중복을 막는다.
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

    @Column(name = "auction_id", nullable = false)
    private Long auctionId;

    @Column(name = "image_id", nullable = false)
    private Long imageId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public AuctionImage(Long auctionId, Long imageId, Integer sortOrder) {
        this.auctionId = auctionId;
        this.imageId = imageId;
        this.sortOrder = sortOrder;
        this.createdAt = LocalDateTime.now();
    }
}
