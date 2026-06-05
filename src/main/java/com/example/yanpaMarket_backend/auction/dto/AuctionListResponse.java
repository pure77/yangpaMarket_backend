package com.example.yanpaMarket_backend.auction.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 경매 목록 페이지 응답 래퍼 DTO.
 *
 * Spring Data의 Page 객체를 프론트엔드 친화적인 구조로 변환한다.
 *
 * - content: 현재 페이지의 경매 카드 목록
 * - totalPages: 전체 페이지 수
 * - totalElements: 조건에 맞는 전체 경매 건수
 * - currentPage: 현재 페이지 번호 (0-based)
 */
public record AuctionListResponse(
        List<AuctionSummaryResponse> content,
        int totalPages,
        long totalElements,
        int currentPage
) {

    /**
     * Spring Data Page 객체로부터 목록 응답 DTO를 생성한다.
     *
     * @param page AuctionSummaryResponse 페이지 객체
     * @return 목록 응답 래퍼 객체
     */
    public static AuctionListResponse from(Page<AuctionSummaryResponse> page) {
        return new AuctionListResponse(
                page.getContent(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.getNumber()
        );
    }
}
