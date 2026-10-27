package com.example.yanpaMarket_backend.support;

import com.example.yanpaMarket_backend.config.RedisConfig;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * [무엇] Redis가 필요한 테스트의 공통 베이스. 진짜 Redis 컨테이너를 JVM당 한 번 띄운다.
 *
 * [왜 싱글톤인가 — @Container static이 아닌 이유] @Container static은 테스트 클래스가 끝나면 컨테이너를 멈춘다.
 *   그런데 Spring은 같은 설정의 컨텍스트를 클래스끼리 캐시해서 재사용하므로, 다음 클래스가 "죽은 포트"를 든
 *   컨텍스트를 받는다. 한 번 띄우고 끝까지 두면 이 문제가 없다(종료 시 Testcontainers가 정리한다).
 *
 * [Docker가 없으면 실패] 건너뛰면 핵심 테스트(사전 거절, 장애 대응)가 조용히 빠진 채 초록불이 된다.
 *   대신 원인을 바로 알 수 있는 메시지로 실패시킨다. @BeforeAll에서 확인해 Spring 컨텍스트 로딩보다 먼저 터진다.
 *
 * [테스트마다 FLUSHDB] H2는 컨텍스트마다 테이블을 새로 만들어 경매 PK가 다시 1부터 시작한다.
 *   Redis는 공유되므로 앞 테스트의 auction:{1}:bid가 남아 있으면 다음 테스트에서 거짓 BID_TOO_LOW가 난다.
 */
public abstract class RedisTestSupport {

    static final String DOCKER_REQUIRED =
            "Docker가 실행 중이 아니라 Redis 테스트를 실행할 수 없습니다. Docker Desktop을 켠 뒤 다시 실행하세요.";

    private static GenericContainer<?> redis;
    private static StringRedisTemplate shared;

    /** 컨테이너를 (처음 한 번) 띄워 돌려준다. Docker가 없으면 매번 같은 메시지로 실패한다. */
    protected static synchronized GenericContainer<?> redis() {
        if (redis == null) {
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                throw new IllegalStateException(DOCKER_REQUIRED);
            }
            GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    // 느린 Redis 재현(DEBUG SLEEP)용. Redis 7은 기본적으로 DEBUG 명령을 막는다.
                    .withCommand("redis-server", "--enable-debug-command", "yes");
            container.start();
            redis = container;
        }
        return redis;
    }

    protected static String redisHost() {
        return redis().getHost();
    }

    protected static int redisPort() {
        return redis().getMappedPort(6379);
    }

    @BeforeAll
    static void requireRedis() {
        redis();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", RedisTestSupport::redisHost);
        registry.add("spring.data.redis.port", RedisTestSupport::redisPort);
        registry.add("app.bid.precheck.enabled", () -> "true");
    }

    @BeforeEach
    void flushRedis() {
        sharedRedis().execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    /** 검증·준비용 템플릿. 명령 타임아웃을 넉넉히(2초) 둔다. */
    protected static synchronized StringRedisTemplate sharedRedis() {
        if (shared == null) {
            shared = newRedisTemplate(redisHost(), redisPort(), Duration.ofSeconds(2));
        }
        return shared;
    }

    /** 운영과 같은 ClientOptions(RedisConfig.clientOptions)로 독립 연결을 만든다. 다 쓰면 close로 닫는다. */
    protected static StringRedisTemplate newRedisTemplate(String host, int port, Duration commandTimeout) {
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(commandTimeout)
                .clientOptions(RedisConfig.clientOptions(Duration.ofMillis(200)))
                .build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port), client);
        factory.afterPropertiesSet();
        factory.start();
        return new StringRedisTemplate(factory);
    }

    protected static void close(StringRedisTemplate template) {
        ((LettuceConnectionFactory) template.getConnectionFactory()).destroy();
    }
}
