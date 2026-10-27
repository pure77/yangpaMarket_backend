package com.example.yanpaMarket_backend.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * [무엇] Lettuce(Redis 클라이언트) 연결 옵션.
 *
 * [왜 필요한가 — 끊긴 동안 기다리지 않기] Lettuce 기본값은 연결이 끊긴 동안 명령을 큐에 쌓아 두고 재연결을 기다린다.
 *   그러면 Redis가 죽어 있는 동안 모든 입찰이 명령 타임아웃(50ms)까지 기다린 뒤에야 fail-open으로 넘어간다.
 *   REJECT_COMMANDS면 끊긴 동안 즉시 실패 → 즉시 "통과"다. 재연결은 autoReconnect가 백그라운드에서 계속 시도한다.
 *
 * [왜 연결 타임아웃·TimeoutOptions까지 다시 넣나] builder.clientOptions(...)는 Boot가 만든 ClientOptions를
 *   통째로 바꾼다. Boot는 거기에 spring.data.redis.connect-timeout과 TimeoutOptions.enabled()를 넣어 두므로,
 *   여기서 빠뜨리면 연결 타임아웃 설정이 조용히 사라진다.
 *
 * [static clientOptions] Spring 없이 만드는 통합 테스트도 운영과 똑같은 옵션을 쓰게 하려고 밖에 연다.
 *
 * [알려진 한계 — 의도적으로 남겨 둠] REJECT_COMMANDS가 즉시 실패시키는 건 "한 번 연결됐다가 끊긴" 경우다.
 *   앱이 시작할 때부터 Redis에 연결할 수 없으면 공유 연결이 아직 없어서, 요청마다 새로 연결을 시도하고
 *   최대 connect-timeout(200ms)을 기다린 뒤 실패(→ 통과)한다. 운영에서는 Redis가 앱보다 먼저 떠 있으므로 드물다.
 *   문제가 되면: (B) connect-timeout을 50ms로 낮추거나, (C) 실패 후 잠시 Redis를 건너뛰는 차단 로직(서킷 브레이커)을 넣는다.
 *   동작은 RedisBidPriceGuardFailureTest.처음부터_연결할_수_없으면... 이 고정한다.
 */
@Configuration
public class RedisConfig {

    @Bean
    public LettuceClientConfigurationBuilderCustomizer bidGuardClientOptions(
            @Value("${spring.data.redis.connect-timeout:200ms}") Duration connectTimeout) {
        return builder -> builder.clientOptions(clientOptions(connectTimeout));
    }

    public static ClientOptions clientOptions(Duration connectTimeout) {
        return ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build())
                .timeoutOptions(TimeoutOptions.enabled())
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .autoReconnect(true)
                .build();
    }
}
