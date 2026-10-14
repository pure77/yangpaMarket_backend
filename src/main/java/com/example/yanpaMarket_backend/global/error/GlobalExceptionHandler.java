package com.example.yanpaMarket_backend.global.error; // global.error = 예외/에러코드 공통 패키지

import com.example.yanpaMarket_backend.global.api.ApiResponse;        // 실패 응답 포맷으로 변환할 때 사용
import jakarta.validation.ConstraintViolationException;               // @Validated 파라미터 검증 실패 예외
import org.springframework.http.ResponseEntity;                       // HTTP 상태코드 + 바디를 함께 담는 응답 객체
import org.springframework.validation.FieldError;                     // 검증 실패한 개별 필드 정보
import org.springframework.web.bind.MethodArgumentNotValidException;  // @Valid @RequestBody 검증 실패 예외
import org.springframework.web.bind.annotation.ExceptionHandler;      // 특정 예외 타입을 처리하는 메서드 표시
import org.springframework.web.bind.annotation.RestControllerAdvice;  // 모든 @RestController에 공통 적용되는 예외 처리기

/**
 * [무엇] 컨트롤러에서 터지는 모든 예외를 한곳에서 가로채는 "전역 예외 처리기".
 *        예외를 프로젝트 공통 실패 응답 포맷(ApiResponse.failure)으로 변환한다.
 * [어떻게 쓰임]
 *   - 컨트롤러/서비스 어디서 예외가 나든 Spring 이 자동으로 여기 메서드 중 하나를 호출한다.
 *   - try/catch 를 컨트롤러마다 쓰지 않아도 일관된 에러 응답이 보장된다.
 * [연결]
 *   - ApiException → ErrorCode 의 상태/코드로 응답 (서비스가 의도적으로 던진 예외)
 *   - 검증 예외 → VALIDATION_ERROR 로 응답
 *   - 나머지 모든 예외 → INTERNAL_ERROR(500)로 응답 (최종 방어선)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * [비즈니스 예외] 서비스가 던진 ApiException 처리.
     * ErrorCode 안의 HTTP 상태와 코드 이름을 그대로 응답에 반영한다.
     *
     * exception.getData()는 대부분 null이라 기존 에러 응답은 {"data":null}로 동일하다.
     * ApiException.withData(...)로 던진 경우에만 값이 실린다.
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleApiException(ApiException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.failure(exception.getMessage(), errorCode.name(), exception.getData()));
    }

    /**
     * [요청 바디 검증 실패] @Valid 가 붙은 @RequestBody DTO 검증이 실패했을 때.
     * 실패한 첫 번째 필드의 메시지를 사용자에게 돌려준다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError(); // 검증 실패한 필드 1건 추출
        // 필드 정보가 없으면 기본 메시지, 있으면 그 필드에 지정된 메시지 사용
        String message = fieldError == null ? ErrorCode.VALIDATION_ERROR.getDefaultMessage() : fieldError.getDefaultMessage();
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus()) // 400 Bad Request
                .body(ApiResponse.failure(message, ErrorCode.VALIDATION_ERROR.name()));
    }

    /**
     * [파라미터 검증 실패] @PathVariable/@RequestParam 등에 건 제약조건 위반 시.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus()) // 400 Bad Request
                .body(ApiResponse.failure(exception.getMessage(), ErrorCode.VALIDATION_ERROR.name()));
    }

    /**
     * [최종 방어선] 위에서 잡지 못한 모든 예외 처리.
     * 내부 오류 상세는 노출하지 않고 일반화된 500 메시지만 내려준다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus()) // 500 Internal Server Error
                .body(ApiResponse.failure(ErrorCode.INTERNAL_ERROR.getDefaultMessage(), ErrorCode.INTERNAL_ERROR.name()));
    }
}
