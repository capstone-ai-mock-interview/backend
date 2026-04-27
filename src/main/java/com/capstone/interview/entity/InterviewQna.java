package com.capstone.interview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_qnas")
@Getter
@NoArgsConstructor
public class InterviewQna {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Column(name = "question_content", nullable = false, columnDefinition = "TEXT")
    private String questionContent;

    @Column(name = "answer_content", columnDefinition = "TEXT")
    private String answerContent;

    @Column(name = "is_follow_up", nullable = false)
    private boolean isFollowUp = false;

    // 셀프 참조: 꼬리질문인 경우 부모 질문 ID
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private InterviewQna parent;

    @Column(name = "model_answer", columnDefinition = "TEXT")
    private String modelAnswer;

    @Column(name = "individual_feedback", columnDefinition = "TEXT")
    private String individualFeedback;

    @Column(name = "audio_url", length = 1024)
    private String audioUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 모범답안 + 개별 피드백 저장 (EvaluationService에서 호출) */
    public void updateFeedback(String modelAnswer, String individualFeedback) {
        this.modelAnswer = modelAnswer;
        this.individualFeedback = individualFeedback;
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
}
