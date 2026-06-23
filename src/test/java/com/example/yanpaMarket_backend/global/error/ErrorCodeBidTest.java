package com.example.yanpaMarket_backend.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ErrorCodeBidTest {

    @Test
    void 입찰_에러코드_4종의_HTTP_상태가_정의된다() {
        assertThat(ErrorCode.BID_TOO_LOW.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.AUCTION_ENDED.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.SELF_BID_NOT_ALLOWED.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.ALREADY_BIDDING.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }
}
