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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
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
 *
 * [왜 placeBid에 @Transactional을 안 붙였나 — 이 클래스의 핵심 설계]
 *   포함 관계가 반드시 "락 ⊃ 트랜잭션"이어야 한다.
 *     @Transactional :  [ 트랜잭션 [ 락 ... 락해제 ] 커밋 ]  ← 락 해제 후 미커밋 구간 발생 = 락이 무의미
 *     현재 구조       :  [ 락 [ 트랜잭션 ... 커밋 ] 락해제 ]  ← 커밋까지 끝나야 락이 풀린다
 *   또 @Transactional이면 메서드 종료 시점에 커밋되므로 메서드 안의 broadcast가 커밋 전에 나간다.
 *   롤백돼도 메시지는 이미 전송되어 되돌릴 수 없다.
 *   → 그래서 트랜잭션 경계를 어노테이션이 아니라 TransactionTemplate으로 "코드에 명시"한다.
 *   (반대로 getBids는 읽기 전용이라 순서 제어가 필요 없어 @Transactional을 쓴다. 요구가 달라 도구가 다름)
 */
@Service
@RequiredArgsConstructor
public class BidService {

    private final BidRepository bidRepository;
    private final AuctionRepository auctionRepository;
    private final UserRepository userRepository;
    private final BidLock bidLock;
    private final SimpMessagingTemplate messagingTemplate;
    // [주입] TransactionTemplate은 Spring Boot가 자동 등록해주는 빈이다.
    //   spring-boot-transaction의 TransactionAutoConfiguration 안에
    //   TransactionTemplateConfiguration이 있고, 거기에
    //     @ConditionalOnSingleCandidate(PlatformTransactionManager.class)
    //     @Bean @ConditionalOnMissingBean TransactionTemplate transactionTemplate(PlatformTransactionManager)
    //   가 선언돼 있다. 즉 직접 조립할 필요가 없어 BidController와 동일하게
    //   @RequiredArgsConstructor로 주입만 받는다. (SimpMessagingTemplate과 같은 상황)
    // [조건] 트랜잭션 매니저가 "정확히 하나"일 때만 자동 등록된다. DB를 2개 이상 써서
    //   매니저가 여러 개가 되면 이 자동 설정이 꺼지므로 @Bean으로 직접 만들거나 @Qualifier로 지정할 것.
    private final TransactionTemplate transactionTemplate;

