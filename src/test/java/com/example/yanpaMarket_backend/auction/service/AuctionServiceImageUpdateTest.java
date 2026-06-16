package com.example.yanpaMarket_backend.auction.service; // 테스트 대상(AuctionService)과 같은 패키지

import static org.assertj.core.api.Assertions.assertThat;     // 값 검증
import static org.assertj.core.api.Assertions.assertThatCode; // 예외 발생 여부 검증

import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionItemCondition;
import com.example.yanpaMarket_backend.auction.dto.AuctionCreateRequest;
import com.example.yanpaMarket_backend.auction.dto.AuctionCreateResponse;
import com.example.yanpaMarket_backend.auction.dto.AuctionDetailResponse;
import com.example.yanpaMarket_backend.auction.dto.AuctionUpdateRequest;
import com.example.yanpaMarket_backend.common.util.PublicIdGenerator;
import com.example.yanpaMarket_backend.image.domain.Image;
import com.example.yanpaMarket_backend.image.domain.ImageStatus;
import com.example.yanpaMarket_backend.image.domain.StorageProvider;
import com.example.yanpaMarket_backend.image.repository.ImageRepository;
import com.example.yanpaMarket_backend.user.domain.User;
import com.example.yanpaMarket_backend.user.domain.UserStatus;
import com.example.yanpaMarket_backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경매 수정 시 이미지 재연결의 회귀 테스트.
 *
 * 배경(버그): update()는 기존 auction_images 매핑을 삭제한 뒤 새 목록으로 다시 INSERT한다.
 * 그런데 Hibernate는 flush 시 INSERT를 DELETE보다 먼저 실행하므로, 삭제를 명시적으로 flush하지
 * 않으면 유니크 제약(auction_id+image_id, auction_id+sort_order)과 충돌해 수정이 실패한다.
 * (사진을 추가하거나 일부 삭제하면 기존과 겹치는 image_id/sort_order가 생겨 재현된다.)
 *
 * 수정: deleteByAuctionId 직후 flush()로 DELETE를 INSERT보다 먼저 DB에 반영.
 *
 * 실제 유니크 제약 위반은 DB에서만 재현되므로 H2(MySQL 모드) 통합 테스트로 검증한다.
 * application-test.properties의 H2 MySQL 모드 데이터소스를 사용한다.
 * (Spring Boot 4에서는 @DataJpaTest 등 슬라이스 모듈이 분리되어 기본 클래스패스에 없으므로
 *  전체 컨텍스트를 올리는 @SpringBootTest(MOCK 웹 환경)를 사용한다.
 *  SecurityConfig가 웹 전용 HttpSecurity를 필요로 하므로 NONE 대신 MOCK을 쓰고,
 *  WebMvcConfig가 읽는 app.upload.* 속성은 test properties에 채워 컨텍스트 부팅을 보장한다.
 *  @Transactional로 테스트 간 롤백.)
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuctionServiceImageUpdateTest {

    @Autowired
    private AuctionService auctionService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ImageRepository imageRepository;

    private String sellerPublicId; // 각 테스트에서 공통으로 쓰는 판매자 식별자

    /** [사전 준비] 각 테스트 실행 전에 판매자 사용자 1명을 DB에 생성. */
    @BeforeEach
    void setUp() {
        User seller = userRepository.save(User.builder()
                .publicId(PublicIdGenerator.newUlid())
                .email("seller@yangpa.com")
                .nickname("판매자")
                .phone("010-1234-5678")
                .isAdmin(false)
                .status(UserStatus.ACTIVE)
                .marketingOptIn(false)
                .build());
        sellerPublicId = seller.getPublicId();
    }

    /** [회귀 테스트] 기존 이미지 유지 + 새 이미지 추가 수정이 유니크 제약 충돌 없이 되는지 검증. */
    @Test
    void 사진_추가_수정시_유니크제약_위반없이_반영된다() {
        String img1 = saveImage();
        String img2 = saveImage();
        String img3 = saveImage(); // 신규로 추가할 사진

        String auctionId = createAuction(List.of(img1, img2));

        AuctionUpdateRequest request = updateRequestWithImages(List.of(img1, img2, img3));

        AuctionDetailResponse[] result = new AuctionDetailResponse[1];
        assertThatCode(() -> result[0] = auctionService.update(sellerPublicId, auctionId, request))
                .doesNotThrowAnyException();
        assertThat(result[0].imageIds()).containsExactly(img1, img2, img3);
    }

    /** [회귀 테스트] 일부 이미지 삭제 수정이 유니크 제약 충돌 없이 되는지 검증. */
    @Test
    void 사진_삭제_수정시_유니크제약_위반없이_반영된다() {
        String img1 = saveImage();
        String img2 = saveImage();
        String img3 = saveImage();

        String auctionId = createAuction(List.of(img1, img2, img3));

        // img1, img3 제거 후 img2만 유지 (image_id/sort_order가 기존과 겹쳐 재현됨)
        AuctionUpdateRequest request = updateRequestWithImages(List.of(img2));

        AuctionDetailResponse[] result = new AuctionDetailResponse[1];
        assertThatCode(() -> result[0] = auctionService.update(sellerPublicId, auctionId, request))
                .doesNotThrowAnyException();
        assertThat(result[0].imageIds()).containsExactly(img2);
    }

    // === 테스트 헬퍼 ===

    /** [헬퍼] 판매자 소유의 이미지 1건을 DB에 저장하고 그 public_id를 반환. */
    private String saveImage() {
        String publicId = PublicIdGenerator.newUlid();
        Image image = imageRepository.save(Image.builder()
                .publicId(publicId)
                .uploaderUserId(userRepository.findByPublicId(sellerPublicId).orElseThrow().getId())
                .storageProvider(StorageProvider.LOCAL)
                .objectKey("key/" + publicId)
                .fileUrl("http://localhost/img/" + publicId + ".jpg")
                .originalFilename(publicId + ".jpg")
                .contentType("image/jpeg")
                .fileSizeBytes(1024L)
                .uploadStatus(ImageStatus.UPLOADED)
                .build());
        return image.getPublicId();
    }

    /** [헬퍼] 주어진 이미지들로 경매를 등록하고 경매 public_id를 반환. */
    private String createAuction(List<String> imageIds) {
        AuctionCreateRequest request = new AuctionCreateRequest(
                "테스트 경매", "설명", AuctionCategory.ELECTRONICS, AuctionItemCondition.LIKE_NEW,
                10000L, null, LocalDateTime.now().plusDays(1), imageIds);
        AuctionCreateResponse response = auctionService.create(sellerPublicId, request);
        return response.auctionId();
    }

    /** [헬퍼] 주어진 이미지 목록을 가진 수정 요청 DTO를 생성. */
    private AuctionUpdateRequest updateRequestWithImages(List<String> imageIds) {
        return new AuctionUpdateRequest(
                "수정된 제목", "수정된 설명", AuctionCategory.FASHION, AuctionItemCondition.USED,
                20000L, null, LocalDateTime.now().plusDays(2), imageIds);
    }
}
