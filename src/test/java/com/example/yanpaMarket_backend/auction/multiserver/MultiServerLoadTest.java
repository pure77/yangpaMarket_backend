package com.example.yanpaMarket_backend.auction.multiserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionItemCondition;
import com.example.yanpaMarket_backend.auction.precheck.BidPriceGuard;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.mysql.MySQLContainer;

/**
 * [무엇] 서버 2대 부하 측정. 서버 A·B(MySQL·Redis 공유)에 현실적인 입찰 부하를 주고,
 *   Redis 사전 거절 켬/끔에 따라 "공용 DB에 들어간 쿼리 수"가 얼마나 다른지 잰다.
 *
 * [왜 쿼리 수가 주 지표인가] 이 PC에서는 서버 2대·MySQL·Redis·부하 생성기가 CPU를 나눠 쓰고 Docker 네트워크도 거친다.
 *   응답 시간은 그 영향을 크게 받지만, "입찰 요청 하나가 DB에 몇 개의 쿼리를 보냈나"는 환경과 무관하다.
 *   서버를 늘려도 DB는 하나라서, 서버가 늘수록 이 숫자가 공용 DB 부하를 결정한다.
 *   측정: MySQL SHOW GLOBAL STATUS의 Questions·Com_select·Com_insert·Com_update·Com_commit 증가량(부하 구간 전후).
 *
 * [부하 모양 — 현실적 조건] (HotAuctionLoadTest의 현실 부하 측정과 같은 조건)
 *   - 경매마다 입찰자 N명, 절반은 서버 A로 절반은 서버 B로 (로드밸런서가 나눠 준 것처럼)
 *   - 생각 시간 thinkMillis/2 ~ thinkMillis*2 (기본 1.5~6초)
 *   - 가격 읽는 시점 send(보내기 직전) / decide(금액을 정한 순간) — 아래 bidLoop 주석 참고
 *   - 금액 = 현재가 + 인상폭 × (1~3배 무작위)
 *
 * [입찰자가 보는 현재가 — 3단계 이후를 가정] 서버마다 구독자를 하나씩 두고, 어느 서버에서 온 갱신이든 공유 현재가에 반영한다.
 *   지금은 서버 간 전파가 없어(3단계 미구현) 실제 사용자는 자기 서버 갱신만 본다. 그걸 그대로 재현하면
 *   입찰자의 가격이 일부러 낡아져 사전 거절이 실제보다 유리해 보인다. 그래서 "모두 다 받는" 상황을 가정해 공정하게 비교한다.
 *
 * [구간마다 서버를 새로 띄우는 이유] 사전 거절 켬/끔은 기동 시점에 빈이 정해진다(@ConditionalOnProperty).
 *   서버 A가 ddl-auto=create로 테이블을 새로 만들어 구간끼리 데이터가 섞이지 않는다. Redis도 구간마다 비운다.
 *
 * [실행] 평소엔 건너뛴다(구간 하나에 약 1분, 기본 8구간).
 *   ./gradlew.bat test --tests "com.example.yanpaMarket_backend.auction.multiserver.MultiServerLoadTest" -Dmultiserver.load=true
 *   파라미터: -Dmultiserver.load.auctions=1,5  -Dmultiserver.load.bidders=100  -Dmultiserver.load.seconds=60
 *            -Dmultiserver.load.thinkMillis=3000  -Dmultiserver.load.priceReadAt=send,decide
 *   리포트: build/reports/multiserver/multi-server-load-{시각}.txt
 *
 * [한계] 서버 락은 아직 서버마다 따로다(2단계 미구현). 그래서 409가 날 수 있고, 그것도 기록한다.
 *   분산 락을 넣은 뒤 같은 테스트로 다시 재면 "분산 락에서 사전 거절이 아껴 주는 것"을 볼 수 있다.
 */
@EnabledIfSystemProperty(named = "multiserver.load", matches = "true")
class MultiServerLoadTest extends RedisTestSupport {

    /** Auction 생성자의 minimumBidIncrement와 같아야 한다. */
    private static final long INCREMENT = 10_000L;
    private static final long START_PRICE = 10_000L;
    private static final List<String> STATUS_KEYS =
            List.of("Questions", "Com_select", "Com_insert", "Com_update", "Com_commit");

