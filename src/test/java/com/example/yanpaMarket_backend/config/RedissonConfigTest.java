package com.example.yanpaMarket_backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

class RedissonConfigTest {

    @Test
    void Redis가_없어도_클라이언트_생성은_실패하지_않는다() throws Exception {
        int deadPort;
        try (ServerSocket s = new ServerSocket(0)) {
            deadPort = s.getLocalPort(); // 닫힌 뒤엔 아무도 듣지 않는 포트
        }
        Config config = RedissonConfig.config("localhost", deadPort, "", 0,
                Duration.ofMillis(50), Duration.ofMillis(200));

        RedissonClient client = Redisson.create(config); // lazyInitialization=true면 여기서 접속하지 않는다
        try {
            assertThat(client.isShutdown()).isFalse();
        } finally {
            client.shutdown();
        }
    }

    @Test
    void Spring_Redis_설정값이_그대로_옮겨진다() {
        Config config = RedissonConfig.config("redis.local", 6380, "pw", 3,
                Duration.ofMillis(50), Duration.ofMillis(200));

        assertThat(config.isLazyInitialization()).isTrue();
        assertThat(config.useSingleServer().getAddress()).isEqualTo("redis://redis.local:6380");
        assertThat(config.useSingleServer().getDatabase()).isEqualTo(3);
        assertThat(config.useSingleServer().getTimeout()).isEqualTo(50);
        assertThat(config.useSingleServer().getConnectTimeout()).isEqualTo(200);
        assertThat(config.useSingleServer().getRetryAttempts()).isZero();
        assertThat(config.useSingleServer().getPassword()).isEqualTo("pw");
    }
}
