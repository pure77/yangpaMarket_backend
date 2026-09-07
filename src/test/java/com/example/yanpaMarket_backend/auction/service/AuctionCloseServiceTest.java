package com.example.yanpaMarket_backend.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionItemCondition;
import com.example.yanpaMarket_backend.auction.domain.AuctionStatus;
import com.example.yanpaMarket_backend.auction.domain.Bid;
import com.example.yanpaMarket_backend.auction.dto.AuctionEndedMessage;
import com.example.yanpaMarket_backend.auction.repository.AuctionRepository;
import com.example.yanpaMarket_backend.auction.repository.BidRepository;
import com.example.yanpaMarket_backend.common.util.PublicIdGenerator;
import com.example.yanpaMarket_backend.user.domain.User;
import com.example.yanpaMarket_backend.user.domain.UserStatus;
import com.example.yanpaMarket_backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// @Transactional 미사용: 종료 로직이 자체 트랜잭션으로 커밋되어야 하므로 수동 정리한다.
@SpringBootTest
@ActiveProfiles("test")
class AuctionCloseServiceTest {

    @Autowired AuctionCloseService closeService;
    // 특정 경매 1건만 충돌시키기 위해 spy로 대체한다(나머지 호출은 실제 구현이 그대로 실행된다).
    @MockitoSpyBean AuctionCloser auctionCloser;
    @Autowired AuctionRepository auctionRepository;
    @Autowired BidRepository bidRepository;
    @Autowired UserRepository userRepository;

    @AfterEach
    void tearDown() {
        bidRepository.deleteAll();
        auctionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 입찰이_있는_만료경매는_PAYMENT_PENDING으로_종료되고_낙찰자가_기록된다() {
        User seller = userRepository.save(user("s-close@y.com", "010-1111-0001"));
        User winner = userRepository.save(user("w-close@y.com", "010-1111-0002"));
        Auction auction = auctionRepository.save(expiredAuction(seller.getId()));
        Bid bid = bidRepository.save(Bid.create(auction.getId(), winner.getId(), 30000L, LocalDateTime.now()));
        auction.assignHighestBid(bid.getId());
        auctionRepository.saveAndFlush(auction);

        List<AuctionEndedMessage> messages = closeService.closeExpired(LocalDateTime.now());

        Auction reloaded = auctionRepository.findById(auction.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AuctionStatus.PAYMENT_PENDING);
        assertThat(reloaded.getWinnerUserId()).isEqualTo(winner.getId());
        assertThat(messages).anySatisfy(m -> {
            assertThat(m.type()).isEqualTo("AUCTION_ENDED");
            assertThat(m.winnerId()).isEqualTo(winner.getPublicId());
        });
    }

    @Test
    void 입찰이_없는_만료경매는_ENDED로_종료된다() {
        User seller = userRepository.save(user("s2-close@y.com", "010-1111-0003"));
        Auction auction = auctionRepository.save(expiredAuction(seller.getId()));

        closeService.closeExpired(LocalDateTime.now());

        Auction reloaded = auctionRepository.findById(auction.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AuctionStatus.ENDED);
        assertThat(reloaded.getWinnerUserId()).isNull();
    }

    /**
     * [회귀] 배치 전체 롤백 방지.
     * 수정 전: 만료 경매 전부가 한 트랜잭션이라, 1건이 @Version 충돌로 실패하면 전부 롤백됐다.
     * 수정 후: 트랜잭션 경계가 AuctionCloser.closeOne(1건)이라 실패는 그 1건에만 갇힌다.
     *
     * 세 건을 만료시키고 가운데 1건만 충돌하도록 spy로 강제한 뒤,
     * 나머지 2건이 정상 종료되고 실패한 1건은 ACTIVE로 남아 다음 주기에 재시도 가능한지 확인한다.
     */
    @Test
    void 한_건이_충돌해도_나머지_경매는_정상_종료된다() {
        User seller = userRepository.save(user("s3-close@y.com", "010-1111-0004"));
        Auction a1 = auctionRepository.save(expiredAuction(seller.getId()));
        Auction a2 = auctionRepository.save(expiredAuction(seller.getId()));
        Auction a3 = auctionRepository.save(expiredAuction(seller.getId()));

        // a2만 종료 직전 입찰이 들어온 상황을 흉내낸다.
        doThrow(new OptimisticLockingFailureException("동시 입찰 충돌"))
                .when(auctionCloser).closeOne(eq(a2.getId()), any(LocalDateTime.class));

        List<AuctionEndedMessage> messages = closeService.closeExpired(LocalDateTime.now());

        // 실패한 1건을 뺀 2건이 종료되고 메시지도 2건만 나간다.
        assertThat(messages).hasSize(2);
        assertThat(auctionRepository.findById(a1.getId()).orElseThrow().getStatus())
                .isEqualTo(AuctionStatus.ENDED);
        assertThat(auctionRepository.findById(a3.getId()).orElseThrow().getStatus())
                .isEqualTo(AuctionStatus.ENDED);
        // 충돌한 건은 손대지 않은 채 ACTIVE로 남아 다음 주기에 다시 대상이 된다.
        assertThat(auctionRepository.findById(a2.getId()).orElseThrow().getStatus())
                .isEqualTo(AuctionStatus.ACTIVE);
    }

    /** [회귀] 이미 종료된 경매는 다시 잡히지 않는다(멱등성). 두 번 돌려도 두 번째는 빈 결과여야 한다. */
    @Test
    void 두_번_실행해도_같은_경매를_다시_종료하지_않는다() {
        User seller = userRepository.save(user("s4-close@y.com", "010-1111-0005"));
        auctionRepository.save(expiredAuction(seller.getId()));

        assertThat(closeService.closeExpired(LocalDateTime.now())).hasSize(1);
        assertThat(closeService.closeExpired(LocalDateTime.now())).isEmpty();
    }

    private User user(String email, String phone) {
        return User.builder().publicId(PublicIdGenerator.newUlid())
                .email(email).nickname("유저").phone(phone)
                .isAdmin(false).status(UserStatus.ACTIVE).marketingOptIn(false).build();
    }

    private Auction expiredAuction(Long sellerId) {
        LocalDateTime now = LocalDateTime.now();
        return Auction.builder().publicId(PublicIdGenerator.newUlid())
                .sellerUserId(sellerId).title("만료 경매").description("설명")
                .category(AuctionCategory.ETC).itemCondition(AuctionItemCondition.USED)
                .startPrice(10000L).buyNowPrice(null)
                .startAt(now.minusDays(2)).endAt(now.minusMinutes(1)).build();
    }
}
