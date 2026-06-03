package com.example.yanpaMarket_backend.global.error;

import com.example.yanpaMarket_backend.global.api.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 컨트롤러 전역 예외 처리기.
 * 모든 예외를 프로젝트 공통 실패 응답 포맷(ApiResponse.failure)으로 변환합니다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 서비스 계층에서 의도적으로 던진 비즈니스 예외 처리.
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.failure(exception.getMessage(), errorCode.name()));
    }

    /**
     * @Valid 요청 바디 검증 실패 처리.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? ErrorCode.VALIDATION_ERROR.getDefaultMessage() : fieldError.getDefaultMessage();
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(ApiResponse.failure(message, ErrorCode.VALIDATION_ERROR.name()));
    }

    /**
     * PathVariable/RequestParam 제약조건 위반 처리.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(ApiResponse.failure(exception.getMessage(), ErrorCode.VALIDATION_ERROR.name()));
    }

    /**
     * 처리되지 않은 예외에 대한 최종 방어 처리.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ApiResponse.failure(ErrorCode.INTERNAL_ERROR.getDefaultMessage(), ErrorCode.INTERNAL_ERROR.name()));
    }
}
