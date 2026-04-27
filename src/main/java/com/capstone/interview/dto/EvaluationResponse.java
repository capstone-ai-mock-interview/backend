package com.capstone.interview.dto;

import java.util.List;

/** AI 면접 평가 결과 */
public record EvaluationResponse(
        int totalScore,                // 종합 점수 (100점 만점, 4개 항목 평균)
        String level,                  // 레벨 (상/중/하)
        int accuracyScore,             // 기술 정확성 점수 (100점 만점)
        int logicScore,                // 논리성 점수 (100점 만점)
        int depthScore,                // 깊이 점수 (100점 만점)
        int deliveryScore,             // 전달력 점수 (100점 만점)
        String overallFeedback,        // AI의 종합 피드백
        List<String> strengths,        // 강점 리스트
        List<String> weaknesses,       // 약점 리스트
        List<QAPair> detailedFeedbacks // 질문별 상세 피드백
) {}
