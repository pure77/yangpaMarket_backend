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
 */
@Component
@RequiredArgsConstructor
public class AuctionCloseScheduler {

    private final AuctionCloseService auctionCloseService;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedDelay = 10000)
    public void closeExpiredAuctions() {
        // 트랜잭션 커밋 후 종료 결과 목록 수신
        List<CloseResult> results = auctionCloseService.closeExpiredWithDestination(LocalDateTime.now());
        // 커밋 완료 후 각 경매 topic으로 AUCTION_ENDED broadcast
        for (CloseResult result : results) {
            messagingTemplate.convertAndSend(
                    "/topic/auction/" + result.auctionPublicId(), result.message());
        }
    }
}