    private final List<Integer> auctionCounts = Arrays.stream(
                    System.getProperty("multiserver.load.auctions", "1,5").split(","))
            .map(String::trim).map(Integer::parseInt).toList();
    private final int biddersPerAuction = Integer.getInteger("multiserver.load.bidders", 100);
    private final int seconds = Integer.getInteger("multiserver.load.seconds", 60);
    private final int thinkMillis = Integer.getInteger("multiserver.load.thinkMillis", 3000);
    private final List<String> readModes = Arrays.stream(
                    System.getProperty("multiserver.load.priceReadAt", "send,decide").split(","))
            .map(String::trim).toList();

    private final StringBuilder report = new StringBuilder();

    /** 구간 하나의 결과. 요약 표를 만들 때 켬/끔 짝을 찾는 데 쓴다. */
    private record PhaseResult(String readMode, int auctions, boolean precheck, int requests, int created,
                               int tooLow, int conflicts, Map<String, Long> queries, GuardStats guard,
                               long p50Micros, long p95Micros) {

        double perRequest(String key) {
            return requests == 0 ? 0 : (double) queries.get(key) / requests;
        }
    }

    @Test
    void 서버_2대에서_사전_거절_켬끔에_따른_DB_쿼리_수() throws Exception {
        line("━━ 서버 2대 부하 측정 ━━ " + LocalDateTime.now().withNano(0));
        line(String.format("입찰자 %d명/경매 (A·B 절반씩), %d초, 생각 시간 %d~%dms, 경매 수 %s, 가격 읽는 시점 %s",
                biddersPerAuction, seconds, thinkMillis / 2, thinkMillis * 2, auctionCounts, readModes));
        List<PhaseResult> results = new ArrayList<>();
        try {
            int pair = 0;
            for (String readMode : readModes) {
                for (int auctions : auctionCounts) {
                    // [순서 번갈아] 같은 순서로만 돌면 앞 구간이 늘 JIT 예열 손해를 본다. 짝마다 끔/켬 순서를 바꾼다.
                    boolean[] order = pair++ % 2 == 0 ? new boolean[] {false, true} : new boolean[] {true, false};
                    for (boolean precheck : order) {
                        results.add(runPhase(readMode, auctions, precheck));
                    }
                }
            }
            summarize(results);
        } finally {
            writeReport();
        }
    }

    // ── 구간 하나 ──────────────────────────────────────────────────────────

