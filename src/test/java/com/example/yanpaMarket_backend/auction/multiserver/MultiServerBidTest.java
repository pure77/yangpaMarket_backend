package com.example.yanpaMarket_backend.auction.multiserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionItemCondition;
import com.example.yanpaMarket_backend.auction.precheck.BidRedisKeys;
import com.example.yanpaMarket_backend.auction.precheck.GuardStats;
import com.example.yanpaMarket_backend.auction.precheck.RedisBidPriceGuard;
import com.example.yanpaMarket_backend.auction.repository.AuctionRepository;
import com.example.yanpaMarket_backend.common.util.PublicIdGenerator;
import com.example.yanpaMarket_backend.security.JwtProvider;
import com.example.yanpaMarket_backend.support.RedisTestSupport;
import com.example.yanpaMarket_backend.support.multiserver.MultiServerClient;
import com.example.yanpaMarket_backend.support.multiserver.MultiServerClient.BidResult;
import com.example.yanpaMarket_backend.support.multiserver.MultiServerTestSupport;
import com.example.yanpaMarket_backend.support.multiserver.ServerHandle;
import com.example.yanpaMarket_backend.user.domain.User;
import com.example.yanpaMarket_backend.user.domain.UserStatus;
import com.example.yanpaMarket_backend.user.repository.UserRepository;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
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
 *
 * [검사 두 종류]
 *   항상 엄격: 지금도 반드시 지켜져야 하는 것(정합성, 에러 0건, 자기 서버 메시지 수신). 어기면 실패.
 *   목표: 2·3단계에서 이룰 것. 상황별 Gate가 OBSERVE면 "알려진 미해결"로 기록만, STRICT면 실패.
 *   → 2단계(분산 락) 완료 시 SCENARIO_1_GATE, 3단계(서버 간 전파·스케줄러 중복 방지) 완료 시 SCENARIO_2/3_GATE를 STRICT로 바꾼다.
 * 설계: docs/superpowers/specs/2026-10-30-multi-server-test-design.md (루트 저장소)
 */
