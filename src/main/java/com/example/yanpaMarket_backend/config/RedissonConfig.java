package com.example.yanpaMarket_backend.config;

import com.example.yanpaMarket_backend.auction.concurrency.DistributedBidLock;
import com.example.yanpaMarket_backend.auction.concurrency.InMemoryBidLock;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * [무엇] 2단계 분산 락(DistributedBidLock)이 쓰는 Redisson 클라이언트. app.bid.lock.distributed.enabled=true일 때만 만든다.
 *
 * [왜 스타터가 아니라 직접 만드나] redisson-spring-boot-starter는 lettuce-core를 빼고 Spring의 Redis 연결을 Redisson으로 바꾼다.
 *   그러면 사전 거절(RedisBidPriceGuard)이 기대는 Lettuce 설정(끊김 즉시 실패, 50ms 타임아웃)이 사라진다.
 *   그래서 Lettuce는 그대로 두고, 락 전용 Redisson 클라이언트를 따로 하나 만든다(Redis 연결이 두 종류가 된다).
 *
 * [설정을 Spring 값에서 읽는 이유] 같은 Redis를 보고, 같은 짧은 타임아웃 원칙(느린 Redis가 죽은 Redis보다 위험)을 지키기 위해서다.
 *   - retryAttempts=0: Redisson 기본은 실패 시 여러 번 재시도한다 → Redis가 죽으면 입찰 하나가 수 초 멈춘다. 즉시 포기하고 fail-open.
 *   - lazyInitialization=true: 기본은 생성 시 접속을 시도해, Redis가 없으면 앱이 아예 뜨지 않는다. 사전 거절과 같은 "Redis 없어도 뜬다".
 * [DistributedBidLock도 여기서 등록] 스위치가 켜졌을 때만 @Primary BidLock으로 올라가 BidService가 이쪽을 받는다(꺼지면 InMemoryBidLock 그대로).
 * [static config] 테스트가 Spring 없이 같은 설정으로 클라이언트를 만들게 하려고 연다(RedisConfig.testClientOptions와 같은 이유).
 */
@Configuration
@ConditionalOnProperty(name = "app.bid.lock.distributed.enabled", havingValue = "true")
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.database:0}") int database,
            @Value("${spring.data.redis.timeout}") Duration timeout,
            @Value("${spring.data.redis.connect-timeout}") Duration connectTimeout) {
        return Redisson.create(config(host, port, password, database, timeout, connectTimeout));
    }

    /**
     * [@Primary] InMemoryBidLock도 BidLock 빈이라, BidService가 이쪽을 받도록 우선권을 준다.
     *   InMemoryBidLock 빈은 그대로 두고 1단으로 주입받는다(부하 테스트도 그 타입을 직접 쓴다).
     */
    @Bean
    @Primary
    public DistributedBidLock distributedBidLock(InMemoryBidLock local, RedissonClient redissonClient,
            @Value("${app.bid.lock.wait:500ms}") Duration wait,
            @Value("${app.bid.lock.lease:3s}") Duration lease,
            @Value("${app.bid.lock.breaker.slow-call:30ms}") Duration slowCall,
            @Value("${app.bid.lock.breaker.open-wait:1s}") Duration openWait) {
        CircuitBreaker breaker = CircuitBreaker.of("redisBidLock", breakerConfig(slowCall, openWait));
        return new DistributedBidLock(local, redissonClient, wait, lease, breaker);
    }

    /**
     * [무엇] Redis 락 호출용 차단기 설정. 최근 20건(COUNT_BASED) 중 10건 이상 모이면 실패율 또는 느림 비율이 50% 이상일 때 열린다.
     *   slowCall: 이보다 오래 걸린 호출을 "느림"으로 센다(타임아웃 50ms보다 낮게, 평소 왕복 ~1ms보다 확실히 높게).
     *   openWait: 열린 뒤 이 시간이 지나면 반열림(3건 시험). static인 이유: 테스트가 같은 설정을 쓰게 하려고.
     */
    public static CircuitBreakerConfig breakerConfig(Duration slowCall, Duration openWait) {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .slowCallRateThreshold(50)
                .slowCallDurationThreshold(slowCall)
                .waitDurationInOpenState(openWait)
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
    }

    public static Config config(String host, int port, String password, int database,
                                Duration timeout, Duration connectTimeout) {
        Config config = new Config();
        config.setLazyInitialization(true);
        SingleServerConfig server = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database)
                .setTimeout((int) timeout.toMillis())
                .setConnectTimeout((int) connectTimeout.toMillis())
                .setRetryAttempts(0)
                // 2단 락이라 동시에 Redis 락을 다투는 건 서버당 1건 → 연결이 많을 필요가 없다.
                .setConnectionPoolSize(8)
                .setConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(4)
                .setSubscriptionConnectionMinimumIdleSize(1);
        if (password != null && !password.isBlank()) {
            server.setPassword(password);
        }
        return config;
    }
}
