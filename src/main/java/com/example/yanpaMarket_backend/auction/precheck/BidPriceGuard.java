package com.example.yanpaMarket_backend.auction.precheck;

import java.time.LocalDateTime;

/**
 * [무엇] 입찰 락 앞에서 "확실히 무효인 입찰"을 걸러내는 문지기.
 *
 * [원칙] 의심스러우면 통과. 통과는 언제 해도 안전하고(락 안 DB 검증이 최종 판정), 거절은 확실할 때만 한다.
 * [예외를 던지지 않는다] 구현체는 저장소 장애를 스스로 삼켜야 한다. 호출자(BidService, AuctionService)는
 *   try/catch 없이 부른다. 특히 record/evict는 이미 커밋된 뒤라 예외가 나면 성공한 요청이 실패로 응답된다.
 * [왜 인터페이스인가] 서비스가 Redis를 직접 모르게 한다(BidLock과 같은 패턴). 스위치 꺼짐(NoOp)과
 *   부하 테스트의 측정용 래퍼도 같은 자리에 끼울 수 있다.
 */
public interface BidPriceGuard {

    /** 락 앞 판정. 거절이 확실할 때만 SelfBid/TooLow, 나머지는 전부 Pass. */
    PrecheckResult precheck(Long auctionId, Long bidderUserId, long amount, LocalDateTime now);

    /** 입찰 커밋 직후(락 안, 브로드캐스트 전) 최신 현재가를 기록한다. 현재가는 내려가지 않는다. */
    void record(Long auctionId, BidPriceSnapshot snapshot);

    /** 경매 수정·취소 커밋 후 기록을 지운다. 현재가가 내려갈 수 있는 유일한 경로다. */
    void evict(Long auctionId);
}
