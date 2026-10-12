package com.example.yanpaMarket_backend.auction.repository; // auction.repository = 경매 DB 접근 계층

import com.example.yanpaMarket_backend.auction.domain.Auction;
import com.example.yanpaMarket_backend.auction.domain.AuctionCategory;
import com.example.yanpaMarket_backend.auction.domain.AuctionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;     // 페이지네이션 결과
import org.springframework.data.domain.Pageable; // 페이지 요청(번호/크기/정렬)
import org.springframework.data.jpa.repository.Query; // 직접 작성한 JPQL 쿼리
import org.springframework.data.repository.query.Param; // 쿼리 파라미터 바인딩
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * [무엇] 경매(Auction) 엔티티의 DB 접근 Repository.
 * [연결]
 *   - AuctionService 가 목록/상세/내경매 조회에 사용.
 */
public interface AuctionRepository extends JpaRepository<Auction, Long> {

    Optional<Auction> findByPublicId(String publicId); // 외부 식별자로 경매 1건 조회(상세)

    /**
     * [ID 프로젝션] publicId로 내부 PK만 조회한다. 엔티티를 영속성 컨텍스트에 올리지 않는다.
     *
     * [왜 필요한가] BidService.placeBid는 락 밖에서 경매를 식별한 뒤,
     *   락 안에서 findById로 "최신 상태를 재조회"해 검증한다(TOCTOU 방지).
     *   그런데 락 밖에서 findByPublicId로 엔티티를 읽으면 그 낡은 인스턴스가 영속성 컨텍스트에 남는다.
     *   OSIV(spring.jpa.open-in-view)가 켜지면 요청 전체가 컨텍스트 하나를 공유하므로
     *   락 안의 findById가 DB에 가지 않고 1차 캐시의 낡은 인스턴스를 그대로 돌려준다 → 재조회가 무의미.
     *   ID만 가져오면 낡은 엔티티가 애초에 컨텍스트에 안 들어가므로 OSIV 설정과 무관하게 안전하다.
     *
     * 존재 확인(404 판정)은 Optional.empty()로 그대로 되고, 읽는 컬럼도 하나뿐이다.
     */
    @Query("select a.id from Auction a where a.publicId = :publicId")
    Optional<Long> findIdByPublicId(@Param("publicId") String publicId);

    /**
     * [공개 목록 조회] 진행중(status) + 공개시각 지남(start_at<=now) 경매를,
     * 카테고리/키워드(검색어)로 추가 필터링한다.
     * - 파라미터가 null이면 해당 조건은 건너뛴다(동적 필터).
     * - 문자열 결합이 아닌 파라미터 바인딩(:name)을 써서 SQL 인젝션을 방지.
     */
    @Query("""
            select a from Auction a
            where a.status = :status
              and a.startAt <= :now
              and (:category is null or a.category = :category)
              and (:keyword is null or a.title like %:keyword%)
            """)
    Page<Auction> findPublicList(
            @Param("status") AuctionStatus status,     // 보통 ACTIVE
            @Param("now") LocalDateTime now,           // 현재 시각(공개 유예 판정)
            @Param("category") AuctionCategory category,// 카테고리 필터(없으면 null)
            @Param("keyword") String keyword,          // 제목 검색어(없으면 null)
            Pageable pageable                          // 페이지/정렬 정보
    );

    /** [내 경매] 본인이 등록한 모든 경매(공개유예/취소 포함)를 최신순으로 조회 */
    List<Auction> findBySellerUserIdOrderByCreatedAtDesc(Long sellerUserId);

    /** 주어진 상태이면서 종료시각이 지난 경매(자동 종료 대상) 조회. */
    List<Auction> findByStatusAndEndAtBefore(AuctionStatus status, LocalDateTime time);

    /**
     * [자동 종료 대상 ID만 조회] 종료 배치가 "건별 트랜잭션"으로 처리하기 위해 ID 목록만 먼저 받는다.
     *
     * 엔티티를 통째로 읽지 않는 이유: 이 조회는 트랜잭션 밖에서 실행되므로 반환된 엔티티는 준영속이라
     * 어차피 쓸 수 없고, 실제 종료는 AuctionCloser가 각자 트랜잭션 안에서 findById로 다시 읽는다.
     * (ID 프로젝션을 쓰는 이유는 findIdByPublicId와 동일 — 낡은 엔티티를 영속성 컨텍스트에 올리지 않는다)
     */
    @Query("select a.id from Auction a where a.status = :status and a.endAt < :time")
    List<Long> findIdsByStatusAndEndAtBefore(
            @Param("status") AuctionStatus status,
            @Param("time") LocalDateTime time);
}
