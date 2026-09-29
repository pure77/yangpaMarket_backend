package com.example.yanpaMarket_backend.config;

import com.example.yanpaMarket_backend.config.properties.CorsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket 설정.
 *
 * [왜 필요한가]
 * WebSocket 자체는 "양방향 바이트 파이프"일 뿐이라 주소도 구독 개념도 없다.
 * 그 위에 STOMP(텍스트 메시징 프로토콜)를 얹어야 destination(/topic/auction/{경매ID})과
 * SUBSCRIBE / SEND / MESSAGE 명령을 쓸 수 있다. 이 클래스가 그 STOMP 규칙을 스프링에 등록한다.
 *
 * [이 프로젝트의 구조] — 쓰기는 REST, 전파만 WebSocket
 *   입찰 넣기 : POST /api/v1/auctions/{id}/bids → BidController → BidService (인증·검증·트랜잭션)
 *   입찰 전파 : BidService가 커밋 후 SimpMessagingTemplate.convertAndSend("/topic/auction/{id}")
 *              → SimpleBroker → 그 경매를 구독 중인 모든 브라우저
 *   경매 종료 : AuctionCloseScheduler(10초 주기)가 같은 topic으로 AUCTION_ENDED 발행
 *
 * [왜 입찰 쓰기를 STOMP(/app)가 아니라 REST로 받나]
 *   - 기존 JWT 필터 체인을 그대로 재사용 (STOMP면 CONNECT 프레임용 인터셉터를 따로 만들어야 함)
 *   - 409 ALREADY_BIDDING / 400 BID_TOO_LOW 같은 에러를 HTTP 상태코드로 즉시 반환 가능
 *   - "트랜잭션 커밋 후에만 broadcast"라는 순서를 서비스 코드에서 명확히 제어 가능
 *   → "쓰기는 신뢰할 수 있는 REST로, 읽기 전파는 빠른 WebSocket으로" 라는 역할 분담.
 *
 * [연결] 발행: BidService, AuctionCloseScheduler / 구독: 프론트 realtimeClient.ts
 */
