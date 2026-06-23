package com.example.yanpaMarket_backend.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionItemCondition;
import com.example.yanpaMarket_backend.auction.dto.BidRequest;
import com.example.yanpaMarket_backend.auction.repository.AuctionRepository;
import com.example.yanpaMarket_backend.auction.repository.BidRepository;
import com.example.yanpaMarket_backend.common.util.PublicIdGenerator;
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

// @Transactional 미사용: 각 입찰이 실제 커밋되어야 동시성/일관성을 검증할 수 있다.
@SpringBootTest
@ActiveProfiles("test")
class BidConcurrencyTest {

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
    void 동일경매_동시입찰시_입찰수와_현재가가_일관된다() throws InterruptedException {
        User seller = userRepository.save(user("s-conc@y.com", "010-9000-0000"));
        LocalDateTime now = LocalDateTime.now();
        Auction auction = auctionRepository.save(Auction.builder()
                .publicId(PublicIdGenerator.newUlid())
                .sellerUserId(seller.getId())
                .title("동시성 경매").description("설명")
                .category(AuctionCategory.ETC).itemCondition(AuctionItemCondition.USED)
                .startPrice(10000L).buyNowPrice(null)
                .startAt(now.minusMinutes(1)).endAt(now.plusHours(1)).build());
        String auctionPublicId = auction.getPublicId();

        int n = 60;
        String[] bidderPublicIds = new String[n];
        for (int i = 0; i < n; i++) {
            User b = userRepository.save(user("b-conc-" + i + "@y.com", String.format("010-8%03d-0000", i)));
            bidderPublicIds[i] = b.getPublicId();
        }

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger maxAccepted = new AtomicInteger(10000);

        for (int i = 0; i < n; i++) {
            int amount = 20000 + i * 10000; // 모두 최소인상폭(10000) 이상 간격
            String bidderId = bidderPublicIds[i];
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    bidService.placeBid(bidderId, auctionPublicId, new BidRequest((long) amount));
                    success.incrementAndGet();
                    maxAccepted.accumulateAndGet(amount, Math::max);
                } catch (Exception ignored) {
                    // 동시 도착 순서상 현재가보다 낮아 거절(BID_TOO_LOW)될 수 있음 — 정상
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown(); // 일제히 시작
        done.await(20, TimeUnit.SECONDS);
        pool.shutdown();

        Auction reloaded = auctionRepository.findByPublicId(auctionPublicId).orElseThrow();
        long persistedBids = bidRepository.findByAuctionIdOrderByCreatedAtDesc(
                reloaded.getId(), PageRequest.of(0, n + 10)).getTotalElements();

        // 일관성 불변식: 성공 수 == 입찰수 == 저장된 bids 수, 현재가 == 채택된 최고 금액
        assertThat(reloaded.getBidCount()).isEqualTo(success.get());
        assertThat(persistedBids).isEqualTo(success.get());
        assertThat(reloaded.getCurrentPrice()).isEqualTo((long) maxAccepted.get());
        assertThat(success.get()).isGreaterThan(0);
    }

    private User user(String email, String phone) {
        return User.builder().publicId(PublicIdGenerator.newUlid())
                .email(email).nickname("유저").phone(phone)
                .isAdmin(false).status(UserStatus.ACTIVE).marketingOptIn(false).build();
    }
}
