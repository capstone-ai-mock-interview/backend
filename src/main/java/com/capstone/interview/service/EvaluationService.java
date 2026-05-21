package com.capstone.interview.service;

import com.capstone.interview.config.LLMClient;
import com.capstone.interview.entity.Interview;
import com.capstone.interview.entity.InterviewMode;
import com.capstone.interview.entity.InterviewParticipant;
import com.capstone.interview.entity.InterviewQna;
import com.capstone.interview.entity.InterviewStatus;
import com.capstone.interview.event.QnaSavedEvent;
import com.capstone.interview.repository.InterviewParticipantRepository;
import com.capstone.interview.repository.InterviewQnaRepository;
import com.capstone.interview.repository.InterviewRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private static final String TURN_EVALUATION_SYSTEM_PROMPT = """
            당신은 신입/주니어 개발자 기술 면접 답변을 평가하는 전문 면접관입니다.

            평가 원칙:
            - 질문 의도와 지원자 답변 요약의 일치도, 기술 정확성, 논리성, 구체성, 실무 연결성을 기준으로 평가합니다.
            - 지나친 칭찬이나 혹평 없이, 실제 면접 후 피드백처럼 구체적이고 실용적으로 작성합니다.
            - 모범답안은 암기식 정답이 아니라 면접에서 말하기 좋은 자연스러운 답변으로 작성합니다.
            - 답변 요약이 비어 있거나 모름에 가까우면 부족한 점과 학습 방향을 짧게 제시합니다.

            출력 규칙:
            - 반드시 JSON 객체만 출력합니다.
            - Markdown, 코드블록, 설명 문장, 주석을 출력하지 않습니다.
            - individual_feedback은 기술 정확도, 논리적 전달력, 답변 구체성을 기준으로 평가하며, 답변의 강점과 약점을 모두 언급합니다. 150자 이하입니다.
            - model_answer는 180자 이하입니다.
            - 모든 문장은 한국어로 작성합니다.

            출력 형식:
            {
              "individual_feedback": "답변에 대한 짧고 구체적인 피드백",
              "model_answer": "면접에서 말하기 좋은 모범답안"
            }
            """;

    private static final String TOTAL_EVALUATION_SYSTEM_PROMPT = """
            당신은 신입/주니어 개발자 기술 면접 결과를 종합하는 전문 평가자입니다.

            역할:
            - 여러 턴의 질문, 답변 요약, 개별 피드백을 바탕으로 전체 면접 결과를 요약합니다.
            - 원본 답변을 새로 길게 재평가하지 말고, 제공된 턴별 평가 결과를 종합합니다.

            평가 유형 (7개):
            - 학습력: 새로운 기술을 습득하고 적용하는 능력
            - 문제해결력: 상황 분석, 해결 전략, 트러블슈팅 사고
            - 협업능력: 팀 내 소통, 의견 조율, 협력 경험
            - 기술역량: 개념 이해, 기술 정확성, 원리 설명 능력
            - 주도성: 자발적 개선, 리더십, 적극적 참여
            - 스트레스내성: 압박 상황 대처, 돌발 질문 대응력
            - 직무적합성: 지원 직무와 답변 경험의 연결성

            total_feedback 작성 규칙:
            - 면접에서 보여준 강점, 부족한 부분, 개선 방향을 하나의 자연스러운 문단으로 서술합니다.
            - 번호 매기기(1), 2), 3))나 항목 구분 없이 매끄럽게 이어지는 문장으로 작성합니다.
            - 준비도, 전달력, 지식 균형에 대한 분석을 포함합니다.
            - 다음 면접을 위한 핵심 학습 권고 2~3가지를 자연스럽게 녹여서 제시합니다.
            - 400자 이하

            strength_types / weakness_types 작성 규칙:
            - 7개 유형 중 강점 유형 1~2개를 선택하여 간단한 칭찬 코멘트와 함께 작성
            - 7개 유형 중 약점 유형 1~2개를 선택하여 간단한 보완 코멘트와 함께 작성

            competency_chart 작성 규칙:
            - 반드시 7개 유형 모두 포함
            - 해당 면접에서 평가할 수 없는 유형은 3점으로 설정
            - 값은 0~10 사이 정수

            출력 규칙:
            - 반드시 JSON 객체만 출력합니다.
            - Markdown, 코드블록, 설명 문장, 주석을 출력하지 않습니다.
            - overall_score는 반드시 "상", "중", "하" 중 하나입니다.
            - 모든 문장은 한국어로 작성합니다.

            출력 형식:
            {
              "total_feedback": "종합 피드백",
              "overall_score": "중",
              "strength_types": [
                {"type": "유형명", "comment": "칭찬 코멘트"}
              ],
              "weakness_types": [
                {"type": "유형명", "comment": "보완 코멘트"}
              ],
              "competency_chart": {
                "학습력": 3,
                "문제해결력": 0,
                "협업능력": 3,
                "기술역량": 0,
                "주도성": 3,
                "스트레스내성": 3,
                "직무적합성": 0
              }
            }
            """;

    private final InterviewRepository interviewRepository;
    private final InterviewQnaRepository interviewQnaRepository;
    private final InterviewParticipantRepository participantRepository;
    private final LLMClient llmClient;
    private final ObjectMapper objectMapper;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleQnaSaved(QnaSavedEvent event) {
        try {
            evaluateTurn(event.sessionId(), event.turnNumber());
        } catch (Exception e) {
            log.warn("[turn evaluation failed] sessionId={}, turn={}", event.sessionId(), event.turnNumber(), e);
        }
    }

    @Transactional
    public void evaluateTurn(String sessionId, Integer turnNumber) {
        Interview interview = interviewRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("면접 세션을 찾을 수 없습니다: " + sessionId));
        InterviewQna qna = interviewQnaRepository.findByInterviewAndSequenceNumber(interview, turnNumber)
                .orElseThrow(() -> new IllegalArgumentException("QnA를 찾을 수 없습니다: " + sessionId + "#" + turnNumber));

        if (!shouldEvaluateTurn(qna)) {
            return;
        }

        try {
            String response = llmClient.invoke(
                    TURN_EVALUATION_SYSTEM_PROMPT,
                    buildTurnPrompt(interview, qna)
            );
            JsonNode root = objectMapper.readTree(extractJson(response));

            String individualFeedback = root.path("individual_feedback").asText("");
            String modelAnswer = root.path("model_answer").asText("");
            qna.saveFeedback(modelAnswer, individualFeedback);
            interviewQnaRepository.save(qna);

            log.info("[turn evaluation saved] sessionId={}, turn={}", sessionId, turnNumber);
        } catch (Exception e) {
            throw new IllegalStateException("Turn evaluation failed: " + sessionId + "#" + turnNumber, e);
        }
    }

    @Async
    @Transactional
    public void evaluate(String sessionId) {
        waitForLastAgentSave();

        Interview interview = interviewRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("면접 세션을 찾을 수 없습니다: " + sessionId));

        if (interview.getStatus() == InterviewStatus.FAILED) {
            log.warn("[evaluation skipped] failed sessionId={}", sessionId);
            return;
        }

        if (interview.getMode() == InterviewMode.GROUP) {
            evaluateAllParticipants(sessionId);
            return;
        }

        if (interview.getTotalFeedback() != null) {
            log.info("[evaluation skipped] already completed sessionId={}", sessionId);
            return;
        }

        List<InterviewQna> qnas = interviewQnaRepository
                .findByInterviewOrderBySequenceNumberAsc(interview)
                .stream()
                .filter(this::hasQuestion)
                .toList();

        if (qnas.isEmpty()) {
            log.warn("[evaluation skipped] no valid QnA sessionId={}", sessionId);
            return;
        }

        for (InterviewQna qna : qnas) {
            if (shouldEvaluateTurn(qna)) {
                evaluateTurn(sessionId, qna.getSequenceNumber());
            }
        }

        String response = llmClient.invoke(
                TOTAL_EVALUATION_SYSTEM_PROMPT,
                buildTotalPrompt(interview, qnas)
        );
        parseAndSaveTotal(response, interview);
    }

    @Async
    @Transactional
    public void evaluateAllParticipants(String sessionId) {
        waitForLastAgentSave();

        Interview interview = interviewRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("면접 세션을 찾을 수 없습니다: " + sessionId));

        if (interview.getStatus() == InterviewStatus.FAILED) {
            log.warn("[participant evaluation skipped] failed sessionId={}", sessionId);
            return;
        }

        List<InterviewParticipant> participants =
                participantRepository.findByInterviewOrderByJoinedAtAsc(interview);

        for (InterviewParticipant participant : participants) {
            if (participant.hasFeedback()) {
                log.info("[evaluation skipped] participant already evaluated memberId={}",
                        participant.getMember().getId());
                continue;
            }
            evaluateParticipant(interview, participant);
        }
    }

    @Async
    @Transactional
    public void evaluateParticipant(String sessionId, Long memberId) {
        waitForLastAgentSave();

        Interview interview = interviewRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("면접 세션을 찾을 수 없습니다: " + sessionId));
        if (interview.getStatus() == InterviewStatus.FAILED) {
            log.warn("[participant evaluation skipped] failed sessionId={}, memberId={}", sessionId, memberId);
            return;
        }

        InterviewParticipant participant = participantRepository
                .findByInterviewOrderByJoinedAtAsc(interview)
                .stream()
                .filter(p -> p.getMember().getId().equals(memberId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("면접 참가자를 찾을 수 없습니다: " + sessionId + "#" + memberId));

        if (participant.hasFeedback()) {
            log.info("[evaluation skipped] participant already evaluated memberId={}", memberId);
            return;
        }
        evaluateParticipant(interview, participant);
    }

    private void evaluateParticipant(Interview interview, InterviewParticipant participant) {
        Long memberId = participant.getMember().getId();
        List<InterviewQna> qnas = interviewQnaRepository
                .findByInterviewAndRespondentMemberIdOrderBySequenceNumberAsc(interview, memberId)
                .stream()
                .filter(this::hasQuestion)
                .toList();

        if (qnas.isEmpty() && !interview.isGroupMode()) {
            qnas = interviewQnaRepository.findByInterviewOrderBySequenceNumberAsc(interview)
                    .stream()
                    .filter(this::hasQuestion)
                    .filter(q -> q.getRespondentMemberId() == null)
                    .toList();
        }

        if (qnas.isEmpty()) {
            log.warn("[evaluation skipped] no QnA for participant memberId={}", memberId);
            return;
        }

        for (InterviewQna qna : qnas) {
            if (shouldEvaluateTurn(qna)) {
                evaluateTurnForQna(interview, qna);
            }
        }

        qnas = interviewQnaRepository
                .findByInterviewAndRespondentMemberIdOrderBySequenceNumberAsc(interview, memberId)
                .stream()
                .filter(this::hasQuestion)
                .toList();
        if (qnas.isEmpty() && !interview.isGroupMode()) {
            qnas = interviewQnaRepository.findByInterviewOrderBySequenceNumberAsc(interview);
        }

        String response = llmClient.invoke(
                TOTAL_EVALUATION_SYSTEM_PROMPT,
                buildTotalPrompt(interview, qnas)
        );
        parseAndSaveParticipantTotal(response, participant);
    }

    private void evaluateTurnForQna(Interview interview, InterviewQna qna) {
        try {
            String response = llmClient.invoke(
                    TURN_EVALUATION_SYSTEM_PROMPT,
                    buildTurnPrompt(interview, qna)
            );
            JsonNode root = objectMapper.readTree(extractJson(response));
            String individualFeedback = root.path("individual_feedback").asText("");
            String modelAnswer = root.path("model_answer").asText("");
            qna.saveFeedback(modelAnswer, individualFeedback);
            interviewQnaRepository.save(qna);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Turn evaluation failed: " + interview.getSessionId() + "#" + qna.getSequenceNumber(), e);
        }
    }

    private void parseAndSaveParticipantTotal(String llmResponse, InterviewParticipant participant) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(llmResponse));
            String totalFeedback = root.path("total_feedback").asText("피드백 정보가 없습니다.");
            String overallScore = root.path("overall_score").asText("N/A");
            String competencyChart = root.path("competency_chart").isMissingNode()
                    ? "{}"
                    : root.path("competency_chart").toString();

            String combined = totalFeedback + "\n\n[SCORE]\n" + overallScore + "\n\n[CHART]\n" + competencyChart;
            participant.saveTotalFeedback(combined, overallScore);
            participantRepository.save(participant);
            log.info("[participant evaluation saved] sessionId={}, memberId={}",
                    participant.getInterview().getSessionId(), participant.getMember().getId());
        } catch (Exception e) {
            log.error("[participant evaluation parse failed] memberId={}", participant.getMember().getId(), e);
        }
    }

    private String buildTurnPrompt(Interview interview, InterviewQna qna) {
        return """
                직무/카테고리:
                %s

                질문:
                %s

                질문 의도:
                %s

                꼬리질문 여부:
                %s

                지원자 답변 핵심 요약:
                %s

                부족하다고 판단된 지점:
                %s
                """.formatted(
                valueOrDefault(interview.getCategory(), "일반 IT"),
                valueOrDefault(qna.getQuestionContent(), "없음"),
                valueOrDefault(qna.getIntent(), "없음"),
                qna.isFollowUp(),
                answerSummaryText(qna),
                valueOrDefault(qna.getFocusPoint(), "없음")
        );
    }

    private String buildTotalPrompt(Interview interview, List<InterviewQna> qnas) {
        StringBuilder sb = new StringBuilder();
        sb.append("직무/카테고리:\n")
                .append(valueOrDefault(interview.getCategory(), "일반 IT"))
                .append("\n\n총 질문 수:\n")
                .append(qnas.size())
                .append("\n\n턴별 평가 결과:\n");

        for (InterviewQna qna : qnas) {
            sb.append(qna.getSequenceNumber()).append(".\n")
                    .append("질문: ").append(valueOrDefault(qna.getQuestionContent(), "없음")).append('\n')
                    .append("답변 요약: ").append(answerSummaryText(qna)).append('\n')
                    .append("개별 피드백: ").append(valueOrDefault(qna.getIndividualFeedback(), "없음")).append("\n\n");
        }

        sb.append("위 턴별 평가 결과를 종합해 전체 피드백, 종합 등급, 역량 차트를 생성하세요.");
        return sb.toString();
    }

    private void parseAndSaveTotal(String llmResponse, Interview interview) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(llmResponse));
            String totalFeedback = root.path("total_feedback").asText("피드백 정보가 없습니다.");
            String overallScore = root.path("overall_score").asText("N/A");
            String competencyChart = root.path("competency_chart").isMissingNode()
                    ? "{}"
                    : root.path("competency_chart").toString();
            String strengthTypes = root.path("strength_types").isMissingNode()
                    ? "[]"
                    : root.path("strength_types").toString();
            String weaknessTypes = root.path("weakness_types").isMissingNode()
                    ? "[]"
                    : root.path("weakness_types").toString();

            String combined = totalFeedback + "\n\n[SCORE]\n" + overallScore
                    + "\n\n[CHART]\n" + competencyChart
                    + "\n\n[STRENGTH]\n" + strengthTypes
                    + "\n\n[WEAKNESS]\n" + weaknessTypes;
            interview.saveTotalFeedback(combined);
            interviewRepository.save(interview);
            log.info("[total evaluation saved] sessionId={}", interview.getSessionId());
        } catch (Exception e) {
            log.error("[total evaluation parse failed] sessionId={}, response={}",
                    interview.getSessionId(), preview(llmResponse), e);
            interview.fail();
            interviewRepository.save(interview);
        }
    }

    private boolean shouldEvaluateTurn(InterviewQna qna) {
        return hasQuestion(qna)
                && qna.getAnswerSummary() != null
                && (isBlank(qna.getModelAnswer()) || isBlank(qna.getIndividualFeedback()));
    }

    private boolean hasQuestion(InterviewQna qna) {
        return qna.getQuestionContent() != null && !qna.getQuestionContent().isBlank();
    }

    private String answerSummaryText(InterviewQna qna) {
        String raw = qna.getAnswerSummary();
        if (raw == null || raw.isBlank()) {
            return "답변 요약 없음";
        }

        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.isArray()) {
                if (node.isEmpty()) {
                    return "답변 요약 없음";
                }
                StringBuilder sb = new StringBuilder();
                for (JsonNode item : node) {
                    if (!item.asText("").isBlank()) {
                        if (sb.length() > 0) {
                            sb.append(' ');
                        }
                        sb.append(item.asText());
                    }
                }
                return sb.length() == 0 ? "답변 요약 없음" : sb.toString();
            }
        } catch (Exception ignored) {
            // Keep the raw value when older rows contain plain text.
        }
        return raw;
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

    private void waitForLastAgentSave() {
        try {
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String valueOrDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String preview(String value) {
        if (value == null || value.length() <= 200) {
            return value;
        }
        return value.substring(0, 200);
    }
}
