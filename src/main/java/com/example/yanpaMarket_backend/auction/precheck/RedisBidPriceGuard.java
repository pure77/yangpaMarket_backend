package com.example.yanpaMarket_backend.auction.precheck;

import com.example.yanpaMarket_backend.auction.domain.BidRules;
import com.example.yanpaMarket_backend.common.time.AuctionTimes;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * [무엇] Redis에 둔 현재가 사본으로 낮은 입찰을 락 앞에서 거절한다.
 *
 * [어떻게]
 *   precheck: HMGET 한 번(필드 4개를 원자적으로 읽음) → 판정은 도메인과 같은 BidRules.check.
 *             읽기만 하므로 Lua가 필요 없다.
 *   record  : bid-record.lua — 단조 증가 가드 + 네 필드 일괄 기록 + 절대 시각 만료.
 *   evict   : DEL.
 *
 * [fail-open] 모든 Redis 오류(연결 실패, 50ms 타임아웃, 값 파싱 오류)를 여기서 삼킨다.
 *   precheck는 Pass를 돌려줘 기존 락 경로로 가게 하고, record/evict는 로그만 남긴다.
 *   Redis가 없으면 "느려질 뿐 틀리지 않는다" — 최종 판정은 락 안의 DB 검증이 한다.
 *   [장애 비용] 연결이 끊긴 뒤에는 즉시 실패(REJECT_COMMANDS), 느린 Redis는 최대 50ms.
 *   단, 앱 시작부터 Redis가 없으면 요청마다 최대 200ms(connect-timeout) — 알려진 한계, RedisConfig 주석 참고.
 *
 * [키 없음 = 통과] 키는 첫 입찰 커밋의 record가 만든다. 락 밖에서 DB를 읽어 채우지 않는다
 *   (그 값은 읽는 순간 이미 낡았을 수 있다).
 */
@Component
@ConditionalOnProperty(name = "app.bid.precheck.enabled", havingValue = "true", matchIfMissing = true)
public class RedisBidPriceGuard implements BidPriceGuard {

    /** 종료 후 이만큼 지나면 키를 지운다. 종료 직후의 입찰은 어차피 endAt 조건으로 통과된다. */
    static final Duration KEY_GRACE = Duration.ofHours(1);

    private static final List<Object> FIELDS =
            List.of(BidRedisKeys.PRICE, BidRedisKeys.INC, BidRedisKeys.SELLER, BidRedisKeys.END_AT);

    // [EVALSHA] RedisTemplate이 SHA로 먼저 호출하고, Redis 재시작으로 스크립트 캐시가 비면(NOSCRIPT) EVAL로 다시 보낸다.
    private static final RedisScript<Long> RECORD_SCRIPT =
            RedisScript.of(new ClassPathResource("redis/bid-record.lua"), Long.class);

    private final StringRedisTemplate redis;
    private final GuardFailureLog failureLog = new GuardFailureLog();

    private final LongAdder precheckPass = new LongAdder();
    private final LongAdder precheckTooLow = new LongAdder();
    private final LongAdder precheckSelfBid = new LongAdder();
    private final LongAdder precheckFailures = new LongAdder();
    private final LongAdder recordFailures = new LongAdder();
    private final LongAdder evictFailures = new LongAdder();

    public RedisBidPriceGuard(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public PrecheckResult precheck(Long auctionId, Long bidderUserId, long amount, LocalDateTime now) {
        try {
            List<Object> values = redis.opsForHash().multiGet(BidRedisKeys.bid(auctionId), FIELDS);
            PrecheckResult result = judge(values, bidderUserId, amount, AuctionTimes.toEpochMillis(now));
            failureLog.recovered();
            count(result);
            return result;
        } catch (RuntimeException e) {
            // 연결 실패·타임아웃·숫자가 아닌 값 모두 여기로 온다. 의심스러우면 통과.
            precheckFailures.increment();
            failureLog.failure("precheck", e);
            return PrecheckResult.PASS;
        }
    }

    private PrecheckResult judge(List<Object> values, Long bidderUserId, long amount, long nowMillis) {
        if (values == null || values.size() != FIELDS.size() || values.contains(null)) {
            return PrecheckResult.PASS; // 키 없음 또는 일부 필드만 있음
        }
        long price = Long.parseLong((String) values.get(0));
        long increment = Long.parseLong((String) values.get(1));
        Long sellerUserId = Long.valueOf((String) values.get(2));
        long endAtMillis = Long.parseLong((String) values.get(3));

        // [종료 시각은 통과 근거로만] 끝났거나 끝났을 수 있으면 락에서 AUCTION_ENDED로 판정하게 넘긴다.
        //   거절 근거로 쓰면 서버 시계가 빠를 때 마감 직전의 유효한 입찰이 거절된다.
        if (nowMillis >= endAtMillis) {
            return PrecheckResult.PASS;
        }
        return switch (BidRules.check(sellerUserId, bidderUserId, amount, price, increment)) {
            case SELF_BID -> PrecheckResult.SELF_BID;
            case TOO_LOW -> new PrecheckResult.TooLow(price, price + increment);
            case OK -> PrecheckResult.PASS;
        };
    }

    private void count(PrecheckResult result) {
        // [default 없음] 결과 종류가 늘면 여기서 컴파일 에러가 나 카운터를 빠뜨리지 않는다.
        switch (result) {
            case PrecheckResult.Pass _ -> precheckPass.increment();
            case PrecheckResult.SelfBid _ -> precheckSelfBid.increment();
            case PrecheckResult.TooLow _ -> precheckTooLow.increment();
        }
    }

    @Override
    public void record(Long auctionId, BidPriceSnapshot snapshot) {
        try {
            long endAtMillis = AuctionTimes.toEpochMillis(snapshot.endAt());
            redis.execute(RECORD_SCRIPT, List.of(BidRedisKeys.bid(auctionId)),
                    String.valueOf(snapshot.currentPrice()),
                    String.valueOf(snapshot.minimumBidIncrement()),
                    String.valueOf(snapshot.sellerUserId()),
                    String.valueOf(endAtMillis),
                    String.valueOf(endAtMillis + KEY_GRACE.toMillis()));
            failureLog.recovered();
        } catch (RuntimeException e) {
            // [삼키는 이유] 입찰은 이미 커밋됐다. 여기서 던지면 성공한 입찰에 실패 응답이 나가고 브로드캐스트도 빠진다.
            //   Redis 값이 실제보다 낮게 남을 뿐(덜 거절)이고, 다음 성공 입찰의 record가 바로잡는다.
            recordFailures.increment();
            failureLog.failure("record", e);
        }
    }

    @Override
    public void evict(Long auctionId) {
        try {
            redis.delete(BidRedisKeys.bid(auctionId));
            failureLog.recovered();
        } catch (RuntimeException e) {
            // [삼키는 이유] afterCommit에서 던지면 "커밋은 됐는데 API는 500"이 된다.
            evictFailures.increment();
            failureLog.failure("evict", e);
        }
    }

    public GuardStats stats() {
        return new GuardStats(precheckPass.sum(), precheckTooLow.sum(), precheckSelfBid.sum(),
                precheckFailures.sum(), recordFailures.sum(), evictFailures.sum());
    }
}
