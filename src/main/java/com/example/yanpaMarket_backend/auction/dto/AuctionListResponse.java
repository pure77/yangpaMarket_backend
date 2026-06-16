package com.example.yanpaMarket_backend.auction.dto; // auction.dto = 경매 API 요청/응답 객체 패키지

import java.util.List;
import org.springframework.data.domain.Page; // Spring Data의 페이지네이션 결과 타입

/**
 * [무엇] 경매 "목록" 페이지 응답 래퍼 DTO.
 *        Spring Data의 Page 객체를 프론트가 쓰기 쉬운 단순 구조로 변환한다.
 * [어떻게 쓰임]
 *   - GET /api/v1/auctions 의 응답 데이터.
 * [연결]
 *   - AuctionService 가 조회한 Page<AuctionSummaryResponse> 를 from()으로 변환.
 */
public record AuctionListResponse(
        List<AuctionSummaryResponse> content, // 현재 페이지의 경매 카드 목록
        int totalPages,                       // 전체 페이지 수
        long totalElements,                   // 조건에 맞는 전체 경매 건수
        int currentPage                       // 현재 페이지 번호(0부터 시작)
) {

    /**
     * [변환 팩토리] Page 객체 → 목록 응답 래퍼로 매핑.
     */
    public static AuctionListResponse from(Page<AuctionSummaryResponse> page) {
        return new AuctionListResponse(
                page.getContent(),       // 현재 페이지 내용
                page.getTotalPages(),    // 총 페이지 수
                page.getTotalElements(), // 총 건수
                page.getNumber()         // 현재 페이지 번호
        );
    }
}
