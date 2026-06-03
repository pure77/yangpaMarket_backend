package com.example.yanpaMarket_backend.user.dto;

import com.example.yanpaMarket_backend.user.domain.User;
import java.time.LocalDateTime;

public record UserMeResponse(
        String userId,
        String email,
        String nickname,
        String phone,
        String profileImage,
        LocalDateTime createdAt
) {
    public static UserMeResponse from(User user) {
        return new UserMeResponse(
                user.getPublicId(),
                user.getEmail(),
                user.getNickname(),
                user.getPhone(),
                user.getProfileImageUrl(),
                user.getCreatedAt()
        );
    }
}
