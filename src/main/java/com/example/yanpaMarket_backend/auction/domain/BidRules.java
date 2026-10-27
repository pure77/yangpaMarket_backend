package com.example.yanpaMarket_backend.auction.domain;

/**
 * [무엇] 입찰 판정 규칙 ②본인 입찰 금지, ③최소 인상폭을 판정하는 순수 함수.
 *
 * [왜 따로 뺐나] 같은 규칙을 두 곳에서 쓴다.
 *   - Auction.placeBid : 락 안에서 DB의 최신 값으로 최종 판정
 *   - RedisBidPriceGuard: 락 앞에서 Redis 사본 값으로 사전 거절
 *   규칙이 자바와 Lua처럼 두 군데에 생기면 언젠가 어긋난다. 한 함수를 같이 부르면 어긋날 수 없다.
 *
 * [규칙 ①(종료 여부)은 왜 여기 없나] 종료 판정은 두 곳의 정책이 다르다.
 *   도메인은 "종료면 거절", 사전 거절은 "종료(일 수도 있음)면 통과"다(시계 차이로 유효한 입찰을 거절하지 않기 위해).
 *
 * [순서] 본인 여부(자격 없음) → 금액(자격은 있는데 부족). 판매자의 낮은 입찰은 SELF_BID가 나와야 한다.
 */
public final class BidRules {

    public enum Verdict { OK, SELF_BID, TOO_LOW }

    private BidRules() {
    }

    public static Verdict check(Long sellerUserId, Long bidderUserId, long amount, long currentPrice, long increment) {
        // [규칙 ②] 자기 물건 값을 올리는 자전거래(shill bidding) 방지.
        //   [== 대신 equals] Long은 객체라 ==는 참조 비교다. -128~127만 캐싱되어 작은 테스트 ID에서만 우연히 통과한다.
        //   [한계] 부계정을 만들면 우회된다. 근본 방어가 아니라 최소 방어선이다.
        if (sellerUserId.equals(bidderUserId)) {
            return Verdict.SELF_BID;
        }
        // [규칙 ③] "현재가보다 크면"이 아니라 "현재가 + 인상폭 이상". 1원씩 올리는 눈치싸움을 막는다.
        if (amount < currentPrice + increment) {
            return Verdict.TOO_LOW;
        }
        return Verdict.OK;
    }
}
