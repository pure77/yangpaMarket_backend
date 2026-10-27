package com.example.yanpaMarket_backend.auction.precheck;

/**
 * [무엇] 사전 거절 판정 결과. 셋 중 하나뿐이라 sealed로 닫는다.
 * [왜 sealed인가] 호출자는 switch 패턴 매칭으로 분기하고 default를 두지 않는다.
 *   나중에 결과 종류를 추가하면, 처리하지 않은 switch가 전부 컴파일 에러가 되어 빠뜨릴 수 없다.
 */
public sealed interface PrecheckResult
        permits PrecheckResult.Pass, PrecheckResult.SelfBid, PrecheckResult.TooLow {

    PrecheckResult PASS = new Pass();
    PrecheckResult SELF_BID = new SelfBid();

    /** 락 경로로 진행한다(최종 판정은 DB). */
    record Pass() implements PrecheckResult {
    }

    /** 판매자 본인 입찰 → SELF_BID_NOT_ALLOWED. */
    record SelfBid() implements PrecheckResult {
    }

    /** 입찰가 미달 → BID_TOO_LOW. 응답 data(BidTooLowData)에 그대로 싣는다. */
    record TooLow(long currentPrice, long minimumBid) implements PrecheckResult {
    }
}
