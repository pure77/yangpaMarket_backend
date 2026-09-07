package com.example.yanpaMarket_backend.auction.scheduler;

import com.example.yanpaMarket_backend.auction.service.AuctionCloseService;
import com.example.yanpaMarket_backend.auction.service.AuctionCloseService.CloseResult;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * [무엇] 10초 주기로 만료 경매를 자동 종료하고 AUCTION_ENDED를 WebSocket으로 broadcast하는 스케줄러.
 * [어떻게]
 *   - @Scheduled(fixedDelay=10000): 이전 실행 완료 후 10초 뒤 재실행.
 *   - 종료 전이(트랜잭션)는 AuctionCloseService에 위임 → 커밋 후 결과를 받아 send.
 *     → self-invocation으로 인한 트랜잭션 프록시 누락 문제를 구조적으로 방지.
 *   - messagingTemplate.convertAndSend: "/topic/auction/{publicId}" 구독자에게 AUCTION_ENDED 전송.
 * [연결]
 *   - AuctionCloseService.closeExpiredWithDestination()이 트랜잭션 커밋까지 완료.
 *   - @EnableScheduling은 YanpaMarketBackendApplication에 선언.
 *
 * [왜 스케줄러가 필요한가 — 근본 문제]
 *   경매 종료는 "아무도 요청하지 않는 이벤트"다. "3시에 끝난다"고 DB에 적혀 있어도
 *   3시가 됐을 때 그걸 알려줄 사람이 없다. HTTP는 요청이 있어야만 동작한다.
 *   대안 비교:
 *     조회할 때 lazy 종료   → 아무도 안 보면 영원히 안 끝남. 낙찰 알림도 못 감
 *     경매마다 타이머 등록  → 경매 10만 개면 타이머 10만 개. 재시작하면 전부 소실
 *     주기적 폴링(현재)     → 단순하고, 재시작해도 다음 주기에 자동 복구
 *
 * [역할 분담 — CloseResult가 둘을 잇는 다리]
 *   AuctionCloseService   : 트랜잭션 안 (DB 상태 전이)  — WebSocket을 모른다
 *   AuctionCloseScheduler : 트랜잭션 밖 (broadcast)     — 종료 규칙을 모른다
 *   덕분에 서비스를 테스트할 때 WebSocket 모킹이 필요 없다(closeExpired 테스트 편의 메서드 참고).
 *
 * [해결됨]
 *   - 배치 전체 롤백: 트랜잭션 경계가 AuctionCloser.closeOne(경매 1건)으로 내려갔다.
 *     한 건이 @Version 충돌로 실패해도 나머지는 정상 종료되고, 실패 건은 다음 주기에 재시도된다.
 *   - 로깅: AuctionCloseService가 대상/종료/보류 건수를 남긴다(할 일이 없는 주기는 조용히 넘어간다).
 *
 * [알려진 한계 — 미수정]
 *   1) 서버 2대면 중복 실행: 각 서버가 스케줄러를 돌린다. @Version이 DB 이중 수정은 막지만
 *      broadcast는 두 번 나갈 수 있다. ShedLock 같은 분산 스케줄러 락 필요.
 *      → InMemoryBidLock, SimpleBroker와 같은 시점에 함께 해결해야 하는 항목.
 *   2) 종료 1건당 쿼리 3번(경매/입찰/낙찰자 조회). 건별 트랜잭션이라 배치 선조회로 줄이기 어렵다
 *      — 선조회한 낙찰 정보는 그 사이 새 입찰이 들어오면 낡은 값이 되기 때문이다.
 *      다만 여기서 N은 "10초 안에 만료된 경매 수"라 보통 0~수 건이고 전부 PK 조회다.
 *      정확성을 택하고 쿼리 수는 감수했다.
 */
@Component
@RequiredArgsConstructor
public class AuctionCloseScheduler {

    private final AuctionCloseService auctionCloseService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * [왜 fixedDelay인가 — fixedRate와의 차이]
     *   fixedRate  : 시작 시각 기준. 이전 작업이 12초 걸려도 10초 뒤 다음 실행을 예약 → 겹쳐서 쌓인다
     *   fixedDelay : 종료 시각 기준. 이전 작업이 끝나고 나서 10초 뒤 → 절대 겹치지 않는다
     *   DB를 건드리는 배치라 겹치면 같은 경매를 두 번 종료시키려 시도할 수 있다. fixedDelay가 안전.
     *
     * [10초의 의미 — 정확도 vs 부하]
     *   경매가 실제 endAt보다 최대 10초 늦게 종료된다(그동안 status는 여전히 ACTIVE).
     *   1초로 하면 정확하지만 DB 쿼리가 10배, 1분으로 하면 부하는 적지만 종료가 최대 1분 늦다.
     *   [핵심] 그 지연을 도메인이 메운다 — Auction.placeBid의 !endAt.isAfter(now) 조건이
     *   status가 ACTIVE여도 입찰을 거절한다. 그래서 주기를 느슨하게 둬도 데이터는 정확하다.
     *   이 두 파일은 짝을 이룬다. 한쪽만 보면 "왜 이렇게 대충?"으로 보인다.
     */
    @Scheduled(fixedDelay = 10000)
    public void closeExpiredAuctions() {
        // [왜 별도 빈에 위임하나 — self-invocation 함정]
        //   @Transactional은 AOP 프록시로 동작한다. 스프링이 진짜 객체를 감싼 프록시를 만들어
        //   컨테이너에 등록하고, 트랜잭션 begin/commit 코드는 "프록시의 감싸는 부분"에 있다.
        //   즉 프록시를 거치지 않으면 트랜잭션이 아예 시작되지 않는다.
        //
        //   외부 호출: 호출자 → [프록시: begin] → 진짜객체 → [프록시: commit]   ✅
        //   내부 호출: 진짜객체.A() → this.B()                                  ❌ 프록시를 안 거침
        //             (this는 언제나 진짜 객체다. 프록시는 바깥에서 들어오는 호출만 가로챌 수 있다)
        //
        //   만약 종료 로직을 이 클래스 안에 @Transactional 메서드로 두고 this로 호출했다면,
        //   에러도 경고도 없이 트랜잭션 없이 실행된다. 그러면 조회된 Auction이 준영속(detached)이라
        //   dirty checking이 동작하지 않아 UPDATE가 아예 안 나가고, 경매가 하나도 종료되지 않는다.
        //   (broadcast만 나가고 DB는 그대로 → 원인 찾기가 매우 어렵다)
        //
        //   아래처럼 별도 빈으로 분리하면 주입받은 auctionCloseService가 "프록시"이므로
        //   외부 호출이 되어 트랜잭션이 정상적으로 열린다. 구조적으로 실수할 수 없다.
        //   (BidService는 순서 제어까지 필요해 TransactionTemplate을 썼다 — 같은 문제, 다른 해법)
        List<CloseResult> results = auctionCloseService.closeExpiredWithDestination(LocalDateTime.now());
        // [커밋 후 broadcast] 위 메서드가 "반환된 시점 = 커밋 완료"다.
        //   롤백되면 예외가 전파되어 이 루프에 도달하지 않는다. BidService와 동일한 원칙.
        for (CloseResult result : results) {
            messagingTemplate.convertAndSend(
                    "/topic/auction/" + result.auctionPublicId(), result.message());
        }
    }
}
