package com.capstone.interview.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_qnas",
       uniqueConstraints = @UniqueConstraint(columnNames = {"interview_id", "sequence_number"}))
@Getter
@NoArgsConstructor
public class InterviewQna {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    @Column(name = "respondent_member_id")
    private Long respondentMemberId;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Column(name = "question_content", columnDefinition = "TEXT")
    private String questionContent;

    @Column(name = "answer_content", columnDefinition = "TEXT")
    private String answerContent;

    @Column(name = "is_follow_up", nullable = false)
    private boolean isFollowUp = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private InterviewQna parent;

    @Column(name = "intent", length = 255)
    private String intent;

    @Column(name = "model_answer", columnDefinition = "TEXT")
    private String modelAnswer;

    @Column(name = "individual_feedback", columnDefinition = "TEXT")
    private String individualFeedback;

    @Column(name = "answer_summary", columnDefinition = "TEXT")
    private String answerSummary;

    @Column(name = "follow_up_decision", length = 32)
    private String followUpDecision;

    @Column(name = "focus_point", columnDefinition = "TEXT")
    private String focusPoint;

    @Column(name = "audio_url", length = 1024)
    private String audioUrl;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // EvaluationService에서 LLM 평가 결과(모범답안 + 개별피드백) 저장 시 호출
    public void saveFeedback(String modelAnswer, String individualFeedback) {
        this.modelAnswer = modelAnswer;
        this.individualFeedback = individualFeedback;
    }
    @Builder
    public InterviewQna(Interview interview, Integer sequenceNumber, String questionContent,
                        String answerContent, boolean isFollowUp, InterviewQna parent,
                        String intent, String answerSummary, String followUpDecision,
                        String focusPoint, LocalDateTime startedAt, LocalDateTime expiresAt,
                        Long respondentMemberId) {
        this.interview = interview;
        this.respondentMemberId = respondentMemberId;
        this.sequenceNumber = sequenceNumber;
        this.questionContent = questionContent;
        this.answerContent = answerContent;
        this.isFollowUp = isFollowUp;
        this.parent = parent;
        this.intent = intent;
        this.answerSummary = answerSummary;
        this.followUpDecision = followUpDecision;
        this.focusPoint = focusPoint;
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
    }

    public void setRespondentMemberId(Long respondentMemberId) {
        this.respondentMemberId = respondentMemberId;
    }

    public void updateAnswer(String answerContent) {
        this.answerContent = answerContent;
    }

    public void updateQuestion(String questionContent, String intent, boolean isFollowUp) {
        this.questionContent = questionContent;
        this.intent = intent;
        this.isFollowUp = isFollowUp;
    }

    public void updateAnswerAnalysis(String answerSummary, String followUpDecision, String focusPoint) {
        this.answerSummary = answerSummary;
        this.followUpDecision = followUpDecision;
        this.focusPoint = focusPoint;
    }

    public void updateTimer(LocalDateTime startedAt, LocalDateTime expiresAt) {
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // [TEST] 테스트용 더미 QnA 생성 팩토리 메서드 (실제 배포 시 삭제)
    public static InterviewQna createDummy(Interview interview, int seq, String question, String answer, boolean followUp) {
        InterviewQna qna = new InterviewQna();
        qna.interview = interview;
        qna.sequenceNumber = seq;
        qna.questionContent = question;
        qna.answerContent = answer;
        qna.isFollowUp = followUp;
        return qna;
    }
}
