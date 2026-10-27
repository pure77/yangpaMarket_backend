package com.example.yanpaMarket_backend.common.transaction;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * [무엇] "이 트랜잭션이 커밋되면 그때 실행"을 한 줄로 쓰게 해 주는 유틸.
 *
 * [왜 필요한가] @Transactional 메서드 안에서 외부 저장소(Redis)를 바로 고치면, 뒤에서 롤백돼도
 *   외부 변경은 되돌아가지 않는다. 커밋이 확정된 뒤에만 실행해야 DB와 어긋나지 않는다.
 *
 * [트랜잭션 밖이면 즉시 실행] 기다릴 커밋이 없으니 미룰 이유가 없다. 조용히 버리면 호출이 사라진다.
 *
 * [주의] afterCommit에서 던진 예외는 이미 커밋된 뒤에 호출자에게 전파된다(커밋은 됐는데 요청은 실패).
 *   그래서 여기 넘기는 작업은 스스로 예외를 삼켜야 한다. BidPriceGuard.evict가 그렇게 만들어져 있다.
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
