package com.example.yanpaMarket_backend.auction.multiserver;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.yanpaMarket_backend.support.RedisTestSupport;
import com.example.yanpaMarket_backend.support.multiserver.MultiServerTestSupport;
import com.example.yanpaMarket_backend.support.multiserver.ServerHandle;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * [무엇] 서버 2대 재현 테스트. 같은 앱을 서버 A·B로 두 번 띄워 MySQL·Redis를 공유하게 하고,
 *   서버가 2대일 때 깨지는 것(입찰 락, 서버 간 메시지 전파, 종료 스케줄러)을 숫자로 남긴다.
 * [왜 기본 실행에서 빠지나] 앱 2개 + MySQL 컨테이너라 느리다. -Dmultiserver=true 일 때만 돈다(부하 테스트와 같은 방식).
 * [왜 RedisTestSupport를 상속하나] 싱글톤 Redis 컨테이너, Docker 확인(@BeforeAll), 테스트마다 FLUSHDB(@BeforeEach)를 그대로 쓴다.
 *   이 클래스는 @SpringBootTest가 아니므로 그 클래스의 @DynamicPropertySource는 쓰이지 않는다.
 * 설계: docs/superpowers/specs/2026-10-30-multi-server-test-design.md (루트 저장소)
 */
@EnabledIfSystemProperty(named = "multiserver", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MultiServerBidTest extends RedisTestSupport {

    private static ServerHandle serverA;
    private static ServerHandle serverB;

    @BeforeAll
    static void startServers() {
        // [순서] A가 테이블을 만들고(create) B는 검증만 한다(validate). 반드시 A → B 순서.
        serverA = MultiServerTestSupport.startServer("A", "create", redisHost(), redisPort());
        try {
            serverB = MultiServerTestSupport.startServer("B", "validate", redisHost(), redisPort());
        } catch (RuntimeException e) {
            serverA.close();
            throw e;
        }
    }

    @AfterAll
    static void stopServers() {
        if (serverB != null) {
            serverB.close();
        }
        if (serverA != null) {
            serverA.close();
        }
    }

    @Test
    @Order(0)
    void 두_서버가_서로_다른_포트로_떠서_같은_DB를_본다() throws Exception {
        assertThat(serverA.port()).isPositive();
        assertThat(serverB.port()).isPositive().isNotEqualTo(serverA.port());

        HttpClient http = HttpClient.newHttpClient();
        for (ServerHandle server : new ServerHandle[] {serverA, serverB}) {
            HttpResponse<String> res = http.send(
                    HttpRequest.newBuilder(URI.create(server.baseUrl() + "/api/v1/auctions")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(res.statusCode()).as("서버 %s 공개 목록 응답", server.name()).isEqualTo(200);
        }
    }
}
