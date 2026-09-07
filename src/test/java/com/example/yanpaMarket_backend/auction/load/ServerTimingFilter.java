package com.example.yanpaMarket_backend.auction.load;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * [무엇] 입찰 POST 요청의 "서버 처리 시간"을 직접 재고, 그 요청의 구간들을 한 번에 확정하는 필터.
 *
 * [왜 필요한가]
 *   응답 시간은 클라이언트가, 락 시간은 서버가 잰다. 그 차이를 "(a) 진입·조회"라고 불렀는데
 *   두 가지 문제가 있었다.
 *     1) 백분위끼리 뺐다 — p95(A+B) != p95(A)+p95(B) 라 존재하지 않는 값이 나온다
 *     2) 부하 생성기가 같은 JVM이라 클라이언트 비용(WS 구독자 팬아웃 등)이 섞였다
 *   이 필터가 서버 안쪽 시간을 직접 재면, 서버 내부 분해가 정확해지고
 *   "네트워크+클라이언트" 몫도 응답시간 - 서버처리시간 으로 분리해 볼 수 있다.
 *
 * [순서] Ordered.HIGHEST_PRECEDENCE — Spring Security 필터체인(기본 -100)보다 앞이라
 *   JWT 검증 시간까지 서버 처리 시간에 포함된다.
 *
 * [적용 범위] POST /api/v1/auctions/{id}/bids 만. 그 외 요청은 그냥 통과시킨다.
 */
final class ServerTimingFilter extends OncePerRequestFilter implements Ordered {

    private final HotAuctionLoadTest.LoadMetrics metrics;

    ServerTimingFilter(HotAuctionLoadTest.LoadMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        if (!"POST".equals(request.getMethod()) || !request.getRequestURI().endsWith("/bids")) {
            chain.doFilter(request, response);
            return;
        }

        RequestTiming.reset();
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long serverTotal = System.nanoTime() - start;
            long[] slot = RequestTiming.SLOT.get();
            long wait = slot[RequestTiming.WAIT];
            long hold = slot[RequestTiming.HOLD];
            long broadcast = slot[RequestTiming.BROADCAST];

            metrics.serverHandling.add(serverTotal);
            metrics.lockWait.add(wait);
            metrics.lockHold.add(hold);
            metrics.broadcast.add(broadcast);
            // 요청별 정확한 뺄셈 — 백분위 뺄셈이 아니다
            long tx = Math.max(0, hold - broadcast);
            metrics.transaction.add(tx);
            metrics.entry.add(Math.max(0, serverTotal - wait - hold));

            // [성공만 따로 기록하는 이유]
            //   거절(BID_TOO_LOW)은 락 안에서 SELECT 하나 하고 예외를 던지고 끝나 매우 빠르다.
            //   경합이 심하면 요청의 95%가 거절이라 락 보유/트랜잭션 통계가 거절 쪽으로 끌려간다.
            //   그 값으로 "단일 경매 상한 = 1/보유시간"을 계산하면 실제보다 몇 배 부풀려진다.
            //   성공한 입찰(INSERT + UPDATE + 커밋)만 따로 재야 진짜 쓰기 비용이 나온다.
            if (response.getStatus() == 201) {
                metrics.lockHoldSuccess.add(hold);
                metrics.transactionSuccess.add(tx);
            }

            RequestTiming.SLOT.remove();
        }
    }
}
