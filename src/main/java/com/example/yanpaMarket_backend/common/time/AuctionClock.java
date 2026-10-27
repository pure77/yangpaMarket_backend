package com.example.yanpaMarket_backend.common.time;

import java.time.LocalDateTime;

/**
 * [무엇] 경매 판정(입찰 가능 여부, 공개, 종료)에 쓰는 "현재 시각" 공급자.
 *
 * [왜 인터페이스인가] 서버를 여러 대로 늘리면 서버마다 시계가 조금씩 달라 마감 경계 판정이 어긋난다.
 *   그때 판정 시각을 DB(NOW(3))나 Redis(TIME) 한 곳에서 가져오도록 구현체만 바꾸면 된다.
 *   호출하는 쪽(BidService, AuctionService, AuctionCloseScheduler)은 바뀌지 않는다.
 *   지금 정책은 그대로 "처리하는 순간의 시각"이다.
 */
public interface AuctionClock {

    LocalDateTime now();
}
