package com.example.yanpaMarket_backend.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

/** [무엇] 스프링 컨텍스트의 Redis 연결이 테스트 컨테이너를 가리키는지 확인한다(배선 검증). */
@SpringBootTest
@ActiveProfiles("test")
class RedisConnectionSmokeTest extends RedisTestSupport {

    @Autowired StringRedisTemplate redisTemplate;

    @Test
    void 컨텍스트의_Redis가_테스트_컨테이너에_붙는다() {
        redisTemplate.opsForValue().set("smoke", "ok");
        assertThat(sharedRedis().opsForValue().get("smoke")).isEqualTo("ok");
    }
}
