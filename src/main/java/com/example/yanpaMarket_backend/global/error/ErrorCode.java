package com.example.yanpaMarket_backend.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청값이 올바르지 않습니다."),
    CONFLICT(HttpStatus.CONFLICT, "중복 또는 충돌이 발생했습니다."),
    OAUTH_ERROR(HttpStatus.BAD_REQUEST, "소셜 인증 처리에 실패했습니다."),
    PROFILE_SETUP_REQUIRED(HttpStatus.BAD_REQUEST, "프로필 추가 입력이 필요합니다."),
    AUCTION_NOT_FOUND(HttpStatus.NOT_FOUND, "경매를 찾을 수 없습니다."),
    CANNOT_MODIFY(HttpStatus.BAD_REQUEST, "입찰자가 있어 수정/삭제할 수 없습니다."),
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "이미지 파일이 올바르지 않습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;
}
