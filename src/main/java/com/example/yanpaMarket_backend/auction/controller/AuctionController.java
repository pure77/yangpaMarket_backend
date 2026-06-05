package com.example.yanpaMarket_backend.auction.controller;

import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.dto.AuctionCreateRequest;
import com.example.yanpaMarket_backend.auction.dto.AuctionCreateResponse;
import com.example.yanpaMarket_backend.auction.dto.AuctionDetailResponse;
import com.example.yanpaMarket_backend.auction.dto.AuctionListResponse;
import com.example.yanpaMarket_backend.auction.dto.AuctionSummaryResponse;
import com.example.yanpaMarket_backend.auction.dto.AuctionUpdateRequest;
import com.example.yanpaMarket_backend.auction.service.AuctionService;
import com.example.yanpaMarket_backend.global.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 경매 CRUD 컨트롤러.
 * 공개: GET ""(목록), GET "/{id}"(상세).
 * 인증: POST ""(등록), PUT/DELETE "/{id}", GET "/mine"(내 경매).
 */
@RestController
@RequestMapping("/api/v1/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService;

    @GetMapping
    public ApiResponse<AuctionListResponse> list(
            @RequestParam(required = false) AuctionCategory category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "endTime,asc") String sort
    ) {
        Pageable pageable = PageRequest.of(page, size, toSort(sort));
        return ApiResponse.success(auctionService.list(category, keyword, pageable));
    }

    @GetMapping("/mine")
    public ApiResponse<List<AuctionSummaryResponse>> listMine(Authentication authentication) {
        return ApiResponse.success(auctionService.listMine((String) authentication.getPrincipal()));
    }

    @GetMapping("/{auctionId}")
    public ApiResponse<AuctionDetailResponse> getDetail(@PathVariable String auctionId) {
        return ApiResponse.success(auctionService.getDetail(auctionId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuctionCreateResponse> create(
            Authentication authentication,
            @Valid @RequestBody AuctionCreateRequest request
    ) {
        return ApiResponse.success(
                auctionService.create((String) authentication.getPrincipal(), request));
    }

    @PutMapping("/{auctionId}")
    public ApiResponse<AuctionDetailResponse> update(
            Authentication authentication,
            @PathVariable String auctionId,
            @Valid @RequestBody AuctionUpdateRequest request
    ) {
        return ApiResponse.success(
                auctionService.update((String) authentication.getPrincipal(), auctionId, request));
    }

    @DeleteMapping("/{auctionId}")
    public ApiResponse<Void> delete(
            Authentication authentication,
            @PathVariable String auctionId
    ) {
        auctionService.delete((String) authentication.getPrincipal(), auctionId);
        return ApiResponse.successMessage("경매가 취소되었습니다");
    }

    /** "endTime,asc" / "currentPrice,desc" / "bidCount,desc" → 엔티티 필드 정렬로 변환. */
    private Sort toSort(String sort) {
        String[] parts = sort.split(",");
        String field = switch (parts[0]) {
            case "currentPrice" -> "currentPrice";
            case "bidCount" -> "bidCount";
            default -> "endAt"; // endTime
        };
        Sort.Direction direction = (parts.length > 1 && parts[1].equalsIgnoreCase("desc"))
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, field);
    }
}
