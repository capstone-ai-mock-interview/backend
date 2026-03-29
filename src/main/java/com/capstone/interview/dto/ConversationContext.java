package com.capstone.interview.dto;

import java.util.List;
import java.util.Set;

/**
 * 면접 세션의 대화 맥락. 질문 중복 방지 및 꼬리질문 판단에 사용.
 * 생성/관리: InterviewService / 사용: QuestionGenerator, FollowUpEngine
 */
public record ConversationContext(
    String sessionId,
    ResumeJson resumeJson,
    List<QAPair> history,
    Set<String> askedQuestions
) {}
