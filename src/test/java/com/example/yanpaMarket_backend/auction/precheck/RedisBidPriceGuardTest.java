package com.example.yanpaMarket_backend.auction.precheck;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.yanpaMarket_backend.common.time.AuctionTimes;
import com.example.yanpaMarket_backend.support.RedisTestSupport;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * [무엇] RedisBidPriceGuard를 Spring 없이 진짜 Redis에 붙여 검증한다.
 * [왜 Spring 없이] 판정·기록 규칙만 보는 테스트라 컨텍스트가 필요 없다. 빠르고 원인이 좁다.
 */
class RedisBidPriceGuardTest extends RedisTestSupport {

    private static final Long AUCTION = 1L;
    private static final Long SELLER = 10L;
    private static final Long BIDDER = 20L;
    // [왜 먼 미래] 만료(PEXPIREAT endAt+1시간)가 실제 시계보다 과거면 Redis가 키를 즉시 지운다.
    //   고정 날짜가 지나가도 테스트가 깨지지 않도록 충분히 먼 미래로 둔다.
    private static final LocalDateTime NOW = LocalDateTime.of(2099, 10, 26, 20, 0);
    private static final LocalDateTime END = NOW.plusHours(1);
    private static final String KEY = BidRedisKeys.bid(AUCTION);

    private final StringRedisTemplate redis = sharedRedis();
    private final RedisBidPriceGuard guard = new RedisBidPriceGuard(redis);

    private void recordPrice(long price) {
        guard.record(AUCTION, new BidPriceSnapshot(price, 10_000L, SELLER, END));
    }

    @Test
    void 키가_없으면_통과() {
        assertThat(guard.precheck(AUCTION, BIDDER, 1L, NOW)).isEqualTo(PrecheckResult.PASS);
    }

    @Test
    void 낮은_금액은_TooLow와_유효_입찰가를_돌려준다() {
        recordPrice(100_000L);
        assertThat(guard.precheck(AUCTION, BIDDER, 105_000L, NOW))
                .isEqualTo(new PrecheckResult.TooLow(100_000L, 110_000L));
    }

    @Test
    void 판매자_본인은_SelfBid() {
        recordPrice(100_000L);
        assertThat(guard.precheck(AUCTION, SELLER, 500_000L, NOW)).isEqualTo(PrecheckResult.SELF_BID);
    }

    @Test
    void 충분한_금액은_통과() {
        recordPrice(100_000L);
        assertThat(guard.precheck(AUCTION, BIDDER, 110_000L, NOW)).isEqualTo(PrecheckResult.PASS);
    }

    @Test
    void 종료_시각이_됐거나_지났으면_낮은_금액도_통과() {
        // [왜] 종료 시각은 통과 근거로만 쓴다. AUCTION_ENDED 판정은 락 안 도메인이 한다.
        recordPrice(100_000L);
        assertThat(guard.precheck(AUCTION, BIDDER, 1L, END)).isEqualTo(PrecheckResult.PASS);
        assertThat(guard.precheck(AUCTION, BIDDER, 1L, END.plusSeconds(1))).isEqualTo(PrecheckResult.PASS);
    }

    @Test
    void 더_낮은_기록은_무시된다_단조_증가() {
        recordPrice(1_200_000L);
        recordPrice(1_100_000L);
        assertThat(redis.opsForHash().get(KEY, BidRedisKeys.PRICE)).isEqualTo("1200000");
    }

    @Test
    void 네_필드를_모두_기록한다() {
        recordPrice(100_000L);
        Map<Object, Object> hash = redis.opsForHash().entries(KEY);
        assertThat(hash).containsEntry(BidRedisKeys.PRICE, "100000")
                .containsEntry(BidRedisKeys.INC, "10000")
                .containsEntry(BidRedisKeys.SELLER, "10")
                .containsEntry(BidRedisKeys.END_AT, String.valueOf(AuctionTimes.toEpochMillis(END)));
    }

    @Test
    void 일부_필드만_있으면_통과() {
        redis.opsForHash().put(KEY, BidRedisKeys.PRICE, "100000");
        assertThat(guard.precheck(AUCTION, BIDDER, 1L, NOW)).isEqualTo(PrecheckResult.PASS);
        assertThat(guard.stats().precheckFailures()).isZero();
    }

    @Test
    void 오염된_값은_통과하고_실패로_센다() {
        redis.opsForHash().putAll(KEY, Map.of(
                BidRedisKeys.PRICE, "abc",
                BidRedisKeys.INC, "10000",
                BidRedisKeys.SELLER, "10",
                BidRedisKeys.END_AT, String.valueOf(AuctionTimes.toEpochMillis(END))));
        assertThat(guard.precheck(AUCTION, BIDDER, 1L, NOW)).isEqualTo(PrecheckResult.PASS);
        assertThat(guard.stats().precheckFailures()).isEqualTo(1);
    }

    @Test
    void 만료는_종료_1시간_뒤_절대_시각이고_다시_기록해도_밀리지_않는다() {
        long expected = AuctionTimes.toEpochMillis(END) + RedisBidPriceGuard.KEY_GRACE.toMillis();
        RedisScript<Long> pexpiretime = RedisScript.of("return redis.call('PEXPIRETIME', KEYS[1])", Long.class);

        recordPrice(100_000L);
        assertThat(redis.execute(pexpiretime, List.of(KEY))).isEqualTo(expected);

        recordPrice(200_000L);
        assertThat(redis.execute(pexpiretime, List.of(KEY))).isEqualTo(expected);
    }

    @Test
    void evict는_키를_지운다() {
        recordPrice(100_000L);
        guard.evict(AUCTION);
        assertThat(redis.hasKey(KEY)).isFalse();
    }

    @Test
    void 판정_결과가_카운터에_쌓인다() {
        recordPrice(100_000L);
        guard.precheck(AUCTION, BIDDER, 110_000L, NOW); // pass
        guard.precheck(AUCTION, BIDDER, 1L, NOW);        // tooLow
        guard.precheck(AUCTION, SELLER, 1L, NOW);        // selfBid

        GuardStats stats = guard.stats();
        assertThat(stats.precheckPass()).isEqualTo(1);
        assertThat(stats.precheckTooLow()).isEqualTo(1);
        assertThat(stats.precheckSelfBid()).isEqualTo(1);
    }
}
