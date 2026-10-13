package com.example.yanpaMarket_backend.user.service;

import com.example.yanpaMarket_backend.common.cache.CacheNames;
import com.example.yanpaMarket_backend.user.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * [무엇] publicId(JWT subject)로 사용자 요약(PK, 닉네임)을 조회하고 캐시한다.
 *
 * [왜 별도 클래스인가 — self-invocation 함정]
 *   @Cacheable은 AOP 프록시로 동작한다. 같은 클래스 안에서 this.메서드()로 부르면 프록시를 안 거쳐
 *   에러 없이 캐시만 무시된다(AuctionCloseScheduler 주석의 @Transactional 함정과 같은 원리).
 *   BidService가 "다른 빈"인 이 서비스를 호출하므로 항상 프록시를 거친다.
 *
 * [캐시 정책] CacheConfig 참고 — 저장 후 10분 만료.
 *   unless: 없는 사용자(Optional.empty → #result == null)는 캐시하지 않는다.
 *   (Spring Cache는 Optional의 내용물을 꺼내 저장하고, 꺼낼 때 다시 Optional로 감싸 돌려준다)
 *
 * [주의] 사용자 상태(탈퇴/정지)나 닉네임을 바꾸는 기능을 추가하면 그 메서드에
 *   @CacheEvict(cacheNames = CacheNames.USER_SUMMARY_BY_PUBLIC_ID, key = "<publicId>")를 붙일 것.
 */
@Service
@RequiredArgsConstructor
public class UserLookupService {

    private final UserRepository userRepository;

    @Cacheable(cacheNames = CacheNames.USER_SUMMARY_BY_PUBLIC_ID, unless = "#result == null")
    public Optional<UserSummary> findSummaryByPublicId(String publicId) {
        return userRepository.findByPublicId(publicId)
                .map(user -> new UserSummary(user.getId(), user.getNickname()));
    }
}
