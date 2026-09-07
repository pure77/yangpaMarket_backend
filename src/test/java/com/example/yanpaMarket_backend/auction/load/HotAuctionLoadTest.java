package com.example.yanpaMarket_backend.auction.load;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import com.example.yanpaMarket_backend.auction.concurrency.BidLock;
import com.example.yanpaMarket_backend.auction.concurrency.InMemoryBidLock;
import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionItemCondition;
import com.example.yanpaMarket_backend.auction.dto.BidUpdateMessage;
import com.example.yanpaMarket_backend.auction.repository.AuctionRepository;
import com.example.yanpaMarket_backend.auction.repository.BidRepository;
import com.example.yanpaMarket_backend.common.util.PublicIdGenerator;
import com.example.yanpaMarket_backend.security.JwtProvider;
import com.example.yanpaMarket_backend.user.domain.User;
import com.example.yanpaMarket_backend.user.domain.UserStatus;
import com.example.yanpaMarket_backend.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ActiveProfilesResolver;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

/**
 * [무엇] "인기 경매에 입찰이 몰리는" 상황을 재현하는 부하 테스트.
 *        경매 1건에 구독자 N명이 붙어 있고 입찰자 M명이 계속 입찰을 던진다.
 *
 * [왜 부하 도구(k6/Gatling)가 아니라 JUnit인가]
 *   재려는 핵심 지표 중 "락 보유/대기 시간"은 서버 내부 값이라 외부 도구로는 볼 수 없다.
 *   밖에서는 "응답 800ms"만 보이고, 그중 얼마가 락 대기이고 얼마가 트랜잭션인지 알 수 없는데,
 *   그 둘은 조치가 정반대다(임계구역 단축 vs DB/커넥션풀). 같은 JVM 안이면 그냥 잴 수 있다.
 *   또 "POST 전송 → 다른 구독자 수신"이라는 크로스 프로토콜 지연도 ConcurrentHashMap 하나로 끝난다.
 *
 * [실행 방법] 평소 ./gradlew test 에서는 건너뛴다(수십 초 걸림).
 *   ./gradlew.bat test --tests '*HotAuctionLoadTest*' -Dloadtest=true
 *   파라미터: -Dload.subscribers=100 -Dload.bidders=20 -Dload.seconds=60 -Dload.thinkMillis=100
 *   리포트: 콘솔 + build/reports/load/hot-auction-{시각}.txt
 *
 * [측정 분해 — 전부 "요청별"로 확정한다]
 *   POST 전송 ─┐  ← 클라이언트 시작
 *              │ 네트워크+클라이언트 = 응답 - 서버처리   (같은 JVM이면 여기가 부풀어 오른다)
 *              ├─ 서버 처리 합계                        ← ServerTimingFilter가 직접 측정
 *              │    (a) 진입·조회 = 서버처리 - 락구간    JWT 필터 + 락 밖 조회 2회
 *              │    (b) 락 대기                          ← TimingBidLock
 *              │    (c) 트랜잭션 = 보유 - broadcast       재조회/검증/INSERT/커밋
 *              │    (d) broadcast                        ← SimpMessagingTemplate spy
 *   201 수신 ──┘
 *              │ (f) 팬아웃      발행 → 각 구독자 수신
 *   MESSAGE ───┘
 *
 *   [왜 요청별인가] 예전엔 백분위끼리 뺐다(p95(응답) - p95(락)). 그런데
 *   p95(A+B) != p95(A)+p95(B)라 존재하지 않는 값이 나오고, 클라이언트 비용까지 섞였다.
 *   지금은 RequestTiming(ThreadLocal)으로 한 요청의 구간들을 모아 필터가 한 번에 기록한다.
 *
 * [주의 — DB가 무엇이냐에 따라 해석이 달라진다]
 *   기본(test 프로필)은 H2 인메모리다. 디스크 fsync가 없어 트랜잭션이 실제 MySQL보다 훨씬 빠르고,
 *   그래서 (c)가 작게 나오고 커넥션 풀도 잘 안 마른다. 즉 "락이 병목"으로 보이기 쉽다.
 *   DB 병목을 제대로 판단하려면 MySQL로 붙여야 한다.
 *     ./gradlew.bat test --tests '*HotAuctionLoadTest*' -Dloadtest=true -Dload.profile=mysql
 *   리포트 상단에 실제 DB 제품명을 찍으므로 어느 쪽으로 돌렸는지 반드시 확인할 것.
 *
 * [데이터 안전] 이 테스트는 deleteAll을 호출하고 프로필 설정상 ddl-auto=create-drop이다.
 *   개발 DB(yanpa_market)에 붙으면 데이터가 전부 사라지므로, assertSafeDatabase()가
 *   H2이거나 이름에 'loadtest'가 들어간 DB가 아니면 아무 작업도 하지 않고 즉시 실패시킨다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(resolver = HotAuctionLoadTest.LoadProfileResolver.class)
@EnabledIfSystemProperty(named = "loadtest", matches = "true")
class HotAuctionLoadTest {

    /**
     * 기본은 test(H2), -Dload.profile=mysql 이면 MySQL 프로필로 돈다.
     * H2는 fsync가 없어 트랜잭션이 실제보다 빠르므로, DB 병목을 판단하려면 mysql로 돌려야 한다.
     */
    static class LoadProfileResolver implements ActiveProfilesResolver {
        @Override
        @NonNull
        public String[] resolve(@NonNull Class<?> testClass) {
            return new String[] {System.getProperty("load.profile", "test")};
        }
    }

    /**
     * [메인과 같은 환경변수를 그대로 쓴다] DB_URL / DB_USERNAME / DB_PASSWORD.
     * 다만 DB 이름 뒤에 "_loadtest"를 붙여 개발 DB 옆에 별도 DB를 만들어 붙는다.
     *   DB_URL = jdbc:mysql://localhost:3306/yanpa_market
     *   실제   → jdbc:mysql://localhost:3306/yanpa_market_loadtest?createDatabaseIfNotExist=true...
     *
     * 이 테스트는 deleteAll을 호출하고 ddl-auto=create-drop이라 개발 DB에 붙으면 데이터가 사라진다.
     * 로컬이라도 계정/경매를 다시 만드는 수고가 있으니 이름만 분리했다. 추가 설정은 전혀 필요 없다.
     * (정말 특정 URL로 붙어야 하면 LOAD_DB_URL로 덮어쓸 수 있다)
     */
    @DynamicPropertySource
    static void resolveLoadTestDatabase(DynamicPropertyRegistry registry) {
        if (!"mysql".equals(System.getProperty("load.profile", "test"))) {
            return; // H2로 돌 때는 건드리지 않는다
        }
        if (System.getenv("LOAD_DB_URL") != null) {
            return; // 명시적으로 지정했으면 그대로 둔다
        }
        String devUrl = System.getenv("DB_URL");
        if (devUrl == null || devUrl.isBlank()) {
            return; // 없으면 application-mysql.properties의 기본값을 쓴다
        }
        registry.add("spring.datasource.url", () -> toLoadTestUrl(devUrl));
    }

    /** "jdbc:mysql://host:port/db?params" → "jdbc:mysql://host:port/db_loadtest?params+자동생성" */
    static String toLoadTestUrl(String devUrl) {
        int queryAt = devUrl.indexOf('?');
        String base = queryAt < 0 ? devUrl : devUrl.substring(0, queryAt);
        String query = queryAt < 0 ? "" : devUrl.substring(queryAt + 1);

        int slashAt = base.lastIndexOf('/');
        String loadBase = base.substring(0, slashAt + 1) + base.substring(slashAt + 1) + "_loadtest";

        if (!query.contains("createDatabaseIfNotExist")) {
            query = query.isBlank() ? "createDatabaseIfNotExist=true" : query + "&createDatabaseIfNotExist=true";
        }
        return loadBase + "?" + query;
    }

    /** 최소 입찰 인상폭. Auction 생성자의 minimumBidIncrement와 같아야 한다. */
    private static final long INCREMENT = 10_000L;

    private final int subscriberCount = Integer.getInteger("load.subscribers", 100);
    private final int bidderCount = Integer.getInteger("load.bidders", 20);
    private final int durationSeconds = Integer.getInteger("load.seconds", 60);
    private final int thinkMillis = Integer.getInteger("load.thinkMillis", 100);

    @LocalServerPort int port;
    @Autowired UserRepository userRepository;
    @Autowired AuctionRepository auctionRepository;
    @Autowired BidRepository bidRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired DataSource dataSource;
    @Autowired LoadMetrics metrics;

    // broadcast 소요 시간과 "발행 시각"을 얻기 위해 spy로 감싼다. 실제 전송은 그대로 수행된다.
    @MockitoSpyBean SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * BidLock을 측정용 데코레이터로 바꿔 끼운다.
     * @Primary라 BidService는 이 빈을 주입받고, 실제 잠금은 InMemoryBidLock에 그대로 위임된다.
     */
    @TestConfiguration
    static class LoadTestConfig {

        @Bean
        LoadMetrics loadMetrics() {
            return new LoadMetrics();
        }

        @Bean
        @Primary
        BidLock timingBidLock(InMemoryBidLock delegate) {
            return new TimingBidLock(delegate);
        }

        /**
         * 서버 처리 시간을 직접 재는 필터. Filter 타입 빈이라 스프링 부트가 자동 등록하고,
         * Ordered.HIGHEST_PRECEDENCE라 시큐리티 필터체인보다 앞에 선다(JWT 검증 시간도 포함).
         */
        @Bean
        ServerTimingFilter serverTimingFilter(LoadMetrics metrics) {
            return new ServerTimingFilter(metrics);
        }
    }

    /**
     * 부하 테스트가 모으는 모든 수치. 스레드 여러 개가 동시에 쓴다.
     *
     * [구간은 요청별로 확정된다] ServerTimingFilter가 요청 하나가 끝날 때
     * entry/lockWait/lockHold/broadcast/transaction 을 한 번에 기록한다.
     * 백분위끼리 빼서 추정하지 않는다.
     */
    static class LoadMetrics {
        final LoadSamples serverHandling = new LoadSamples();  // 서버 안쪽 전체 (필터 측정)
        final LoadSamples entry = new LoadSamples();           // (a) 진입·조회 = 서버전체 - 락구간
        final LoadSamples lockWait = new LoadSamples();        // (b)
        final LoadSamples lockHold = new LoadSamples();        // (c)+(d) — 성공/거절 전부
        final LoadSamples transaction = new LoadSamples();     // (c) = 보유 - broadcast
        // [성공만] 거절은 SELECT 후 즉시 예외라 매우 빠르다. 경합이 심하면 요청의 95%가 거절이라
        //   전체 통계가 거절 쪽으로 끌려가 "단일 경매 상한"이 몇 배 부풀려진다.
        //   실제 쓰기 비용(INSERT+UPDATE+커밋)은 성공한 입찰로만 재야 한다.
        final LoadSamples lockHoldSuccess = new LoadSamples();
        final LoadSamples transactionSuccess = new LoadSamples();
        final LoadSamples broadcast = new LoadSamples();       // (d)
        final LoadSamples responseTime = new LoadSamples();    // 입찰 API 응답 (클라이언트 측정)
        final LoadSamples propagation = new LoadSamples();     // POST 전송 → 구독자 수신
        final LoadSamples fanout = new LoadSamples();          // (f) 발행 → 구독자 수신

        /** 금액을 키로 "보낸 시각"과 "발행 시각"을 공유한다 — 크로스 프로토콜 상관용. */
        final ConcurrentHashMap<Long, Long> sentAtByPrice = new ConcurrentHashMap<>();
        final ConcurrentHashMap<Long, Long> publishedAtByPrice = new ConcurrentHashMap<>();

        final AtomicInteger created201 = new AtomicInteger();
        final AtomicInteger tooLow400 = new AtomicInteger();
        final AtomicInteger conflict409 = new AtomicInteger();
        final AtomicInteger serverError500 = new AtomicInteger();
        final AtomicInteger otherStatus = new AtomicInteger();
        final AtomicInteger ioError = new AtomicInteger();

        /** 입찰자들이 "현재 화면에 보이는 현재가"로 삼는 값. 구독 메시지와 성공 응답으로 갱신된다. */
        final AtomicLong latestPrice = new AtomicLong();

        // Hikari 풀 샘플링
        // [왜 평균과 "대기 발생 비율"까지 세나] 최대값 하나로 판단하면 시작 순간 스파이크 한 번에
        //   "풀 부족"으로 오판한다. 실제로 평균 active 1.0(사용률 10%)인데 최대 pending 10이 찍혀
        //   풀 부족이라고 잘못 결론 낸 적이 있다. 지속성으로 판단해야 한다.
        final AtomicInteger poolSamples = new AtomicInteger();
        final AtomicLong poolActiveSum = new AtomicLong();
        final AtomicLong poolIdleSum = new AtomicLong();
        final AtomicLong poolPendingSum = new AtomicLong();
        final AtomicInteger poolPendingMax = new AtomicInteger();
        final AtomicInteger poolActiveMax = new AtomicInteger();
        final AtomicInteger poolPendingSamples = new AtomicInteger(); // pending>0 이었던 샘플 수

        /** 스윕 모드에서 구간마다 처음부터 다시 재기 위해 전부 비운다. latestPrice는 경매가 이어지므로 남긴다. */
        void reset() {
            serverHandling.reset(); entry.reset(); lockWait.reset(); lockHold.reset();
            transaction.reset(); broadcast.reset(); responseTime.reset(); propagation.reset(); fanout.reset();
            lockHoldSuccess.reset(); transactionSuccess.reset();
            sentAtByPrice.clear(); publishedAtByPrice.clear();
            created201.set(0); tooLow400.set(0); conflict409.set(0);
            serverError500.set(0); otherStatus.set(0); ioError.set(0);
            poolSamples.set(0); poolActiveSum.set(0); poolIdleSum.set(0); poolPendingSum.set(0);
            poolPendingMax.set(0); poolActiveMax.set(0); poolPendingSamples.set(0);
        }
    }

    /** 스윕 한 구간의 요약. 마지막에 표로 묶어 "어디서 꺾이는지" 보여준다. */
    record PhaseResult(int bidders, double successPerSec, double rejectRate,
                       double respP50, double respP95, double propP95,
                       double entryP95, double waitP95, double avgPending) {
    }

    /**
     * [안전장치] 이 테스트는 데이터를 지우고(deleteAll), 프로필 설정상 ddl-auto=create-drop이라
     * 컨텍스트 종료 시 테이블을 drop한다. 개발 DB(yanpa_market)를 가리키면 데이터가 전부 날아간다.
     * 그래서 H2이거나 이름에 'loadtest'가 들어간 DB가 아니면 아무것도 하지 않고 즉시 실패시킨다.
     */
    private void assertSafeDatabase() throws Exception {
        String url;
        try (Connection connection = dataSource.getConnection()) {
            url = connection.getMetaData().getURL();
        }
        boolean safe = url.contains("h2:mem") || url.toLowerCase().contains("loadtest");
        if (!safe) {
            throw new IllegalStateException(
                    "부하 테스트는 H2 또는 이름에 'loadtest'가 들어간 전용 DB에서만 실행할 수 있습니다.\n"
                            + "  현재 접속 URL: " + url + "\n"
                            + "  개발 DB에 붙으면 deleteAll + ddl-auto=create-drop 으로 데이터가 사라집니다.");
        }
    }

    @Test
    void 인기_경매_입찰_폭주_부하() throws Exception {
        assertSafeDatabase();

        // ── 준비: 판매자 1명, 경매 1건, 입찰자 N명 ────────────────────────────────
        cleanUp();
        User seller = userRepository.save(user("seller-load@y.com", "셀러", "010-9999-0000"));
        LocalDateTime now = LocalDateTime.now();
        Auction auction = auctionRepository.save(Auction.builder()
                .publicId(PublicIdGenerator.newUlid())
                .sellerUserId(seller.getId())
                .title("인기 경매").description("부하 테스트")
                .category(AuctionCategory.ETC).itemCondition(AuctionItemCondition.USED)
                .startPrice(10_000L).buyNowPrice(null)
                .startAt(now.minusMinutes(1)).endAt(now.plusHours(2)) // 테스트 중 종료되지 않게
                .build());
        String auctionPublicId = auction.getPublicId();
        metrics.latestPrice.set(auction.getCurrentPrice());

        // 스윕 구간 중 가장 큰 입찰자 수만큼 계정을 미리 만든다
        int maxBidders = sweepPhases().stream().mapToInt(Integer::intValue).max().orElse(bidderCount);
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < maxBidders; i++) {
            User bidder = userRepository.save(
                    user("bidder-load-" + i + "@y.com", "입찰자" + i, String.format("010-7%03d-0000", i)));
            tokens.add(jwtProvider.createAccessToken(bidder.getPublicId(), false));
        }

        // broadcast 소요 시간 + 발행 시각 기록 (실제 전송은 callRealMethod로 그대로 수행)
        // 소요 시간은 LoadSamples에 바로 넣지 않고 ThreadLocal 슬롯에 놓는다 —
        // 이 호출은 락 보유 구간 안, 즉 요청 스레드 위에서 일어나므로
        // ServerTimingFilter가 "그 요청의" broadcast로 정확히 묶어 기록한다.
        doAnswer(invocation -> {
            long start = System.nanoTime();
            Object payload = invocation.getArgument(1);
            if (payload instanceof BidUpdateMessage message) {
                metrics.publishedAtByPrice.putIfAbsent(message.currentPrice(), start);
            }
            try {
                return invocation.callRealMethod();
            } finally {
                RequestTiming.SLOT.get()[RequestTiming.BROADCAST] = System.nanoTime() - start;
            }
        }).when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        // ── 구독자 N명 접속 ──────────────────────────────────────────────────────
        WebSocketStompClient stompClient = new WebSocketStompClient(
                new SockJsClient(List.<Transport>of(new WebSocketTransport(new StandardWebSocketClient()))));
        List<StompSession> sessions = new ArrayList<>();
        for (int i = 0; i < subscriberCount; i++) {
            StompSession session = stompClient
                    .connectAsync("http://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {})
                    .get(10, TimeUnit.SECONDS);
            session.subscribe("/topic/auction/" + auctionPublicId, frameHandler());
            sessions.add(session);
        }
        Thread.sleep(1000); // 구독 등록이 브로커에 반영될 시간

        // ── 동시성 스윕: -Dload.sweep=20,40,60,100 이면 값마다 순차 측정 ──────────
        // 구독자와 경매는 그대로 두고 입찰자 수만 바꾼다. 그래야 무엇 때문에 달라졌는지 구분된다.
        // Hikari 샘플러는 구간마다 새로 띄운다(구간별 평균/대기비율을 따로 재야 하므로).
        List<Integer> phases = sweepPhases();
        List<PhaseResult> results = new ArrayList<>();

        for (int phaseBidders : phases) {
            metrics.reset();                       // 구간마다 통계를 새로 모은다
            int bidCountBefore = auctionRepository.findByPublicId(auctionPublicId).orElseThrow().getBidCount();

            HikariDataSource hikari = unwrapHikari();
            Thread poolSampler = startPoolSampler(hikari);
            long wallNanos = runBiddingPhase(auctionPublicId, tokens, phaseBidders);
            Thread.sleep(1000);                    // 마지막 메시지들이 도착할 시간
            poolSampler.interrupt();

            Auction reloaded = auctionRepository.findByPublicId(auctionPublicId).orElseThrow();
            long persistedBids = bidRepository.findByAuctionIdOrderByCreatedAtDesc(
                    reloaded.getId(), org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements();

            String report = buildReport(reloaded, persistedBids, wallNanos, hikari,
                    phaseBidders, reloaded.getBidCount() - bidCountBefore);
            System.out.println(report);
            writeReport(report);
            results.add(snapshot(phaseBidders, wallNanos));
        }

        if (results.size() > 1) {
            String table = sweepTable(results);
            System.out.println(table);
            writeReport(table);
        }

        // ── 정리 ────────────────────────────────────────────────────────────────
        sessions.forEach(StompSession::disconnect);
        stompClient.stop();
        cleanUp();
    }

    /** -Dload.sweep=20,40,60,100 → [20,40,60,100]. 없으면 단일 구간(-Dload.bidders). */
    private List<Integer> sweepPhases() {
        String sweep = System.getProperty("load.sweep");
        if (sweep == null || sweep.isBlank()) {
            return List.of(bidderCount);
        }
        List<Integer> phases = new ArrayList<>();
        for (String part : sweep.split(",")) {
            phases.add(Integer.parseInt(part.trim()));
        }
        return phases;
    }

    /** 입찰자 n명으로 durationSeconds 동안 부하를 준다. 반환값은 실제 소요 나노초. */
    private long runBiddingPhase(String auctionPublicId, List<String> tokens, int n) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch done = new CountDownLatch(n);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(durationSeconds);
        long wallStart = System.nanoTime();

        for (int i = 0; i < n; i++) {
            String token = tokens.get(i);
            pool.submit(() -> {
                try {
                    while (System.nanoTime() < deadline) {
                        placeOneBid(auctionPublicId, token);
                        Thread.sleep(ThreadLocalRandom.current().nextInt(thinkMillis / 2, thinkMillis * 2));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        done.await(durationSeconds + 30L, TimeUnit.SECONDS);
        pool.shutdown();
        return System.nanoTime() - wallStart;
    }

    private PhaseResult snapshot(int bidders, long wallNanos) {
        int created = metrics.created201.get();
        long total = created + metrics.tooLow400.get() + metrics.conflict409.get() + metrics.serverError500.get();
        int n = metrics.poolSamples.get();
        return new PhaseResult(
                bidders,
                created / (wallNanos / 1_000_000_000.0),
                total > 0 ? metrics.tooLow400.get() / (double) total : 0,
                metrics.responseTime.percentileMillis(0.50),
                metrics.responseTime.percentileMillis(0.95),
                metrics.propagation.percentileMillis(0.95),
                metrics.entry.percentileMillis(0.95),
                metrics.lockWait.percentileMillis(0.95),
                n > 0 ? metrics.poolPendingSum.get() / (double) n : 0);
    }

    /** 스윕 결과를 한 표로 묶고 최적 동시성을 짚어준다. */
    private String sweepTable(List<PhaseResult> results) {
        PhaseResult best = results.get(0);
        for (PhaseResult r : results) {
            if (r.successPerSec() > best.successPerSec()) {
                best = r;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%n━━ 동시성 스윕 ━━ 구독자 %d / 각 %d초 ━━%n", subscriberCount, durationSeconds));
        sb.append(String.format("%-8s %9s %8s %9s %9s %9s %9s %9s %9s%n",
                "입찰자", "성공/s", "거절률", "응답p50", "응답p95", "전파p95", "(a)p95", "(b)p95", "pending"));
        for (PhaseResult r : results) {
            sb.append(String.format("%-8d %9.1f %7.0f%% %8.1fms %8.1fms %8.1fms %8.1fms %8.1fms %9.1f%s%n",
                    r.bidders(), r.successPerSec(), r.rejectRate() * 100,
                    r.respP50(), r.respP95(), r.propP95(), r.entryP95(), r.waitP95(), r.avgPending(),
                    r == best ? "  <- 최대" : ""));
        }
        sb.append(String.format("%n→ 최적 동시성: 입찰자 %d명 부근 (성공 %.1f/s)%n", best.bidders(), best.successPerSec()));
        PhaseResult last = results.get(results.size() - 1);
        if (best != last && best.successPerSec() > 0) {
            sb.append(String.format("→ 입찰자 %d명에서는 %.0f%% 수준으로 떨어진다 — 동시성을 늘릴수록 손해인 구간%n",
                    last.bidders(), last.successPerSec() / best.successPerSec() * 100));
        }
        return sb.toString();
    }

    /** 입찰 1건: 화면에 보이는 현재가 위로 금액을 정하고 실제 HTTP POST를 보낸다. */
    private void placeOneBid(String auctionPublicId, String token) {
        long base = metrics.latestPrice.get();
        long amount = base + INCREMENT * (1 + ThreadLocalRandom.current().nextInt(3));
        long start = System.nanoTime();
        metrics.sentAtByPrice.putIfAbsent(amount, start);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/v1/auctions/" + auctionPublicId + "/bids"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString("{\"amount\":" + amount + "}"))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            metrics.responseTime.add(System.nanoTime() - start);

            switch (response.statusCode()) {
                case 201 -> {
                    metrics.created201.incrementAndGet();
                    metrics.latestPrice.accumulateAndGet(amount, Math::max);
                }
                case 400 -> metrics.tooLow400.incrementAndGet();  // BID_TOO_LOW — 경합에서 밀림(정상)
                case 409 -> metrics.conflict409.incrementAndGet(); // 락이 뚫린 신호
                case 500 -> metrics.serverError500.incrementAndGet();
                default -> metrics.otherStatus.incrementAndGet();
            }
        } catch (Exception e) {
            metrics.ioError.incrementAndGet();
        }
    }

    /** 구독자가 BID_UPDATE를 받을 때마다 전파 지연/팬아웃을 기록하고 현재가를 갱신한다. */
    private StompFrameHandler frameHandler() {
        return new StompFrameHandler() {
            @Override @NonNull public Type getPayloadType(@NonNull StompHeaders headers) {
                return byte[].class; // 기본 SimpleMessageConverter — 원문 그대로 받아 직접 파싱
            }

            @Override public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                long received = System.nanoTime();
                try {
                    JsonNode node = objectMapper.readTree((byte[]) payload);
                    if (!"BID_UPDATE".equals(node.path("type").asText())) {
                        return;
                    }
                    long price = node.path("currentPrice").asLong();
                    metrics.latestPrice.accumulateAndGet(price, Math::max);

                    Long sentAt = metrics.sentAtByPrice.get(price);
                    if (sentAt != null) {
                        metrics.propagation.add(received - sentAt);
                    }
                    Long publishedAt = metrics.publishedAtByPrice.get(price);
                    if (publishedAt != null) {
                        metrics.fanout.add(received - publishedAt);
                    }
                } catch (Exception ignored) {
                    // 파싱 실패는 측정에서 제외한다(테스트 자체를 깨뜨리지 않는다)
                }
            }
        };
    }

    private Thread startPoolSampler(HikariDataSource hikari) {
        Thread thread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                if (hikari != null) {
                    var mx = hikari.getHikariPoolMXBean();
                    int pending = mx.getThreadsAwaitingConnection();
                    int active = mx.getActiveConnections();
                    metrics.poolSamples.incrementAndGet();
                    metrics.poolActiveSum.addAndGet(active);
                    metrics.poolIdleSum.addAndGet(mx.getIdleConnections());
                    metrics.poolPendingSum.addAndGet(pending);
                    metrics.poolPendingMax.accumulateAndGet(pending, Math::max);
                    metrics.poolActiveMax.accumulateAndGet(active, Math::max);
                    if (pending > 0) {
                        metrics.poolPendingSamples.incrementAndGet();
                    }
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private HikariDataSource unwrapHikari() {
        try {
            return dataSource.unwrap(HikariDataSource.class);
        } catch (Exception e) {
            return null; // Hikari가 아니면 DB 섹션을 생략한다
        }
    }

    private String databaseProduct() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName()
                    + " " + connection.getMetaData().getDatabaseProductVersion();
        } catch (Exception e) {
            return "unknown";
        }
    }

    // ── 리포트 ──────────────────────────────────────────────────────────────────

    private String buildReport(Auction auction, long persistedBids, long wallNanos, HikariDataSource hikari,
                               int phaseBidders, int bidCountDelta) {
        double wallSeconds = wallNanos / 1_000_000_000.0;

        // 클라이언트 측정
        double respP50 = metrics.responseTime.percentileMillis(0.50);
        double respP95 = metrics.responseTime.percentileMillis(0.95);
        double respP99 = metrics.responseTime.percentileMillis(0.99);

        // 서버 측정 — 전부 요청별로 확정된 값이라 백분위 뺄셈이 없다
        double srvP50 = metrics.serverHandling.percentileMillis(0.50);
        double srvP95 = metrics.serverHandling.percentileMillis(0.95);
        double entryP50 = metrics.entry.percentileMillis(0.50);
        double entryP95 = metrics.entry.percentileMillis(0.95);
        double waitP50 = metrics.lockWait.percentileMillis(0.50);
        double waitP95 = metrics.lockWait.percentileMillis(0.95);
        double holdP50 = metrics.lockHold.percentileMillis(0.50);
        double holdP95 = metrics.lockHold.percentileMillis(0.95);
        double txP50 = metrics.transaction.percentileMillis(0.50);
        double txP95 = metrics.transaction.percentileMillis(0.95);
        double castP50 = metrics.broadcast.percentileMillis(0.50);
        double castP95 = metrics.broadcast.percentileMillis(0.95);

        // 하네스 몫: 클라이언트 응답 - 서버 처리. 같은 JVM이면 여기가 부풀어 오른다.
        double clientP50 = Math.max(0, respP50 - srvP50);
        double clientP95 = Math.max(0, respP95 - srvP95);

        double propP50 = metrics.propagation.percentileMillis(0.50);
        double propP95 = metrics.propagation.percentileMillis(0.95);
        double propP99 = metrics.propagation.percentileMillis(0.99);
        double fanP50 = metrics.fanout.percentileMillis(0.50);
        double fanP95 = metrics.fanout.percentileMillis(0.95);

        int created = metrics.created201.get();
        int rejected = metrics.tooLow400.get();
        long totalRequests = created + rejected + metrics.conflict409.get() + metrics.serverError500.get();
        double rejectRate = totalRequests > 0 ? rejected / (double) totalRequests : 0;

        // [상한은 "성공한 입찰"의 보유 시간으로 계산한다]
        //   전체 보유 시간을 쓰면 거절(SELECT 후 즉시 예외)이 통계를 끌어내려 상한이 몇 배 부풀려진다.
        //   실측 예: 거절률 96%일 때 전체 p50 0.7ms(→ 상한 1359/s)였지만 그건 거절의 속도였다.
        double holdSuccessP50 = metrics.lockHoldSuccess.percentileMillis(0.50);
        double holdSuccessP95 = metrics.lockHoldSuccess.percentileMillis(0.95);
        double txSuccessP50 = metrics.transactionSuccess.percentileMillis(0.50);
        double throughputCap = holdSuccessP50 > 0 ? 1000.0 / holdSuccessP50 : 0;
        double actualThroughput = created / wallSeconds;

        // [포화 판정] 아래 중 하나라도 걸리면 이미 밀어붙일 만큼 밀어붙인 상태다.
        //   여기서 "부하를 더 올려라"라고 안내하면 안 된다.
        double pendingRatio = metrics.poolSamples.get() > 0
                ? metrics.poolPendingSamples.get() / (double) metrics.poolSamples.get() : 0;
        boolean poolStarved = pendingRatio > 0.20;
        boolean lockQueued = srvP95 > 0 && waitP95 > srvP95 * 0.25;
        boolean saturated = poolStarved || lockQueued || rejectRate > 0.5;
        boolean goalMet = propP95 > 0 && propP95 < 1000;
        boolean consistent = bidCountDelta == created;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%n━━ 인기 경매 부하 ━━ 구독자 %d / 입찰자 %d / %d초 ━━%n",
                subscriberCount, phaseBidders, durationSeconds));
        sb.append(String.format("DB: %s%n", databaseProduct()));

        sb.append(String.format("%n[응답]%n"));
        sb.append(String.format("  입찰 API 응답        p50 %6.1fms   p95 %6.1fms   p99 %6.1fms%n",
                respP50, respP95, respP99));
        sb.append(String.format("  전파 지연            p50 %6.1fms   p95 %6.1fms   p99 %6.1fms    목표 p95 < 1000ms  %s%n",
                propP50, propP95, propP99, goalMet ? "OK" : "FAIL"));

        sb.append(String.format("%n[구간 분해] (p50 / p95, 비율은 서버 처리 p95 기준 — 요청별 측정)%n"));
        sb.append(String.format("  %-26s %6.1fms / %6.1fms%n", "서버 처리 합계", srvP50, srvP95));
        sb.append(segment("  (a) 진입·조회", entryP50, entryP95, srvP95));
        sb.append(segment("  (b) 락 대기", waitP50, waitP95, srvP95));
        sb.append(segment("  (c) 트랜잭션", txP50, txP95, srvP95));
        sb.append(segment("  (d) broadcast", castP50, castP95, srvP95));
        sb.append(String.format("  %-26s %6.1fms / %6.1fms   %s%n", "네트워크+클라이언트", clientP50, clientP95,
                clientP95 > srvP95 ? "<- 부하생성기가 같은 JVM. 서버 성능 아님" : ""));
        sb.append(String.format("  %-26s %6.1fms / %6.1fms%n", "(f) 팬아웃", fanP50, fanP95));

        sb.append(String.format("%n[락]%n"));
        sb.append(String.format("  보유 시간 (전체)     p50 %6.1fms   p95 %6.1fms   ← 거절 포함, 참고용%n",
                holdP50, holdP95));
        sb.append(String.format("  보유 시간 (성공만)   p50 %6.1fms   p95 %6.1fms   (그중 트랜잭션 %.1fms)%n",
                holdSuccessP50, holdSuccessP95, txSuccessP50));
        sb.append(String.format("  단일 경매 상한       ~ %.0f bids/sec   (1 / 성공 보유 p50)%n", throughputCap));
        sb.append(String.format("  실제 성공 처리량     ~ %.1f bids/sec   (상한의 %.0f%%)%n",
                actualThroughput, throughputCap > 0 ? actualThroughput / throughputCap * 100 : 0));
        if (saturated) {
            sb.append(String.format("  -> 이미 포화 상태다(%s). 부하를 더 올려도 성공 처리량은 늘지 않는다.%n",
                    saturationReason(poolStarved, lockQueued, rejectRate)));
            if (rejectRate > 0.5) {
                sb.append(String.format("     거절률 %.0f%% — 성공 1건당 거절 %.0f건. 처리량 상한은 락/DB가 아니라%n",
                        rejectRate * 100, created > 0 ? rejected / (double) created : 0));
                sb.append("     \"현재가가 입찰자들에게 퍼지는 속도\"(전파 지연)에 묶여 있다.\n");
            }
        } else if (throughputCap > 0 && actualThroughput < throughputCap * 0.5) {
            sb.append("  -> 서버가 한계에 안 닿았다. 부하를 올려야(-Dload.bidders 늘리고 -Dload.thinkMillis 줄이기) 병목이 드러난다.\n");
        }

        sb.append(String.format("%n[DB]%n"));
        sb.append(poolSection(hikari));

        sb.append(String.format("%n[결과 분포]%n"));
        sb.append(String.format("  201                %,6d   (성공)%n", created));
        sb.append(String.format("  400 BID_TOO_LOW    %,6d   (정상 — 경합에서 밀림)   거절률 %.0f%%%n",
                rejected, rejectRate * 100));
        sb.append(String.format("  409 ALREADY_BIDDING%,6d   %s%n", metrics.conflict409.get(),
                metrics.conflict409.get() == 0 ? "OK 락 정상" : "<- 락이 뚫렸다는 신호"));
        sb.append(String.format("  500                %,6d   %s%n", metrics.serverError500.get(),
                metrics.serverError500.get() == 0 ? "OK" : "<- 버그"));
        if (metrics.otherStatus.get() > 0 || metrics.ioError.get() > 0) {
            sb.append(String.format("  기타/IO오류        %,6d / %,6d%n",
                    metrics.otherStatus.get(), metrics.ioError.get()));
        }

        sb.append(String.format("%n[정합성]  이번 구간 bidCount 증가 %d == 201응답 %d  %s   (경매 누적 bids행 %d)%n",
                bidCountDelta, created, consistent ? "OK" : "FAIL", persistedBids));

        sb.append(String.format("%n[판정 힌트] %s%n",
                verdict(entryP95, waitP95, txP95, castP95, srvP95, clientP95, holdP95)));
        return sb.toString();
    }

    private String segment(String label, double p50, double p95, double totalP95) {
        double share = totalP95 > 0 ? (p95 / totalP95) * 100 : 0;
        return String.format("  %-26s %6.1fms / %6.1fms   %4.0f%%%n", label, p50, p95, share);
    }

    /**
     * Hikari 풀 상태.
     * [왜 최대값으로 판단하지 않나] 최대 하나만 보면 시작 순간 스파이크 한 번에 "풀 부족"으로 오판한다.
     * 실제로 평균 active 1.0(사용률 10%)인데 최대 pending 10이 찍혀 잘못 결론 낸 적이 있다.
     * 그래서 평균과 "대기가 있었던 시간 비율"로 지속성을 본다.
     */
    private String poolSection(HikariDataSource hikari) {
        int n = metrics.poolSamples.get();
        if (hikari == null || n == 0) {
            return "  (Hikari 풀 정보를 읽을 수 없음)\n";
        }
        int max = hikari.getMaximumPoolSize();
        double avgActive = metrics.poolActiveSum.get() / (double) n;
        double avgPending = metrics.poolPendingSum.get() / (double) n;
        double pendingRatio = metrics.poolPendingSamples.get() / (double) n;

        String judgement;
        if (avgPending >= 1.0 || pendingRatio > 0.20) {
            judgement = String.format("풀 고갈 — 대기가 지속됨(구간의 %.0f%%). maximum-pool-size 상향 검토", pendingRatio * 100);
        } else if (metrics.poolPendingMax.get() > 0) {
            judgement = String.format("순간 스파이크만(구간의 %.1f%%) — 정상 구간은 여유", pendingRatio * 100);
        } else {
            judgement = "여유";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("  active   평균 %.1f / 최대 %d   (풀 %d, 사용률 %.0f%%)%n",
                avgActive, metrics.poolActiveMax.get(), max, avgActive / max * 100));
        sb.append(String.format("  pending  평균 %.1f / 최대 %d   대기 발생 구간 %.1f%%%n",
                avgPending, metrics.poolPendingMax.get(), pendingRatio * 100));
        sb.append(String.format("  판정: %s%n", judgement));
        return sb.toString();
    }

    /**
     * 어디가 병목인지 한 줄로 요약한다.
     * 서버 내부 구간끼리만 비교하고, 하네스 비용이 서버보다 크면 그 사실을 먼저 알린다.
     */
    private String verdict(double entry, double wait, double tx, double cast,
                           double server, double client, double hold) {
        if (server == 0) {
            return "샘플 부족 — 지속 시간을 늘리거나 입찰자를 늘려볼 것";
        }
        if (client > server) {
            return String.format(
                    "하네스 비용(%.1fms)이 서버 처리(%.1fms)보다 큼 — 부하생성기가 같은 JVM이라 생기는 왜곡. "
                            + "서버 병목 판단은 [구간 분해]의 서버 내부 항목끼리만 할 것", client, server);
        }
        boolean poolStarved = metrics.poolPendingSamples.get() > metrics.poolSamples.get() * 0.2;
        double max = Math.max(Math.max(entry, wait), Math.max(tx, cast));
        if (max == wait) {
            return poolStarved
                    ? "락 대기가 지배적 + 커넥션 대기도 지속 -> 트랜잭션이 길어져 락 보유가 길어진 연쇄. 풀 크기부터 볼 것"
                    : String.format("락 대기가 지배적 -> 임계구역(보유 %.1fms)을 줄여야 처리량이 오른다", hold);
        }
        if (max == tx) {
            return poolStarved
                    ? "트랜잭션이 지배적 + 커넥션 대기 지속 -> 풀 고갈. maximum-pool-size 상향"
                    : "트랜잭션이 지배적, 커넥션은 여유 -> 쿼리/커밋 자체가 느림. 인덱스와 @Version UPDATE를 볼 것";
        }
        if (max == cast) {
            return "broadcast가 지배적 -> 락 안에서 발행하는 비용이 큼. 순서 보장 방식 재검토 대상";
        }
        // [(a) 분기에도 풀 상태를 본다]
        //   락 밖 조회 2회(user, auction)가 커넥션을 못 잡고 기다리면 그 시간이 전부 (a)로 잡힌다.
        //   실측: 풀 10에서 (a) p95 229ms(92%)였고 pending 평균 64.3 — JWT 필터가 아니라 풀 대기였다.
        //   풀을 30으로 올리자 (a)가 108ms로 절반이 됐다.
        return poolStarved
                ? "진입·조회가 지배적 + 커넥션 대기 지속 -> 락 밖 조회 2회가 커넥션을 기다리는 것. "
                        + "JWT 필터가 아니라 풀 크기 문제다. maximum-pool-size 상향 또는 락 밖 조회 줄이기"
                : "진입·조회가 지배적 -> JWT 필터 또는 락 밖 조회 2회를 볼 것";
    }

    /** 포화 이유를 짧게 적는다. 여러 개면 함께 표기한다. */
    private String saturationReason(boolean poolStarved, boolean lockQueued, double rejectRate) {
        StringBuilder sb = new StringBuilder();
        if (poolStarved) {
            sb.append("커넥션 대기 지속");
        }
        if (lockQueued) {
            sb.append(sb.length() > 0 ? ", " : "").append("락 대기 누적");
        }
        if (rejectRate > 0.5) {
            sb.append(sb.length() > 0 ? ", " : "").append(String.format("거절률 %.0f%%", rejectRate * 100));
        }
        return sb.toString();
    }

    private void writeReport(String report) {
        try {
            Path dir = Path.of("build", "reports", "load");
            Files.createDirectories(dir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path file = dir.resolve("hot-auction-" + stamp + ".txt");
            Files.writeString(file, report);
            System.out.println("리포트 저장: " + file.toAbsolutePath());
        } catch (Exception e) {
            System.out.println("리포트 파일 저장 실패: " + e.getMessage());
        }
    }

    private void cleanUp() {
        bidRepository.deleteAll();
        auctionRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User user(String email, String nickname, String phone) {
        return User.builder().publicId(PublicIdGenerator.newUlid())
                .email(email).nickname(nickname).phone(phone)
                .isAdmin(false).status(UserStatus.ACTIVE).marketingOptIn(false).build();
    }
}
