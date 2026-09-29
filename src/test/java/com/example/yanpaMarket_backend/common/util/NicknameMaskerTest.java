package com.example.yanpaMarket_backend.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NicknameMaskerTest {

    @Test
    void 첫_글자만_남기고_나머지를_마스킹() {
        assertThat(NicknameMasker.mask("홍길동")).isEqualTo("홍**");
        assertThat(NicknameMasker.mask("A")).isEqualTo("A**");
    }

    @Test
    void null이나_공백은_익명으로_표시() {
        assertThat(NicknameMasker.mask(null)).isEqualTo("익명");
        assertThat(NicknameMasker.mask("   ")).isEqualTo("익명");
    }
}
