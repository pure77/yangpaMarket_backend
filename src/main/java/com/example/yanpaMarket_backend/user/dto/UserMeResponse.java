package com.example.yanpaMarket_backend.user.dto; // user.dto = 사용자 API 요청/응답 전송 객체

import com.example.yanpaMarket_backend.user.domain.User; // 변환 대상 엔티티
import java.time.LocalDateTime;

/**
 * [무엇] "내 정보 조회" API의 응답 DTO.
 *        User 엔티티에서 외부로 보여줄 필드만 골라 담는다(내부 PK 등은 제외).
 * [어떻게 쓰임]
 *   - GET /api/v1/users/me 응답 데이터로 사용.
 * [연결]
 *   - UserController/UserService → from(user) 로 변환 → ApiResponse.success(...) 에 담아 반환.
 *   - 프론트의 사용자 정보 화면(마이페이지)과 형태가 맞춰져 있음.
 *
 * record = 불변 응답 객체.
 */
public record UserMeResponse(
        String userId,            // 외부 식별자(User.publicId)
        String email,             // 이메일
        String nickname,          // 닉네임
        String phone,             // 전화번호
        String profileImage,      // 프로필 이미지 URL
        LocalDateTime createdAt   // 가입 시각
) {
    /**
     * [변환 팩토리] User 엔티티 → UserMeResponse 로 매핑.
     * 엔티티를 그대로 노출하지 않고 필요한 필드만 추려서 반환한다.
     */
    public static UserMeResponse from(User user) {
        return new UserMeResponse(
                user.getPublicId(),       // 내부 id가 아닌 publicId를 외부 userId로
                user.getEmail(),
                user.getNickname(),
                user.getPhone(),
                user.getProfileImageUrl(),
                user.getCreatedAt()
        );
    }
}
