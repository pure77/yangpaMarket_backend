package com.example.yanpaMarket_backend.global.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * [무엇] 공통 응답 래퍼가 "실패 응답에도 부가 정보를 담을 수 있는지"를 검증한다.
 * [왜] 실패 시 data는 원래 항상 null이었다. 이 확장이 기존 에러 응답을 바꾸지 않는다는 것이
 *      하위호환의 핵심이라 두 오버로드를 나란히 검증한다.
 */
class ApiResponseTest {

    /** 값 2개짜리 테스트용 payload. 실제 사용 타입(BidTooLowData)은 Task 2에서 만든다. */
    private record Detail(long currentPrice, long minimumBid) {}

    @Test
    void failure에_data를_주면_그대로_담긴다() {
        Detail detail = new Detail(10000L, 20000L);

        ApiResponse<Detail> response = ApiResponse.failure("낮음", "BID_TOO_LOW", detail);

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isEqualTo(detail);
        assertThat(response.message()).isEqualTo("낮음");
        assertThat(response.code()).isEqualTo("BID_TOO_LOW");
    }

    @Test
    void 기존_failure는_data가_여전히_null이다() {
        ApiResponse<Void> response = ApiResponse.failure("권한 없음", "UNAUTHORIZED");

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.code()).isEqualTo("UNAUTHORIZED");
    }
}
