package com.capstone.interview.config;

import java.util.List;

/**
 * LLM 호출 공통 인터페이스.
 * 모든 LLM 호출 모듈(ResumeService, QuestionGenerator, FollowUpEngine, AnswerEvaluator)은
 * 이 인터페이스를 통해 LLM에 접근한다.
 * 구현체 예시: BedrockClaudeClient, BedrockTitanClient
 */
public interface LLMClient {

    /** 프롬프트를 보내고 텍스트 응답을 받는다 */
    String invoke(String prompt);

    /** 텍스트를 임베딩 벡터로 변환한다 (RAG 파이프라인용) */
    List<Float> embed(String text);
}
