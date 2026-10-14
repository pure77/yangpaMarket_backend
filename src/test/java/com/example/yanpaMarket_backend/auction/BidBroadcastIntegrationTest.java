package com.example.yanpaMarket_backend.auction;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionItemCondition;
import com.example.yanpaMarket_backend.auction.dto.BidRequest;
import com.example.yanpaMarket_backend.auction.dto.BidUpdateMessage;
import com.example.yanpaMarket_backend.auction.repository.AuctionRepository;
import com.example.yanpaMarket_backend.auction.repository.BidRepository;
import com.example.yanpaMarket_backend.auction.service.BidService;
import com.example.yanpaMarket_backend.common.util.PublicIdGenerator;
import com.example.yanpaMarket_backend.user.domain.User;
import com.example.yanpaMarket_backend.user.domain.UserStatus;
import com.example.yanpaMarket_backend.user.repository.UserRepository;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.lang.NonNull;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BidBroadcastIntegrationTest {

    @LocalServerPort int port;
    @Autowired BidService bidService;
    @Autowired UserRepository userRepository;
    @Autowired AuctionRepository auctionRepository;
    @Autowired BidRepository bidRepository;

    @AfterEach
    void tearDown() {
        bidRepository.deleteAll();
        auctionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 입찰하면_구독자가_1초내에_BID_UPDATE를_수신한다() throws Exception {
        User seller = userRepository.save(user("s-ws@y.com", "셀러", "010-3333-0001"));
        User bidder = userRepository.save(user("b-ws@y.com", "홍길동", "010-3333-0002"));
        LocalDateTime now = LocalDateTime.now();
        Auction auction = auctionRepository.save(Auction.builder()
                .publicId(PublicIdGenerator.newUlid())
                .sellerUserId(seller.getId())
                .title("WS 경매").description("설명")
                .category(AuctionCategory.ETC).itemCondition(AuctionItemCondition.USED)
                .startPrice(10000L).buyNowPrice(null)
                .startAt(now.minusMinutes(1)).endAt(now.plusHours(1)).build());
        String auctionPublicId = auction.getPublicId();

        WebSocketStompClient stompClient = new WebSocketStompClient(new SockJsClient(
                java.util.List.<Transport>of(new WebSocketTransport(new StandardWebSocketClient()))));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        BlockingQueue<BidUpdateMessage> received = new LinkedBlockingQueue<>();
        StompSession session = stompClient
                .connectAsync("http://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {})
                .get(2, TimeUnit.SECONDS);
        session.subscribe("/topic/auction/" + auctionPublicId, new StompFrameHandler() {
            @Override @NonNull public Type getPayloadType(@NonNull StompHeaders headers) {
                return BidUpdateMessage.class;
            }
            @Override public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                received.add((BidUpdateMessage) payload);
            }
        });

        Thread.sleep(300); // 구독 등록 완료 대기
        bidService.placeBid(bidder.getPublicId(), auctionPublicId, new BidRequest(20000L));

        BidUpdateMessage message = received.poll(1, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        assertThat(message.type()).isEqualTo("BID_UPDATE");
        assertThat(message.currentPrice()).isEqualTo(20000L);
        assertThat(message.maskedBidder()).isEqualTo("홍**");

        session.disconnect();
        stompClient.stop();
    }

    private User user(String email, String nickname, String phone) {
        return User.builder().publicId(PublicIdGenerator.newUlid())
                .email(email).nickname(nickname).phone(phone)
                .isAdmin(false).status(UserStatus.ACTIVE).marketingOptIn(false).build();
    }
}
