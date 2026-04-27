package com.capstone.interview.service;

import com.capstone.interview.config.LLMClient;
import com.capstone.interview.dto.EvaluationResponse;
import com.capstone.interview.dto.QAPair;
import com.capstone.interview.entity.Interview;
import com.capstone.interview.entity.InterviewQna;
import com.capstone.interview.entity.InterviewStatus;
import com.capstone.interview.repository.InterviewQnARepository;
import com.capstone.interview.repository.InterviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final InterviewRepository interviewRepository;
    private final InterviewQnARepository interviewQnARepository;
    private final LLMClient llmClient;

    @Transactional
    public EvaluationResponse evaluate(Long interviewId) {

        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new IllegalArgumentException("면접을 찾을 수 없습니다: " + interviewId));

        if (interview.getStatus() != InterviewStatus.COMPLETED) {
            throw new IllegalArgumentException("완료된 면접만 평가할 수 있습니다. 현재 상태: " + interview.getStatus());
        }

        List<InterviewQna> qnaList = interviewQnARepository.findByInterviewId(interviewId);
        if (qnaList.isEmpty()) {
            throw new IllegalArgumentException("평가할 질문-답변이 없습니다.");
        }

        // 각 질문-답변 개별 평가 + 모범답안 생성
        List<QAPair> detailedFeedbacks = new ArrayList<>();

        for (InterviewQna qna : qnaList) {
            if (qna.getAnswerContent() == null || qna.getAnswerContent().isBlank()) {
                continue;
            }

            String individualPrompt = buildIndividualPrompt(qna.getQuestionContent(), qna.getAnswerContent());
            String individualFeedback = llmClient.invoke(individualPrompt);

            String modelAnswerPrompt = buildModelAnswerPrompt(qna.getQuestionContent());
            String modelAnswer = llmClient.invoke(modelAnswerPrompt);

            qna.updateFeedback(modelAnswer, individualFeedback);

            detailedFeedbacks.add(new QAPair(
                    qna.getQuestionContent(),
                    qna.getAnswerContent(),
                    modelAnswer,
                    individualFeedback
            ));
        }

        // 종합 평가
        String totalPrompt = buildTotalPrompt(qnaList);
        String totalResult = llmClient.invoke(totalPrompt);

        int accuracyScore = parseScore(totalResult, "기술 정확성");
        int logicScore = parseScore(totalResult, "논리성");
        int depthScore = parseScore(totalResult, "깊이");
        int deliveryScore = parseScore(totalResult, "전달력");
        int totalScore = (accuracyScore + logicScore + depthScore + deliveryScore) / 4;
        String level = calculateLevel(totalScore);
        List<String> strengths = parseList(totalResult, "강점");
        List<String> weaknesses = parseList(totalResult, "약점");
        String overallFeedback = parseFeedbackText(totalResult);

        interview.updateTotalFeedback(totalResult);

        return new EvaluationResponse(
                totalScore, level,
                accuracyScore, logicScore, depthScore, deliveryScore,
                overallFeedback, strengths, weaknesses,
                detailedFeedbacks
        );
    }

    // ===== 프롬프트 =====

    private String buildIndividualPrompt(String question, String answer) {
        return """
                당신은 기술 면접 평가관입니다.
                아래 질문과 답변을 평가해주세요.
                
                [질문]
                %s
                
                [답변]
                %s
                
                다음 4가지 기준으로 평가해주세요:
                1. 기술 정확성: 답변 내용의 기술적 사실 여부
                2. 논리성: 답변의 논리적 구조와 흐름
                3. 깊이: 기술 원리에 대한 이해 수준
                4. 전달력: 답변의 명확성과 구조화 정도
                
                한국어로 3~5문장으로 피드백을 작성해주세요.
                """.formatted(question, answer);
    }

    private String buildModelAnswerPrompt(String question) {
        return """
                당신은 기술 면접 전문가입니다.
                아래 질문에 대한 모범답안을 작성해주세요.
                
                [질문]
                %s
                
                핵심 개념을 포함하여 한국어로 3~5문장으로 작성해주세요.
                """.formatted(question);
    }

    private String buildTotalPrompt(List<InterviewQna> qnaList) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                당신은 기술 면접 평가관입니다.
                아래는 한 면접의 전체 질문-답변 목록입니다.
                
                """);

        for (InterviewQna qna : qnaList) {
            if (qna.getAnswerContent() == null || qna.getAnswerContent().isBlank()) {
                continue;
            }
            sb.append("[질문 %d] %s\n".formatted(qna.getSequenceNumber(), qna.getQuestionContent()));
            sb.append("[답변 %d] %s\n\n".formatted(qna.getSequenceNumber(), qna.getAnswerContent()));
        }

        sb.append("""
                아래 형식으로 정확하게 응답해주세요:
                
                [점수]
                기술 정확성: (0~100 숫자만)
                논리성: (0~100 숫자만)
                깊이: (0~100 숫자만)
                전달력: (0~100 숫자만)
                
                [강점]
                - (강점 1)
                - (강점 2)
                - (강점 3)
                
                [약점]
                - (약점 1)
                - (약점 2)
                - (약점 3)
                
                [종합 피드백]
                (전반적인 면접 수행 평가를 3~5문장으로 작성)
                """);

        return sb.toString();
    }

    // ===== 파싱 =====

    private int parseScore(String response, String label) {
        Pattern pattern = Pattern.compile(label + "\\s*[:：]\\s*(\\d+)");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            int score = Integer.parseInt(matcher.group(1));
            return Math.min(100, Math.max(0, score));
        }
        return 0;
    }

    private String calculateLevel(int totalScore) {
        if (totalScore >= 80) return "상";
        if (totalScore >= 50) return "중";
        return "하";
    }

    private List<String> parseList(String response, String section) {
        List<String> items = new ArrayList<>();
        String sectionPattern = "\\[" + section + "\\]([\\s\\S]*?)(?=\\[|$)";
        Pattern pattern = Pattern.compile(sectionPattern);
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            String block = matcher.group(1);
            Pattern itemPattern = Pattern.compile("-\\s*(.+)");
            Matcher itemMatcher = itemPattern.matcher(block);
            while (itemMatcher.find()) {
                items.add(itemMatcher.group(1).trim());
            }
        }
        return items;
    }

    private String parseFeedbackText(String response) {
        Pattern pattern = Pattern.compile("\\[종합 피드백\\]([\\s\\S]*)");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return response;
    }
}
