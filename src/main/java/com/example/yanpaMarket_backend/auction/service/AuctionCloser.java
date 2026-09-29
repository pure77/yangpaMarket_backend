package com.example.yanpaMarket_backend.auction.service;

import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.auction.domain.AuctionStatus;
import com.example.yanpaMarket_backend.auction.domain.Bid;
import com.example.yanpaMarket_backend.auction.dto.AuctionEndedMessage;
import com.example.yanpaMarket_backend.auction.repository.AuctionRepository;
import com.example.yanpaMarket_backend.auction.repository.BidRepository;
import com.example.yanpaMarket_backend.auction.service.AuctionCloseService.CloseResult;
import com.example.yanpaMarket_backend.user.domain.User;
import com.example.yanpaMarket_backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [무엇] 경매 1건을 자기 트랜잭션 안에서 종료시키는 컴포넌트.
 *
 * [왜 별도 빈으로 분리했나 — 두 가지 이유가 겹친다]
 *   (1) 건별 트랜잭션
 *       예전에는 AuctionCloseService가 만료 경매 "전부"를 한 트랜잭션으로 처리했다.
 *       종료 처리 중 누군가 그 경매에 입찰하면 @Version 충돌이 나고,
 *       100건 중 1건 때문에 100건 전부 롤백됐다. 인기 경매가 계속 충돌하면
 *       다른 경매 종료까지 무기한 밀린다.
 *       이제 이 메서드가 경매 1건의 트랜잭션 경계라, 실패는 그 1건에만 갇힌다.
 *   (2) self-invocation 회피
 *       @Transactional은 AOP 프록시로 동작해서 "외부에서 프록시를 통해" 호출될 때만 트랜잭션이 열린다.
 *       같은 클래스 안에서 this.closeOne(...)으로 부르면 프록시를 안 거쳐 조용히 무시된다.
 *       별도 빈으로 두면 AuctionCloseService가 주입받은 참조가 프록시라 항상 정상 동작한다.
 *       (AuctionCloseScheduler가 AuctionCloseService를 분리해 부르는 것과 같은 원리)
 *
 * [연결] AuctionCloseService가 만료 대상 ID를 돌면서 이 메서드를 건별로 호출한다.
 */
@Service
@RequiredArgsConstructor
public class AuctionCloser {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final UserRepository userRepository;

    /**
     * 경매 1건을 종료한다. 이 메서드 전체가 하나의 트랜잭션이다.
     *
     * @return broadcast에 필요한 결과. 이미 종료됐거나 사라진 경매면 null(보낼 것이 없다).
     * @throws org.springframework.dao.OptimisticLockingFailureException
     *         종료 직전 입찰이 들어와 @Version이 충돌한 경우. 호출자가 잡아 이 1건만 건너뛴다.
     */
    @Transactional
    public CloseResult closeOne(Long auctionId, LocalDateTime now) {
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        // [멱등성] 다른 주기가 이미 종료했거나(status != ACTIVE) 삭제됐으면 아무것도 하지 않는다.
        //   대상 조회와 이 트랜잭션 사이에는 시간차가 있으므로 반드시 다시 확인해야 한다.
        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE) {
            return null;
        }

        // 최고 입찰이 있으면 조회, 없으면 null
        Bid highest = (auction.getHighestBidId() == null)
                ? null
                : bidRepository.findById(auction.getHighestBidId()).orElse(null);

        // 경매 도메인이 판단한다: 입찰 있으면 PAYMENT_PENDING + winner 기록, 없으면 ENDED.
        // save() 호출이 없는 것은 의도적 — auction은 영속 상태라 dirty checking이 커밋 시 UPDATE를 만든다.
        auction.close(highest, now);

        // 낙찰자는 내부 PK가 아니라 publicId로 내보낸다(외부 식별자 노출 원칙).
        String winnerPublicId = (auction.getWinnerUserId() == null)
                ? null
                : userRepository.findById(auction.getWinnerUserId()).map(User::getPublicId).orElse(null);

        return new CloseResult(
                auction.getPublicId(),
                AuctionEndedMessage.of(auction.getCurrentPrice(), winnerPublicId));
    }
}
