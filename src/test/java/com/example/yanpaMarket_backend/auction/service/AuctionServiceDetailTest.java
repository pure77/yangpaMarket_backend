package com.example.yanpaMarket_backend.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionItemCondition;
import com.example.yanpaMarket_backend.auction.domain.Bid;
import com.example.yanpaMarket_backend.auction.dto.AuctionDetailResponse;
import com.example.yanpaMarket_backend.auction.repository.AuctionRepository;
import com.example.yanpaMarket_backend.auction.repository.BidRepository;
import com.example.yanpaMarket_backend.common.util.PublicIdGenerator;
import com.example.yanpaMarket_backend.user.domain.User;
import com.example.yanpaMarket_backend.user.domain.UserStatus;
import com.example.yanpaMarket_backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuctionServiceDetailTest {

    @Autowired private AuctionService auctionService;
    @Autowired private AuctionRepository auctionRepository;
    @Autowired private BidRepository bidRepository;
    @Autowired private UserRepository userRepository;

    private int phoneSeq = 0;

    private User user(String email, String nickname) {
        return User.builder()
                .publicId(PublicIdGenerator.newUlid())
                .email(email).nickname(nickname).phone("010-" + (++phoneSeq) + "-0000")
                .isAdmin(false).status(UserStatus.ACTIVE).marketingOptIn(false)
                .build();
    }

    @Test
    void 종료된_경매_상세는_낙찰자_publicId를_반환한다() {
        User seller = userRepository.save(user("seller@y.com", "판매자"));
        User winner = userRepository.save(user("winner@y.com", "홍길동"));
        LocalDateTime now = LocalDateTime.now();

        Auction auction = Auction.builder()
                .publicId(PublicIdGenerator.newUlid())
                .sellerUserId(seller.getId())
                .title("종료경매").description("설명")
                .category(AuctionCategory.ELECTRONICS)
                .itemCondition(AuctionItemCondition.LIKE_NEW)
                .startPrice(10000L).buyNowPrice(null)
                .startAt(now.minusHours(2)).endAt(now.minusMinutes(1))
                .build();
        // auction.getId()가 필요한 Bid.create 전에 먼저 영속화해 PK를 확보한다
        auction = auctionRepository.save(auction);
        auction.placeBid(winner.getId(), 20000L, now.minusMinutes(30));
        Bid bid = bidRepository.save(Bid.create(auction.getId(), winner.getId(), 20000L, now.minusMinutes(30)));
        auction.assignHighestBid(bid.getId());
        auction.close(bid, now.minusMinutes(1));
        String publicId = auctionRepository.save(auction).getPublicId();

        AuctionDetailResponse detail = auctionService.getDetail(publicId);

        assertThat(detail.winnerUserId()).isEqualTo(winner.getPublicId());
    }

    @Test
    void 진행중_경매_상세의_낙찰자는_null() {
        User seller = userRepository.save(user("seller2@y.com", "판매자2"));
        LocalDateTime now = LocalDateTime.now();
        Auction auction = Auction.builder()
                .publicId(PublicIdGenerator.newUlid())
                .sellerUserId(seller.getId())
                .title("진행경매").description("설명")
                .category(AuctionCategory.ELECTRONICS)
                .itemCondition(AuctionItemCondition.LIKE_NEW)
                .startPrice(10000L).buyNowPrice(null)
                .startAt(now.minusMinutes(1)).endAt(now.plusHours(1))
                .build();
        String publicId = auctionRepository.save(auction).getPublicId();

        AuctionDetailResponse detail = auctionService.getDetail(publicId);

        assertThat(detail.winnerUserId()).isNull();
    }
}
