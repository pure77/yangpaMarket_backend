package com.example.yanpaMarket_backend.auction.precheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.yanpaMarket_backend.support.RedisTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * [무엇] Redis 장애 때 사전 거절이 "빨리 통과"하는지 고정한다(fail-open + 짧은 타임아웃).
 * [왜 시간을 재나] 결과가 Pass인 것만으로는 부족하다. 오래 기다린 뒤 Pass하면 그동안 Tomcat 스레드가 묶인다.
 */
class RedisBidPriceGuardFailureTest extends RedisTestSupport {

    private static final Long AUCTION = 1L;
    private static final Long SELLER = 10L;
    private static final Long BIDDER = 20L;
    // [왜 먼 미래] record()는 PEXPIREAT endAt+1h(절대 시각)을 건다. 지난 날짜면 Redis가 키를 바로 지워 판정이 Pass가 된다.
    private static final LocalDateTime NOW = LocalDateTime.of(2099, 10, 26, 20, 0);
    private static final BidPriceSnapshot SNAPSHOT = new BidPriceSnapshot(100_000L, 10_000L, SELLER, NOW.plusHours(1));

    @Test
    void 처음부터_연결할_수_없으면_연결_타임아웃_안에_통과하고_예외가_없다() {
        // 아무도 듣지 않는 포트. [알려진 한계] 한 번도 연결된 적 없으면 요청마다 연결을 다시 시도한다
        //   → 요청당 최대 connect-timeout(200ms). Windows는 거절된 연결에도 이 시간을 다 쓸 수 있다.
        StringRedisTemplate dead = newRedisTemplate("localhost", 1, Duration.ofSeconds(2));
        try {
            RedisBidPriceGuard guard = new RedisBidPriceGuard(dead);

            long start = System.nanoTime();
            PrecheckResult result = guard.precheck(AUCTION, BIDDER, 1L, NOW);
            long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

            assertThat(result).isEqualTo(PrecheckResult.PASS);
            assertThat(elapsedMs).isLessThan(1_000);
            assertThatCode(() -> guard.record(AUCTION, SNAPSHOT)).doesNotThrowAnyException();
            assertThatCode(() -> guard.evict(AUCTION)).doesNotThrowAnyException();
            assertThat(guard.stats().precheckFailures()).isEqualTo(1);
            assertThat(guard.stats().recordFailures()).isEqualTo(1);
            assertThat(guard.stats().evictFailures()).isEqualTo(1);
        } finally {
            close(dead);
        }
    }

    @Test
    void 연결이_끊기면_명령_타임아웃을_기다리지_않고_즉시_통과() throws InterruptedException {
        // [왜 전용 컨테이너] 공유 컨테이너는 다른 테스트가 쓰므로 멈출 수 없다.
        GenericContainer<?> own = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        own.start();
        // [명령 타임아웃 2초] REJECT_COMMANDS가 없다면 끊긴 동안 2초씩 기다린다. 아래 500ms 단언이 그걸 잡는다.
        StringRedisTemplate template = newRedisTemplate(own.getHost(), own.getMappedPort(6379), Duration.ofSeconds(2));
        try {
            RedisBidPriceGuard guard = new RedisBidPriceGuard(template);
            guard.record(AUCTION, SNAPSHOT);
            assertThat(guard.precheck(AUCTION, BIDDER, 1L, NOW)).isInstanceOf(PrecheckResult.TooLow.class); // 연결 확립

            own.stop();
            Thread.sleep(500); // Lettuce가 연결 끊김을 감지할 시간

            long start = System.nanoTime();
            PrecheckResult result = guard.precheck(AUCTION, BIDDER, 1L, NOW);
            long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

            assertThat(result).isEqualTo(PrecheckResult.PASS);
            assertThat(elapsedMs).isLessThan(500);
        } finally {
            close(template);
            if (own.isRunning()) {
                own.stop();
            }
        }
    }

    @Test
    void 느린_Redis는_명령_타임아웃만큼만_기다리고_통과한_뒤_복구된다() {
        StringRedisTemplate guardRedis = newRedisTemplate(redisHost(), redisPort(), Duration.ofMillis(50));
        StringRedisTemplate blocker = newRedisTemplate(redisHost(), redisPort(), Duration.ofSeconds(5));
        try {
            RedisBidPriceGuard guard = new RedisBidPriceGuard(guardRedis);
            guard.record(AUCTION, SNAPSHOT);
            guard.precheck(AUCTION, BIDDER, 1L, NOW); // 연결 확립

            // DEBUG SLEEP 0.3: Redis 전체가 300ms 동안 아무 명령도 처리하지 않는다
            CompletableFuture<Void> sleeping = CompletableFuture.runAsync(() -> blocker.execute(
                    (RedisCallback<Object>) c -> c.execute("DEBUG",
                            "SLEEP".getBytes(StandardCharsets.UTF_8), "0.3".getBytes(StandardCharsets.UTF_8))));
            sleepQuietly(50); // DEBUG SLEEP이 먼저 도착하도록

            long start = System.nanoTime();
            PrecheckResult result = guard.precheck(AUCTION, BIDDER, 1L, NOW);
            long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

            assertThat(result).isEqualTo(PrecheckResult.PASS);
            assertThat(elapsedMs).isLessThan(250); // 300ms를 다 기다리지 않았다
            assertThat(guard.stats().precheckFailures()).isEqualTo(1);

            sleeping.join();
            assertThat(guard.precheck(AUCTION, BIDDER, 1L, NOW)).isInstanceOf(PrecheckResult.TooLow.class); // 복구
        } finally {
            close(guardRedis);
            close(blocker);
        }
    }

    @Test
    void 스크립트_캐시가_비어도_기록된다() {
        RedisBidPriceGuard guard = new RedisBidPriceGuard(sharedRedis());
        guard.record(AUCTION, SNAPSHOT); // SHA가 Redis에 올라간다

        sharedRedis().execute((RedisCallback<Object>) c -> {
            c.scriptingCommands().scriptFlush(); // Redis 재시작과 같은 상황
            return null;
        });
        guard.record(AUCTION, new BidPriceSnapshot(200_000L, 10_000L, SELLER, NOW.plusHours(1)));

        assertThat(sharedRedis().opsForHash().get(BidRedisKeys.bid(AUCTION), BidRedisKeys.PRICE)).isEqualTo("200000");
        assertThat(guard.stats().recordFailures()).isZero();
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