@EnabledIfSystemProperty(named = "multiserver", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MultiServerBidTest extends RedisTestSupport {

    enum Gate { OBSERVE, STRICT }

    private static final Gate SCENARIO_1_GATE = Gate.OBSERVE; // 2단계(분산 락) 완료 시 STRICT
    private static final Gate SCENARIO_2_GATE = Gate.OBSERVE; // 3단계(서버 간 전파) 완료 시 STRICT
    private static final Gate SCENARIO_3_GATE = Gate.OBSERVE; // 3단계(스케줄러 중복 방지) 완료 시 STRICT
    private static final Gate SCENARIO_4_GATE = Gate.STRICT;  // 지금도 통과해야 한다

    private static final long INCREMENT = 10_000L;
    private static final StringBuilder REPORT = new StringBuilder();

    private static ServerHandle serverA;
    private static ServerHandle serverB;
    private static MultiServerClient client;

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
        client = new MultiServerClient();
        line("━━ 서버 2대 재현 테스트 ━━ " + LocalDateTime.now().withNano(0)
                + "   (A :" + serverA.port() + ", B :" + serverB.port() + ")");
    }

    @AfterAll
    static void stopServers() throws IOException {
        // [왜 finally] 종료 중 예외가 나도 지금까지 쌓은 리포트는 반드시 남긴다.
        try {
            if (client != null) {
                client.close();
            }
            if (serverB != null) {
                serverB.close();
            }
            if (serverA != null) {
                serverA.close();
            }
        } finally {
            writeReport();
        }
    }

    // ── 도우미 ─────────────────────────────────────────────────────────────

    private static void line(String text) {
        REPORT.append(text).append('\n');
    }

    /** 목표 검사: 충족이면 기록, 미달이면 Gate에 따라 "알려진 미해결" 기록 또는 실패. */
    private static void checkTarget(Gate gate, boolean met, String target, String knownGap) {
        if (met) {
            line("  목표 " + target + " → 충족");
            return;
        }
        if (gate == Gate.STRICT) {
            line("  목표 " + target + " → 미달 (엄격 모드: 실패)");
            fail("목표 미달: " + target);
        }
        line("  목표 " + target + " → 미달  ← 알려진 미해결: " + knownGap);
    }

    /** 조건이 맞을 때까지 최대 timeout 동안 100ms 간격으로 확인한다. 고정 sleep 대신 쓴다. */
    private static boolean waitUntil(Duration timeout, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(100);
        }
        return condition.getAsBoolean();
    }

    private static void writeReport() throws IOException {
        String text = REPORT.toString();
        System.out.println(text);
        Path dir = Path.of("build", "reports", "multiserver");
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = dir.resolve("multi-server-" + stamp + ".txt");
        Files.writeString(file, text);
        System.out.println("리포트 파일: " + file.toAbsolutePath());
    }

    /** 상황 하나의 데이터: 판매자, 입찰자들과 그 토큰, 경매. MySQL을 공유하므로 상황마다 새로 만든다. */
    private record Fixture(User seller, List<User> bidders, List<String> tokens, Auction auction) {
    }

    /**
     * @param prefix    이메일 접두어(상황별로 다르게)
     * @param phoneCode 전화번호 가운데 4자리(상황별로 다르게 — 전화번호 유니크 충돌 방지)
     */
    private static Fixture fixture(String prefix, int phoneCode, int bidderCount, LocalDateTime endAt) {
        UserRepository users = serverA.bean(UserRepository.class);
        JwtProvider jwt = serverA.bean(JwtProvider.class); // 두 서버가 같은 JWT 키를 쓰므로 B에서도 통한다
        User seller = users.save(user(prefix + "-seller", phoneCode, 0));
        List<User> bidders = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        for (int i = 1; i <= bidderCount; i++) {
            User bidder = users.save(user(prefix + "-b" + i, phoneCode, i));
            bidders.add(bidder);
            tokens.add(jwt.createAccessToken(bidder.getPublicId(), false));
        }
        LocalDateTime now = LocalDateTime.now();
        Auction auction = serverA.bean(AuctionRepository.class).save(Auction.builder()
                .publicId(PublicIdGenerator.newUlid())
                .sellerUserId(seller.getId())
                .title("서버 2대 " + prefix).description("multi-server test")
                .category(AuctionCategory.ETC).itemCondition(AuctionItemCondition.USED)
                .startPrice(10_000L).buyNowPrice(null)
                .startAt(now.minusMinutes(1)).endAt(endAt)
                .build());
        return new Fixture(seller, bidders, tokens, auction);
    }

    private static User user(String emailPrefix, int phoneCode, int index) {
        return User.builder()
                .publicId(PublicIdGenerator.newUlid())
                .email(emailPrefix + "@ms.test")
                .nickname("유저" + index)
                .phone(String.format("010-%04d-%04d", phoneCode, index))
                .isAdmin(false).status(UserStatus.ACTIVE).marketingOptIn(false)
                .build();
    }

    private static Auction reload(Fixture f) {
        return serverA.bean(AuctionRepository.class).findByPublicId(f.auction().getPublicId()).orElseThrow();
    }

    private static Object redisPrice(Long auctionId) {
        return sharedRedis().opsForHash().get(BidRedisKeys.bid(auctionId), BidRedisKeys.PRICE);
    }

    // ── 테스트 ─────────────────────────────────────────────────────────────

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

    @Test
    @Order(4)
    void 상황4_Redis_사전_거절이_서버를_넘어_작동한다() {
        line("[상황 4] Redis 사전 거절 (모드: " + SCENARIO_4_GATE + ")");
        Fixture f = fixture("s4", 9104, 2, LocalDateTime.now().plusMinutes(10));
        String auctionId = f.auction().getPublicId();

        BidResult first = client.bid(serverA, auctionId, f.tokens().get(0), 30_000L);
        assertThat(first.status()).as("서버 A 30,000원 입찰").isEqualTo(201);

        RedisBidPriceGuard guardB = serverB.bean(RedisBidPriceGuard.class);
        GuardStats before = guardB.stats();
        BidResult low = client.bid(serverB, auctionId, f.tokens().get(1), 35_000L);
        GuardStats delta = guardB.stats().minus(before);

        assertThat(low.status()).as("서버 B 35,000원 입찰 상태").isEqualTo(400);
        assertThat(low.code()).isEqualTo("BID_TOO_LOW");
        assertThat(low.currentPrice()).isEqualTo(30_000L);
        assertThat(low.minimumBid()).isEqualTo(40_000L);
        // 서버 B가 락에 들어가지 않고 A가 기록한 Redis 가격으로 거절했다는 증거
        assertThat(delta.precheckTooLow()).as("서버 B 사전 거절 횟수").isEqualTo(1);

        BidResult ok = client.bid(serverB, auctionId, f.tokens().get(1), 40_000L);
        assertThat(ok.status()).as("서버 B 40,000원 입찰").isEqualTo(201);
        assertThat(redisPrice(f.auction().getId())).isEqualTo("40000");

        line("  A 30,000 성공 → B 35,000 BID_TOO_LOW(락 앞 거절 " + delta.precheckTooLow() + "건) → B 40,000 성공, Redis 40,000");
        line("  통과");
    }
}
