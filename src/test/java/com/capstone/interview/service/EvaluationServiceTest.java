package com.capstone.interview.service;

import com.capstone.interview.config.LLMClient;
import com.capstone.interview.dto.EvaluationResponse;
import com.capstone.interview.entity.Interview;
import com.capstone.interview.entity.InterviewQna;
import com.capstone.interview.entity.InterviewStatus;
import com.capstone.interview.repository.InterviewQnARepository;
import com.capstone.interview.repository.InterviewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    @Mock
    private InterviewRepository interviewRepository;
    @Mock
    private InterviewQnARepository interviewQnARepository;
    @Mock
    private LLMClient llmClient;
    @InjectMocks
    private EvaluationService evaluationService;

    private static final String MOCK_TOTAL_RESPONSE = """
            [점수]
            기술 정확성: 85
            논리성: 78
            깊이: 72
            전달력: 80
            
            [강점]
            - Spring 프레임워크에 대한 기본 이해가 있음
            - 답변이 간결하고 명확함
            
            [약점]
            - 구체적인 예시가 부족함
            - 기술 원리에 대한 깊이 있는 설명이 필요함
            
            [종합 피드백]
            전반적으로 기본 개념은 이해하고 있으나 구체적인 사례가 보강되면 더 좋은 답변이 될 것입니다.
            """;

    @Test
    @DisplayName("COMPLETED 면접을 정상적으로 평가한다")
    void evaluate_success() {
        Interview interview = createInterview(10L, InterviewStatus.COMPLETED);
        InterviewQna qna1 = createQna(1, "Spring DI를 설명해주세요", "의존성 주입입니다");
        InterviewQna qna2 = createQna(2, "JPA란?", "ORM 프레임워크입니다");

        given(interviewRepository.findById(10L)).willReturn(Optional.of(interview));
        given(interviewQnARepository.findByInterviewId(10L)).willReturn(List.of(qna1, qna2));
        given(llmClient.invoke(contains("평가해주세요"))).willReturn("기본 개념은 맞지만 구체적 설명이 부족합니다.");
        given(llmClient.invoke(contains("모범답안"))).willReturn("DI란 객체 간 결합도를 낮추기 위해...");
        given(llmClient.invoke(contains("형식으로 정확하게"))).willReturn(MOCK_TOTAL_RESPONSE);

        EvaluationResponse response = evaluationService.evaluate(10L);

        assertThat(response).isNotNull();
        assertThat(response.detailedFeedbacks()).hasSize(2);
        assertThat(response.detailedFeedbacks().get(0).individualFeedback()).isNotNull();
        assertThat(response.detailedFeedbacks().get(0).modelAnswer()).isNotNull();
        assertThat(response.accuracyScore()).isEqualTo(85);
        assertThat(response.logicScore()).isEqualTo(78);
        assertThat(response.depthScore()).isEqualTo(72);
        assertThat(response.deliveryScore()).isEqualTo(80);
        assertThat(response.totalScore()).isEqualTo(78);
        assertThat(response.level()).isEqualTo("중");
        assertThat(response.strengths()).hasSize(2);
        assertThat(response.weaknesses()).hasSize(2);
    }

    @Test
    @DisplayName("존재하지 않는 면접 ID로 호출하면 에러가 발생한다")
    void evaluate_interviewNotFound() {
        given(interviewRepository.findById(999L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> evaluationService.evaluate(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("면접을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("COMPLETED가 아닌 면접은 평가할 수 없다")
    void evaluate_notCompleted() {
        Interview interview = createInterview(10L, InterviewStatus.IN_PROGRESS);
        given(interviewRepository.findById(10L)).willReturn(Optional.of(interview));
        assertThatThrownBy(() -> evaluationService.evaluate(10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("완료된 면접만 평가할 수 있습니다");
    }

    @Test
    @DisplayName("질문-답변이 없으면 에러가 발생한다")
    void evaluate_emptyQnaList() {
        Interview interview = createInterview(10L, InterviewStatus.COMPLETED);
        given(interviewRepository.findById(10L)).willReturn(Optional.of(interview));
        given(interviewQnARepository.findByInterviewId(10L)).willReturn(List.of());
        assertThatThrownBy(() -> evaluationService.evaluate(10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("평가할 질문-답변이 없습니다");
    }

    @Test
    @DisplayName("답변이 없는 질문은 평가에서 제외된다")
    void evaluate_skipEmptyAnswer() {
        Interview interview = createInterview(10L, InterviewStatus.COMPLETED);
        InterviewQna qna1 = createQna(1, "Spring DI?", "의존성 주입입니다");
        InterviewQna qna2 = createQna(2, "JPA란?", null);

        given(interviewRepository.findById(10L)).willReturn(Optional.of(interview));
        given(interviewQnARepository.findByInterviewId(10L)).willReturn(List.of(qna1, qna2));
        given(llmClient.invoke(any())).willReturn(MOCK_TOTAL_RESPONSE);

        EvaluationResponse response = evaluationService.evaluate(10L);
        assertThat(response.detailedFeedbacks()).hasSize(1);
    }

    // ===== 헬퍼 =====

    private Interview createInterview(Long id, InterviewStatus status) {
        try {
            Interview interview = new Interview();
            var idField = Interview.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(interview, id);
            var statusField = Interview.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(interview, status);
            return interview;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private InterviewQna createQna(int seq, String question, String answer) {
        try {
            InterviewQna qna = new InterviewQna();
            var seqField = InterviewQna.class.getDeclaredField("sequenceNumber");
            seqField.setAccessible(true);
            seqField.set(qna, seq);
            var qField = InterviewQna.class.getDeclaredField("questionContent");
            qField.setAccessible(true);
            qField.set(qna, question);
            var aField = InterviewQna.class.getDeclaredField("answerContent");
            aField.setAccessible(true);
            aField.set(qna, answer);
            return qna;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
