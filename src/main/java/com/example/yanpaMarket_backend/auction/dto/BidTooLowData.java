package com.example.yanpaMarket_backend.auction.dto;

/**
 * [무엇] BID_TOO_LOW 실패 응답의 data. 거절 시점의 현재가와, 통과하려면 넣어야 할 최소 금액.
 *
 * [왜] 거절만 알려주면 클라이언트는 추측으로 재시도하고 다시 거절당한다.
 *      숫자를 함께 주면 다음 시도가 거의 확실히 성공해 총 요청량이 줄어든다.
 *
 * [주의] 로직을 넣지 않는다. 값 2개짜리 운반체라서 도메인(Auction)이 직접 import해도
 *        결합이 사실상 없다. 계산은 던지는 쪽(Auction.placeBid)이 한다.
 */
public record BidTooLowData(long currentPrice, long minimumBid) {
}
