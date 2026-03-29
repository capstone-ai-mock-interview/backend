package com.capstone.interview.dto;

/** 질문-답변 쌍. ConversationContext의 history에 누적됨 */
public record QAPair(
    String questionId,
    String questionText,
    String questionType,
    String answerText
) {}