@Configuration
// [무엇] STOMP 메시지 브로커 인프라를 통째로 켜는 스위치.
//        내부적으로 DelegatingWebSocketMessageBrokerConfiguration을 import한다.
// [무엇이 자동 생성되나] clientInboundChannel / clientOutboundChannel / brokerChannel,
//        StompSubProtocolHandler(WebSocket 프레임 ↔ STOMP 프레임 변환),
//        SimpAnnotationMethodMessageHandler(@MessageMapping 라우팅),
//        SimpleBrokerMessageHandler(구독자 관리 + fan-out),
//        그리고 BidService가 주입받아 쓰는 SimpMessagingTemplate 빈.
// [주의] 이 줄을 지우면 SimpMessagingTemplate 빈이 사라져 BidService 주입 실패 → 부팅 자체가 안 된다.
@EnableWebSocketMessageBroker
// [왜 이 인터페이스를 구현하나]
//   @EnableWebSocketMessageBroker가 만들어주는 기본 설정에는 딱 두 가지가 비어 있다.
//     (1) 클라이언트가 어느 URL로 접속하나  (2) 메시지를 어떻게 라우팅하나
//   이건 애플리케이션마다 달라서 스프링이 정할 수 없다. 그래서 스프링은 컨테이너에 등록된
//   모든 WebSocketMessageBrokerConfigurer 빈을 찾아 아래 콜백 메서드들을 호출해준다.
//   즉 이 인터페이스는 "부팅 중 스프링이 나를 불러주는 콜백 규약"이다.
//   이 설정 클래스가 없으면 엔드포인트가 하나도 등록되지 않아 클라이언트가 접속할 곳이 없다.
//   모든 메서드가 default라서 필요한 것만 골라 @Override 하면 된다.
//   (다른 훅 예시: configureClientInboundChannel — STOMP 단에 JWT 인증 인터셉터를 붙일 때 사용)
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // 허용 Origin은 SecurityConfig의 CORS와 같은 값을 공유한다(app.cors.allowed-origins).
    private final CorsProperties corsProperties;

    /**
     * 클라이언트가 WebSocket 핸드셰이크를 맺을 URL을 등록한다.
     * StompEndpointRegistry = "접속 주소 접수처". HTTP로 치면 @RequestMapping을 등록하는 자리.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 접속 주소: ws://localhost:8080/ws
        // [핵심] 핸드셰이크는 HTTP로 시작한다. 브라우저가 GET /ws + "Upgrade: websocket" 헤더를
        //        보내면 서버가 101 Switching Protocols로 답하며 그 TCP 연결이 WebSocket으로 승격된다.
        //        → 그래서 Spring Security에서도 /ws/** 를 "HTTP 경로로" 열어줘야 한다.
        registry.addEndpoint("/ws")
                // [무엇] Origin 화이트리스트(CORS).
                // [왜 필요한가] WebSocket에는 브라우저의 동일 출처 정책(SOP)이 적용되지 않는다.
                //        악성 사이트가 우리 서버로 연결을 그냥 열 수 있고, 브라우저는 그 연결에
                //        쿠키를 자동으로 실어 보낸다(CSWSH: Cross-Site WebSocket Hijacking).
                //        따라서 서버가 직접 핸드셰이크의 Origin 헤더를 검사해야 하며 그게 이 설정이다.
                // [왜 Origins가 아니라 OriginPatterns인가] setAllowedOrigins("*")는 credentials와
                //        함께 쓸 수 없다. Patterns 버전은 와일드카드 + credentials를 동시에 허용한다.
                // [설정에서 주입] app.cors.allowed-origins 를 SecurityConfig의 CORS와 함께 쓴다.
                //        예전에는 두 곳이 같은 값을 각자 하드코딩해, 배포 시 한쪽만 고치면
                //        나머지가 조용히 막혔다(서비스는 뜨는데 프론트만 안 붙는 장애).
                // [기본값 5173] Vite 개발 서버 포트. vite.config.ts의 프록시가 changeOrigin으로 Host만
                //        바꾸고 Origin 헤더는 그대로 넘기기 때문에 로컬에서 이 값으로 매칭된다.
                // [운영] CORS_ALLOWED_ORIGINS 환경변수로 주입한다. prod 프로필은 기본값 없이 필수라
                //        미설정 시 애플리케이션이 기동되지 않는다(잘못 배포되어 조용히 막히는 것보다 낫다).
                .setAllowedOriginPatterns(corsProperties.getAllowedOrigins().toArray(String[]::new))
                // [무엇] WebSocket이 막힌 환경(구형 프록시·방화벽·LB)에서의 자동 폴백.
                //        WebSocket → XHR-streaming → XHR-polling 순으로 내려앉으며,
                //        애플리케이션 코드 입장에서는 어느 쪽이든 똑같이 보인다.
                // [부수효과] /ws 하나만 열리는 게 아니라 SockJS 프로토콜용 하위 경로가 함께 열린다.
                //        /ws/info, /ws/{server}/{session}/websocket, /ws/{server}/{session}/xhr_streaming
                //        → Security에서 /ws 만 열면 /ws/info 에서 401이 나며 실패한다. 반드시 /ws/** 로.
                // [짝 맞추기] 프론트가 new SockJS("/ws")를 쓰므로 서버도 반드시 withSockJS()여야 한다.
                //        한쪽만 바꾸면 연결되지 않는다. (front: src/app/repositories/realtimeClient.ts)
                .withSockJS();
    }

    /**
     * 메시지 라우팅 설정.
     * MessageBrokerRegistry = destination 문자열의 접두사(prefix)를 보고 목적지를 가르는 규칙표.
     * (앞의 StompEndpointRegistry가 "어디로 접속하나"라면, 이건 "메시지를 어디로 흘려보내나")
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // [무엇] 두 가지를 동시에 뜻한다.
        //        (1) 스프링 내장 인메모리 브로커를 켠다. 브로커는 "누가 어떤 destination을 구독 중인지"를
        //            Map에 기억해뒀다가, 그 destination으로 메시지가 오면 구독자 전원에게 복사해 뿌린다(fan-out).
        //        (2) /topic 으로 시작하는 destination은 브로커가 담당한다.
        // [효과] /topic/auction/{경매ID} 형태라서 경매마다 채널이 자연스럽게 분리된다.
        //        abc123을 보는 사람은 xyz789의 입찰 메시지를 받지 않는다.
        // [한계 — 중요] Simple"Broker"는 인메모리 + 단일 인스턴스 전용이다.
        //        - 서버를 재시작하면 구독 정보가 전부 날아간다.
        //        - 서버를 2대로 늘리면 1번 서버의 구독자는 2번 서버가 보낸 메시지를 못 받는다.
        //          구독자 Map이 각 JVM 안에만 있기 때문. (메시지 영속성·재전송·ack도 없다)
        //        → InMemoryBidLock의 한계와 정확히 같은 지점이다. 스케일아웃하는 순간 둘 다 깨진다.
        //          그때가 브로커는 enableStompBrokerRelay(RabbitMQ/ActiveMQ), 락은 Redis 분산락으로
        //          함께 교체할 시점이다. MVP 단계에서는 외부 인프라 없이 바로 도는 쪽을 택했다.
        registry.enableSimpleBroker("/topic");

        // [무엇] 클라이언트가 SEND한 메시지 중 /app 으로 시작하는 것은 브로커로 직행시키지 않고
        //        @MessageMapping 컨트롤러로 보낸다.
        //          destination이 "/app/..."   → 컨트롤러로 (검증·DB저장 등 비즈니스 로직 수행)
        //          destination이 "/topic/..." → 브로커로 직행 (검증 없이 그대로 fan-out)
        // [왜 이 구분이 필요한가] 클라이언트가 /topic으로 직접 SEND할 수 있게 두면, 악의적인 사용자가
        //        아무 검증 없이 "현재가 100만원" 같은 가짜 입찰 메시지를 다른 구독자에게 뿌릴 수 있다.
        //        서버가 /app으로 받아 검증한 뒤 /topic으로 내보내는 것이 정석인 이유다.
        // [현재 상태] 이 프로젝트의 입찰 쓰기는 REST POST라 /app은 아직 미사용(표준 설정만 유지).
        //        실시간 채팅이나 "입력 중" 표시 같은 진짜 양방향 기능이 생기면 그때 사용한다.
        // [참고] setUserDestinationPrefix(기본값 "/user")는 특정 사용자 1명에게만 보내는 용도.
        //        "최고가에서 밀려났습니다" 같은 개인 알림에 쓴다. 현재는 미사용.
        registry.setApplicationDestinationPrefixes("/app");
    }
}
