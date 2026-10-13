package com.example.yanpaMarket_backend.config;

import com.example.yanpaMarket_backend.common.cache.CacheNames;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * [무엇] Spring Cache(@Cacheable)를 켜고, 캐시 이름별 Caffeine 정책을 한 곳에서 정의한다.
 *
 * [왜 필요한가] 입찰 요청은 락 밖에서 "사용자 publicId → PK", "경매 publicId → PK" 조회를 매번 했다.
 *   부하 테스트(인기 경매 3건 × 입찰자 100명)에서 이 조회들이 DB 커넥션을 기다린 시간이
 *   서버 처리의 87%였고, 요청의 92%는 어차피 거절될 입찰이었다. 같은 답을 매번 DB에 묻지 않도록 캐시한다.
 *
 * [왜 Caffeine(서버 메모리)인가 — Redis가 아니라]
 *   두 값은 바뀌지 않거나 거의 안 바뀌어서 서버끼리 공유할 필요가 없다.
 *   Redis로 옮기면 네트워크 왕복이 생겨 캐시를 두는 의미가 줄어든다.
 *   나중에 "바뀌는 데이터"(경매 상세 등)를 캐시할 때는 Spring Cache 추상화 덕분에 저장소만 바꿀 수 있다.
 *
 * [캐시별 정책]
 *   auctionIdByPublicId   : 최대 1만 건, 마지막 접근 후 1시간 → 끝난 경매는 아무도 안 찾으니 자연스럽게 빠진다.
 *   userSummaryByPublicId : 최대 1만 건, "저장 후" 10분 → expireAfterAccess로 하면 계속 입찰하는 사용자는
 *                           영원히 갱신되지 않는다. 저장 시점 기준이어야 닉네임 등이 최대 10분 안에 반영된다.
 *   [주의] 사용자 상태(탈퇴/정지)나 닉네임을 바꾸는 기능을 추가하면 그 메서드에 반드시
 *          @CacheEvict(cacheNames = CacheNames.USER_SUMMARY_BY_PUBLIC_ID, key = "<publicId>")를 붙일 것.
 *          10분 만료는 그걸 빠뜨렸을 때의 안전장치일 뿐이다.
 *
 * [통계] 위 두 캐시는 recordStats()로 적중/미스 횟수를 기록한다(부하 테스트 [캐시] 리포트용).
 *
 * [등록 안 된 이름] setCaffeine의 기본 정책(최대 1천 건, 10분)이 적용된다.
 *   이름 오타로 새 캐시가 생겨도 무한히 커지지 않게 하려는 방어선이다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(Duration.ofMinutes(10)));

        manager.registerCustomCache(CacheNames.AUCTION_ID_BY_PUBLIC_ID, Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(Duration.ofHours(1))
                // 적중/미스 횟수 기록 — 부하 테스트 리포트의 [캐시] 섹션에서 읽는다. 비용은 조회마다 카운터 증가 수준.
                .recordStats()
                .build());

        manager.registerCustomCache(CacheNames.USER_SUMMARY_BY_PUBLIC_ID, Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(10))
                // 적중/미스 횟수 기록 — 부하 테스트 리포트의 [캐시] 섹션에서 읽는다. 비용은 조회마다 카운터 증가 수준.
                .recordStats()
                .build());

        return manager;
    }
}
