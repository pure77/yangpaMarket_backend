package com.example.yanpaMarket_backend.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionItemCondition;
import com.example.yanpaMarket_backend.auction.dto.BidRequest;
import com.example.yanpaMarket_backend.auction.dto.BidUpdateMessage;
import com.example.yanpaMarket_backend.auction.repository.AuctionRepository;
import com.example.yanpaMarket_backend.auction.repository.BidRepository;
import com.example.yanpaMarket_backend.common.util.PublicIdGenerator;
import com.example.yanpaMarket_backend.user.domain.User;
import com.example.yanpaMarket_backend.user.domain.UserStatus;
import com.example.yanpaMarket_backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * [무엇] BID_UPDATE broadcast의 "전송 순서" 회귀 테스트.
 *
 * [배경 — 무엇을 막는 테스트인가]
 *   수정 전에는 convertAndSend가 락 밖에 있었다. unlock()과 send() 사이에 스레드 스케줄링이
 *   끼어들 수 있어, 나중 입찰(6,000원)의 메시지가 먼저 입찰(5,000원)보다 먼저 도착할 수 있었다.
 *   프론트의 onBidUpdate는 currentBid를 무조건 대입하므로 화면 현재가가 역행해 보인다(DB는 정확).
 *   지금은 락 안 커밋 직후에 보내므로 경매별 전송 순서가 직렬화된다.
 *
 * [어떻게 검증하나]
 *   SimpMessagingTemplate을 spy로 바꿔 실제 전송 대신 currentPrice만 호출 순서대로 기록한다.
 *   입찰이 성공할 때마다 현재가는 반드시 오르므로, 기록된 값은 단조 증가여야 한다.
 *
 * [주의] 수정 전 코드에서 이 테스트가 100% 실패하지는 않는다(스케줄링 타이밍 의존).
 *   순서가 어긋날 "가능성"을 잡는 테스트이므로, 실패하면 확실한 회귀 신호로 본다.
 *
 * @Transactional 미사용: 각 입찰이 실제 커밋되어야 순서를 검증할 수 있다.
 */
@SpringBootTest
@ActiveProfiles("test")
class BidBroadcastOrderTest {

    @Autowired BidService bidService;
    @Autowired UserRepository userRepository;
    @Autowired AuctionRepository auctionRepository;
    @Autowired BidRepository bidRepository;

    // 실제 브로커 전송을 가로채 순서만 기록하기 위해 spy로 대체한다.
    @MockitoSpyBean SimpMessagingTemplate messagingTemplate;

    private final List<Long> sentPrices = Collections.synchronizedList(new ArrayList<>());

    @AfterEach
    void tearDown() {
        bidRepository.deleteAll();
        auctionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 동시입찰시_broadcast된_현재가는_단조증가한다() throws InterruptedException {
        // 전송을 실제로 하지 않고 currentPrice만 호출 순서대로 적는다.
        doAnswer(invocation -> {
            Object payload = invocation.getArgument(1);
            if (payload instanceof BidUpdateMessage message) {
                sentPrices.add(message.currentPrice());
            }
            return null;
        }).when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        User seller = userRepository.save(user("s-order@y.com", "010-7000-0000"));
        LocalDateTime now = LocalDateTime.now();
        Auction auction = auctionRepository.save(Auction.builder()
                .publicId(PublicIdGenerator.newUlid())
                .sellerUserId(seller.getId())
                .title("순서 검증 경매").description("설명")
                .category(AuctionCategory.ETC).itemCondition(AuctionItemCondition.USED)
                .startPrice(10000L).buyNowPrice(null)
                .startAt(now.minusMinutes(1)).endAt(now.plusHours(1)).build());
        String auctionPublicId = auction.getPublicId();

        int n = 40;
        String[] bidderPublicIds = new String[n];
        for (int i = 0; i < n; i++) {
            User b = userRepository.save(user("b-order-" + i + "@y.com", String.format("010-6%03d-0000", i)));
            bidderPublicIds[i] = b.getPublicId();
        }

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        AtomicInteger success = new AtomicInteger();

        for (int i = 0; i < n; i++) {
            int amount = 20000 + i * 10000; // 모두 최소인상폭(10000) 이상 간격
            String bidderId = bidderPublicIds[i];
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    bidService.placeBid(bidderId, auctionPublicId, new BidRequest((long) amount));
                    success.incrementAndGet();
                } catch (Exception ignored) {
                    // 동시 도착 순서상 현재가보다 낮아 거절(BID_TOO_LOW)될 수 있음 — 정상
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown(); // 일제히 시작
        done.await(20, TimeUnit.SECONDS);
        pool.shutdown();

        List<Long> snapshot = new ArrayList<>(sentPrices);

        // 성공한 입찰 수만큼만 전송되어야 한다(롤백된 입찰은 broadcast되지 않는다).
        assertThat(snapshot).hasSize(success.get());
        assertThat(success.get()).isGreaterThan(1); // 순서를 볼 수 있을 만큼은 성공해야 의미가 있다

        // 핵심 불변식: 전송된 현재가는 단조 증가한다(역행하는 메시지가 없다).
        for (int i = 1; i < snapshot.size(); i++) {
            assertThat(snapshot.get(i))
                    .as("broadcast 순서 역전: index %d 에서 %d → %d", i, snapshot.get(i - 1), snapshot.get(i))
                    .isGreaterThan(snapshot.get(i - 1));
        }

        // 마지막으로 전송된 값은 DB의 최종 현재가와 일치해야 한다.
        Auction reloaded = auctionRepository.findByPublicId(auctionPublicId).orElseThrow();
        assertThat(snapshot.get(snapshot.size() - 1)).isEqualTo(reloaded.getCurrentPrice());
    }

    private User user(String email, String phone) {
        return User.builder().publicId(PublicIdGenerator.newUlid())
                .email(email).nickname("유저").phone(phone)
                .isAdmin(false).status(UserStatus.ACTIVE).marketingOptIn(false).build();
    }
}
