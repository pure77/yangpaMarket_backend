package com.example.yanpaMarket_backend.auction.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.yanpaMarket_backend.auction.domain.BidRules.Verdict;
import org.junit.jupiter.api.Test;

/**
 * [무엇] 입찰 판정 규칙(본인 입찰, 최소 인상폭)의 단위 테스트.
 * [왜] Auction.placeBid와 Redis 사전 거절이 이 함수 하나를 같이 쓴다. 여기서 경계가 틀리면 두 곳이 같이 틀린다.
 */
class BidRulesTest {

    private static final Long SELLER = 1L;
    private static final Long BIDDER = 2L;

    @Test
    void 금액이_현재가_더하기_인상폭과_같으면_OK() {
        assertThat(BidRules.check(SELLER, BIDDER, 20_000L, 10_000L, 10_000L)).isEqualTo(Verdict.OK);
    }

    @Test
    void 일원이라도_모자라면_TOO_LOW() {
        assertThat(BidRules.check(SELLER, BIDDER, 19_999L, 10_000L, 10_000L)).isEqualTo(Verdict.TOO_LOW);
    }

    @Test
    void 판매자의_낮은_입찰은_TOO_LOW보다_SELF_BID가_먼저() {
        // [순서] 자격 없음(본인) → 금액 부족. 도메인의 기존 검증 순서와 같아야 에러 코드가 바뀌지 않는다.
        assertThat(BidRules.check(SELLER, SELLER, 1L, 10_000L, 10_000L)).isEqualTo(Verdict.SELF_BID);
    }

    @Test
    void 판매자_비교는_참조가_아니라_값으로_한다() {
        // [왜] Long은 -128~127만 캐싱한다. ==로 비교하면 큰 ID에서만 본인 입찰이 뚫린다.
        Long seller = Long.valueOf(100_000L);
        Long sameIdOtherObject = Long.valueOf(100_000L);
        assertThat(BidRules.check(seller, sameIdOtherObject, 50_000L, 10_000L, 10_000L)).isEqualTo(Verdict.SELF_BID);
    }
}
