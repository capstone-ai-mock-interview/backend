package com.capstone.interview.dto;

/** 질문별 상세 — 내 답변 vs 모범답안 비교 + 개별 피드백 */
public record QAPair(
        String question,            // 질문 내용
        String userAnswer,          // 사용자 답변
        String modelAnswer,         // AI 모범답안
        String individualFeedback   // 개별 피드백
) {}
