package com.example.yanpaMarket_backend.auction.service;

import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionImage;
import com.example.yanpaMarket_backend.auction.domain.AuctionStatus;
import com.example.yanpaMarket_backend.auction.dto.AuctionCreateRequest;
import com.example.yanpaMarket_backend.auction.dto.AuctionCreateResponse;
import com.example.yanpaMarket_backend.auction.dto.AuctionDetailResponse;
import com.example.yanpaMarket_backend.auction.dto.AuctionListResponse;
import com.example.yanpaMarket_backend.auction.dto.AuctionSummaryResponse;
import com.example.yanpaMarket_backend.auction.dto.AuctionUpdateRequest;
import com.example.yanpaMarket_backend.auction.repository.AuctionImageRepository;
import com.example.yanpaMarket_backend.auction.repository.AuctionRepository;
import com.example.yanpaMarket_backend.common.util.PublicIdGenerator;
import com.example.yanpaMarket_backend.global.error.ApiException;
import com.example.yanpaMarket_backend.global.error.ErrorCode;
import com.example.yanpaMarket_backend.image.domain.Image;
import com.example.yanpaMarket_backend.image.repository.ImageRepository;
import com.example.yanpaMarket_backend.user.domain.User;
import com.example.yanpaMarket_backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경매 CRUD 서비스.
 *
 * 핵심 정책:
 *  - 등록 시 start_at = now + 공개유예(기본 5분). 그 전엔 공개 목록 비노출/입찰 불가.
 *  - 수정/삭제는 소유자 && 입찰 0건일 때만(isModifiable). 아니면 FORBIDDEN/CANNOT_MODIFY.
 *  - 삭제는 소프트 취소(cancel).
 *
 * 동작 흐름:
 *  create  → Auction 생성 → 이미지 연결(attachImages) → AuctionCreateResponse 반환
 *  list    → ACTIVE + start_at <= now 조건으로 공개 목록 페이징 반환
 *  getDetail → 경매 + 이미지 URL 목록 + 판매자 정보 조합
 *  listMine  → 판매자 본인의 모든 경매 반환
 *  update  → 소유자/수정가능 검증 → 필드 업데이트 → 이미지 재연결
 *  delete  → 소유자/수정가능 검증 → 소프트 취소(auction.cancel)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final AuctionImageRepository auctionImageRepository;
    private final ImageRepository imageRepository;
    private final UserRepository userRepository;

    // application.properties: app.auction.publish-delay-minutes=5 (기본값 5분)
    @Value("${app.auction.publish-delay-minutes:5}")
    private long publishDelayMinutes;

    /**
     * 경매 등록.
     * start_at 을 now + publishDelayMinutes 로 설정해 즉시 공개를 방지한다.
     */
    @Transactional
    public AuctionCreateResponse create(String sellerPublicId, AuctionCreateRequest request) {
        User seller = getUserOrUnauthorized(sellerPublicId);
        LocalDateTime now = LocalDateTime.now();

        Auction auction = Auction.builder()
                .publicId(PublicIdGenerator.newUlid())
                .sellerUserId(seller.getId())
                .title(request.title())
                .description(request.description())
                .category(request.category())
                .itemCondition(request.condition())
                .startPrice(request.startPrice())
                .buyNowPrice(request.buyNowPrice())
                .startAt(now.plusMinutes(publishDelayMinutes)) // 5분 공개 유예
                .endAt(request.endTime())
                .build();
        auctionRepository.save(auction);

        attachImages(auction, request.imageIds());
        return AuctionCreateResponse.from(auction);
    }

    /**
     * 공개 경매 목록 조회 (페이징).
     * 카테고리/키워드 필터링 지원. start_at <= now 이고 ACTIVE 상태인 경매만 노출.
     */
    public AuctionListResponse list(
            AuctionCategory category, String keyword, Pageable pageable) {
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword;
        Page<Auction> page = auctionRepository.findPublicList(
                AuctionStatus.ACTIVE, LocalDateTime.now(), category, normalizedKeyword, pageable);
        Page<AuctionSummaryResponse> mapped = page.map(
                auction -> AuctionSummaryResponse.from(auction, primaryImageUrl(auction)));
        return AuctionListResponse.from(mapped);
    }

    /**
     * 경매 상세 조회.
     * 이미지 URL 목록(sortOrder 순)과 판매자 정보를 포함해 반환한다.
     */
    public AuctionDetailResponse getDetail(String auctionPublicId) {
        Auction auction = getAuctionOrThrow(auctionPublicId);
        User seller = userRepository.findById(auction.getSellerUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.AUCTION_NOT_FOUND, "판매자를 찾을 수 없습니다."));
        return AuctionDetailResponse.from(auction, imageUrls(auction.getId()), seller);
    }

    /**
     * 내 경매 목록 조회.
     * 판매자 본인이 등록한 모든 경매를 최신순으로 반환한다(상태 무관).
     */
    public List<AuctionSummaryResponse> listMine(String sellerPublicId) {
        User seller = getUserOrUnauthorized(sellerPublicId);
        return auctionRepository.findBySellerUserIdOrderByCreatedAtDesc(seller.getId()).stream()
                .map(auction -> AuctionSummaryResponse.from(auction, primaryImageUrl(auction)))
                .toList();
    }

    /**
     * 경매 수정.
     * 소유자 검증 → 입찰 0건 검증(isModifiable) → 필드 업데이트 → 이미지 재연결.
     * 이미지는 기존 매핑을 전부 제거 후 새 목록으로 재구성한다.
     */
    @Transactional
    public AuctionDetailResponse update(
            String sellerPublicId, String auctionPublicId, AuctionUpdateRequest request) {
        User seller = getUserOrUnauthorized(sellerPublicId);
        Auction auction = getAuctionOrThrow(auctionPublicId);
        assertOwner(auction, seller);
        if (!auction.isModifiable()) {
            throw new ApiException(ErrorCode.CANNOT_MODIFY);
        }

        auction.update(
                request.title(), request.description(), request.category(),
                request.condition(), request.startPrice(), request.buyNowPrice(), request.endTime());

        // 이미지 재연결: 기존 매핑 제거 후 새 목록으로 재구성
        auctionImageRepository.deleteByAuctionId(auction.getId());
        auction.assignPrimaryImage(null);
        attachImages(auction, request.imageIds());

        User refreshedSeller = userRepository.findById(auction.getSellerUserId()).orElseThrow();
        return AuctionDetailResponse.from(auction, imageUrls(auction.getId()), refreshedSeller);
    }

    /**
     * 경매 삭제(소프트 취소).
     * 소유자 검증 → 입찰 0건 검증 → auction.cancel("판매자 취소") 호출.
     * DB에서 실제 삭제하지 않고 상태만 CANCELLED 로 변경한다.
     */
    @Transactional
    public void delete(String sellerPublicId, String auctionPublicId) {
        User seller = getUserOrUnauthorized(sellerPublicId);
        Auction auction = getAuctionOrThrow(auctionPublicId);
        assertOwner(auction, seller);
        if (!auction.isModifiable()) {
            throw new ApiException(ErrorCode.CANNOT_MODIFY);
        }
        auction.cancel("판매자 취소");
    }

    // =========================================================================
    // 내부 헬퍼
    // =========================================================================

    /**
     * 경매에 이미지 목록을 연결한다.
     * - imageIds 순서대로 sortOrder 를 부여하며 첫 번째 이미지를 primaryImage 로 설정.
     * - 존재하지 않는 이미지나 타인 이미지 사용 시 예외 발생.
     */
    private void attachImages(Auction auction, List<String> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            return;
        }
        List<Image> images = imageRepository.findByPublicIdIn(imageIds);
        Map<String, Image> byPublicId = new HashMap<>();
        for (Image image : images) {
            byPublicId.put(image.getPublicId(), image);
        }
        int sortOrder = 0;
        Long firstImageId = null;
        for (String imageId : imageIds) {
            Image image = byPublicId.get(imageId);
            if (image == null) {
                throw new ApiException(ErrorCode.INVALID_IMAGE, "존재하지 않는 이미지입니다: " + imageId);
            }
            if (!image.getUploaderUserId().equals(auction.getSellerUserId())) {
                throw new ApiException(ErrorCode.FORBIDDEN, "본인이 업로드한 이미지만 사용할 수 있습니다.");
            }
            auctionImageRepository.save(AuctionImage.builder()
                    .auctionId(auction.getId())
                    .imageId(image.getId())
                    .sortOrder(sortOrder++)
                    .build());
            image.markAttached();
            if (firstImageId == null) {
                firstImageId = image.getId();
            }
        }
        auction.assignPrimaryImage(firstImageId);
    }

    /**
     * 특정 경매의 이미지 URL 목록을 sortOrder 순으로 반환한다.
     */
    private List<String> imageUrls(Long auctionId) {
        List<AuctionImage> mappings = auctionImageRepository.findByAuctionIdOrderBySortOrderAsc(auctionId);
        List<String> urls = new ArrayList<>();
        for (AuctionImage mapping : mappings) {
            imageRepository.findById(mapping.getImageId())
                    .ifPresent(image -> urls.add(image.getFileUrl()));
        }
        return urls;
    }

    /**
     * 경매의 대표 이미지 URL 반환. primaryImageId 가 없으면 null.
     */
    private String primaryImageUrl(Auction auction) {
        if (auction.getPrimaryImageId() == null) {
            return null;
        }
        return imageRepository.findById(auction.getPrimaryImageId())
                .map(Image::getFileUrl)
                .orElse(null);
    }

    /** publicId 로 사용자 조회. 없으면 UNAUTHORIZED 예외. */
    private User getUserOrUnauthorized(String publicId) {
        return userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
    }

    /** publicId 로 경매 조회. 없으면 AUCTION_NOT_FOUND 예외. */
    private Auction getAuctionOrThrow(String auctionPublicId) {
        return auctionRepository.findByPublicId(auctionPublicId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUCTION_NOT_FOUND));
    }

    /** 경매 소유자가 아니면 FORBIDDEN 예외. */
    private void assertOwner(Auction auction, User user) {
        if (!auction.getSellerUserId().equals(user.getId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "본인 경매만 수정/삭제할 수 있습니다.");
        }
    }
}
