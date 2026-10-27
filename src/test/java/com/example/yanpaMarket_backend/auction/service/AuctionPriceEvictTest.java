package com.example.yanpaMarket_backend.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionItemCondition;
import com.example.yanpaMarket_backend.auction.dto.AuctionUpdateRequest;
import com.example.yanpaMarket_backend.auction.precheck.BidRedisKeys;
import com.example.yanpaMarket_backend.auction.repository.AuctionRepository;
import com.example.yanpaMarket_backend.common.util.PublicIdGenerator;
import com.example.yanpaMarket_backend.global.error.ApiException;
import com.example.yanpaMarket_backend.support.RedisTestSupport;
import com.example.yanpaMarket_backend.user.domain.User;
import com.example.yanpaMarket_backend.user.domain.UserStatus;
import com.example.yanpaMarket_backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * [무엇] 경매 수정·취소가 커밋되면 Redis 현재가 사본이 지워지는지, 롤백되면 남는지 검증한다.
 * [왜 키를 직접 넣나] 지금은 수정·취소가 입찰 0건일 때만 가능하고 키는 입찰 커밋 후에만 생겨서,
 *   정상 경로로는 "키가 있는 상태의 수정"을 만들 수 없다. evict는 앞으로 생길 경로를 위한 방어선이다.
 * [@Transactional 미사용] 테스트 트랜잭션이 감싸면 커밋이 일어나지 않아 afterCommit이 실행되지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuctionPriceEvictTest extends RedisTestSupport {

    @Autowired AuctionService auctionService;
    @Autowired AuctionRepository auctionRepository;
    @Autowired UserRepository userRepository;

    private String sellerPublicId;
    private String auctionPublicId;
    private String key;

    @BeforeEach
    void setUp() {
        User seller = userRepository.save(User.builder().publicId(PublicIdGenerator.newUlid())
                .email("seller-evict@y.com").nickname("판매자").phone("010-7200-0001")
                .isAdmin(false).status(UserStatus.ACTIVE).marketingOptIn(false).build());
        sellerPublicId = seller.getPublicId();
        LocalDateTime now = LocalDateTime.now();
        Auction auction = auctionRepository.save(Auction.builder()
                .publicId(PublicIdGenerator.newUlid())
                .sellerUserId(seller.getId())
                .title("수정 경매").description("설명")
                .category(AuctionCategory.ETC).itemCondition(AuctionItemCondition.USED)
                .startPrice(50_000L).buyNowPrice(null)
                .startAt(now.minusMinutes(1)).endAt(now.plusHours(1)).build());
        auctionPublicId = auction.getPublicId();
        key = BidRedisKeys.bid(auction.getId());
        sharedRedis().opsForHash().putAll(key, Map.of(
                BidRedisKeys.PRICE, "50000", BidRedisKeys.INC, "10000",
                BidRedisKeys.SELLER, String.valueOf(seller.getId()), BidRedisKeys.END_AT, "9999999999999"));
    }

    @AfterEach
    void tearDown() {
        auctionRepository.deleteAll();
        userRepository.deleteAll();
    }

    private AuctionUpdateRequest request(List<String> imageIds) {
        return new AuctionUpdateRequest("수정된 제목", "수정된 설명", AuctionCategory.ETC, AuctionItemCondition.USED,
                10_000L, null, LocalDateTime.now().plusHours(2), imageIds);
    }

    @Test
    void 수정이_커밋되면_키가_지워진다() {
        auctionService.update(sellerPublicId, auctionPublicId, request(List.of()));
        assertThat(sharedRedis().hasKey(key)).isFalse();
    }

    @Test
    void 취소가_커밋되면_키가_지워진다() {
        auctionService.delete(sellerPublicId, auctionPublicId);
        assertThat(sharedRedis().hasKey(key)).isFalse();
    }

    @Test
    void 수정이_도중에_실패해_롤백되면_키가_남는다() {
        // 도메인 변경과 evict 등록 뒤에 이미지 연결이 실패한다(INVALID_IMAGE) → 트랜잭션 롤백 → afterCommit 없음
        assertThatThrownBy(() -> auctionService.update(sellerPublicId, auctionPublicId, request(List.of("NO_SUCH_IMAGE"))))
                .isInstanceOf(ApiException.class);
        assertThat(sharedRedis().hasKey(key)).isTrue();
    }
}
