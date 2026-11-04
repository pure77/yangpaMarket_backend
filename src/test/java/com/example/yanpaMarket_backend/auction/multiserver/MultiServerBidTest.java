package com.example.yanpaMarket_backend.auction.multiserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionStatus;
import com.example.yanpaMarket_backend.auction.domain.AuctionItemCondition;
import com.example.yanpaMarket_backend.auction.precheck.BidRedisKeys;
import com.example.yanpaMarket_backend.auction.precheck.GuardStats;
import com.example.yanpaMarket_backend.auction.precheck.RedisBidPriceGuard;
import com.example.yanpaMarket_backend.auction.repository.AuctionRepository;
import com.example.yanpaMarket_backend.auction.repository.BidRepository;
import com.example.yanpaMarket_backend.support.multiserver.MultiServerClient.Subscriber;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiPredicate;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.data.domain.PageRequest;
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

    private static final Gate SCENARIO_1_GATE = Gate.STRICT;  // 2단계(분산 락) 완료 — 409 0건이어야 한다
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
    @Order(1)
    void 상황1_같은_경매에_두_서버로_동시_입찰() throws Exception {
        line("[상황 1] 동시 입찰 (모드: " + SCENARIO_1_GATE + ")");
        int bidderCount = 40;
        Fixture f = fixture("s1", 9101, bidderCount, LocalDateTime.now().plusMinutes(10));
        String auctionId = f.auction().getPublicId();

        // 서버:상태:에러코드 → 건수 (예: "A:201:-", "B:409:ALREADY_BIDDING", "A:-1:-"=IO 오류)
        // [왜 코드까지] 상태만 세면 AUCTION_ENDED 같은 400(준비 잘못)도 "정상"으로 통과하고,
        //   모든 409가 ALREADY_BIDDING(락 충돌)인 것처럼 보인다.
        Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        List<Future<?>> workers = new ArrayList<>();
        AtomicLong maxAccepted = new AtomicLong(0);
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(bidderCount);
        try {
            for (int i = 0; i < bidderCount; i++) {
                ServerHandle target = i % 2 == 0 ? serverA : serverB; // 절반은 A, 절반은 B
                String token = f.tokens().get(i);
                workers.add(pool.submit(() -> {
                    start.await();
                    long amount = 20_000L;
                    while (System.nanoTime() < deadline) {
                        BidResult r = client.bid(target, auctionId, token, amount);
                        String code = r.code() == null ? "-" : r.code();
                        counts.computeIfAbsent(target.name() + ":" + r.status() + ":" + code, k -> new AtomicInteger())
                                .incrementAndGet();
                        if (r.status() == 201) {
                            maxAccepted.accumulateAndGet(amount, Math::max);
                            amount += INCREMENT;
                        } else if (r.status() == 400 && "BID_TOO_LOW".equals(r.code()) && r.minimumBid() != null) {
                            amount = r.minimumBid(); // 거절 응답의 최소 입찰가로 재시도 — 실제 경합처럼 몰리게
                        }
                        // 409(충돌)·그 외는 같은 금액으로 재시도
                        Thread.sleep(20);
                    }
                    return null;
                }));
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(40, TimeUnit.SECONDS)).as("입찰 스레드 종료").isTrue();
            // [왜 get()] 작업 스레드 안의 예외는 Future에만 담긴다. 확인하지 않으면 조용히 묻힌다.
            for (Future<?> w : workers) {
                try {
                    w.get();
                } catch (ExecutionException e) {
                    fail("입찰 작업 스레드에서 예외 발생: " + e.getCause(), e.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }

        // 정상 응답 = 201, 400+BID_TOO_LOW(경합으로 가격이 올라 있음), 409+ALREADY_BIDDING(락 충돌). 나머지는 모두 예상 밖.
        int created = sumCounts(counts, (status, code) -> status == 201);
        int conflicts = sumCounts(counts, (status, code) -> status == 409 && "ALREADY_BIDDING".equals(code));
        int unexpected = sumCounts(counts, (status, code) -> !(status == 201
                || (status == 400 && "BID_TOO_LOW".equals(code))
                || (status == 409 && "ALREADY_BIDDING".equals(code))));
        line("  응답 (서버:상태:코드 → 건수) " + new TreeMap<>(counts));

        Auction reloaded = reload(f);
        long persisted = serverA.bean(BidRepository.class)
                .findByAuctionIdOrderByCreatedAtDesc(reloaded.getId(), PageRequest.of(0, 1)).getTotalElements();

        // ── 항상 엄격 ──
        assertThat(unexpected).as("500·IO 등 예상 밖 응답 수 " + new TreeMap<>(counts)).isZero();
        assertThat(reloaded.getBidCount()).as("DB 입찰 수 == 201 응답 수").isEqualTo(created);
        assertThat(persisted).as("저장된 입찰 행 수 == 201 응답 수").isEqualTo(created);
        assertThat(reloaded.getCurrentPrice()).as("DB 현재가 == 성공한 최고 금액").isEqualTo(maxAccepted.get());
        assertThat(redisPrice(reloaded.getId())).as("Redis 사본 == DB 현재가")
                .isEqualTo(String.valueOf(reloaded.getCurrentPrice()));
        line("  정합성 OK — 입찰 수 " + reloaded.getBidCount() + " == 성공 " + created
                + ", 현재가 " + reloaded.getCurrentPrice() + " == 최고 성공가, Redis == DB");

        // ── 목표 ──
        checkTarget(SCENARIO_1_GATE, conflicts == 0, "409(ALREADY_BIDDING) 0건 (현재 " + conflicts + "건)",
                "2단계 분산 락");
    }

    /**
     * [왜 이 테스트] 입찰 갱신 메시지는 처리한 서버의 인메모리 브로커로만 나간다.
     * 서버 A·B 각각에 구독자를 두고 A/B 번갈아 입찰해, 메시지가 서버를 넘어 전달되는지 관찰한다.
     * 엄격: 자기 서버 메시지 수신·중복/초과 없음·STOMP 오류 없음. 목표: 전원이 전부 수신(현재 미해결 예상).
     */
    @Test
    @Order(2)
    void 상황2_실시간_메시지가_서버를_넘어가는지() throws Exception {
        line("[상황 2] 서버 간 메시지 전파 (모드: " + SCENARIO_2_GATE + ")");
        int bids = 10;
        Fixture f = fixture("s2", 9102, bids, LocalDateTime.now().plusMinutes(10));
        String auctionId = f.auction().getPublicId();

        List<Subscriber> subscribers = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            subscribers.add(client.subscribe(serverA, auctionId));
            subscribers.add(client.subscribe(serverB, auctionId));
        }
        Thread.sleep(1000); // 구독 등록이 각 서버 브로커에 반영될 시간

        List<Long> pricesViaA = new ArrayList<>();
        List<Long> pricesViaB = new ArrayList<>();
        for (int i = 0; i < bids; i++) {
            ServerHandle target = i % 2 == 0 ? serverA : serverB; // A, B, A, B …
            long amount = 20_000L + i * INCREMENT;
            BidResult r = client.bid(target, auctionId, f.tokens().get(i), amount);
            assertThat(r.status()).as("준비 입찰 %d (%s, %d원)", i, target.name(), amount).isEqualTo(201);
            (target == serverA ? pricesViaA : pricesViaB).add(amount);
        }
        waitUntil(Duration.ofSeconds(2),
                () -> subscribers.stream().allMatch(s -> s.bidUpdatePrices().size() >= bids));

        // ── 항상 엄격 ──
        for (Subscriber s : subscribers) {
            assertThat(s.errorCount()).as("%s 구독자 STOMP 오류 없음", s.server()).isZero();
            List<Long> received = s.bidUpdatePrices();
            List<Long> ownServerPrices = s.server().equals("A") ? pricesViaA : pricesViaB;
            assertThat(received).as("%s 구독자는 자기 서버에서 처리된 입찰 갱신을 모두 받아야 한다", s.server())
                    .containsAll(ownServerPrices);
            assertThat(new HashSet<>(received)).as("%s 구독자 중복 수신 없음", s.server()).hasSameSizeAs(received);
            assertThat(received.size()).as("%s 구독자 최대 %d건", s.server(), bids).isLessThanOrEqualTo(bids);
        }

        // ── 목표 ──
        StringBuilder perServer = new StringBuilder();
        for (String server : List.of("A", "B")) {
            List<Integer> sizes = subscribers.stream().filter(s -> s.server().equals(server))
                    .map(s -> s.bidUpdatePrices().size()).toList();
            perServer.append(server).append(" 구독자 수신 ").append(sizes).append("/").append(bids).append("   ");
        }
        line("  " + perServer.toString().trim());
        boolean allReceived = subscribers.stream().allMatch(s -> s.bidUpdatePrices().size() == bids);
        checkTarget(SCENARIO_2_GATE, allReceived, "모든 구독자가 " + bids + "건 전부 수신", "3단계 서버 간 전파");
    }

    /**
     * [왜 이 테스트] 종료 스케줄러(10초 주기)는 두 서버 모두에서 돈다. 같은 경매를 두 서버가 각자 닫으려 할 때
     * DB 최종 상태가 한 번만 정상 반영되는지, 종료 메시지가 구독자에게 몇 번 가는지 관찰한다.
     * 엄격: 최종 상태·낙찰자·종료 시각·STOMP 오류 없음. 목표: 모든 구독자가 종료 메시지를 정확히 1번 수신.
     */
    @Test
    @Order(3)
    void 상황3_경매_종료_스케줄러() throws Exception {
        line("[상황 3] 종료 스케줄러 (모드: " + SCENARIO_3_GATE + ")");
        // [5초 뒤 마감] 생성 직후 입찰 1건이 마감 전에 성공해야 한다. 느린 PC를 고려해 spec의 3초보다 여유를 둔다.
        Fixture f = fixture("s3", 9103, 1, LocalDateTime.now().plusSeconds(5));
        String auctionId = f.auction().getPublicId();

        // [왜 구독이 먼저] 종료는 endAt 이후에만 일어난다. 구독을 먼저 끝내 두면 어떤 종료 메시지보다
        //   구독자가 확실히 살아 있다. 구독·입찰이 마감 전에 끝났는지는 아래에서 검증한다.
        List<Subscriber> subscribers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            subscribers.add(client.subscribe(serverA, auctionId));
            subscribers.add(client.subscribe(serverB, auctionId));
        }
        Thread.sleep(1000); // 구독 등록 반영

        BidResult bid = client.bid(serverA, auctionId, f.tokens().get(0), 20_000L);
        assertThat(bid.status()).as("마감 전 입찰").isEqualTo(201);
        assertThat(LocalDateTime.now().isBefore(f.auction().getEndAt()))
                .as("준비 지연(테스트 구성 문제): 구독·입찰이 마감 전에 끝나지 않았다").isTrue();

        // 두 서버의 스케줄러(10초 주기)가 마감된 경매를 종료할 때까지 기다린다
        boolean closed = waitUntil(Duration.ofSeconds(30), () -> reload(f).getStatus() != AuctionStatus.ACTIVE);
        assertThat(closed).as("30초 안에 경매 종료").isTrue();
        // 다른 서버가 다음 주기에 한 번 더 종료(·메시지 전송)하는지 보려고 한 주기를 더 기다린다
        Thread.sleep(11_000);

        // ── 항상 엄격 ──
        Auction reloaded = reload(f);
        assertThat(reloaded.getStatus()).as("최종 상태").isEqualTo(AuctionStatus.PAYMENT_PENDING);
        assertThat(reloaded.getWinnerUserId()).as("낙찰자").isEqualTo(f.bidders().get(0).getId());
        assertThat(reloaded.getEndedAt()).as("종료 시각 기록").isNotNull();
        for (Subscriber s : subscribers) {
            assertThat(s.errorCount()).as("%s 구독자 STOMP 오류 없음", s.server()).isZero();
        }
        line("  DB 최종 상태 OK — PAYMENT_PENDING, 낙찰자 일치, 종료 시각 기록");

        // ── 목표 ──
        StringBuilder perServer = new StringBuilder();
        // 3단계(서버 간 전파) 후에는 한 서버만 닫아도 양쪽 구독자가 받으므로 이 값은 2가 된다.
        int receivingServers = 0;
        for (String server : List.of("A", "B")) {
            List<Integer> endedCounts = subscribers.stream().filter(s -> s.server().equals(server))
                    .map(Subscriber::auctionEndedCount).toList();
            if (endedCounts.stream().anyMatch(c -> c > 0)) {
                receivingServers++;
            }
            perServer.append(server).append(" 구독자 종료 메시지 ").append(endedCounts).append("회   ");
        }
        line("  " + perServer.toString().trim() + "  (종료 메시지를 받은 구독자가 있는 서버 " + receivingServers + "대)");
        boolean exactlyOnce = subscribers.stream().allMatch(s -> s.auctionEndedCount() == 1);
        checkTarget(SCENARIO_3_GATE, exactlyOnce, "모든 구독자가 종료 메시지를 정확히 1번 수신",
                "3단계 서버 간 전파·스케줄러 중복 방지");
    }

    /** "서버:상태:코드" 키 중 조건에 맞는 것의 건수 합계. 코드가 없으면 "-". */
    private static int sumCounts(Map<String, AtomicInteger> counts, BiPredicate<Integer, String> match) {
        int sum = 0;
        for (Map.Entry<String, AtomicInteger> e : counts.entrySet()) {
            String[] parts = e.getKey().split(":", 3);
            if (match.test(Integer.parseInt(parts[1]), parts[2])) {
                sum += e.getValue().get();
            }
        }
        return sum;
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
