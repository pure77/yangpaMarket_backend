package com.example.yanpaMarket_backend.common.cache;

/**
 * [무엇] 애플리케이션 캐시 이름 상수 모음.
 * [왜 상수로 두나] @Cacheable(cacheNames=...)와 CacheConfig의 정책 등록이 같은 문자열을 써야 한다.
 *   문자열을 각자 적으면 오타 하나로 "정책 없는 캐시"가 새로 만들어지는데, 에러가 나지 않아 알아채기 어렵다.
 * [왜 common 패키지인가] 리포지토리(auction), 서비스(user), 설정(config)이 모두 참조하므로
 *   어느 한 도메인에 두지 않는다.
 */
public final class CacheNames {

    /** 경매 publicId → 경매 PK. 값이 절대 바뀌지 않는다. (AuctionRepository.findIdByPublicId) */
    public static final String AUCTION_ID_BY_PUBLIC_ID = "auctionIdByPublicId";

    /** 사용자 publicId → UserSummary(id, nickname). (UserLookupService.findSummaryByPublicId) */
    public static final String USER_SUMMARY_BY_PUBLIC_ID = "userSummaryByPublicId";

    private CacheNames() {
    }
}
