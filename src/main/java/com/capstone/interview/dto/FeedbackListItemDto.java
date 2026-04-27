package com.capstone.interview.dto;

import java.time.LocalDateTime;

/** 면접 기록 목록 조회용 (마이페이지) */
public record FeedbackListItemDto(
        Long interviewId,
        String category,
        String status,
        int questionCount,
        LocalDateTime createdAt
) {}
