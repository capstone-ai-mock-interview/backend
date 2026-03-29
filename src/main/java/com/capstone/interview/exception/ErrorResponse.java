package com.capstone.interview.exception;

import java.time.LocalDateTime;

/**
 * 통일된 에러 응답 형식.
 * 모든 API 에러는 이 형식으로 반환된다.
 * 팀원은 이 클래스를 직접 사용할 필요 없음 — GlobalExceptionHandler가 자동 변환.
 */
public record ErrorResponse(
    int status,
    String code,
    String message,
    LocalDateTime timestamp
) {}
