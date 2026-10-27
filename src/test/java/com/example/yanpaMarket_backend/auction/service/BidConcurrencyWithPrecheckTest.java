package com.example.yanpaMarket_backend.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionItemCondition;
import com.example.yanpaMarket_backend.auction.dto.BidRequest;
import com.example.yanpaMarket_backend.auction.precheck.BidRedisKeys;
import com.example.yanpaMarket_backend.auction.precheck.GuardStats;
import com.example.yanpaMarket_backend.auction.precheck.RedisBidPriceGuard;
import com.example.yanpaMarket_backend.auction.repository.AuctionRepository;
import com.example.yanpaMarket_backend.auction.repository.BidRepository;
import com.example.yanpaMarket_backend.common.util.PublicIdGenerator;
import com.example.yanpaMarket_backend.global.error.ApiException;
import com.example.yanpaMarket_backend.global.error.ErrorCode;
import com.example.yanpaMarket_backend.support.RedisTestSupport;
import com.example.yanpaMarket_backend.user.domain.User;
import com.example.yanpaMarket_backend.user.domain.UserStatus;
import com.example.yanpaMarket_backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
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
 *
 * [1차 파도: 콜드 스타트 폭주] 60건(고정 시드로 섞은 순서)을 한꺼번에 출발시킨다.
 *   - 가장 높은 금액(610,000)은 다른 어떤 입찰이 먼저 성공해도 항상 유효하다. 거절되면 사전 거절이 낡은 값으로
 *     유효한 입찰을 버린 것이다.
 *   - 사본 키가 없는 첫 폭주에서는 모든 사전 확인이 "키 없음 → 통과"가 되는 것이 설계다(명세 7-3).
 *     그래서 1차에서는 사전 거절 발생 여부는 보지 않고, DB 정합성·Redis 사본 == DB·Redis 정상(precheckFailures 0)만 본다.
 *
 * [2차 파도: 사본이 데워진 뒤] 1차가 완전히 끝난 뒤, 현재가(P)+최소 인상폭보다 낮은 금액으로 20건을 동시에 보낸다.
 *   사본에 P가 있으므로 20건 모두 락 앞에서 BID_TOO_LOW로 거절되어야 한다(precheckTooLow 증가량 == 20).
 *   DB(입찰 수·현재가)는 그대로여야 하고 Redis 사본도 여전히 DB와 같아야 한다.
 *
 * [공통 안전장치] 스레드 풀 크기를 입찰 수와 같게 해 모두 출발선(start 래치)에 선 뒤 동시에 출발시키고,
 *   출발선·완료·풀 종료 대기 결과를 모두 단언한다(진행 중 입찰과 단언·@AfterEach 정리의 경합 방지).
 *   정상 거절은 BID_TOO_LOW(ApiException)뿐이며, 락 실패·NPE·500류 등 다른 예외는 모아서 비어 있음을 단언한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class BidConcurrencyWithPrecheckTest extends RedisTestSupport {

    @Autowired BidService bidService;
    @Autowired UserRepository userRepository;
    @Autowired AuctionRepository auctionRepository;
    @Autowired BidRepository bidRepository;
    @Autowired RedisBidPriceGuard redisBidPriceGuard;

    @AfterEach
    void tearDown() {
        bidRepository.deleteAll();
        auctionRepository.deleteAll();
        userRepository.deleteAll();
    }

    /** 한 번의 동시 입찰 파도 결과. */
    private static class Wave {
        final AtomicInteger success = new AtomicInteger();
        final AtomicInteger tooLow = new AtomicInteger();
        final AtomicInteger maxAccepted = new AtomicInteger(0);
        final ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();
    }

    private record BidTask(String bidderPublicId, long amount) {}

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

        // ---------- 1차 파도: 콜드 스타트 폭주 ----------
        int n = 60;
        long highest = 20000L + (n - 1) * 10000L; // 610,000
        List<BidTask> wave1Tasks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            User b = userRepository.save(user("b-conc-pre-" + i + "@y.com", String.format("010-9%03d-1111", i)));
            wave1Tasks.add(new BidTask(b.getPublicId(), 20000L + i * 10000L));
        }
        Collections.shuffle(wave1Tasks, new Random(42)); // 높은 입찰이 커밋된 뒤 낮은 입찰이 도착하는 경합 유도

        GuardStats before1 = redisBidPriceGuard.stats();
        Wave w1 = runWave(auctionPublicId, wave1Tasks);
        GuardStats delta1 = redisBidPriceGuard.stats().minus(before1);
        System.out.println("[BidConcurrencyWithPrecheckTest] wave1 success=" + w1.success.get()
                + " guardDelta=" + delta1);

        assertThat(w1.unexpected).as("1차 예상 밖 예외: %s", w1.unexpected).isEmpty();

        Auction afterWave1 = auctionRepository.findByPublicId(auctionPublicId).orElseThrow();
        long persisted1 = bidRepository.findByAuctionIdOrderByCreatedAtDesc(
                afterWave1.getId(), PageRequest.of(0, n + 50)).getTotalElements();

        assertThat(afterWave1.getBidCount()).isEqualTo(w1.success.get());
        assertThat(persisted1).isEqualTo(w1.success.get());
        assertThat(afterWave1.getCurrentPrice()).isEqualTo((long) w1.maxAccepted.get());
        assertThat(w1.maxAccepted.get()).isEqualTo((int) highest); // 유효한 최고가 입찰은 거절되지 않는다
        assertThat(redisPrice(afterWave1.getId()))
                .isEqualTo(String.valueOf(afterWave1.getCurrentPrice())); // Redis 사본 == DB
        // 콜드 스타트 폭주에서는 모든 사전 확인이 "키 없음 → 통과"가 되는 것이 설계다. 여기서는 Redis 정상 여부만 본다.
        assertThat(delta1.precheckFailures()).as("1차 Redis 사전 확인 실패 (delta=%s)", delta1).isZero();

        // ---------- 2차 파도: 사본이 데워진 뒤, 현재가+인상폭보다 낮은 입찰 20건 ----------
        long p = afterWave1.getCurrentPrice(); // 610,000
        int m = 20;
        List<BidTask> wave2Tasks = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            User b = userRepository.save(user("b-conc-pre2-" + i + "@y.com", String.format("010-8%03d-2222", i)));
            wave2Tasks.add(new BidTask(b.getPublicId(), p - i * 1000L)); // 모두 P + 인상폭(10,000)보다 낮다
        }

        GuardStats before2 = redisBidPriceGuard.stats();
        Wave w2 = runWave(auctionPublicId, wave2Tasks);
        GuardStats delta2 = redisBidPriceGuard.stats().minus(before2);
        System.out.println("[BidConcurrencyWithPrecheckTest] wave2 tooLow=" + w2.tooLow.get()
                + " guardDelta=" + delta2);

        assertThat(w2.unexpected).as("2차 예상 밖 예외: %s", w2.unexpected).isEmpty();
        assertThat(w2.success.get()).as("2차에서 낮은 입찰이 성공했다").isZero();
        assertThat(w2.tooLow.get()).isEqualTo(m);
        assertThat(delta2.precheckTooLow()).as("2차 입찰이 모두 락 앞에서 거절돼야 한다 (delta=%s)", delta2).isEqualTo(m);
        assertThat(delta2.precheckFailures()).as("2차 Redis 사전 확인 실패 (delta=%s)", delta2).isZero();

        Auction afterWave2 = auctionRepository.findByPublicId(auctionPublicId).orElseThrow();
        assertThat(afterWave2.getBidCount()).isEqualTo(afterWave1.getBidCount());
        assertThat(afterWave2.getCurrentPrice()).isEqualTo(afterWave1.getCurrentPrice());
        assertThat(redisPrice(afterWave2.getId()))
                .isEqualTo(String.valueOf(afterWave2.getCurrentPrice())); // Redis 사본 == DB
    }

    /** 모든 입찰을 출발선에 세운 뒤 동시에 출발시키고, 끝까지(풀 종료까지) 기다려 결과를 모은다. */
    private Wave runWave(String auctionPublicId, List<BidTask> tasks) throws InterruptedException {
        int count = tasks.size();
        Wave wave = new Wave();
        ExecutorService pool = Executors.newFixedThreadPool(count); // 풀 크기 == 입찰 수: 전원이 출발선에 선다
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(count);
        try {
            for (BidTask task : tasks) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        bidService.placeBid(task.bidderPublicId(), auctionPublicId, new BidRequest(task.amount()));
                        wave.success.incrementAndGet();
                        wave.maxAccepted.accumulateAndGet((int) task.amount(), Math::max);
                    } catch (ApiException e) {
                        // 현재가보다 낮아 거절(락 앞 사전 거절 또는 락 안 도메인 거절) — 정상. 그 밖의 코드는 예상 밖.
                        if (e.getErrorCode() == ErrorCode.BID_TOO_LOW) wave.tooLow.incrementAndGet();
                        else wave.unexpected.add(e);
                    } catch (Throwable t) {
                        wave.unexpected.add(t);
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).as("모든 입찰이 출발선에 서지 못했다").isTrue();
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).as("입찰이 제한 시간 안에 끝나지 않았다").isTrue();
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).as("스레드 풀이 종료되지 않았다").isTrue();
        } finally {
            pool.shutdownNow();
        }
        return wave;
    }

    private Object redisPrice(Long auctionId) {
        return sharedRedis().opsForHash().get(BidRedisKeys.bid(auctionId), BidRedisKeys.PRICE);
    }

    private User user(String email, String phone) {
        return User.builder().publicId(PublicIdGenerator.newUlid())
                .email(email).nickname("유저").phone(phone)
                .isAdmin(false).status(UserStatus.ACTIVE).marketingOptIn(false).build();
    }
}
