package com.example.yanpaMarket_backend.auction.service;

import com.example.yanpaMarket_backend.auction.domain.AuctionStatus;
import com.example.yanpaMarket_backend.auction.dto.AuctionEndedMessage;
import com.example.yanpaMarket_backend.auction.repository.AuctionRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * [무엇] 만료 경매 일괄 종료 오케스트레이터.
 * [어떻게]
 *   - ACTIVE 상태이면서 endAt이 지난 경매의 "ID 목록"을 먼저 조회한다(트랜잭션 밖, 읽기).
 *   - 각 ID를 AuctionCloser.closeOne(...)에 넘겨 건별 트랜잭션으로 종료시킨다.
 *   - broadcast용 결과 목록을 반환한다(실제 send는 스케줄러가 커밋 후 수행).
 * [연결]
 *   - AuctionCloseScheduler가 10초 주기로 이 서비스를 호출한다.
 *   - 실제 상태 전이와 트랜잭션 경계는 AuctionCloser가 담당한다.
 *
 * [이 클래스에 @Transactional이 없는 이유 — 2차 수정의 핵심]
 *   예전에는 이 메서드 전체가 하나의 트랜잭션이었다. 그래서 종료 처리 중 누군가 입찰해
 *   @Version 충돌이 나면 **100건 중 1건 때문에 100건 전부 롤백**됐다.
 *   지금은 트랜잭션 경계가 AuctionCloser.closeOne(경매 1건)으로 내려갔고,
 *   이 클래스는 루프를 돌며 실패를 건별로 격리하는 역할만 한다.
 *   → 한 건이 실패해도 나머지는 정상 종료되고, 실패한 건은 다음 주기(10초 뒤)에 자동 재시도된다.
 *
 * [왜 self-invocation 문제가 없나]
 *   closeExpired()가 closeExpiredWithDestination()을 this로 부르지만,
 *   두 메서드 모두 @Transactional이 아니므로 프록시를 거칠 필요가 없다.
 *   트랜잭션이 필요한 지점은 별도 빈(AuctionCloser)이라 항상 프록시를 통해 호출된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionCloseService {

    private final AuctionRepository auctionRepository;
    private final AuctionCloser auctionCloser;

    /**
     * [실제 종료 메서드] 스케줄러가 호출한다.
     * broadcast 대상(경매 publicId)과 메시지를 함께 반환하여
     * 스케줄러가 트랜잭션 커밋 후 WebSocket send를 수행할 수 있게 한다.
     */
    public List<CloseResult> closeExpiredWithDestination(LocalDateTime now) {
        // 대상 ID만 조회한다. 엔티티를 읽어도 트랜잭션 밖이라 준영속이 되어 쓸 수 없고,
        // 실제 종료는 closeOne이 자기 트랜잭션 안에서 findById로 다시 읽는다.
        List<Long> expiredIds = auctionRepository.findIdsByStatusAndEndAtBefore(AuctionStatus.ACTIVE, now);
        if (expiredIds.isEmpty()) {
            return List.of(); // 10초마다 도는 배치라, 할 일이 없으면 로그도 남기지 않는다.
        }

        List<CloseResult> results = new ArrayList<>();
        int skipped = 0;
        for (Long auctionId : expiredIds) {
            try {
                CloseResult result = auctionCloser.closeOne(auctionId, now);
                if (result != null) { // null = 이미 종료됨(다른 주기/인스턴스가 처리)
                    results.add(result);
                }
            } catch (OptimisticLockingFailureException e) {
                // 종료 직전 입찰이 들어와 @Version이 충돌했다. 오류가 아니라 정상적인 경합이며,
                // 이 경매는 다음 주기(10초 뒤)에 다시 대상으로 잡혀 재시도된다.
                skipped++;
                log.debug("경매 종료 보류(동시 입찰 충돌) auctionId={}", auctionId);
            } catch (RuntimeException e) {
                // 예상치 못한 실패도 이 1건에만 가둔다. 나머지 경매는 계속 종료한다.
                skipped++;
                log.error("경매 종료 실패 auctionId={}", auctionId, e);
            }
        }
        log.info("경매 자동 종료 배치: 대상 {}건, 종료 {}건, 보류 {}건", expiredIds.size(), results.size(), skipped);
        return results;
    }

    /**
     * [테스트 편의 메서드] 메시지만 필요할 때 사용한다.
     * AuctionCloseServiceTest에서 직접 호출하여 결과 검증에 사용.
     */
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
