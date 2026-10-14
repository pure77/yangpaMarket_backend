package com.example.yanpaMarket_backend.user.service;

/**
 * [무엇] 캐시에 담는 사용자 요약(내부 PK + 닉네임).
 * [왜 User 엔티티를 캐시하지 않나]
 *   엔티티는 영속성 컨텍스트에 묶인 객체라, 트랜잭션 밖에서 꺼내 쓰면 지연 로딩 오류가 나거나
 *   여러 스레드가 같은 인스턴스를 공유하게 된다. 입찰에 필요한 값만 불변 record로 복사해 둔다.
 * [쓰는 곳] BidService.placeBid — id는 입찰 저장/본인 입찰 금지 검사, nickname은 broadcast 마스킹.
 */
public record UserSummary(Long id, String nickname) {
}
