package com.example.yanpaMarket_backend.support.multiserver;

import org.springframework.context.ConfigurableApplicationContext;

/**
 * [무엇] 테스트가 띄운 서버 하나. 이름(A/B), 실제 포트, 그 서버의 스프링 컨텍스트.
 * [왜 컨텍스트를 들고 있나] 데이터 준비(리포지토리)와 서버별 카운터 확인(예: 사전 거절 통계)을 그 서버의 빈으로 하기 위해서다.
 */
public record ServerHandle(String name, int port, ConfigurableApplicationContext context) implements AutoCloseable {

    public <T> T bean(Class<T> type) {
        return context.getBean(type);
    }

    public String baseUrl() {
        return "http://localhost:" + port;
    }

    @Override
    public void close() {
        context.close();
    }
}
