package com.example.yanpaMarket_backend.auction.service;

import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.auction.domain.AuctionStatus;
import com.example.yanpaMarket_backend.auction.domain.Bid;
import com.example.yanpaMarket_backend.auction.dto.AuctionEndedMessage;
import com.example.yanpaMarket_backend.auction.repository.AuctionRepository;
import com.example.yanpaMarket_backend.auction.repository.BidRepository;
import com.example.yanpaMarket_backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [무엇] 만료 경매 일괄 종료 서비스.
 * [어떻게]
 *   - ACTIVE 상태인 경매 중 endAt이 지난 것을 조회한다.
 *   - 입찰이 있으면 PAYMENT_PENDING + winnerUserId 기록, 없으면 ENDED.
 *   - 한 트랜잭션 안에서 상태를 전이하고 커밋한 뒤,
 *     broadcast용 AuctionEndedMessage 목록을 반환한다(실제 send는 스케줄러가 커밋 후 수행).
 * [연결]
 *   - AuctionCloseScheduler가 10초 주기로 이 서비스를 호출한다.
 *   - @Scheduled 메서드와 분리된 별도 Bean으로 두어 self-invocation 트랜잭션 프록시 문제를 회피한다.
 */
@Service
@RequiredArgsConstructor
public class AuctionCloseService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final UserRepository userRepository;

    /**
     * [실제 종료 메서드] 스케줄러가 호출한다.
     * broadcast 대상(경매 publicId)과 메시지를 함께 반환하여
     * 스케줄러가 트랜잭션 커밋 후 WebSocket send를 수행할 수 있게 한다.
     */
    @Transactional
    public List<CloseResult> closeExpiredWithDestination(LocalDateTime now) {
        List<Auction> expired = auctionRepository.findByStatusAndEndAtBefore(AuctionStatus.ACTIVE, now);
        List<CloseResult> results = new ArrayList<>();
        for (Auction auction : expired) {
            // 최고 입찰이 있으면 조회, 없으면 null
            Bid highest = (auction.getHighestBidId() == null)
                    ? null
                    : bidRepository.findById(auction.getHighestBidId()).orElse(null);
            // 경매 도메인 종료: 입찰 있으면 PAYMENT_PENDING, 없으면 ENDED
            auction.close(highest, now);

            // 낙찰자 publicId 조회 (없으면 null)
            String winnerPublicId = (auction.getWinnerUserId() == null)
                    ? null
                    : userRepository.findById(auction.getWinnerUserId())
                            .map(u -> u.getPublicId()).orElse(null);

            results.add(new CloseResult(
                    auction.getPublicId(),
                    AuctionEndedMessage.of(auction.getCurrentPrice(), winnerPublicId)));
        }
        return results;
    }

    /**
     * [테스트 편의 메서드] 메시지만 필요할 때 사용한다.
     * AuctionCloseServiceTest에서 직접 호출하여 결과 검증에 사용.
     */
    @Transactional
    public List<AuctionEndedMessage> closeExpired(LocalDateTime now) {
        return closeExpiredWithDestination(now).stream().map(CloseResult::message).toList();
    }

    /**
     * [내부 DTO] 종료된 경매의 broadcast 대상(topic 목적지 = publicId)과 메시지.
     * auctionPublicId → "/topic/auction/{publicId}" 로 변환하여 WebSocket send.
     */
    public record CloseResult(String auctionPublicId, AuctionEndedMessage message) {
    }
}
