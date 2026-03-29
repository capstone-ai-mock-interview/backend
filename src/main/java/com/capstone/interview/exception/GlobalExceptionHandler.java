package com.capstone.interview.exception;

import java.time.LocalDateTime;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리.
 * 모든 Controller에서 발생한 예외를 잡아 ErrorResponse로 변환한다.
 * 새 에러 종류가 필요하면 커스텀 예외 클래스 + @ExceptionHandler 메서드를 추가.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 입력값 검증 오류 (400) */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleValidation(IllegalArgumentException e) {
        ErrorResponse error = new ErrorResponse(
            400, "VALIDATION_ERROR", e.getMessage(), LocalDateTime.now()
        );
        return ResponseEntity.status(400).body(error);
    }

    /** 예상하지 못한 서버 오류 (500) — catch-all */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAll(Exception e) {
        ErrorResponse error = new ErrorResponse(
            500, "INTERNAL_ERROR", "서버 내부 오류", LocalDateTime.now()
        );
        return ResponseEntity.status(500).body(error);
    }
}
