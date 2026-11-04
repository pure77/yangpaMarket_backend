package com.example.yanpaMarket_backend.support.multiserver;

import com.example.yanpaMarket_backend.YanpaMarketBackendApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * [무엇] 서버 2대 재현 테스트의 기반: 두 서버가 함께 쓰는 MySQL 컨테이너와, 앱을 하나 더 띄우는 startServer.
 *
 * [왜 SpringApplicationBuilder로 직접 띄우나] Spring 테스트(@SpringBootTest)는 앱 하나만 띄우고 컨텍스트를 캐시해 재사용한다.
 *   직접 두 번 실행하면 두 앱은 빈을 하나도 공유하지 않는다 → 서버 2대처럼 락·메시지 브로커·스케줄러가 각자 따로 생긴다.
 *
 * [왜 모든 설정을 명령행 인자로 넘기나] 명령행 인자는 우선순위가 가장 높다.
 *   - 메인 application.yml이 spring.profiles.active=secret 을 켜므로 --spring.profiles.active=test 로 덮어써야 한다
 *     (secret 프로필은 JWT_SECRET 등 실제 비밀값을 요구한다).
 *   - application-test.properties의 H2 설정을 MySQL로 덮어써야 한다. builder.properties()는 기본값이라 여기에 진다.
 *
 * [스키마] 첫 서버는 ddl-auto=create(JPA가 테이블 생성), 두 번째 서버는 validate.
 *   Flyway는 끈다 — 이 프로젝트에는 Boot 4의 Flyway 자동 설정 모듈이 없어 어차피 실행되지 않는다(별도 작업으로 확인 중).
 *   create-drop이 아니라 create인 이유: 먼저 닫히는 서버가 다른 서버가 쓰는 테이블을 지우지 않게.
 *
 * [Redis 타임아웃 500ms] 이 PC는 부하 시 Docker 경유 Redis 왕복이 50ms를 넘을 수 있다(진단 결과).
 *   운영 기본값(50ms)이면 사전 거절이 타임아웃으로 건너뛰어져 엄격 검사가 이유 없이 실패할 수 있어서, 이 테스트에서만 늘린다.
 *
 * [분산 락] 두 서버는 같은 Redis의 auction:{id}:lock을 다툰다. Redisson 타임아웃은 spring.data.redis.timeout(500ms)을 따른다.
 */
public final class MultiServerTestSupport {

    static final String DOCKER_REQUIRED =
            "Docker가 실행 중이 아니라 서버 2대 테스트를 실행할 수 없습니다. Docker Desktop을 켠 뒤 다시 실행하세요.";

    private static MySQLContainer mysql;

    private MultiServerTestSupport() {
    }

    /** 두 서버가 함께 쓰는 MySQL 컨테이너를 JVM당 한 번 띄운다. */
    public static synchronized MySQLContainer mysql() {
        if (mysql == null) {
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                throw new IllegalStateException(DOCKER_REQUIRED);
            }
            MySQLContainer container = new MySQLContainer(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("yanpa_multiserver")
                    .withUsername("yanpa")
                    .withPassword("yanpa")
                    .withEnv("TZ", "Asia/Seoul");
            container.start();
            mysql = container;
        }
        return mysql;
    }

    /**
     * 앱을 하나 더 띄운다.
     * @param name     리포트에 쓸 서버 이름(A/B)
     * @param ddlAuto  첫 서버 "create", 그 뒤 서버 "validate"
     */
    public static ServerHandle startServer(String name, String ddlAuto, String redisHost, int redisPort) {
        return startServer(name, ddlAuto, redisHost, redisPort, true);
    }

    /**
     * @param precheckEnabled Redis 사전 거절 켬/끔. 서버 2대 부하 측정(MultiServerLoadTest)이 켬/끔을 비교할 때 쓴다.
     *                        끄면 NoOpBidPriceGuard — 사전 거절에는 Redis를 쓰지 않는다.
     *                        분산 락은 별도 스위치(-Dmultiserver.distributedLock)라, 켜져 있으면 사전 거절이 꺼져도 락에는 Redis를 쓴다.
     */
    public static ServerHandle startServer(String name, String ddlAuto, String redisHost, int redisPort,
                                           boolean precheckEnabled) {
        MySQLContainer db = mysql();
        String jdbcUrl = db.getJdbcUrl()
                + (db.getJdbcUrl().contains("?") ? "&" : "?")
                + "serverTimezone=Asia/Seoul&useSSL=false&allowPublicKeyRetrieval=true";
        String[] args = {
                "--spring.profiles.active=test",
                "--server.port=0",
                "--spring.datasource.url=" + jdbcUrl,
                "--spring.datasource.username=" + db.getUsername(),
                "--spring.datasource.password=" + db.getPassword(),
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--spring.flyway.enabled=false",
                "--spring.jpa.hibernate.ddl-auto=" + ddlAuto,
                "--spring.data.redis.host=" + redisHost,
                "--spring.data.redis.port=" + redisPort,
                "--spring.data.redis.timeout=500ms",
                "--app.bid.precheck.enabled=" + precheckEnabled,
                // [분산 락] 기본 켬(2단계). -Dmultiserver.distributedLock=false면 도입 전 상태(서버마다 따로 락)를 재현한다.
                "--app.bid.lock.distributed.enabled=" + System.getProperty("multiserver.distributedLock", "true"),
                // [차단기] 이 PC는 Docker 경유 Redis 왕복이 부하 시 수십 ms까지 늘어, 운영값(30ms)이면 정상 Redis에도 차단기가 열린다.
                "--app.bid.lock.breaker.slow-call=200ms",
                "--spring.application.name=multiserver-" + name,
                "--app.upload.dir=build/test-uploads/multiserver-" + name,
        };
        try {
            ConfigurableApplicationContext context = new SpringApplicationBuilder(YanpaMarketBackendApplication.class).run(args);
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            return new ServerHandle(name, port, context);
        } catch (RuntimeException e) {
            throw new IllegalStateException("서버 " + name + " 기동 실패: " + e.getMessage(), e);
        }
    }
}
