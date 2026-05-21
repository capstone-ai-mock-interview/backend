package com.capstone.interview.service;

import com.capstone.interview.config.LLMClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResumePreprocessorService {

    private static final int MAX_RESUME_CHARS = 8_000;

    private static final String SYSTEM_PROMPT = """
            당신은 이력서 원문을 면접 질문 생성에 쓰기 좋은 구조화 JSON으로 정리하는 도우미입니다.

            규칙:
            - 질문을 만들지 마세요.
            - 이력서에 실제로 적힌 내용만 정리하세요.
            - 없는 경험, 성과, 기술을 추측하거나 추가하지 마세요.
            - 프로젝트/경험 중심으로 정리하되, 각 claim에는 관련 기술스택 keywords를 반드시 포함하세요.
            - 응답은 JSON 객체 하나만 출력하세요.
            """;

    private static final String USER_PROMPT_TEMPLATE = """
            아래 이력서 원문을 다음 JSON 스키마로 정리하세요.

            {
              "summary": "이력서 내용 요약 1문장",
              "claims": [
                {
                  "text": "지원자가 이력서에 쓴 프로젝트/경험/역할/구현 사실 1개",
                  "keywords": ["해당 claim과 직접 연결된 기술스택 또는 검색 키워드"]
                }
              ]
            }

            세부 기준:
            - claims는 최대 8개까지 작성하세요.
            - claims.text는 "RAG를 사용했다"처럼 너무 일반적으로 쓰지 말고, 프로젝트 맥락을 포함하세요.
            - claims.keywords에는 Java, Spring Boot, JPA, PostgreSQL, AWS Bedrock, pgvector, RAG처럼 실제 기술명을 우선 포함하세요.
            - 기술스택이 명시되지 않은 claim은 keywords에 프로젝트 도메인 키워드를 넣으세요.

            [이력서 원문]
            %s
            """;

    private final LLMClient llmClient;
    private final ObjectMapper objectMapper;

    public String preprocess(String originalText) {
        if (originalText == null || originalText.isBlank()) {
            throw new IllegalArgumentException("이력서 원문이 비어있습니다.");
        }

        String resumeText = limitLength(originalText);
        String response = llmClient.invoke(SYSTEM_PROMPT, USER_PROMPT_TEMPLATE.formatted(resumeText));
        try {
            JsonNode root = objectMapper.readTree(extractJson(response));
            validate(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalArgumentException("이력서 전처리 응답 파싱에 실패했습니다.", e);
        }
    }

    private String limitLength(String text) {
        String trimmed = text.trim();
        if (trimmed.length() <= MAX_RESUME_CHARS) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_RESUME_CHARS);
    }

    private String extractJson(String llmResponse) {
        if (llmResponse == null) {
            return "";
        }
        String response = llmResponse.trim();
        if (response.startsWith("```")) {
            response = response
                    .replaceAll("^```json\\s*", "")
                    .replaceAll("^```\\s*", "")
                    .replaceAll("\\s*```$", "")
                    .trim();
        }
        return response;
    }

    private void validate(JsonNode root) {
        if (!root.isObject()) {
            throw new IllegalArgumentException("전처리 결과가 JSON 객체가 아닙니다.");
        }
        if (!root.hasNonNull("summary") || root.path("summary").asText().isBlank()) {
            throw new IllegalArgumentException("전처리 결과에 summary가 없습니다.");
        }
        JsonNode claims = root.path("claims");
        if (!claims.isArray() || claims.isEmpty()) {
            throw new IllegalArgumentException("전처리 결과에 claims가 없습니다.");
        }
        for (JsonNode claim : claims) {
            if (claim.path("text").asText().isBlank()) {
                throw new IllegalArgumentException("claim.text가 비어있습니다.");
            }
            JsonNode keywords = claim.path("keywords");
            if (!keywords.isArray() || keywords.isEmpty()) {
                throw new IllegalArgumentException("claim.keywords가 비어있습니다.");
            }
        }
    }
}
