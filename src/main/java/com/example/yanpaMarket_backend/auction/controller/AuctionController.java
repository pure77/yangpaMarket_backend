package com.example.yanpaMarket_backend.auction.controller; // auction.controller = 경매 HTTP 요청 처리 계층

import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.dto.AuctionCreateRequest;
import com.example.yanpaMarket_backend.auction.dto.AuctionCreateResponse;
import com.example.yanpaMarket_backend.auction.dto.AuctionDetailResponse;
import com.example.yanpaMarket_backend.auction.dto.AuctionListResponse;
import com.example.yanpaMarket_backend.auction.dto.AuctionSummaryResponse;
import com.example.yanpaMarket_backend.auction.dto.AuctionUpdateRequest;
import com.example.yanpaMarket_backend.auction.service.AuctionService;
import com.example.yanpaMarket_backend.global.api.ApiResponse;
import jakarta.validation.Valid; // 요청 바디 검증 트리거
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest; // 페이지 요청 생성
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;        // 정렬 조건
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication; // 인증 사용자 정보
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;  // URL 경로 변수 바인딩
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;   // 요청 본문(JSON) 바인딩
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;  // 쿼리 파라미터 바인딩
import org.springframework.web.bind.annotation.ResponseStatus;// 응답 HTTP 상태 지정
import org.springframework.web.bind.annotation.RestController;

/**
 * [무엇] 경매 CRUD REST 컨트롤러.
 * [공개/인증 구분] (SecurityConfig 와 연동)
 *   - 공개: GET ""(목록), GET "/{id}"(상세)
 *   - 인증: POST ""(등록), PUT/DELETE "/{id}"(수정/삭제), GET "/mine"(내 경매)
 * [연결]
 *   - 모든 경로 prefix: /api/v1/auctions
 *   - 실제 로직은 AuctionService 에 위임, 결과는 ApiResponse 로 감싸 반환.
 */
@RestController
@RequestMapping("/api/v1/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService; // 경매 비즈니스 로직(생성자 주입)

    /**
     * [GET /api/v1/auctions] 공개 경매 목록(페이징/필터/정렬).
     * 모든 파라미터는 선택값이며 기본값이 있다.
     */
    @GetMapping
    public ApiResponse<AuctionListResponse> list(
            @RequestParam(required = false) AuctionCategory category, // 카테고리 필터(선택)
            @RequestParam(required = false) String keyword,          // 제목 검색어(선택)
            @RequestParam(defaultValue = "0") int page,              // 페이지 번호(0부터)
            @RequestParam(defaultValue = "20") int size,             // 페이지 크기
            @RequestParam(defaultValue = "endTime,asc") String sort  // 정렬 기준(기본: 마감 임박순)
    ) {
        Pageable pageable = PageRequest.of(page, size, toSort(sort)); // 페이지+정렬 정보 구성
        return ApiResponse.success(auctionService.list(category, keyword, pageable));
    }

    /**
     * [GET /api/v1/auctions/mine] 로그인 사용자가 등록한 경매 목록(인증 필요).
     */
    @GetMapping("/mine")
    public ApiResponse<List<AuctionSummaryResponse>> listMine(Authentication authentication) {
        return ApiResponse.success(auctionService.listMine((String) authentication.getPrincipal())); // principal=publicId
    }

    /**
     * [GET /api/v1/auctions/{id}] 경매 상세 조회(공개).
     */
    @GetMapping("/{auctionId}")
    public ApiResponse<AuctionDetailResponse> getDetail(@PathVariable String auctionId) {
        return ApiResponse.success(auctionService.getDetail(auctionId));
    }

    /**
     * [POST /api/v1/auctions] 경매 등록(인증 필요). 성공 시 201 Created.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) // 생성 성공 → 201
    public ApiResponse<AuctionCreateResponse> create(
            Authentication authentication,                 // 로그인 사용자(판매자)
            @Valid @RequestBody AuctionCreateRequest request // 등록 요청(검증)
    ) {
        return ApiResponse.success(
                auctionService.create((String) authentication.getPrincipal(), request));
    }

    /**
     * [PUT /api/v1/auctions/{id}] 경매 수정(인증+소유자+입찰0건일 때만 가능).
     */
    @PutMapping("/{auctionId}")
    public ApiResponse<AuctionDetailResponse> update(
            Authentication authentication,
            @PathVariable String auctionId,                // 수정할 경매 식별자
            @Valid @RequestBody AuctionUpdateRequest request
    ) {
        return ApiResponse.success(
                auctionService.update((String) authentication.getPrincipal(), auctionId, request));
    }

    /**
     * [DELETE /api/v1/auctions/{id}] 경매 삭제(소프트 취소, 인증+소유자 필요).
     */
    @DeleteMapping("/{auctionId}")
    public ApiResponse<Void> delete(
            Authentication authentication,
            @PathVariable String auctionId
    ) {
        auctionService.delete((String) authentication.getPrincipal(), auctionId);
        return ApiResponse.successMessage("경매가 취소되었습니다"); // 데이터 없이 메시지만 반환
    }

    /**
     * [내부 헬퍼] "필드,방향" 형식 정렬 문자열을 Spring Data Sort 로 변환.
     * 예: "endTime,asc" / "currentPrice,desc" / "bidCount,desc".
     * 허용되지 않은 필드는 endAt(마감시각) 기준으로 처리(안전한 기본값).
     */
    private Sort toSort(String sort) {
        String[] parts = sort.split(","); // [필드, 방향]
        String field = switch (parts[0]) {
            case "currentPrice" -> "currentPrice"; // 현재가 정렬
            case "bidCount" -> "bidCount";         // 입찰수 정렬
            default -> "endAt";                    // 그 외(endTime 포함)는 마감시각
        };
        // 방향이 desc면 내림차순, 아니면 오름차순
        Sort.Direction direction = (parts.length > 1 && parts[1].equalsIgnoreCase("desc"))
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, field);
    }
}
