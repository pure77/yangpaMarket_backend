package com.example.yanpaMarket_backend.user.domain; // user.domain = 사용자 도메인(엔티티/상태) 패키지

/**
 * [무엇] 사용자 계정의 "상태"를 나타내는 enum.
 * [어떻게 쓰임]
 *   - User.status 컬럼에 문자열로 저장된다(@Enumerated(STRING)).
 *   - 회원가입 단계/활성/비활성 판단에 사용.
 * [연결]
 *   - 카카오 로그인 직후 PENDING_PROFILE → 추가정보 입력 완료 시 User.completeProfile()에서 ACTIVE 로 전환.
 */
public enum UserStatus {
    PENDING_PROFILE, // 소셜 로그인은 했지만 닉네임/전화번호 등 추가정보 미입력 (가입 미완료)
    ACTIVE,          // 가입 완료, 정상 이용 가능
    INACTIVE         // 탈퇴/휴면 등 비활성 계정
}
