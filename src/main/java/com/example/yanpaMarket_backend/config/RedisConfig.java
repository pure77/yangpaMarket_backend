package com.example.yanpaMarket_backend.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import java.time.Duration;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * [무엇] Lettuce(Redis 클라이언트) 연결 옵션 중 입찰 사전 거절에 필요한 두 가지만 Boot의 옵션 위에 얹는다.
 *
 * [왜 필요한가 — 끊긴 동안 기다리지 않기] Lettuce 기본값은 연결이 끊긴 동안 명령을 큐에 쌓아 두고 재연결을 기다린다.
 *   그러면 Redis가 죽어 있는 동안 모든 입찰이 명령 타임아웃(50ms)까지 기다린 뒤에야 fail-open으로 넘어간다.
 *   REJECT_COMMANDS면 끊긴 동안 즉시 실패 → 즉시 "통과"다. 재연결은 autoReconnect가 백그라운드에서 계속 시도한다.
 *
 * [왜 LettuceClientOptionsBuilderCustomizer인가] Boot가 만든 ClientOptions.Builder를 그대로 받아 두 값만 바꾼다.
 *   builder.clientOptions(...)로 통째로 갈아끼우면 Boot가 넣은 연결 타임아웃, TimeoutOptions,
 *   spring.data.redis.ssl.bundle의 SslOptions, 클러스터 모드의 ClusterClientOptions·토폴로지 갱신이 조용히 사라진다.
 *   이제 그것들은 모두 보존된다.
 *
 * [static 헬퍼] Spring 없이 만드는 통합 테스트도 같은 두 옵션을 쓰게 하려고 밖에 연다(applyGuardOptions).
 *
 * [알려진 한계 — 의도적으로 남겨 둠, 결정 A] REJECT_COMMANDS가 즉시 실패시키는 건 "한 번 연결됐다가 끊긴" 경우다.
 *   앱이 시작할 때부터 Redis에 연결할 수 없으면 공유 연결이 아직 없어서, 사전 거절(precheck)과 기록(record, 입찰 락 안에서 실행)이
 *   각각 최대 connect-timeout(200ms)을 기다린 뒤 실패(→ 통과)한다. 패킷을 버리는 방화벽 같은 경우가 그렇다.
 *   운영에서는 Redis가 앱보다 먼저 떠 있으므로 드물다.
 *   문제가 되면: (B) connect-timeout을 낮추거나, (C) 실패 후 잠시 Redis를 건너뛰는 차단 로직(서킷 브레이커)을 넣는다.
 *   동작은 RedisBidPriceGuardFailureTest.처음부터_연결할_수_없으면... 이 고정한다.
 */
@Configuration
public class RedisConfig {

    @Bean
    public LettuceClientOptionsBuilderCustomizer bidGuardClientOptions() {
        return RedisConfig::applyGuardOptions;
    }

    /** Boot(또는 테스트)의 빌더에 끊긴 동안 즉시 실패 + 자동 재연결만 지정한다. */
    public static ClientOptions.Builder applyGuardOptions(ClientOptions.Builder builder) {
        return builder
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .autoReconnect(true);
    }

    /** Boot 빌더가 없는 테스트용: 연결 타임아웃·TimeoutOptions를 Boot처럼 채우고 가드 옵션을 얹는다. */
    public static ClientOptions testClientOptions(Duration connectTimeout) {
        return applyGuardOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build())
                .timeoutOptions(TimeoutOptions.enabled()))
                .build();
    }
}
