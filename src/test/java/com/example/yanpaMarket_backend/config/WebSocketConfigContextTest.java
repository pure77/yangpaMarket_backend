package com.example.yanpaMarket_backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;

/** WebSocket 설정이 켜지면 SimpMessagingTemplate 빈이 자동 등록된다. */
@SpringBootTest
@ActiveProfiles("test")
class WebSocketConfigContextTest {

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void 메시징_템플릿_빈이_등록된다() {
        assertThat(messagingTemplate).isNotNull();
    }
}
