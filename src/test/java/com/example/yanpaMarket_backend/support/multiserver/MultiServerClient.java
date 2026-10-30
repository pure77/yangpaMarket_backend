package com.example.yanpaMarket_backend.support.multiserver;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * [무엇] 서버 2대 테스트의 "사용자 쪽": 지정한 서버로 입찰(HTTP)하고, 지정한 서버에 구독(STOMP)해 받은 메시지를 종류별로 기록한다.
 * [왜 서버를 매번 지정하나] 같은 경매라도 어느 서버로 들어가느냐가 이 테스트의 핵심이다(로드밸런서 없이 직접 고른다).
 */
public final class MultiServerClient implements AutoCloseable {

    /** 입찰 결과. status −1은 IO 오류(연결 실패·타임아웃). BID_TOO_LOW면 currentPrice/minimumBid가 채워진다. */
    public record BidResult(int status, String code, Long currentPrice, Long minimumBid) {
    }

    /** 구독자 한 명: 어느 서버에 붙었는지, 받은 입찰 갱신 가격들, 받은 종료 메시지 수. */
    public static final class Subscriber {
        private final String server;
        private final List<Long> bidUpdatePrices = new CopyOnWriteArrayList<>();
        private final AtomicInteger auctionEnded = new AtomicInteger();

        Subscriber(String server) {
            this.server = server;
        }

        public String server() {
            return server;
        }

        public List<Long> bidUpdatePrices() {
            return List.copyOf(bidUpdatePrices);
        }

        public int auctionEndedCount() {
            return auctionEnded.get();
        }
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final WebSocketStompClient stomp = new WebSocketStompClient(
            new SockJsClient(List.<Transport>of(new WebSocketTransport(new StandardWebSocketClient()))));
    private final List<StompSession> sessions = new CopyOnWriteArrayList<>();

    public BidResult bid(ServerHandle server, String auctionPublicId, String token, long amount) {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(server.baseUrl() + "/api/v1/auctions/" + auctionPublicId + "/bids"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString("{\"amount\":" + amount + "}"))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            if (body == null || body.isBlank()) {
                return new BidResult(response.statusCode(), null, null, null);
            }
            JsonNode root = json.readTree(body);
            JsonNode code = root.path("code");
            JsonNode data = root.path("data");
            return new BidResult(
                    response.statusCode(),
                    code.isMissingNode() || code.isNull() ? null : code.asText(),
                    longOrNull(data.path("currentPrice")),
                    longOrNull(data.path("minimumBid")));
        } catch (IOException e) {
            return new BidResult(-1, e.getClass().getSimpleName(), null, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new BidResult(-1, "INTERRUPTED", null, null);
        }
    }

    private static Long longOrNull(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asLong();
    }

    /** server의 /ws에 접속해 경매 토픽을 구독한다. 메시지는 type 필드로 구분해 기록한다. */
    public Subscriber subscribe(ServerHandle server, String auctionPublicId) throws Exception {
        StompSession session = stomp
                .connectAsync(server.baseUrl() + "/ws", new StompSessionHandlerAdapter() {})
                .get(10, TimeUnit.SECONDS);
        sessions.add(session);
        Subscriber subscriber = new Subscriber(server.name());
        session.subscribe("/topic/auction/" + auctionPublicId, new StompFrameHandler() {
            @Override
            @NonNull
            public Type getPayloadType(@NonNull StompHeaders headers) {
                return byte[].class; // 원문 그대로 받아 type 필드로 직접 구분한다(입찰 갱신·종료 메시지가 같은 토픽)
            }

            @Override
            public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                JsonNode node = json.readTree((byte[]) payload);
                String type = node.path("type").asText();
                if ("BID_UPDATE".equals(type)) {
                    subscriber.bidUpdatePrices.add(node.path("currentPrice").asLong());
                } else if ("AUCTION_ENDED".equals(type)) {
                    subscriber.auctionEnded.incrementAndGet();
                }
            }
        });
        return subscriber;
    }

    @Override
    public void close() {
        sessions.forEach(session -> {
            if (session.isConnected()) {
                session.disconnect();
            }
        });
        stomp.stop();
    }
}
