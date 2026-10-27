package com.example.yanpaMarket_backend.common.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class AfterCommitTest {

    @Autowired TransactionTemplate transactionTemplate;

    @Test
    void 커밋된_뒤에_실행된다() {
        AtomicBoolean ran = new AtomicBoolean();
        transactionTemplate.executeWithoutResult(status -> {
            AfterCommit.run(() -> ran.set(true));
            assertThat(ran).isFalse(); // 아직 커밋 전
        });
        assertThat(ran).isTrue();
    }

    @Test
    void 롤백되면_실행되지_않는다() {
        AtomicBoolean ran = new AtomicBoolean();
        transactionTemplate.executeWithoutResult(status -> {
            AfterCommit.run(() -> ran.set(true));
            status.setRollbackOnly();
        });
        assertThat(ran).isFalse();
    }

    @Test
    void 트랜잭션_밖에서는_즉시_실행된다() {
        AtomicBoolean ran = new AtomicBoolean();
        AfterCommit.run(() -> ran.set(true));
        assertThat(ran).isTrue();
    }
}
