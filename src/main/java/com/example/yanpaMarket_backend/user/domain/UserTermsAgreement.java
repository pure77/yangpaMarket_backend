package com.example.yanpaMarket_backend.user.domain; // user.domain = 사용자 도메인 패키지

import com.example.yanpaMarket_backend.common.domain.BaseTimeEntity; // 생성/수정 시각 자동관리 부모
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;     // 연관 엔티티 로딩 시점(LAZY=지연) 지정
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;    // 외래키 컬럼 지정
import jakarta.persistence.ManyToOne;     // N:1 관계(여러 동의 ↔ 한 사용자)
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [무엇] 사용자의 "약관 동의 이력"을 담는 엔티티 (= user_terms_agreements 테이블 한 행).
 *        어떤 약관(termCode)에 동의/철회했는지 사용자별로 기록한다.
 * [어떻게 쓰임]
 *   - 회원가입 시 약관 동의 정보를 저장한다.
 * [연결]
 *   - User 와 N:1 관계(한 사용자가 여러 약관 동의 행을 가짐).
 *   - (user_id, term_code) 조합은 유니크 → 같은 약관 중복 기록 방지.
 *   - UserTermsAgreementRepository 로 조회/저장.
 */
@Getter
@Entity
@Table(
        name = "user_terms_agreements",
        // 한 사용자가 같은 약관 코드에 대해 여러 행을 갖지 못하도록 유니크 제약
        uniqueConstraints = @UniqueConstraint(name = "uk_user_terms_agreements_user_term", columnNames = {"user_id", "term_code"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA용 기본 생성자
public class UserTermsAgreement extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // PK 자동증가
    private Long id;

    /** 이 동의가 속한 사용자 (외래키 user_id). LAZY = 실제 접근 시점에 로딩 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 약관 식별 코드 (예: "TERMS_OF_SERVICE", "PRIVACY_POLICY") */
    @Column(name = "term_code", nullable = false, length = 100)
    private String termCode;

    /** 필수 약관 여부 (true=필수, false=선택) */
    @Column(name = "is_required", nullable = false)
    private boolean required;

    /** 동의 여부 (true=동의함) */
    @Column(name = "agreed", nullable = false)
    private boolean agreed;

    /** 동의한 시각 (미동의면 null일 수 있음) */
    @Column(name = "agreed_at")
    private LocalDateTime agreedAt;

    /** 동의 철회 시각 (철회 안 했으면 null) */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /**
     * 빌더 생성자. 약관 동의 행을 만들 때 사용.
     */
    @Builder
    public UserTermsAgreement(User user, String termCode, boolean required, boolean agreed, LocalDateTime agreedAt, LocalDateTime revokedAt) {
        this.user = user;
        this.termCode = termCode;
        this.required = required;
        this.agreed = agreed;
        this.agreedAt = agreedAt;
        this.revokedAt = revokedAt;
    }
}
