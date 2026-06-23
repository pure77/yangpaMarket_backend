package com.example.yanpaMarket_backend.user.domain; // user.domain = 사용자 도메인 패키지

import com.example.yanpaMarket_backend.common.domain.BaseTimeEntity; // createdAt/updatedAt 자동관리 부모
import jakarta.persistence.Column;
import jakarta.persistence.Entity;          // 이 클래스가 DB 테이블과 매핑됨을 표시
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;      // enum을 어떤 형태로 저장할지 지정
import jakarta.persistence.GeneratedValue;  // PK 자동 생성 전략
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;              // PK 필드 표시
import jakarta.persistence.Table;           // 테이블 이름/제약 설정
import jakarta.persistence.UniqueConstraint;// 유니크 제약(중복 방지)
import lombok.AccessLevel;
import lombok.Builder;                       // 빌더 패턴 생성자 자동 생성
import lombok.Getter;                        // getter 자동 생성
import lombok.NoArgsConstructor;             // 기본 생성자 자동 생성(JPA 필수)

/**
 * [무엇] 사용자 정보를 담는 JPA 엔티티 (= users 테이블 한 행).
 * [특징]
 *   - 소셜(카카오) 로그인 전용 → passwordHash는 null 허용.
 *   - email/phone/publicId는 유니크 제약으로 중복 방지.
 *   - "가입 완료" 판단: status==ACTIVE && nickname/phone 존재 (AuthService.isCompleteUser 참조).
 * [연결]
 *   - BaseTimeEntity 상속 → 생성/수정 시각 자동 기록.
 *   - UserRepository 로 조회/저장, UserMeResponse 로 변환되어 API 응답에 사용.
 *   - publicId 는 JWT subject 및 외부 API 식별자로 쓰인다.
 */
@Getter
@Entity
@Table(
        name = "users", // 매핑할 테이블 이름
        uniqueConstraints = { // 컬럼 중복을 DB 레벨에서 금지
                @UniqueConstraint(name = "uk_users_public_id", columnNames = "public_id"),
                @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_users_phone", columnNames = "phone")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 외부 무분별한 new 금지(JPA용 기본 생성자만 허용)
public class User extends BaseTimeEntity {

    /** 내부 PK (DB 자동증가, 외부 노출 금지) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // DB의 AUTO_INCREMENT 사용
    private Long id;

    /** 외부 노출 식별자 (JWT subject, API 응답에 사용) — 추측 방지용 별도 ID */
    @Column(name = "public_id", nullable = false, length = 64)
    private String publicId;

    /** 카카오 계정 이메일 (카카오가 비공개면 null) */
    @Column(name = "email", length = 255)
    private String email;

    /** 비밀번호 해시 — 소셜 전용이라 현재 null. 자체 가입 확장 시 사용 예정. */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    /** 서비스 내 닉네임 (회원가입 완료 단계에서 입력) */
    @Column(name = "nickname", length = 20)
    private String nickname;

    /** 연락처 (회원가입 완료 시 입력, 유니크) */
    @Column(name = "phone", length = 20)
    private String phone;

    /** 관리자 여부 — JWT의 isAdmin 클레임으로 전파되어 권한 판단에 사용 */
    @Column(name = "is_admin", nullable = false)
    private boolean isAdmin;

    /** 계정 상태 (PENDING_PROFILE / ACTIVE / INACTIVE) — 문자열로 저장 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private UserStatus status;

    /** 마케팅 수신 동의 여부 */
    @Column(name = "marketing_opt_in", nullable = false)
    private boolean marketingOptIn;

    /** 카카오 프로필 이미지 URL (null 허용) */
    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    /**
     * 빌더 생성자. 새 사용자 생성 시 필드를 골라 채운다.
     * (@Builder 로 User.builder().publicId(...).build() 형태로 호출)
     */
    @Builder
    public User(
            String publicId,
            String email,
            String passwordHash,
            String nickname,
            String phone,
            boolean isAdmin,
            UserStatus status,
            boolean marketingOptIn,
            String profileImageUrl
    ) {
        this.publicId = publicId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.phone = phone;
        this.isAdmin = isAdmin;
        this.status = status;
        this.marketingOptIn = marketingOptIn;
        this.profileImageUrl = profileImageUrl;
    }

    /**
     * [상태 변경] 회원가입 추가정보 입력 완료 시 호출.
     * 닉네임/전화번호/마케팅동의를 채우고 상태를 ACTIVE로 전환한다.
     * (엔티티가 스스로 자신의 상태를 바꾸는 도메인 메서드)
     */
    public void completeProfile(String nickname, String phone, boolean marketingOptIn) {
        this.nickname = nickname;
        this.phone = phone;
        this.marketingOptIn = marketingOptIn;
        this.status = UserStatus.ACTIVE; // 프로필 완성 = 활성 계정
    }

    /** [상태 변경] 카카오 프로필 이미지 URL 갱신 */
    public void updateProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }
}