    private PhaseResult runPhase(String readMode, int auctions, boolean precheck) throws Exception {
        flushRedisNow();
        ServerHandle a = MultiServerTestSupport.startServer("A", "create", redisHost(), redisPort(), precheck);
        ServerHandle b;
        try {
            b = MultiServerTestSupport.startServer("B", "validate", redisHost(), redisPort(), precheck);
        } catch (RuntimeException e) {
            a.close();
            throw e;
        }
        try (MultiServerClient client = new MultiServerClient()) {
            // ── 준비(측정 밖): 경매·입찰자·토큰, 서버마다 구독자 하나 ──
            List<Auction> auctionList = new ArrayList<>();
            List<List<String>> tokens = new ArrayList<>();
            for (int i = 0; i < auctions; i++) {
                auctionList.add(createAuction(a, i));
                tokens.add(createBidders(a, i));
            }
            Map<String, AtomicLong> latest = new ConcurrentHashMap<>();
            for (Auction auction : auctionList) {
                AtomicLong price = new AtomicLong(START_PRICE);
                latest.put(auction.getPublicId(), price);
                for (ServerHandle server : List.of(a, b)) {
                    client.subscribe(server, auction.getPublicId(), p -> price.accumulateAndGet(p, Math::max));
                }
            }
            Thread.sleep(1000); // 구독 등록이 각 서버 브로커에 반영될 시간

            GuardStats guardBefore = bothServers(a, b);
            Map<String, Long> statusBefore = mysqlStatus();

            // ── 부하 ──
            Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
            ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
            int total = auctions * biddersPerAuction;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
            ExecutorService pool = Executors.newFixedThreadPool(total);
            List<Future<?>> workers = new ArrayList<>();
            try {
                for (int ai = 0; ai < auctions; ai++) {
                    String auctionId = auctionList.get(ai).getPublicId();
                    for (int bi = 0; bi < biddersPerAuction; bi++) {
                        ServerHandle target = bi % 2 == 0 ? a : b; // 절반은 A, 절반은 B
                        String token = tokens.get(ai).get(bi);
                        workers.add(pool.submit(() -> {
                            bidLoop(client, target, auctionId, token, latest.get(auctionId), readMode, deadline,
                                    counts, latencies);
                            return null;
                        }));
                    }
                }
                pool.shutdown();
                assertThat(pool.awaitTermination(seconds + 60L, TimeUnit.SECONDS)).as("입찰 스레드 종료").isTrue();
                for (Future<?> w : workers) {
                    try {
                        w.get(); // 작업 스레드 안의 예외는 Future에만 담긴다
                    } catch (ExecutionException e) {
                        fail("입찰 작업 스레드에서 예외 발생: " + e.getCause(), e.getCause());
                    }
                }
            } finally {
                pool.shutdownNow();
            }

            Map<String, Long> statusAfter = mysqlStatus(); // 정합성 확인 쿼리보다 먼저 — 측정에 섞이지 않게
            GuardStats guard = bothServers(a, b).minus(guardBefore);
            Map<String, Long> queries = new HashMap<>();
            STATUS_KEYS.forEach(k -> queries.put(k, statusAfter.get(k) - statusBefore.get(k)));

            int created = sum(counts, (status, code) -> status == 201);
            int tooLow = sum(counts, (status, code) -> status == 400 && "BID_TOO_LOW".equals(code));
            int conflicts = sum(counts, (status, code) -> status == 409 && "ALREADY_BIDDING".equals(code));
            int requests = counts.values().stream().mapToInt(AtomicInteger::get).sum();
            int unexpected = requests - created - tooLow - conflicts;

            // ── 항상 엄격: 정합성 ──
            assertThat(unexpected).as("500·IO 등 예상 밖 응답 " + new TreeMap<>(counts)).isZero();
            long bidCountSum = 0;
            for (Auction auction : auctionList) {
                Auction reloaded = a.bean(AuctionRepository.class).findByPublicId(auction.getPublicId()).orElseThrow();
                bidCountSum += reloaded.getBidCount();
                if (precheck && reloaded.getBidCount() > 0) {
                    assertThat(sharedRedis().opsForHash().get(BidRedisKeys.bid(reloaded.getId()), BidRedisKeys.PRICE))
                            .as("Redis 사본 == DB 현재가").isEqualTo(String.valueOf(reloaded.getCurrentPrice()));
                }
            }
            assertThat(bidCountSum).as("DB 입찰 수 합계 == 201 응답 수").isEqualTo(created);

            List<Long> sorted = latencies.stream().sorted().toList();
            PhaseResult result = new PhaseResult(readMode, auctions, precheck, requests, created, tooLow, conflicts,
                    queries, guard, percentile(sorted, 50), percentile(sorted, 95));
            describe(result, counts);
            return result;
        } finally {
            b.close();
            a.close();
        }
    }

    /**
     * 입찰자 한 명의 반복.
     * [가격 읽는 시점]
     *   send   : 생각 시간 → 보내기 직전에 현재가를 읽고 금액을 정한다. 도착 시 이미 낮은 입찰이 적다.
     *   decide : 현재가를 읽고 금액을 정한 뒤 → 생각 시간 → 그 금액으로 보낸다. 실제 사용자처럼
     *            그사이 가격이 오르면 도착할 때 이미 낮다(사전 거절이 락 앞에서 걸러낼 수 있는 입찰).
     *   두 경우 모두 생각 시간이 먼저 오므로, 시작 순간에 전원이 동시에 보내는 스파이크가 없다.
     */
    private void bidLoop(MultiServerClient client, ServerHandle target, String auctionId, String token,
                         AtomicLong latest, String readMode, long deadline,
                         Map<String, AtomicInteger> counts, ConcurrentLinkedQueue<Long> latencies)
            throws InterruptedException {
        while (System.nanoTime() < deadline) {
            long base;
            if ("decide".equals(readMode)) {
                base = latest.get();
                Thread.sleep(think());
            } else {
                Thread.sleep(think());
                base = latest.get();
            }
            if (System.nanoTime() >= deadline) {
                break;
            }
            long amount = base + INCREMENT * (1 + ThreadLocalRandom.current().nextInt(3));
            long start = System.nanoTime();
            BidResult r = client.bid(target, auctionId, token, amount);
            latencies.add((System.nanoTime() - start) / 1_000);
            counts.computeIfAbsent(r.status() + ":" + (r.code() == null ? "-" : r.code()), k -> new AtomicInteger())
                    .incrementAndGet();
            if (r.status() == 201) {
                latest.accumulateAndGet(amount, Math::max);
            }
        }
    }