    /** 입찰을 처리하고 커밋 성공 후 BID_UPDATE를 broadcast한다. */
    public BidResponse placeBid(String bidderPublicId, String auctionPublicId, BidRequest request) {
        // 락 밖에서 식별자 해석(읽기 전용)
        // [왜 락 밖인가] 임계구역을 최대한 짧게 유지하기 위해. 여기 값들은 경합 대상이 아니다
        //   (사용자 PK/경매 PK는 변하지 않고, 닉네임 마스킹은 단순 문자열 조작).
        User bidder = userRepository.findByPublicId(bidderPublicId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
        // [ID 프로젝션을 쓰는 이유] 엔티티가 아니라 내부 PK만 가져온다.
        //   락 밖에서 읽은 경매는 이미 낡은 데이터(stale)다 — 이 줄과 락 획득 사이에
        //   다른 사람이 입찰해 currentPrice가 올랐을 수 있다. 그 값으로 검증하면
        //   TOCTOU(검사 시점 ≠ 사용 시점) 버그가 되므로, doPlaceBid가 락 안에서
        //   findById로 최신 상태를 다시 로딩해 검증한다.
        //   엔티티로 받으면 그 낡은 인스턴스가 영속성 컨텍스트에 남아, OSIV가 켜질 경우
        //   락 안의 findById가 1차 캐시에서 그걸 그대로 돌려줘 "재조회"가 무의미해진다.
        //   ID만 받으면 애초에 컨텍스트에 안 들어가므로 OSIV 설정과 무관하게 안전하다.
        Long auctionId = auctionRepository.findIdByPublicId(auctionPublicId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUCTION_NOT_FOUND));
        long amount = request.amount();
        String maskedBidder = NicknameMasker.mask(bidder.getNickname());

        // 락 안에서 [트랜잭션 실행 → 커밋 → broadcast] 까지 처리한다.
        PlacedBid placed;
        try {
            placed = bidLock.executeWithLock(auctionId, () -> {
                // transactionTemplate.execute()는 커밋까지 끝내고 반환한다. 그 호출이 락 람다 안에 있으므로
                // 커밋이 락 안에서 일어나고, 다음 스레드는 반드시 반영된 상태를 읽는다.
                PlacedBid p = transactionTemplate.execute(status -> doPlaceBid(auctionId, bidder.getId(), amount));

                // [커밋 후에만 broadcast] 별도 조건문이 없다. 검증 실패로 롤백되거나 @Version 충돌이 나면
                //   예외가 위로 전파되어 이 줄에 애초에 도달하지 않는다.
                //   트랜잭션 안에서 보냈다면 롤백돼도 "존재하지 않는 입찰"이 모든 구독자에게 전송된다.
                // [왜 락 안에서 보내나 — 메시지 순서 보장]
                //   락 밖에서 보내면 unlock과 send 사이에 스레드 스케줄링이 끼어들어
                //   나중 입찰(6,000원)의 메시지가 먼저 입찰(5,000원)보다 먼저 도착할 수 있다.
                //   프론트는 currentBid를 무조건 대입하므로 화면 현재가가 잠시 역행한다(DB는 정확).
                //   락 안에서 보내면 경매별로 전송 순서가 직렬화된다.
                //   임계구역이 조금 길어지지만 SimpleBroker는 인메모리라 비용이 작다.
                //   → 외부 브로커(RabbitMQ 등)로 전환하면 이 판단을 다시 해야 한다(네트워크 I/O가 됨).
                messagingTemplate.convertAndSend(
                        "/topic/auction/" + auctionPublicId,
                        BidUpdateMessage.of(p.currentPrice(), p.bidCount(), p.remainingTime(), maskedBidder));
                return p;
            });
        } catch (OptimisticLockingFailureException e) {
            // [이중 안전망] 1차 방어는 BidLock(같은 JVM 내 직렬화), 2차 방어는 Auction의 @Version(DB 레벨).
            //   서버 2대 이상이면 인메모리 락은 무력화되지만 @Version은 살아 있어 정합성이 깨지지 않는다.
            // [예외 번역] 안 잡으면 GlobalExceptionHandler의 Exception 최종 방어선에 걸려 500이 나간다.
            //   서버 오류가 아니라 "재시도하면 되는" 상황이므로 409로 번역해 프론트가 안내할 수 있게 한다.
            throw new ApiException(ErrorCode.ALREADY_BIDDING);
        }

        return BidResponse.of(
                placed.bidPublicId(), placed.amount(), placed.currentPrice(), placed.bidCount(), placed.createdAt());
    }

