package com.example.yanpaMarket_backend.auction.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionItemCondition;
import com.example.yanpaMarket_backend.auction.repository.AuctionRepository;
import com.example.yanpaMarket_backend.auction.repository.BidRepository;
import com.example.yanpaMarket_backend.common.util.PublicIdGenerator;
import com.example.yanpaMarket_backend.security.JwtProvider;
import com.example.yanpaMarket_backend.user.domain.User;
import com.example.yanpaMarket_backend.user.domain.UserStatus;
import com.example.yanpaMarket_backend.user.repository.UserRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BidControllerTest {

    @LocalServerPort int port;
    @Autowired UserRepository userRepository;
    @Autowired AuctionRepository auctionRepository;
    @Autowired BidRepository bidRepository;
    @Autowired JwtProvider jwtProvider;

    private String auctionPublicId;
    private String bidderToken;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        // 재실행 시 유니크 제약 충돌 방지: 이전 데이터 정리
        bidRepository.deleteAll();
        auctionRepository.deleteAll();
        userRepository.deleteAll();

        User seller = userRepository.save(user("seller-ctl@y.com", "셀러", "010-1111-1111"));
        User bidder = userRepository.save(user("bidder-ctl@y.com", "비더", "010-2222-2222"));
        bidderToken = jwtProvider.createAccessToken(bidder.getPublicId(), false);

        LocalDateTime now = LocalDateTime.now();
        Auction auction = Auction.builder()
                .publicId(PublicIdGenerator.newUlid())
                .sellerUserId(seller.getId())
                .title("컨트롤러 경매").description("설명")
                .category(AuctionCategory.ETC).itemCondition(AuctionItemCondition.USED)
                .startPrice(10000L).buyNowPrice(null)
                .startAt(now.minusMinutes(1)).endAt(now.plusHours(1))
                .build();
        auctionPublicId = auctionRepository.save(auction).getPublicId();
    }

    private User user(String email, String nickname, String phone) {
        return User.builder()
                .publicId(PublicIdGenerator.newUlid())
                .email(email).nickname(nickname).phone(phone)
                .isAdmin(false).status(UserStatus.ACTIVE).marketingOptIn(false)
                .build();
    }

    @Test
    void 인증된_입찰은_201을_반환한다() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url("/api/v1/auctions/" + auctionPublicId + "/bids")))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + bidderToken)
                .POST(HttpRequest.BodyPublishers.ofString("{\"amount\":20000}"))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

        assertThat(res.statusCode()).isEqualTo(201);
    }

    @Test
    void 비로그인_입찰내역_조회는_200을_반환한다() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url("/api/v1/auctions/" + auctionPublicId + "/bids?page=0&size=10")))
                .GET()
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

        assertThat(res.statusCode()).isEqualTo(200);
    }

    @Test
    void 비로그인_입찰_시도는_401을_반환한다() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url("/api/v1/auctions/" + auctionPublicId + "/bids")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"amount\":20000}"))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

        assertThat(res.statusCode()).isEqualTo(401);
    }

    /**
     * [회귀] page 음수 검증.
     * 수정 전: @Min이 없어 PageRequest.of(-1, 10)이 IllegalArgumentException을 던졌고,
     *          GlobalExceptionHandler의 Exception 최종 방어선에 걸려 500이 나갔다.
     * 수정 후: @Validated + @Min(0)이 먼저 걸러 400 VALIDATION_ERROR로 응답한다.
     */
    @Test
    void 음수_page_요청은_400을_반환한다() throws Exception {
        HttpResponse<String> res = get("/api/v1/auctions/" + auctionPublicId + "/bids?page=-1");

        assertThat(res.statusCode()).isEqualTo(400);
        assertThat(res.body()).contains("VALIDATION_ERROR");
    }

    /**
     * [회귀] size 상한 검증.
     * 수정 전: 상한이 없어 size=100000 요청 하나로 입찰 전체 + 그 전원의 닉네임을 조회할 수 있었다.
     *          이 GET은 SecurityConfig에서 permitAll이라 비로그인 사용자도 호출 가능하다.
     * 수정 후: @Max(100)으로 차단한다. (프론트는 size=50 고정 호출이라 여유가 있다)
     */
    @Test
    void size_상한을_넘는_요청은_400을_반환한다() throws Exception {
        HttpResponse<String> res = get("/api/v1/auctions/" + auctionPublicId + "/bids?size=101");

        assertThat(res.statusCode()).isEqualTo(400);
        assertThat(res.body()).contains("VALIDATION_ERROR");
    }

    /** [회귀] 경계값은 통과해야 한다 — size=100은 상한 이내. */
    @Test
    void size_상한_경계값은_200을_반환한다() throws Exception {
        HttpResponse<String> res = get("/api/v1/auctions/" + auctionPublicId + "/bids?size=100");

        assertThat(res.statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url(path))).GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
