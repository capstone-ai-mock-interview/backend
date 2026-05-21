package com.capstone.interview.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class FeedbackResponse {
    private boolean success;
    private String totalFeedback; // AI의 전체 총평
    private String overallScore;
    private String competencyChart; //차트
    private String strengthTypes; // 강점 유형 JSON
    private String weaknessTypes; // 약점 유형 JSON
    private List<QAPair> qaPairs; // 질문-답변-개별피드백 세트 리스트
}