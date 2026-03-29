package com.capstone.interview.dto;

import java.util.List;

/**
 * 이력서 파싱 결과. LLM이 텍스트를 구조화한 데이터.
 * 생성: ResumeService / 사용: QuestionGenerator, InterviewService
 */
public record ResumeJson(
    List<String> techStacks,
    List<ProjectInfo> projects,
    List<CareerInfo> careers,
    List<TroubleshootingInfo> troubleshootingExperiences
) {}
