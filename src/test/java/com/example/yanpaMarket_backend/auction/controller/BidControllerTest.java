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

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
