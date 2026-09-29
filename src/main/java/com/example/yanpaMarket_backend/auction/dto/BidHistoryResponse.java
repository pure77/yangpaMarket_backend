package com.example.yanpaMarket_backend.auction.dto;

import com.example.yanpaMarket_backend.auction.domain.Bid;
import com.example.yanpaMarket_backend.common.util.NicknameMasker;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

/** 입찰 내역 페이지 응답. 닉네임은 마스킹, isHighest는 경매의 highestBidId로 판정. */
public record BidHistoryResponse(List<Item> content, long totalElements) {

    public record Item(String maskedNickname, long price, LocalDateTime createdAt, boolean isHighest) {
    }

    /**
     * @param page          입찰 페이지(최신순)
     * @param highestBidId  현재 최고 입찰 Bid PK (null 가능)
     * @param nicknameById  bidderUserId → 닉네임 매핑
     */
    public static BidHistoryResponse from(Page<Bid> page, Long highestBidId, Map<Long, String> nicknameById) {
        List<Item> items = page.getContent().stream()
                .map(bid -> new Item(
                        NicknameMasker.mask(nicknameById.get(bid.getBidderUserId())),
                        bid.getAmount(),
                        bid.getCreatedAt(),
                        bid.getId().equals(highestBidId)))
                .toList();
        return new BidHistoryResponse(items, page.getTotalElements());
    }
}
