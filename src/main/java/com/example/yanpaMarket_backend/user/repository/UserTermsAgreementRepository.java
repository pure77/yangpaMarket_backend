package com.example.yanpaMarket_backend.user.repository; // user.repository = 사용자 DB 접근 계층

import com.example.yanpaMarket_backend.user.domain.UserTermsAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * [무엇] 약관 동의 이력(UserTermsAgreement)의 DB 접근 Repository.
 * [어떻게 쓰임]
 *   - 기본 CRUD는 JpaRepository가 제공.
 *   - deleteByUserId: 특정 사용자의 약관 동의 기록을 일괄 삭제(재동의/탈퇴 처리 등).
 * [연결]
 *   - AuthService 의 회원가입/약관 처리 흐름에서 사용.
 */
public interface UserTermsAgreementRepository extends JpaRepository<UserTermsAgreement, Long> {
    void deleteByUserId(Long userId); // 해당 사용자의 모든 약관 동의 행 삭제
}