    private int think() {
        return ThreadLocalRandom.current().nextInt(thinkMillis / 2, thinkMillis * 2);
    }

    // ── 준비 ───────────────────────────────────────────────────────────────

    private Auction createAuction(ServerHandle a, int index) {
        UserRepository users = a.bean(UserRepository.class);
        User seller = users.save(user("load-a" + index + "-seller", 9300 + index, 0));
        LocalDateTime now = LocalDateTime.now();
        return a.bean(AuctionRepository.class).save(Auction.builder()
                .publicId(PublicIdGenerator.newUlid())
                .sellerUserId(seller.getId())
                .title("서버 2대 부하 " + index).description("multi-server load test")
                .category(AuctionCategory.ETC).itemCondition(AuctionItemCondition.USED)
                .startPrice(START_PRICE).buyNowPrice(null)
                .startAt(now.minusMinutes(1)).endAt(now.plusMinutes(30))
                .build());
    }

    /** 경매 index의 입찰자들을 만들고 토큰을 돌려준다. 두 서버가 같은 JWT 키를 쓰므로 B에서도 통한다. */
    private List<String> createBidders(ServerHandle a, int index) {
        UserRepository users = a.bean(UserRepository.class);
        JwtProvider jwt = a.bean(JwtProvider.class);
        List<String> tokens = new ArrayList<>();
        for (int i = 1; i <= biddersPerAuction; i++) {
            User bidder = users.save(user("load-a" + index + "-b" + i, 9300 + index, i));
            tokens.add(jwt.createAccessToken(bidder.getPublicId(), false));
        }
        return tokens;
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

    // ── 측정 도우미 ─────────────────────────────────────────────────────────

    /** MySQL 전역 상태 카운터. 두 서버의 모든 쿼리가 합쳐진 값이다(공용 DB가 실제로 받은 양). */
    private static Map<String, Long> mysqlStatus() throws Exception {
        MySQLContainer db = MultiServerTestSupport.mysql();
        Map<String, Long> values = new HashMap<>();
        try (Connection c = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SHOW GLOBAL STATUS WHERE Variable_name IN "
                     + "('Questions','Com_select','Com_insert','Com_update','Com_commit')")) {
            while (rs.next()) {
                values.put(rs.getString(1), rs.getLong(2));
            }
        }
        assertThat(values.keySet()).as("MySQL 상태 카운터").containsAll(STATUS_KEYS);
        return values;
    }

    /** 두 서버의 사전 거절 카운터 합. */
    private static GuardStats bothServers(ServerHandle a, ServerHandle b) {
        GuardStats x = guardStats(a);
        GuardStats y = guardStats(b);
        return new GuardStats(x.precheckPass() + y.precheckPass(), x.precheckTooLow() + y.precheckTooLow(),
                x.precheckSelfBid() + y.precheckSelfBid(), x.precheckFailures() + y.precheckFailures(),
                x.recordFailures() + y.recordFailures(), x.evictFailures() + y.evictFailures());
    }

    /** 사전 거절 카운터. 끔(NoOp)이면 0. */
    private static GuardStats guardStats(ServerHandle server) {
        return server.bean(BidPriceGuard.class) instanceof RedisBidPriceGuard redis
                ? redis.stats() : new GuardStats(0, 0, 0, 0, 0, 0);
    }

