package com.example.yanpaMarket_backend.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionItemCondition;
import com.example.yanpaMarket_backend.auction.dto.BidRequest;
import com.example.yanpaMarket_backend.auction.precheck.BidRedisKeys;
import com.example.yanpaMarket_backend.auction.repository.AuctionRepository;
import com.example.yanpaMarket_backend.auction.repository.BidRepository;
import com.example.yanpaMarket_backend.common.util.PublicIdGenerator;
import com.example.yanpaMarket_backend.support.RedisTestSupport;
import com.example.yanpaMarket_backend.user.domain.User;
import com.example.yanpaMarket_backend.user.domain.UserStatus;
import com.example.yanpaMarket_backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * [무엇] 사전 거절을 켠 채 같은 경매에 동시에 입찰해도 DB 정합성이 그대로이고, Redis 사본이 DB와 같게 끝나는지 본다.
 * [유효한 최고가] 가장 높은 금액(610,000)은 다른 어떤 입찰이 먼저 성공해도 항상 유효하다.
 *   이게 거절되면 사전 거절이 낡은 값이나 잘못된 값으로 유효한 입찰을 버린 것이다.
 */
@SpringBootTest
@ActiveProfiles("test")
class BidConcurrencyWithPrecheckTest extends RedisTestSupport {

    @Autowired BidService bidService;
    @Autowired UserRepository userRepository;
    @Autowired AuctionRepository auctionRepository;
    @Autowired BidRepository bidRepository;

    @AfterEach
    void tearDown() {
        bidRepository.deleteAll();
        auctionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 동시_입찰_후_DB_정합성과_Redis_사본이_일치하고_최고가_입찰은_성공한다() throws InterruptedException {
        User seller = userRepository.save(user("s-conc-pre@y.com", "010-9100-0000"));
        LocalDateTime now = LocalDateTime.now();
        Auction auction = auctionRepository.save(Auction.builder()
                .publicId(PublicIdGenerator.newUlid())
                .sellerUserId(seller.getId())
                .title("동시성 경매(사전 거절)").description("설명")
                .category(AuctionCategory.ETC).itemCondition(AuctionItemCondition.USED)
                .startPrice(10000L).buyNowPrice(null)
                .startAt(now.minusMinutes(1)).endAt(now.plusHours(1)).build());
        String auctionPublicId = auction.getPublicId();

        int n = 60;
        long highest = 20000L + (n - 1) * 10000L; // 610,000
        String[] bidderPublicIds = new String[n];
        for (int i = 0; i < n; i++) {
            User b = userRepository.save(user("b-conc-pre-" + i + "@y.com", String.format("010-9%03d-1111", i)));
            bidderPublicIds[i] = b.getPublicId();
        }

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger maxAccepted = new AtomicInteger(10000);

        for (int i = 0; i < n; i++) {
            int amount = 20000 + i * 10000;
            String bidderId = bidderPublicIds[i];
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    bidService.placeBid(bidderId, auctionPublicId, new BidRequest((long) amount));
                    success.incrementAndGet();
                    maxAccepted.accumulateAndGet(amount, Math::max);
                } catch (Exception ignored) {
                    // 현재가보다 낮아 거절(락 앞 또는 락 안) — 정상
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        done.await(20, TimeUnit.SECONDS);
        pool.shutdown();

        Auction reloaded = auctionRepository.findByPublicId(auctionPublicId).orElseThrow();
        long persistedBids = bidRepository.findByAuctionIdOrderByCreatedAtDesc(
                reloaded.getId(), PageRequest.of(0, n + 10)).getTotalElements();

        assertThat(reloaded.getBidCount()).isEqualTo(success.get());
        assertThat(persistedBids).isEqualTo(success.get());
        assertThat(reloaded.getCurrentPrice()).isEqualTo((long) maxAccepted.get());
        assertThat(maxAccepted.get()).isEqualTo((int) highest); // 유효한 최고가 입찰은 거절되지 않는다
        assertThat(sharedRedis().opsForHash().get(BidRedisKeys.bid(reloaded.getId()), BidRedisKeys.PRICE))
                .isEqualTo(String.valueOf(reloaded.getCurrentPrice())); // Redis 사본 == DB
    }

    private User user(String email, String phone) {
        return User.builder().publicId(PublicIdGenerator.newUlid())
                .email(email).nickname("유저").phone(phone)
                .isAdmin(false).status(UserStatus.ACTIVE).marketingOptIn(false).build();
    }
}
