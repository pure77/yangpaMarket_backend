package com.example.yanpaMarket_backend.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.test.context.ActiveProfiles;

// @Transactional 미사용: 종료 로직이 자체 트랜잭션으로 커밋되어야 하므로 수동 정리한다.
@SpringBootTest
@ActiveProfiles("test")
class AuctionCloseServiceTest {

    @Autowired AuctionCloseService closeService;
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