    /**
     * 임계구역 안의 트랜잭션 본문. 검증 실패 시 ApiException으로 롤백.
     * [왜 private인가] 락 없이 호출되면 동시성 보장이 통째로 무너진다. 접근 제어자로 오용을 차단한다.
     */
    private PlacedBid doPlaceBid(Long auctionId, Long bidderUserId, long amount) {
        // [now를 한 번만] isLive/endAt 검증, Bid 생성 시각, remaining 계산이 모두 같은 시각을 써야 한다.
        //   각자 now()를 부르면 "검증은 통과했는데 저장 시각은 종료 후" 같은 모순이 생길 수 있다.
        LocalDateTime now = LocalDateTime.now();
        // 락 안에서 최신 상태 재로딩(낙관적 잠금 버전 포함)
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUCTION_NOT_FOUND));

        // [검증을 도메인에 위임] "경매의 규칙은 경매가 안다"(리치 도메인 모델).
        //   규칙이 한 곳에만 있어 다른 진입점이 생겨도 우회할 수 없고, DB 없이 단위 테스트가 가능하다.
        auction.placeBid(bidderUserId, amount, now); // 규칙 위반 시 throw → 롤백

        // [순서가 강제되는 이유] save를 거쳐야 bid PK가 생기므로 assignHighestBid를 분리할 수밖에 없다.
        //   가격/카운트 갱신(placeBid) → INSERT로 PK 생성(save) → 그 PK를 경매에 반영(assignHighestBid).
        Bid bid = bidRepository.save(Bid.create(auctionId, bidderUserId, amount, now));
        auction.assignHighestBid(bid.getId());
        // auctionRepository.save(auction)이 없는 것도 의도적 — auction은 영속 상태라
        // JPA dirty checking이 커밋 시점에 UPDATE를 자동 생성한다.

        long remaining = Math.max(0, Duration.between(now, auction.getEndAt()).getSeconds()); // 이미 지난 경우 음수 방지
        return new PlacedBid(
                bid.getPublicId(), amount, auction.getCurrentPrice(), auction.getBidCount(), bid.getCreatedAt(), remaining);
    }

    /**
     * 입찰 내역 조회(공개). 닉네임 마스킹 + 최고가 표시.
     * [여기는 왜 @Transactional인가] 읽기 전용이라 락도 broadcast도 커밋 타이밍 제어도 없다.
     *   순서를 제어할 필요가 없으니 간결한 선언적 방식이 맞다. placeBid와 스타일이 다른 건 요구가 달라서다.
     * [readOnly=true 효과] JPA flush 모드가 MANUAL이 되어 dirty checking용 스냅샷을 만들지 않는다.
     */
    @Transactional(readOnly = true)
    public BidHistoryResponse getBids(String auctionPublicId, Pageable pageable) {
        Auction auction = auctionRepository.findByPublicId(auctionPublicId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUCTION_NOT_FOUND));
        Page<Bid> page = bidRepository.findByAuctionIdOrderByCreatedAtDesc(auction.getId(), pageable);

        // 입찰자 ID 목록으로 닉네임 일괄 조회
        // [N+1 회피] Bid마다 findById를 부르면 10건 조회에 쿼리 11번. 여기는 목록 1번 + 닉네임 1번 = 2번.
        // [Set인 이유] 같은 사람이 여러 번 입찰하면 ID가 중복되어 IN 절이 불필요하게 길어진다.
        // [(a,b)->a] toMap은 키 중복 시 예외를 던진다. PK라 중복될 리 없지만 방어적으로 첫 값을 유지.
        // [닉네임을 Bid에 저장하지 않는 이유] 비정규화하면 사용자가 닉네임을 바꿔도 과거 입찰엔 옛 값이 남는다.
        //   정규화를 유지하고 배치 조회로 성능을 확보하는 쪽을 택했다.
        Set<Long> bidderIds = new HashSet<>();
        page.getContent().forEach(bid -> bidderIds.add(bid.getBidderUserId()));
        Map<Long, String> nicknameById = userRepository.findAllById(bidderIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname, (a, b) -> a));

        // [isHighest 판정] 경매가 들고 있는 highestBidId(단일 진실 원천)와 대조한다.
        //   "금액이 제일 큰 것"으로 계산하면 현재 페이지 안에서만 비교하게 되어 2페이지에서 오판한다.
        return BidHistoryResponse.from(page, auction.getHighestBidId(), nicknameById);
    }

    /**
     * placeBid 트랜잭션 결과(REST 응답 + WS 메시지 구성을 위한 스냅샷).
     * [왜 스냅샷이 필요한가] 트랜잭션이 끝나면 영속성 컨텍스트가 닫혀 auction은 detached 상태가 된다.
     *   트랜잭션 밖에서 엔티티 게터를 호출하는 건 안전하지 않으므로, 필요한 값을 원시 타입으로 복사해 나온다.
     *   이 record 하나가 BidResponse(REST)와 BidUpdateMessage(WS) 둘 다의 재료가 된다.
     */
    private record PlacedBid(
            String bidPublicId, long amount, long currentPrice, int bidCount, LocalDateTime createdAt, long remainingTime) {
    }
}
