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
}
