package com.example.yanpaMarket_backend.auction.service;

import com.example.yanpaMarket_backend.auction.concurrency.BidLock;
import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.auction.domain.Bid;
import com.example.yanpaMarket_backend.auction.dto.BidHistoryResponse;
import com.example.yanpaMarket_backend.auction.dto.BidRequest;
import com.example.yanpaMarket_backend.auction.dto.BidResponse;
import com.example.yanpaMarket_backend.auction.dto.BidUpdateMessage;
import com.example.yanpaMarket_backend.auction.repository.AuctionRepository;
import com.example.yanpaMarket_backend.auction.repository.BidRepository;
import com.example.yanpaMarket_backend.common.util.NicknameMasker;
import com.example.yanpaMarket_backend.global.error.ApiException;
import com.example.yanpaMarket_backend.global.error.ErrorCode;
import com.example.yanpaMarket_backend.user.domain.User;
import com.example.yanpaMarket_backend.user.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 입찰 핵심 서비스.
 *
 * placeBid 흐름:
 *  1) 입찰자/경매 식별자 해석(락 밖, 읽기)
 *  2) BidLock.executeWithLock(auctionId, ...) 안에서 TransactionTemplate으로 짧은 쓰기 트랜잭션 실행
 *       - 경매 재로딩 → Auction.placeBid 검증/갱신 → Bid insert → highestBidId 반영
 *  3) 트랜잭션 정상 커밋 후(executeWithLock 정상 반환 직후) /topic/auction/{publicId}로 BID_UPDATE broadcast
 *
 * 안전망: 인메모리 락이 실패해 @Version 충돌이 나면 OptimisticLockingFailureException → ALREADY_BIDDING(409).
 */
@Service
public class BidService {

    private final BidRepository bidRepository;
    private final AuctionRepository auctionRepository;
    private final UserRepository userRepository;
    private final BidLock bidLock;
    private final SimpMessagingTemplate messagingTemplate;
    private final TransactionTemplate transactionTemplate;

    public BidService(
            BidRepository bidRepository,
            AuctionRepository auctionRepository,
            UserRepository userRepository,
            BidLock bidLock,
            SimpMessagingTemplate messagingTemplate,
            PlatformTransactionManager transactionManager) {
        this.bidRepository = bidRepository;
        this.auctionRepository = auctionRepository;
        this.userRepository = userRepository;
        this.bidLock = bidLock;
        this.messagingTemplate = messagingTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 입찰을 처리하고 커밋 성공 후 BID_UPDATE를 broadcast한다. */
    public BidResponse placeBid(String bidderPublicId, String auctionPublicId, BidRequest request) {
        // 락 밖에서 식별자 해석(읽기 전용)
        User bidder = userRepository.findByPublicId(bidderPublicId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
        Auction auctionRef = auctionRepository.findByPublicId(auctionPublicId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUCTION_NOT_FOUND));
        Long auctionId = auctionRef.getId();
        long amount = request.amount();
        String maskedBidder = NicknameMasker.mask(bidder.getNickname());

        // 락 안에서 트랜잭션 실행 → 커밋까지 완료된 결과 반환
        PlacedBid placed;
        try {
            placed = bidLock.executeWithLock(auctionId,
                    () -> transactionTemplate.execute(status -> doPlaceBid(auctionId, bidder.getId(), amount)));
        } catch (OptimisticLockingFailureException e) {
            throw new ApiException(ErrorCode.ALREADY_BIDDING);
        }

        // 커밋이 성공적으로 끝난 이후에만 broadcast (롤백/예외 시 여기 도달하지 않음)
        messagingTemplate.convertAndSend(
                "/topic/auction/" + auctionPublicId,
                BidUpdateMessage.of(placed.currentPrice(), placed.bidCount(), placed.remainingTime(), maskedBidder));

        return BidResponse.of(
                placed.bidPublicId(), placed.amount(), placed.currentPrice(), placed.bidCount(), placed.createdAt());
    }

    /** 임계구역 안의 트랜잭션 본문. 검증 실패 시 ApiException으로 롤백. */
    private PlacedBid doPlaceBid(Long auctionId, Long bidderUserId, long amount) {
        LocalDateTime now = LocalDateTime.now();
        // 락 안에서 최신 상태 재로딩(낙관적 잠금 버전 포함)
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUCTION_NOT_FOUND));

        auction.placeBid(bidderUserId, amount, now); // 규칙 위반 시 throw → 롤백

        Bid bid = bidRepository.save(Bid.create(auctionId, bidderUserId, amount, now));
        auction.assignHighestBid(bid.getId());

        long remaining = Math.max(0, Duration.between(now, auction.getEndAt()).getSeconds());
        return new PlacedBid(
                bid.getPublicId(), amount, auction.getCurrentPrice(), auction.getBidCount(), bid.getCreatedAt(), remaining);
    }

    /** 입찰 내역 조회(공개). 닉네임 마스킹 + 최고가 표시. */
    @Transactional(readOnly = true)
    public BidHistoryResponse getBids(String auctionPublicId, Pageable pageable) {
        Auction auction = auctionRepository.findByPublicId(auctionPublicId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUCTION_NOT_FOUND));
        Page<Bid> page = bidRepository.findByAuctionIdOrderByCreatedAtDesc(auction.getId(), pageable);

        // 입찰자 ID 목록으로 닉네임 일괄 조회
        Set<Long> bidderIds = new HashSet<>();
        page.getContent().forEach(bid -> bidderIds.add(bid.getBidderUserId()));
        Map<Long, String> nicknameById = userRepository.findAllById(bidderIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname, (a, b) -> a));

        return BidHistoryResponse.from(page, auction.getHighestBidId(), nicknameById);
    }

    /** placeBid 트랜잭션 결과(REST 응답 + WS 메시지 구성을 위한 스냅샷). */
    private record PlacedBid(
            String bidPublicId, long amount, long currentPrice, int bidCount, LocalDateTime createdAt, long remainingTime) {
    }
}
