package com.example.yanpaMarket_backend.auction.controller;

import com.example.yanpaMarket_backend.auction.dto.BidHistoryResponse;
import com.example.yanpaMarket_backend.auction.dto.BidRequest;
import com.example.yanpaMarket_backend.auction.dto.BidResponse;
import com.example.yanpaMarket_backend.auction.service.BidService;
import com.example.yanpaMarket_backend.global.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 입찰 컨트롤러.
 * - POST /{auctionId}/bids : 인증 필요(입찰)
 * - GET  /{auctionId}/bids : 공개(입찰 내역)
 */
@RestController
@RequestMapping("/api/v1/auctions/{auctionId}/bids")
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BidResponse> placeBid(
            Authentication authentication,
            @PathVariable String auctionId,
            @Valid @RequestBody BidRequest request
    ) {
        return ApiResponse.success(
                bidService.placeBid((String) authentication.getPrincipal(), auctionId, request));
    }

    @GetMapping
    public ApiResponse<BidHistoryResponse> getBids(
            @PathVariable String auctionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.success(bidService.getBids(auctionId, pageable));
    }
}
