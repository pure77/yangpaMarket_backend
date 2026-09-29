package com.example.yanpaMarket_backend.auction.concurrency;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 고정 개수(256) ReentrantLock 스트라이프 기반 인메모리 락.
 * auctionId 해시로 스트라이프를 선택하므로 락 객체 수가 무한 증가하지 않는다.
 * 단일 인스턴스 한정 — 서버 2대 이상에서는 무력화되며, 그 시점이 Redis 분산 락 도입 트리거다.
 *
 * [왜 synchronized가 아닌가]
 *   메서드에 synchronized를 걸면 모든 경매가 자물쇠 하나를 공유한다.
 *   전자제품 경매와 패션 경매가 서로 줄을 서게 되어 "동시 접속 100명" 목표에서 치명적이다.
 *   ReentrantLock은 추후 tryLock(timeout)/인터럽트 처리로 발전시킬 여지도 남는다.
 *
 * [왜 ConcurrentHashMap<Long, Lock>(경매별 락)이 아닌가]
 *   거짓 경합이 전혀 없어 이상적이지만 락 객체가 무한히 늘어난다(경매 10만 개 = 락 10만 개).
 *   언제 지울지도 어렵다 — 지우는 순간 다른 스레드가 그 락을 잡고 있을 수 있어
 *   제대로 하려면 참조 카운팅이 필요하고, 그 카운팅이 또 동시성 문제를 낳는다.
 *   스트라이핑은 "메모리 상한을 고정하는 대신 약간의 거짓 경합을 받아들이는" 거래다.
 *   (Guava Striped, 옛 ConcurrentHashMap의 segment와 같은 아이디어)
 *
 * [싱글톤이 전제] 스프링 빈은 기본 싱글톤이라 앱 전체가 아래 locks 배열 하나를 공유한다.
 *   이게 락이 동작하는 전제다. @Scope("prototype")이면 요청마다 새 배열이 생겨
 *   코드는 똑같아 보이는데 아무것도 막지 못한다.
 */
@Component
public class InMemoryBidLock implements BidLock {

    // 2의 거듭제곱. 동시 입찰 스레드 수보다 충분히 크면 충돌 확률이 낮다(목표 동시 100명 → 여유).
    // 충돌해도 정확성에는 문제가 없다. 무관한 두 경매가 잠깐 순서를 기다릴 뿐.
    private static final int STRIPES = 256;
    private final ReentrantLock[] locks = new ReentrantLock[STRIPES];

    public InMemoryBidLock() {
        for (int i = 0; i < STRIPES; i++) {
            // new ReentrantLock() = 비공정(unfair). new ReentrantLock(true)로 하면
            // "먼저 온 스레드가 먼저"가 보장되지만 처리량이 크게 떨어진다.
            // 경매에서 마이크로초 단위 도착 순서는 의미가 없으므로 비공정이 옳은 선택.
            locks[i] = new ReentrantLock();
        }
    }

    /**
     * 템플릿 메서드 패턴 — "락 잡고 → 뭔가 하고 → 반드시 푼다"는 뼈대를 고정하고
     * 가운데 "뭔가"만 호출자가 채운다. 호출자가 unlock()을 잊을 방법이 없다.
     * lock()/unlock()을 직접 노출했다면 언젠가 누군가 finally를 빼먹었을 것이다.
     *
     * Runnable이 아니라 Supplier<T>인 이유: 락 안에서 만든 결과(PlacedBid)를 밖으로 꺼내야 한다.
     * <T> 제네릭이라 입찰 전용이 아니라 범용 락으로 쓸 수 있다.
     */
    @Override
    public <T> T executeWithLock(Long auctionId, Supplier<T> action) {
        // [왜 % 가 아니라 Math.floorMod 인가 — 중요]
        //   hashCode()는 음수를 반환할 수 있고, 자바의 %는 음수를 그대로 남긴다.
        //     -5 % 256              = -5   → locks[-5] → ArrayIndexOutOfBoundsException
        //     Math.floorMod(-5, 256) = 251  → 안전
        //   Long.hashCode()는 (int)(value ^ (value >>> 32))라 값이 커지면 음수가 나온다.
        //   %로 짰다면 개발 중엔 멀쩡하다가 운영에서 특정 ID부터 터지는 버그가 됐을 것이다.
        ReentrantLock lock = locks[Math.floorMod(auctionId.hashCode(), STRIPES)];
        lock.lock();
        try {
            return action.get();
        } finally {
            // [finally가 절대적으로 필요한 이유]
            //   action.get()은 자주 예외를 던진다(입찰가 미달/본인 입찰/종료된 경매 — 전부 정상 흐름).
            //   finally가 없으면 그때마다 락이 영원히 안 풀리고, 그 스트라이프에 걸린 모든 경매가
            //   영구 정지한다. 서버 재시작 외에는 답이 없다. 데드락 중 가장 흔한 형태.
            lock.unlock();
        }
    }
}
