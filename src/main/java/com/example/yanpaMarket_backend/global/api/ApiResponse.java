package com.example.yanpaMarket_backend.global.api;

/**
 * 프로젝트 공통 API 응답 래퍼.
 * - 성공: success=true, data 채움
 * - 실패: success=false, message/code 채움
 */
public record ApiResponse<T>(boolean success, T data, String message, String code) {

    /**
     * 성공 응답(data 포함) 팩토리 메서드.
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    /**
     * 성공 응답(message만 전달) 팩토리 메서드.
     */
    public static ApiResponse<Void> successMessage(String message) {
        return new ApiResponse<>(true, null, message, null);
    }

    /**
     * 실패 응답 팩토리 메서드.
     */
    public static ApiResponse<Void> failure(String message, String code) {
        return new ApiResponse<>(false, null, message, code);
    }
}