    private void flushRedisNow() {
        sharedRedis().execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    private interface StatusMatch {
        boolean test(int status, String code);
    }

    private static int sum(Map<String, AtomicInteger> counts, StatusMatch match) {
        int total = 0;
        for (Map.Entry<String, AtomicInteger> e : counts.entrySet()) {
            String[] parts = e.getKey().split(":", 2);
            if (match.test(Integer.parseInt(parts[0]), parts[1])) {
                total += e.getValue().get();
            }
        }
        return total;
    }

    private static long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    // ── 리포트 ─────────────────────────────────────────────────────────────

    private void describe(PhaseResult r, Map<String, AtomicInteger> counts) {
        line("");
        line(String.format("[구간] 가격 읽는 시점 %s / 경매 %d건 / 사전 거절 %s", r.readMode(), r.auctions(),
                r.precheck() ? "켬" : "끔"));
        line(String.format("  요청 %,d건 = 201 %,d / 400 BID_TOO_LOW %,d / 409 %,d   응답 p50 %.1fms p95 %.1fms",
                r.requests(), r.created(), r.tooLow(), r.conflicts(), r.p50Micros() / 1000.0, r.p95Micros() / 1000.0));
        line("  응답 분포 " + new TreeMap<>(counts));
        line(String.format("  DB 쿼리 증가  Questions %,d  SELECT %,d  INSERT %,d  UPDATE %,d  COMMIT %,d",
                r.queries().get("Questions"), r.queries().get("Com_select"), r.queries().get("Com_insert"),
                r.queries().get("Com_update"), r.queries().get("Com_commit")));
        line(String.format("  요청당        Questions %.2f  SELECT %.2f", r.perRequest("Questions"),
                r.perRequest("Com_select")));
        if (r.precheck()) {
            GuardStats g = r.guard();
            line(String.format("  사전 거절     통과 %,d  락 앞 거절(TooLow) %,d  Redis 실패로 통과 %,d   락 앞 거절 비율 %.0f%%",
                    g.precheckPass(), g.precheckTooLow(), g.precheckFailures(),
                    r.tooLow() == 0 ? 0 : 100.0 * g.precheckTooLow() / r.tooLow()));
        }
        line("  정합성 OK — DB 입찰 수 합계 == 201 응답 수" + (r.precheck() ? ", Redis == DB" : ""));
    }

    /** 같은 조건의 끔/켬 짝을 찾아 요청당 쿼리 수 감소율을 표로 낸다. */
    private void summarize(List<PhaseResult> results) {
        line("");
        line("━━ 요약: 요청 1건당 DB 쿼리 수 (끔 → 켬) ━━");
        line(String.format("%-8s %-6s %14s %14s %8s %8s %8s", "읽는시점", "경매", "Questions", "SELECT", "감소",
                "거절률", "409"));
        for (PhaseResult off : results) {
            if (off.precheck()) {
                continue;
            }
            PhaseResult on = results.stream()
                    .filter(r -> r.precheck() && r.readMode().equals(off.readMode()) && r.auctions() == off.auctions())
                    .findFirst().orElse(null);
            if (on == null) {
                continue;
            }
            double qOff = off.perRequest("Questions");
            double qOn = on.perRequest("Questions");
            line(String.format("%-8s %-6s %6.2f → %5.2f %6.2f → %5.2f %7.0f%% %3.0f→%3.0f%% %3d→%3d",
                    off.readMode(), off.auctions() + "건", qOff, qOn,
                    off.perRequest("Com_select"), on.perRequest("Com_select"),
                    qOff == 0 ? 0 : 100.0 * (qOff - qOn) / qOff,
                    off.requests() == 0 ? 0 : 100.0 * off.tooLow() / off.requests(),
                    on.requests() == 0 ? 0 : 100.0 * on.tooLow() / on.requests(),
                    off.conflicts(), on.conflicts()));
        }
        line("  감소 = 요청당 Questions(모든 SQL 문) 감소율. 거절률 = BID_TOO_LOW / 전체 요청.");
    }

    private void line(String text) {
        report.append(text).append('\n');
    }

    private void writeReport() throws java.io.IOException {
        String text = report.toString();
        System.out.println(text);
        Path dir = Path.of("build", "reports", "multiserver");
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = dir.resolve("multi-server-load-" + stamp + ".txt");
        Files.writeString(file, text);
        System.out.println("리포트 파일: " + file.toAbsolutePath());
    }
}
