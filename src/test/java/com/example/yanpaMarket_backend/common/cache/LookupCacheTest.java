package com.example.yanpaMarket_backend.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionItemCondition;
import com.example.yanpaMarket_backend.auction.repository.AuctionRepository;
import com.example.yanpaMarket_backend.common.util.PublicIdGenerator;
import com.example.yanpaMarket_backend.user.domain.User;
import com.example.yanpaMarket_backend.user.domain.UserStatus;
import com.example.yanpaMarket_backend.user.repository.UserRepository;
import com.example.yanpaMarket_backend.user.service.UserLookupService;
import com.example.yanpaMarket_backend.user.service.UserSummary;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * [무엇] 입찰 경로의 조회 캐시(경매 ID, 사용자 요약)가 실제로 적용되는지 검증한다.
 * [왜] @Cacheable은 프록시로 동작해 호출 위치가 잘못되면 에러 없이 "조용히" 무시된다.
 *      그래서 "캐시에 값이 들어갔는가 / 없음은 안 들어갔는가"를 CacheManager로 직접 확인한다.
 * [격리] 캐시 키는 매번 새로 만드는 ULID(publicId)라 다른 테스트와 키가 겹치지 않는다.
 *        그래도 @BeforeEach에서 두 캐시를 비워 순서 의존을 없앤다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LookupCacheTest {

    @Autowired private AuctionRepository auctionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CacheManager cacheManager;
    @Autowired private UserLookupService userLookupService;

    private Cache auctionIdCache;
    private Cache userSummaryCache;

    @BeforeEach
    void setUp() {
        auctionIdCache = cacheManager.getCache(CacheNames.AUCTION_ID_BY_PUBLIC_ID);
        auctionIdCache.clear();
        userSummaryCache = cacheManager.getCache(CacheNames.USER_SUMMARY_BY_PUBLIC_ID);
        userSummaryCache.clear();
    }

    @Test
    void 경매ID_조회결과가_캐시에_저장된다() {
        User seller = userRepository.save(user("cache-seller@y.com", "판매자", "010-8000-0001"));
        Auction auction = auctionRepository.save(auction(seller.getId()));

        Optional<Long> found = auctionRepository.findIdByPublicId(auction.getPublicId());

        assertThat(found).contains(auction.getId());
        assertThat(auctionIdCache.get(auction.getPublicId(), Long.class)).isEqualTo(auction.getId());
    }

    @Test
    void 없는_경매는_캐시하지_않는다() {
        String unknown = PublicIdGenerator.newUlid();

        Optional<Long> found = auctionRepository.findIdByPublicId(unknown);

        assertThat(found).isEmpty();
        assertThat(auctionIdCache.get(unknown)).isNull();
    }

    @Test
    void 사용자_요약이_캐시되어_만료_전에는_DB가_바뀌어도_이전_값을_돌려준다() {
        User user = userRepository.saveAndFlush(user("cache-user@y.com", "홍길동", "010-8000-0002"));

        UserSummary first = userLookupService.findSummaryByPublicId(user.getPublicId()).orElseThrow();

        // DB의 닉네임을 바꾼다. 캐시가 적용됐다면 두 번째 조회는 DB에 가지 않고 이전 값을 돌려준다.
        user.completeProfile("김철수", "010-8000-0002", false);
        userRepository.saveAndFlush(user);
        UserSummary second = userLookupService.findSummaryByPublicId(user.getPublicId()).orElseThrow();

        assertThat(first).isEqualTo(new UserSummary(user.getId(), "홍길동"));
        assertThat(second.nickname()).isEqualTo("홍길동");
        assertThat(userSummaryCache.get(user.getPublicId(), UserSummary.class)).isEqualTo(first);
    }

    @Test
    void 없는_사용자는_캐시하지_않는다() {
        String unknown = PublicIdGenerator.newUlid();

        Optional<UserSummary> found = userLookupService.findSummaryByPublicId(unknown);

        assertThat(found).isEmpty();
        assertThat(userSummaryCache.get(unknown)).isNull();
    }

    private Auction auction(Long sellerId) {
        LocalDateTime now = LocalDateTime.now();
        return Auction.builder()
                .publicId(PublicIdGenerator.newUlid())
                .sellerUserId(sellerId)
                .title("캐시 테스트 경매").description("설명")
                .category(AuctionCategory.ETC).itemCondition(AuctionItemCondition.USED)
                .startPrice(10_000L).buyNowPrice(null)
                .startAt(now.minusMinutes(1)).endAt(now.plusHours(1))
                .build();
    }

    private User user(String email, String nickname, String phone) {
        return User.builder()
                .publicId(PublicIdGenerator.newUlid())
                .email(email).nickname(nickname).phone(phone)
                .isAdmin(false).status(UserStatus.ACTIVE).marketingOptIn(false)
                .build();
    }
}
